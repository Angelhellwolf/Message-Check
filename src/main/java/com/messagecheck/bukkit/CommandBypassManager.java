package com.messagecheck.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CommandBypassManager {
    private final Set<UUID> bypassing = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final JavaPlugin plugin;
    private final BukkitSchedulerAdapter scheduler;

    public CommandBypassManager(JavaPlugin plugin, BukkitSchedulerAdapter scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    public boolean isBypassing(UUID uuid) {
        return bypassing.contains(uuid);
    }

    public void dispatch(Player player, String commandLine) {
        scheduler.runSync(() -> {
            bypassing.add(player.getUniqueId());
            try {
                player.performCommand(commandLine);
            } finally {
                bypassing.remove(player.getUniqueId());
            }
        });
    }
}
