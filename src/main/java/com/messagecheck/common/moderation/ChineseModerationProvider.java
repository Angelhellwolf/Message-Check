package com.messagecheck.common.moderation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.messagecheck.common.FilterOutcome;
import com.messagecheck.common.FilterResult;
import com.messagecheck.common.MessageContext;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Moderation provider that delegates to the public xxapi.cn detection service when operating in mainland China.
 */
public final class ChineseModerationProvider implements ModerationProvider {
    private static final String DETECT_URL = "https://v2.xxapi.cn/api/detect";

    private final OkHttpClient client;
    private final Executor executor;
    private final Logger logger;

    public ChineseModerationProvider(Executor executor, Logger logger) {
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
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(DETECT_URL))
                .newBuilder()
                .addQueryParameter("text", context.getRawMessage())
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (logger != null) {
                    logger.log(Level.WARNING, "Chinese moderation request failed: {0}", response);
                }
                return FilterResult.flag("cn_detect_http_" + response.code());
            }
            String body = response.body() != null ? response.body().string() : "";
            String trimmed = body.trim();
            int jsonStart = trimmed.indexOf('{');
            if (jsonStart > 0) {
                trimmed = trimmed.substring(jsonStart);
            }
            JsonObject json = JsonParser.parseString(trimmed).getAsJsonObject();
            boolean prohibited = json.has("is_prohibited") && json.get("is_prohibited").getAsBoolean();
            if (prohibited) {
                return FilterResult.block("cn_detect_block");
            }
            return FilterResult.allow();
        } catch (IOException ex) {
            if (logger != null) {
                logger.log(Level.WARNING, "Chinese moderation request error", ex);
            }
            return FilterResult.flag("cn_detect_error");
        } catch (Exception ex) {
            if (logger != null) {
                logger.log(Level.WARNING, "Chinese moderation parse error", ex);
            }
            return new FilterResult(FilterOutcome.FLAG, "cn_detect_parse", context.getCreatedAt());
        }
    }
}
