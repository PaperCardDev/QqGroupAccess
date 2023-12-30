package cn.paper_card.qq_group_access;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

class OnQuit implements Listener {

    private final @NotNull ConcurrentHashMap<Integer, UUID> groupSyncMessages;
    private final @NotNull Logger logger;


    OnQuit(@NotNull ThePlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.groupSyncMessages = plugin.getGroupSyncMessages();
        this.logger = plugin.getSLF4JLogger();
    }

    @EventHandler
    public void on(@NotNull PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final UUID uniqueId = player.getUniqueId();

        int c = 0;
        for (Integer integer : this.groupSyncMessages.keySet()) {
            final UUID uuid = this.groupSyncMessages.get(integer);
            if (uniqueId.equals(uuid)) {
                this.groupSyncMessages.remove(integer);
                ++c;
            }
        }

        this.logger.info("移除了玩家 %s 的 %d 条群同步消息".formatted(player.getName(), c));
    }
}
