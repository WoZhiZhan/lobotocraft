package com.wzz.lobotocraft.item.ego.base;

import com.wzz.lobotocraft.api.NameProvider;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.client.renderer.item.BaseEgoCurioRenderer;
import com.wzz.lobotocraft.item.AttributeEntry;
import com.wzz.lobotocraft.item.base.IBodyPartRenderer;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class BaseEgoCurio extends Item implements ICurioItem, GeoItem, IAttributeItem,
        IMentalValueItem, NameProvider, IWorkBonusItem, IBodyPartRenderer {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation MOVE_ANIMATION = RawAnimation.begin().thenLoop("move");
    public static final Map<Integer, Boolean> WEARER_MOVING = new HashMap<>();
    public static int currentRenderEntityId = -1;
    public static boolean isRenderingAsCurio = false;

    public BaseEgoCurio(Properties properties) {
        super(properties);
    }

    /**
     * 获取饰品名称（用于资源路径）
     */
    public abstract String curioName();

    public abstract boolean hasAnimatable();

    public boolean rendersOnWearer() {
        return true;
    }

    public boolean render2D() {
        return true;
    }

    /**
     * 子类重写返回 true 表示支持移动动画
     */
    public boolean hasMoveAnimation() {
        return false;
    }

    public boolean hasIdleAnimation() {
        return false;
    }

    @Override
    public boolean hasAttribute() {
        return false;
    }

    @Override
    @Deprecated
    public String getAttributeName() {
        return this.getCurrentClassName() + " Attribute";
    }

    @Override
    @Deprecated
    public UUID getAttributeUUID() {
        return null;
    }

    @Override
    @Deprecated
    public float getAttributeBonus() {
        return 0;
    }

    @Override
    @Deprecated
    public Mode getAttributeMode() {
        return Mode.ADDITION;
    }

    @Override
    @Deprecated
    public Attribute getAttribute() {
        return null;
    }

    @Override
    public boolean hasMentalBonus() {
        return false;
    }

    @Override
    public float getMentalBonus(net.minecraft.world.entity.player.Player player) {
        return 0.0f;
    }

    @Override
    public float getMentalRegenPerTick(net.minecraft.world.entity.player.Player player) {
        return 0.0f;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        if (!hasAnimatable()) return;

        controllers.add(new AnimationController<>(this, "move_controller", 0, state -> {
            if (!isRenderingAsCurio) return PlayState.STOP;
            boolean isMoving = WEARER_MOVING.getOrDefault(currentRenderEntityId, false);
            if (hasMoveAnimation() && isMoving) {
                state.getController().setAnimation(MOVE_ANIMATION);
                return PlayState.CONTINUE;
            }
            if (hasIdleAnimation() && !isMoving) {
                state.getController().setAnimation(IDLE_ANIMATION);
                return PlayState.CONTINUE;
            }
            return PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        LivingEntity livingEntity = slotContext.entity();
        if (!livingEntity.level.isClientSide && livingEntity.level instanceof ServerLevel serverLevel) {
            GeoItem.getOrAssignId(stack, serverLevel);
        }
        if (livingEntity.level.isClientSide) return;
        if (this.hasAttribute()) {
            applyAttributeModifiers(livingEntity);
        }
        if (this.hasMentalBonus() && livingEntity instanceof net.minecraft.world.entity.player.Player player) {
            applyMentalEffects(player);
        }
    }

    private void applyAttributeModifiers(LivingEntity entity) {
        List<AttributeEntry> entries = getAttributeEntries(entity);
        if (entries.isEmpty()) return;

        for (AttributeEntry entry : entries) {
            applyAttributeModifier(entity, entry);
        }
    }

    private void applyAttributeModifier(LivingEntity entity, AttributeEntry entry) {
        AttributeInstance attributeInstance = entity.getAttribute(entry.getAttribute());
        if (attributeInstance == null) return;
        entity.getPersistentData().putBoolean(entry.getName(), true);
        AttributeModifier existingModifier = attributeInstance.getModifier(entry.getUuid());
        if (existingModifier != null) {
            if (existingModifier.getAmount() != entry.getValue()) {
                attributeInstance.removeModifier(entry.getUuid());
                attributeInstance.addTransientModifier(entry.createModifier());
            }
        } else {
            attributeInstance.addTransientModifier(entry.createModifier());
        }
    }

    private void applyMentalEffects(net.minecraft.world.entity.player.Player player) {
        player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
            float maxBonus = getMentalBonus(player);
            if (maxBonus != 0) {
                mental.setExtraMentalValue(maxBonus);
            }
            float regenPerTick = getMentalRegenPerTick(player);
            if (regenPerTick > 0) {
                mental.addMentalValue(regenPerTick);
            }
        });
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        ICurioItem.super.onUnequip(slotContext, newStack, stack);
        LivingEntity entity = slotContext.entity();
        List<AttributeEntry> entries = getAttributeEntries(entity);
        boolean hasMaxHealth = false;

        for (AttributeEntry entry : entries) {
            AttributeInstance instance = entity.getAttribute(entry.getAttribute());
            if (instance != null) {
                instance.removeModifier(entry.getUuid());
            }
            EntityUtil.removeAttribute(entity, entry.getUuid());
            if (entry.getAttribute() == Attributes.MAX_HEALTH) {
                hasMaxHealth = true;
            }
        }
        entity.refreshDimensions();
        if (hasMaxHealth) {
            entity.heal(0.01f);
        }
        if (this.hasMentalBonus() && entity instanceof net.minecraft.world.entity.player.Player player) {
            player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                mental.setExtraMentalValue(0.0f);
                float effectiveMax = mental.getEffectiveMaxMentalValue();
                if (mental.getMentalValue() > effectiveMax) {
                    mental.setMentalValue(effectiveMax);
                }
            });
        }
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BaseEgoCurioRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null)
                    this.renderer = new BaseEgoCurioRenderer(curioName());

                return this.renderer;
            }
        });
    }
}
