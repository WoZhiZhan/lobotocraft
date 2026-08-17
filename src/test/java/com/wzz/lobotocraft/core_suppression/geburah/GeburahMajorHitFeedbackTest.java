package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeburahMajorHitFeedbackTest {
    @Test
    void everyBloodMistDamageTierHasAVisibleCameraImpulse() {
        GeburahMajorMove move = GeburahMajorMove.BLOOD_MIST;

        GeburahMajorHitFeedback.Profile chip =
                GeburahMajorHitFeedback.forCue(move, move.cues().get(1));
        GeburahMajorHitFeedback.Profile middle =
                GeburahMajorHitFeedback.forCue(move, move.cues().get(5));
        GeburahMajorHitFeedback.Profile finisher =
                GeburahMajorHitFeedback.forCue(move, move.cues().get(6));

        assertEquals(5, chip.durationTicks());
        assertEquals(0.45F, chip.amplitude(), 0.0001F);
        assertEquals(8, middle.durationTicks());
        assertEquals(0.82F, middle.amplitude(), 0.0001F);
        assertEquals(12, finisher.durationTicks());
        assertEquals(1.45F, finisher.amplitude(), 0.0001F);

        for (GeburahMajorMove.Cue cue : move.cues()) {
            GeburahMajorHitFeedback.Profile feedback =
                    GeburahMajorHitFeedback.forCue(move, cue);
            assertTrue(feedback.durationTicks() > 0);
            assertTrue(feedback.amplitude() > 0.0F);
        }
    }
}
