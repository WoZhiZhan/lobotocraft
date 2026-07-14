package com.wzz.lobotocraft.item.ego.fourth_match_flame;

import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
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
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 最大生命值 + 4").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 玩家造成伤害时将额外点燃目标，攻击被点燃的目标造成的伤害+20%").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 无视火焰伤害").withStyle(ChatFormatting.LIGHT_PURPLE));
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