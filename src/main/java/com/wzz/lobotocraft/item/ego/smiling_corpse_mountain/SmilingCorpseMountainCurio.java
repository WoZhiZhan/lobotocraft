package com.wzz.lobotocraft.item.ego.smiling_corpse_mountain;

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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SmilingCorpseMountainCurio extends BaseEgoCurio {
    public SmilingCorpseMountainCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "smiling_corpse_mountain";
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
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.smiling_corpse_mountain_curio.effect." + index,
                    ChatFormatting.GREEN);
        }
        EgoTooltipUtil.blank(tooltip);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.common.full_set_effect", ChatFormatting.AQUA);
        for (int index = 1; index <= 4; index++) {
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.smiling_corpse_mountain_curio.set." + index,
                    ChatFormatting.LIGHT_PURPLE);
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(LivingEntity living) {
        List<AttributeEntry> entries = new ArrayList<>();
        entries.add(new AttributeEntry(UUID.fromString("e99dec58-bdde-4e8b-bfcc-61875c6f9eeb"),
                this.getCurrentClassName() + " Max Health Penalty",
                Attributes.MAX_HEALTH, 5.0D));
        entries.add(new AttributeEntry(UUID.fromString("d84faff5-59a9-4cbe-bba0-104a286dfce2"),
                this.getCurrentClassName() + " Max Mental Penalty",
                ModAttributes.EXTRA_MENTAL_VALUE.get(), 5.0D));
        return entries;
    }
}
