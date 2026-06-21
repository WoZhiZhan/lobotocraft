package com.wzz.lobotocraft.item.ego.end_bird;

import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.*;

/**
 * 薄暝（终末之鸟 E.G.O 武器，ALEPH）
 *
 * ※只有全属性(勇气/谨慎/自律/正义)满级(V)的玩家才能拿起这把武器。
 * ※普通攻击造成 红/白/黑/蓝 各一段 18 点伤害；蓝色段额外附带目标最大生命 10% 伤害(上限5点)。
 * ※右键长按蓄力(蓄力时间必须超过蓄力动画时长)，松开释放特殊攻击；
 *   在阶段攻击造成最后一段伤害后 1.5 秒内再次长按右键可提升至下一蓄力阶段。
 *   一阶(3→3-1)：无伤挥砍后剑影对身前 5x5 造成 红/白/黑/蓝/蓝 五段 30 点(蓝段+10%HP≤8)，顺序播放特殊音效1/2/3/4/4。
 *   二阶(3-2→3-3)：将单个生物传送至玩家身前 6 格并造成 1 点红伤(特殊音效1)；
 *                  期间玩家无法转向并获得最高级缓慢，生物无法移动；
 *                  收刀后造成 红/白/黑/蓝 四段 45 点(蓝段+10%HP≤15，出伤时播放二段专用音效)，随后转向恢复。
 *   三阶(3-4→3-5)：对身前 5x5 造成 红/白/黑/蓝 四段 100 点(蓝段+20%HP≤30)；
 *                  播放特殊音效1/2/3，造成伤害时播放特殊音效4；结束后蓄力攻击进入 30 秒冷却。
 * ※特殊攻击命中生物后给予其最高等级缓慢 1 秒。
 * ※持有此武器时终末鸟不会出现(见 hasWeaponHolder，由黑森林之门召唤处检查)。
 * 隐藏机制：蓄力进行与后摇期间无法左键普攻；蓄力期间移动速度大幅降低(约80%)。
 *
 * ─── 动画对照表 ─────────────────────────────
 *  "3"                  一阶蓄力   "3-1" 一阶攻击
 *  "3-2"                二阶蓄力   "3-3" 二阶攻击
 *  "3-4"                三阶蓄力   "3-5" 三阶攻击
 * ─────────────────────────────────────────────
 */
public class EndBirdWeapon extends BaseEgoWeapon {

    // ───────────── 蓄力阶段 ─────────────
    private static final int STAGE_NONE = 0;
    private static final int STAGE_1    = 1;
    private static final int STAGE_2    = 2;
    private static final int STAGE_3    = 3;

    // ───────────── NBT Key ─────────────
    private static final String KEY_CHARGE_STAGE     = "ChargeStage";     // 当前(或已预约)蓄力阶段
    private static final String KEY_CAN_CHARGE_UP    = "CanChargeUp";     // 连招窗口开启中
    private static final String KEY_LAST_DAMAGE_TICK = "LastDamageTick";  // 上次阶段攻击最后一段伤害时间
    private static final String KEY_COOLDOWN         = "UseTick";         // 三阶后的蓄力冷却(tick)
    private static final String KEY_IS_ANIMATING     = "IsAnimating";     // 蓄力/攻击/后摇进行中

    // ───────────── 动画名 ─────────────
    private static final String ANIM_CHARGE_1   = "3";
    private static final String ANIM_STAGE1_ATK = "3-1";
    private static final String ANIM_CHARGE_2   = "3-2";
    private static final String ANIM_STAGE2_ATK = "3-3";
    private static final String ANIM_CHARGE_3   = "3-4";
    private static final String ANIM_STAGE3_ATK = "3-5";

    /** 蓄力动画时长(tick)。"蓄力时间必须超过动画时间"即以此为最短蓄力时长,请与动画文件实际长度对齐 */
    private static final int CHARGE_ANIM_TICKS = 40;
    /** 连招窗口:最后一段伤害后 1.5 秒(30 tick)内再次右键可升阶 */
    private static final int COMBO_WINDOW_TICKS = 30;
    /** 三阶后的蓄力冷却:30 秒 */
    private static final int STAGE3_COOLDOWN_TICKS = 30 * 20;
    private static final int TIMER_TICKS_PER_SECOND = 20;

    // 伤害帧来自 animations/weapon/end_bird_weapon.animation.json,按 20 tick/s 换算。
    private static final int STAGE1_RED_HIT_TICK = animationTick(0.2917);
    private static final int STAGE1_WHITE_HIT_TICK = animationTick(0.4167);
    private static final int STAGE1_BLACK_HIT_TICK = animationTick(0.5417);
    private static final int STAGE1_BLUE_1_HIT_TICK = animationTick(0.6667);
    private static final int STAGE1_BLUE_2_HIT_TICK = animationTick(0.7917);
    private static final int STAGE1_END_TICK = animationTick(2.4167);
    private static final int STAGE2_DAMAGE_HIT_TICK = animationTick(1.75);
    private static final int STAGE2_END_TICK = animationTick(2.25);
    private static final int STAGE3_DAMAGE_HIT_TICK = animationTick(0.875);
    private static final int STAGE3_END_TICK = animationTick(1.875);
    public static final String THIN_DUSK_PASSIVE_MARK = "lobotocraft_thin_dusk_passive_until";
    public static final String THIN_DUSK_SPECIAL_DAMAGE = "lobotocraft_thin_dusk_special_damage";
    public static final String THIN_DUSK_SHIELD_COOLDOWN = "lobotocraft_thin_dusk_shield_cooldown_until";
    private static final int THIN_DUSK_PASSIVE_VULNERABLE_TICKS = 15 * 20;
    private static final int THIN_DUSK_SHIELD_TICKS = 15 * 20;
    private static final int THIN_DUSK_SHIELD_COOLDOWN_TICKS = 25 * 20;

    // =======================================================================
    //  构造 / 基础
    // =======================================================================

    public EndBirdWeapon() {
        // 基础攻击力18:Tier 加成 17 + SwordItem 基础 1 = 18;攻速 0.6 = 4.0 - 3.4
        super(new Tier(), 17, -3.4f,
                new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public String weaponName() { return "end_bird"; }

    /** 装备要求:勇气/谨慎/自律/正义全部满级(V) */
    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("JusticeLevel", 5);
        map.put("TemperanceLevel", 5);
        map.put("PrudenceLevel", 5);
        map.put("FortitudeLevel", 5);
        return map;
    }

    @Override public int getUseDuration(ItemStack stack)      { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override protected boolean hasIdle()       { return false; }
    @Override public boolean hasAnimatable()    { return true; }

    /** 持有此武器时终末鸟不会出现(黑森林之门召唤前调用此检查) */
    public static boolean hasWeaponHolder(ServerLevel level) {
        for (net.minecraft.server.level.ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            for (ItemStack st : p.getInventory().items) {
                if (st.getItem() instanceof EndBirdWeapon) return true;
            }
            if (p.getOffhandItem().getItem() instanceof EndBirdWeapon) return true;
        }
        return false;
    }

    public static boolean hasThinDuskSetWithCurio(Player player, Item curio) {
        return EgoArmorHelper.isFullEGO(player, "end_bird")
                && EgoArmorHelper.isHoldingWeapon(player, "end_bird")
                && CuriosUtil.hasCurios(player, curio);
    }

    public static void markPassiveVulnerable(LivingEntity target, Level level) {
        if (target == null || level == null || level.isClientSide) return;
        target.getPersistentData().putLong(THIN_DUSK_PASSIVE_MARK,
                level.getGameTime() + THIN_DUSK_PASSIVE_VULNERABLE_TICKS);
    }

    public static boolean isThinDuskSpecialDamage(Player player) {
        return player != null && player.getPersistentData().getBoolean(THIN_DUSK_SPECIAL_DAMAGE);
    }

    // =======================================================================
    //  注册动画
    // =======================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<BaseEgoWeapon> controller = new AnimationController<>(
                this, "controller", 0, state -> PlayState.CONTINUE);
        registerAdditionalAnimations(controller);
        controllers.add(controller);
    }

    @Override
    protected void registerAdditionalAnimations(AnimationController<BaseEgoWeapon> controller) {
        for (String name : new String[]{
                ANIM_CHARGE_1, ANIM_STAGE1_ATK,
                ANIM_CHARGE_2, ANIM_STAGE2_ATK,
                ANIM_CHARGE_3, ANIM_STAGE3_ATK
        }) {
            controller.triggerableAnim(name, RawAnimation.begin().thenPlay(name));
        }
    }

    // =======================================================================
    //  左键普通攻击:红/白/黑/蓝各18点,蓝段+10%HP(≤5)
    // =======================================================================

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!super.onLeftClickEntity(stack, player, entity)) return false; // 需满蓄力
        // 隐藏机制1:蓄力进行与后摇期间无法左键普攻
        if (stack.getOrCreateTag().getBoolean(KEY_IS_ANIMATING)) return false;
        if (player.isUsingItem()) return false;
        if (!canUseItem(player)) return false;
        if (!(entity instanceof LivingEntity target)) return false;

        if (!player.level().isClientSide) {
            playSound(player, ModSounds.END_BIRD_WEAPON_NORMAL.get());
            dealNormalHit(player, target, 0f);
        }
        return true;
    }

    private static int animationTick(double seconds) {
        return Math.max(1, (int) Math.round(seconds * TIMER_TICKS_PER_SECOND));
    }

    private void dealNormalHit(Player player, LivingEntity target, float bonus) {
        // 四段链式:红/白/黑/蓝 各18;蓝段额外附带目标最大生命10%伤害(上限5)
        // 每段前清除无敌帧,确保四段都能命中
        dealStep(player, target, "red", 18f + bonus);
        dealStep(player, target, "white", 18f + bonus);
        dealStep(player, target, "black", 18f + bonus);
        float blueExtra = EntityUtil.addMaxHealthPercentageDamage(target, 0.1f, 0.1f, 5f);
        dealStep(player, target, "blue", 18f + bonus + blueExtra);
    }

    /** 单段伤害:清除目标无敌帧后立即结算,保证多段连击每段都生效 */
    private void dealStep(Player player, LivingEntity target, String color, float damage) {
        target.hurtTime = 0;
        target.hurtDuration = 0;
        target.hurtMarked = false;
        target.invulnerableTime = 0;
        target.hurt(DamageHelper.getDamage(player, color), damage);
    }

    // =======================================================================
    //  右键蓄力:use / onUseTick / releaseUsing
    // =======================================================================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getOrCreateTag().getInt(KEY_COOLDOWN) > 0) {
            player.displayClientMessage(Component.literal(
                    "§c蓄力攻击冷却中：" + (stack.getOrCreateTag().getInt(KEY_COOLDOWN) / 20) + " 秒"), true);
            return InteractionResultHolder.pass(stack);
        }
        if (stack.getOrCreateTag().getBoolean(KEY_IS_ANIMATING)) return InteractionResultHolder.pass(stack);
        if (!canUseItem(player)) return InteractionResultHolder.pass(stack);

        // 连招窗口内再次右键:提升蓄力阶段(在开始蓄力的瞬间结算)
        if (!level.isClientSide && stack.getOrCreateTag().getBoolean(KEY_CAN_CHARGE_UP)) {
            long last = stack.getOrCreateTag().getLong(KEY_LAST_DAMAGE_TICK);
            if (level.getGameTime() - last <= COMBO_WINDOW_TICKS) {
                int cur = stack.getOrCreateTag().getInt(KEY_CHARGE_STAGE);
                if (cur >= STAGE_1 && cur < STAGE_3) {
                    stack.getOrCreateTag().putInt(KEY_CHARGE_STAGE, cur + 1);
                    player.displayClientMessage(Component.literal("§a蓄力提升至 " + (cur + 1) + " 阶段！"), true);
                }
            }
            stack.getOrCreateTag().putBoolean(KEY_CAN_CHARGE_UP, false);
        }

        // 播放对应阶段的蓄力动画
        if (!level.isClientSide) {
            int stage = Math.max(STAGE_1, stack.getOrCreateTag().getInt(KEY_CHARGE_STAGE));
            switch (stage) {
                case STAGE_2 -> triggerAnimation(player, stack, ANIM_CHARGE_2);
                case STAGE_3 -> triggerAnimation(player, stack, ANIM_CHARGE_3);
                default      -> triggerAnimation(player, stack, ANIM_CHARGE_1);
            }
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!(entity instanceof Player player)) return;
        int useTime = getUseDuration(stack) - remainingUseDuration;

        // 隐藏机制2:蓄力期间移动速度大幅降低(缓慢Ⅴ≈90%,以效果近似-80%,持续5tick自动过期)
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 4, false, false, false));

        // 蓄力进度提示
        if (useTime > 0 && useTime % 20 == 0) {
            int stage = Math.max(STAGE_1, stack.getOrCreateTag().getInt(KEY_CHARGE_STAGE));
            String state = useTime >= CHARGE_ANIM_TICKS ? "§a(已蓄满,松开释放)" : "§7(蓄力中...)";
            player.displayClientMessage(Component.literal("§e蓄力阶段 " + stage + " " + state), true);
        }

        super.onUseTick(level, entity, stack, remainingUseDuration);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        if (!(entity instanceof Player player)) return;

        int useTime = getUseDuration(stack) - timeCharged;
        // 蓄力时间必须超过蓄力动画时间,否则不释放
        if (useTime < CHARGE_ANIM_TICKS) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("§7蓄力不足……"), true);
            }
            return;
        }
        if (stack.getOrCreateTag().getInt(KEY_COOLDOWN) > 0) return;
        if (stack.getOrCreateTag().getBoolean(KEY_IS_ANIMATING)) return;
        if (!canUseItem(player)) return;

        int chargeStage = Math.max(STAGE_1, stack.getOrCreateTag().getInt(KEY_CHARGE_STAGE));

        // 进入攻击演出:期间禁普攻、禁再次蓄力(后摇结束时解除)
        stack.getOrCreateTag().putBoolean(KEY_IS_ANIMATING, true);
        stack.getOrCreateTag().putLong("AnimStartTick", level.getGameTime());
        stack.getOrCreateTag().putBoolean(KEY_CAN_CHARGE_UP, false);

        if (!level.isClientSide) {
            switch (chargeStage) {
                case STAGE_2 -> executeStage2Attack(player, stack);
                case STAGE_3 -> executeStage3Attack(player, stack);
                default      -> executeStage1Attack(player, stack);
            }
        }

        super.releaseUsing(stack, level, entity, timeCharged);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide) return;

        // 冷却倒计时
        int cd = stack.getOrCreateTag().getInt(KEY_COOLDOWN);
        if (cd > 0) stack.getOrCreateTag().putInt(KEY_COOLDOWN, cd - 1);

        // 连招窗口超时:重置蓄力阶段
        if (stack.getOrCreateTag().getBoolean(KEY_CAN_CHARGE_UP)) {
            long last = stack.getOrCreateTag().getLong(KEY_LAST_DAMAGE_TICK);
            if (level.getGameTime() - last > COMBO_WINDOW_TICKS) {
                stack.getOrCreateTag().putBoolean(KEY_CAN_CHARGE_UP, false);
                stack.getOrCreateTag().putInt(KEY_CHARGE_STAGE, STAGE_NONE);
            }
        }

        // 看门狗:动画/演出锁定超过6秒仍未解除则强制复位,避免武器卡成"无效武器"
        if (stack.getOrCreateTag().getBoolean(KEY_IS_ANIMATING)) {
            long animStart = stack.getOrCreateTag().getLong("AnimStartTick");
            if (level.getGameTime() - animStart > 120) {
                stack.getOrCreateTag().putBoolean(KEY_IS_ANIMATING, false);
            }
        }
    }

    // =======================================================================
    //  ★ 一阶:无伤挥砍 + 剑影 5x5 五段 红/白/黑/蓝/蓝 各30(蓝段+10%HP≤8)
    //          音效顺序:特殊1/2/3/4/4;结束后开 1.5 秒连招窗口
    // =======================================================================

    private void executeStage1Attack(Player player, ItemStack stack) {
        triggerAnimation(player, stack, ANIM_STAGE1_ATK);
        tryGrantLargeBirdShield(player);

        TimerEntry timer = new TimerEntry() {
            private int t = 0;
            private List<LivingEntity> targets;

            @Override
            public void onRunning(@NotNull LivingEntity living) {
                if (!(living instanceof Player p)) return;
                t++;

                if (t == 1) {
                    // 无伤害的挥砍 + 固定目标(剑影出现时的范围)
                    targets = getEntitiesInFront(p, 5.0, 2.5);
                    p.level().playSound(null, p.blockPosition(),
                            net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
                // 剑影五段跟随 3-1 动画里骨骼 "2" 的五次显现帧。
                if (t == STAGE1_RED_HIT_TICK) { dealHitToAll(p, targets, "red",   30f, 0f,   0f); playSpecialSound(p, 1); }
                if (t == STAGE1_WHITE_HIT_TICK) { dealHitToAll(p, targets, "white", 30f, 0f,   0f); playSpecialSound(p, 2); }
                if (t == STAGE1_BLACK_HIT_TICK) { dealHitToAll(p, targets, "black", 30f, 0f,   0f); playSpecialSound(p, 3); }
                if (t == STAGE1_BLUE_1_HIT_TICK) { dealHitToAll(p, targets, "blue",  30f, 0.1f, 8f); playSpecialSound(p, 4); }
                if (t == STAGE1_BLUE_2_HIT_TICK) { dealHitToAll(p, targets, "blue",  30f, 0.1f, 8f); playSpecialSound(p, 4); }

                if (t == STAGE1_END_TICK) {
                    // 等 3-1 演出收完后开启连招窗口,避免新蓄力打断剩余动画。
                    openComboWindow(p, stack, STAGE_1);
                    endAnimation(stack);
                    this.removeTimer(p.getUUID());
                }
            }
        };
        timer.addSkillTimer(player, 0, (STAGE1_END_TICK + 2) * 50, TIMER_TICKS_PER_SECOND);
    }

    // =======================================================================
    //  ★ 二阶:传送单个生物至身前6格+1红伤(特殊1);期间玩家锁视角+最高级缓慢,
    //          生物定身;收刀后 红/白/黑/蓝 四段45(蓝段+10%HP≤15,二段专用出伤音效);转向恢复
    // =======================================================================

    private void executeStage2Attack(Player player, ItemStack stack) {
        triggerAnimation(player, stack, ANIM_STAGE2_ATK);
        tryGrantLargeBirdShield(player);
        LivingEntity capturedTarget = getNearestEntityInFront(player, 20.0, 3.0);

        TimerEntry timer = new TimerEntry() {
            private int t = 0;
            private final LivingEntity target = capturedTarget;
            private Vec3 frozenPos = null;
            private float lockedYaw, lockedPitch;

            @Override
            public void onRunning(@NotNull LivingEntity living) {
                if (!(living instanceof Player p)) return;
                t++;

                if (t == 1) {
                    if (target == null || !target.isAlive()) {
                        // 无目标:直接进入连招窗口
                        openComboWindow(p, stack, STAGE_2);
                        endAnimation(stack);
                        this.removeTimer(p.getUUID());
                        return;
                    }
                    // 传送目标至玩家身前6格
                    Vec3 lookH = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize();
                    Vec3 dest = p.position().add(lookH.scale(6.0));
                    target.teleportTo(dest.x, p.getY(), dest.z);
                    frozenPos = new Vec3(dest.x, p.getY(), dest.z);
                    // 锁定玩家视角 + 最高级缓慢
                    lockedYaw = p.getYRot();
                    lockedPitch = p.getXRot();
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                            STAGE2_END_TICK + 5, 255, false, false));
                    // 1 点红色伤害(特殊音效1)
                    target.hurtTime = 0; target.hurtDuration = 0;
                    target.hurtMarked = false; target.invulnerableTime = 0;
                    hurtAsThinDuskSpecial(p, target, "red", 1f);
                    applySlowness(target);
                    playSpecialSound(p, 1);
                }

                // 冻结期:每tick锁定玩家视角(服务端),目标定身,持续到 3-3 动画收完。
                if (t >= 2 && t <= STAGE2_END_TICK) {
                    p.setYRot(lockedYaw);
                    p.setXRot(lockedPitch);
                    p.yRotO = lockedYaw;
                    p.xRotO = lockedPitch;
                    if (target != null && target.isAlive() && frozenPos != null) {
                        target.setPos(frozenPos.x, frozenPos.y, frozenPos.z);
                        target.setDeltaMovement(Vec3.ZERO);
                    }
                }

                // 收刀命中帧:同一瞬间结算 红45→白45→黑45→蓝45(+10%≤15)。
                if (target != null && target.isAlive()) {
                    if (t == STAGE2_DAMAGE_HIT_TICK) {
                        playSound(p, ModSounds.END_BIRD_WEAPON_STAGE2_HIT.get());
                        dealChainedStep(p, target,
                                new String[]{"red", "white", "black", "blue"},
                                null,
                                45f, 0.1f, 15f);
                    }
                }

                if (t == STAGE2_END_TICK) {
                    frozenPos = null;
                    openComboWindow(p, stack, STAGE_2);
                    endAnimation(stack);
                    this.removeTimer(p.getUUID());
                }
            }
        };
        timer.addSkillTimer(player, 0, (STAGE2_END_TICK + 2) * 50, TIMER_TICKS_PER_SECOND);
    }

    // =======================================================================
    //  ★ 三阶:身前5x5 红/白/黑/蓝 四段100(蓝段+20%HP≤30)
    //          音效:特殊1/2/3 蓄势,造成伤害时特殊4;结束后蓄力进入30秒冷却
    // =======================================================================

    private void executeStage3Attack(Player player, ItemStack stack) {
        triggerAnimation(player, stack, ANIM_STAGE3_ATK);
        tryGrantLargeBirdShield(player);

        TimerEntry timer = new TimerEntry() {
            private int t = 0;
            private List<LivingEntity> targets;

            @Override
            public void onRunning(@NotNull LivingEntity living) {
                if (!(living instanceof Player p)) return;
                t++;

                if (t == 1)  { targets = getEntitiesInFront(p, 5.0, 2.5); playSpecialSound(p, 1); }
                if (t == Math.max(1, STAGE3_DAMAGE_HIT_TICK - 2)) playSpecialSound(p, 2);
                if (t == Math.max(1, STAGE3_DAMAGE_HIT_TICK - 1)) playSpecialSound(p, 3);

                if (t == STAGE3_DAMAGE_HIT_TICK) {
                    playSpecialSound(p, 4);
                    dealHitToAll(p, targets, "red", 100f, 0f, 0f);
                    dealHitToAll(p, targets, "white", 100f, 0f, 0f);
                    dealHitToAll(p, targets, "black", 100f, 0f, 0f);
                    dealHitToAll(p, targets, "blue", 100f, 0.2f, 30f);
                }

                if (t == STAGE3_END_TICK) {
                    // 三阶为最终阶段:不开连招窗口,蓄力攻击进入30秒冷却
                    stack.getOrCreateTag().putInt(KEY_COOLDOWN, STAGE3_COOLDOWN_TICKS);
                    stack.getOrCreateTag().putInt(KEY_CHARGE_STAGE, STAGE_NONE);
                    endAnimation(stack);
                    this.removeTimer(p.getUUID());
                }
            }
        };
        timer.addSkillTimer(player, 0, (STAGE3_END_TICK + 2) * 50, TIMER_TICKS_PER_SECOND);
    }

    // =======================================================================
    //  工具方法
    // =======================================================================

    /** 玩家正前方 depth × (halfWidth*2) 矩形范围内所有存活生物(按朝向投影判定) */
    private List<LivingEntity> getEntitiesInFront(Player player, double depth, double halfWidth) {
        Vec3 pos = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 lookH = new Vec3(look.x, 0, look.z).normalize();
        Vec3 rightH = new Vec3(-lookH.z, 0, lookH.x);
        double r = Math.max(depth, halfWidth) + 1.0;
        AABB rough = new AABB(pos.x - r, pos.y - 2.0, pos.z - r, pos.x + r, pos.y + 4.0, pos.z + r);
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity e : player.level().getEntitiesOfClass(
                LivingEntity.class, rough, ent -> ent != player && ent.isAlive())) {
            Vec3 delta = e.position().subtract(pos);
            double fwd = delta.dot(lookH);
            double side = Math.abs(delta.dot(rightH));
            if (fwd >= 0 && fwd <= depth && side <= halfWidth) result.add(e);
        }
        return result;
    }

    /** 玩家视线方向最近的生物(二阶单体) */
    private LivingEntity getNearestEntityInFront(Player player, double maxDistance, double halfWidth) {
        List<LivingEntity> entities = getEntitiesInFront(player, maxDistance, halfWidth);
        if (entities.isEmpty()) return null;
        Vec3 pos = player.position();
        return entities.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(pos))).orElse(null);
    }

    /** 特殊攻击命中:给予目标最高等级缓慢 1 秒 */
    private void applySlowness(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 255, false, false));
    }

    private void playSpecialSound(Player player, int index) {
        SoundEvent sound = switch (index) {
            case 2 -> ModSounds.END_BIRD_WEAPON_SPECIAL_2.get();
            case 3 -> ModSounds.END_BIRD_WEAPON_SPECIAL_3.get();
            case 4 -> ModSounds.END_BIRD_WEAPON_SPECIAL_4.get();
            default -> ModSounds.END_BIRD_WEAPON_SPECIAL_1.get();
        };
        playSound(player, sound);
    }

    private void playSound(Player player, SoundEvent sound) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private boolean isThinDuskSpecialVulnerable(Player player, LivingEntity target) {
        if (!hasThinDuskSetWithCurio(player, ModItems.PUNISHING_BIRD_CURIO.get())) return false;
        return target.getPersistentData().getLong(THIN_DUSK_PASSIVE_MARK) > player.level().getGameTime();
    }

    private float scaleSpecialDamage(Player player, LivingEntity target, float damage) {
        return isThinDuskSpecialVulnerable(player, target) ? damage * 1.25f : damage;
    }

    private void hurtAsThinDuskSpecial(Player player, LivingEntity target, String color, float damage) {
        player.getPersistentData().putBoolean(THIN_DUSK_SPECIAL_DAMAGE, true);
        try {
            target.hurt(DamageHelper.getDamage(player, color), scaleSpecialDamage(player, target, damage));
        } finally {
            player.getPersistentData().putBoolean(THIN_DUSK_SPECIAL_DAMAGE, false);
        }
    }

    private void tryGrantLargeBirdShield(Player player) {
        if (!hasThinDuskSetWithCurio(player, ModItems.LARGEBIRD_CURIO.get())) return;
        long now = player.level().getGameTime();
        if (player.getPersistentData().getLong(THIN_DUSK_SHIELD_COOLDOWN) > now) return;

        float before = player.getAbsorptionAmount();
        float targetAbsorption = Math.max(before, player.getMaxHealth() * 0.5f);
        float granted = targetAbsorption - before;
        if (granted <= 0.0f) return;

        player.setAbsorptionAmount(targetAbsorption);
        player.getPersistentData().putLong(THIN_DUSK_SHIELD_COOLDOWN, now + THIN_DUSK_SHIELD_COOLDOWN_TICKS);

        TimerEntry timer = new TimerEntry() {
            @Override
            public void onEnd(@NotNull LivingEntity living) {
                float current = living.getAbsorptionAmount();
                if (current > before) {
                    living.setAbsorptionAmount(Math.max(before, current - granted));
                }
            }
        };
        timer.addSkillTimer(player, 0, THIN_DUSK_SHIELD_TICKS * 50, 1);
    }

    /** 对目标列表全体造成同色单段伤害(蓝色段可附带最大生命百分比伤害),命中附加最高级缓慢1秒 */
    private void dealHitToAll(Player player, List<LivingEntity> targets,
                              String color, float baseDamage, float bluePercent, float blueCap) {
        if (targets == null) return;
        for (LivingEntity t : targets) {
            if (!t.isAlive()) continue;
            float dmg = baseDamage;
            if ("blue".equals(color) && bluePercent > 0) {
                dmg += EntityUtil.addMaxHealthPercentageDamage(t, bluePercent, bluePercent, blueCap);
            }
            t.hurtTime = 0; t.hurtDuration = 0; t.hurtMarked = false; t.invulnerableTime = 0;
            hurtAsThinDuskSpecial(player, t, color, dmg);
            applySlowness(t);
        }
    }

    /** 对单个目标造成一个动画帧上的单段伤害,并播放对应特殊音效。 */
    private void dealOneWithSound(Player player, LivingEntity target,
                                  String color, int sound,
                                  float baseDamage, float bluePercent, float blueCap) {
        dealChainedStep(player, target, new String[]{color}, new int[]{sound}, baseDamage, bluePercent, blueCap);
    }

    /** 顺序结算多段伤害(每段前清无敌帧,保证段段命中) */
    private void dealChainedStep(Player player, LivingEntity target,
                                 String[] colors, int[] sounds,
                                 float baseDamage, float bluePercent, float blueCap) {
        for (int idx = 0; idx < colors.length; idx++) {
            if (!target.isAlive()) return;
            String color = colors[idx];
            float dmg = baseDamage;
            if ("blue".equals(color) && bluePercent > 0) {
                dmg += EntityUtil.addMaxHealthPercentageDamage(target, bluePercent, bluePercent, blueCap);
            }
            if (sounds != null && idx < sounds.length) playSpecialSound(player, sounds[idx]);
            target.hurtTime = 0; target.hurtDuration = 0; target.hurtMarked = false; target.invulnerableTime = 0;
            hurtAsThinDuskSpecial(player, target, color, dmg);
            applySlowness(target);
        }
    }

    /** 开启 1.5 秒连招窗口:在窗口内再次长按右键可提升至下一蓄力阶段 */
    private void openComboWindow(Player player, ItemStack stack, int completedStage) {
        stack.getOrCreateTag().putLong(KEY_LAST_DAMAGE_TICK, player.level().getGameTime());
        stack.getOrCreateTag().putBoolean(KEY_CAN_CHARGE_UP, true);
        stack.getOrCreateTag().putInt(KEY_CHARGE_STAGE, completedStage);
        player.displayClientMessage(Component.literal("§b1.5 秒内再次蓄力可提升阶段！"), true);
    }

    /** 演出结束:解除普攻/蓄力锁定 */
    private void endAnimation(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(KEY_IS_ANIMATING, false);
    }

    // =======================================================================
    //  Tooltip
    // =======================================================================

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> components, TooltipFlag flag) {
        super.appendHoverText(stack, level, components, flag);
        if (stack.getOrCreateTag().getInt(KEY_COOLDOWN) > 0) {
            components.add(Component.literal("§a蓄力冷却中：" + (stack.getOrCreateTag().getInt(KEY_COOLDOWN) / 20) + " 秒"));
        }
        if (ClientInputUtil.isShiftPressed()) {
            components.add(Component.literal("§6※只有全属性为满级的玩家才能拿起这把武器。"));
            components.add(Component.literal("§6※这把武器会同时造成物理，精神，侵蚀和灵魂伤害。"));
            components.add(Component.literal("§6※这把武器右键可蓄力进行特殊攻击，一共三种特殊攻击阶段。"));
            return;
        }
        components.add(Component.literal("§7大鸟那永不闭合的眼睛、高鸟那能衡量一切罪恶的天平、小鸟那能吞噬一切的巨口，这三者守护着黑森林的和平。"));
        components.add(Component.literal("§7而那些能够同时驾驭这三者的人也能带来和平。"));
        components.add(Component.literal("§7按住<Shift>查看详情"));
    }

    // =======================================================================
    //  Tier:攻击力 18(17+1),攻速 0.6,无耐久消耗
    // =======================================================================

    private static class Tier implements net.minecraft.world.item.Tier {
        @Override public int getUses()                    { return 0; }
        @Override public float getSpeed()                 { return 2.0F; }
        @Override public float getAttackDamageBonus()     { return 0.0F; }
        @Override public int getLevel()                   { return 2; }
        @Override public int getEnchantmentValue()        { return 14; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    }
}
