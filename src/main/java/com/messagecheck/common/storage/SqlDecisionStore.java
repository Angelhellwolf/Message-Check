package com.messagecheck.common.storage;

import com.messagecheck.common.FilterOutcome;
import com.messagecheck.common.FilterResult;
import com.messagecheck.common.config.MessageCheckConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SqlDecisionStore implements DecisionStore {
    private static final String TABLE = "message_check_cache";
    private final HikariDataSource dataSource;
    private final Executor executor;
    private final Logger logger;

    public SqlDecisionStore(MessageCheckConfig.SqlConfig config, Executor executor, Logger logger) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
        hikariConfig.setPoolName("MessageCheck-SQL");
        hikariConfig.setAutoCommit(true);
        this.dataSource = new HikariDataSource(hikariConfig);
        this.executor = executor;
        this.logger = logger;
        initialise();
    }

    private void initialise() {
        runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                                 "message_hash VARCHAR(128) PRIMARY KEY, " +
                                 "outcome VARCHAR(16) NOT NULL, " +
                                 "reason VARCHAR(255) NOT NULL, " +
                                 "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                                 ")")) {
                statement.execute();
            }
        }).exceptionally(throwable -> {
            logger.log(Level.SEVERE, "Failed to initialise SQL cache", throwable);
            return null;
        });
    }

    private <T> CompletableFuture<T> runAsync(SqlSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }, executor);
    }

    private CompletableFuture<Void> runAsync(SqlRunnable runnable) {
        return CompletableFuture.runAsync(() -> {
            try {
                runnable.run();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<FilterResult>> find(String key) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT outcome, reason, updated_at FROM " + TABLE + " WHERE message_hash = ?")) {
                statement.setString(1, key);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.<FilterResult>empty();
                    }
                    FilterOutcome outcome = FilterOutcome.valueOf(resultSet.getString("outcome"));
                    String reason = resultSet.getString("reason");
                    Timestamp timestamp = resultSet.getTimestamp("updated_at");
                    Instant instant = timestamp != null ? timestamp.toInstant() : Instant.now();
                    return Optional.of(new FilterResult(outcome, reason, instant));
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed to query SQL cache", ex);
                return Optional.<FilterResult>empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> save(String key, FilterResult result) {
        return runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "REPLACE INTO " + TABLE + " (message_hash, outcome, reason, updated_at) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, key);
                statement.setString(2, result.getOutcome().name());
                statement.setString(3, result.getReason());
                statement.setTimestamp(4, Timestamp.from(result.getTimestamp()));
                statement.executeUpdate();
            }
        }).exceptionally(throwable -> {
            logger.log(Level.WARNING, "Failed to store SQL cache", throwable);
            return null;
        });
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private interface SqlSupplier<T> {
        T get() throws Exception;
    }

    private interface SqlRunnable {
        void run() throws Exception;
    }
}
