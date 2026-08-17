package com.wzz.lobotocraft.core_suppression.geburah;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeburahEncounterDataTest {
    @Test
    void fiveOrFewerPlayersAreEliminatedWhenTheFifthSharedLifeIsSpent() {
        GeburahEncounterData data = new GeburahEncounterData();
        UUID player = UUID.randomUUID();
        data.start(UUID.randomUUID(), Set.of(player));

        for (int i = 0; i < 4; i++) {
            assertEquals(GeburahEncounterData.DeathOutcome.REVIVE, data.consumeDeath(player));
        }
        assertEquals(GeburahEncounterData.DeathOutcome.SPECTATE, data.consumeDeath(player));
        assertEquals(0, data.getSharedLives());
        assertTrue(data.isEliminated(player));
    }

    @Test
    void moreThanFivePlayersHaveNoSharedRevive() {
        GeburahEncounterData data = new GeburahEncounterData();
        UUID player = UUID.randomUUID();
        data.start(UUID.randomUUID(), Set.of(player, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        assertEquals(GeburahEncounterData.DeathOutcome.SPECTATE, data.consumeDeath(player));
        assertEquals(0, data.getSharedLives());
    }

    @Test
    void employeeInvulnerabilityStateSurvivesNbtRoundTrip() {
        GeburahEncounterData data = new GeburahEncounterData();
        UUID employeeWithInvulnerability = UUID.randomUUID();
        UUID employeeWithoutInvulnerability = UUID.randomUUID();
        data.start(UUID.randomUUID(), Set.of(UUID.randomUUID()));
        data.recordEmployeeInvulnerability(employeeWithInvulnerability, true);
        data.recordEmployeeInvulnerability(employeeWithoutInvulnerability, false);

        GeburahEncounterData restored = GeburahEncounterData.load(data.save(new CompoundTag()));

        assertEquals(Boolean.TRUE,
                restored.getEmployeeInvulnerability(employeeWithInvulnerability));
        assertEquals(Boolean.FALSE,
                restored.getEmployeeInvulnerability(employeeWithoutInvulnerability));
    }
}
