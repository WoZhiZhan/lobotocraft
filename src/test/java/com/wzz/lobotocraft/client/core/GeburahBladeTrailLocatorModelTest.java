package com.wzz.lobotocraft.client.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class GeburahBladeTrailLocatorModelTest {
    @Test
    void weaponLocatorsAreExportedAsTrackedChildBones() throws IOException {
        Map<String, ExpectedLocator> expected = new LinkedHashMap<>();
        expected.put("nitai2_start", locator("nitai2", -3.7D, 17.6D, -8.2D));
        expected.put("nitai2_end", locator("nitai2", -3.7D, 20.5D, -31.9D));
        expected.put("liadao_start", locator("liadao", -4.0D, 20.2D, -19.3D));
        expected.put("liadao_mid", locator("liadao", -4.0D, 12.4D, -23.1D));
        expected.put("liadao_end", locator("liadao", -4.0D, 3.8D, -26.6D));
        expected.put("changmao_maojian", locator("changmao", -4.1D, 18.9D, -18.8D));
        expected.put("zhongjian_start", locator("zhongjian", -4.1D, 16.5D, -4.5D));
        expected.put("zhongjian_end", locator("zhongjian", -4.1D, 18.0D, -27.8D));

        JsonObject geometry = loadGeometry();
        JsonArray bones = geometry.getAsJsonArray("bones");
        Map<String, JsonObject> byName = new LinkedHashMap<>();
        bones.forEach(element -> {
            JsonObject bone = element.getAsJsonObject();
            byName.put(bone.get("name").getAsString(), bone);
        });

        expected.forEach((name, locator) -> {
            JsonObject bone = byName.get(name);
            assertNotNull(bone, "Missing locator bone " + name);
            assertEquals(locator.parent(), bone.get("parent").getAsString());
            JsonArray pivot = bone.getAsJsonArray("pivot");
            assertArrayEquals(locator.pivot(), new double[]{
                    pivot.get(0).getAsDouble(), pivot.get(1).getAsDouble(),
                    pivot.get(2).getAsDouble()
            }, 0.000001D);
        });
    }

    private static JsonObject loadGeometry() throws IOException {
        String path = "/assets/lobotocraft/geo/geburah.geo.json";
        try (var stream = GeburahBladeTrailLocatorModelTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "Missing " + path);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8)).getAsJsonObject();
            return root.getAsJsonArray("minecraft:geometry")
                    .get(0).getAsJsonObject();
        }
    }

    private static ExpectedLocator locator(String parent, double x, double y, double z) {
        return new ExpectedLocator(parent, new double[]{x, y, z});
    }

    private record ExpectedLocator(String parent, double[] pivot) {
    }
}
