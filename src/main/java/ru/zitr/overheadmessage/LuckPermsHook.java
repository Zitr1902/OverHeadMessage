package ru.zitr.overheadmessage;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Thin LuckPerms wrapper. This class is only ever loaded when the LuckPerms
 * plugin is present (see OverHeadMessage#onEnable), so referencing the LP API
 * here is safe; without LuckPerms the class is never touched.
 */
public class LuckPermsHook {

    private final LuckPerms api;

    private LuckPermsHook(LuckPerms api) {
        this.api = api;
    }

    /** Returns a working hook, or null if LuckPerms is not available. */
    public static LuckPermsHook tryHook(OverHeadMessage plugin) {
        try {
            RegisteredServiceProvider<LuckPerms> rsp =
                    plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
            if (rsp != null) {
                return new LuckPermsHook(rsp.getProvider());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Returns the first group from {@code priority} the player belongs to
     * (directly assigned groups + primary group), or null if none match.
     */
    public String getPrimaryGroup(Player player, List<String> priority) {
        if (priority == null || priority.isEmpty()) {
            return null;
        }
        try {
            User user = api.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                return null;
            }
            Set<String> groups = new HashSet<>();
            for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
                groups.add(node.getGroupName().toLowerCase());
            }
            String primary = user.getPrimaryGroup();
            if (primary != null) {
                groups.add(primary.toLowerCase());
            }
            for (String g : priority) {
                if (groups.contains(g.toLowerCase())) {
                    return g;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
