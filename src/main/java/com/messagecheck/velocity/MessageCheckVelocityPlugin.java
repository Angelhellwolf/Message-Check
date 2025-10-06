package com.messagecheck.velocity;

import com.messagecheck.common.FilterResult;
import com.messagecheck.common.MessageCheckService;
import com.messagecheck.common.MessageContext;
import com.messagecheck.common.config.ConfigLoader;
import com.messagecheck.common.config.MessageCheckConfig;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Plugin(id = "message-check", name = "Message-Check", version = "0.1.0", authors = {"AngelHell"})
public final class MessageCheckVelocityPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private MessageCheckService service;
    private MessageCheckConfig config;
    private VelocitySchedulerAdapter scheduler;
    private VelocityCommandBypassManager bypassManager;
    private java.util.logging.Logger julLogger;

    public MessageCheckVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        scheduler = new VelocitySchedulerAdapter(server, this);
        bypassManager = new VelocityCommandBypassManager(server, this);
        julLogger = new VelocityJavaLogger(logger);
        try {
            initialiseConfig();
        } catch (IOException ex) {
            logger.error("Failed to load configuration", ex);
            return;
        }
        if (service == null) {
            logger.error("Message-Check could not start due to configuration issues.");
            return;
        }
        server.getEventManager().register(this, this);
        registerCommand();
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(config.getBypassPermission())) {
            return;
        }
        String message = event.getMessage();
        if (message == null) {
            message = "";
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        MessageContext context = MessageContext.builder(player.getUniqueId(), player.getUsername(), message)
                .build();
        final String finalMessage = message;

        event.setResult(PlayerChatEvent.ChatResult.denied());
        CompletableFuture<FilterResult> future = service.evaluate(context);
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                logger.error("Failed to evaluate chat message", throwable);
                scheduler.runSync(() -> broadcast(finalMessage, player));
                return;
            }
            if (result.isBlocked()) {
                scheduler.runSync(() -> {
                    player.sendMessage(Component.text("你的消息被系统拦截，请注意言论规范。"));
                    notifyStaff(player, finalMessage, result);
                });
                return;
            }
            scheduler.runSync(() -> {
                broadcast(finalMessage, player);
                if (result.isFlagged()) {
                    notifyStaff(player, finalMessage, result);
                }
            });
        });
    }

    @Subscribe
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getCommandSource();
        UUID uuid = player.getUniqueId();
        if (bypassManager.isBypassing(uuid)) {
            return;
        }
        if (player.hasPermission(config.getBypassPermission())) {
            return;
        }
        String command = event.getCommand();
        String lower = "/" + command;
        if (!service.shouldCheckCommand(lower)) {
            return;
        }
        event.setResult(CommandExecuteEvent.CommandResult.denied());
        MessageContext context = MessageContext.builder(uuid, player.getUsername(), lower)
                .command(true)
                .commandLabel(command.split(" ", 2)[0])
                .build();
        CompletableFuture<FilterResult> future = service.evaluate(context);
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                logger.error("Failed to evaluate command", throwable);
                bypassManager.dispatch(player, command);
                return;
            }
            if (result.isBlocked()) {
                scheduler.runSync(() -> {
                    player.sendMessage(Component.text("该指令已被阻止，原因：" + result.getReason()));
                    notifyStaff(player, lower, result);
                });
                return;
            }
            if (result.isFlagged()) {
                scheduler.runSync(() -> notifyStaff(player, lower, result));
            }
            bypassManager.dispatch(player, command);
        });
    }

    private void broadcast(String message, Player player) {
        Component formatted = Component.text()
                .append(Component.text("<" + player.getUsername() + "> "))
                .append(Component.text(message))
                .build();
        server.getAllPlayers().forEach(target -> target.sendMessage(formatted));
        server.getConsoleCommandSource().sendMessage(formatted);
    }

    private void notifyStaff(Player player, String message, FilterResult result) {
        String format = config.getStaffNotifyFormat()
                .replace("%player%", player.getUsername())
                .replace("%message%", message)
                .replace("%reason%", result.getReason());
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(format);
        server.getAllPlayers().stream()
                .filter(p -> p.hasPermission(config.getNotifyStaffPermission()))
                .forEach(p -> p.sendMessage(component));
        server.getConsoleCommandSource().sendMessage(component);
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (service != null) {
            service.close();
        }
    }

    private void registerCommand() {
        server.getCommandManager().register(server.getCommandManager().metaBuilder("messagecheck").build(), new SimpleCommand() {
            @Override
            public void execute(SimpleCommand.Invocation invocation) {
                if (!(invocation.source() instanceof Player) || invocation.source().hasPermission("messagecheck.admin")) {
                    reloadConfiguration();
                    invocation.source().sendMessage(Component.text("Message-Check 配置已重新加载"));
                } else {
                    invocation.source().sendMessage(Component.text("你没有权限使用此命令。"));
                }
            }
        });
    }

    private void initialiseConfig() throws IOException {
        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }
        Path configPath = dataDirectory.resolve("config.yml");
        if (!Files.exists(configPath)) {
            try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                if (in != null) {
                    Files.copy(in, configPath);
                }
            }
        }
        config = ConfigLoader.load(configPath, julLogger);
        service = new MessageCheckService(config, julLogger);
    }

    private void reloadConfiguration() {
        if (service != null) {
            service.close();
        }
        try {
            initialiseConfig();
        } catch (IOException ex) {
            logger.error("Failed to reload configuration", ex);
        }
    }
}
