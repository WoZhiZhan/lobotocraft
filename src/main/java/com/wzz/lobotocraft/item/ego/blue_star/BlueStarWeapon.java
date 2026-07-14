package com.wzz.lobotocraft.item.ego.blue_star;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.ScreenDistortionEffectPacket;
import com.wzz.lobotocraft.util.ClientInputUtil;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 新星之声 武器(ALEPH)。
 * 攻击力 12/18/24 白(精神)伤害,攻击冷却1.5秒,射程25格。
 * 右键:从身旁漂浮的"新星之声"处朝准心射出最多3段白色光束(25格射线),
 *   段数/伤害仅与当前精神值占最大精神的百分比有关:
 *     <30%   : 1段,每段12
 *     30~60% : 2段,每段18
 *     >60%   : 3段,每段24
 * Shift右键(120秒CD):特殊攻击,播放碧蓝新星攻击音效+屏幕扭曲,
 *   对全图敌对生物造成等同玩家精神值的白伤,解除该维度恐慌员工并回10%精神,
 *   并使该维度未出逃且计数器未满的异想体计数器+1。
 * 套装效果(武器+护甲+饰品):见 BlueStarSetEvent(命中减速、满精神光束+25%、致命复活)。
 */
public class BlueStarWeapon extends BaseEgoWeapon {

    private static final String KEY_COOLDOWN = "AttackCooldown";  // 普通攻击冷却(tick)
    private static final String KEY_SPECIAL_CD = "SpecialCooldown"; // 特殊攻击冷却(tick)
    private static final int ATTACK_COOLDOWN = 30;      // 1.5秒
    private static final int SPECIAL_COOLDOWN = 120 * 20; // 120秒
    private static final double BEAM_RANGE = 25.0;

    public BlueStarWeapon() {
        super(new ModTier.WeaponTier(), 0, -2.4f, new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String weaponName() { return "blue_star"; }

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("PrudenceLevel", 5);
        map.put("TemperanceLevel", 5);
        map.put("EmployeeLevel", 5);
        return map;
    }

    @Override public boolean hasAnimatable() { return true; }
    @Override protected String getAttackName() { return "idle"; }
    @Override protected boolean hasIdle() { return true; }

    @Override
    protected void registerAdditionalAnimations(AnimationController<BaseEgoWeapon> controller) {
        controller.triggerableAnim("special_attack",
                RawAnimation.begin().thenPlay("special_attack"));
    }

    // 左键不进行普通近战(本武器以右键光束为主),保留基类满蓄力判定即可
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        return super.onLeftClickEntity(stack, player, entity);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canUseItem(player)) return InteractionResultHolder.pass(stack);

        // Shift右键:特殊攻击
        if (player.isShiftKeyDown()) {
            int scd = stack.getOrCreateTag().getInt(KEY_SPECIAL_CD);
            if (scd > 0) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal(
                            "§b特殊攻击冷却中：" + (scd / 20) + " 秒"), true);
                }
                return InteractionResultHolder.pass(stack);
            }
            if (!level.isClientSide) {
                doSpecialAttack((ServerPlayer) player, stack);
            }
            return InteractionResultHolder.success(stack);
        }

        // 普通右键:光束攻击
        int cd = stack.getOrCreateTag().getInt(KEY_COOLDOWN);
        if (cd > 0) return InteractionResultHolder.pass(stack);
        stack.getOrCreateTag().putInt(KEY_COOLDOWN, ATTACK_COOLDOWN);

        if (!level.isClientSide) {
            fireBeams((ServerPlayer) player);
        }
        return InteractionResultHolder.success(stack);
    }

    // ==================== 光束攻击 ====================

    private void fireBeams(ServerPlayer player) {
        float cur = MentalValueUtil.getMentalValue(player);
        float max = MentalValueUtil.getEffectiveMaxMentalValue(player);
        float ratio = max > 0 ? cur / max : 0f;

        int orbCount = 1;
        if (ratio > 0.60f) orbCount = 3;
        else if (ratio >= 0.30f) orbCount = 2;

        float beamDamage = switch (orbCount) {
            case 3 -> 24.0F;
            case 2 -> 18.0F;
            default -> 12.0F;
        };

        // 第 1 段（总是存在）
        shootSingleBeam(player, getOrbPosition(player, 0, orbCount), beamDamage);
        // 第 2 段
        if (orbCount >= 2) {
            shootSingleBeam(player, getOrbPosition(player, 1, orbCount), beamDamage);
        }
        // 第 3 段
        if (orbCount >= 3) {
            shootSingleBeam(player, getOrbPosition(player, 2, orbCount), beamDamage);
        }

        player.level().playSound(null, player.blockPosition(),
                ModSounds.BLUE_STAR_ATTACK.get(), SoundSource.PLAYERS, 1.0f, 1.4f);
    }

    /**
     * 获取第 index 个漂浮光球的位置（0-based，共 total 个）
     * 光球在玩家右前方呈弧形分布，间距明显
     */
    private Vec3 getOrbPosition(Player player, int index, int total) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();

        if (total <= 1) {
            // 只有一个光球时，在右上前方
            return eye.add(right.scale(0.8)).add(0, 0.5, 0).add(look.scale(0.3));
        }

        // 多个光球时，分布在以 eye + 偏移中心 为圆心、半径 0.5 的圆弧上
        // 圆心位置：玩家右方 0.8、上方 0.5、前方 0.3
        Vec3 center = eye.add(right.scale(0.8)).add(0, 0.5, 0).add(look.scale(0.3));

        // 三个光球分布在 -40° 到 +40° 的弧上（相对于中心正上方）
        // index 0 -> -40°, index 1 -> 0°, index 2 -> +40°
        double startAngle = Math.toRadians(-40);
        double endAngle = Math.toRadians(40);
        double angleStep = (total > 2) ? (endAngle - startAngle) / (total - 1) : 0;
        double angle = startAngle + index * angleStep;

        // 在 center 的右-上平面内偏移
        Vec3 up = new Vec3(0, 1, 0);
        // 将 up 投影到与 look 垂直的平面，保证光球不会跑到玩家前方太远
        Vec3 orbitUp = up.subtract(look.scale(up.dot(look))).normalize();
        Vec3 orbitRight = right;  // right 已经与 look 垂直

        double radius = 0.5;
        return center
                .add(orbitUp.scale(Math.sin(angle) * radius))
                .add(orbitRight.scale(Math.cos(angle) * radius));
    }

    /** 从指定起点朝玩家准星方向发射一道25格白色光束 */
    private void shootSingleBeam(ServerPlayer player, Vec3 origin, float damage) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();

        // 从玩家眼睛做射线检测，找到准星实际指向的远点
        Vec3 eyeEnd = eye.add(look.scale(BEAM_RANGE));
        HitResult eyeHit = level.clip(new ClipContext(eye, eyeEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 targetPoint = eyeHit.getType() == HitResult.Type.BLOCK ? eyeHit.getLocation() : eyeEnd;

        // 光束方向：从光球 origin 指向准星目标点
        Vec3 beamDir = targetPoint.subtract(origin).normalize();
        Vec3 end = origin.add(beamDir.scale(BEAM_RANGE));

        // 方块阻挡裁剪（沿光束方向从 origin 出发）
        HitResult blockHit = level.clip(new ClipContext(origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 realEnd = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : end;

        // 扫描生物
        LivingEntity target = null;
        double bestDist = Double.MAX_VALUE;
        AABB scan = new AABB(origin, realEnd).inflate(1.0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, scan,
                en -> en != player && en.isAlive() && !(en instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            Vec3 rel = e.getBoundingBox().getCenter().subtract(origin);
            double along = rel.dot(beamDir);
            if (along < 0 || along > BEAM_RANGE) continue;
            double perp = rel.subtract(beamDir.scale(along)).length();
            if (perp > 1.2) continue;
            if (along < bestDist) { bestDist = along; target = e; }
        }

        Vec3 hitPoint = target != null ? target.getBoundingBox().getCenter() : realEnd;
        drawBeamParticles(level, origin, hitPoint);

        if (target != null) {
            EntityUtil.clearHurtTime(target);
            target.hurt(DamageHelper.getDamage(player, "white"), damage);
            if (target instanceof ServerPlayer sp) {
                MentalValueUtil.reduceMentalValue(sp, damage);
            }
            if (isBlueStarSet(player)) {
                target.getPersistentData().putLong("blue_star_slow_until",
                        level.getGameTime() + 200);
            }
        }
    }

    /** 光束粒子特效 */
    private void drawBeamParticles(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 0.01) return;
        Vec3 step = dir.normalize().scale(0.5);
        Vec3 p = from;
        for (double d = 0; d < len; d += 0.5) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.01);
            p = p.add(step);
        }
    }

    // ==================== 特殊攻击 ====================

    private void doSpecialAttack(ServerPlayer player, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        stack.getOrCreateTag().putInt(KEY_SPECIAL_CD, SPECIAL_COOLDOWN);

        // 攻击动画 + 碧蓝新星攻击音效 + 屏幕扭曲
        triggerAnimation(player, stack, "special_attack");
        level.playSound(null, player.blockPosition(),
                ModSounds.BLUE_STAR_ATTACK.get(), SoundSource.PLAYERS, 1.4f, 1.0f);
        MessageLoader.getLoader().sendToPlayer(player, new ScreenDistortionEffectPacket(0.3f, 30));

        float mentalDamage = MentalValueUtil.getMentalValue(player);

        // 全图范围
        AABB whole = new AABB(-30000000, level.getMinBuildHeight(), -30000000,
                30000000, level.getMaxBuildHeight(), 30000000);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, whole, LivingEntity::isAlive)) {
            if (target instanceof AbstractAbnormality abnormality) {
                if (abnormality.hasEscape()) {
                    EntityUtil.clearHurtTime(abnormality);
                    abnormality.hurt(DamageHelper.getDamage(player, "white"), mentalDamage);
                } else if (abnormality.getQliphothCounter() < abnormality.getMaxQliphothCounter()) {
                    abnormality.increaseQliphothCounter(1);
                }
                continue;
            }

            if (isSpecialAttackEnemy(target)) {
                EntityUtil.clearHurtTime(target);
                target.hurt(DamageHelper.getDamage(player, "white"), mentalDamage);
            }
        }

        for (ServerPlayer targetPlayer : level.getServer().getPlayerList().getPlayers()) {
            if (targetPlayer.serverLevel() == level && isPanicking(targetPlayer)) {
                recoverFromPanic(targetPlayer);
            }
        }

        player.displayClientMessage(Component.literal("§b新星之声：群星共鸣！"), true);
    }

    private boolean isSpecialAttackEnemy(LivingEntity target) {
        return !(target instanceof Player)
                && !(target instanceof EntityClerk)
                && !(target instanceof Villager)
                && target.getType().getCategory() == MobCategory.MONSTER;
    }

    private boolean isPanicking(ServerPlayer player) {
        return MentalValueUtil.getMentalValue(player) <= 0.0F
                || player.getPersistentData().getBoolean("isharmla_panic");
    }

    private void recoverFromPanic(ServerPlayer player) {
        float current = MentalValueUtil.getMentalValue(player);
        float recoveredMental = Math.max(1.0F, MentalValueUtil.getEffectiveMaxMentalValue(player) * 0.10F);
        MentalValueUtil.setMentalValue(player, Math.max(current, recoveredMental));
        player.getPersistentData().putBoolean("isharmla_panic", false);
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    // ==================== 冷却倒计时 ====================

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide) return;
        int cd = stack.getOrCreateTag().getInt(KEY_COOLDOWN);
        if (cd > 0) stack.getOrCreateTag().putInt(KEY_COOLDOWN, cd - 1);
        int scd = stack.getOrCreateTag().getInt(KEY_SPECIAL_CD);
        if (scd > 0) stack.getOrCreateTag().putInt(KEY_SPECIAL_CD, scd - 1);

        // 持握时身旁漂浮"新星之声"的粒子提示
        if (isSelected && entity instanceof Player player && level instanceof ServerLevel sl
                && player.tickCount % 6 == 0) {
            float cur = MentalValueUtil.getMentalValue(player);
            float max = MentalValueUtil.getEffectiveMaxMentalValue(player);
            float ratio = max > 0 ? cur / max : 0f;
            int orbCount = 1;
            if (ratio > 0.60f) orbCount = 3;
            else if (ratio >= 0.30f) orbCount = 2;

            for (int i = 0; i < orbCount; i++) {
                Vec3 pos = getOrbPosition(player, i, orbCount);
                sl.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    /** 是否穿戴全套新星之声(武器+护甲+饰品) */
    public static boolean isBlueStarSet(Player player) {
        return com.wzz.lobotocraft.util.EgoArmorHelper.isWearingFullSet(player, "blue_star")
                && com.wzz.lobotocraft.util.EgoArmorHelper.isHoldingWeapon(player, "blue_star")
                && hasBlueStarCurio(player);
    }

    private static boolean hasBlueStarCurio(Player player) {
        for (ItemStack st : com.wzz.lobotocraft.util.CuriosUtil.getCuriosItems(player)) {
            if (st.getItem() instanceof BlueStarCurio) return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> components, TooltipFlag flag) {
        super.appendHoverText(stack, level, components, flag);
        int scd = stack.getOrCreateTag().getInt(KEY_SPECIAL_CD);
        if (scd > 0) {
            components.add(Component.literal("§b特殊攻击冷却：" + (scd / 20) + " 秒"));
        }
        if (ClientInputUtil.isShiftPressed()) {
            components.add(Component.literal("§6※右键攻击会根据持有者当前精神值生成“新星之声”光束。"));
            components.add(Component.literal("§7右键从身旁射出最多三段白色光束(射程25格)。"));
            components.add(Component.literal("§7精神<30%：1段，每段12白伤。"));
            components.add(Component.literal("§7精神30%~60%：2段，每段18白伤。"));
            components.add(Component.literal("§7精神>60%：3段，每段24白伤。"));
            components.add(Component.literal("§7Shift右键(120秒)：对全图敌对生物造成等同精神值的白伤，"));
            components.add(Component.literal("§7解除该维度恐慌员工并回10%精神，未出逃异想体计数器+1。"));
            return;
        }
        components.add(Component.literal("§7新星自我们的绝望中闪耀。在它的光芒之下，众生皆为平等。"));
        components.add(Component.literal("§7按住<Shift>查看详情"));
    }
}
