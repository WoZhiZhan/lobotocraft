package com.wzz.lobotocraft.compat.ysm;

import com.mojang.logging.LogUtils;
import com.wzz.lobotocraft.employee.EmployeeAppearance;
import com.wzz.lobotocraft.employee.EmployeeRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Optional bridge for both the current and legacy Yes Steve Model implementations. */
public final class YsmEmployeeCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> REPORTED_FAILURES = ConcurrentHashMap.newKeySet();
    private static final String MODERN_PROVIDER = EmployeeAppearance.MODERN_YSM_PROVIDER;
    private static final String LEGACY_PROVIDER = EmployeeAppearance.LEGACY_YSM_PROVIDER;

    private YsmEmployeeCompat() {
    }

    public static String activeProvider() {
        if (available(MODERN_PROVIDER)) return MODERN_PROVIDER;
        if (available(LEGACY_PROVIDER)) return LEGACY_PROVIDER;
        return "";
    }

    public static boolean isAvailable() {
        return !activeProvider().isEmpty();
    }

    public static Optional<EmployeeAppearance> validateRequested(EmployeeAppearance requested) {
        EmployeeAppearance clean = requested == null
                ? EmployeeAppearance.DEFAULT : requested.sanitized();
        if (clean.isDefault()) return Optional.of(EmployeeAppearance.DEFAULT);
        String provider = activeProvider();
        if (provider.isEmpty() || !provider.equals(clean.provider())) return Optional.empty();
        try {
            return provider.equals(MODERN_PROVIDER)
                    ? resolveModern(clean)
                    : legacyMajorVersion() >= 2 ? validateReleaseSelection(clean)
                    : validateLegacy(clean);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            reportFailure("validate-" + provider, error);
            return Optional.empty();
        }
    }

    /** Writes YSM's stable Forge capability payload before vanilla loads the employee entity. */
    public static boolean preparePlayerData(EmployeeRecord record, CompoundTag playerData) {
        String provider = activeProvider();
        if (provider.isEmpty()) return false;
        EmployeeAppearance appearance = record.appearance();
        CompoundTag forgeCaps = playerData.getCompound("ForgeCaps");
        String capabilityId = provider + ":model_id";
        CompoundTag model = forgeCaps.getCompound(capabilityId);
        if (provider.equals(MODERN_PROVIDER)) {
            model.putString("model_hash", appearance.isDefault() ? "" : appearance.modelId());
            model.putString("select_texture", appearance.isDefault()
                    ? "" : normalizeReleaseTexture(appearance.textureId()));
            model.putBoolean("mandatory", true);
            model.putBoolean("disabled", appearance.isDefault());
        } else if (legacyMajorVersion() >= 2) {
            model.putString("model_id", appearance.isDefault() ? "default"
                    : appearance.modelId().isBlank() ? appearance.modelPath() : appearance.modelId());
            model.putString("select_texture", appearance.isDefault()
                    ? "default" : normalizeReleaseTexture(appearance.textureId()));
            model.putBoolean("mandatory", true);
            model.putBoolean("disabled", appearance.isDefault());
        } else {
            EmployeeAppearance selection = appearance.isDefault()
                    ? new EmployeeAppearance(LEGACY_PROVIDER, LEGACY_PROVIDER + ":steve", "steve",
                    LEGACY_PROVIDER + ":steve/tartaric_acid.png") : appearance;
            model.putString("model_id", selection.modelId());
            model.putString("select_texture", selection.textureId());
        }
        forgeCaps.put(capabilityId, model);
        playerData.put("ForgeCaps", forgeCaps);
        return true;
    }

    private static Optional<EmployeeAppearance> resolveModern(EmployeeAppearance appearance)
            throws ReflectiveOperationException {
        Optional<?> snapshot = modernSnapshot();
        return snapshot.isEmpty() ? Optional.empty() : resolveModern(appearance, snapshot.get());
    }

    private static Optional<EmployeeAppearance> resolveModern(EmployeeAppearance appearance,
                                                               Object snapshot)
            throws ReflectiveOperationException {

        Class<?> hashType = Class.forName("com.elfmcys.ysm.model.domain.ModelHash");
        Object hash = null;
        if (appearance.provider().equals(MODERN_PROVIDER)) {
            try {
                hash = hashType.getMethod("parse", String.class).invoke(null, appearance.modelId());
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                hash = null;
            }
        }

        Object handle = null;
        if (hash != null) {
            handle = optional(snapshot.getClass().getMethod("find", hashType)
                    .invoke(snapshot, hash)).orElse(null);
        }
        if (handle == null && !appearance.modelPath().isEmpty()) {
            handle = optional(snapshot.getClass().getMethod("findPath", String.class)
                    .invoke(snapshot, appearance.modelPath())).orElse(null);
        }
        if (handle == null || authorizationRequired(handle)) return Optional.empty();

        Object descriptor = handle.getClass().getMethod("descriptor").invoke(handle);
        Object resolvedHash = descriptor.getClass().getMethod("modelHash").invoke(descriptor);
        String path = modelPath(handle);
        String texture = normalizeModernTexture(appearance.textureId());
        Object view = handle.getClass().getMethod("view").invoke(handle);
        Object playerView = view.getClass().getMethod("getPlayer").invoke(view);
        Object textures = playerView.getClass().getMethod("getTextureNames").invoke(playerView);
        if (!contains(textures, texture)) return Optional.empty();

        EmployeeAppearance resolved = new EmployeeAppearance(MODERN_PROVIDER,
                resolvedHash.toString(), path, texture).sanitized();
        return resolved.isDefault() ? Optional.empty() : Optional.of(resolved);
    }

    private static Optional<?> modernSnapshot() throws ReflectiveOperationException {
        Class<?> serviceType = Class.forName("com.elfmcys.ysm.model.server.ServerModelService");
        Optional<?> service = optional(serviceType.getMethod("current").invoke(null));
        if (service.isEmpty()) return Optional.empty();
        return optional(service.get().getClass().getMethod("snapshot").invoke(service.get()));
    }

    private static Optional<EmployeeAppearance> validateLegacy(EmployeeAppearance appearance)
            throws ReflectiveOperationException {
        return validateLegacySelection(appearance.modelPath(), appearance.textureId());
    }

    private static Optional<EmployeeAppearance> validateLegacySelection(String modelPath,
                                                                         String textureId)
            throws ReflectiveOperationException {
        ResourceLocation model = ResourceLocation.tryParse(LEGACY_PROVIDER + ":" + modelPath);
        ResourceLocation texture = ResourceLocation.tryParse(textureId);
        if (model == null || texture == null || !texture.getNamespace().equals(LEGACY_PROVIDER)
                || !texture.getPath().startsWith(modelPath + "/")) {
            return Optional.empty();
        }

        Class<?> manager = Class.forName("com.elfmcys.yesstevemodel.model.ServerModelManager");
        Map<?, ?> models = (Map<?, ?>) manager.getField("CACHE_NAME_INFO").get(null);
        Collection<?> authorized = (Collection<?>) manager.getField("AUTH_MODELS").get(null);
        boolean present = models.keySet().stream().map(String::valueOf)
                .anyMatch(key -> key.equals(modelPath));
        if (!present || authorized.stream().map(String::valueOf)
                .anyMatch(modelPath::equals)) return Optional.empty();
        return Optional.of(new EmployeeAppearance(LEGACY_PROVIDER, model.toString(), modelPath,
                texture.toString()));
    }

    private static Optional<EmployeeAppearance> validateReleaseSelection(
            EmployeeAppearance appearance) throws ReflectiveOperationException {
        Optional<ReleaseCatalog> catalog = releaseCatalog();
        if (catalog.isEmpty()) return Optional.empty();
        String modelId = catalog.get().models().containsKey(appearance.modelId())
                ? appearance.modelId() : appearance.modelPath();
        Object model = catalog.get().models().get(modelId);
        if (model == null || catalog.get().authorized().contains(modelId)) return Optional.empty();
        List<String> textures = findStringList(model, 2);
        String texture = normalizeReleaseTexture(appearance.textureId());
        if (!textures.contains(texture)) return Optional.empty();
        return Optional.of(new EmployeeAppearance(LEGACY_PROVIDER, modelId, modelId, texture));
    }

    private static Optional<ReleaseCatalog> releaseCatalog() throws ReflectiveOperationException {
        Optional<Class<?>> manager = YsmClassLocator.find(LEGACY_PROVIDER, "server-catalog",
                "net/minecraftforge/server/ServerLifecycleHooks",
                "it/unimi/dsi/fastutil/floats/FloatReferencePair", "java/nio/file/Path");
        if (manager.isEmpty()) return Optional.empty();
        Map<String, Object> models = Map.of();
        Set<String> authorized = Set.of();
        for (Method method : manager.get().getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0
                    || !(Map.class.isAssignableFrom(method.getReturnType())
                    || Set.class.isAssignableFrom(method.getReturnType()))) continue;
            Object value = method.invoke(null);
            if (value instanceof Map<?, ?> map && stringKeyed(map) && releaseModelMap(map)) {
                @SuppressWarnings("unchecked") Map<String, Object> cast = (Map<String, Object>) map;
                models = cast;
            } else if (value instanceof Set<?> set && set.stream().allMatch(String.class::isInstance)) {
                @SuppressWarnings("unchecked") Set<String> cast = (Set<String>) set;
                authorized = cast;
            }
        }
        return models.isEmpty() ? Optional.empty()
                : Optional.of(new ReleaseCatalog(models, authorized));
    }

    private static boolean stringKeyed(Map<?, ?> map) {
        return !map.isEmpty() && map.keySet().stream().allMatch(String.class::isInstance);
    }

    private static boolean releaseModelMap(Map<?, ?> map) {
        Object sample = map.values().stream().filter(value -> value != null).findFirst().orElse(null);
        return sample != null && !findStringList(sample, 2).isEmpty();
    }

    private static List<String> findStringList(Object root, int depth) {
        if (root == null || depth < 0) return List.of();
        if (root instanceof List<?> list && !list.isEmpty()
                && list.stream().allMatch(String.class::isInstance)) {
            @SuppressWarnings("unchecked") List<String> cast = (List<String>) list;
            return List.copyOf(cast);
        }
        if (depth == 0) return List.of();
        for (Method method : root.getClass().getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0
                    || method.getReturnType() == void.class || method.getDeclaringClass() == Object.class) continue;
            try {
                List<String> found = findStringList(method.invoke(root), depth - 1);
                if (!found.isEmpty()) return found;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Structural discovery skips accessors that require unavailable optional state.
            }
        }
        return List.of();
    }

    private static String normalizeReleaseTexture(String texture) {
        String value = texture;
        int namespace = value.indexOf(':');
        if (namespace >= 0) value = value.substring(namespace + 1);
        int path = value.lastIndexOf('/');
        if (path >= 0) value = value.substring(path + 1);
        if (value.toLowerCase().endsWith(".png")) value = value.substring(0, value.length() - 4);
        return value;
    }

    private static int legacyMajorVersion() {
        var info = ModList.get().getModFileById(LEGACY_PROVIDER);
        if (info == null) return 0;
        String version = info.versionString();
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) end++;
        if (end == 0) return 0;
        try {
            return Integer.parseInt(version.substring(0, end));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean authorizationRequired(Object handle) throws ReflectiveOperationException {
        Object location = handle.getClass().getMethod("location").invoke(handle);
        Object rootKind = location.getClass().getMethod("rootKind").invoke(location);
        Object access = rootKind.getClass().getMethod("accessPolicy").invoke(rootKind);
        return access.toString().contains("AUTHORIZED");
    }

    private static String modelPath(Object handle) throws ReflectiveOperationException {
        Object location = handle.getClass().getMethod("location").invoke(handle);
        Object path = location.getClass().getMethod("path").invoke(location);
        return String.valueOf(path.getClass().getMethod("value").invoke(path));
    }

    private static boolean contains(Object values, String expected) throws ReflectiveOperationException {
        if (values instanceof Collection<?> collection) return collection.contains(expected);
        Method contains = values.getClass().getMethod("contains", Object.class);
        return Boolean.TRUE.equals(contains.invoke(values, expected));
    }

    private static String normalizeModernTexture(String texture) {
        String value = texture.substring(texture.lastIndexOf('/') + 1);
        return value.toLowerCase().endsWith(".png")
                ? value.substring(0, value.length() - 4) : value;
    }

    private static boolean available(String provider) {
        if (!ModList.get().isLoaded(provider)) return false;
        if (provider.equals(LEGACY_PROVIDER) && legacyMajorVersion() < 2) return true;
        try {
            Class<?> main = Class.forName(provider.equals(MODERN_PROVIDER)
                    ? "com.elfmcys.ysm.YesSteveModel"
                    : "com.elfmcys.yesstevemodel.YesSteveModel");
            return Boolean.TRUE.equals(main.getMethod("isAvailable").invoke(null));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            reportFailure("availability-modern", error);
            return false;
        }
    }

    private static Optional<?> optional(Object value) {
        return value instanceof Optional<?> optional ? optional : Optional.empty();
    }

    private static void reportFailure(String operation, Throwable error) {
        if (REPORTED_FAILURES.add(operation)) {
            LOGGER.warn("YSM employee compatibility failed during {}: {}", operation,
                    error.getMessage());
        }
    }

    private record ReleaseCatalog(Map<String, Object> models, Set<String> authorized) {
    }
}
