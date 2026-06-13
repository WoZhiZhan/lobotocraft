package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.entity.base.AnimationRunnable;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class AnimationTimerEntry extends TimerEntry {
    private final AbstractAbnormality abnormality;
    private final String startAnimationName;
    private final String endAnimationName;
    private final AnimationRunnable runnable;

    public AnimationTimerEntry(AbstractAbnormality abnormality, String startAnimationName, String endAnimationName,
                               int duration) {
        this(abnormality, startAnimationName, endAnimationName, duration, null);
    }

    public AnimationTimerEntry(AbstractAbnormality abnormality, String startAnimationName, String endAnimationName,
                               int duration, AnimationRunnable runnable) {
        this.abnormality = abnormality;
        this.startAnimationName = startAnimationName;
        this.endAnimationName = endAnimationName;
        this.addSkillTimer(abnormality, 0, duration, 1, false);
        this.runnable = runnable;
    }

    @Override
    public void onStart(@NotNull LivingEntity living) {
        abnormality.setAnimation(this.startAnimationName);
    }

    @Override
    public void onEnd(@NotNull LivingEntity living) {
        if (runnable != null) {
            if (runnable.run()) {
                abnormality.setAnimation(runnable.newAnimation());
                return;
            }
        }
        abnormality.setAnimation(this.endAnimationName);
    }
}