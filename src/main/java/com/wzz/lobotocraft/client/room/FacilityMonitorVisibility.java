package com.wzz.lobotocraft.client.room;

import com.wzz.lobotocraft.world.data.FacilityAreaEntry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Client-side view of the server-filtered facility sections and containment slots. */
public final class FacilityMonitorVisibility {

    private static final FacilityMonitorVisibility EMPTY =
            new FacilityMonitorVisibility(Set.of(), Set.of());

    private final Set<String> visibleGroups;
    private final Set<String> visibleSections;

    private FacilityMonitorVisibility(Set<String> visibleGroups, Set<String> visibleSections) {
        this.visibleGroups = Set.copyOf(visibleGroups);
        this.visibleSections = Set.copyOf(visibleSections);
    }

    public static FacilityMonitorVisibility empty() {
        return EMPTY;
    }

    public static FacilityMonitorVisibility fromAreas(List<FacilityAreaEntry> areas) {
        if (areas == null || areas.isEmpty()) return EMPTY;
        Set<String> groups = new HashSet<>();
        Set<String> sections = new HashSet<>();
        for (FacilityAreaEntry area : areas) {
            if (area == null || area.groupId == null || area.groupId.isBlank()) continue;
            String section = sectionPrefix(area.groupId);
            if (section == null) continue;
            groups.add(area.groupId);
            sections.add(section);
        }
        return new FacilityMonitorVisibility(groups, sections);
    }

    public boolean isVisible(FacilityLayoutTemplate.Area area) {
        if (area == null) return false;
        String section = sectionPrefix(area.areaId());
        if (section == null || !visibleSections.contains(section)) return false;
        return area.role() != FacilityLayoutTemplate.AreaRole.CONTAINMENT_ROOM
                || visibleGroups.contains(area.areaId());
    }

    static String sectionPrefix(String groupId) {
        if (groupId == null || !groupId.startsWith("dept/")) return null;
        String[] parts = groupId.split("/");
        if (parts.length < 3 || parts[1].isBlank()) return null;
        if ("central".equals(parts[1])) {
            if (parts.length < 4
                    || !("upper".equals(parts[2]) || "lower".equals(parts[2]))) return null;
            return "dept/central/" + parts[2] + "/";
        }
        return "dept/" + parts[1] + "/";
    }
}
