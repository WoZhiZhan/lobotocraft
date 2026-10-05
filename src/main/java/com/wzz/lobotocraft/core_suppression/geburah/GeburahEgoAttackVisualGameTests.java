package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.entity.EntityGeburah;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

@GameTestHolder("lobotocraft_ego_ground")
@PrefixGameTestTemplate(false)
public final class GeburahEgoAttackVisualGameTests {
    @GameTest(template = "workcloseregressiongametests.navigation_empty",
            templateNamespace = "lobotocraft_ego_ground", timeoutTicks = 40,
            batch = "ego_slash_stab_visual")
    public static void realSlashStabEntrancesKeepSingleDamageAndDontConsumeExtraRandom(GameTestHelper h)
            throws Exception {
        var level = h.getLevel();
        EntityGeburah boss = ModEntities.geburah.get().create(level);
        AbstractAbnormality target = ModEntities.abandoned_murderer.get().create(level);
        h.assertTrue(boss != null && target != null, "could not create real combat fixture");
        BlockPos origin = h.absolutePos(new BlockPos(3, 2, 3));
        boss.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, -90, 0);
        target.moveTo(boss.getX() + 3, boss.getY(), boss.getZ(), 0, 0);
        boss.setNoAi(true);
        target.setNoAi(true);
        target.setEscape(true);
        h.assertTrue(target.canEscape() && target.isAbnormalityCombatTarget(),
                "fixture must be a normally escapable, damageable abnormality");
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10_000);
        target.setHealth(10_000);
        level.addFreshEntity(boss);
        level.addFreshEntity(target);
        var perform = EntityGeburah.class.getDeclaredMethod("performComboMove", GeburahMove.class);
        perform.setAccessible(true);
        var ego = EntityGeburah.class.getDeclaredField("comboEgo");
        ego.setAccessible(true);
        ego.setBoolean(boss, true);
        boss.debugSetPhase(GeburahPhase.EGO);
        int[] events = {0};
        Consumer<LivingHurtEvent> listener = event -> {
            if (event.getEntity() == target && event.getSource().getEntity() == boss) events[0]++;
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, listener);
        try {
            int expectedEvents = 0;
            for (GeburahMove move : new GeburahMove[]{GeburahMove.SLASH, GeburahMove.STAB}) {
                long seed = 8319;
                var expectedRandom = net.minecraft.util.RandomSource.create(seed);
                expectedRandom.nextInt(move.maxDamage(true) - move.minDamage(true) + 1);
                boss.getRandom().setSeed(seed);
                float before = target.getHealth();
                perform.invoke(boss, move);
                h.assertTrue(target.getHealth() < before, "cosmetic trial blocked " + move);
                h.assertTrue(events[0] == ++expectedEvents, "cosmetic trial added extra damage for " + move);
                h.assertTrue(boss.getRandom().nextInt() == expectedRandom.nextInt(),
                        "visual event consumed combat RNG for " + move);
            }
            h.succeed();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            boss.discard();
            target.discard();
        }
    }
}
