package com.wzz.lobotocraft.employee;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeRuntimeManagerTest {
    @Test
    void idleWanderUsesAnIrregularFiveToTwentySecondDelay() {
        RandomSource random = RandomSource.create(9137L);
        boolean varied = false;
        int first = EmployeeRuntimeManager.idleWanderDelay(random);
        for (int i = 0; i < 100; i++) {
            int delay = EmployeeRuntimeManager.idleWanderDelay(random);
            assertTrue(delay >= 100 && delay <= 400);
            varied |= delay != first;
        }
        assertTrue(varied);
    }

    @Test
    void syntheticEmployeesNeverInitializeSignedChat() {
        var actions = EmployeeRuntimeManager.aiPlayerInfoActions();

        assertTrue(actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER));
        assertTrue(actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE));
        assertTrue(actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED));
        assertTrue(actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY));
        assertTrue(actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME));
        assertFalse(actions.contains(ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT));
    }

    @Test
    void staleAutonomousSuppressionTaskResetsEvenAfterTargetUuidWasCleared() {
        assertTrue(EmployeeRuntimeManager.shouldResetAutonomousSuppressionTask(
                false, EmployeeTaskType.SEARCH_AND_SUPPRESS));
        assertFalse(EmployeeRuntimeManager.shouldResetAutonomousSuppressionTask(
                true, EmployeeTaskType.SEARCH_AND_SUPPRESS));
        assertFalse(EmployeeRuntimeManager.shouldResetAutonomousSuppressionTask(
                false, EmployeeTaskType.IDLE));
    }
}
