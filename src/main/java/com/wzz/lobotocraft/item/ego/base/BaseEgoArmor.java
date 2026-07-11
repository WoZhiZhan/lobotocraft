package com.wzz.lobotocraft.item.ego.base;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.client.renderer.item.BaseEgoArmorRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * E.G.O装备基类
 * 所有E.G.O装备都继承这个类，避免重复代码
 * 子类只需要实现抗性值和特殊效果即可
 */
public abstract class BaseEgoArmor extends ArmorItem implements IEgoArmor, GeoItem, IEgoLevelItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");

    public BaseEgoArmor(ArmorMaterial material, Type type, Properties properties) {
        super(material, type, properties);
    }

    /**
     * 获取装备名称（用于资源路径）
     * 例如：忏悔套装返回 "repentance"
     */
    public abstract String armorName();

    public abstract boolean hasAnimatable();

    /**
     * 是否使用分离的贴图
     * 返回true：每个部位使用独立的贴图文件（helmet.png, chestplate.png等）
     * 返回false：所有部位共用一个贴图文件（armor.png）
     */
    public boolean useSeparateTextures() {
        return true;
    }

    // 分离模型
    public boolean useSeparateModel() {
        return true;
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
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        if (!hasAnimatable()) return;
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            state.getController().setAnimation(IDLE_ANIMATION);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> swapWithEquipmentSlot(Item item, Level level, Player player, InteractionHand hand) {
        AtomicBoolean value = new AtomicBoolean(false);
        player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
            if (data.isArmorLocked()) {
                value.set(true);
            }
        });
        if (value.get()) {
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        return super.swapWithEquipmentSlot(item, level, player, hand);
    }

    @Override
    public @Nullable String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return ModMain.MODID + ":textures/air.png";
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private GeoArmorRenderer<?> renderer;

            @Override
            public @NotNull HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack,
                                                                   EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
                if (this.renderer == null)
                    this.renderer = new BaseEgoArmorRenderer(armorName());

                this.renderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
                return this.renderer;
            }
        });
    }
}