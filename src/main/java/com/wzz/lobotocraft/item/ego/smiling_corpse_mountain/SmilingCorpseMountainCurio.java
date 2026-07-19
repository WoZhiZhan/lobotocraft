package com.wzz.lobotocraft.item.ego.smiling_corpse_mountain;

import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
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
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 最大精神值 + 5").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 最大生命值 + 5").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 锁定装备后，每清理一个石碑，使自身使用“笑靥”造成的伤害 +1%。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 特殊攻击命中带有“腐败”的生物时，对方每有一层“腐败”便额外造成一次伤害。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 每隔30秒生成持续25秒的护盾，护盾值 = 清理石碑数 × 2。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 使用“笑靥”命中带“腐败”的生物时，使其移动速度降低70%，持续5秒。").withStyle(ChatFormatting.LIGHT_PURPLE));
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
