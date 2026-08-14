package com.wzz.lobotocraft.item.ego.repentance;

import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class RepentanceCurio extends BaseEgoCurio {
    public RepentanceCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "repentance";
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
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.penitence_curio.effect." + index,
                    ChatFormatting.GREEN);
        }
        EgoTooltipUtil.blank(tooltip);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.penitence_curio.special_heading",
                ChatFormatting.AQUA);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.penitence_curio.special.1",
                ChatFormatting.LIGHT_PURPLE);
        for (int index = 2; index <= 3; index++) {
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.penitence_curio.special." + index,
                    ChatFormatting.RED);
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(LivingEntity living) {
        return List.of(new AttributeEntry(
                UUID.fromString("29f82af0-fe7f-4f2a-a316-b9a6a1eaafa7"),
                this.getCurrentClassName() + " Extra Mental Value Bonus",
                ModAttributes.EXTRA_MENTAL_VALUE.get(), 2D));
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }
}
