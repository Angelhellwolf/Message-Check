package com.messagecheck.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class BukkitSchedulerAdapter {
    private final JavaPlugin plugin;
    private final boolean folia;
    private final Object globalRegionScheduler;
    private final Method globalExecute;
    private final Object asyncScheduler;
    private final Method asyncRunNow;

    public BukkitSchedulerAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
        Object global = null;
        Method execute = null;
        Object async = null;
        Method runNow = null;
        boolean detected = false;
        try {
            Method globalMethod = Bukkit.class.getMethod("getGlobalRegionScheduler");
            global = globalMethod.invoke(null);
            execute = global.getClass().getMethod("execute", Plugin.class, Runnable.class);
            Method asyncMethod = Bukkit.class.getMethod("getAsyncScheduler");
            async = asyncMethod.invoke(null);
            Class<?> asyncSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            runNow = asyncSchedulerClass.getMethod("runNow", Plugin.class, Consumer.class);
            detected = true;
        } catch (Throwable ignored) {
            detected = false;
        }
        this.folia = detected;
        this.globalRegionScheduler = global;
        this.globalExecute = execute;
        this.asyncScheduler = async;
        this.asyncRunNow = runNow;
    }

    public void runSync(Runnable runnable) {
        if (folia && globalRegionScheduler != null && globalExecute != null) {
            try {
                globalExecute.invoke(globalRegionScheduler, plugin, runnable);
                return;
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING, "Folia sync dispatch failed", throwable);
            }
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    public void runAsync(Runnable runnable) {
        if (folia && asyncScheduler != null && asyncRunNow != null) {
            try {
                asyncRunNow.invoke(asyncScheduler, plugin, (Consumer<Object>) task -> runnable.run());
                return;
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING, "Folia async dispatch failed", throwable);
            }
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }
}
