package ru.zitr.overheadmessage;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatListener implements Listener {

    private final OverHeadMessage plugin;
    private final ConfigManager config;
    private final OverheadMessageManager manager;
    private final ColorService colors;
    private final AfkManager afkManager;

    public ChatListener(OverHeadMessage plugin, ConfigManager config, OverheadMessageManager manager,
                        ColorService colors, AfkManager afkManager) {
        this.plugin = plugin;
        this.config = config;
        this.manager = manager;
        this.colors = colors;
        this.afkManager = afkManager;
    }

    // Runs FIRST (LOWEST): if the sender is AFK, we must clear AFK before any chat
    // plugin formats the line, otherwise the %ohm_afk% placeholder leaks into chat
    // and AFK looks like it never turned off. So we hold the message: cancel it,
    // clear AFK on the main thread (placeholder becomes empty), then re-send the
    // chat so it goes out cleanly through the normal pipeline.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChatAfkGuard(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        if (!afkManager.isAfk(player.getUniqueId())) {
            return;
        }
        final String message = event.getMessage();
        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            // Clear AFK first so %ohm_afk% / the AFK display are gone...
            afkManager.clearAfk(player);
            // ...then let the message continue (player is no longer AFK on this pass).
            player.chat(message);
        });
    }

    // Chat is async; we only READ here and dispatch entity work to the main thread.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final String raw = event.getMessage();

        if (raw == null || raw.trim().isEmpty()) {
            return;
        }
        if (!config.isWorldEnabled(player.getWorld().getName())) {
            return;
        }

        String body = raw.trim();

        // A leading '!' switches to the "with_exclamation" color and is removed
        // from the text shown above the head.
        final boolean exclamation = body.startsWith("!");
        if (exclamation) {
            body = body.substring(1).trim();
        }
        if (body.isEmpty()) {
            return;
        }
        if (body.length() > config.getMaxMessageLength()) {
            body = body.substring(0, config.getMaxMessageLength());
        }

        final String message = body;
        // Hop back to main thread before touching any entity or resolving color.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            // Chatting counts as activity; clear AFK state (show() will replace the display).
            afkManager.notifyChat(player);
            String hex = colors.resolveHex(player, exclamation);
            String text = ColorService.toLegacyColor(hex)
                    + ChatColor.translateAlternateColorCodes('&', message);
            manager.show(player, text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.remove(event.getPlayer().getUniqueId(), false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        manager.remove(event.getEntity().getUniqueId(), false);
    }
}
