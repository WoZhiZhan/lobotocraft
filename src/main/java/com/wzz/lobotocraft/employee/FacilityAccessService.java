package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.block.entity.EscapeBlockEntity;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.FacilityAreaEntry;
import com.wzz.lobotocraft.world.data.FacilityAreaType;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** One fail-closed authority for department, section and containment-slot access. */
public final class FacilityAccessService {

    private FacilityAccessService() {
    }

    public static boolean isGroupOpen(MinecraftServer server, ResourceLocation dimension,
                                      String groupId) {
        Optional<Section> section = resolveSection(dimension, groupId);
        if (section.isEmpty() || !DepartmentProgressService.isSectionOpen(server,
                section.get().department, section.get().centralSection)) return false;

        List<FacilityWorldLayout.ContainmentBinding> slots = FacilityWorldLayout
                .containmentBindings(dimension).stream()
                .filter(binding -> binding.groupId().equals(groupId))
                .toList();
        if (slots.isEmpty()) return true;
        for (FacilityWorldLayout.ContainmentBinding slot : slots) {
            if (containmentAccess(server, slot) == ContainmentAccess.OPEN) return true;
        }
        return false;
    }

    public static ContainmentAccess containmentAccess(MinecraftServer server,
                                                       ResourceLocation dimension,
                                                       BlockPos markerPos) {
        return FacilityWorldLayout.containmentAt(dimension, markerPos)
                .map(binding -> containmentAccess(server, binding))
                .orElse(ContainmentAccess.UNKNOWN);
    }

    /**
     * Resolves a real world position through the measured facility geometry.
     * Unknown geometry fails closed so teleport/spawn callers cannot leak into
     * unopened or unclassified parts of the company.
     */
    public static boolean isPositionOpen(MinecraftServer server, ResourceLocation dimension,
                                         BlockPos position) {
        if (server == null || dimension == null || position == null) return false;
        Optional<FacilityWorldLayout.AreaDefinition> fixed =
                FacilityWorldLayout.areaAt(dimension, position);
        if (fixed.isEmpty()) fixed = FacilityWorldLayout.areaAt(dimension, position.above());
        if (fixed.isPresent()) {
            return isGroupOpen(server, dimension, fixed.get().groupId());
        }

        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) return false;
        return CompanyRoomData.get(level).getAreas(dimension).stream()
                .filter(area -> area.type == FacilityAreaType.CONTAINMENT
                        && contains(area, position))
                .min(java.util.Comparator.comparingLong(FacilityAccessService::volume))
                .map(area -> containmentAccess(server, dimension, area.markerPos)
                        == ContainmentAccess.OPEN)
                .orElse(false);
    }

    /** Loaded escape anchors that belong to measured, currently open facility areas. */
    public static List<BlockPos> openEscapeBlocks(ServerLevel level) {
        if (level == null) return List.of();
        ResourceLocation dimension = level.dimension().location();
        return EscapeBlockEntity.getEscapeBlocks(level.dimension()).stream()
                .filter(pos -> level.getBlockEntity(pos) instanceof EscapeBlockEntity)
                .filter(pos -> isPositionOpen(level.getServer(), dimension, pos))
                .map(BlockPos::immutable)
                .toList();
    }

    private static boolean contains(FacilityAreaEntry area, BlockPos position) {
        return position.getX() >= area.minPos.getX() && position.getX() <= area.maxPos.getX()
                && position.getY() >= area.minPos.getY() && position.getY() <= area.maxPos.getY()
                && position.getZ() >= area.minPos.getZ() && position.getZ() <= area.maxPos.getZ();
    }

    private static long volume(FacilityAreaEntry area) {
        long x = (long) area.maxPos.getX() - area.minPos.getX() + 1L;
        long y = (long) area.maxPos.getY() - area.minPos.getY() + 1L;
        long z = (long) area.maxPos.getZ() - area.minPos.getZ() + 1L;
        return Math.max(1L, x) * Math.max(1L, y) * Math.max(1L, z);
    }

    public static Optional<Section> resolveSection(ResourceLocation dimension, String groupId) {
        if (dimension == null || groupId == null || groupId.isBlank()) return Optional.empty();
        List<String> departments = new ArrayList<>();
        FacilityWorldLayout.areasForGroup(dimension, groupId).forEach(area ->
                departments.add(area.departmentId()));
        FacilityWorldLayout.containmentBindings(dimension).stream()
                .filter(binding -> binding.groupId().equals(groupId))
                .forEach(binding -> departments.add(binding.departmentId()));
        if (departments.isEmpty()) return Optional.empty();

        EmployeeDepartment department = EmployeeDepartment.byId(departments.get(0));
        if (department == EmployeeDepartment.UNASSIGNED) return Optional.empty();
        for (String value : departments) {
            if (EmployeeDepartment.byId(value) != department) return Optional.empty();
        }
        int centralSection = department == EmployeeDepartment.CENTRAL
                && groupId.startsWith("dept/central/lower/") ? 1 : 0;
        return Optional.of(new Section(department, centralSection));
    }

    private static ContainmentAccess containmentAccess(
            MinecraftServer server, FacilityWorldLayout.ContainmentBinding binding) {
        Optional<Section> section = resolveSection(binding.dimension(), binding.groupId());
        if (section.isEmpty()) return ContainmentAccess.UNKNOWN;
        if (!DepartmentProgressService.isSectionOpen(server,
                section.get().department, section.get().centralSection)) {
            return ContainmentAccess.DEPARTMENT_LOCKED;
        }
        int capacity = DepartmentProgressService.containmentSlotCapacity(server,
                section.get().department, section.get().centralSection);
        return binding.slotIndex() < capacity
                ? ContainmentAccess.OPEN : ContainmentAccess.UNEXPANDED;
    }

    public enum ContainmentAccess {
        OPEN,
        UNEXPANDED,
        DEPARTMENT_LOCKED,
        UNKNOWN
    }

    public record Section(EmployeeDepartment department, int centralSection) {
    }
}
