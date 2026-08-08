package com.wzz.lobotocraft.client.compat;

import com.mojang.logging.LogUtils;
import com.wzz.lobotocraft.compat.ysm.YsmClassLocator;
import com.wzz.lobotocraft.employee.EmployeeAppearance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client-only reflective catalog reader for optional YSM model selection. */
public final class YsmClientCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> REPORTED_FAILURES = ConcurrentHashMap.newKeySet();

    private YsmClientCompat() {
    }

    public static boolean isAvailable(String provider) {
        if (provider == null || provider.isBlank() || !ModList.get().isLoaded(provider)) return false;
        if (!provider.equals(EmployeeAppearance.MODERN_YSM_PROVIDER)) {
            if (!provider.equals(EmployeeAppearance.LEGACY_YSM_PROVIDER)) return false;
            try {
                Class<?> main = Class.forName("com.elfmcys.yesstevemodel.YesSteveModel");
                try {
                    return Boolean.TRUE.equals(main.getMethod("isAvailable").invoke(null));
                } catch (NoSuchMethodException ignored) {
                    return true;
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                reportFailure("availability-legacy", error);
                return false;
            }
        }
        try {
            Class<?> main = Class.forName("com.elfmcys.ysm.YesSteveModel");
            return Boolean.TRUE.equals(main.getMethod("isAvailable").invoke(null));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            reportFailure("availability", error);
            return false;
        }
    }

    public static List<ModelOption> models(String provider) {
        if (!isAvailable(provider)) return List.of();
        try {
            List<ModelOption> models = provider.equals(EmployeeAppearance.MODERN_YSM_PROVIDER)
                    ? modernModels() : classExists(
                    "com.elfmcys.yesstevemodel.client.ClientModelManager")
                    ? legacyModels() : releaseModels();
            models.sort(Comparator.comparing(ModelOption::path));
            return List.copyOf(models);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            reportFailure("catalog-" + provider, error);
            return List.of();
        }
    }

    private static List<ModelOption> modernModels() throws ReflectiveOperationException {
        Class<?> serviceType = Class.forName("com.elfmcys.ysm.client.model.ClientModelService");
        Optional<?> service = optional(serviceType.getMethod("current").invoke(null));
        if (service.isEmpty()) return new ArrayList<>();
        Object catalog = service.get().getClass().getMethod("catalog").invoke(service.get());
        Map<?, ?> entries = (Map<?, ?>) catalog.getClass().getMethod("models").invoke(catalog);
        Class<?> metadataType = Class.forName(
                "com.elfmcys.ysm.client.model.catalog.CatalogModelMetadata");

        List<ModelOption> result = new ArrayList<>();
        for (Object entry : entries.values()) {
            if (Boolean.TRUE.equals(entry.getClass().getMethod("authorizationRequired").invoke(entry))) {
                continue;
            }
            Object hash = entry.getClass().getMethod("modelHash").invoke(entry);
            String path = String.valueOf(entry.getClass().getMethod("displayPath").invoke(entry));
            Object descriptor = entry.getClass().getMethod("displayDescriptor").invoke(entry);
            Object view = descriptor.getClass().getMethod("view").invoke(descriptor);
            Object playerView = view.getClass().getMethod("getPlayer").invoke(view);
            List<String> textures = strings(playerView.getClass().getMethod("getTextureNames")
                    .invoke(playerView));
            if (textures.isEmpty()) continue;

            Object metadata = metadataType.getMethod("from", entry.getClass()).invoke(null, entry);
            String defaultTexture = String.valueOf(metadata.getClass()
                    .getMethod("defaultTexture").invoke(metadata));
            prioritize(textures, defaultTexture);
            result.add(new ModelOption(EmployeeAppearance.MODERN_YSM_PROVIDER,
                    hash.toString(), path, displayName(path), textures));
        }
        return result;
    }

    private static List<ModelOption> legacyModels() throws ReflectiveOperationException {
        Class<?> manager = Class.forName("com.elfmcys.yesstevemodel.client.ClientModelManager");
        Field modelsField = manager.getField("MODELS");
        Map<?, ?> entries = (Map<?, ?>) modelsField.get(null);
        Collection<?> authorized = (Collection<?>) manager.getField("AUTH_MODELS").get(null);

        List<ModelOption> result = new ArrayList<>();
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            if (!(entry.getKey() instanceof ResourceLocation model)) continue;
            if (authorized.stream().map(String::valueOf).anyMatch(model.getPath()::equals)) continue;
            List<String> textures = strings(entry.getValue());
            if (textures.isEmpty()) continue;
            result.add(new ModelOption(EmployeeAppearance.LEGACY_YSM_PROVIDER,
                    model.toString(), model.getPath(), displayName(model.getPath()), textures));
        }
        return result;
    }

    private static List<ModelOption> releaseModels() throws ReflectiveOperationException {
        Optional<Class<?>> manager = YsmClassLocator.find(
                EmployeeAppearance.LEGACY_YSM_PROVIDER, "client-catalog",
                "com/mojang/blaze3d/systems/RenderSystem", "java/util/WeakHashMap",
                "java/util/concurrent/ConcurrentLinkedQueue", "java/nio/ByteBuffer");
        if (manager.isEmpty()) return new ArrayList<>();

        Map<?, ?> entries = Map.of();
        for (Method method : manager.get().getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0
                    || !Map.class.isAssignableFrom(method.getReturnType())) continue;
            Object value = method.invoke(null);
            if (value instanceof Map<?, ?> map && releaseModelMap(map)) {
                entries = map;
                break;
            }
        }

        List<ModelOption> result = new ArrayList<>();
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            if (!(entry.getKey() instanceof String modelId) || entry.getValue() == null
                    || releaseAuthorizationRequired(entry.getValue())) continue;
            List<String> textures = releaseTextures(entry.getValue());
            if (textures.isEmpty()) continue;
            result.add(new ModelOption(EmployeeAppearance.LEGACY_YSM_PROVIDER,
                    modelId, modelId, displayName(modelId), textures));
        }
        return result;
    }

    private static boolean releaseModelMap(Map<?, ?> map) {
        if (map.isEmpty() || !map.keySet().stream().allMatch(String.class::isInstance)) return false;
        Object sample = map.values().stream().filter(value -> value != null).findFirst().orElse(null);
        if (sample == null) return false;
        int mapAccessors = 0;
        int listAccessors = 0;
        for (Method method : sample.getClass().getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) continue;
            if (Map.class.isAssignableFrom(method.getReturnType())) mapAccessors++;
            if (List.class.isAssignableFrom(method.getReturnType())) listAccessors++;
        }
        return mapAccessors >= 2 && listAccessors >= 1;
    }

    private static List<String> releaseTextures(Object model) {
        for (Method outer : model.getClass().getMethods()) {
            if (Modifier.isStatic(outer.getModifiers()) || outer.getParameterCount() != 0
                    || outer.getReturnType().isPrimitive() || outer.getDeclaringClass() == Object.class) continue;
            try {
                Object nested = outer.invoke(model);
                if (nested == null) continue;
                for (Method inner : nested.getClass().getMethods()) {
                    if (Modifier.isStatic(inner.getModifiers()) || inner.getParameterCount() != 0
                            || !Map.class.isAssignableFrom(inner.getReturnType())) continue;
                    Object value = inner.invoke(nested);
                    if (value instanceof Map<?, ?> map && textureMap(map)) {
                        List<String> textures = map.keySet().stream().map(String::valueOf).sorted().toList();
                        return new ArrayList<>(textures);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Structural discovery skips optional model accessors that are not ready yet.
            }
        }
        return List.of();
    }

    private static boolean textureMap(Map<?, ?> map) {
        return !map.isEmpty() && map.keySet().stream().allMatch(String.class::isInstance)
                && map.values().stream().filter(value -> value != null)
                .allMatch(AbstractTexture.class::isInstance);
    }

    private static boolean releaseAuthorizationRequired(Object model) {
        for (Method outer : model.getClass().getMethods()) {
            if (Modifier.isStatic(outer.getModifiers()) || outer.getParameterCount() != 0
                    || outer.getReturnType().isPrimitive() || outer.getDeclaringClass() == Object.class) continue;
            try {
                Object nested = outer.invoke(model);
                if (nested == null || !authorizationStateShape(nested.getClass())) continue;
                for (Method method : nested.getClass().getMethods()) {
                    if (!Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0
                            && method.getReturnType() == boolean.class) {
                        return Boolean.TRUE.equals(method.invoke(nested));
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Continue looking for the stable authorization-state shape.
            }
        }
        return true;
    }

    private static boolean authorizationStateShape(Class<?> type) {
        int booleanGetters = 0;
        int booleanSetters = 0;
        int mapGetters = 0;
        int stringGetters = 0;
        int textureGetters = 0;
        for (Method method : type.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getDeclaringClass() == Object.class) continue;
            if (method.getParameterCount() == 0 && method.getReturnType() == boolean.class) booleanGetters++;
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == boolean.class
                    && method.getReturnType() == void.class) booleanSetters++;
            if (method.getParameterCount() == 0 && Map.class.isAssignableFrom(method.getReturnType())) mapGetters++;
            if (method.getParameterCount() == 0 && method.getReturnType() == String.class) stringGetters++;
            if (method.getParameterCount() == 0
                    && AbstractTexture.class.isAssignableFrom(method.getReturnType())) textureGetters++;
        }
        return booleanGetters == 1 && booleanSetters == 1 && mapGetters == 1
                && stringGetters == 1 && textureGetters >= 2;
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError error) {
            return false;
        }
    }

    private static List<String> strings(Object values) {
        List<String> result = new ArrayList<>();
        if (values instanceof Collection<?> collection) {
            collection.forEach(value -> result.add(String.valueOf(value)));
        } else if (values instanceof Iterable<?> iterable) {
            iterable.forEach(value -> result.add(String.valueOf(value)));
        } else if (values != null && values.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(values); index++) {
                result.add(String.valueOf(Array.get(values, index)));
            }
        }
        result.removeIf(String::isBlank);
        result.sort(String::compareTo);
        return result;
    }

    private static void prioritize(List<String> values, String preferred) {
        int index = values.indexOf(preferred);
        if (index > 0) {
            values.remove(index);
            values.add(0, preferred);
        }
    }

    private static String displayName(String path) {
        int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return separator >= 0 ? path.substring(separator + 1) : path;
    }

    private static Optional<?> optional(Object value) {
        return value instanceof Optional<?> optional ? optional : Optional.empty();
    }

    private static void reportFailure(String operation, Throwable error) {
        if (REPORTED_FAILURES.add(operation)) {
            LOGGER.warn("Could not read the optional YSM client catalog during {}: {}", operation,
                    error.getMessage());
        }
    }

    public record ModelOption(String provider, String modelId, String path, String displayName,
                              List<String> textures) {
        public ModelOption {
            textures = List.copyOf(textures);
        }

        public EmployeeAppearance appearance(int textureIndex) {
            if (textures.isEmpty()) return EmployeeAppearance.DEFAULT;
            int index = Math.max(0, Math.min(textures.size() - 1, textureIndex));
            return new EmployeeAppearance(provider, modelId, path, textures.get(index));
        }

        public int textureIndex(String textureId) {
            int index = textures.indexOf(textureId);
            return index < 0 ? 0 : index;
        }
    }
}
