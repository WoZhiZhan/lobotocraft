package com.wzz.lobotocraft.entity.abnormality;

import com.lobotocraft.facility.FacilityManager;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.world.data.EndBirdEggTracker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/** Original Apocalypse Bird egg cycle: every living event egg acts independently. */
final class EndBirdMeltdownService {
    static final int MIN_DELAY_TICKS = 60 * 20;
    static final int MAX_DELAY_TICKS = 120 * 20;
    private static final String NEXT_MELTDOWN_TICK = "EndBirdNextMeltdownTick";

    private EndBirdMeltdownService() {
    }

    static void tick(AbstractAbnormality egg, String eggType) {
        if (!(egg.level() instanceof ServerLevel level) || egg.isDeadOrDying()) return;
        EndBirdEggTracker tracker = EndBirdEggTracker.get(level);
        CompoundTag data = egg.getPersistentData();
        if (!tracker.isTrackedEgg(egg.getUUID(), eggType)) {
            data.remove(NEXT_MELTDOWN_TICK);
            return;
        }

        long now = level.getGameTime();
        long next = data.getLong(NEXT_MELTDOWN_TICK);
        if (next <= 0L) {
            data.putLong(NEXT_MELTDOWN_TICK, now + nextDelayTicks(level.getRandom()));
            return;
        }
        if (now < next) return;

        FacilityManager.activateEndBirdMeltdowns(level, 4);
        data.putLong(NEXT_MELTDOWN_TICK, now + nextDelayTicks(level.getRandom()));
    }

    static int nextDelayTicks(RandomSource random) {
        return MIN_DELAY_TICKS + random.nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1);
    }
}
