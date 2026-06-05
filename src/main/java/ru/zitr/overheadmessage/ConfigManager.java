package ru.zitr.overheadmessage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final OverHeadMessage plugin;

    private int displayTimeTicks;
    private int fadeInTicks;
    private boolean idleAnimation;
    private int fadeOutTicks;
    private int maxMessageLength;
    private double yOffset;
    private double scale;
    private boolean textShadow;
    private List<String> enabledWorlds;
    private boolean debug;

    // colors
    private String defaultColor;
    private String withExclamationColor;
    private List<String> groupPriority;
    private Map<String, Map<String, String>> groupOverrides;

    // afk
    private boolean afkEnabled;
    private int afkIdleTicks;
    private int afkUpdateIntervalTicks;
    private String afkPlaceholderText;
    private String afkTimerTextFormat;
    private List<String> afkMessages;

    public ConfigManager(OverHeadMessage plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        displayTimeTicks = Math.max(1, c.getInt("display-time-ticks", 120));
        fadeInTicks = Math.max(0, c.getInt("fade-in-ticks", 2));
        idleAnimation = c.getBoolean("idle-animation", true);
        fadeOutTicks = Math.max(0, c.getInt("fade-out-ticks", 4));
        maxMessageLength = Math.max(1, c.getInt("max-message-length", 80));
        yOffset = c.getDouble("y-offset", 0.35);
        scale = c.getDouble("scale", 0.9);
        textShadow = c.getBoolean("text-shadow", true);
        enabledWorlds = c.getStringList("enabled-worlds");
        debug = c.getBoolean("debug", false);

        loadColors(c.getConfigurationSection("colors"));
        loadAfk(c.getConfigurationSection("afk"));

        // Keep fades within total lifetime.
        if (fadeInTicks + fadeOutTicks > displayTimeTicks) {
            fadeOutTicks = Math.max(0, displayTimeTicks - fadeInTicks);
        }
    }

    private void loadColors(ConfigurationSection colors) {
        groupOverrides = new HashMap<>();
        if (colors == null) {
            defaultColor = "#FFFFFF";
            withExclamationColor = "#FF6B6B";
            groupPriority = Collections.emptyList();
            return;
        }
        defaultColor = colors.getString("default", "#FFFFFF");
        withExclamationColor = colors.getString("with_exclamation", "#FF6B6B");
        groupPriority = colors.getStringList("group_priority");

        ConfigurationSection overrides = colors.getConfigurationSection("group_overrides");
        if (overrides != null) {
            for (String group : overrides.getKeys(false)) {
                ConfigurationSection gs = overrides.getConfigurationSection(group);
                if (gs == null) {
                    continue;
                }
                Map<String, String> map = new HashMap<>();
                if (gs.isString("default")) {
                    map.put("default", gs.getString("default"));
                }
                if (gs.isString("with_exclamation")) {
                    map.put("with_exclamation", gs.getString("with_exclamation"));
                }
                groupOverrides.put(group.toLowerCase(), map);
            }
        }
    }

    private void loadAfk(ConfigurationSection afk) {
        if (afk == null) {
            afkEnabled = true;
            afkIdleTicks = 600;
            afkUpdateIntervalTicks = 20;
            afkPlaceholderText = "";
            afkTimerTextFormat = "&7%time%";
            afkMessages = Collections.singletonList("%group_color%AFK");
            return;
        }
        afkEnabled = afk.getBoolean("enabled", true);
        afkIdleTicks = Math.max(1, afk.getInt("idle-ticks", 600));
        afkUpdateIntervalTicks = Math.max(1, afk.getInt("update-interval-ticks", 20));
        afkPlaceholderText = afk.getString("placeholder_text", "");
        afkTimerTextFormat = afk.getString("timer_text_format", "&7%time%");
        afkMessages = afk.getStringList("messages");
        if (afkMessages.isEmpty()) {
            afkMessages = Collections.singletonList("%group_color%AFK");
        }
    }

    public boolean isWorldEnabled(String worldName) {
        return enabledWorlds.isEmpty() || enabledWorlds.contains(worldName);
    }

    public int getDisplayTimeTicks() { return displayTimeTicks; }
    public int getFadeInTicks() { return fadeInTicks; }
    public boolean isIdleAnimation() { return idleAnimation; }
    public int getFadeOutTicks() { return fadeOutTicks; }
    public int getMaxMessageLength() { return maxMessageLength; }
    public double getYOffset() { return yOffset; }
    public double getScale() { return scale; }
    public boolean isTextShadow() { return textShadow; }
    public List<String> getEnabledWorlds() { return enabledWorlds; }
    public boolean isDebug() { return debug; }

    public String getDefaultColor() { return defaultColor; }
    public String getWithExclamationColor() { return withExclamationColor; }
    public List<String> getGroupPriority() { return groupPriority; }
    public Map<String, Map<String, String>> getGroupOverrides() { return groupOverrides; }

    public boolean isAfkEnabled() { return afkEnabled; }
    public int getAfkIdleTicks() { return afkIdleTicks; }
    public int getAfkUpdateIntervalTicks() { return afkUpdateIntervalTicks; }
    public String getAfkPlaceholderText() { return afkPlaceholderText; }
    public String getAfkTimerTextFormat() { return afkTimerTextFormat; }
    public List<String> getAfkMessages() { return afkMessages; }
}
