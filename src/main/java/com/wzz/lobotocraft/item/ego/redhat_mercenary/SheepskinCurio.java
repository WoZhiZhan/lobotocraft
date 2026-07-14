package com.wzz.lobotocraft.item.ego.redhat_mercenary;

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
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class SheepskinCurio extends BaseEgoCurio {
    public SheepskinCurio() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String curioName() {
        return "sheepskin";
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
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7...这就是为什么我能在那匹狼饿着肚子的情况下安全回到这里！"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 最大生命值 + 9").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 最大精神值 + 5").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 装备者穿戴“郁蓝创痕”或“猩红创痕”时，造成的伤害提高15%").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("饰品套装效果（郁蓝创痕饰品+猩红创痕饰品+羊皮饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 当玩家穿戴“郁蓝创痕”和“猩红创痕”装备后同时穿戴三个饰品并锁定时。玩家红色，白色，黑色伤害抗性将被更改为0.6，蓝色伤害抗性为0.8。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 当玩家穿戴“郁蓝创痕”时，潜狼效果将受到强化“累计损失10%生命值后，进入潜狼模式6秒，6秒内玩家将无法受到伤害，且玩家造成的红色伤害+6点”。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 当玩家穿戴“猩红创痕”时，玩家进入暴怒状态（血量低于50%）后，攻速提高25%，副手火铳的cd将降低至1.5s。玩家对“猎物”造成的伤害额外+3点。玩家击杀“猎物”后，回复5%的精神值和生命值。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家穿戴“猩红创痕”或者“郁蓝创痕”时，装备效力等同于ALEPH级。").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean hasAttribute() {
        return true;
    }

    @Override
    public List<AttributeEntry> getAttributeEntries(@Nullable LivingEntity living) {
        return List.of(
                new AttributeEntry(UUID.fromString("69170e61-980d-4a9b-aad3-f6da9f0218d1"),
                        this.getCurrentClassName() + " Max Health Bonus", Attributes.MAX_HEALTH, 9D),
                new AttributeEntry(UUID.fromString("c650ea2a-f7d3-429f-865a-731bd18cee10"),
                        this.getCurrentClassName() + " Mental Bonus", ModAttributes.EXTRA_MENTAL_VALUE.get(), 5D)
        );
    }
}