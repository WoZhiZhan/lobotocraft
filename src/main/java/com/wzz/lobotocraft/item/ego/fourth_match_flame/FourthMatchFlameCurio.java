package com.wzz.lobotocraft.item.ego.fourth_match_flame;

import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class FourthMatchFlameCurio extends BaseEgoCurio {
    public FourthMatchFlameCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "fourth_match_flame";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.common.effects", ChatFormatting.YELLOW);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.fourth_match_flame_curio.effect.1",
                ChatFormatting.GREEN);
        EgoTooltipUtil.blank(tooltip);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.common.full_set_effect",
                ChatFormatting.AQUA);
        for (int index = 1; index <= 2; index++) {
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.fourth_match_flame_curio.set." + index,
                    ChatFormatting.LIGHT_PURPLE);
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(LivingEntity living) {
        return List.of(
                new AttributeEntry(UUID.fromString("e9667f5f-66dd-4cfd-8274-a6ef1297ae81"),
                        this.getCurrentClassName() + " Max Health Bonus", Attributes.MAX_HEALTH, 4)
        );
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }
}
