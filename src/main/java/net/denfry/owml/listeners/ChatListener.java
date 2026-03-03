package net.denfry.owml.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.managers.PunishmentManager;

import java.util.UUID;

public class ChatListener implements Listener {

    private final PunishmentManager punishmentManager;
    private final OverWatchML plugin;

    public ChatListener(PunishmentManager punishmentManager, OverWatchML plugin) {
        this.punishmentManager = punishmentManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();


        if (punishmentManager.isUntrustedMiner(playerId)) {

            Component originalMessage = event.message();


            Component taggedMessage = Component.text("[Untrusted Miner] ", NamedTextColor.RED).append(originalMessage);


            event.message(taggedMessage);


            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Modified chat format for untrusted miner: " + player.getName());
            }
        }
    }
}
