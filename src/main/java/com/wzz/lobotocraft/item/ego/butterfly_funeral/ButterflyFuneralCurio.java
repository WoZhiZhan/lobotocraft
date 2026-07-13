package com.wzz.lobotocraft.item.ego.butterfly_funeral;

import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.ego.base.BaseEgoCurio;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
        tooltip.add(Component.literal("效果：").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • 工作成功率 + 1").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 工作速度 + 1").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 最大生命值 + 1").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 最大精神值 + 1").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 移动速度 + 1").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 攻击速度 + 1").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("  • 玩家造成的伤害 + 2%").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("套装效果（装备+武器+饰品）：").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  • 当玩家手持两把武器时，按r会进入宣判攻击状态。宣判攻击状态下玩家手持武器姿势改变开火模式也会常驻为三连发。宣判攻击状态下玩家造成的所有伤害均无视生物无敌帧。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家每次造成伤害时为对方叠加“救赎”效果。每层救赎效果额外提高对方受到“圣宣”玩家1%的伤害。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家每次命中10次生物有为自己叠加“蝶引”效果。每层“蝶引”效果减少武器“圣宣”的0.5秒攻击间隔。").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("  • 玩家拥有5层“蝶引”且目标身上拥有50层“救赎”时，玩家每次对该目标造成伤害时将额外附带1点蓝色伤害。").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public List<AttributeEntry> getAttributeEntries() {
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
