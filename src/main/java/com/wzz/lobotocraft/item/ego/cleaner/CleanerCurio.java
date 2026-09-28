package com.wzz.lobotocraft.item.ego.cleaner;

import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class CleanerCurio extends BaseEgoCurio {
    private static final UUID HEALTH_UUID = UUID.fromString("a9fa2ee9-42e7-4ed1-8d9a-6e69af6962fb");
    private static final UUID MENTAL_UUID = UUID.fromString("c4648f3e-41a3-43a7-94fb-9c2d2cc4db75");

    public CleanerCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "cleaner";
    }

    @Override
    public String projectionSlot() {
        return "right_boby";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public boolean rendersOnWearer() {
        return false;
    }

    @Override
    public boolean render2D() {
        return false;
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(@Nullable LivingEntity living) {
        return List.of(
                new AttributeEntry(HEALTH_UUID, this.getCurrentClassName() + " Max Health Bonus",
                        Attributes.MAX_HEALTH, 5D),
                new AttributeEntry(MENTAL_UUID, this.getCurrentClassName() + " Max Mental Bonus",
                        ModAttributes.EXTRA_MENTAL_VALUE.get(), 5D)
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.common.effects", ChatFormatting.YELLOW);
        for (int index = 1; index <= 3; index++) {
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.cleaner_curio.effect." + index,
                    ChatFormatting.GREEN);
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
