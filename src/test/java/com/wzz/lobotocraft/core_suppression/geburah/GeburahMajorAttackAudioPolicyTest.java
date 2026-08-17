package com.wzz.lobotocraft.core_suppression.geburah;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeburahMajorAttackAudioPolicyTest {
    @Test
    void normalBloodMistFinisherMatchesTheModelBladeRelease() {
        assertEquals(78, GeburahMajorMove.BLOOD_MIST.cues().get(6).tick());
    }

    @Test
    void onlyFinisherCueStartsTheMainBloodMistSound() {
        GeburahMajorMove move = GeburahMajorMove.BLOOD_MIST;

        assertEquals(GeburahMajorAttackAudioPolicy.HitSound.WINDUP,
                GeburahMajorAttackAudioPolicy.forCue(move, move.cues().get(1), 39, 8));
        assertEquals(GeburahMajorAttackAudioPolicy.HitSound.NONE,
                GeburahMajorAttackAudioPolicy.forCue(move, move.cues().get(2), 45, 39));
        assertEquals(GeburahMajorAttackAudioPolicy.HitSound.IMPACT,
                GeburahMajorAttackAudioPolicy.forCue(move, move.cues().get(6), 78, 59));
    }

    @Test
    void windupSoundCanReplayForSeparatedChipGroupsButNotAdjacentCues() {
        GeburahMajorMove move = GeburahMajorMove.EGO_BLOOD_MIST;

        assertEquals(GeburahMajorAttackAudioPolicy.HitSound.WINDUP,
                GeburahMajorAttackAudioPolicy.forCue(move, move.cues().get(1), 39, 0));
        assertEquals(GeburahMajorAttackAudioPolicy.HitSound.NONE,
                GeburahMajorAttackAudioPolicy.forCue(move, move.cues().get(3), 75, 62));
    }
}
