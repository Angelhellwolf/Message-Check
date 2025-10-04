package com.messagecheck.common.moderation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.messagecheck.common.FilterOutcome;
import com.messagecheck.common.FilterResult;
import com.messagecheck.common.MessageContext;
import com.messagecheck.common.config.MessageCheckConfig;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OpenAiModerationProvider implements ModerationProvider {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final MessageCheckConfig.OpenAiConfig config;
    private final OkHttpClient client;
    private final Executor executor;
    private final Logger logger;

    public OpenAiModerationProvider(MessageCheckConfig.OpenAiConfig config, Executor executor, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.logger = logger;
        this.client = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build();
    }

    @Override
    public CompletableFuture<FilterResult> evaluate(MessageContext context) {
        return CompletableFuture.supplyAsync(() -> executeRequest(context), executor);
    }

    private FilterResult executeRequest(MessageContext context) {
        String promptTemplate = config.getPromptTemplate();
        String prompt = promptTemplate == null || promptTemplate.isEmpty()
                ? "Classify the following Minecraft chat message as ALLOW, BLOCK or FLAG only: " + context.getRawMessage()
                : String.format(promptTemplate, context.getRawMessage());

        JsonObject payload = new JsonObject();
        payload.addProperty("model", config.getModel());
        JsonArray messages = new JsonArray();

        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", "You moderate Minecraft chat. Return only ALLOW, BLOCK or FLAG.");
        messages.add(system);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", prompt);
        messages.add(user);

        payload.add("messages", messages);
        payload.addProperty("temperature", 0);
        payload.addProperty("max_tokens", 4);

        RequestBody requestBody = RequestBody.create(payload.toString().getBytes(StandardCharsets.UTF_8), JSON);

        Request request = new Request.Builder()
                .url(config.getBaseUrl())
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.log(Level.WARNING, "OpenAI moderation request failed: {0}", response);
                return FilterResult.flag("openai_http_" + response.code());
            }
            String body = response.body() != null ? response.body().string() : "";
            String verdict = parseVerdict(body);
            if (verdict == null) {
                return FilterResult.flag("openai_unknown_response");
            }
            switch (verdict) {
                case "allow":
                    return FilterResult.allow();
                case "block":
                    return FilterResult.block("openai_block");
                case "flag":
                default:
                    return new FilterResult(FilterOutcome.FLAG, "openai_flag", Instant.now());
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "OpenAI moderation request error", ex);
            return FilterResult.flag("openai_error");
        }
    }

    private String parseVerdict(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                return null;
            }
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");
            if (message == null) {
                return null;
            }
            String content = message.get("content").getAsString();
            return content.trim().toLowerCase();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to parse OpenAI response: {0}", body);
            return null;
        }
    }
}
