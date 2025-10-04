package com.messagecheck.common.storage;

import com.messagecheck.common.FilterResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CompositeDecisionStore implements DecisionStore {
    private final List<DecisionStore> stores;

    public CompositeDecisionStore(List<DecisionStore> stores) {
        this.stores = new ArrayList<>(stores);
    }

    @Override
    public CompletableFuture<Optional<FilterResult>> find(String key) {
        return findRecursive(0, key, Optional.empty());
    }

    private CompletableFuture<Optional<FilterResult>> findRecursive(int index, String key, Optional<FilterResult> current) {
        if (current.isPresent() || index >= stores.size()) {
            return CompletableFuture.completedFuture(current);
        }
        return stores.get(index).find(key).thenCompose(result -> {
            if (result.isPresent()) {
                return CompletableFuture.completedFuture(result);
            }
            return findRecursive(index + 1, key, current);
        });
    }

    @Override
    public CompletableFuture<Void> save(String key, FilterResult result) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (DecisionStore store : stores) {
            future = future.thenCompose(ignored -> store.save(key, result));
        }
        return future;
    }

    @Override
    public void close() throws Exception {
        for (DecisionStore store : stores) {
            store.close();
        }
    }
}
