package com.wzz.lobotocraft.ego;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EgoLoadoutServiceTest {
    @Test
    void acceptsOnlyAResearchedWeaponStockWithSatisfiedRequirements() {
        AiEgoRepositoryData.Stock stock = stock("o-01-01", "weapon", 1,
                List.of("lobotocraft:test_weapon"));

        assertTrue(EgoLoadoutService.isValidAiWeaponAssignment(
                "o-01-01", stock, List.of(), id -> true));
        assertFalse(EgoLoadoutService.isValidAiWeaponAssignment(
                "o-01-01", stock, List.of("fortitude:3"), id -> true));
    }

    @Test
    void rejectsMissingStaleOrNonWeaponAssignments() {
        AiEgoRepositoryData.Stock valid = stock("o-01-01", "weapon", 1,
                List.of("lobotocraft:test_weapon"));
        AiEgoRepositoryData.Stock empty = stock("o-01-01", "weapon", 0,
                List.of("lobotocraft:test_weapon"));
        AiEgoRepositoryData.Stock armor = stock("o-01-01", "armor", 1,
                List.of("lobotocraft:test_armor"));

        assertFalse(EgoLoadoutService.isValidAiWeaponAssignment("", valid, List.of(), id -> true));
        assertFalse(EgoLoadoutService.isValidAiWeaponAssignment("o-01-02", valid, List.of(), id -> true));
        assertFalse(EgoLoadoutService.isValidAiWeaponAssignment("o-01-01", null, List.of(), id -> true));
        assertFalse(EgoLoadoutService.isValidAiWeaponAssignment("o-01-01", empty, List.of(), id -> true));
        assertFalse(EgoLoadoutService.isValidAiWeaponAssignment("o-01-01", armor, List.of(), id -> true));
        assertFalse(EgoLoadoutService.isValidAiWeaponAssignment("o-01-01", valid, List.of(), id -> false));
        assertFalse(EgoLoadoutService.isValidAiWeaponAssignment("o-01-01",
                stock("o-01-01", "weapon", 1,
                        List.of("lobotocraft:test_weapon", "minecraft:stone")),
                List.of(), id -> id.endsWith("test_weapon")));
    }

    private static AiEgoRepositoryData.Stock stock(String code, String type, int total,
                                                    List<String> itemIds) {
        return new AiEgoRepositoryData.Stock(code, type, total, 1,
                itemIds.stream().map(itemId -> new EgoItemSpec(itemId, null)).toList());
    }
}
