package ru.zitr.overheadmessage;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI expansion. Only loaded when PlaceholderAPI is installed.
 *
 * Placeholders:
 *   %ohm_afk%        -> the colorized AFK placeholder_text while AFK, else ""
 *   %ohm_afk_time%   -> adaptive AFK time (e.g. "1h 2m") while AFK, else ""
 *   %ohm_afk_state%  -> "true" / "false"
 */
public class OhmPlaceholders extends PlaceholderExpansion {

    private final OverHeadMessage plugin;
    private final AfkManager afk;

    public OhmPlaceholders(OverHeadMessage plugin, AfkManager afk) {
        this.plugin = plugin;
        this.afk = afk;
    }

    @Override
    public String getIdentifier() {
        return "ohm";
    }

    @Override
    public String getAuthor() {
        return "ZiTr_";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        switch (params.toLowerCase()) {
            case "afk":
                return afk.getAfkPlaceholder(player.getUniqueId());
            case "afk_time":
                return afk.getAfkTime(player.getUniqueId());
            case "afk_state":
                return afk.isAfk(player.getUniqueId()) ? "true" : "false";
            default:
                return null;
        }
    }
}
