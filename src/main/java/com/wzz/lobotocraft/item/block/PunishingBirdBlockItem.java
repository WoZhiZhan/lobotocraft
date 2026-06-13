package com.wzz.lobotocraft.item.block;

import com.wzz.lobotocraft.client.model.item.GenericGeoItemModel;
import com.wzz.lobotocraft.client.renderer.item.BaseGeoItemRenderer;
import com.wzz.lobotocraft.init.ModBlocks;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
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

public class PunishingBirdBlockItem extends BaseGeoBlockItem implements IClientItemExtensions {

    public PunishingBirdBlockItem() {
        super(ModBlocks.PUNISHING_BIRD.get(), new Properties().stacksTo(64));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
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
                                    "punishing_bird.geo.json",
                                    "punishing_bird.png",
                                    null
                            )
                    );
                }
                return this.renderer;
            }
        });
    }
}