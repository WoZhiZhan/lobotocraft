package com.wzz.lobotocraft.core_suppression.chesed;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionData;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionEncounter;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

public final class ChesedEncounterService implements CoreSuppressionEncounter {
    public static final ChesedEncounterService INSTANCE = new ChesedEncounterService();

    private ChesedEncounterService() {
    }

    @Override
    public boolean start(MinecraftServer server, CoreSuppressionData suppression) {
        ChesedEncounterData.get(server.overworld()).start(server.overworld().random::nextInt);
        return true;
    }

    @Override
    public void tick(MinecraftServer server, CoreSuppressionData suppression) {
        if (!ChesedEncounterData.get(server.overworld()).isActive()) {
            CoreSuppressionManager.failChallenge(server, "Chesed 核心抑制状态丢失");
        }
    }

    @Override
    public void finish(MinecraftServer server, boolean success) {
        ChesedEncounterData.get(server.overworld()).clear();
    }

    @Override
    public int visualStage(MinecraftServer server) {
        return ChesedEncounterData.get(server.overworld()).getVisualStage();
    }

    @Override
    public int mechanicState(MinecraftServer server) {
        return ChesedEncounterData.get(server.overworld()).getSelectedDamageMask();
    }

    @Override
    public void onQliphothMeltdown(MinecraftServer server, CoreSuppressionData suppression,
                                    int overloadLevel) {
        ChesedEncounterData.get(server.overworld()).handleOverload(
                overloadLevel, server.overworld().random::nextInt);
    }

    @Override
    public float incomingDamageMultiplier(ServerPlayer target, DamageSource source) {
        if (target.level().dimension() != ModDimensions.LOBOTO_KEY
                || DamageHelper.isExecution(source)) {
            return 1.0F;
        }
        ChesedEncounterData data = ChesedEncounterData.get(target.serverLevel());
        if (!data.isActive()) return 1.0F;
        return ChesedDamagePolicy.multiplier(
                data.getSelectedDamageMask(), resolveDamageKind(source));
    }

    @Override
    public void onServerStartup(MinecraftServer server, CoreSuppressionData suppression) {
        ChesedEncounterData data = ChesedEncounterData.get(server.overworld());
        if (suppression.getActiveType() != CoreSuppressionType.CHESED && data.isActive()) {
            data.clear();
        }
    }

    private static ChesedDamageKind resolveDamageKind(DamageSource source) {
        if (DamageHelper.isWhiteDamage(source)) return ChesedDamageKind.WHITE;
        if (DamageHelper.isBlackDamage(source)) return ChesedDamageKind.BLACK;
        if (DamageHelper.isBlueDamage(source)) return ChesedDamageKind.PALE;
        return ChesedDamageKind.RED;
    }
}
