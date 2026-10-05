package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.damagetype.AttackDamage;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.mixinaccess.IDamageSource;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class DamageHelper {
    private static final Set<ResourceKey<DamageType>> VANILLA_DAMAGE_TYPES = Set.of(
            DamageTypes.IN_FIRE,
            DamageTypes.LIGHTNING_BOLT,
            DamageTypes.ON_FIRE,
            DamageTypes.LAVA,
            DamageTypes.HOT_FLOOR,
            DamageTypes.IN_WALL,
            DamageTypes.CRAMMING,
            DamageTypes.DROWN,
            DamageTypes.STARVE,
            DamageTypes.CACTUS,
            DamageTypes.FALL,
            DamageTypes.FLY_INTO_WALL,
            DamageTypes.FELL_OUT_OF_WORLD,
            DamageTypes.GENERIC,
            DamageTypes.MAGIC,
            DamageTypes.WITHER,
            DamageTypes.DRAGON_BREATH,
            DamageTypes.DRY_OUT,
            DamageTypes.SWEET_BERRY_BUSH,
            DamageTypes.FREEZE,
            DamageTypes.STALAGMITE,
            DamageTypes.FALLING_BLOCK,
            DamageTypes.FALLING_ANVIL,
            DamageTypes.FALLING_STALACTITE,
            DamageTypes.STING,
            DamageTypes.MOB_ATTACK,
            DamageTypes.MOB_ATTACK_NO_AGGRO,
            DamageTypes.PLAYER_ATTACK,
            DamageTypes.ARROW,
            DamageTypes.TRIDENT,
            DamageTypes.MOB_PROJECTILE,
            DamageTypes.FIREWORKS,
            DamageTypes.FIREBALL,
            DamageTypes.UNATTRIBUTED_FIREBALL,
            DamageTypes.WITHER_SKULL,
            DamageTypes.THROWN,
            DamageTypes.INDIRECT_MAGIC,
            DamageTypes.THORNS,
            DamageTypes.EXPLOSION,
            DamageTypes.PLAYER_EXPLOSION,
            DamageTypes.SONIC_BOOM,
            DamageTypes.BAD_RESPAWN_POINT,
            DamageTypes.OUTSIDE_BORDER,
            DamageTypes.GENERIC_KILL
    );

    private static final Set<ResourceKey<DamageType>> resourceKeys = new HashSet<>();

    public static Set<ResourceKey<DamageType>> LOBOTTOCRAFT_DAMAGE_TYPES() {
        if (resourceKeys.isEmpty()) {
            resourceKeys.add(ResourceKey.create(Registries.DAMAGE_TYPE, ResourceUtil.createInstance("blue")));
            resourceKeys.add(ResourceKey.create(Registries.DAMAGE_TYPE, ResourceUtil.createInstance("red")));
            resourceKeys.add(ResourceKey.create(Registries.DAMAGE_TYPE, ResourceUtil.createInstance("white")));
            resourceKeys.add(ResourceKey.create(Registries.DAMAGE_TYPE, ResourceUtil.createInstance("black")));
        }
        return resourceKeys;
    }

    public static boolean isVanillaDamage(ResourceKey<DamageType> key) {
        return VANILLA_DAMAGE_TYPES.contains(key);
    }

    public static boolean isMyModDamage(ResourceKey<DamageType> key) {
        return LOBOTTOCRAFT_DAMAGE_TYPES().contains(key);
    }

    public static ResourceKey<DamageType> getDamageResourceKey(DamageSource damageSource) {
        Holder<DamageType> holder = damageSource.typeHolder();
        Optional<ResourceKey<DamageType>> optionalKey = holder.unwrapKey();
        return optionalKey.orElse(null);
    }

    public static boolean isWhiteDamage(DamageSource damageSource) {
        if (damageSource == null)
            return false;
        if (damageSource instanceof IDamageSource iDamageSource && iDamageSource.getDamageType() != null && iDamageSource.getDamageType().equals("white")) {
            return true;
        }
        return damageSource.getMsgId().equals("white");
    }

    public static boolean isRedDamage(DamageSource damageSource) {
        if (damageSource == null)
            return false;
        if (damageSource instanceof IDamageSource iDamageSource && iDamageSource.getDamageType() != null && iDamageSource.getDamageType().equals("red")) {
            return true;
        }
        if (isUnchargedEgoWeaponMelee(damageSource)) {
            return true;
        }
        return damageSource.getMsgId().equals("red");
    }

    public static boolean isUnchargedEgoWeaponMelee(DamageSource damageSource) {
        return damageSource != null
                && damageSource.is(DamageTypes.PLAYER_ATTACK)
                && damageSource.getEntity() instanceof LivingEntity living
                && BaseEgoWeapon.isMarkedPartialAttack(living);
    }

    public static boolean isBlueDamage(DamageSource damageSource) {
        if (damageSource == null)
            return false;
        if (damageSource instanceof IDamageSource iDamageSource && iDamageSource.getDamageType() != null && iDamageSource.getDamageType().equals("blue")) {
            return true;
        }
        return damageSource.getMsgId().equals("blue");
    }

    public static boolean isBlackDamage(DamageSource damageSource) {
        if (damageSource == null)
            return false;
        if (damageSource instanceof IDamageSource iDamageSource && iDamageSource.getDamageType() != null && iDamageSource.getDamageType().equals("black")) {
            return true;
        }
        return damageSource.getMsgId().equals("black");
    }

    public static boolean inFire(DamageSource damageSource) {
        return damageSource != null && damageSource.is(DamageTypes.IN_FIRE);
    }

    public static boolean onFire(DamageSource damageSource) {
        return damageSource != null && damageSource.is(DamageTypes.ON_FIRE);
    }

    public static boolean isAllFire(DamageSource damageSource) {
        return onFire(damageSource) || inFire(damageSource);
    }

    public static boolean isFellOutOfWorld(DamageSource damageSource) {
        return damageSource != null && damageSource.is(DamageTypes.FELL_OUT_OF_WORLD);
    }

    public static boolean isKill(DamageSource damageSource) {
        return damageSource != null && damageSource.is(DamageTypes.GENERIC_KILL);
    }

    public static boolean inCactus(DamageSource damageSource) {
        return damageSource != null && damageSource.is(DamageTypes.CACTUS);
    }

    public static boolean isDragonBreath(DamageSource damageSource) {
        return damageSource != null && damageSource.is(DamageTypes.DRAGON_BREATH);
    }

    public static boolean isDryOut(DamageSource damageSource) {
        return damageSource != null && damageSource.is(DamageTypes.DRY_OUT);
    }

    public static boolean isGeneric(DamageSource damageSource) {
        return damageSource != null && damageSource.is(DamageTypes.GENERIC);
    }

    public static boolean isMagic(DamageSource damageSource) {
        return damageSource != null && damageSource.is(DamageTypes.MAGIC);
    }

    public static boolean isLava(DamageSource damageSource) {
        return damageSource != null && damageSource.is(DamageTypes.LAVA);
    }

    public static boolean isFall(DamageSource damageSource) {
        return damageSource != null && damageSource.is(DamageTypes.FALL);
    }

    public static boolean isExplosion(DamageSource damageSource) {
        return damageSource != null && (damageSource.is(DamageTypes.EXPLOSION)
                || damageSource.is(DamageTypes.PLAYER_EXPLOSION)
                || damageSource.is(DamageTypes.BAD_RESPAWN_POINT));
    }

    public static boolean isMobAttack(DamageSource damageSource) {
        return damageSource != null && damageSource.is(DamageTypes.MOB_ATTACK);
    }

    /**
     * 判断伤害源是否为弓箭
     */
    public static boolean isArrow(DamageSource damageSource) {
        return damageSource.is(DamageTypes.ARROW);
    }

    /**
     * 判断伤害源是否为三叉戟
     */
    public static boolean isTrident(DamageSource damageSource) {
        return damageSource.is(DamageTypes.TRIDENT);
    }

    /**
     * 判断伤害源是否为火球类（恶魂火球、烈焰人火球等）
     */
    public static boolean isFireball(DamageSource damageSource) {
        return damageSource.is(DamageTypes.FIREBALL)
                || damageSource.is(DamageTypes.UNATTRIBUTED_FIREBALL);
    }

    /**
     * 判断伤害源是否为凋零头颅
     */
    public static boolean isWitherSkull(DamageSource damageSource) {
        return damageSource.is(DamageTypes.WITHER_SKULL);
    }

    /**
     * 判断伤害源是否为烟花
     */
    public static boolean isFireworks(DamageSource damageSource) {
        return damageSource.is(DamageTypes.FIREWORKS);
    }

    /**
     * 判断伤害源是否为投掷物（雪球、鸡蛋、药水等）
     */
    public static boolean isThrown(DamageSource damageSource) {
        return damageSource.is(DamageTypes.THROWN);
    }

    /**
     * 判断伤害源是否为弹射物（通用）
     */
    public static boolean isMobProjectile(DamageSource damageSource) {
        return damageSource.is(DamageTypes.MOB_PROJECTILE);
    }

    /**
     * 统一判断是否为远程攻击
     * 包括所有弹射物类型
     */
    public static boolean isRangedAttack(DamageSource damageSource) {
        if (damageSource == null) return false;
        return damageSource instanceof AttackDamage attack && attack.delivery() == AttackDamage.Delivery.RANGED
                || damageSource.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile
                || damageSource.getDirectEntity() instanceof com.wzz.lobotocraft.entity.projectile.DeflectableAttack
                || damageSource.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                || isArrow(damageSource)
                || isTrident(damageSource)
                || isFireball(damageSource)
                || isWitherSkull(damageSource)
                || isFireworks(damageSource)
                || isThrown(damageSource)
                || isMobProjectile(damageSource);
    }

    /**
     * 判断伤害是否为近战攻击
     */
    public static boolean isMeleeAttack(DamageSource damageSource) {
        if (damageSource == null) return false;

        return damageSource.is(DamageTypes.MOB_ATTACK)
                || damageSource.is(DamageTypes.PLAYER_ATTACK)
                || damageSource.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
    }

    /**
     * 获取弹射物实体（如果有）
     */
    public static net.minecraft.world.entity.projectile.Projectile getProjectile(DamageSource damageSource) {
        if (damageSource.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
            return projectile;
        }
        return null;
    }

    /**
     * 判断是否为魔法攻击（药水、龙息等非弹射物的远程攻击）
     */
    public static boolean isMagicRanged(DamageSource damageSource) {
        return damageSource.is(DamageTypes.MAGIC)
                || damageSource.is(DamageTypes.INDIRECT_MAGIC)
                || damageSource.is(DamageTypes.DRAGON_BREATH)
                || damageSource.is(DamageTypes.SONIC_BOOM);
    }

    /**
     * 判断一个伤害源是否为处决伤害
     */
    public static boolean isExecution(DamageSource damageSource) {
        return damageSource.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD) ||
                damageSource.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
    }

    @SuppressWarnings("all")
    public static DamageSource getDamage(Entity attacker, String msg) {
        return getDamage(attacker, attacker, msg);
    }

    @SuppressWarnings("all")
    public static DamageSource getDamage(Entity directAttacker, Entity causingEntity, String msg) {
        return getDamage(directAttacker, causingEntity, msg,
                AttackDamage.Targeting.NORMAL, AttackDamage.Delivery.DIRECT);
    }

    public static DamageSource getDamage(Entity attacker, String color, AttackDamage.Targeting targeting) {
        return getDamage(attacker, attacker, color, targeting, AttackDamage.Delivery.DIRECT);
    }

    public static DamageSource getRangedDamage(Entity attacker, String color) {
        return getDamage(attacker, attacker, color, AttackDamage.Targeting.NORMAL, AttackDamage.Delivery.RANGED);
    }

    public static boolean isIndiscriminate(DamageSource source) {
        return source instanceof AttackDamage attack && attack.targeting() == AttackDamage.Targeting.INDISCRIMINATE;
    }

    public static AttackDamage.Targeting targetingOf(DamageSource source) {
        return isIndiscriminate(source) ? AttackDamage.Targeting.INDISCRIMINATE : AttackDamage.Targeting.NORMAL;
    }

    public static DamageSource getDamage(Entity directAttacker, Entity causingEntity, String msg,
            AttackDamage.Targeting targeting, AttackDamage.Delivery delivery) {
        if (msg.equals("pale")) msg = "blue";
        MinecraftServer server = directAttacker.getServer();
        if (server == null) return directAttacker.level.damageSources.generic();
        msg = StringUtil.normalizeTypeName(msg);
        Registry<DamageType> damageTypeRegistry =
                server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        ResourceKey<DamageType> damageTypeKey =
                ResourceKey.create(Registries.DAMAGE_TYPE, ResourceUtil.createInstanceWithColon(msg));
        Holder<DamageType> damageTypeHolder =
                damageTypeRegistry.getHolderOrThrow(damageTypeKey);
        AttackDamage attackDamage = new AttackDamage(damageTypeHolder, directAttacker, causingEntity, targeting, delivery);
        if (attackDamage instanceof IDamageSource iDamageSource) {
            iDamageSource.setDamageType(StringUtil.extractTypeName(msg));
        }
        return attackDamage;
    }
}
