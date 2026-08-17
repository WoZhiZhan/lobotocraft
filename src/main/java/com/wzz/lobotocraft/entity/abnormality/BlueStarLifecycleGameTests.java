package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.base.EscapeTracker;
import com.wzz.lobotocraft.event.definition.abnormality.AbnormalityEscapeEvent;
import com.wzz.lobotocraft.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(BlueStarLifecycleGameTests.TEST_NAMESPACE)
public final class BlueStarLifecycleGameTests {
    static final String TEST_NAMESPACE = ModMain.MODID + "_blue_star_lifecycle";

    private BlueStarLifecycleGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = TEST_NAMESPACE,
            timeoutTicks = 40, required = true, batch = "blue_star_escape_lifecycle")
    public static void triggerEscapePublishesCommonLifecycle(GameTestHelper helper) {
        EntityBlueStar blueStar = ModEntities.blue_star.get().create(helper.getLevel());
        if (blueStar == null) {
            helper.fail("could not create blue star");
            return;
        }

        BlockPos spawn = helper.absolutePos(new BlockPos(4, 1, 4));
        blueStar.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(blueStar);

        EscapeTracker tracker = EscapeTracker.getInstance();
        tracker.resetForDay(helper.getLevel().getServer());
        EscapeEventRecorder recorder = new EscapeEventRecorder(blueStar);
        MinecraftForge.EVENT_BUS.register(recorder);
        try {
            helper.assertTrue(tracker.getCurrentAlertLevel(helper.getLevel().getServer()) == -1,
                    "escape alert baseline was not normal");

            blueStar.triggerEscape();

            helper.assertTrue(blueStar.hasEscape(), "blue star did not enter escaped state");
            helper.assertTrue(recorder.matchingEvents == 1,
                    "blue star escape did not publish exactly one abnormality escape event");
            helper.assertTrue(tracker.getCurrentAlertLevel(helper.getLevel().getServer()) >= 0,
                    "blue star escape was not registered by the facility escape tracker");
            helper.succeed();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(recorder);
            blueStar.discard();
            tracker.resetForDay(helper.getLevel().getServer());
        }
    }

    private static final class EscapeEventRecorder {
        private final EntityBlueStar expected;
        private int matchingEvents;

        private EscapeEventRecorder(EntityBlueStar expected) {
            this.expected = expected;
        }

        @SubscribeEvent
        public void onEscape(AbnormalityEscapeEvent event) {
            if (event.getAbnormality() == expected) matchingEvents++;
        }
    }
}
