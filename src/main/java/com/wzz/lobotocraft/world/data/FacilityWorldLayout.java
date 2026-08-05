package com.wzz.lobotocraft.world.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Fixed one-to-one bindings between the generated Minecraft company and the
 * original game's schematic nodes. World distances never affect screen
 * distances; the cuboids are only used to identify the logical room an entity
 * currently occupies.
 */
public final class FacilityWorldLayout {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCE = "/assets/lobotocraft/facility/world_layout.json";
    private static final int CONTAINMENT_FOOTPRINT_RADIUS = 16;
    private static final LayoutData DATA = load();

    private FacilityWorldLayout() {
    }

    public static List<FacilityAreaEntry> mergeAreas(ResourceLocation dimension,
                                                      List<FacilityAreaEntry> dynamicAreas) {
        List<FacilityAreaEntry> result = new ArrayList<>();
        for (FacilityAreaEntry dynamic : dynamicAreas) {
            FacilityAreaEntry copy = copy(dynamic);
            bindDynamicArea(copy);
            result.add(copy);
        }
        for (AreaDefinition definition : DATA.areas) {
            if (definition.dimension.equals(dimension)) result.add(definition.toEntry());
        }
        return List.copyOf(result);
    }

    public static void applyRoomBinding(RoomEntry room) {
        if (room == null || room.dimension == null || room.pos == null) return;
        ContainmentBinding binding = DATA.containments.get(key(room.dimension, room.pos));
        if (binding != null) room.departmentId = binding.departmentId;
    }

    public static Optional<ContainmentBinding> containmentAt(ResourceLocation dimension,
                                                              BlockPos markerPos) {
        if (dimension == null || markerPos == null) return Optional.empty();
        return Optional.ofNullable(DATA.containments.get(key(dimension, markerPos)));
    }

    public static List<ContainmentBinding> containmentBindings(ResourceLocation dimension) {
        return DATA.containments.values().stream()
                .filter(binding -> binding.dimension.equals(dimension))
                .sorted(Comparator.comparingInt((ContainmentBinding binding) -> binding.markerPos.getY())
                        .thenComparingInt(binding -> binding.markerPos.getX())
                        .thenComparingInt(binding -> binding.markerPos.getZ()))
                .toList();
    }

    /**
     * Returns the ticking chunks needed to cover each open facility section.
     * Sections are enveloped independently so opening one side department does
     * not load a still-locked department on the other side of the company.
     * Minecraft chunks are vertical columns, so this also intentionally covers
     * the lift connections between measured rooms on different Y levels.
     */
    public static Set<ChunkPos> facilityChunks(
            ResourceLocation dimension, BiPredicate<String, Integer> isSectionOpen) {
        if (dimension == null || isSectionOpen == null) return Set.of();

        Map<SectionKey, HorizontalBounds> boundsBySection = new HashMap<>();
        for (AreaDefinition area : DATA.areas) {
            if (!area.dimension.equals(dimension)) continue;
            sectionBounds(boundsBySection, area.departmentId, area.groupId)
                    .include(area.minPos, area.maxPos);
        }
        for (ContainmentBinding binding : DATA.containments.values()) {
            if (!binding.dimension.equals(dimension)) continue;
            BlockPos marker = binding.markerPos;
            sectionBounds(boundsBySection, binding.departmentId, binding.groupId)
                    .include(marker.offset(-CONTAINMENT_FOOTPRINT_RADIUS, 0,
                                    -CONTAINMENT_FOOTPRINT_RADIUS),
                            marker.offset(CONTAINMENT_FOOTPRINT_RADIUS, 0,
                                    CONTAINMENT_FOOTPRINT_RADIUS));
        }

        Set<ChunkPos> chunks = new HashSet<>();
        boundsBySection.forEach((section, bounds) -> {
            if (!isSectionOpen.test(section.departmentId, section.centralSection)) return;
            int minChunkX = bounds.minX >> 4;
            int maxChunkX = bounds.maxX >> 4;
            int minChunkZ = bounds.minZ >> 4;
            int maxChunkZ = bounds.maxZ >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    chunks.add(new ChunkPos(chunkX, chunkZ));
                }
            }
        });
        return Set.copyOf(chunks);
    }

    private static HorizontalBounds sectionBounds(Map<SectionKey, HorizontalBounds> sections,
                                                   String departmentId, String groupId) {
        int centralSection = "central".equals(departmentId)
                && groupId.startsWith("dept/central/lower/") ? 1 : 0;
        return sections.computeIfAbsent(new SectionKey(departmentId, centralSection),
                ignored -> new HorizontalBounds());
    }

    public static List<AreaDefinition> areasForGroup(ResourceLocation dimension, String groupId) {
        if (dimension == null || groupId == null || groupId.isBlank()) return List.of();
        return DATA.areas.stream()
                .filter(area -> area.dimension.equals(dimension) && area.groupId.equals(groupId))
                .toList();
    }

    /** Resolves a schematic node back to every real cuboid represented by it. */
    public static List<AreaDefinition> targetAreas(ServerLevel level, String groupId) {
        if (level == null || groupId == null || groupId.isBlank()) return List.of();
        ResourceLocation dimension = level.dimension().location();
        List<AreaDefinition> result = new ArrayList<>(areasForGroup(dimension, groupId));

        for (ContainmentBinding binding : containmentBindings(dimension)) {
            if (!binding.groupId.equals(groupId)) continue;
            FacilityAreaEntry dynamic = CompanyRoomData.get(level).getAreas(dimension).stream()
                    .filter(area -> area.type == FacilityAreaType.CONTAINMENT
                            && area.markerPos.equals(binding.markerPos))
                    .findFirst().orElse(null);
            BlockPos min = dynamic == null ? binding.markerPos.offset(-10, -5, -10) : dynamic.minPos;
            BlockPos max = dynamic == null ? binding.markerPos.offset(10, 5, 10) : dynamic.maxPos;
            result.add(new AreaDefinition("containment/" + binding.markerPos.asLong(),
                    dimension, binding.departmentId, FacilityAreaType.CONTAINMENT,
                    binding.groupId, min, max));
        }
        return List.copyOf(result);
    }

    public static Optional<AreaDefinition> areaAt(ResourceLocation dimension, BlockPos pos) {
        if (dimension == null || pos == null) return Optional.empty();
        return DATA.areas.stream()
                .filter(area -> area.dimension.equals(dimension) && area.contains(pos))
                .min(Comparator
                        .comparingInt((AreaDefinition area) ->
                                FacilityAreaSelection.typePriority(area.type))
                        .thenComparingLong(AreaDefinition::volume)
                        .thenComparing(AreaDefinition::id));
    }

    private static void bindDynamicArea(FacilityAreaEntry area) {
        if (area.dimension == null || area.markerPos == null) return;
        if (area.type == FacilityAreaType.CONTAINMENT) {
            ContainmentBinding binding = DATA.containments.get(key(area.dimension, area.markerPos));
            if (binding != null) {
                area.departmentId = binding.departmentId;
                area.groupId = binding.groupId;
            }
            return;
        }
        areaAt(area.dimension, area.markerPos).ifPresent(definition -> {
            area.departmentId = definition.departmentId;
            area.groupId = definition.groupId;
        });
    }

    private static FacilityAreaEntry copy(FacilityAreaEntry source) {
        FacilityAreaEntry copy = new FacilityAreaEntry(
                source.dimension, source.key, source.markerPos, source.minPos, source.maxPos,
                source.departmentId, source.type, source.groupId);
        copy.floorIndex = source.floorIndex;
        return copy;
    }

    private static LayoutData load() {
        try (InputStream stream = FacilityWorldLayout.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8)).getAsJsonObject();
            ResourceLocation dimension = requireResourceLocation(root.get("dimension").getAsString());

            List<AreaDefinition> areas = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("areas")) {
                JsonObject value = element.getAsJsonObject();
                areas.add(new AreaDefinition(
                        requireText(value, "id"), dimension,
                        requireText(value, "department"),
                        FacilityAreaType.fromSerializedName(requireText(value, "type")),
                        requireText(value, "group"),
                        blockPos(value.getAsJsonArray("from")),
                        blockPos(value.getAsJsonArray("to"))));
            }

            Map<String, ContainmentBinding> containments = new HashMap<>();
            for (JsonElement element : root.getAsJsonArray("containments")) {
                JsonObject value = element.getAsJsonObject();
                BlockPos marker = blockPos(value.getAsJsonArray("marker"));
                ContainmentBinding binding = new ContainmentBinding(
                        dimension, marker, requireText(value, "department"),
                        requireText(value, "group"), value.get("slot").getAsInt());
                if (binding.slotIndex < 0) {
                    throw new IllegalStateException("Negative containment slot at " + marker);
                }
                if (containments.put(key(dimension, marker), binding) != null) {
                    throw new IllegalStateException("Duplicate containment binding at " + marker);
                }
            }
            return new LayoutData(List.copyOf(areas), Map.copyOf(containments));
        } catch (Exception exception) {
            LOGGER.error("Unable to load fixed facility world layout", exception);
            return new LayoutData(List.of(), Map.of());
        }
    }

    private static ResourceLocation requireResourceLocation(String value) {
        ResourceLocation result = ResourceLocation.tryParse(value);
        if (result == null) throw new IllegalArgumentException("Invalid dimension: " + value);
        return result;
    }

    private static String requireText(JsonObject value, String field) {
        String result = value.get(field).getAsString().trim();
        if (result.isEmpty()) throw new IllegalArgumentException("Blank facility layout field: " + field);
        return result;
    }

    private static BlockPos blockPos(JsonArray values) {
        if (values == null || values.size() != 3) {
            throw new IllegalArgumentException("Facility coordinates require exactly three values");
        }
        return new BlockPos(values.get(0).getAsInt(), values.get(1).getAsInt(), values.get(2).getAsInt());
    }

    private static String key(ResourceLocation dimension, BlockPos pos) {
        return dimension + "@" + pos.asLong();
    }

    private record LayoutData(List<AreaDefinition> areas,
                              Map<String, ContainmentBinding> containments) {
    }

    private record SectionKey(String departmentId, int centralSection) {
    }

    private static final class HorizontalBounds {
        private int minX = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        private void include(BlockPos min, BlockPos max) {
            minX = Math.min(minX, Math.min(min.getX(), max.getX()));
            minZ = Math.min(minZ, Math.min(min.getZ(), max.getZ()));
            maxX = Math.max(maxX, Math.max(min.getX(), max.getX()));
            maxZ = Math.max(maxZ, Math.max(min.getZ(), max.getZ()));
        }
    }

    public record AreaDefinition(String id, ResourceLocation dimension, String departmentId,
                                 FacilityAreaType type, String groupId,
                                 BlockPos minPos, BlockPos maxPos) {
        public AreaDefinition {
            BlockPos first = minPos;
            BlockPos second = maxPos;
            minPos = new BlockPos(Math.min(first.getX(), second.getX()),
                    Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
            maxPos = new BlockPos(Math.max(first.getX(), second.getX()),
                    Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= minPos.getX() && pos.getX() <= maxPos.getX()
                    && pos.getY() >= minPos.getY() && pos.getY() <= maxPos.getY()
                    && pos.getZ() >= minPos.getZ() && pos.getZ() <= maxPos.getZ();
        }

        public long volume() {
            return (long) (maxPos.getX() - minPos.getX() + 1)
                    * (maxPos.getY() - minPos.getY() + 1)
                    * (maxPos.getZ() - minPos.getZ() + 1);
        }

        private FacilityAreaEntry toEntry() {
            BlockPos center = new BlockPos(
                    (minPos.getX() + maxPos.getX()) / 2,
                    (minPos.getY() + maxPos.getY()) / 2,
                    (minPos.getZ() + maxPos.getZ()) / 2);
            return new FacilityAreaEntry(dimension, "fixed:" + id, center,
                    minPos, maxPos, departmentId, type, groupId);
        }
    }

    public record ContainmentBinding(ResourceLocation dimension, BlockPos markerPos,
                                     String departmentId, String groupId, int slotIndex) {
    }
}
