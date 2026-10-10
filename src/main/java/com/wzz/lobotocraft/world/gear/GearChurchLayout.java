package com.wzz.lobotocraft.world.gear;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored geometry metadata shared by pieces, spawn lifecycle and tests. No facility binding. */
public final class GearChurchLayout {
    public static final int WIDTH = 145, HEIGHT = 52, DEPTH = 134, PART_SIZE = 32, RESIDENTS = 16;
    public static final BlockPos CENTER = new BlockPos(WIDTH / 2, 0, DEPTH / 2);
    // Authored front porch: source (-5,-57,64), above its packed-mud floor and soil support.
    public static final BlockPos ENTRANCE = new BlockPos(72, 3, 130);
    public static final ResourceLocation ID = new ResourceLocation("lobotocraft", "gear_church");
    private static final JsonObject MANIFEST = load();
    public static final List<Part> PARTS = parts();
    public static final List<BlockPos> SPAWNS = positions("spawns");
    private static final int[] ROOF = MANIFEST.getAsJsonArray("roofHeights").asList().stream()
            .mapToInt(JsonElement::getAsInt).toArray();
    private static final Set<Long> FOUNDATION = foundation();

    private GearChurchLayout() {}
    private static JsonObject load() {
        try (var stream = GearChurchLayout.class.getResourceAsStream("/data/lobotocraft/structures/gear_church/manifest.json")) {
            if (stream == null) throw new IllegalStateException("Missing authored church manifest");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private static BlockPos pos(JsonArray a) { return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()); }
    private static List<Part> parts() {
        List<Part> parts = new ArrayList<>();
        for (var e : MANIFEST.getAsJsonArray("parts")) {
            var p = e.getAsJsonObject();
            parts.add(new Part(new ResourceLocation(p.get("template").getAsString()), pos(p.getAsJsonArray("offset"))));
        }
        return List.copyOf(parts);
    }
    private static List<BlockPos> positions(String key) {
        List<BlockPos> positions = new ArrayList<>();
        for (var e : MANIFEST.getAsJsonArray(key)) positions.add(pos(e.getAsJsonArray()));
        if (positions.size() != RESIDENTS) throw new IllegalStateException("Church must declare exactly sixteen resident slots");
        return List.copyOf(positions);
    }
    private static Set<Long> foundation() {
        Set<Long> columns = new HashSet<>();
        for (var e : MANIFEST.getAsJsonArray("foundationColumns")) {
            var a = e.getAsJsonArray();
            columns.add(new BlockPos(a.get(0).getAsInt(), 0, a.get(1).getAsInt()).asLong());
        }
        return Set.copyOf(columns);
    }
    public static BlockPos world(BlockPos local, BlockPos anchor, Rotation rotation) {
        return anchor.offset(local.subtract(CENTER).rotate(rotation));
    }
    public static BlockPos local(BlockPos world, BlockPos anchor, Rotation rotation) {
        Rotation inverse = switch (rotation) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> rotation;
        };
        return world.subtract(anchor).rotate(inverse).offset(CENTER);
    }
    public static boolean foundation(int x, int z) { return FOUNDATION.contains(new BlockPos(x, 0, z).asLong()); }
    public static int roofHeight(int x, int z) { return ROOF[z * WIDTH + x]; }
    public static boolean indoors(BlockPos local) {
        return local.getX() >= 0 && local.getX() < WIDTH && local.getZ() >= 0 && local.getZ() < DEPTH
                && foundation(local.getX(), local.getZ()) && local.getY() >= 2
                && local.getY() < roofHeight(local.getX(), local.getZ());
    }
    public record Part(ResourceLocation template, BlockPos offset) {}
}
