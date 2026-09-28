package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.capability.PanicType;
import com.wzz.lobotocraft.item.ego.army_in_black.ArmyInBlackWeapon;
import com.wzz.lobotocraft.item.ego.blue_star.BlueStarWeapon;
import com.wzz.lobotocraft.item.ego.butterfly_funeral.ButterflyFuneralWeapon;
import com.wzz.lobotocraft.item.ego.end_bird.EndBirdWeapon;
import com.wzz.lobotocraft.item.ego.happy_teddy.HappyTeddyWeapon;
import com.wzz.lobotocraft.item.ego.repentance.RepentanceWeapon;
import com.wzz.lobotocraft.item.ego.snowqueen.SnowQueenWeapon;
import com.wzz.lobotocraft.item.ego.the_lady_facing_the_wall.LadyFacingWallWeapon;
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

    @Test
    void panicCounterattackUsesTheEquippedWeaponAndItsCurrentMode() {
        assertTrue(EmployeeRuntimeManager.shouldCounterPanickingEmployee(
                PanicType.BERSERK, Object.class, false, false, 0.1F));
        for (Class<?> whiteWeapon : new Class<?>[]{
                BlueStarWeapon.class, HappyTeddyWeapon.class, RepentanceWeapon.class,
                SnowQueenWeapon.class, LadyFacingWallWeapon.class
        }) {
            assertTrue(EmployeeRuntimeManager.shouldCounterPanickingEmployee(
                    PanicType.PRUDENCE, whiteWeapon, true, false, 1.0F), whiteWeapon.getSimpleName());
        }
        assertTrue(EmployeeRuntimeManager.shouldCounterPanickingEmployee(
                PanicType.PRUDENCE, ArmyInBlackWeapon.class, true, false, 0.51F));
        assertFalse(EmployeeRuntimeManager.shouldCounterPanickingEmployee(
                PanicType.PRUDENCE, ArmyInBlackWeapon.class, true, false, 0.5F));
        assertTrue(EmployeeRuntimeManager.shouldCounterPanickingEmployee(
                PanicType.PRUDENCE, ButterflyFuneralWeapon.class, true, false, 1.0F));
        assertFalse(EmployeeRuntimeManager.shouldCounterPanickingEmployee(
                PanicType.PRUDENCE, ButterflyFuneralWeapon.class, true, true, 1.0F));
        assertFalse(EmployeeRuntimeManager.shouldCounterPanickingEmployee(
                PanicType.PRUDENCE, EndBirdWeapon.class, true, false, 1.0F));
        assertFalse(EmployeeRuntimeManager.shouldCounterPanickingEmployee(
                PanicType.PRUDENCE, BlueStarWeapon.class, false, false, 1.0F));
        assertFalse(EmployeeRuntimeManager.shouldCounterPanickingEmployee(
                PanicType.NONE, BlueStarWeapon.class, true, false, 1.0F));
    }
}
