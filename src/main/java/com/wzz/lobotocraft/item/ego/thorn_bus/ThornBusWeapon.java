package com.wzz.lobotocraft.item.ego.thorn_bus;

import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ThornBusWeapon extends BaseEgoWeapon {
    private static final String LAST_ATTACKER_TAG = "lobotocraft_thorn_bus_last_attacker";
    private static final String LAST_ATTACK_TIME_TAG = "lobotocraft_thorn_bus_last_attack_time";
    private static final long KILL_CREDIT_TICKS = 5 * 20L + 20L;
    private static final Item[] TULIPS = {
            Items.RED_TULIP,
            Items.ORANGE_TULIP,
            Items.WHITE_TULIP,
            Items.PINK_TULIP
    };

    public ThornBusWeapon() {
        super(
            new Tier(),
            3,
            -2.4f,  // 攻击速度修正（基础4 + (-2) = 2.0）
            new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("TemperanceLevel", 3);
        return map;
    }

    @Override
    public boolean hasAnimatable() {
        return true;
    }

    @Override
    protected boolean hasIdle() {
        return false;
    }

    @Override
    public String weaponName() {
        return "thorn_bus";
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        boolean leftClick = super.onLeftClickEntity(stack, player, entity);
        if (!leftClick) return false;
        if (stack.getOrCreateTag().getBoolean("isInAnimatable")) {
            return false;
        }
        if (!canUseItem(player))
            return false;
        if (entity instanceof LivingEntity target && player.getAttackStrengthScale(0.0f) == 1.0f) {
            markThornBusHit(target, player);
            player.playSound(ModSounds.THORN_BUS_WEAPON.get());
            triggerAttackAnimation(player, stack);
            if (!player.level.isClientSide) {
                player.getServer().execute(() -> {
                    TimerEntry<LivingEntity> timerEntry = new TimerEntry<>() {
                        @Override
                        public void onRunning(@NotNull LivingEntity living) {
                            living.hurt(DamageHelper.getDamage(player, "white"), 2);
                        }
                    };
                    timerEntry.addSkillTimer(target, 0, 5000, 1, true);
                });
            }
        }
        return true;
    }

    public static void tryAwardKillTulip(LivingEntity dead, DamageSource source) {
        if (dead.level().isClientSide || source == null) {
            return;
        }
        if (!(source.getEntity() instanceof Player player)) {
            return;
        }
        if (!hasKillCredit(dead, player)) {
            return;
        }

        Item tulip = TULIPS[player.getRandom().nextInt(TULIPS.length)];
        ItemStack reward = new ItemStack(tulip);
        if (!player.addItem(reward)) {
            player.drop(reward, false);
        }
    }

    private static void markThornBusHit(LivingEntity target, Player player) {
        if (target.level().isClientSide) {
            return;
        }
        CompoundTag data = target.getPersistentData();
        data.putUUID(LAST_ATTACKER_TAG, player.getUUID());
        data.putLong(LAST_ATTACK_TIME_TAG, target.level().getGameTime());
    }

    private static boolean hasKillCredit(LivingEntity dead, Player player) {
        if (player.getMainHandItem().getItem() instanceof ThornBusWeapon) {
            return true;
        }
        CompoundTag data = dead.getPersistentData();
        if (!data.hasUUID(LAST_ATTACKER_TAG)) {
            return false;
        }
        UUID lastAttacker = data.getUUID(LAST_ATTACKER_TAG);
        if (!lastAttacker.equals(player.getUUID())) {
            return false;
        }
        long elapsedTicks = dead.level().getGameTime() - data.getLong(LAST_ATTACK_TIME_TAG);
        return elapsedTicks >= 0 && elapsedTicks <= KILL_CREDIT_TICKS;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (ClientInputUtil.isShiftPressed()) {
            p_41423_.add(Component.literal("§7寻求常人无法承受的快感通常会使人丧失自我。"));
            p_41423_.add(Component.literal("§7如果那些荆棘上落下的粉末散播到了尘世，那么人们恐怕此生都将像陷入泥沼一般难以求生。"));
        } else {
            p_41423_.add(Component.literal("§6※持有者攻击时会附加额外的精神伤害。"));
            p_41423_.add(Component.literal("§6※击杀生物后获得一朵随机颜色的郁金香。"));
            p_41423_.add(Component.literal("§7<按Shift查看详细信息>"));
        }
    }

    private static class Tier implements net.minecraft.world.item.Tier {
        @Override
        public int getUses() {
            return 0;
        }

        @Override
        public float getSpeed() {
            return 3.0F;
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
