package com.messagecheck.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.messagecheck.common.config.MessageCheckConfig;
import com.messagecheck.common.moderation.ChineseModerationProvider;
import com.messagecheck.common.moderation.ModerationProvider;
import com.messagecheck.common.moderation.OpenAiModerationProvider;
import com.messagecheck.common.storage.CompositeDecisionStore;
import com.messagecheck.common.storage.DecisionStore;
import com.messagecheck.common.storage.NoOpDecisionStore;
import com.messagecheck.common.storage.RedisDecisionStore;
import com.messagecheck.common.storage.SqlDecisionStore;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageCheckService implements AutoCloseable {
    private static final Pattern COLOR_PATTERN = Pattern.compile("§[0-9A-FK-ORa-fk-or]");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    private final MessageCheckConfig config;
    private final Logger logger;
    private final EventLoopGroup eventLoopGroup;
    private final Executor executor;
    private final Cache<String, FilterResult> cache;
    private final ModerationProvider moderationProvider;
    private final DecisionStore decisionStore;
    private final Map<UUID, Deque<MessageHistoryEntry>> history;
    private final Set<String> bannedWords;
    private final List<Pattern> bannedPatterns;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public MessageCheckService(MessageCheckConfig config, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.logger = logger;
        this.eventLoopGroup = new NioEventLoopGroup(config.getAsyncThreads());
        this.executor = eventLoopGroup.next();
        this.cache = buildCache(config.getCacheConfig());
        this.bannedWords = config.getFilterConfig().getBannedWords();
        this.bannedPatterns = compilePatterns(config.getFilterConfig().getBannedPatterns());
        this.history = new ConcurrentHashMap<>();
        this.moderationProvider = createModerationProvider();
        this.decisionStore = createDecisionStore();
    }

    private Cache<String, FilterResult> buildCache(MessageCheckConfig.CacheConfig cacheConfig) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(cacheConfig.getMaximumSize());
        Duration expire = cacheConfig.getExpireAfter();
        if (!expire.isZero() && !expire.isNegative()) {
            builder.expireAfterWrite(expire);
        }
        return builder.build();
    }

    private List<Pattern> compilePatterns(Set<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return Collections.emptyList();
        }
        List<Pattern> compiled = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            try {
                compiled.add(Pattern.compile(pattern));
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Invalid regex pattern in configuration: {0}", pattern);
            }
        }
        return compiled;
    }

    private ModerationProvider createModerationProvider() {
        if (isChineseRegion(config.getRegion())) {
            return new ChineseModerationProvider(executor, logger);
        }
        if (!config.getOpenAiConfig().isEnabled() || config.getOpenAiConfig().getApiKey().isEmpty()) {
            return new ModerationProvider() {
                @Override
                public CompletableFuture<FilterResult> evaluate(MessageContext context) {
                    return CompletableFuture.completedFuture(FilterResult.allow());
                }
            };
        }
        return new OpenAiModerationProvider(config.getOpenAiConfig(), executor, logger);
    }

    private boolean isChineseRegion(String region) {
        if (region == null || region.trim().isEmpty()) {
            return false;
        }
        String normalised = region.trim().toLowerCase(Locale.ROOT);
        String simplified = normalised.replace('-', '_');
        return normalised.equals("cn")
                || normalised.equals("china")
                || normalised.contains("china")
                || simplified.equals("zh_cn");
    }

    private DecisionStore createDecisionStore() {
        List<DecisionStore> stores = new ArrayList<>();
        MessageCheckConfig.SqlConfig sql = config.getSqlConfig();
        if (sql.isEnabled() && !sql.getJdbcUrl().isEmpty()) {
            try {
                stores.add(new SqlDecisionStore(sql, executor, logger));
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Failed to initialise SQL decision store", ex);
            }
        }
        MessageCheckConfig.RedisConfig redis = config.getRedisConfig();
        if (redis.isEnabled()) {
            try {
                int expiry = (int) config.getOpenAiConfig().getCacheDuration().getSeconds();
                stores.add(new RedisDecisionStore(
                        redis.getHost(),
                        redis.getPort(),
                        redis.getPassword(),
                        redis.getDatabase(),
                        redis.isSsl(),
                        expiry,
                        executor,
                        logger
                ));
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Failed to initialise redis decision store", ex);
            }
        }
        if (stores.isEmpty()) {
            return new NoOpDecisionStore();
        }
        return new CompositeDecisionStore(stores);
    }

    public CompletableFuture<FilterResult> evaluate(MessageContext context) {
        if (!config.isEnabled()) {
            return CompletableFuture.completedFuture(FilterResult.allow());
        }
        return CompletableFuture.supplyAsync(() -> prepareState(context), executor)
                .thenCompose(this::resolveDecision);
    }

    private EvaluationState prepareState(MessageContext context) {
        String sanitized = sanitizeMessage(context.getRawMessage());
        String hashed = hashMessage(sanitized);
        return new EvaluationState(context, sanitized.toLowerCase(), context.getRawMessage(), hashed);
    }

    private CompletableFuture<FilterResult> resolveDecision(EvaluationState state) {
        FilterResult spamResult = detectSpam(state);
        if (spamResult != null) {
            cache.put(state.cacheKey, spamResult);
            return storeAndReturn(state.cacheKey, spamResult);
        }

        FilterResult localResult = evaluateLocally(state);
        if (localResult != null) {
            cache.put(state.cacheKey, localResult);
            return storeAndReturn(state.cacheKey, localResult);
        }

        FilterResult cachedResult = cache.getIfPresent(state.cacheKey);
        if (cachedResult != null) {
            return CompletableFuture.completedFuture(cachedResult);
        }

        return decisionStore.find(state.cacheKey).thenCompose(optional -> {
            if (optional.isPresent()) {
                FilterResult result = optional.get();
                cache.put(state.cacheKey, result);
                return CompletableFuture.completedFuture(result);
            }
            return moderationProvider.evaluate(state.context).thenCompose(result -> {
                cache.put(state.cacheKey, result);
                return storeAndReturn(state.cacheKey, result);
            });
        });
    }

    private CompletableFuture<FilterResult> storeAndReturn(String key, FilterResult result) {
        return decisionStore.save(key, result)
                .exceptionally(throwable -> {
                    logger.log(Level.WARNING, "Failed to persist moderation result", throwable);
                    return null;
                })
                .thenApply(ignored -> result);
    }

    private FilterResult detectSpam(EvaluationState state) {
        MessageCheckConfig.SpamControlConfig spamConfig = config.getSpamControl();
        if (spamConfig.getMaxMessages() <= 0) {
            return null;
        }
        UUID uuid = state.context.getPlayerId();
        Deque<MessageHistoryEntry> deque = history.computeIfAbsent(uuid, key -> new ArrayDeque<>());
        Instant cutoff = Instant.now().minus(spamConfig.getInterval());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().timestamp.isBefore(cutoff)) {
                deque.removeFirst();
            }
            int sizeBefore = deque.size();
            for (MessageHistoryEntry entry : deque) {
                double similarity = similarity(entry.normalizedMessage, state.normalized);
                if (similarity >= spamConfig.getSimilarityThreshold()) {
                    entry.bump();
                    if (entry.count >= spamConfig.getMaxMessages()) {
                        return FilterResult.block("spam_similarity");
                    }
                }
            }
            deque.addLast(new MessageHistoryEntry(state.normalized));
            if (sizeBefore + 1 >= spamConfig.getMaxMessages()) {
                return FilterResult.block("spam_rate");
            }
        }
        return null;
    }

    private FilterResult evaluateLocally(EvaluationState state) {
        String rawLower = state.rawMessage.toLowerCase();
        for (String prefix : config.getBlockedPrefixes()) {
            if (!prefix.isEmpty() && rawLower.startsWith(prefix)) {
                return FilterResult.block("blocked_prefix");
            }
        }

        for (String word : bannedWords) {
            if (state.normalized.contains(word.toLowerCase())) {
                return FilterResult.block("banned_word");
            }
        }

        for (Pattern pattern : bannedPatterns) {
            Matcher matcher = pattern.matcher(state.rawMessage);
            if (matcher.find()) {
                return FilterResult.block("banned_pattern");
            }
        }
        return null;
    }

    private String sanitizeMessage(String message) {
        if (message == null) {
            return "";
        }
        String sanitized = message;
        if (config.getFilterConfig().isSanitizeColors()) {
            sanitized = COLOR_PATTERN.matcher(sanitized).replaceAll("");
        }
        if (config.getFilterConfig().isStripUrls()) {
            sanitized = URL_PATTERN.matcher(sanitized).replaceAll("");
        }
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        return sanitized;
    }

    private String hashMessage(String message) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }

    private double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int distance = levenshteinDistance(a, b);
        int maxLen = Math.max(a.length(), b.length());
        return 1.0 - ((double) distance / (double) maxLen);
    }

    private int levenshteinDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) {
            costs[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int corner = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int upper = costs[j];
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                costs[j] = Math.min(Math.min(costs[j - 1] + 1, costs[j] + 1), corner + cost);
                corner = upper;
            }
        }
        return costs[b.length()];
    }

    public boolean shouldCheckCommand(String commandLine) {
        String lower = commandLine.toLowerCase();
        for (String prefix : config.getCheckCommands()) {
            if (lower.startsWith(prefix.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public String getBypassPermission() {
        return config.getBypassPermission();
    }

    public MessageCheckConfig getConfig() {
        return config;
    }

    public void notifyBlocked(String staffFormat, String playerName, String message, java.util.function.Consumer<String> sender) {
        String formatted = staffFormat
                .replace("%player%", playerName)
                .replace("%message%", message);
        sender.accept(formatted);
    }

    public void recordAddress(UUID uuid, InetAddress address) {
        // Placeholder for future IP-based checks
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                moderationProvider.close();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed to close moderation provider", ex);
            }
            try {
                decisionStore.close();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed to close decision store", ex);
            }
            eventLoopGroup.shutdownGracefully();
        }
    }

    private static final class EvaluationState {
        private final MessageContext context;
        private final String normalized;
        private final String rawMessage;
        private final String cacheKey;

        private EvaluationState(MessageContext context, String normalized, String rawMessage, String cacheKey) {
            this.context = context;
            this.normalized = normalized;
            this.rawMessage = rawMessage;
            this.cacheKey = cacheKey;
        }
    }

    private static final class MessageHistoryEntry {
        private final String normalizedMessage;
        private Instant timestamp;
        private int count;

        private MessageHistoryEntry(String normalizedMessage) {
            this.normalizedMessage = normalizedMessage;
            this.timestamp = Instant.now();
            this.count = 1;
        }

        private void bump() {
            this.timestamp = Instant.now();
            this.count++;
        }
    }
}
