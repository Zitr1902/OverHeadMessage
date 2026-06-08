package ru.zitr.overheadmessage;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

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

    // Shift + right-click on another player shows that player's name above their
    // head. While a display is already up for the target, re-triggering is ignored
    // until it disappears.
    @EventHandler(ignoreCancelled = true)
    public void onShiftRightClick(PlayerInteractEntityEvent event) {
        // PlayerInteractAtEntityEvent is a subclass and fires alongside the base
        // event; skip it so the click is handled exactly once.
        if (event instanceof PlayerInteractAtEntityEvent) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // ignore the off-hand pass
        }
        Player clicker = event.getPlayer();
        if (!clicker.isSneaking()) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        String format = config.getShiftRightClickNameFormat();
        if (format == null || format.isEmpty()) {
            return;
        }
        if (!config.isWorldEnabled(target.getWorld().getName())) {
            return;
        }
        // One-press lock: while this clicker already has a name overlay on the target,
        // re-clicking does nothing until it fades on its own.
        if (manager.hasNameOverlay(target.getUniqueId(), clicker.getUniqueId())) {
            return;
        }
        String groupColor = ColorService.toLegacyColor(colors.resolveHex(target, true));
        final String text = ColorService.colorize(format
                .replace("%group_color%", groupColor)
                .replace("%name%", target.getName()));
        // Defer to the next tick (like chat). Spawning the TextDisplay and mounting
        // it as a passenger from inside the interact event itself can fail to take,
        // leaving the text floating at the spawn point with no fade-in animation.
        // showName adds a PRIVATE overlay seen only by the clicker; the target's
        // public chat / AFK display stays for everyone else, just hidden from the
        // clicker until the name ends.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!target.isOnline() || !clicker.isOnline()
                    || manager.hasNameOverlay(target.getUniqueId(), clicker.getUniqueId())) {
                return;
            }
            manager.showName(target, clicker, text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        manager.remove(id, false);
        manager.removeNameOverlaysFor(id);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        manager.remove(id, false);
        manager.removeNameOverlaysFor(id);
    }
}
