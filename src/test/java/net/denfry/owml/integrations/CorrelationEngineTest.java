package net.denfry.owml.integrations;

import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class CorrelationEngineTest {

    @Mock
    private Player mockPlayer;

    @Mock
    private AntiCheatIntegration grimIntegration;

    @Mock
    private AntiCheatIntegration vulcanIntegration;

    @Mock
    private AntiCheatIntegration matrixIntegration;

    @Mock
    private AntiCheatIntegration spartanIntegration;

    private CorrelationEngine correlationEngine;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        when(grimIntegration.getProviderName()).thenReturn("Grim");
        when(vulcanIntegration.getProviderName()).thenReturn("Vulcan");
        when(matrixIntegration.getProviderName()).thenReturn("Matrix");
        when(spartanIntegration.getProviderName()).thenReturn("Spartan");

        List<AntiCheatIntegration> integrations = Arrays.asList(
                grimIntegration, vulcanIntegration, matrixIntegration, spartanIntegration
        );

        correlationEngine = new CorrelationEngine(integrations);
    }

    @Test
    public void testGrimMovementWeight() {
        when(grimIntegration.getViolationLevel(mockPlayer, "movement")).thenReturn(0.5); // 0.5 score
        when(vulcanIntegration.getViolationLevel(mockPlayer, "movement")).thenReturn(0.0);
        when(matrixIntegration.getViolationLevel(mockPlayer, "movement")).thenReturn(0.0);
        when(spartanIntegration.getViolationLevel(mockPlayer, "movement")).thenReturn(0.0);

        // Internal: 0.8 * 0.6 = 0.48
        // External: Grim score 0.5 * 1.4 weight = 0.7. Since consensus = 1, average = 0.7.
        // Formula: 0.48 + (0.7 * 0.4) = 0.48 + 0.28 = 0.76
        double combined = correlationEngine.calculateCombinedScore(mockPlayer, 0.8, "movement");
        assertEquals(0.76, combined, 0.01);
    }

    @Test
    public void testVulcanCombatWeight() {
        when(grimIntegration.getViolationLevel(mockPlayer, "combat")).thenReturn(0.0);
        when(vulcanIntegration.getViolationLevel(mockPlayer, "combat")).thenReturn(0.6); // 0.6 score
        when(matrixIntegration.getViolationLevel(mockPlayer, "combat")).thenReturn(0.0);
        when(spartanIntegration.getViolationLevel(mockPlayer, "combat")).thenReturn(0.0);

        // Internal: 0.5 * 0.6 = 0.3
        // External: Vulcan score 0.6 * 1.3 weight = 0.78.
        // Formula: 0.3 + (0.78 * 0.4) = 0.3 + 0.312 = 0.612
        double combined = correlationEngine.calculateCombinedScore(mockPlayer, 0.5, "combat");
        assertEquals(0.612, combined, 0.01);
    }

    @Test
    public void testCrossProviderConsensusMultiplier() {
        when(grimIntegration.getViolationLevel(mockPlayer, "movement")).thenReturn(0.5);
        when(vulcanIntegration.getViolationLevel(mockPlayer, "movement")).thenReturn(0.4);
        when(matrixIntegration.getViolationLevel(mockPlayer, "movement")).thenReturn(0.0);
        when(spartanIntegration.getViolationLevel(mockPlayer, "movement")).thenReturn(0.0);

        // Grim weight: 1.4, score: 0.5 -> 0.7
        // Vulcan weight: 1.1 (not combat), score: 0.4 -> 0.44
        // Total external = (0.7 + 0.44) / 2 = 0.57
        // Internal: 0.5 * 0.6 = 0.3
        // External component: 0.57 * 0.4 = 0.228
        // Base combined = 0.3 + 0.228 = 0.528
        // Consensus = 2 -> multiply by 1.3 = 0.528 * 1.3 = 0.6864
        double combined = correlationEngine.calculateCombinedScore(mockPlayer, 0.5, "movement");
        assertEquals(0.6864, combined, 0.01);
    }

    @Test
    public void testScoreCap() {
        when(grimIntegration.getViolationLevel(mockPlayer, "movement")).thenReturn(1.0);
        when(vulcanIntegration.getViolationLevel(mockPlayer, "movement")).thenReturn(1.0);
        when(matrixIntegration.getViolationLevel(mockPlayer, "movement")).thenReturn(1.0);
        when(spartanIntegration.getViolationLevel(mockPlayer, "movement")).thenReturn(1.0);

        double combined = correlationEngine.calculateCombinedScore(mockPlayer, 1.0, "movement");
        // Due to 1.3 multiplier and max values, it would exceed 1.0, but should be capped.
        assertEquals(1.0, combined, 0.001);
    }
}