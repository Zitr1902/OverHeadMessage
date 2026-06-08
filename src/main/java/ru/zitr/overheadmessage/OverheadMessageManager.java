package ru.zitr.overheadmessage;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks one active overhead display per player. Must be called from the main thread. */
public class OverheadMessageManager {

    private final OverHeadMessage plugin;
    private final ConfigManager config;
    // Public displays (chat / AFK), one per target, seen by everyone.
    private final Map<UUID, OverheadDisplay> active = new ConcurrentHashMap<>();
    // Private shift+right-click name overlays, keyed by VIEWER uuid (one inspect at a time).
    private final Map<UUID, OverheadDisplay> nameOverlays = new ConcurrentHashMap<>();

    public OverheadMessageManager(OverHeadMessage plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void show(Player player, String text) {
        display(player, text, false);
    }

    /** Shows a persistent (AFK) display that stays until explicitly removed. */
    public void showAfk(Player player, String text) {
        display(player, text, true);
    }

    /**
     * Shows a private shift+right-click name overlay: only {@code viewer} sees it,
     * mounted on {@code target} as a second display. The target's public display
     * (chat / AFK) is left intact for everyone else, but hidden from the viewer so
     * they see the name instead; it is revealed to them again once the name ends.
     */
    public void showName(Player target, Player viewer, String text) {
        UUID targetId = target.getUniqueId();
        UUID viewerId = viewer.getUniqueId();

        OverheadDisplay prev = nameOverlays.get(viewerId);
        if (prev != null && !prev.isRemoved()) {
            // One-press lock: this viewer is already inspecting this target.
            if (prev.getTargetId().equals(targetId)) {
                return;
            }
            // Switching to another target: drop the old overlay (restores its public display).
            prev.destroyNow();
        }

        // Hide the target's current public display from this viewer only.
        OverheadDisplay pub = active.get(targetId);
        if (pub != null) {
            pub.hideFrom(viewer);
        }

        OverheadDisplay overlay =
                new OverheadDisplay(plugin, config, this, target, text, false, true, viewerId);
        nameOverlays.put(viewerId, overlay);
        overlay.start();
    }

    /** Live-updates the AFK timer if the current display is the persistent one. */
    public void updateAfk(Player player, String text) {
        OverheadDisplay d = active.get(player.getUniqueId());
        if (d != null && d.isPersistent()) {
            d.updateText(text);
        }
    }

    private void display(Player player, String text, boolean persistent) {
        if (!player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();

        OverheadDisplay display = new OverheadDisplay(plugin, config, this, player, text, persistent, false, null);
        OverheadDisplay old = active.put(id, display);

        if (old != null) {
            // Old message immediately plays its fade-out and removes its TextDisplay,
            // then the new message appears (with its own animations).
            old.fadeOutAndRemove();
            long delay = config.getFadeOutTicks() + 2L;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (active.get(id) == display && player.isOnline()) {
                    display.start();
                }
            }, delay);
        } else {
            display.start();
        }
    }

    /** Removes the tracked display. instant=true skips the fade-out animation. */
    public void remove(UUID id, boolean fade) {
        OverheadDisplay display = active.remove(id);
        if (display == null) {
            return;
        }
        if (fade) {
            display.fadeOutAndRemove();
        } else {
            display.destroyNow();
        }
    }

    /** Called by a public display when it finishes its own lifecycle. */
    void forget(UUID id, OverheadDisplay display) {
        active.remove(id, display);
    }

    /** Called by a name overlay when it ends: drop it and reveal the public display again. */
    void forgetName(OverheadDisplay overlay) {
        UUID viewerId = overlay.getViewerId();
        nameOverlays.remove(viewerId, overlay);
        Player viewer = plugin.getServer().getPlayer(viewerId);
        if (viewer != null) {
            OverheadDisplay pub = active.get(overlay.getTargetId());
            if (pub != null) {
                pub.revealTo(viewer);
            }
        }
    }

    /** Drops any name overlay this player owns (as viewer) or carries (as target). */
    public void removeNameOverlaysFor(UUID playerId) {
        for (OverheadDisplay d : new ArrayList<>(nameOverlays.values())) {
            if (playerId.equals(d.getViewerId()) || playerId.equals(d.getTargetId())) {
                d.destroyNow();
            }
        }
    }

    public void removeAll() {
        for (OverheadDisplay display : active.values()) {
            display.destroyNow();
        }
        active.clear();
        for (OverheadDisplay overlay : new ArrayList<>(nameOverlays.values())) {
            overlay.destroyNow();
        }
        nameOverlays.clear();
    }

    /** True while the player currently has an overhead display (chat / AFK / nick). */
    public boolean hasActive(UUID id) {
        return active.containsKey(id);
    }

    /** True while {@code viewer} already has a name overlay on {@code target} (one-press lock). */
    public boolean hasNameOverlay(UUID targetId, UUID viewerId) {
        OverheadDisplay d = nameOverlays.get(viewerId);
        return d != null && !d.isRemoved() && d.getTargetId().equals(targetId);
    }

    public int activeCount() {
        return active.size();
    }
}
