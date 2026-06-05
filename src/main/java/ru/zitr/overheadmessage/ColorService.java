package ru.zitr.overheadmessage;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves the hex color for a message, honoring LuckPerms group overrides. */
public class ColorService {

    private static final Pattern HEX = Pattern.compile("#([0-9a-fA-F]{6})");

    private final ConfigManager config;
    private final LuckPermsHook luckPerms; // may be null

    public ColorService(ConfigManager config, LuckPermsHook luckPerms) {
        this.config = config;
        this.luckPerms = luckPerms;
    }

    public boolean isLuckPermsHooked() {
        return luckPerms != null;
    }

    /** Hex color (#RRGGBB) to use for this player/message. */
    public String resolveHex(Player player, boolean exclamation) {
        String key = exclamation ? "with_exclamation" : "default";

        if (luckPerms != null) {
            String group = luckPerms.getPrimaryGroup(player, config.getGroupPriority());
            if (group != null) {
                Map<String, String> override = config.getGroupOverrides().get(group.toLowerCase());
                if (override != null) {
                    String hex = override.get(key);
                    if (hex != null && !hex.isEmpty()) {
                        return hex;
                    }
                }
            }
        }
        return exclamation ? config.getWithExclamationColor() : config.getDefaultColor();
    }

    /** Converts a #RRGGBB hex string into a legacy section color prefix. */
    public static String toLegacyColor(String hex) {
        try {
            return ChatColor.of(hex).toString();
        } catch (Throwable t) {
            return ChatColor.WHITE.toString();
        }
    }

    /** Applies inline hex (#RRGGBB) tokens and legacy '&' codes to a string. */
    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        Matcher m = HEX.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(toLegacyColor(m.group())));
        }
        m.appendTail(sb);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}
