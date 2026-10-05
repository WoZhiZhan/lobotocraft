package com.wzz.lobotocraft.entity.ai;

import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.event.definition.mental_value.MentalValueEvent;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.FearFeedbackPacket;
import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Minecraft adaptation of the original per-creature first-encounter horror system. */
public final class AbnormalityFearService {
    private static final String ENCOUNTERED_EMPLOYEES_TAG = "lobotocraft_fear_encountered_employees";
    private static final double ENCOUNTER_RANGE = 50.0D;
    private static final double VIEW_DOT_THRESHOLD = 0.5D;

    private AbnormalityFearService() {
    }

    public static void tickEncounter(Entity threat, AbnormalityFearSource source) {
        if (!(threat.level() instanceof ServerLevel level) || threat.tickCount % 5 != 0) return;
        for (ServerPlayer employee : level.players()) {
            if (employee.isCreative() || employee.isSpectator() || !isVisibleTo(employee, threat)
                    || !markFirstEncounter(threat, employee)) {
                continue;
            }
            AbnormalityFearResponse response = source.encounterFear(employee);
            apply(employee, response);
            if (response.level() >= 2 || response.forcePanic()) {
                dispatchFeedback(level, employee, response.level(), threat.getId());
            }
        }
    }

    public static void apply(ServerPlayer employee, AbnormalityFearResponse response) {
        if (response.forcePanic()) {
            MentalValueUtil.setMentalValue(employee, 0.0F, MentalValueEvent.ChangeType.REDUCE);
            return;
        }
        float fraction = response.maximumMentalFraction();
        if (fraction > 0.0F && !MentalValueUtil.isPanic(employee)) {
            MentalValueUtil.reduceMentalValue(employee,
                    MentalValueUtil.getEffectiveMaxMentalValue(employee) * fraction,
                    MentalValueEvent.ChangeType.REDUCE);
        }
    }

    public static AbnormalityFearResponse defaultResponse(AbstractAbnormality abnormality,
                                                            ServerPlayer employee) {
        RiskLevel risk = abnormality.getRiskLevel();
        if (risk == null) return AbnormalityFearResponse.level(0);
        int difference = risk.ordinal() - employeeLevel(employee);
        int level = difference < 0 ? 0 : Math.min(5, difference + 1);
        return AbnormalityFearResponse.level(level);
    }

    public static int employeeLevel(ServerPlayer employee) {
        return employee.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS)
                .map(stats -> stats.getEmployeeLevel())
                .orElse(1);
    }

    /** Suppression ends this threat's encounter; other living sources keep their memory. */
    public static void resetSourceEncounters(Entity threat) {
        if (threat != null && !threat.level().isClientSide) {
            threat.getPersistentData().remove(ENCOUNTERED_EMPLOYEES_TAG);
        }
    }

    /** A new life may encounter every living fear source again. */
    public static void resetEncounter(ServerPlayer employee) {
        if (employee == null || employee.server == null) return;
        String employeeId = employee.getUUID().toString();
        for (ServerLevel level : employee.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                CompoundTag data = entity.getPersistentData();
                if (!data.contains(ENCOUNTERED_EMPLOYEES_TAG)) continue;
                CompoundTag encountered = data.getCompound(ENCOUNTERED_EMPLOYEES_TAG);
                if (!encountered.contains(employeeId)) continue;
                encountered.remove(employeeId);
                if (encountered.isEmpty()) data.remove(ENCOUNTERED_EMPLOYEES_TAG);
                else data.put(ENCOUNTERED_EMPLOYEES_TAG, encountered);
            }
        }
    }

    private static void dispatchFeedback(ServerLevel level, ServerPlayer employee, int fearLevel, int threatEntityId) {
        for (ServerPlayer viewer : level.players()) {
            if (viewer instanceof EmployeeServerPlayer || viewer.isRemoved()
                    || viewer.distanceToSqr(employee) > 64.0D * 64.0D) {
                continue;
            }
            MessageLoader.getLoader().sendToPlayer(viewer,
                    new FearFeedbackPacket(employee.getId(), fearLevel, threatEntityId));
        }
    }

    /** The original encounteredWorker list belongs to each threat entity, not to the employee. */
    private static boolean markFirstEncounter(Entity threat, ServerPlayer employee) {
        CompoundTag encountered = threat.getPersistentData().getCompound(ENCOUNTERED_EMPLOYEES_TAG);
        String employeeId = employee.getUUID().toString();
        if (encountered.getBoolean(employeeId)) return false;
        encountered.putBoolean(employeeId, true);
        threat.getPersistentData().put(ENCOUNTERED_EMPLOYEES_TAG, encountered);
        return true;
    }

    private static boolean isVisibleTo(Player employee, Entity threat) {
        if (employee.distanceToSqr(threat) > ENCOUNTER_RANGE * ENCOUNTER_RANGE
                || !employee.hasLineOfSight(threat)) {
            return false;
        }
        Vec3 toThreat = threat.getEyePosition().subtract(employee.getEyePosition());
        return toThreat.lengthSqr() < 1.0E-6D
                || employee.getLookAngle().normalize().dot(toThreat.normalize()) >= VIEW_DOT_THRESHOLD;
    }
}
