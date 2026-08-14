package com.wzz.lobotocraft.item.ego.end_bird;

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

public class EndBirdCurio extends BaseEgoCurio {
    public EndBirdCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "end_bird";
    }

    @Override
    public boolean hasAnimatable() {
        return true;
    }

    @Override
    public boolean hasMoveAnimation() {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.common.effects", ChatFormatting.YELLOW);
        for (int index = 1; index <= 8; index++) {
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.end_bird_curio.effect." + index,
                    ChatFormatting.GREEN);
        }
        EgoTooltipUtil.blank(tooltip);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.common.full_set_effect",
                ChatFormatting.AQUA);
        for (int index = 1; index <= 3; index++) {
            EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.end_bird_curio.set." + index,
                    ChatFormatting.LIGHT_PURPLE);
        }
        EgoTooltipUtil.blank(tooltip);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.end_bird_curio.story.1",
                ChatFormatting.GOLD);
        EgoTooltipUtil.add(tooltip, "tooltip.lobotocraft.end_bird_curio.story.2",
                ChatFormatting.GOLD);
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public int getWorkSpeedBonus(Player player, WorkType workType) {
        return 14;
    }

    @Override
    public float getWorkSuccessBonus(Player player, WorkType workType) {
        return 0.07F;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(LivingEntity living) {
        return Arrays.asList(
                new AttributeEntry(UUID.fromString("ec348c7c-f0df-425c-b6cf-c3e4a9247168"),
                        this.getCurrentClassName() + " Max Health Bonus", Attributes.MAX_HEALTH, 7),
                new AttributeEntry(UUID.fromString("a9e9019f-754c-4878-b3a9-5c500eee001c"),
                        this.getCurrentClassName() + " Max Mental Bonus", ModAttributes.EXTRA_MENTAL_VALUE.get(), 7),
                new AttributeEntry(UUID.fromString("5c105315-364b-4bd5-8ccb-861ac1c9d984"),
                        this.getCurrentClassName() + " Max Speed Bonus", Attributes.MOVEMENT_SPEED, 0.014D),
                new AttributeEntry(UUID.fromString("e29489fd-d169-474a-8f16-d8e7ee3015a8"),
                        this.getCurrentClassName() + " Max Attack Speed Bonus", Attributes.ATTACK_SPEED, 0.026D)
        );
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }
}
