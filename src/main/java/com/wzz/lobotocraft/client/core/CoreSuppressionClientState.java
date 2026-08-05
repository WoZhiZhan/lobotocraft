package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import net.minecraft.client.Minecraft;

public final class CoreSuppressionClientState {
    private static final int TRANSITION_DURATION_TICKS = 60;

    private static boolean active;
    private static CoreSuppressionType type;
    private static String ownerName = "";
    private static int energy;
    private static int energyQuota;
    private static int meltdownLevel;
    private static int requiredMeltdownLevel;
    private static int visualStage;
    private static int completedMask;
    private static boolean fullVisualEffects;
    private static int transitionTicks = TRANSITION_DURATION_TICKS;
    private static long suppressionTicks;
    private static boolean netzachHasOverloaded;

    private CoreSuppressionClientState() {
    }

    public static void update(boolean active, int typeOrdinal, String ownerName,
                              int energy, int energyQuota,
                              int meltdownLevel, int requiredMeltdownLevel,
                              int visualStage, int completedMask,
                              boolean fullVisualEffects) {
        CoreSuppressionType nextType = CoreSuppressionType.byOrdinal(typeOrdinal);
        boolean entering = active && (!CoreSuppressionClientState.active || type != nextType);
        boolean sameSuppression = active && CoreSuppressionClientState.active && type == nextType;
        boolean phaseChanged = sameSuppression && CoreSuppressionClientState.visualStage != visualStage;
        boolean overloadAdvanced = sameSuppression && meltdownLevel > CoreSuppressionClientState.meltdownLevel;

        CoreSuppressionClientState.active = active;
        CoreSuppressionClientState.type = nextType;
        CoreSuppressionClientState.ownerName = ownerName;
        CoreSuppressionClientState.energy = energy;
        CoreSuppressionClientState.energyQuota = energyQuota;
        CoreSuppressionClientState.meltdownLevel = meltdownLevel;
        CoreSuppressionClientState.requiredMeltdownLevel = requiredMeltdownLevel;
        CoreSuppressionClientState.visualStage = visualStage;
        CoreSuppressionClientState.completedMask = completedMask;
        CoreSuppressionClientState.fullVisualEffects = fullVisualEffects;
        if (active && nextType != null) CoreSuppressionWarningClientState.clear();

        if (!active || nextType == null) {
            suppressionTicks = 0L;
            transitionTicks = TRANSITION_DURATION_TICKS;
            netzachHasOverloaded = false;
        } else if (entering) {
            suppressionTicks = 0L;
            netzachHasOverloaded = nextType == CoreSuppressionType.NETZACH && meltdownLevel >= 2;
            transitionTicks = nextType == CoreSuppressionType.YESOD
                    ? 0 : TRANSITION_DURATION_TICKS;
        } else if ((nextType == CoreSuppressionType.MALKUTH || nextType == CoreSuppressionType.YESOD)
                && phaseChanged) {
            transitionTicks = 0;
        } else if (nextType == CoreSuppressionType.NETZACH && overloadAdvanced) {
            netzachHasOverloaded = true;
            transitionTicks = 0;
        }
    }

    public static void tick() {
        CoreSuppressionPostProcessor.tick();
        if (!isActive() || Minecraft.getInstance().isPaused()) return;
        suppressionTicks++;
        if (transitionTicks < TRANSITION_DURATION_TICKS) transitionTicks++;
    }

    public static boolean isActive() {
        return active && type != null;
    }

    public static boolean isActive(CoreSuppressionType expected) {
        return isActive() && type == expected;
    }

    public static CoreSuppressionType getType() {
        return type;
    }

    public static String getOwnerName() {
        return ownerName;
    }

    public static int getEnergy() {
        return energy;
    }

    public static int getEnergyQuota() {
        return energyQuota;
    }

    public static int getMeltdownLevel() {
        return meltdownLevel;
    }

    public static int getRequiredMeltdownLevel() {
        return requiredMeltdownLevel;
    }

    public static int getVisualStage() {
        return visualStage;
    }

    public static float getTransitionRate(float partialTick) {
        if (type == CoreSuppressionType.NETZACH && !netzachHasOverloaded) return -1.0F;
        return Math.min(1.0F, (transitionTicks + partialTick) / TRANSITION_DURATION_TICKS);
    }

    public static float getEffectTime(float partialTick) {
        float seconds = 1.0F + (suppressionTicks + partialTick) / 20.0F;
        return seconds % 100.0F;
    }

    public static int getCompletedMask() {
        return completedMask;
    }

    public static boolean usesFullVisualEffects() {
        return fullVisualEffects;
    }

    public static boolean requirementsMet() {
        return type != null && energyQuota > 0
                && type.isFacilityObjectiveMet(energy, energyQuota, meltdownLevel);
    }
}
