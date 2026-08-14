package com.wzz.lobotocraft.item.ego.snowqueen;

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

public class SnowQueenCurio extends BaseEgoCurio {
    public SnowQueenCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "snowqueen";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.common.effects", ChatFormatting.YELLOW);
        for (int index = 1; index <= 2; index++) {
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.snow_queen_curio.effect." + index,
                    ChatFormatting.GREEN);
        }
        EgoTooltipUtil.blank(tooltip);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.common.full_set_effect",
                ChatFormatting.AQUA);
        for (int index = 1; index <= 3; index++) {
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.snow_queen_curio.set." + index,
                    ChatFormatting.LIGHT_PURPLE);
        }
        for (int index = 1; index <= 3; index++) {
            EgoTooltipUtil.blank(tooltip);
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.snow_queen_curio.story." + index,
                    ChatFormatting.AQUA);
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(LivingEntity living) {
        return List.of(
                new AttributeEntry(UUID.fromString("28a5b459-0e25-4d7d-92b6-110835a9e854"),
                        this.getCurrentClassName() + " Mental Bonus", ModAttributes.EXTRA_MENTAL_VALUE.get(), 6D),
                new AttributeEntry(UUID.fromString("758ba69a-d290-4c3d-b4f7-ed65ef5ec2f5"),
                        this.getCurrentClassName() + " Max Health Bonus", Attributes.MAX_HEALTH, 6D)
        );
    }
}
