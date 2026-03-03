package net.denfry.owml.managers;

import net.denfry.owml.storage.IStatsRepository;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class StatsServiceTest {

    private IStatsRepository repository;
    private StatsService statsService;
    private Set<Material> trackedOres;

    @BeforeEach
    void setUp() {
        repository = mock(IStatsRepository.class);
        trackedOres = new HashSet<>(Arrays.asList(Material.DIAMOND_ORE, Material.GOLD_ORE));
        statsService = new StatsService(repository, trackedOres);
    }

    @Test
    void addOreMined_ShouldIncreaseCount_WhenOreIsTracked() {
        UUID playerId = UUID.randomUUID();
        
        statsService.addOreMined(playerId, Material.DIAMOND_ORE);
        statsService.addOreMined(playerId, Material.DIAMOND_ORE);
        statsService.addOreMined(playerId, Material.GOLD_ORE);
        
        Map<Material, Integer> stats = statsService.getOreStats(playerId);
        
        assertEquals(2, stats.get(Material.DIAMOND_ORE));
        assertEquals(1, stats.get(Material.GOLD_ORE));
        assertEquals(3, statsService.getTotalOresMined(playerId));
    }

    @Test
    void addOreMined_ShouldNotIncreaseCount_WhenOreIsNotTracked() {
        UUID playerId = UUID.randomUUID();
        
        statsService.addOreMined(playerId, Material.DIRT);
        
        Map<Material, Integer> stats = statsService.getOreStats(playerId);
        
        assertTrue(stats.isEmpty());
        assertEquals(0, statsService.getTotalOresMined(playerId));
    }

    @Test
    void save_ShouldCallRepository() {
        when(repository.saveAll(anyMap())).thenReturn(CompletableFuture.completedFuture(null));
        
        statsService.save();
        
        verify(repository, times(1)).saveAll(anyMap());
    }

    @Test
    void load_ShouldUpdateInternalState() {
        UUID playerId = UUID.randomUUID();
        Map<UUID, Map<Material, Integer>> loadedData = new HashMap<>();
        Map<Material, Integer> playerStats = new HashMap<>();
        playerStats.put(Material.DIAMOND_ORE, 5);
        loadedData.put(playerId, playerStats);
        
        when(repository.loadAll()).thenReturn(CompletableFuture.completedFuture(loadedData));
        
        statsService.load().join();
        
        assertEquals(5, statsService.getOreStats(playerId).get(Material.DIAMOND_ORE));
    }
}
