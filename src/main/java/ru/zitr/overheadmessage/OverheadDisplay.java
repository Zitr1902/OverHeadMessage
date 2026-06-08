package ru.zitr.overheadmessage;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * A single overhead TextDisplay riding the player as a passenger.
 * Fade-in: scale grows from ~0 to target.
 * Fade-out: flies upward while shrinking, then is removed.
 * Main-thread only.
 */
public class OverheadDisplay {

    private static final float START_SCALE = 0.01f;   // near-zero so growth is visible
    private static final float FLY_UP = 0.8f;         // how high the message flies while fading out

    private final OverHeadMessage plugin;
    private final ConfigManager config;
    private final OverheadMessageManager manager;
    private final Player player;
    private volatile String text;
    private final UUID playerId;
    private final boolean persistent;
    private final boolean nameDisplay;
    // If set, only this player may see the display (shift+right-click name); null = everyone.
    private final UUID viewerId;

    private TextDisplay entity;
    private BukkitRunnable task;

    private int ticks = 0;
    private boolean started = false;
    private boolean fadingOut = false;
    private boolean removed = false;

    private final float targetScale;
    private final float baseY;

    public OverheadDisplay(OverHeadMessage plugin, ConfigManager config, OverheadMessageManager manager,
                           Player player, String text, boolean persistent, boolean nameDisplay, UUID viewerId) {
        this.plugin = plugin;
        this.config = config;
        this.manager = manager;
        this.player = player;
        this.text = text;
        this.persistent = persistent;
        this.nameDisplay = nameDisplay;
        this.viewerId = viewerId;
        this.playerId = player.getUniqueId();
        this.targetScale = (float) config.getScale();
        this.baseY = (float) config.getYOffset();
    }

    public boolean isPersistent() {
        return persistent;
    }

    /** True if this display is a shift+right-click name (has the one-press lock). */
    public boolean isNameDisplay() {
        return nameDisplay;
    }

    UUID getViewerId() {
        return viewerId;
    }

    UUID getTargetId() {
        return playerId;
    }

    boolean isRemoved() {
        return removed;
    }

    /** Reveal this (public) display to a specific viewer again. */
    void revealTo(Player viewer) {
        if (entity != null && entity.isValid()) {
            try { viewer.showEntity(plugin, entity); } catch (Throwable ignored) {}
        }
    }

    /** Hide this (public) display from a specific viewer (they see the private name instead). */
    void hideFrom(Player viewer) {
        if (entity != null && entity.isValid()) {
            try { viewer.hideEntity(plugin, entity); } catch (Throwable ignored) {}
        }
    }

    /** Live-updates the displayed text (used by the AFK timer). */
    public void updateText(String newText) {
        this.text = newText;
        if (entity != null && entity.isValid() && !fadingOut && !removed) {
            try {
                entity.setText(newText);
            } catch (Throwable ignored) {
            }
        }
    }

    public void start() {
        if (started || removed) {
            return;
        }
        started = true;
        if (!player.isOnline()) {
            destroyNow();
            return;
        }
        try {
            // Spawn already at head height so there is no one-frame flash near the
            // feet before the entity becomes a passenger.
            org.bukkit.Location spawnLoc = player.getEyeLocation().add(0, baseY, 0);
            entity = player.getWorld().spawn(spawnLoc, TextDisplay.class, this::configure);
            player.addPassenger(entity);
            applyViewer();
        } catch (Throwable t) {
            if (config.isDebug()) {
                plugin.getLogger().warning("Failed to spawn TextDisplay: " + t.getMessage());
            }
            destroyNow();
            return;
        }

        // Spawned tiny (START_SCALE). One tick later, grow to full size so the
        // client interpolates the appearance (scale up).
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!removed && !fadingOut) {
                applyTransform(targetScale, baseY, config.getFadeInTicks());
            }
        }, 1L);

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, 2L, 1L);
    }

    private void tick() {
        if (removed) {
            cancelTask();
            return;
        }
        if (entity == null || entity.isDead() || !entity.isValid() || !player.isOnline()) {
            destroyNow();
            return;
        }
        ticks++;

        int life = config.getDisplayTimeTicks();
        int fadeOut = config.getFadeOutTicks();

        // Persistent (AFK) displays never auto-expire; they live until removed.
        if (!persistent && !fadingOut && ticks >= life - fadeOut) {
            fadeOutAndRemove();
            return;
        }

        // Gentle idle bob after the fade-in (and, for timed displays, before fade-out).
        if (config.isIdleAnimation() && !fadingOut && ticks > config.getFadeInTicks()
                && (persistent || ticks < life - fadeOut)) {
            double phase = ticks / 10.0;
            float bob = (float) (Math.sin(phase) * 0.03);
            applyTransform(targetScale, baseY + bob, 2);
        }
    }

    /** Plays the fly-up + shrink fade-out, then removes the entity. */
    public void fadeOutAndRemove() {
        if (removed) {
            return;
        }
        if (!started || entity == null || !entity.isValid()) {
            destroyNow();
            return;
        }
        if (fadingOut) {
            return;
        }
        fadingOut = true;
        int fadeOut = Math.max(1, config.getFadeOutTicks());

        // Fly upward while shrinking.
        applyTransform(START_SCALE, baseY + FLY_UP, fadeOut);

        plugin.getServer().getScheduler().runTaskLater(plugin, this::destroyNow, fadeOut + 1L);
    }

    public void destroyNow() {
        if (removed) {
            return;
        }
        removed = true;
        cancelTask();
        try {
            if (entity != null && entity.isValid()) {
                if (player.isOnline()) {
                    try { player.removePassenger(entity); } catch (Throwable ignored) {}
                }
                entity.remove();
            }
        } catch (Throwable ignored) {
        } finally {
            entity = null;
            if (nameDisplay) {
                manager.forgetName(this);
            } else {
                manager.forget(playerId, this);
            }
        }
    }

    // ---- TextDisplay configuration ----

    private void configure(TextDisplay d) {
        d.setText(text);
        d.setBillboard(Display.Billboard.CENTER);
        d.setPersistent(false);
        // Private (shift+right-click name) display: hidden for everyone by default,
        // so it stays hidden from players who join while it is shown too.
        if (viewerId != null) {
            try { d.setVisibleByDefault(false); } catch (Throwable ignored) {}
        }
        // Background and see-through are fixed defaults (not configurable).
        trySetSeeThrough(d);
        trySetShadowed(d, config.isTextShadow());
        trySetTransparentBackground(d);
        trySetAlignment(d);

        // Start collapsed so the fade-in (scale up) has somewhere to grow from.
        Quaternionf identity = new Quaternionf();
        d.setTransformation(new Transformation(
                new Vector3f(0f, baseY, 0f),
                identity,
                new Vector3f(START_SCALE, START_SCALE, START_SCALE),
                new Quaternionf()));
        try {
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(0);
        } catch (Throwable ignored) {}
        try {
            d.setViewRange(64f);
        } catch (Throwable ignored) {}
    }

    /** For a private name display, reveal the entity only to the viewer who clicked. */
    private void applyViewer() {
        if (viewerId == null || entity == null) {
            return;
        }
        Player viewer = plugin.getServer().getPlayer(viewerId);
        if (viewer != null) {
            try { viewer.showEntity(plugin, entity); } catch (Throwable ignored) {}
        }
    }

    private void applyTransform(float scale, float y, int interpolationTicks) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        try {
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(Math.max(0, interpolationTicks));
            entity.setTransformation(new Transformation(
                    new Vector3f(0f, y, 0f),
                    new Quaternionf(),
                    new Vector3f(scale, scale, scale),
                    new Quaternionf()));
        } catch (Throwable ignored) {
        }
    }

    private void trySetSeeThrough(TextDisplay d) {
        try { d.setSeeThrough(false); } catch (Throwable ignored) {}
    }

    private void trySetShadowed(TextDisplay d, boolean v) {
        try { d.setShadowed(v); } catch (Throwable ignored) {}
    }

    private void trySetAlignment(TextDisplay d) {
        try { d.setAlignment(TextDisplay.TextAlignment.CENTER); } catch (Throwable ignored) {}
    }

    /** Background is disabled by default: fully transparent. */
    private void trySetTransparentBackground(TextDisplay d) {
        try {
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        } catch (Throwable ignored) {
        }
    }

    private void cancelTask() {
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignored) {}
            task = null;
        }
    }
}
