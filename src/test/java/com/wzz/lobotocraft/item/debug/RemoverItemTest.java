package com.wzz.lobotocraft.item.debug;

import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoverItemTest {
    @Test
    void protectsHumanCreativeAndAiPlayerTypes() {
        assertTrue(RemovalTargetPolicy.isProtectedPlayerType(Player.class));
        assertTrue(RemovalTargetPolicy.isProtectedPlayerType(ServerPlayer.class));
        assertTrue(RemovalTargetPolicy.isProtectedPlayerType(EmployeeServerPlayer.class));
    }

    @Test
    void ordinaryEntitiesRemainValidRemovalTargets() {
        assertFalse(RemovalTargetPolicy.isProtectedPlayerType(Entity.class));
        assertFalse(RemovalTargetPolicy.isProtectedPlayerType(null));
    }
}
