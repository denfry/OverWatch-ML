package net.denfry.owml.managers;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import java.util.UUID;

public interface IDecoyService {
    void addPlayerPlacedOre(Location location);
    void removePlayerPlacedOre(Location location);
    boolean isPlayerPlacedOre(Location location);
    
    void trackOreBreak(Player player, Block block, Material ore);
    boolean isDecoy(Location location);
    void removeDecoy(Location location);
    
    void cleanup();
}
