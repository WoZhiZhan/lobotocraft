package com.wzz.lobotocraft.item.ego.children_galaxy;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChildrenGalaxyWeapon extends BaseEgoWeapon {
    private static final int COOLDOWN = 40;
    private static final float DAMAGE = 15.0f;
    private static final double ATTACK_RANGE = 3.5D;
    private static final double ATTACK_HALF_WIDTH = 1.5D;
    private static final double ATTACK_HALF_HEIGHT = 2.0D;

    public ChildrenGalaxyWeapon() {
        super(new Tier(), 14, -2.0f, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String weaponName() {
        return "children_galaxy";
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("PrudenceLevel", 2);
        map.put("EmployeeLevel", 2);
        return map;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canUseItem(player) || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }

        player.getCooldowns().addCooldown(this, COOLDOWN);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            swingGalaxy(serverPlayer);
        }
        return InteractionResultHolder.success(stack);
    }

    private void swingGalaxy(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 forward = getHorizontalLook(player);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x).normalize();
        Vec3 origin = player.position().add(0.0D, player.getBbHeight() * 0.55D, 0.0D);
        AABB scanBox = player.getBoundingBox().expandTowards(forward.scale(ATTACK_RANGE)).inflate(2.0D, 1.5D, 2.0D);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, scanBox, target -> isValidTarget(player, target))) {
            Vec3 targetCenter = target.getBoundingBox().getCenter();
            Vec3 relative = targetCenter.subtract(origin);
            double forwardDistance = relative.dot(forward);
            double sideDistance = Math.abs(relative.dot(right));
            double verticalDistance = Math.abs(relative.y);

            if (forwardDistance <= 0.0D || forwardDistance > ATTACK_RANGE) {
                continue;
            }
            if (sideDistance > ATTACK_HALF_WIDTH || verticalDistance > ATTACK_HALF_HEIGHT) {
                continue;
            }
            target.hurt(DamageHelper.getDamage(player, "black"), DAMAGE);
        }

        spawnSwingParticles(level, origin, forward, right);
        level.playSound(null, player.blockPosition(), ModSounds.CHILDREN_GALAXY_WEAPON.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private Vec3 getHorizontalLook(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);
        if (forward.lengthSqr() < 1.0E-6D) {
            double yaw = Math.toRadians(player.getYRot());
            forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        }
        return forward.normalize();
    }

    private boolean isValidTarget(Player player, LivingEntity target) {
        if (target == player || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player targetPlayer) {
            return !targetPlayer.isCreative() && !targetPlayer.isSpectator();
        }
        return !(target instanceof EntityClerk) && !(target instanceof Villager);
    }

    private void spawnSwingParticles(ServerLevel level, Vec3 origin, Vec3 forward, Vec3 right) {
        for (int i = 0; i < 45; i++) {
            double distance = 0.4D + level.random.nextDouble() * ATTACK_RANGE;
            double side = (level.random.nextDouble() - 0.5D) * ATTACK_HALF_WIDTH * 2.0D;
            double height = (level.random.nextDouble() - 0.35D) * 1.8D;
            Vec3 pos = origin.add(forward.scale(distance)).add(right.scale(side)).add(0.0D, height, 0.0D);
            level.sendParticles(ParticleTypes.FALLING_WATER, pos.x, pos.y, pos.z, 1, 0.02D, -0.05D, 0.02D, 0.0D);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0.03D, 0.03D, 0.03D, 0.01D);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.literal("§7右键挥洒坠落的星泪，对前方小范围敌人造成15点黑色伤害。"));
        tooltip.add(Component.literal("§7别让那枚小小的卵石离开身边。"));
    }

    private static class Tier implements net.minecraft.world.item.Tier {
        @Override public int getUses() { return 0; }
        @Override public float getSpeed() { return 2.0F; }
        @Override public float getAttackDamageBonus() { return 0.0F; }
        @Override public int getLevel() { return 2; }
        @Override public int getEnchantmentValue() { return 14; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    }
}
