package com.wzz.lobotocraft.item.ego.butterfly_funeral;

import com.wzz.lobotocraft.init.ModAttributes;
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

public class ButterflyFuneralCurio extends BaseEgoCurio {
    public ButterflyFuneralCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "butterfly_funeral";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.common.effects", ChatFormatting.YELLOW);
        for (int index = 1; index <= 7; index++) {
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.butterfly_funeral_curio.effect." + index,
                    ChatFormatting.GREEN);
        }
        EgoTooltipUtil.blank(tooltip);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.common.full_set_effect",
                ChatFormatting.AQUA);
        for (int index = 1; index <= 4; index++) {
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.butterfly_funeral_curio.set." + index,
                    ChatFormatting.LIGHT_PURPLE);
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(LivingEntity living) {
        return Arrays.asList(
                new AttributeEntry(UUID.fromString("4bdcfde5-6de0-49a0-a4c3-7a60c14dda2f"), this.getCurrentClassName() +  " Move Speed Bonus",
                        Attributes.MOVEMENT_SPEED, 0.01, Mode.MULTIPLY_TOTAL),
                new AttributeEntry(UUID.fromString("83b9cbaa-862a-4914-8fef-eab47d7ee171"), this.getCurrentClassName() +  " Attack Speed Bonus",
                        Attributes.ATTACK_SPEED, 0.005, Mode.MULTIPLY_TOTAL),
                new AttributeEntry(UUID.fromString("c07bce9d-a615-4f50-9620-8ca06f0644c1"), this.getCurrentClassName() +  " Mental Bonus",
                        ModAttributes.EXTRA_MENTAL_VALUE.get(), 1),
                new AttributeEntry(UUID.fromString("e0b3c7ac-2004-43c5-a9af-3c0c092de923"), this.getCurrentClassName() +  " Max Health Bonus",
                        Attributes.MAX_HEALTH, 1),
                new AttributeEntry(UUID.fromString("39b26b6c-b865-401c-b98a-4a2612535595"), this.getCurrentClassName() +  " Attack Damage Bonus",
                        Attributes.ATTACK_DAMAGE, 0.02, Mode.MULTIPLY_TOTAL)
        );
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public int getWorkSpeedBonus(Player player, WorkType workType) {
        return 1;
    }

    @Override
    public float getWorkSuccessBonus(Player player, WorkType workType) {
       return 0.05f;
    }
}
