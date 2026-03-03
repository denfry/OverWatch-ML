package net.denfry.owml.gui.modern;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class PunishmentsGUI implements OverWatchGUI {
    private final Inventory inventory;

    public PunishmentsGUI() {
        this.inventory = Bukkit.createInventory(this, 54, "§8Управление наказаниями");
        setupItems();
    }

    private void setupItems() {
        // Pending
        inventory.setItem(0, ItemBuilder.material(Material.PLAYER_HEAD).skull("Notch")
                .name("§cNotch").lore(List.of("§7Score: 95", "§cML Рекомендует: Бан 7 дней")).build());

        // Types
        inventory.setItem(8, ItemBuilder.material(Material.OAK_DOOR).name("§eKick").lore(List.of("§7Кикнуть с сервера")).build());
        inventory.setItem(17, ItemBuilder.material(Material.CLOCK).name("§6Tempban").lore(List.of("§7Временная блокировка")).build());
        inventory.setItem(26, ItemBuilder.material(Material.BARRIER).name("§cBan").lore(List.of("§7Перманентная блокировка")).build());
        inventory.setItem(35, ItemBuilder.material(Material.ARROW).name("§fMute").lore(List.of("§7Блокировка чата")).build());
        inventory.setItem(44, ItemBuilder.material(Material.COMMAND_BLOCK).name("§dCustom Command").lore(List.of("§7Своя команда")).build());

        // History
        for (int i = 45; i <= 52; i++) {
            inventory.setItem(i, ItemBuilder.material(Material.PAPER).name("§7История #" + (i - 44)).build());
        }

        inventory.setItem(53, ItemBuilder.material(Material.ARROW).name("§cНазад").build());
    }

    @Override
    public void open(Player player) {
        player.openInventory(inventory);
        GUIEffects.playOpen(player);
    }

    @Override
    public void close(Player player) {}

    @Override
    public void refresh(Player player) {
        setupItems();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == 53) {
            GUINavigationStack.pop(player);
            return;
        }

        if (slot >= 8 && slot <= 44 && slot % 9 == 8) {
            GUIEffects.playSuccess(player);
            player.sendMessage("§aДействие применено!");
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
