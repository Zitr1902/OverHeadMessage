package ru.zitr.overheadmessage;

import org.bukkit.plugin.java.JavaPlugin;

public final class OverHeadMessage extends JavaPlugin {

    private ConfigManager configManager;
    private OverheadMessageManager messageManager;
    private ColorService colorService;
    private AfkManager afkManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.messageManager = new OverheadMessageManager(this, configManager);

        // LuckPermsHook is only loaded if LuckPerms is actually installed, so the
        // plugin runs fine without it (colors just fall back to the config defaults).
        LuckPermsHook luckPerms = null;
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            luckPerms = LuckPermsHook.tryHook(this);
            getLogger().info(luckPerms != null
                    ? "Hooked into LuckPerms for per-group colors."
                    : "LuckPerms found but could not be hooked; using default colors.");
        } else {
            getLogger().severe("==================================================================");
            getLogger().severe(" LuckPerms is NOT installed!");
            getLogger().severe(" Per-group colors (group_overrides) will NOT work.");
            getLogger().severe(" Download: https://luckperms.net/download");
            getLogger().severe("==================================================================");
        }
        this.colorService = new ColorService(configManager, luckPerms);
        this.afkManager = new AfkManager(this, configManager, colorService, messageManager);

        getServer().getPluginManager().registerEvents(
                new ChatListener(this, configManager, messageManager, colorService, afkManager), this);
        getServer().getPluginManager().registerEvents(afkManager, this);
        afkManager.start();

        // PlaceholderAPI expansion (only touched if PlaceholderAPI is installed),
        // exposing %ohm_afk%, %ohm_afk_time%, %ohm_afk_state% for use anywhere
        // (e.g. a tab plugin).
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new OhmPlaceholders(this, afkManager).register();
                getLogger().info("Hooked into PlaceholderAPI (%ohm_afk%, %ohm_afk_time%).");
            } catch (Throwable t) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
            }
        } else {
            getLogger().severe("==================================================================");
            getLogger().severe(" PlaceholderAPI is NOT installed!");
            getLogger().severe(" Placeholders %ohm_afk%, %ohm_afk_time%, %ohm_afk_state% won't work.");
            getLogger().severe(" Download: https://www.spigotmc.org/resources/placeholderapi.6245/");
            getLogger().severe("==================================================================");
        }

        OhmCommand command = new OhmCommand(this, configManager, messageManager, colorService);
        if (getCommand("ohm") != null) {
            getCommand("ohm").setExecutor(command);
            getCommand("ohm").setTabCompleter(command);
        }

        getLogger().info("OverHeadMessage enabled. (by ZiTr_)");
    }

    @Override
    public void onDisable() {
        if (afkManager != null) {
            afkManager.stop();
        }
        if (messageManager != null) {
            messageManager.removeAll();
        }
        getLogger().info("OverHeadMessage disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public OverheadMessageManager getMessageManager() {
        return messageManager;
    }

    public ColorService getColorService() {
        return colorService;
    }

    public AfkManager getAfkManager() {
        return afkManager;
    }
}
