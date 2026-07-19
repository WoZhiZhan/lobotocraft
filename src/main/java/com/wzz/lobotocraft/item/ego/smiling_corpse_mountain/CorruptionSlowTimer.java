package com.wzz.lobotocraft.item.ego.smiling_corpse_mountain;

import com.wzz.lobotocraft.util.TimerEntry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * "笑靥"命中带腐败的生物时施加的减速：移动速度 -70%,持续 5 秒。
 * 不可叠加、每次命中刷新持续时间。
 *
 * 必须是一个"命名类"(而不是匿名 TimerEntry),因为 TimerEntry 的刷新逻辑
 * 以 {@code 类 + 实体UUID} 作为键；匿名类每处调用点是同一个类,但为了语义清晰与复用,
 * 这里独立成类。刷新时 removeTimer 不会调用 onEnd,故 modifier 会一直保留,
 * 直到真正 onEnd 时移除。
 */
public class CorruptionSlowTimer extends TimerEntry<LivingEntity> {

    private static final UUID SLOW_UUID = UUID.fromString("c0ffee00-5111-4000-8000-000000005111");
    private static final double SLOW_AMOUNT = -0.70D; // -70%

    /** 减速 5 秒并刷新 */
    public static void apply(LivingEntity target) {
        // delay=0, duration=5000ms, exec/s=1, refresh=true
        new CorruptionSlowTimer().addSkillTimer(target, 0, 5000, 1, true);
    }

    @Override
    public void onStart(@NotNull LivingEntity entity) {
        AttributeInstance ms = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (ms != null && ms.getModifier(SLOW_UUID) == null) {
            ms.addTransientModifier(new AttributeModifier(
                    SLOW_UUID, "smiling_corruption_slow", SLOW_AMOUNT,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    @Override
    public void onEnd(@NotNull LivingEntity entity) {
        AttributeInstance ms = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (ms != null) {
            ms.removeModifier(SLOW_UUID);
        }
    }
}
