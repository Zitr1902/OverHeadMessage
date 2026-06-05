package ru.zitr.overheadmessage;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks one active overhead display per player. Must be called from the main thread. */
public class OverheadMessageManager {

    private final OverHeadMessage plugin;
    private final ConfigManager config;
    private final Map<UUID, OverheadDisplay> active = new ConcurrentHashMap<>();

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

        OverheadDisplay display = new OverheadDisplay(plugin, config, this, player, text, persistent);
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

    /** Called by a display when it finishes its own lifecycle. */
    void forget(UUID id, OverheadDisplay display) {
        active.remove(id, display);
    }

    public void removeAll() {
        for (OverheadDisplay display : active.values()) {
            display.destroyNow();
        }
        active.clear();
    }

    public int activeCount() {
        return active.size();
    }
}
