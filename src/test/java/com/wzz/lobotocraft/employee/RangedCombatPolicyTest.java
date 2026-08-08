package com.wzz.lobotocraft.employee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RangedCombatPolicyTest {
    @Test
    void rangeBandRetreatsHoldsAndApproachesAtItsBoundaries() {
        RangedCombatPolicy.Profile profile = RangedCombatPolicy.normalize(25.0D,
                8.0D, 15.0D, 22.0D);

        assertEquals(RangedCombatPolicy.State.RETREAT,
                RangedCombatPolicy.state(7.99D, true, profile));
        assertEquals(RangedCombatPolicy.State.HOLD,
                RangedCombatPolicy.state(8.0D, true, profile));
        assertEquals(RangedCombatPolicy.State.HOLD,
                RangedCombatPolicy.state(22.0D, true, profile));
        assertEquals(RangedCombatPolicy.State.APPROACH,
                RangedCombatPolicy.state(22.01D, true, profile));
        assertEquals(RangedCombatPolicy.State.APPROACH,
                RangedCombatPolicy.state(15.0D, false, profile));
    }

    @Test
    void invalidWeaponRangesAreClampedIntoAnOrderedUsableBand() {
        RangedCombatPolicy.Profile profile = RangedCombatPolicy.normalize(12.0D,
                30.0D, Double.NaN, 20.0D);

        assertEquals(12.0D, profile.attackRange());
        assertEquals(12.0D, profile.minimumSpacing());
        assertEquals(12.0D, profile.preferredSpacing());
        assertEquals(12.0D, profile.maximumSpacing());
    }
}
