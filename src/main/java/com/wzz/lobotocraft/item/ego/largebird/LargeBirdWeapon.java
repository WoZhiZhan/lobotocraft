package com.wzz.lobotocraft.item.ego.largebird;

import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.ClientInputUtil;
import com.wzz.lobotocraft.util.CuriosUtil;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.TimerEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LargeBirdWeapon extends BaseEgoWeapon {

    public LargeBirdWeapon() {
        super(
                new ModTier.WeaponTier(),
                22,
                -3.2f,  // 攻击速度修正（基础4 + (-2) = 2.0）
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("FortitudeLevel", 3);
        map.put("PrudenceLevel", 3);
        return map;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String weaponName() {
        return "largebird";
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        boolean leftClick = super.onLeftClickEntity(stack, player, entity);
        if (!leftClick) return false;
        if (!(entity instanceof LivingEntity target)) {
            return true;
        }
        if (!canUseItem(player))
            return false;
        int i = player.getRandom().nextInt(101);
        int v = 25;
        if (CuriosUtil.hasCurios(player, ModItems.LARGEBIRD_CURIO.get())) {
            v = 50;
        }
        if (i <= v) {
            TimerEntry<LivingEntity> timerEntry = new TimerEntry<>() {
                @Override
                public void onStart(@NotNull LivingEntity living) {
                    living.getPersistentData().putBoolean("isLargeBirdWeaponBlackDamage", true);
                }

                @Override
                public void onEnd(@NotNull LivingEntity living) {
                    living.getPersistentData().putBoolean("isLargeBirdWeaponBlackDamage", false);
                }
            };
            timerEntry.addSkillTimer(target, 0, 10000, 1, true);
        }
        DamageSource src = DamageHelper.getDamage(player, "black");
        float damage = 22.0F + player.getRandom().nextInt(9) + 1;
        target.hurt(src, damage);
        triggerAttackAnimation(player, stack);
        stack.hurtAndBreak(1, player,
                p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND)
        );
        player.playSound(ModSounds.LARGE_BIRD_WEAPON.get());
        return true;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (ClientInputUtil.isShiftPressed()) {
            p_41423_.add(Component.literal("§7每当有一只黑森林里的动物被它“拯救”时，它的身上就会多出一颗令人毛骨悚然的眼睛。"));
            p_41423_.add(Component.literal("§7等级：§dWAW"));
        } else {
            p_41423_.add(Component.literal("§6※攻击时有25%的概率给目标添加一个易伤效果，使其受到的侵蚀伤害加深50%，持续10秒。"));
            p_41423_.add(Component.literal("§7<按Shift查看详细信息>"));
        }
    }
}