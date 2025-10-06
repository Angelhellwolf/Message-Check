package com.messagecheck.common.storage;

import com.messagecheck.common.FilterResult;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class NoOpDecisionStore implements DecisionStore {
    @Override
    public CompletableFuture<Optional<FilterResult>> find(String key) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Void> save(String key, FilterResult result) {
        return CompletableFuture.completedFuture(null);
    }
}
