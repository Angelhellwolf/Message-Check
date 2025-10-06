package com.messagecheck.common;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class MessageContext {
    private final UUID playerId;
    private final String playerName;
    private final String rawMessage;
    private final boolean command;
    private final String commandLabel;
    private final InetAddress address;
    private final Instant createdAt;

    private MessageContext(Builder builder) {
        this.playerId = builder.playerId;
        this.playerName = builder.playerName;
        this.rawMessage = builder.rawMessage;
        this.command = builder.command;
        this.commandLabel = builder.commandLabel;
        this.address = builder.address;
        this.createdAt = builder.createdAt == null ? Instant.now() : builder.createdAt;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public boolean isCommand() {
        return command;
    }

    public String getCommandLabel() {
        return commandLabel;
    }

    public InetAddress getAddress() {
        return address;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static Builder builder(UUID playerId, String playerName, String rawMessage) {
        return new Builder(playerId, playerName, rawMessage);
    }

    public static final class Builder {
        private final UUID playerId;
        private final String playerName;
        private final String rawMessage;
        private boolean command;
        private String commandLabel;
        private InetAddress address;
        private Instant createdAt;

        private Builder(UUID playerId, String playerName, String rawMessage) {
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.playerName = Objects.requireNonNull(playerName, "playerName");
            this.rawMessage = Objects.requireNonNull(rawMessage, "rawMessage");
        }

        public Builder command(boolean command) {
            this.command = command;
            return this;
        }

        public Builder commandLabel(String commandLabel) {
            this.commandLabel = commandLabel;
            return this;
        }

        public Builder address(InetAddress address) {
            this.address = address;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public MessageContext build() {
            return new MessageContext(this);
        }
    }
}
