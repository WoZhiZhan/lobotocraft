package com.wzz.lobotocraft.item.ego.repentance;

import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.ClientInputUtil;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 忏悔武器
 * 伤害类型：WHITE (精神)
 * 攻击力：5-7
 * 攻击速度：2.0
 * 特殊效果：勇气≥2级时，攻击有5%概率恢复10点精神值
 */
public class RepentanceWeapon extends BaseEgoWeapon {

    public RepentanceWeapon() {
        super(
            new RepentanceTier(),
            6,   // 基础攻击力
            -3.0f,  // 攻击速度修正（基础4 + (-2) = 2.0）
            new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String weaponName() {
        return "repentance";
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        boolean leftClick = super.onLeftClickEntity(stack, player, entity);
        if (!leftClick) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        if (!(entity instanceof LivingEntity target)) {
            return true;
        }
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            if (stats.getFortitudeLevel() >= 2 && player.getRandom().nextFloat() < 0.05f) {
                MentalValueUtil.addMentalValue(serverPlayer, 10);
                serverPlayer.sendSystemMessage(
                        Component.literal("§a忏悔：恢复了10点精神值")
                );
            }
        });
        DamageSource src = DamageHelper.getDamage(player, "white");
        float damage = 5.0F + player.getRandom().nextInt(2) + 1;
        target.hurt(src, damage);
        triggerAttackAnimation(player, stack);
        stack.hurtAndBreak(1, player,
                p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND)
        );
        return true;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (ClientInputUtil.isShiftPressed()) {
            p_41423_.add(Component.literal("§7唯有理解才能获取知识。"));
            p_41423_.add(Component.literal("§7通过对这个异想体的细心观测，我们成功提取出了它的核心并将其塑造成一把武器。"));
            p_41423_.add(Component.literal("§7空洞的眼窝凝视着人们的灵魂，荆棘之冠倾诉着人们的罪孽。"));
            p_41423_.add(Component.literal("§7要想使用这把武器，你必须抱有为了更大的良善而杀戮的决心。"));
            p_41423_.add(Component.literal("§7这把武器虽然不像其他装备那般强大，但它能为持有者提供心灵上的慰藉。"));
            p_41423_.add(Component.literal("§7然而，对于那些缺乏正义的人而言，并不存在什么慰藉。"));
        } else {
            p_41423_.add(Component.literal("§6当持有者的勇气等级大于等于2级时，每次其造成攻击伤害都有5%的概率恢复10点精神值。"));
            p_41423_.add(Component.literal("§7<按Shift查看详细信息>"));
        }
    }

    /**
     * 忏悔武器材质
     */
    private static class RepentanceTier implements Tier {
        @Override
        public int getUses() {
            return 0;
        }

        @Override
        public float getSpeed() {
            return 4.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 0.0F;
        }

        @Override
        public int getLevel() {
            return 2;
        }

        @Override
        public int getEnchantmentValue() {
            return 14;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }
    }
}