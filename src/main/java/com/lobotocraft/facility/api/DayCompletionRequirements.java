package com.lobotocraft.facility.api;

import com.lobotocraft.facility.data.FacilityDayData;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionData;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionType;
import com.wzz.lobotocraft.employee.DepartmentProgressData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class DayCompletionRequirements {
    private static final Map<String, DayCompletionRequirement> REQUIREMENTS = new LinkedHashMap<>();

    static {
        register(new DayCompletionRequirement() {
            @Override
            public String id() {
                return "energy";
            }

            @Override
            public boolean isSatisfied(ServerPlayer player, FacilityDayData data) {
                return data.getEnergy() >= data.getEnergyQuota();
            }

            @Override
            public Component unmetMessage(ServerPlayer player, FacilityDayData data) {
                return Component.translatable("message.lobotocraft.day.requirement.energy",
                        data.getEnergy(), data.getEnergyQuota());
            }
        });
        register(new DayCompletionRequirement() {
            @Override
            public String id() {
                return "department_expansion";
            }

            @Override
            public boolean isSatisfied(ServerPlayer player, FacilityDayData data) {
                return DepartmentProgressData.get(player.server).pendingDay() == 0;
            }

            @Override
            public Component unmetMessage(ServerPlayer player, FacilityDayData data) {
                return Component.translatable("message.lobotocraft.day.requirement.expansion");
            }
        });
        register(new DayCompletionRequirement() {
            @Override
            public String id() {
                return "core_suppression";
            }

            @Override
            public boolean isSatisfied(ServerPlayer player, FacilityDayData data) {
                CoreSuppressionData suppression = CoreSuppressionData.get(player.serverLevel());
                return !suppression.isActive()
                        || CoreSuppressionManager.requirementsMet(player.server, suppression);
            }

            @Override
            public Component unmetMessage(ServerPlayer player, FacilityDayData data) {
                CoreSuppressionType type = CoreSuppressionData.get(player.serverLevel()).getActiveType();
                int requiredLevel = type == null ? 0 : type.getRequiredMeltdownLevel();
                return Component.translatable("message.lobotocraft.day.requirement.core_suppression",
                        data.getEnergy(), data.getEnergyQuota(), data.getMeltdownLevel(), requiredLevel);
            }
        });
    }

    private DayCompletionRequirements() {
    }

    public static synchronized void register(DayCompletionRequirement requirement) {
        if (REQUIREMENTS.putIfAbsent(requirement.id(), requirement) != null) {
            throw new IllegalArgumentException("Duplicate day completion requirement: " + requirement.id());
        }
    }

    public static synchronized boolean allSatisfied(ServerPlayer player, FacilityDayData data) {
        return REQUIREMENTS.values().stream().allMatch(requirement -> requirement.isSatisfied(player, data));
    }

    public static synchronized List<Component> unmetMessages(ServerPlayer player, FacilityDayData data) {
        return REQUIREMENTS.values().stream()
            .filter(requirement -> !requirement.isSatisfied(player, data))
            .map(requirement -> requirement.unmetMessage(player, data))
            .toList();
    }
}
