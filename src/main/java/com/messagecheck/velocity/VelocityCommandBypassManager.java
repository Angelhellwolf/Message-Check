package com.messagecheck.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class VelocityCommandBypassManager {
    private final Set<UUID> bypassing = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ProxyServer server;
    private final Object plugin;

    public VelocityCommandBypassManager(ProxyServer server, Object plugin) {
        this.server = server;
        this.plugin = plugin;
    }

    public boolean isBypassing(UUID uuid) {
        return bypassing.contains(uuid);
    }

    public CompletableFuture<Void> dispatch(Player player, String commandLine) {
        bypassing.add(player.getUniqueId());
        CompletableFuture<Void> future = new CompletableFuture<>();
        server.getCommandManager().executeAsync(player, commandLine)
                .whenComplete((result, throwable) -> {
                    bypassing.remove(player.getUniqueId());
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                    } else {
                        future.complete(null);
                    }
                });
        return future;
    }
}
