package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.util.ResourceUtil;
import com.wzz.lobotocraft.world.data.EndBirdEggTracker;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Builds the one atomic client presentation used by the final egg transaction. */
final class EndBirdVictoryPresentation {
    private static final ResourceLocation VICTORY =
            ResourceUtil.createInstance("textures/gui/end_bird_cg/cg8.png");

    private EndBirdVictoryPresentation() {
    }

    static List<ResourceLocation> textures(String finalEggType) {
        ResourceLocation egg = switch (finalEggType == null ? "" : finalEggType) {
            case EndBirdEggTracker.EGG_EYE ->
                    ResourceUtil.createInstance("textures/gui/end_bird_cg/cg5.png");
            case EndBirdEggTracker.EGG_SMALL ->
                    ResourceUtil.createInstance("textures/gui/end_bird_cg/cg6.png");
            case EndBirdEggTracker.EGG_HIGH ->
                    ResourceUtil.createInstance("textures/gui/end_bird_cg/cg7.png");
            default -> null;
        };
        return egg == null ? List.of(VICTORY) : List.of(egg, VICTORY);
    }
}
