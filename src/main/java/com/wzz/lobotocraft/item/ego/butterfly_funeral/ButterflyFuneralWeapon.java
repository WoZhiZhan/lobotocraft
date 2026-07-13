package com.wzz.lobotocraft.item.ego.butterfly_funeral;

import com.wzz.lobotocraft.client.renderer.item.ButterflyFuneralWeaponRenderer;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.init.ModParticleTypes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * E.G.O 「圣宣 · 蝶之葬礼」
 * <p>
 * 同一个物品类，用 NBT {@link #TAG_BLACK} 区分黑/白两把手枪。
 * 黑白只影响【贴图】和【伤害类型】，不影响动画：
 * 白枪 -> white 伤害 + butterfly_funeral_weapon.png
 * 黑枪 -> black 伤害 + butterfly_funeral_weapon_black.png
 * <p>
 * 动画（两把枪播的完全一样，只取决于是否宣判 / 是否连发）：
 * "1"   正常状态 单发开火
 * "2"   正常状态 三连发开火
 * "a-1" 宣判状态 持枪待机姿势（常驻，不开火）
 * "a-2" 宣判状态 开火
 * <p>
 * 宣判持枪要求：右手(主手)白枪 + 左手(副手)黑枪。
 */
public class ButterflyFuneralWeapon extends BaseEgoWeapon {

    /* ======================= 可调参数 ======================= */
    /** 攻击距离 */
    public static final double ATTACK_RANGE = 10.0D;
    /** 单发基础伤害 */
    public static final float BASE_DAMAGE = 2.0F;
    /** 基础开火冷却（tick） */
    public static final int BASE_COOLDOWN = 52;
    /** 每层蝶引减少的冷却：0.5 秒 = 10 tick */
    public static final int COOLDOWN_PER_BUTTERFLY = 10;
    /** 冷却下限，防止 5 层蝶引时冷却被减到 2 tick 变成无限机枪；想完全按数值走就改成 1 */
    public static final int MIN_COOLDOWN = 6;
    /** 三连发的发数 */
    public static final int BURST_SHOTS = 3;
    /** 三连发的每发间隔（tick） */
    public static final int BURST_INTERVAL = 3;

    /** 每命中多少次叠一层蝶引 */
    public static final int HITS_PER_BUTTERFLY = 10;
    /** 蝶引层数上限：未集齐 E.G.O 套装时 1 层，集齐时 5 层 */
    public static final int MAX_BUTTERFLY_BASE = 1;
    public static final int MAX_BUTTERFLY_FULL_EGO = 5;
    /** 蝶引持续时间 50s */
    public static final int BUTTERFLY_DURATION = 20 * 50;

    /** 救赎层数上限 */
    public static final int MAX_REDEMPTION = 50;
    /** 救赎持续时间 25s */
    public static final int REDEMPTION_DURATION = 20 * 25;
    /** 每层救赎提高的受伤比例 */
    public static final float REDEMPTION_DAMAGE_PER_LAYER = 0.01F;
    /** 满蝶引 + 满救赎时的额外蓝色伤害 */
    public static final float BLUE_BONUS_DAMAGE = 1.0F;

    /** E.G.O 套装标识（EgoArmorHelper.isFullEGO 用） */
    public static final String EGO_SET_NAME = "butterfly_funeral";

    /* ======================= NBT Key ======================= */
    /** 物品 NBT：是否为黑枪 */
    public static final String TAG_BLACK = "BFBlack";
    /** 物品 NBT：开火冷却剩余 tick */
    public static final String TAG_COOLDOWN = "UseTick";
    /** 物品 NBT：蝶引累计命中数（两把枪共享，写入时同步到双手） */
    public static final String TAG_HIT_COUNT = "BFHitCount";
    /** 物品 NBT：三连发剩余发数 */
    public static final String TAG_BURST = "BFBurst";
    /** 物品 NBT：三连发下一发的倒计时 */
    public static final String TAG_BURST_DELAY = "BFBurstDelay";
    /** 物品 NBT：宣判状态。写在物品上是为了自动同步给客户端做动画判定 */
    public static final String TAG_JUDGING = "BFJudging";
    /** 玩家 persistentData：宣判状态（服务端逻辑判定用） */
    public static final String PLAYER_TAG_JUDGING = "BFJudgement";

    /** 宣判：持枪姿势（按 R 后常驻，定格在最后一帧） */
    private static final String ANIM_JUDGE_POSE = "a-1";
    /** 宣判：开火 */
    private static final String ANIM_JUDGE_FIRE = "a-2";

    /**
     * 持枪姿势必须用 thenPlayAndHold：播一遍然后【定格在最后一帧】，那一帧就是架枪姿势。
     * 用 thenLoop 会每播完一遍就重头再来，看起来就是两把枪一直抽搐。
     */
    private static final RawAnimation JUDGE_POSE = RawAnimation.begin().thenPlayAndHold(ANIM_JUDGE_POSE);

    public ButterflyFuneralWeapon() {
        super(
                new Tier(),
                MathUtil.toDamageModifier(2),
                MathUtil.toSpeedModifier(2.6F),
                new Properties().stacksTo(1).fireResistant()
        );
    }

    /* =========================================================
     *  黑 / 白 形态
     * ========================================================= */

    public static boolean isBlack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getOrCreateTag().getBoolean(TAG_BLACK);
    }

    /** 给外部用：把一个圣宣物品变成黑枪形态 */
    public static ItemStack asBlack(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(TAG_BLACK, true);
        return stack;
    }

    /** 给外部用：直接创建一把黑枪。用法 createBlackWeapon(ModItems.BUTTERFLY_FUNERAL_WEAPON.get()) */
    public static ItemStack createBlackWeapon(Item item) {
        return asBlack(new ItemStack(item));
    }

    /** 给外部用：直接创建一把白枪 */
    public static ItemStack createWhiteWeapon(Item item) {
        return new ItemStack(item);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (isBlack(stack)) {
            return Component.empty()
                    .append(super.getName(stack))
                    .append(Component.literal(" [黑]").withStyle(ChatFormatting.DARK_GRAY));
        }
        return super.getName(stack);
    }

    /* =========================================================
     *  蝶引 / 救赎 / 宣判  —— 静态工具，事件类也会调用
     * ========================================================= */

    /** 玩家当前蝶引层数（0 = 没有） */
    public static int getButterflyLayers(LivingEntity living) {
        MobEffectInstance instance = living.getEffect(ModMobEffects.BUTTERFLY_FLIES.get());
        return instance == null ? 0 : instance.getAmplifier() + 1;
    }

    /** 生物当前救赎层数（0 = 没有） */
    public static int getRedemptionLayers(LivingEntity living) {
        MobEffectInstance instance = living.getEffect(ModMobEffects.REDEMPTION.get());
        return instance == null ? 0 : instance.getAmplifier() + 1;
    }

    /** 叠一层蝶引（已满则只刷新时间） */
    public static void addButterflyLayer(Player player) {
        int max = EgoArmorHelper.isFullEGO(player, EGO_SET_NAME) ? MAX_BUTTERFLY_FULL_EGO : MAX_BUTTERFLY_BASE;
        int next = Math.min(getButterflyLayers(player) + 1, max);
        player.addEffect(new MobEffectInstance(
                ModMobEffects.BUTTERFLY_FLIES.get(), BUTTERFLY_DURATION, next - 1, false, true, true));
    }

    /** 叠一层救赎（已满则只刷新时间） */
    public static void addRedemptionLayer(LivingEntity target) {
        int next = Math.min(getRedemptionLayers(target) + 1, MAX_REDEMPTION);
        target.addEffect(new MobEffectInstance(
                ModMobEffects.REDEMPTION.get(), REDEMPTION_DURATION, next - 1, false, true, true));
    }

    /** 玩家是否处于宣判状态（服务端逻辑判定） */
    public static boolean isJudging(Player player) {
        return player != null && player.getPersistentData().getBoolean(PLAYER_TAG_JUDGING);
    }

    /** 这把枪是否处于宣判状态（客户端动画判定，NBT 会自动同步） */
    public static boolean isJudgingStack(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof ButterflyFuneralWeapon
                && stack.getOrCreateTag().getBoolean(TAG_JUDGING);
    }

    /** 主手或副手是否拿着圣宣 */
    public static boolean isHoldingWeapon(Player player) {
        return player.getMainHandItem().getItem() instanceof ButterflyFuneralWeapon
                || player.getOffhandItem().getItem() instanceof ButterflyFuneralWeapon;
    }

    /** 宣判条件：满 E.G.O 套装 + 右手(主手)白枪 + 左手(副手)黑枪 */
    public static boolean canJudge(Player player) {
        if (!EgoArmorHelper.isFullEGO(player, EGO_SET_NAME)) return false;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        // 主手必须是白枪
        if (!(main.getItem() instanceof ButterflyFuneralWeapon) || isBlack(main)) return false;
        // 副手必须是黑枪
        return off.getItem() instanceof ButterflyFuneralWeapon && isBlack(off);
    }

    /** R 键切换宣判状态（由 ToggleJudgementPacket 在服务端调用） */
    public static void toggleJudgement(ServerPlayer player) {
        if (player == null) return;
        if (isJudging(player)) {
            exitJudgement(player);
            player.displayClientMessage(Component.literal("§7宣判 解除"), true);
            return;
        }
        if (!canJudge(player)) {
            player.displayClientMessage(
                    Component.literal("§7需要集齐 E.G.O 套装，并左手白枪、右手黑枪。"), true);
            return;
        }
        player.getPersistentData().putBoolean(PLAYER_TAG_JUDGING, true);

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        // 给两把枪各自分配独立的 GeckoLib 实例 id，否则双手会共用同一套动画状态
        GeoItem.getOrAssignId(main, player.serverLevel());
        GeoItem.getOrAssignId(off, player.serverLevel());
        main.getOrCreateTag().putBoolean(TAG_JUDGING, true);
        off.getOrCreateTag().putBoolean(TAG_JUDGING, true);

        player.displayClientMessage(Component.literal("§f▶ 宣判"), true);
    }

    /** 退出宣判（再按 R / 死亡 / 持枪条件不满足 时调用） */
    public static void exitJudgement(Player player) {
        if (player == null) return;
        player.getPersistentData().putBoolean(PLAYER_TAG_JUDGING, false);
        // 把背包里所有圣宣的宣判标记都清掉，避免掉在地上/丢进箱子的枪还保持架枪姿势
        for (ItemStack stack : player.getInventory().items) clearJudgingTag(stack);
        for (ItemStack stack : player.getInventory().offhand) clearJudgingTag(stack);
        clearJudgingTag(player.getMainHandItem());
        clearJudgingTag(player.getOffhandItem());
    }

    private static void clearJudgingTag(ItemStack stack) {
        if (stack.getItem() instanceof ButterflyFuneralWeapon && stack.hasTag()) {
            stack.getOrCreateTag().putBoolean(TAG_JUDGING, false);
        }
    }

    /* =========================================================
     *  开火
     * ========================================================= */

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canUseItem(player)) {
            return InteractionResultHolder.pass(stack);
        }
        CompoundTag tag = stack.getOrCreateTag();

        // 冷却中：必须返回 pass，原版才会把这次右键转交给另一只手（主手冷却 -> 触发副手）
        if (tag.getInt(TAG_COOLDOWN) > 0) {
            return InteractionResultHolder.pass(stack);
        }

        boolean judging = isJudging(player);
        int layers = getButterflyLayers(player);
        // 宣判常驻三连发；否则有蝶引才解锁三连发
        boolean burst = judging || layers > 0;

        int cooldown = Math.max(MIN_COOLDOWN, BASE_COOLDOWN - layers * COOLDOWN_PER_BUTTERFLY);
        tag.putInt(TAG_COOLDOWN, cooldown);

        if (!level.isClientSide) {
            SoundUtil.playSound(level, player, ModSounds.BUTTERFLY_FUNERAL_WEAPON.get());
            triggerAnimation(player, stack, getFireAnimation(judging, burst));
            performShot(level, player, stack);
            if (burst) {
                tag.putInt(TAG_BURST, BURST_SHOTS - 1);
                tag.putInt(TAG_BURST_DELAY, BURST_INTERVAL);
            }
        }
        // consume 而不是 success：避免原版手臂挥动盖掉 GeckoLib 动画，同时阻止这次右键继续传给副手
        return InteractionResultHolder.consume(stack);
    }

    /**
     * 开火动画。黑白两把枪播的完全一样：
     * 宣判状态   -> ANIM_JUDGE_FIRE（默认 "a-2"）
     * 正常 单发  -> "1"
     * 正常 三连发 -> "2"
     */
    private static String getFireAnimation(boolean judging, boolean burst) {
        if (judging) return ANIM_JUDGE_FIRE;
        return burst ? "2" : "1";
    }

    /** 单发：射线判定 + 白色光团弹道 + 命中后三只蝴蝶 */
    private void performShot(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) return;

        List<LivingEntity> targets = EntityUtil.findAllLivingEntitiesInLookDirection(player, ATTACK_RANGE);

        // 弹道：两把枪都是白色光团（末地烛），打到第一个目标就停
        double lineLength = ATTACK_RANGE;
        if (!targets.isEmpty()) {
            double d = player.getEyePosition().distanceTo(targets.get(0).position());
            lineLength = Math.min(ATTACK_RANGE, d + 0.5D);
        }
        ParticleUtil.spawnLineParticles(level, player, ParticleTypes.END_ROD, 24, 0.0D, lineLength);

        if (targets.isEmpty()) return;

        // 黑枪 -> 黑色伤害，白枪 -> 白色伤害
        DamageSource source = DamageHelper.getDamage(player, isBlack(stack) ? "black" : "white");

        for (LivingEntity target : targets) {
            if (!target.isAlive() || target.isRemoved()) continue;
            // 三连发必须清无敌帧，否则第 2、3 发会被 10tick 无敌帧吞掉
            EntityUtil.clearHurtTime(target);
            target.hurt(source, BASE_DAMAGE);
            // 命中特效：三只蝴蝶
            ParticleUtil.spawnParticlesAroundEntity(target, ModParticleTypes.BUTTERFLY.get(), 3, 0.03D);
        }

        // 命中生物才计数（开火一次算一次，不按命中数量重复计）
        addHitCount(player);
    }

    /** 命中计数：满 10 次 -> 叠一层蝶引并清零。计数写进双手的圣宣 NBT，两把枪共享。 */
    private static void addHitCount(Player player) {
        int count = getHitCount(player) + 1;
        if (count >= HITS_PER_BUTTERFLY) {
            count = 0;
            addButterflyLayer(player);
        }
        setHitCount(player, count);
    }

    private static int getHitCount(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof ButterflyFuneralWeapon) {
            return main.getOrCreateTag().getInt(TAG_HIT_COUNT);
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof ButterflyFuneralWeapon) {
            return off.getOrCreateTag().getInt(TAG_HIT_COUNT);
        }
        return 0;
    }

    private static void setHitCount(Player player, int count) {
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.getItem() instanceof ButterflyFuneralWeapon) {
                stack.getOrCreateTag().putInt(TAG_HIT_COUNT, count);
            }
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        CompoundTag tag = stack.getOrCreateTag();

        // 冷却两端都跑（客户端要靠它判断"主手冷却 -> 走副手"）
        int cooldown = tag.getInt(TAG_COOLDOWN);
        if (cooldown > 0) {
            tag.putInt(TAG_COOLDOWN, cooldown - 1);
        }

        if (level.isClientSide) return;
        if (!(entity instanceof Player player)) return;
        // 只有拿在手上的枪才继续三连发
        if (player.getMainHandItem() != stack && player.getOffhandItem() != stack) {
            tag.putInt(TAG_BURST, 0);
            return;
        }

        int burst = tag.getInt(TAG_BURST);
        if (burst <= 0) return;

        int delay = tag.getInt(TAG_BURST_DELAY);
        if (delay > 0) {
            tag.putInt(TAG_BURST_DELAY, delay - 1);
            return;
        }
        SoundUtil.playSound(level, player, ModSounds.BUTTERFLY_FUNERAL_WEAPON.get());
        performShot(level, player, stack);
        tag.putInt(TAG_BURST, burst - 1);
        tag.putInt(TAG_BURST_DELAY, BURST_INTERVAL);
    }

    /* =========================================================
     *  动画
     * ========================================================= */

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<BaseEgoWeapon> controller =
                new AnimationController<>(this, "controller", 0, state -> {
                    AnimationController<BaseEgoWeapon> ctrl = state.getController();

                    // 宣判状态：常驻持枪姿势（播一遍后定格在最后一帧）。
                    // ButterflyRenderContext.CURRENT 由渲染器在 renderByItem 里设置，
                    // 保证这里拿到的就是"当前正在渲染的那一把枪"。
                    if (isJudgingStack(ButterflyRenderContext.CURRENT)) {
                        ctrl.setAnimation(JUDGE_POSE);
                        return PlayState.CONTINUE;
                    }

                    // 退出宣判：thenPlayAndHold 把骨骼钉死在最后一帧了，
                    // 只返回 STOP 不会让 GeckoLib 把骨骼插值回默认姿势，必须显式把控制器打断。
                    // setAnimation(null) 内部会调 stop()，配合 forceAnimationReset() 清掉定格状态。
                    // 加 STOPPED 判断是为了让这段只在"刚退出"的那一帧跑一次，之后不再重复。
                    if (ctrl.getAnimationState() != AnimationController.State.STOPPED) {
                        ctrl.forceAnimationReset();
                        ctrl.setAnimation(null);
                    }

                    // 正常状态没有待机动画，开火动画走 triggerableAnim。
                    // triggerableAnim 优先级高于 predicate，所以返回 STOP 不影响它们播放，
                    // 而且播的是 thenPlay（不是 hold），播完能自动归位。
                    return PlayState.STOP;
                });

        controller.triggerableAnim("1", RawAnimation.begin().thenPlay("1"));
        controller.triggerableAnim("2", RawAnimation.begin().thenPlay("2"));
        controller.triggerableAnim(ANIM_JUDGE_FIRE, RawAnimation.begin().thenPlay(ANIM_JUDGE_FIRE));
        // ANIM_JUDGE_POSE 不注册成 triggerableAnim：它由上面的 predicate 常驻托管，
        // 再被 trigger 一次会把定格状态打断重播。

        controllers.add(controller);
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
        return "butterfly_funeral";
    }

    /** 换成能按 NBT 动态切贴图的渲染器 */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new ButterflyFuneralWeaponRenderer();
                }
                return this.renderer;
            }
        });
    }

    /* =========================================================
     *  杂项
     * ========================================================= */

    @Override
    public Map<String, Integer> getRequiredLevels() {
        Map<String, Integer> map = new HashMap<>();
        map.put("JusticeLevel", 3);
        return map;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.literal(isBlack(stack) ? "§8※ 黑色形态：造成黑色伤害。" : "§f※ 白色形态：造成白色伤害。"));
        tooltip.add(Component.literal("§6※累计一定攻击次数后，武器的威力会变强。"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§b[E.G.O 套装效果]"));
        tooltip.add(Component.literal("§7右手白枪、左手黑枪时按 §fR §7键进入宣判状态。"));
        tooltip.add(Component.literal("§7宣判状态下常驻三连发，且造成的所有伤害无视无敌帧。"));
        tooltip.add(Component.literal("§7每次造成伤害为目标叠加一层「救赎」，每层使其受到本武器持有者的伤害提高 1%（最多 50 层，25 秒）。"));
        tooltip.add(Component.literal("§7每命中 10 次为自身叠加一层「蝶引」，每层减少 0.5 秒开火间隔（最多 5 层，50 秒）。"));
        tooltip.add(Component.literal("§7拥有 5 层「蝶引」且目标拥有 50 层「救赎」时，每次伤害额外附带 1 点蓝色伤害。"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§7这两把枪令人感到严肃。"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§7死者之哀，死亡之惧，烙印其上。"));
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