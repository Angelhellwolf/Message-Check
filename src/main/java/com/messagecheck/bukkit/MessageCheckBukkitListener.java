package com.messagecheck.bukkit;

import com.messagecheck.common.FilterResult;
import com.messagecheck.common.MessageCheckService;
import com.messagecheck.common.MessageContext;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class MessageCheckBukkitListener implements Listener {
    private final JavaPlugin plugin;
    private final MessageCheckService service;
    private final BukkitSchedulerAdapter scheduler;
    private final CommandBypassManager bypassManager;

    public MessageCheckBukkitListener(JavaPlugin plugin, MessageCheckService service, BukkitSchedulerAdapter scheduler, CommandBypassManager bypassManager) {
        this.plugin = plugin;
        this.service = service;
        this.scheduler = scheduler;
        this.bypassManager = bypassManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(service.getBypassPermission())) {
            return;
        }

        String message = event.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        MessageContext.Builder builder = MessageContext.builder(player.getUniqueId(), player.getName(), message);
        InetSocketAddress address = player.getAddress();
        if (address != null) {
            builder.address(address.getAddress());
        }
        MessageContext context = builder.build();

        event.setCancelled(true);

        CompletableFuture<FilterResult> future = service.evaluate(context);
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to evaluate chat message", throwable);
                scheduler.runSync(() -> broadcastMessage(event, message));
                return;
            }
            if (result.isBlocked()) {
                scheduler.runSync(() -> {
                    player.sendMessage(ChatColor.RED + "你的消息被系统拦截，请注意言论规范。");
                    notifyStaff(player, message, result);
                });
                return;
            }
            scheduler.runSync(() -> {
                broadcastMessage(event, message);
                if (result.isFlagged()) {
                    notifyStaff(player, message, result);
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (bypassManager.isBypassing(player.getUniqueId())) {
            return;
        }
        if (player.hasPermission(service.getBypassPermission())) {
            return;
        }
        String raw = event.getMessage();
        if (!service.shouldCheckCommand(raw)) {
            return;
        }
        event.setCancelled(true);
        String commandLine = raw.startsWith("/") ? raw.substring(1) : raw;
        String[] split = commandLine.split(" ", 2);
        String label = split.length > 0 ? split[0] : commandLine;

        MessageContext.Builder builder = MessageContext.builder(player.getUniqueId(), player.getName(), raw)
                .command(true)
                .commandLabel(label);
        InetSocketAddress address = player.getAddress();
        if (address != null) {
            builder.address(address.getAddress());
        }
        MessageContext context = builder.build();

        CompletableFuture<FilterResult> future = service.evaluate(context);
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to evaluate command", throwable);
                bypassManager.dispatch(player, commandLine);
                return;
            }
            if (result.isBlocked()) {
                scheduler.runSync(() -> {
                    player.sendMessage(ChatColor.RED + "该指令已被阻止，原因：" + result.getReason());
                    notifyStaff(player, raw, result);
                });
                return;
            }
            if (result.isFlagged()) {
                scheduler.runSync(() -> notifyStaff(player, raw, result));
            }
            bypassManager.dispatch(player, commandLine);
        });
    }

    private void broadcastMessage(AsyncPlayerChatEvent event, String message) {
        Player player = event.getPlayer();
        String format = event.getFormat();
        String formatted = String.format(format, player.getDisplayName(), message);
        List<Player> recipients = new ArrayList<>(event.getRecipients());
        for (Player recipient : recipients) {
            recipient.sendMessage(formatted);
        }
        CommandSender console = Bukkit.getConsoleSender();
        console.sendMessage(formatted);
    }

    private void notifyStaff(Player player, String message, FilterResult result) {
        String format = ChatColor.translateAlternateColorCodes('&', service.getConfig().getStaffNotifyFormat());
        String rendered = format
                .replace("%player%", player.getName())
                .replace("%message%", message)
                .replace("%reason%", result.getReason());
        String permission = service.getConfig().getNotifyStaffPermission();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(permission)) {
                online.sendMessage(rendered);
            }
        }
        Bukkit.getConsoleSender().sendMessage(rendered);
    }
}
