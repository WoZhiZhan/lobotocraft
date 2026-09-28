package com.wzz.lobotocraft.client.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChesedPresentationResourcesTest {
    private static final Path RESOURCES = Path.of("src/main/resources/assets/lobotocraft");

    @Test
    void chesedUsesADedicatedRainShaderWithoutWaterRipple() throws IOException {
        String post = Files.readString(RESOURCES.resolve("shaders/post/core_chesed.json"));
        JsonObject postJson = JsonParser.parseString(post).getAsJsonObject();
        assertTrue(post.contains("lobotocraft:core_chesed_rain"));
        assertTrue(post.contains("RainSampler"));
        assertFalse(post.contains("core_water_drop"));
        assertFalse(post.contains("core_vignette"));

        JsonObject auxiliary = postJson.getAsJsonArray("passes").get(0).getAsJsonObject()
                .getAsJsonArray("auxtargets").get(0).getAsJsonObject();
        String auxiliaryId = auxiliary.get("id").getAsString();
        assertEquals("lobotocraft:core/chesed_rain_fx", auxiliaryId);
        String[] resourceParts = auxiliaryId.split(":", 2);
        assertEquals(2, resourceParts.length);
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets")
                .resolve(resourceParts[0]).resolve("textures/effect").resolve(resourceParts[1] + ".png")));

        String fragment = Files.readString(
                RESOURCES.resolve("shaders/program/core_chesed_rain.fsh"));
        JsonParser.parseString(Files.readString(
                RESOURCES.resolve("shaders/program/core_chesed_rain.json")));
        assertTrue(fragment.contains("uniform sampler2D RainSampler"));
        assertTrue(fragment.contains("uniform float Transition"));
        assertTrue(fragment.contains("mix(0.95, 0.55, rate)"));
        assertTrue(fragment.contains("mix(0.40, 1.80, rate)"));
        assertTrue(fragment.contains("mix(2.0, 0.5, rate)"));
        assertTrue(fragment.contains("mix(0.90, 0.15, rate)"));
        assertFalse(fragment.contains("impactRippleOffset"));
        assertFalse(fragment.contains("rippleDisplacement"));
        assertFalse(fragment.contains("rippleUv"));
        assertTrue(fragment.contains("texture(DiffuseSampler, texCoord)"));
        assertTrue(fragment.contains("vec3(0.64, 0.78, 1.12)"));
        assertTrue(fragment.contains("mix(rainLayer, blueFiltered, 0.38)"));
        assertTrue(fragment.contains("texture(RainSampler"));
        assertFalse(fragment.contains("fallingRain"));
        assertFalse(fragment.contains("impactRipples"));
        assertFalse(fragment.contains("hash11"));
        assertFalse(fragment.contains("hash12"));
        assertFalse(fragment.contains("Vignetting"));

        Path rainTexture = RESOURCES.resolve("textures/effect/core/chesed_rain_fx.png");
        assertTrue(Files.isRegularFile(rainTexture));
        BufferedImage image = ImageIO.read(rainTexture.toFile());
        assertNotNull(image);
        assertEquals(1024, image.getWidth());
        assertEquals(1024, image.getHeight());
    }

    @Test
    void chesedHudKeepsSourceSpritesButDrawsRuntimeIndicatorsDirectly() throws IOException {
        Path textureRoot = RESOURCES.resolve("textures/gui/core_suppression/chesed/damage");
        List<String> textureNames = List.of(
                "red_enabled.png", "red_disabled.png",
                "white_enabled.png", "white_disabled.png",
                "black_enabled.png", "black_disabled.png",
                "pale_enabled.png", "pale_disabled.png",
                "core.png");
        for (int i = 0; i < textureNames.size(); i++) {
            String textureName = textureNames.get(i);
            Path texture = textureRoot.resolve(textureName);
            assertTrue(Files.isRegularFile(texture), textureName);
            BufferedImage image = ImageIO.read(texture.toFile());
            assertNotNull(image, textureName);
            if (i < textureNames.size() - 1) {
                assertEquals(96, image.getWidth(), textureName);
                assertEquals(96, image.getHeight(), textureName);
            }
        }

        String overlay = Files.readString(Path.of(
                "src/main/java/com/wzz/lobotocraft/client/core/CoreSuppressionOverlay.java"));
        assertTrue(overlay.contains("textures/gui/core_suppression/chesed/damage/"));
        assertTrue(overlay.contains("drawChesedDamageIcon"));
        assertTrue(overlay.contains("drawTriangle"));
        assertFalse(overlay.contains("CHESED_DAMAGE_ENABLED"));
        assertFalse(overlay.contains("0, 0, 96, 96, 96, 96"));
        assertFalse(overlay.contains("kind.id().toUpperCase()"));
    }

    @Test
    void chesedHudTexturesHaveBlackRgbInFullyTransparentPixels() throws IOException {
        Path textureRoot = RESOURCES.resolve("textures/gui/core_suppression/chesed/damage");
        List<String> textureNames = List.of(
                "red_enabled.png", "red_disabled.png",
                "white_enabled.png", "white_disabled.png",
                "black_enabled.png", "black_disabled.png",
                "pale_enabled.png", "pale_disabled.png",
                "core.png");
        for (String textureName : textureNames) {
            BufferedImage image = ImageIO.read(textureRoot.resolve(textureName).toFile());
            assertNotNull(image, textureName);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    int alpha = (argb >>> 24) & 0xFF;
                    if (alpha == 0) {
                        assertEquals(0, argb & 0x00FFFFFF,
                                textureName + " has colored transparent pixel at " + x + "," + y);
                    }
                }
            }
        }
    }

    @Test
    void chesedHudTexturesHaveNoIsolatedVisiblePixels() throws IOException {
        Path textureRoot = RESOURCES.resolve("textures/gui/core_suppression/chesed/damage");
        List<String> textureNames = List.of(
                "red_enabled.png", "red_disabled.png",
                "white_enabled.png", "white_disabled.png",
                "black_enabled.png", "black_disabled.png",
                "pale_enabled.png", "pale_disabled.png",
                "core.png");
        for (String textureName : textureNames) {
            BufferedImage image = ImageIO.read(textureRoot.resolve(textureName).toFile());
            assertNotNull(image, textureName);
            List<Integer> smallComponents = smallVisibleComponents(image, 8, 4);
            assertTrue(smallComponents.isEmpty(),
                    textureName + " has isolated visible components: " + smallComponents);
        }
    }

    private static List<Integer> smallVisibleComponents(BufferedImage image, int alphaThreshold,
                                                         int componentLimit) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[] visited = new boolean[width * height];
        List<Integer> smallComponents = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (visited[index] || alpha(image, x, y) < alphaThreshold) continue;
                Queue<Integer> queue = new ArrayDeque<>();
                queue.add(index);
                visited[index] = true;
                int size = 0;
                while (!queue.isEmpty()) {
                    int current = queue.remove();
                    int currentX = current % width;
                    int currentY = current / width;
                    size++;
                    for (int offsetY = -1; offsetY <= 1; offsetY++) {
                        for (int offsetX = -1; offsetX <= 1; offsetX++) {
                            if (offsetX == 0 && offsetY == 0) continue;
                            int nextX = currentX + offsetX;
                            int nextY = currentY + offsetY;
                            if (nextX < 0 || nextX >= width || nextY < 0 || nextY >= height) continue;
                            int next = nextY * width + nextX;
                            if (!visited[next] && alpha(image, nextX, nextY) >= alphaThreshold) {
                                visited[next] = true;
                                queue.add(next);
                            }
                        }
                    }
                }
                if (size < componentLimit) smallComponents.add(size);
            }
        }
        return smallComponents;
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xFF;
    }
}
