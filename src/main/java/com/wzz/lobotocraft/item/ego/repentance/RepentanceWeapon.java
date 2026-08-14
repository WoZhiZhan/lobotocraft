package com.wzz.lobotocraft.item.ego.repentance;

import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.util.ClientInputUtil;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.ChatFormatting;
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
                serverPlayer.sendSystemMessage(Component.translatable(
                        "message.lobotocraft.penitence.restored_mental", 10));
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
            for (int index = 1; index <= 6; index++) {
                EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.penitence_weapon.story." + index,
                        ChatFormatting.GRAY);
            }
        } else {
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.penitence_weapon.effect",
                    ChatFormatting.GOLD);
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.common.shift_details",
                    ChatFormatting.GRAY);
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
