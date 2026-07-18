package com.wzz.lobotocraft.item.ego.base;

import com.wzz.lobotocraft.client.renderer.item.BaseEgoWeaponRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * E.G.O武器基类
 * 所有E.G.O武器都继承这个类
 */
public abstract class BaseEgoWeapon extends SwordItem implements GeoItem, IAnimatablePerspective, IEgoLevelItem {
    public static final String PARTIAL_ATTACK_TICK_TAG = "lobotocraft_partial_ego_attack_tick";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    private final RawAnimation ATTACK_ANIMATION = RawAnimation.begin().thenPlay(getAttackName());

    public BaseEgoWeapon(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);

        // 注册为可同步的动画物品
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    /**
     * E.G.O 武器挖掘方块时不消耗耐久,避免在低耐久时撸方块导致武器损坏。
     * (耐久只通过攻击/特殊机制消耗)
     */
    @Override
    public boolean mineBlock(ItemStack stack, Level level, net.minecraft.world.level.block.state.BlockState state,
                             net.minecraft.core.BlockPos pos, net.minecraft.world.entity.LivingEntity entity) {
        // 不调用 super 的耐久消耗逻辑
        return true;
    }

    /**
     * 获取武器名称（用于资源路径）
     * 例如：忏悔武器返回 "repentance"
     */
    public abstract String weaponName();

    public abstract boolean hasAnimatable();

    protected String getAttackName() {
        return "attack";
    }

    protected boolean hasIdle() {
        return hasAnimatable();
    }

    protected boolean autoRegisterAttackAnim() {
        return true;
    }

    protected String postFix() {
        return null;
    }

    /**
     * 触发攻击动画（子类可以调用）
     */
    protected void triggerAttackAnimation(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && hasAnimatable()) {
            triggerAnim(player, GeoItem.getOrAssignId(stack, serverPlayer.serverLevel()), "controller", getAttackName());
        }
    }

    /**
     * 触发自定义动画
     */
    protected void triggerAnimation(net.minecraft.world.entity.player.Player player, ItemStack stack, String name) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && hasAnimatable()) {
            triggerAnim(player, GeoItem.getOrAssignId(stack, serverPlayer.serverLevel()), "controller", name);
        }
    }

    /**
     * 停止自定义触发动画
     */
    protected void stopAnimation(net.minecraft.world.entity.player.Player player, ItemStack stack, String name) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && hasAnimatable()) {
            stopTriggeredAnim(player, GeoItem.getOrAssignId(stack, serverPlayer.serverLevel()), "controller", name);
        }
    }

    public void stopAnimation(Player player, ItemStack stack) {
        if (player.level().isClientSide()) return;
        var item = (GeoItem) stack.getItem();
        item.getAnimatableInstanceCache().getManagerForId(
                GeoItem.getId(stack)
        ).getAnimationControllers().forEach((name, controller) -> controller.forceAnimationReset());
    }

    /**
     * 子类可以重写此方法来注册额外的可触发动画
     * @param controller 动画控制器
     */
    protected void registerAdditionalAnimations(AnimationController<BaseEgoWeapon> controller) {
        // 默认不做任何事，子类可以重写添加自己需要的动画
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        if (item.getOrCreateTag().getInt("UseTick") > 0) {
            item.getOrCreateTag().remove("UseTick");
            return false;
        }
        return super.onDroppedByPlayer(item, player);
    }

    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot armorType, Entity entity) {
        if (entity instanceof Player player) {
            if (!canUseItem(player)) return false;
        }
        return super.canEquip(stack, armorType, entity);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        if (!canUseItem(player)) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        return super.use(world, player, hand);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotIndex, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotIndex, isSelected);
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        boolean fullyCharged = player.getAttackStrengthScale(0.0f) == 1.0f;
        if (!fullyCharged && !player.level().isClientSide) {
            player.getPersistentData().putLong(PARTIAL_ATTACK_TICK_TAG, player.level().getGameTime());
        }
        return fullyCharged;
    }

    public static boolean isMarkedPartialAttack(LivingEntity living) {
        if (!(living.getMainHandItem().getItem() instanceof BaseEgoWeapon)) return false;
        long attackTick = living.getPersistentData().getLong(PARTIAL_ATTACK_TICK_TAG);
        return attackTick > 0L && living.level().getGameTime() - attackTick <= 1L;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        if (!hasAnimatable()) return;

        // 创建动画控制器
        AnimationController<BaseEgoWeapon> controller = new AnimationController<>(this, "controller", 0, state -> {
            if (hasIdle())
                state.getController().setAnimation(IDLE_ANIMATION);
            return PlayState.CONTINUE;
        });

        if (autoRegisterAttackAnim()) {
            controller.triggerableAnim(getAttackName(), ATTACK_ANIMATION);
        }
        // 让子类注册额外的动画
        registerAdditionalAnimations(controller);

        controllers.add(controller);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BaseEgoWeaponRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null)
                    this.renderer = new BaseEgoWeaponRenderer(weaponName(), postFix());

                return this.renderer;
            }
        });
    }
}
