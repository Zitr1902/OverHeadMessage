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
