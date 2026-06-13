package com.wzz.lobotocraft.item.block;

import com.wzz.lobotocraft.client.model.item.GenericGeoItemModel;
import com.wzz.lobotocraft.client.renderer.item.BaseGeoItemRenderer;
import com.wzz.lobotocraft.init.ModBlocks;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class RegenerationReactorBlockItem extends BaseGeoBlockItem implements IClientItemExtensions {
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("animation.hx-1.1");
    
    public RegenerationReactorBlockItem() {
        super(ModBlocks.REGENERATION_REACTOR.get(), new Item.Properties().stacksTo(64));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }
    
    private PlayState predicate(AnimationState<RegenerationReactorBlockItem> state) {
        state.getController().setAnimation(IDLE_ANIMATION);
        return PlayState.CONTINUE;
    }

    @Override
    public void appendHoverText(ItemStack p_40572_, @Nullable Level p_40573_, List<Component> p_40574_, TooltipFlag p_40575_) {
        super.appendHoverText(p_40572_, p_40573_, p_40574_, p_40575_);
        p_40574_.add(Component.translatable("regeneration_reactor.text.1"));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BaseGeoItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new BaseGeoItemRenderer(
                            new GenericGeoItemModel<>(
                                    "regeneration_reactor.geo.json",
                                    "regeneration_reactor.png",
                                    "regeneration_reactor.animation.json"
                            )
                    );
                }
                return this.renderer;
            }
        });
    }
}