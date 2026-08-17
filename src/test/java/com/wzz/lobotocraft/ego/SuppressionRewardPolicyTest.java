package com.wzz.lobotocraft.ego;

import com.wzz.lobotocraft.entity.EntityGeburah;
import com.wzz.lobotocraft.entity.abnormality.EntityIsharmla;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SuppressionRewardPolicyTest {
    @Test
    void geburahSuppressionNeverGrantsPlayerOrRepositoryRewards() {
        assertFalse(SuppressionRewardPolicy.shouldGrantForTypes(
                EntityGeburah.class, null));
        assertFalse(SuppressionRewardPolicy.shouldGrantForTypes(
                null, EntityGeburah.class));
    }

    @Test
    void ordinaryPlayerAndUnattributedSuppressionKeepExistingRewards() {
        assertTrue(SuppressionRewardPolicy.shouldGrantForTypes(
                ServerPlayer.class, null));
        assertTrue(SuppressionRewardPolicy.shouldGrantForTypes(
                EntityIsharmla.class, null));
        assertTrue(SuppressionRewardPolicy.shouldGrantForTypes(null, null));
    }
}
