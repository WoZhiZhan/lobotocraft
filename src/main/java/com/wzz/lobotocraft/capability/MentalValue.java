package com.wzz.lobotocraft.capability;

import com.wzz.lobotocraft.event.definition.mental_value.MentalValueEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;

import java.util.UUID;

public class MentalValue implements IMentalValue {
    private float mentalValue = 20.0F;
    private float maxMentalValue = 20.0F;  // 基础最大精神值
    private float extraMentalValue = 0.0F;  // 额外精神值（字段备份，当玩家为null时使用）
    private Player player;

    // Bug fix 3: setExtraMentalValue 需要通过此 UUID 操控 attribute，
    // 保证 get/set 读写同一个数据源
    private static final UUID EXTRA_MENTAL_MODIFIER_UUID =
            UUID.fromString("d4e5f6a7-b8c9-0123-defa-234567890123");

    public MentalValue(Player player) {
        this.player = player;
    }

    public MentalValue() {
        this.player = null;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public float getMentalValue() {
        return mentalValue;
    }

    @Override
    public void setMentalValue(float value) {
       setMentalValue(value, MentalValueEvent.ChangeType.SET);
    }

    @Override
    public void setMentalValue(float value, MentalValueEvent.ChangeType changeType) {
        float originalValue = this.mentalValue;
        float effectiveMax = getEffectiveMaxMentalValue();
        if (changeType == MentalValueEvent.ChangeType.ERROR) changeType = MentalValueEvent.ChangeType.SET;
        MentalValueEvent.Pre preEvent = new MentalValueEvent.Pre(
                player, originalValue, effectiveMax,
                value, changeType, null
        );

        if (MinecraftForge.EVENT_BUS.post(preEvent)) {
            return;
        }

        float newValue = preEvent.getNewValue();
        effectiveMax = getEffectiveMaxMentalValue();
        this.mentalValue = Math.max(0, Math.min(newValue, effectiveMax));

        MentalValueEvent.Post postEvent = new MentalValueEvent.Post(
                player, originalValue, effectiveMax,
                this.mentalValue, changeType, null
        );
        MinecraftForge.EVENT_BUS.post(postEvent);

        checkDepleted(originalValue);
    }

    @Override
    public float getMaxMentalValue() {
        return maxMentalValue;
    }

    @Override
    public void setMaxMentalValue(float value) {
        float oldMax = this.maxMentalValue;
        float newMax = Math.max(20, value);

        this.maxMentalValue = newMax;

        float effectiveMax = getEffectiveMaxMentalValue();
        if (mentalValue > effectiveMax) {
            mentalValue = effectiveMax;
        }

        if (player != null) {
            MentalValueEvent.MaxChanged event = new MentalValueEvent.MaxChanged(
                    player, this.mentalValue, oldMax, newMax
            );
            MinecraftForge.EVENT_BUS.post(event);
        }
    }

    /**
     * Bug fix 3 核心：
     * 原来 getter 从 player attribute 读取，setter 只写字段，两者数据源不同。
     * 现在：
     *   - getter 优先读 player attribute（兼容饰品/装备通过 attribute 系统添加的加成），
     *     attribute 为 null 时降级读字段。
     *   - setter 同步写字段 AND player attribute（用固定 UUID 的 modifier），
     *     保证两者始终一致。
     */
    @Override
    public float getExtraMentalValue() {
        if (player != null) {
            AttributeInstance attr = player.getAttribute(ModAttributes.EXTRA_MENTAL_VALUE.get());
            if (attr != null) {
                return (float) attr.getValue();
            }
        }
        // 没有 player 或 attribute 时降级到字段（如 NBT 加载阶段）
        return this.extraMentalValue;
    }

    @Override
    public void setExtraMentalValue(float value) {
        this.extraMentalValue = value;

        if (player != null) {
            AttributeInstance attr = player.getAttribute(ModAttributes.EXTRA_MENTAL_VALUE.get());
            if (attr != null) {
                // 先移除旧的 modifier，再重新添加
                attr.removeModifier(EXTRA_MENTAL_MODIFIER_UUID);
                if (value > 0) {
                    attr.addPermanentModifier(new AttributeModifier(
                            EXTRA_MENTAL_MODIFIER_UUID,
                            "Extra Mental Value (Capability)",
                            value,
                            AttributeModifier.Operation.ADDITION
                    ));
                }
            }
        }
    }

    @Override
    public float getEffectiveMaxMentalValue() {
        return maxMentalValue + getExtraMentalValue();
    }

    @Override
    public void addMentalValue(float amount) {
      addMentalValue(amount, MentalValueEvent.ChangeType.ADD);
    }

    @Override
    public void addMentalValue(float amount, MentalValueEvent.ChangeType changeType) {
        if (amount <= 0) return;
        if (changeType == MentalValueEvent.ChangeType.ERROR) changeType = MentalValueEvent.ChangeType.ADD;
        float originalValue = this.mentalValue;
        float effectiveMax = getEffectiveMaxMentalValue();
        float newValue = originalValue + amount;

        MentalValueEvent.Pre preEvent = new MentalValueEvent.Pre(
                player, originalValue, effectiveMax,
                newValue, changeType, null
        );

        if (MinecraftForge.EVENT_BUS.post(preEvent)) {
            return;
        }

        newValue = preEvent.getNewValue();
        effectiveMax = getEffectiveMaxMentalValue();
        this.mentalValue = Math.max(0, Math.min(newValue, effectiveMax));

        MentalValueEvent.Post postEvent = new MentalValueEvent.Post(
                player, originalValue, effectiveMax,
                this.mentalValue, changeType, null
        );
        MinecraftForge.EVENT_BUS.post(postEvent);
    }

    @Override
    public void reduceMentalValue(float amount, MentalValueEvent.ChangeType changeType) {
        if (amount <= 0) return;
        if (changeType == MentalValueEvent.ChangeType.ERROR) changeType = MentalValueEvent.ChangeType.REDUCE;
        float originalValue = this.mentalValue;
        float effectiveMax = getEffectiveMaxMentalValue();
        float newValue = originalValue - amount;

        MentalValueEvent.Pre preEvent = new MentalValueEvent.Pre(
                player, originalValue, effectiveMax,
                newValue, changeType, null
        );

        if (MinecraftForge.EVENT_BUS.post(preEvent)) {
            return;
        }

        newValue = preEvent.getNewValue();
        this.mentalValue = Math.max(0, Math.min(newValue, effectiveMax));

        MentalValueEvent.Post postEvent = new MentalValueEvent.Post(
                player, originalValue, effectiveMax,
                this.mentalValue, changeType, null
        );
        MinecraftForge.EVENT_BUS.post(postEvent);

        checkDepleted(originalValue);
    }

    @Override
    public void reduceMentalValue(float amount) {
       reduceMentalValue(amount, MentalValueEvent.ChangeType.REDUCE);
    }

    @Override
    public boolean isMentalValueEmpty() {
        return mentalValue <= 0;
    }

    @Override
    public void copyFrom(IMentalValue source) {
        this.mentalValue = source.getMentalValue();
        this.maxMentalValue = source.getMaxMentalValue();
        this.extraMentalValue = source.getExtraMentalValue();
    }

    @Override
    public void saveNBTData(CompoundTag nbt) {
        nbt.putFloat("MentalValue", mentalValue);
        nbt.putFloat("MaxMentalValue", maxMentalValue);
        // Bug fix 3: 保存字段值（attribute 会随装备动态变化，不应持久化）
        nbt.putFloat("ExtraMentalValue", extraMentalValue);
    }

    @Override
    public void loadNBTData(CompoundTag nbt) {
        if (nbt.contains("MentalValue")) {
            mentalValue = nbt.getFloat("MentalValue");
        }
        if (nbt.contains("MaxMentalValue")) {
            maxMentalValue = nbt.getFloat("MaxMentalValue");
        }
        if (nbt.contains("ExtraMentalValue")) {
            extraMentalValue = nbt.getFloat("ExtraMentalValue");
            // Bug fix 3: 若 NBT 中有 extra 值，恢复时也要同步给 attribute
            // （player 此时可能还未绑定，setExtraMentalValue 中的 attribute 操作在
            //  player != null 时才生效，所以放在 setPlayer 中二次处理）
        }

        if (maxMentalValue < 20) {
            maxMentalValue = 20;
        }
        if (mentalValue < 0) {
            mentalValue = 0;
        }
        // 注意：此时 player 可能还没绑定，getEffectiveMaxMentalValue() 先按字段计算
        float effectiveMax = maxMentalValue + extraMentalValue;
        if (mentalValue > effectiveMax) {
            mentalValue = effectiveMax;
        }
    }

    /**
     * Bug fix 3: 玩家绑定后，把 NBT 加载的 extraMentalValue 补充同步给 attribute。
     * 调用方：MentalValueProvider 在 AttachCapabilitiesEvent 后应调用此方法
     * （或在 onPlayerJoinWorld 中通过 setExtraMentalValue 重新赋值）。
     */
    public void syncExtraToAttribute() {
        if (this.extraMentalValue > 0 && player != null) {
            // 复用 setExtraMentalValue，它会同时写字段和 attribute
            setExtraMentalValue(this.extraMentalValue);
        }
    }

    private void checkDepleted(float originalValue) {
        checkDepleted(originalValue, null);
    }

    private void checkDepleted(float originalValue, Object cause) {
        if (originalValue > 0 && this.mentalValue <= 0 && player != null) {
            float effectiveMax = getEffectiveMaxMentalValue();
            MentalValueEvent.Depleted event = new MentalValueEvent.Depleted(
                    player, effectiveMax, cause
            );
            MinecraftForge.EVENT_BUS.post(event);
        }
    }
}