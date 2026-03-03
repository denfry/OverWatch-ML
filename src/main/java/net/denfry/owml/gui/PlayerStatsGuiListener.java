package net.denfry.owml.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import net.denfry.owml.gui.subgui.PlayerMiningStatsGUI;
import net.denfry.owml.utils.UUIDUtils;

import java.lang.reflect.Method;
import java.util.UUID;

public class PlayerStatsGuiListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String titleText = event.getView().title().toString();

        if (titleText.contains("📊 Player Analytics")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            if (!(clicked.getItemMeta() instanceof SkullMeta meta)) return;


            Player player = (Player) event.getWhoClicked();
            if (!player.hasPermission("owml.gui_playerstats")) {
                player.sendMessage(Component.text("You do not have permission to view player stats.").color(NamedTextColor.RED));
                return;
            }

            String displayName = meta.getDisplayName();

            if (displayName == null) return;

            String targetPlayerName = displayName;


            UUID targetPlayerId = UUIDUtils.getUUID(targetPlayerName);
            if (targetPlayerId == null) {
                player.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED));
                return;
            }

            PlayerMiningStatsGUI statsGUI = new PlayerMiningStatsGUI(targetPlayerId, targetPlayerName);
            player.openInventory(statsGUI.getInventory());
        }
    }

}
