package com.messagecheck.common.storage;

import com.messagecheck.common.FilterResult;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface DecisionStore extends AutoCloseable {
    CompletableFuture<Optional<FilterResult>> find(String key);

    CompletableFuture<Void> save(String key, FilterResult result);

    @Override
    default void close() throws Exception {
    }
}
