package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.world.data.FacilityAreaType;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** Resolves ordeal spawn anchors to measured, open facility corridors. */
public final class OrdealSpawnPointService {

    private OrdealSpawnPointService() {
    }

    /**
     * Returns every opened department/section once. Candidate corridors inside
     * each group are randomized, so callers can use the first valid one and
     * fall back only when entity collision prevents spawning there.
     */
    public static List<DepartmentCorridors> openCorridorsByDepartment(ServerLevel level) {
        if (level == null) return List.of();
        ResourceLocation dimension = level.dimension().location();
        List<BlockPos> loadedEscapeBlocks = FacilityAccessService.openEscapeBlocks(level).stream()
                .filter(pos -> EntityUtil.isInCompany(level, pos)).toList();

        List<DepartmentCorridors> grouped = classifyCorridors(dimension, loadedEscapeBlocks,
                groupId -> FacilityAccessService.isGroupOpen(level.getServer(), dimension, groupId));
        java.util.Random random = new java.util.Random(level.getRandom().nextLong());
        List<DepartmentCorridors> shuffled = new ArrayList<>(grouped.size());
        for (DepartmentCorridors group : grouped) {
            List<BlockPos> corridors = new ArrayList<>(group.corridors());
            java.util.Collections.shuffle(corridors, random);
            shuffled.add(new DepartmentCorridors(group.department(), List.copyOf(corridors)));
        }
        return List.copyOf(shuffled);
    }

    public static List<BlockPos> collectOpenCorridors(ServerLevel level) {
        return openCorridorsByDepartment(level).stream()
                .flatMap(group -> group.corridors().stream())
                .toList();
    }

    /** Validates non-corridor anchors, such as reactors, against the same map and unlock authority. */
    public static boolean isOpenMappedArea(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !EntityUtil.isInCompany(level, pos)) return false;
        ResourceLocation dimension = level.dimension().location();
        return FacilityWorldLayout.areaAt(dimension, pos)
                .map(area -> FacilityAccessService.isGroupOpen(
                        level.getServer(), dimension, area.groupId()))
                .orElse(false);
    }

    static List<DepartmentCorridors> classifyCorridors(ResourceLocation dimension,
                                                        Collection<BlockPos> candidates,
                                                        Predicate<String> groupOpen) {
        if (dimension == null || candidates == null || groupOpen == null) return List.of();
        Map<DepartmentKey, LinkedHashSet<BlockPos>> grouped = new LinkedHashMap<>();
        for (BlockPos candidate : candidates) {
            if (candidate == null) continue;
            Optional<FacilityWorldLayout.AreaDefinition> resolved =
                    FacilityWorldLayout.areaAt(dimension, candidate);
            if (resolved.isEmpty()) {
                // Escape blocks are embedded in the corridor floor. The walkable
                // logical area therefore commonly begins one block above them.
                resolved = FacilityWorldLayout.areaAt(dimension, candidate.above());
            }
            if (resolved.isEmpty()) continue;
            FacilityWorldLayout.AreaDefinition area = resolved.get();
            if (area.type() != FacilityAreaType.CORRIDOR || !groupOpen.test(area.groupId())) continue;

            Optional<FacilityAccessService.Section> section =
                    FacilityAccessService.resolveSection(dimension, area.groupId());
            if (section.isEmpty()) continue;
            DepartmentKey key = new DepartmentKey(section.get().department().id(),
                    section.get().centralSection());
            grouped.computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                    .add(candidate.immutable());
        }

        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator
                        .comparing(DepartmentKey::departmentId)
                        .thenComparingInt(DepartmentKey::section)))
                .map(entry -> new DepartmentCorridors(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    public record DepartmentKey(String departmentId, int section) {
    }

    public record DepartmentCorridors(DepartmentKey department, List<BlockPos> corridors) {
        public DepartmentCorridors {
            corridors = List.copyOf(corridors);
        }
    }
}
