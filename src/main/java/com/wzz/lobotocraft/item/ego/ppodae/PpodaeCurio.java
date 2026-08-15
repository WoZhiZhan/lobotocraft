package com.wzz.lobotocraft.item.ego.ppodae;

import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;
import java.util.UUID;

public class PpodaeCurio extends BaseEgoCurio {
    private static final UUID SPEED_UUID  = UUID.fromString("a1b2c3d4-0001-0001-0001-000000000001");
    private static final UUID HEALTH_UUID = UUID.fromString("a1b2c3d4-0002-0002-0002-000000000002");
    public PpodaeCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "ppodae";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.common.effects", ChatFormatting.YELLOW);
        for (int index = 1; index <= 3; index++) {
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.ppodae_curio.effect." + index,
                    ChatFormatting.GREEN);
        }
        EgoTooltipUtil.blank(tooltip);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.common.full_set_effect", ChatFormatting.AQUA);
        for (int index = 1; index <= 2; index++) {
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.ppodae_curio.set." + index,
                    ChatFormatting.LIGHT_PURPLE);
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        super.curioTick(slotContext, stack);
        if (slotContext.entity() instanceof ServerPlayer player) {
            boolean holding = player.getMainHandItem().getItem() instanceof PpodaeWeapon;

            AttributeInstance speed  = player.getAttribute(Attributes.MOVEMENT_SPEED);
            AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);

            if (holding) {
                if (speed != null && speed.getModifier(SPEED_UUID) == null) {
                    speed.addTransientModifier(new AttributeModifier(
                            SPEED_UUID, "ppodae_speed", 0.05,
                            AttributeModifier.Operation.MULTIPLY_TOTAL)); // 最终速度 ×1.05
                }
                if (health != null && health.getModifier(HEALTH_UUID) == null) {
                    health.addTransientModifier(new AttributeModifier(
                            HEALTH_UUID, "ppodae_health", 5.0,
                            AttributeModifier.Operation.ADDITION));       // +5 血
                }
            } else {
                removeModifiers(player);
            }
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        super.onUnequip(slotContext, newStack, stack);
        if (slotContext.entity() instanceof ServerPlayer player) {
            removeModifiers(player);
        }
    }

    private void removeModifiers(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED_UUID);

        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.removeModifier(HEALTH_UUID);
    }

    @Override
    public Attribute getAttribute() {
        return Attributes.MAX_HEALTH;
    }

    @Override
    public float getWorkSuccessBonus(Player player, WorkType workType) {
        return 0.0016f;
    }

    @Override
    public int getWorkSpeedBonus(Player player, WorkType workType) {
        return 2;
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public UUID getAttributeUUID() {
        return UUID.fromString("009b15c1-a0c3-4a50-9e5b-8d30d36334ce");
    }

    @Override
    public float getAttributeBonus() {
        return 4f;
    }
}
