package com.wzz.lobotocraft.llm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wzz.lobotocraft.logger.ModLogger;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class LlmConfig {
    public static final String CHAT_PREFIX = "#";
    public static final String API_KEY_ENV = "LOBOTOCRAFT_LLM_API_KEY";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("lobotocraft-llm-runtime.json");
    private static final State DEFAULT = new State(false,
            "https://api.openai.com/v1/chat/completions", "", 15);

    private static volatile String sessionApiKey = "";
    private static volatile State state = load();

    private LlmConfig() {
    }

    public static State state() {
        return state;
    }

    public static String apiKey() {
        String environment = System.getenv(API_KEY_ENV);
        return environment == null || environment.isBlank() ? sessionApiKey : environment.trim();
    }

    public static boolean hasApiKey() {
        return !apiKey().isBlank();
    }

    public static boolean keyFromEnvironment() {
        String environment = System.getenv(API_KEY_ENV);
        return environment != null && !environment.isBlank();
    }

    public static synchronized void update(boolean enabled, String endpoint, String model,
                                           int timeoutSeconds, KeyAction keyAction,
                                           String replacementKey) {
        String validatedEndpoint = validateEndpoint(endpoint);
        String validatedModel = model == null ? "" : model.trim();
        if (validatedModel.length() > 128) throw new IllegalArgumentException("模型名称过长");
        int validatedTimeout = Math.max(3, Math.min(60, timeoutSeconds));

        if (!keyFromEnvironment()) {
            if (keyAction == KeyAction.REPLACE) {
                String key = replacementKey == null ? "" : replacementKey.trim();
                if (key.isEmpty()) throw new IllegalArgumentException("新 API key 不能为空");
                if (key.length() > 512) throw new IllegalArgumentException("API key 过长");
                sessionApiKey = key;
            } else if (keyAction == KeyAction.CLEAR) {
                sessionApiKey = "";
            }
        }

        state = new State(enabled, validatedEndpoint, validatedModel, validatedTimeout);
        save(state);
    }

    private static State load() {
        if (!Files.isRegularFile(CONFIG_PATH)) return DEFAULT;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            boolean enabled = root.has("enabled") && root.get("enabled").getAsBoolean();
            String endpoint = root.has("endpoint") ? validateEndpoint(root.get("endpoint").getAsString())
                    : DEFAULT.endpoint();
            String model = root.has("model") ? root.get("model").getAsString().trim() : "";
            if (model.length() > 128) model = model.substring(0, 128);
            int timeout = root.has("timeoutSeconds") ? root.get("timeoutSeconds").getAsInt()
                    : DEFAULT.timeoutSeconds();
            return new State(enabled, endpoint, model, Math.max(3, Math.min(60, timeout)));
        } catch (Exception ignored) {
            return DEFAULT;
        }
    }

    public static synchronized void initializeServer(MinecraftServer server) {
        if (!keyFromEnvironment()) sessionApiKey = "";
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        sanitizeLegacyKey(worldRoot.resolve("serverconfig/lobotocraft-llm.toml"));
    }

    public static synchronized void clearSessionKey() {
        if (!keyFromEnvironment()) sessionApiKey = "";
    }

    private static void sanitizeLegacyKey(Path path) {
        if (!Files.isRegularFile(path)) return;
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
            boolean changed = false;
            String migratedKey = "";
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                String trimmed = line.trim();
                if (!trimmed.startsWith("apiKey")) continue;
                int firstQuote = line.indexOf('"');
                int lastQuote = line.lastIndexOf('"');
                if (firstQuote < 0 || lastQuote <= firstQuote) continue;
                String legacyKey = line.substring(firstQuote + 1, lastQuote);
                if (!legacyKey.isBlank() && migratedKey.isBlank() && !keyFromEnvironment()) {
                    migratedKey = legacyKey;
                }
                String indentation = line.substring(0, Math.max(0, line.indexOf("apiKey")));
                lines.set(index, indentation + "apiKey = \"\"");
                changed = changed || !legacyKey.isEmpty();
            }
            if (!changed) return;
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!migratedKey.isBlank() && sessionApiKey.isBlank() && !keyFromEnvironment()) {
                sessionApiKey = migratedKey;
            }
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
            ModLogger.LOGGER.warn("Unable to remove the legacy LLM API key from {}", path, exception);
        }
    }

    private static void save(State value) {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", value.enabled());
        root.addProperty("endpoint", value.endpoint());
        root.addProperty("model", value.model());
        root.addProperty("timeoutSeconds", value.timeoutSeconds());
        Path temporary = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, CONFIG_PATH, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                Files.move(temporary, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法保存 LLM 配置", exception);
        }
    }

    private static String validateEndpoint(String value) {
        String endpoint = value == null ? "" : value.trim();
        if (endpoint.length() > 512) throw new IllegalArgumentException("端点地址过长");
        final URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("端点必须是有效的 HTTP(S) 地址");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("端点必须是有效的 HTTP(S) 地址");
        }
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                && isLoopbackHost(uri.getHost());
        if (!(https || localHttp)) {
            throw new IllegalArgumentException("非本机端点必须使用 HTTPS");
        }
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return uri.resolve("/chat/completions").toString();
        }
        if (path.endsWith("/v1") || path.endsWith("/v1/")) {
            String separator = path.endsWith("/") ? "" : "/";
            return URI.create(uri.toString() + separator + "chat/completions").toString();
        }
        return uri.toString();
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null) return false;
        if ("localhost".equalsIgnoreCase(host)
                || "::1".equals(host) || "[::1]".equals(host)) return true;
        String[] octets = host.split("\\.", -1);
        if (octets.length != 4 || !"127".equals(octets[0])) return false;
        for (String octet : octets) {
            if (octet.isEmpty() || !octet.chars().allMatch(Character::isDigit)) return false;
            try {
                if (Integer.parseInt(octet) > 255) return false;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    public enum KeyAction {
        KEEP,
        REPLACE,
        CLEAR;

        public static KeyAction byId(String value) {
            try {
                return valueOf(value);
            } catch (Exception ignored) {
                return KEEP;
            }
        }
    }

    public record State(boolean enabled, String endpoint, String model, int timeoutSeconds) {
    }
}
