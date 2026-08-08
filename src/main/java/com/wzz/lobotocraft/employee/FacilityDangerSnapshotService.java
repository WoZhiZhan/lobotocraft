package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.ordeal.OrdealEntity;
import com.wzz.lobotocraft.util.FacilityCombatRules;
import com.wzz.lobotocraft.world.data.FacilityAreaEntry;
import com.wzz.lobotocraft.world.data.FacilityAreaSelection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves loaded hostile entities to stable monitor area identifiers. */
public final class FacilityDangerSnapshotService {
    private FacilityDangerSnapshotService() {
    }

    public static Set<String> collect(ServerLevel level, List<FacilityAreaEntry> visibleAreas) {
        Set<String> result = new LinkedHashSet<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) continue;
            boolean danger = entity instanceof AbstractAbnormality abnormality && abnormality.hasEscape()
                    || FacilityCombatRules.isOrdealCombatant(entity);
            if (!danger) continue;
            FacilityAreaSelection.containing(visibleAreas,
                            level.dimension().location(), entity.blockPosition())
                    .ifPresent(area -> result.add(area.groupId));
        }
        return Set.copyOf(result);
    }

}
