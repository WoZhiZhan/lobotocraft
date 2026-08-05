package com.wzz.lobotocraft.compat.ysm;

import com.wzz.lobotocraft.employee.EmployeeAppearance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Server-authoritative model keys used to filter the multiplayer picker. */
public record YsmModelCatalog(boolean authoritative, Set<String> keys,
                              Map<String, Set<String>> texturesByKey) {
    private static final String AUTHORITATIVE_TAG = "YsmCatalogAuthoritative";
    private static final String MODELS_TAG = "YsmServerModels";
    private static final String MODEL_KEY_TAG = "Key";
    private static final String TEXTURES_TAG = "Textures";
    private static final int MAX_MODELS = 4096;
    private static final int MAX_TEXTURES_PER_MODEL = 256;
    private static final int MAX_TEXTURES_TOTAL = 16384;

    public YsmModelCatalog {
        Set<String> cleanKeys = keys == null ? Set.of() : Set.copyOf(keys);
        if (texturesByKey == null || texturesByKey.isEmpty()) {
            texturesByKey = Map.of();
        } else {
            Map<String, Set<String>> copy = new HashMap<>();
            texturesByKey.forEach((key, textures) -> {
                if (cleanKeys.contains(key) && textures != null) {
                    copy.put(key, Set.copyOf(textures));
                }
            });
            texturesByKey = Map.copyOf(copy);
        }
        keys = cleanKeys;
    }

    public static YsmModelCatalog unknown() {
        return new YsmModelCatalog(false, Set.of(), Map.of());
    }

    public static YsmModelCatalog authoritative(Collection<String> values) {
        TreeSet<String> clean = new TreeSet<>();
        if (values != null) {
            for (String value : values) {
                String key = cleanKey(value);
                if (key.isEmpty()) continue;
                clean.add(key);
                if (clean.size() >= MAX_MODELS) break;
            }
        }
        return new YsmModelCatalog(true, clean, Map.of());
    }

    public static YsmModelCatalog authoritative(
            Map<String, ? extends Collection<String>> values) {
        TreeSet<String> keys = new TreeSet<>();
        Map<String, Set<String>> texturesByKey = new HashMap<>();
        int textureCount = 0;
        if (values != null) {
            for (Map.Entry<String, ? extends Collection<String>> entry : values.entrySet()) {
                String key = cleanKey(entry.getKey());
                if (key.isEmpty()) continue;
                keys.add(key);
                if (entry.getValue() == null) {
                    if (keys.size() >= MAX_MODELS) break;
                    continue;
                }
                TreeSet<String> textures = new TreeSet<>();
                for (String value : entry.getValue()) {
                    if (textures.size() >= MAX_TEXTURES_PER_MODEL
                            || textureCount >= MAX_TEXTURES_TOTAL) break;
                    String texture = cleanTexture(value);
                    if (!texture.isEmpty() && textures.add(texture)) textureCount++;
                }
                texturesByKey.put(key, textures);
                if (keys.size() >= MAX_MODELS) break;
            }
        }
        return new YsmModelCatalog(true, keys, texturesByKey);
    }

    public boolean allows(String modelId, String modelPath) {
        return !authoritative || keys.contains(modelId) || keys.contains(modelPath);
    }

    public List<String> allowedTextures(String modelId, String modelPath,
                                        Collection<String> clientTextures) {
        if (clientTextures == null || clientTextures.isEmpty()) return List.of();
        if (!authoritative) return List.copyOf(clientTextures);
        if (!allows(modelId, modelPath)) return List.of();

        Set<String> serverTextures = new TreeSet<>();
        boolean textureCatalogKnown = false;
        for (String key : new String[]{modelId, modelPath}) {
            if (key == null || !texturesByKey.containsKey(key)) continue;
            textureCatalogKnown = true;
            serverTextures.addAll(texturesByKey.get(key));
        }
        if (!textureCatalogKnown) return List.copyOf(clientTextures);

        List<String> allowed = new ArrayList<>();
        for (String texture : clientTextures) {
            if (serverTextures.contains(cleanTexture(texture))) allowed.add(texture);
        }
        return List.copyOf(allowed);
    }

    public void writeTo(CompoundTag root) {
        root.putBoolean(AUTHORITATIVE_TAG, authoritative);
        ListTag models = new ListTag();
        keys.stream().sorted().forEach(key -> {
            CompoundTag model = new CompoundTag();
            model.putString(MODEL_KEY_TAG, key);
            if (texturesByKey.containsKey(key)) {
                ListTag textures = new ListTag();
                texturesByKey.get(key).stream().sorted()
                        .forEach(texture -> textures.add(StringTag.valueOf(texture)));
                model.put(TEXTURES_TAG, textures);
            }
            models.add(model);
        });
        root.put(MODELS_TAG, models);
    }

    public static YsmModelCatalog readFrom(CompoundTag root) {
        if (root == null || !root.getBoolean(AUTHORITATIVE_TAG)) return unknown();
        ListTag models = root.getList(MODELS_TAG, Tag.TAG_COMPOUND);
        TreeSet<String> keys = new TreeSet<>();
        Map<String, Set<String>> texturesByKey = new HashMap<>();
        int textureCount = 0;
        for (int index = 0; index < models.size() && keys.size() < MAX_MODELS; index++) {
            CompoundTag model = models.getCompound(index);
            String key = cleanKey(model.getString(MODEL_KEY_TAG));
            if (key.isEmpty()) continue;
            keys.add(key);
            if (!model.contains(TEXTURES_TAG, Tag.TAG_LIST)) continue;
            ListTag textureTags = model.getList(TEXTURES_TAG, Tag.TAG_STRING);
            TreeSet<String> textures = new TreeSet<>();
            for (int textureIndex = 0; textureIndex < textureTags.size()
                    && textures.size() < MAX_TEXTURES_PER_MODEL
                    && textureCount < MAX_TEXTURES_TOTAL; textureIndex++) {
                String texture = cleanTexture(textureTags.getString(textureIndex));
                if (!texture.isEmpty() && textures.add(texture)) textureCount++;
            }
            texturesByKey.put(key, textures);
        }
        return new YsmModelCatalog(true, keys, texturesByKey);
    }

    private static String cleanKey(String value) {
        if (value == null) return "";
        String clean = value.strip();
        if (clean.isEmpty() || clean.length() > EmployeeAppearance.MAX_MODEL_PATH_LENGTH
                || clean.codePoints().anyMatch(character -> Character.isISOControl(character)
                || character == '\u00a7')) {
            return "";
        }
        return clean;
    }

    private static String cleanTexture(String value) {
        if (value == null) return "";
        String clean = value.strip();
        if (clean.isEmpty() || clean.length() > EmployeeAppearance.MAX_TEXTURE_ID_LENGTH
                || clean.codePoints().anyMatch(character -> Character.isISOControl(character)
                || character == '\u00a7')) {
            return "";
        }
        int namespace = clean.indexOf(':');
        if (namespace >= 0) clean = clean.substring(namespace + 1);
        int path = Math.max(clean.lastIndexOf('/'), clean.lastIndexOf('\\'));
        if (path >= 0) clean = clean.substring(path + 1);
        return clean.toLowerCase().endsWith(".png")
                ? clean.substring(0, clean.length() - 4) : clean;
    }
}
