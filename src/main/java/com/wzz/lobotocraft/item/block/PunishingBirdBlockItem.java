package com.wzz.lobotocraft.item.block;

import com.wzz.lobotocraft.client.model.item.GenericGeoItemModel;
import com.wzz.lobotocraft.client.renderer.item.BaseGeoItemRenderer;
import com.wzz.lobotocraft.init.ModBlocks;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.core.animation.AnimatableManager;

import java.util.function.Consumer;

public class PunishingBirdBlockItem extends BaseGeoBlockItem {

    public PunishingBirdBlockItem() {
        super(ModBlocks.PUNISHING_BIRD.get(), new Properties().stacksTo(64));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @OnlyIn(Dist.CLIENT)
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