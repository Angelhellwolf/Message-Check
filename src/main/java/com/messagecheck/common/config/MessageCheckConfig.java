package com.messagecheck.common.config;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@SuppressWarnings("unchecked")
public final class MessageCheckConfig {
    private final boolean enabled;
    private final String bypassPermission;
    private final String region;
    private final int asyncThreads;
    private final Set<String> checkCommands;
    private final Set<String> blockedPrefixes;
    private final String notifyStaffPermission;
    private final String staffNotifyFormat;
    private final SpamControlConfig spamControl;
    private final FilterConfig filterConfig;
    private final OpenAiConfig openAiConfig;
    private final CacheConfig cacheConfig;
    private final SqlConfig sqlConfig;
    private final RedisConfig redisConfig;
    private final LoggingConfig loggingConfig;

    public MessageCheckConfig(
            boolean enabled,
            String bypassPermission,
            String region,
            int asyncThreads,
            Set<String> checkCommands,
            Set<String> blockedPrefixes,
            String notifyStaffPermission,
            String staffNotifyFormat,
            SpamControlConfig spamControl,
            FilterConfig filterConfig,
            OpenAiConfig openAiConfig,
            CacheConfig cacheConfig,
            SqlConfig sqlConfig,
            RedisConfig redisConfig,
            LoggingConfig loggingConfig
    ) {
        this.enabled = enabled;
        this.bypassPermission = defaultIfNull(bypassPermission, "messagecheck.bypass");
        this.region = region == null ? "" : region.trim();
        this.asyncThreads = asyncThreads <= 0 ? 2 : asyncThreads;
        this.checkCommands = Collections.unmodifiableSet(normalise(checkCommands));
        this.blockedPrefixes = Collections.unmodifiableSet(normalise(blockedPrefixes));
        this.notifyStaffPermission = defaultIfNull(notifyStaffPermission, "messagecheck.notify");
        this.staffNotifyFormat = defaultIfNull(staffNotifyFormat, "&c[Message-Check] &7Blocked message from &e%player%&7: &f%message%");
        this.spamControl = Objects.requireNonNull(spamControl, "spamControl");
        this.filterConfig = Objects.requireNonNull(filterConfig, "filterConfig");
        this.openAiConfig = Objects.requireNonNull(openAiConfig, "openAiConfig");
        this.cacheConfig = Objects.requireNonNull(cacheConfig, "cacheConfig");
        this.sqlConfig = Objects.requireNonNull(sqlConfig, "sqlConfig");
        this.redisConfig = Objects.requireNonNull(redisConfig, "redisConfig");
        this.loggingConfig = Objects.requireNonNull(loggingConfig, "loggingConfig");
    }

    private static Set<String> normalise(Set<String> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String value : input) {
            if (value == null) {
                continue;
            }
            set.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return set;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getBypassPermission() {
        return bypassPermission;
    }

    public String getRegion() {
        return region;
    }

    public int getAsyncThreads() {
        return asyncThreads;
    }

    public Set<String> getCheckCommands() {
        return checkCommands;
    }

    public Set<String> getBlockedPrefixes() {
        return blockedPrefixes;
    }

    public String getNotifyStaffPermission() {
        return notifyStaffPermission;
    }

    public String getStaffNotifyFormat() {
        return staffNotifyFormat;
    }

    public SpamControlConfig getSpamControl() {
        return spamControl;
    }

    public FilterConfig getFilterConfig() {
        return filterConfig;
    }

    public OpenAiConfig getOpenAiConfig() {
        return openAiConfig;
    }

    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    public SqlConfig getSqlConfig() {
        return sqlConfig;
    }

    public RedisConfig getRedisConfig() {
        return redisConfig;
    }

    public LoggingConfig getLoggingConfig() {
        return loggingConfig;
    }

    public static MessageCheckConfig from(Map<String, Object> root) {
        Map<String, Object> settings = map(root.get("settings"));
        Map<String, Object> spam = map(root.get("spam-control"));
        Map<String, Object> filters = map(root.get("filters"));
        Map<String, Object> openAi = map(root.get("openai"));
        Map<String, Object> cache = map(root.get("cache"));
        Map<String, Object> storage = map(root.get("storage"));
        Map<String, Object> sql = map(storage.get("sql"));
        Map<String, Object> redis = map(storage.get("redis"));
        Map<String, Object> logging = map(root.get("logging"));

        boolean enabled = bool(settings.get("enabled"), true);
        String bypass = string(settings.get("bypass-permission"), "messagecheck.bypass");
        String region = string(settings.get("region"), "");
        int asyncThreads = number(settings.get("async-threads"), 2).intValue();
        Set<String> commands = toSet(settings.get("check-commands"));
        Set<String> blockedPrefixes = toSet(settings.get("blocked-prefixes"));
        String notifyPerm = string(settings.get("notify-staff-permission"), "messagecheck.notify");
        String notifyFormat = string(settings.get("staff-notify-format"), "&c[Message-Check] &7Blocked message from &e%player%&7: &f%message%");

        SpamControlConfig spamConfig = new SpamControlConfig(
                number(spam.get("max-messages"), 8).intValue(),
                Duration.ofSeconds(number(spam.get("interval-seconds"), 180).longValue()),
                doubleNumber(spam.get("similarity-threshold"), 0.92),
                string(spam.get("action"), "block")
        );

        FilterConfig filterConfig = new FilterConfig(
                toSet(filters.get("banned-words")),
                toSet(filters.get("banned-patterns")),
                bool(filters.get("sanitize-colors"), true),
                bool(filters.get("strip-urls"), false)
        );

        OpenAiConfig openAiConfig = new OpenAiConfig(
                bool(openAi.get("enabled"), false),
                string(openAi.get("base-url"), ""),
                string(openAi.get("api-key"), ""),
                string(openAi.get("model"), "gpt-3.5-turbo"),
                number(openAi.get("request-timeout-millis"), 4500).intValue(),
                Duration.ofMinutes(number(openAi.get("cache-minutes"), 1440).longValue()),
                string(openAi.get("prompt-template"), "")
        );

        CacheConfig cacheConfig = new CacheConfig(
                number(cache.get("maximum-size"), 10000).longValue(),
                Duration.ofMinutes(number(cache.get("expire-after-minutes"), 720).longValue())
        );

        SqlConfig sqlConfig = new SqlConfig(
                bool(sql.get("enabled"), false),
                string(sql.get("jdbc-url"), ""),
                string(sql.get("username"), ""),
                string(sql.get("password"), ""),
                number(sql.get("maximum-pool-size"), 8).intValue()
        );

        RedisConfig redisConfig = new RedisConfig(
                bool(redis.get("enabled"), false),
                string(redis.get("host"), "localhost"),
                number(redis.get("port"), 6379).intValue(),
                string(redis.get("password"), ""),
                number(redis.get("database"), 0).intValue(),
                bool(redis.get("ssl"), false)
        );

        LoggingConfig loggingConfig = new LoggingConfig(
                string(logging.get("level"), "INFO"),
                bool(logging.get("debug"), false)
        );

        return new MessageCheckConfig(
                enabled,
                bypass,
                region,
                asyncThreads,
                commands,
                blockedPrefixes,
                notifyPerm,
                notifyFormat,
                spamConfig,
                filterConfig,
                openAiConfig,
                cacheConfig,
                sqlConfig,
                redisConfig,
                loggingConfig
        );
    }

    private static Map<String, Object> map(Object raw) {
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        return Collections.emptyMap();
    }

    private static Set<String> toSet(Object raw) {
        if (raw instanceof List) {
            Set<String> set = new LinkedHashSet<>();
            for (Object entry : (List<Object>) raw) {
                if (entry != null) {
                    set.add(entry.toString());
                }
            }
            return set;
        }
        if (raw instanceof Set) {
            Set<String> set = new LinkedHashSet<>();
            for (Object entry : (Set<Object>) raw) {
                if (entry != null) {
                    set.add(entry.toString());
                }
            }
            return set;
        }
        return Collections.emptySet();
    }

    private static String string(Object raw, String def) {
        return raw == null ? def : raw.toString();
    }

    private static Boolean bool(Object raw, boolean def) {
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof String) {
            return Boolean.parseBoolean((String) raw);
        }
        return def;
    }

    private static Number number(Object raw, Number def) {
        if (raw instanceof Number) {
            return (Number) raw;
        }
        if (raw instanceof String) {
            try {
                return Double.parseDouble((String) raw);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static double doubleNumber(Object raw, double def) {
        if (raw instanceof Number) {
            return ((Number) raw).doubleValue();
        }
        if (raw instanceof String) {
            try {
                return Double.parseDouble((String) raw);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public static final class SpamControlConfig {
        private final int maxMessages;
        private final Duration interval;
        private final double similarityThreshold;
        private final String action;

        public SpamControlConfig(int maxMessages, Duration interval, double similarityThreshold, String action) {
            this.maxMessages = Math.max(1, maxMessages);
            this.interval = Objects.requireNonNull(interval, "interval");
            this.similarityThreshold = similarityThreshold;
            this.action = defaultIfNull(action, "block");
        }

        public int getMaxMessages() {
            return maxMessages;
        }

        public Duration getInterval() {
            return interval;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public String getAction() {
            return action;
        }
    }

    public static final class FilterConfig {
        private final Set<String> bannedWords;
        private final Set<String> bannedPatterns;
        private final boolean sanitizeColors;
        private final boolean stripUrls;

        public FilterConfig(Set<String> bannedWords, Set<String> bannedPatterns, boolean sanitizeColors, boolean stripUrls) {
            this.bannedWords = Collections.unmodifiableSet(normalise(bannedWords));
            this.bannedPatterns = Collections.unmodifiableSet(bannedPatterns == null ? Collections.emptySet() : bannedPatterns);
            this.sanitizeColors = sanitizeColors;
            this.stripUrls = stripUrls;
        }

        public Set<String> getBannedWords() {
            return bannedWords;
        }

        public Set<String> getBannedPatterns() {
            return bannedPatterns;
        }

        public boolean isSanitizeColors() {
            return sanitizeColors;
        }

        public boolean isStripUrls() {
            return stripUrls;
        }
    }

    public static final class OpenAiConfig {
        private final boolean enabled;
        private final String baseUrl;
        private final String apiKey;
        private final String model;
        private final int timeoutMillis;
        private final Duration cacheDuration;
        private final String promptTemplate;

        public OpenAiConfig(boolean enabled, String baseUrl, String apiKey, String model, int timeoutMillis, Duration cacheDuration, String promptTemplate) {
            this.enabled = enabled;
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.model = model;
            this.timeoutMillis = timeoutMillis;
            this.cacheDuration = cacheDuration;
            this.promptTemplate = promptTemplate;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getModel() {
            return model;
        }

        public int getTimeoutMillis() {
            return timeoutMillis;
        }

        public Duration getCacheDuration() {
            return cacheDuration;
        }

        public String getPromptTemplate() {
            return promptTemplate;
        }
    }

    public static final class CacheConfig {
        private final long maximumSize;
        private final Duration expireAfter;

        public CacheConfig(long maximumSize, Duration expireAfter) {
            this.maximumSize = maximumSize;
            this.expireAfter = expireAfter;
        }

        public long getMaximumSize() {
            return maximumSize;
        }

        public Duration getExpireAfter() {
            return expireAfter;
        }
    }

    public static final class SqlConfig {
        private final boolean enabled;
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final int maximumPoolSize;

        public SqlConfig(boolean enabled, String jdbcUrl, String username, String password, int maximumPoolSize) {
            this.enabled = enabled;
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
            this.maximumPoolSize = maximumPoolSize;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }
    }

    public static final class RedisConfig {
        private final boolean enabled;
        private final String host;
        private final int port;
        private final String password;
        private final int database;
        private final boolean ssl;

        public RedisConfig(boolean enabled, String host, int port, String password, int database, boolean ssl) {
            this.enabled = enabled;
            this.host = host;
            this.port = port;
            this.password = password;
            this.database = database;
            this.ssl = ssl;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getPassword() {
            return password;
        }

        public int getDatabase() {
            return database;
        }

        public boolean isSsl() {
            return ssl;
        }
    }

    public static final class LoggingConfig {
        private final String level;
        private final boolean debug;

        public LoggingConfig(String level, boolean debug) {
            this.level = level;
            this.debug = debug;
        }

        public String getLevel() {
            return level;
        }

        public boolean isDebug() {
            return debug;
        }
    }
    private static <T> T defaultIfNull(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
