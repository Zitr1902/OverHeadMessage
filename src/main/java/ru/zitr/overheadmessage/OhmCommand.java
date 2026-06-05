package ru.zitr.overheadmessage;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class OhmCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "overheadmessage.admin";

    private final OverHeadMessage plugin;
    private final ConfigManager config;
    private final OverheadMessageManager manager;
    private final ColorService colors;

    public OhmCommand(OverHeadMessage plugin, ConfigManager config,
                      OverheadMessageManager manager, ColorService colors) {
        this.plugin = plugin;
        this.config = config;
        this.manager = manager;
        this.colors = colors;
    }

    private String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(c("&cNo permission."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(c("&e/ohm reload &7- reload config"));
            sender.sendMessage(c("&e/ohm status &7- show settings"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                config.reload();
                plugin.getAfkManager().start();
                sender.sendMessage(c("&aOverHeadMessage config reloaded."));
                return true;
            case "status":
                sendStatus(sender);
                return true;
            default:
                sender.sendMessage(c("&cUnknown subcommand. Use reload or status."));
                return true;
        }
    }

    private void sendStatus(CommandSender s) {
        s.sendMessage(c("&6=== OverHeadMessage &7(by ZiTr_) &6==="));
        s.sendMessage(c("&7active displays: &f" + manager.activeCount()));
        s.sendMessage(c("&7display-time-ticks: &f" + config.getDisplayTimeTicks()));
        s.sendMessage(c("&7fade-in / fade-out: &f" + config.getFadeInTicks() + " / " + config.getFadeOutTicks()));
        s.sendMessage(c("&7idle-animation: &f" + config.isIdleAnimation()));
        s.sendMessage(c("&7max-message-length: &f" + config.getMaxMessageLength()));
        s.sendMessage(c("&7y-offset / scale: &f" + config.getYOffset() + " / " + config.getScale()));
        s.sendMessage(c("&7text-shadow: &f" + config.isTextShadow()));
        s.sendMessage(c("&7colors: &f" + config.getDefaultColor()
                + " &7/ ! &f" + config.getWithExclamationColor()));
        s.sendMessage(c("&7luckperms: &f" + (colors.isLuckPermsHooked() ? "hooked" : "not found")));
        s.sendMessage(c("&7afk: &f" + (config.isAfkEnabled() ? "on" : "off")
                + " &7idle: &f" + config.getAfkIdleTicks() + "t"
                + " &7update: &f" + config.getAfkUpdateIntervalTicks() + "t"));
        s.sendMessage(c("&7enabled-worlds: &f"
                + (config.getEnabledWorlds().isEmpty() ? "ALL" : config.getEnabledWorlds())));
        s.sendMessage(c("&7debug: &f" + config.isDebug()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String sub : Arrays.asList("reload", "status")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
            return out;
        }
        return Collections.emptyList();
    }
}
