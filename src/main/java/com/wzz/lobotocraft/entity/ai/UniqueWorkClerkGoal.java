package com.wzz.lobotocraft.entity.ai;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.work.UniqueWorkRequest;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

public final class UniqueWorkClerkGoal extends Goal {
    private final EntityClerk clerk;

    public UniqueWorkClerkGoal(EntityClerk clerk) {
        this.clerk = clerk;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP, Flag.TARGET));
    }

    @Override public boolean canUse() { return UniqueWorkRequest.isControlled(clerk); }
    @Override public void tick() { UniqueWorkRequest.tickClerk(clerk); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
}
