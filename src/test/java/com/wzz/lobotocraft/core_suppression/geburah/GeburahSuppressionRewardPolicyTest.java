package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GeburahSuppressionRewardPolicyTest {
    @Test
    void increasesAiResearchByOneButNeverBeyondFive() {
        assertEquals(3, GeburahSuppressionRewardPolicy.aiResearchMaximum(2, true));
        assertEquals(5, GeburahSuppressionRewardPolicy.aiResearchMaximum(5, true));
        assertEquals(5, GeburahSuppressionRewardPolicy.aiResearchMaximum(9, true));
        assertEquals(2, GeburahSuppressionRewardPolicy.aiResearchMaximum(2, false));
        assertEquals(5, GeburahSuppressionRewardPolicy.aiResearchMaximum(9, false));
    }

    @Test
    void boostsEligibleCombatDamageOnlyAfterRewardUnlock() {
        assertEquals(11.5F, GeburahSuppressionRewardPolicy.combatDamage(10.0F, true, true), 0.0001F);
        assertEquals(10.0F, GeburahSuppressionRewardPolicy.combatDamage(10.0F, false, true), 0.0001F);
        assertEquals(10.0F, GeburahSuppressionRewardPolicy.combatDamage(10.0F, true, false), 0.0001F);
    }

    @Test
    void keepsBloodMistPlayerEgoAsAnExplicitPlaceholder() {
        assertEquals("blood_mist", GeburahSuppressionRewardPolicy.PLAYER_EGO_ID);
        assertEquals("血雾", GeburahSuppressionRewardPolicy.PLAYER_EGO_NAME);
        assertFalse(GeburahSuppressionRewardPolicy.PLAYER_EGO_IMPLEMENTED);
    }
}
