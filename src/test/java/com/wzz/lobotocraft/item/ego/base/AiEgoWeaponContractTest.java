package com.wzz.lobotocraft.item.ego.base;

import com.wzz.lobotocraft.item.ego.approval_birds.ApprovalBirdWeapon;
import com.wzz.lobotocraft.item.ego.bigbadwolf.BigBadwolfWeapon;
import com.wzz.lobotocraft.item.ego.blue_star.BlueStarWeapon;
import com.wzz.lobotocraft.item.ego.butterfly_funeral.ButterflyFuneralWeapon;
import com.wzz.lobotocraft.item.ego.children_galaxy.ChildrenGalaxyWeapon;
import com.wzz.lobotocraft.item.ego.crumbling_armor.CrumblingArmorWeapon;
import com.wzz.lobotocraft.item.ego.end_bird.EndBirdWeapon;
import com.wzz.lobotocraft.item.ego.fourth_match_flame.FourthMatchFlameWeapon;
import com.wzz.lobotocraft.item.ego.helper.HelperWeapon;
import com.wzz.lobotocraft.item.ego.nothing_there.NothingThereWeapon;
import com.wzz.lobotocraft.item.ego.smiling_corpse_mountain.SmilingCorpseMountainWeapon;
import com.wzz.lobotocraft.item.ego.snowqueen.SnowQueenWeapon;
import org.junit.jupiter.api.Test;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiEgoWeaponContractTest {
    @Test
    void immediateSpecialAttacksAreAiEnabled() {
        for (Class<?> weapon : List.of(ApprovalBirdWeapon.class, BigBadwolfWeapon.class,
                BlueStarWeapon.class, ChildrenGalaxyWeapon.class, FourthMatchFlameWeapon.class,
                HelperWeapon.class, NothingThereWeapon.class,
                SmilingCorpseMountainWeapon.class, SnowQueenWeapon.class)) {
            assertTrue(AiSpecialEgoWeapon.class.isAssignableFrom(weapon), weapon.getName());
        }
    }

    @Test
    void chargedSpecialAttacksRemainManual() {
        assertFalse(AiSpecialEgoWeapon.class.isAssignableFrom(CrumblingArmorWeapon.class));
        assertFalse(AiSpecialEgoWeapon.class.isAssignableFrom(EndBirdWeapon.class));
    }

    @Test
    void solemnLamentDeclaresRangedAndCombatModeBehavior() {
        assertTrue(AiRangedEgoWeapon.class.isAssignableFrom(ButterflyFuneralWeapon.class));
        assertTrue(AiCombatModeEgoWeapon.class.isAssignableFrom(ButterflyFuneralWeapon.class));
    }

    @Test
    void blueStarOverridesTheTargetAwareAiAttackHook() throws NoSuchMethodException {
        assertTrue(BlueStarWeapon.class.getDeclaredMethod("useForAi",
                ServerPlayer.class, ItemStack.class, InteractionHand.class,
                LivingEntity.class).getDeclaringClass() == BlueStarWeapon.class);
    }
}
