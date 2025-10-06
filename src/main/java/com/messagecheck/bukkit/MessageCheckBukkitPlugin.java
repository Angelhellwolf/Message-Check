package com.messagecheck.bukkit;

import com.messagecheck.common.MessageCheckService;
import com.messagecheck.common.config.ConfigLoader;
import com.messagecheck.common.config.MessageCheckConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;

public final class MessageCheckBukkitPlugin extends JavaPlugin {
    private MessageCheckService service;
    private BukkitSchedulerAdapter scheduler;
    private CommandBypassManager bypassManager;
    private MessageCheckConfig config;

    @Override
    public void onEnable() {
        saveDefaultConfigFile();
        scheduler = new BukkitSchedulerAdapter(this);
        reloadConfiguration();
        if (service == null) {
            getLogger().severe("Failed to initialise Message-Check. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        bypassManager = new CommandBypassManager(this, scheduler);
        getServer().getPluginManager().registerEvents(new MessageCheckBukkitListener(this, service, scheduler, bypassManager), this);
        Objects.requireNonNull(getCommand("messagecheck"), "messagecheck command not defined").setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (service != null) {
            service.close();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("messagecheck.admin")) {
            sender.sendMessage("§c你没有权限执行此命令。");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§e用法: /" + label + " reload");
            return true;
        }
        reloadConfiguration();
        sender.sendMessage("§aMessage-Check 配置已重新加载。");
        return true;
    }

    private void reloadConfiguration() {
        if (service != null) {
            service.close();
            service = null;
        }
        try {
            Path configPath = getDataFolder().toPath().resolve("config.yml");
            config = ConfigLoader.load(configPath, getLogger());
            service = new MessageCheckService(config, getLogger());
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "无法加载配置: {0}", ex.getMessage());
        }
    }

    private void saveDefaultConfigFile() {
        File folder = getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            getLogger().severe("无法创建插件数据目录: " + folder);
        }
        File configFile = new File(folder, "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
    }

    public MessageCheckService getService() {
        return service;
    }

    public MessageCheckConfig getPluginConfig() {
        return config;
    }
}
