package com.messagecheck.common;

import java.time.Instant;
import java.util.Objects;

public final class FilterResult {
    private final FilterOutcome outcome;
    private final String reason;
    private final Instant timestamp;

    public FilterResult(FilterOutcome outcome, String reason, Instant timestamp) {
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.reason = reason == null ? "" : reason;
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public static FilterResult allow() {
        return new FilterResult(FilterOutcome.ALLOW, "", Instant.now());
    }

    public static FilterResult block(String reason) {
        return new FilterResult(FilterOutcome.BLOCK, reason, Instant.now());
    }

    public static FilterResult flag(String reason) {
        return new FilterResult(FilterOutcome.FLAG, reason, Instant.now());
    }

    public FilterOutcome getOutcome() {
        return outcome;
    }

    public String getReason() {
        return reason;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public boolean isBlocked() {
        return outcome == FilterOutcome.BLOCK;
    }

    public boolean isFlagged() {
        return outcome == FilterOutcome.FLAG;
    }

    public boolean isAllowed() {
        return outcome == FilterOutcome.ALLOW;
    }
}
