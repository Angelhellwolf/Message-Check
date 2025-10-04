package com.messagecheck.common.storage;

import com.messagecheck.common.FilterOutcome;
import com.messagecheck.common.FilterResult;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RedisDecisionStore implements DecisionStore {
    private final JedisPool pool;
    private final Executor executor;
    private final Logger logger;
    private final int expirySeconds;

    public RedisDecisionStore(String host, int port, String password, int database, boolean ssl, int expirySeconds, Executor executor, Logger logger) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(32);
        this.pool = new JedisPool(config, host, port, 5000, password == null || password.isEmpty() ? null : password, database, ssl);
        this.executor = executor;
        this.logger = logger;
        this.expirySeconds = expirySeconds;
    }

    @Override
    public CompletableFuture<Optional<FilterResult>> find(String key) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                String outcome = jedis.hget(key, "outcome");
                if (outcome == null) {
                    return Optional.empty();
                }
                String reason = jedis.hget(key, "reason");
                String timestampString = jedis.hget(key, "timestamp");
                Instant timestamp = timestampString != null ? Instant.ofEpochMilli(Long.parseLong(timestampString)) : Instant.now();
                return Optional.of(new FilterResult(FilterOutcome.valueOf(outcome), reason, timestamp));
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed to query redis cache", ex);
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> save(String key, FilterResult result) {
        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.hset(key, "outcome", result.getOutcome().name());
                jedis.hset(key, "reason", result.getReason());
                jedis.hset(key, "timestamp", String.valueOf(result.getTimestamp().toEpochMilli()));
                if (expirySeconds > 0) {
                    jedis.expire(key, expirySeconds);
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed to store redis cache", ex);
            }
        }, executor);
    }

    @Override
    public void close() {
        pool.close();
    }
}
