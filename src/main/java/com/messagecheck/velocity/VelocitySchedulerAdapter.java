package com.messagecheck.velocity;

import com.velocitypowered.api.proxy.ProxyServer;

import java.util.concurrent.TimeUnit;

public final class VelocitySchedulerAdapter {
    private final ProxyServer server;
    private final Object plugin;

    public VelocitySchedulerAdapter(ProxyServer server, Object plugin) {
        this.server = server;
        this.plugin = plugin;
    }

    public void runSync(Runnable runnable) {
        server.getScheduler().buildTask(plugin, runnable).schedule();
    }

    public void runLater(Runnable runnable, long delay, TimeUnit unit) {
        server.getScheduler().buildTask(plugin, runnable).delay(delay, unit).schedule();
    }
}
