package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.world.data.EndBirdEggTracker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EndBirdVictoryPresentationTest {
    @Test
    void finalEggIllustrationAlwaysPrecedesTheVictoryIllustration() {
        assertEquals(List.of(
                        "lobotocraft:textures/gui/end_bird_cg/cg6.png",
                        "lobotocraft:textures/gui/end_bird_cg/cg8.png"),
                EndBirdVictoryPresentation.textures(EndBirdEggTracker.EGG_SMALL).stream()
                        .map(Object::toString)
                        .toList());
    }

    @Test
    void legacyCompletionWithoutAnEggTypeStillShowsVictory() {
        assertEquals(List.of("lobotocraft:textures/gui/end_bird_cg/cg8.png"),
                EndBirdVictoryPresentation.textures(null).stream()
                        .map(Object::toString)
                        .toList());
    }
}
