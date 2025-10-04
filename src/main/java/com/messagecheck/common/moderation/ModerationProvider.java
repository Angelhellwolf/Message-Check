package com.messagecheck.common.moderation;

import com.messagecheck.common.FilterResult;
import com.messagecheck.common.MessageContext;

import java.util.concurrent.CompletableFuture;

public interface ModerationProvider extends AutoCloseable {
    CompletableFuture<FilterResult> evaluate(MessageContext context);

    @Override
    default void close() throws Exception {
    }
}
