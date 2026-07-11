package com.wzz.lobotocraft.item.ego.red_shoes;

import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RedShoesCurio extends BaseEgoCurio {
    public RedShoesCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "red_shoes";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 最大生命值 + 4").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家每次造成伤害会额外造成3点红色伤害的dot，最多叠加5层）").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家每有1级自律等级额外提高“血之渴望”1点伤害。玩家击杀生物后回复5%的生命值和精神值").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(LivingEntity living) {
        List<AttributeEntry> entries = new ArrayList<>();
        entries.add(new AttributeEntry(UUID.fromString("67320f76-0d3b-4911-bd35-89c6e041c85d"),
                this.getCurrentClassName() + " Max Health Penalty",
                Attributes.MAX_HEALTH, 4.0D));
        return entries;
    }
}
