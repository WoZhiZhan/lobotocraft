package com.wzz.lobotocraft.world.gear;

import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("lobotocraft_gear_church_placement")
@PrefixGameTestTemplate(false)
public final class GearChurchPlacementGameTests {
    @GameTest(template = "workcloseregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_gear_church_placement", timeoutTicks = 2400,
            batch = "gear_church_manual_placement")
    public static void manualChunkClippedPlacementPopulatesSixteenSafeSlotsAndPersists(GameTestHelper h) {
        GearChurchGameTests.manualChunkClippedPlacementPopulatesSixteenSafeSlotsAndPersists(h);
    }
}
