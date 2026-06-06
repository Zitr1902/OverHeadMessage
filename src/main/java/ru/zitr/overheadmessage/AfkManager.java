package ru.zitr.overheadmessage;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects idle players and shows an AFK message above their head:
 * line 1 = a random phrase, line 2 = a live timer. Any activity clears it.
 * All state is touched on the main thread only.
 */
public class AfkManager implements Listener {

    private final OverHeadMessage plugin;
    private final ConfigManager config;
    private final ColorService colors;
    private final OverheadMessageManager manager;

    private final Map<UUID, Long> lastActive = new HashMap<>();
    // Read from the async chat thread (isAfk), written on the main thread, so it must be concurrent.
    private final Set<UUID> afk = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> afkSince = new HashMap<>();
    private final Map<UUID, String> afkMessage = new HashMap<>();
    private final Random random = new Random();

    private BukkitTask task;

    public AfkManager(OverHeadMessage plugin, ConfigManager config,
                      ColorService colors, OverheadMessageManager manager) {
        this.plugin = plugin;
        this.config = config;
        this.colors = colors;
        this.manager = manager;
    }

    public void start() {
        stop();
        if (!config.isAfkEnabled()) {
            return;
        }
        int interval = Math.max(1, config.getAfkUpdateIntervalTicks());
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignored) {}
            task = null;
        }
        lastActive.clear();
        afk.clear();
        afkSince.clear();
        afkMessage.clear();
    }

    private void tick() {
        if (!config.isAfkEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long thresholdMs = config.getAfkIdleTicks() * 50L;

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Long last = lastActive.get(id);
            if (last == null) {
                lastActive.put(id, now);
                continue;
            }
            if (!afk.contains(id)) {
                if (now - last >= thresholdMs && config.isWorldEnabled(p.getWorld().getName())) {
                    enterAfk(p, now);
                }
            } else {
                long elapsed = now - afkSince.getOrDefault(id, now);
                manager.updateAfk(p, buildText(afkMessage.get(id), elapsed));
            }
        }
    }

    private void enterAfk(Player p, long now) {
        UUID id = p.getUniqueId();
        afk.add(id);
        afkSince.put(id, now);

        List<String> messages = config.getAfkMessages();
        String raw = messages.get(random.nextInt(messages.size()));
        // %group_color% uses the group's "with_exclamation" color.
        String groupColor = ColorService.toLegacyColor(colors.resolveHex(p, true));
        String line = raw.replace("%group_color%", groupColor);
        afkMessage.put(id, line);

        manager.showAfk(p, buildText(line, 0L));
    }

    /** Overhead text: phrase line + timer line (placeholder goes to tab, not here). */
    private String buildText(String messageLine, long elapsedMs) {
        return ColorService.colorize(messageLine == null ? "" : messageLine)
                + '\n' + buildTimer(elapsedMs);
    }

    private String buildTimer(long elapsedMs) {
        String wrapper = config.getAfkTimerTextFormat().replace("%time%", formatTime(elapsedMs));
        return ColorService.colorize(wrapper);
    }

    /** Adaptive time: seconds -> minutes -> hours+minutes (e.g. 30s, 1m, 1h 2m). */
    private String formatTime(long elapsedMs) {
        long totalSec = Math.max(0, elapsedMs / 1000L);
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;

        if (h > 0) {
            return h + "h " + m + "m";
        } else if (m > 0) {
            return m + "m";
        }
        return s + "s";
    }

    // ---- accessors for PlaceholderAPI (%ohm_afk%, %ohm_afk_time%, %ohm_afk_state%) ----

    public boolean isAfk(UUID id) {
        return afk.contains(id);
    }

    /** The (colorized) placeholder_text while AFK, empty otherwise. */
    public String getAfkPlaceholder(UUID id) {
        if (!afk.contains(id)) {
            return "";
        }
        return ColorService.colorize(config.getAfkPlaceholderText());
    }

    /** Adaptive AFK time string (e.g. "1h 2m") while AFK, empty otherwise. */
    public String getAfkTime(UUID id) {
        if (!afk.contains(id)) {
            return "";
        }
        long elapsed = System.currentTimeMillis() - afkSince.getOrDefault(id, System.currentTimeMillis());
        return formatTime(elapsed);
    }

    // ---- activity tracking ----

    /** Marks the player active. Called on the main thread. */
    private void markActive(Player p, boolean removeDisplay) {
        UUID id = p.getUniqueId();
        lastActive.put(id, System.currentTimeMillis());
        if (afk.remove(id)) {
            afkSince.remove(id);
            afkMessage.remove(id);
            if (removeDisplay) {
                manager.remove(id, true);
            }
        }
    }

    /** Called from the chat listener: clears AFK but lets show() replace the display. */
    public void notifyChat(Player p) {
        markActive(p, false);
    }

    /**
     * Fully clears AFK and removes the AFK overhead display. Used to hold an AFK
     * player's chat message: AFK is cleared first (so %ohm_afk% becomes empty)
     * before the message is re-sent into chat. Main thread only.
     */
    public void clearAfk(Player p) {
        markActive(p, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        markActive(event.getPlayer(), true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        markActive(event.getPlayer(), true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        markActive(event.getPlayer(), true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        markActive(event.getPlayer(), true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        markActive(event.getPlayer(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastActive.remove(id);
        afk.remove(id);
        afkSince.remove(id);
        afkMessage.remove(id);
    }
}
