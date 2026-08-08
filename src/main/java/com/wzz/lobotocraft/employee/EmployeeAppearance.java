package com.wzz.lobotocraft.employee;

import net.minecraft.nbt.CompoundTag;

/** Persisted optional renderer selection for a synthetic employee. */
public record EmployeeAppearance(String provider, String modelId, String modelPath,
                                 String textureId) {
    public static final String MODERN_YSM_PROVIDER = "ysm";
    public static final String LEGACY_YSM_PROVIDER = "yes_steve_model";
    public static final int MAX_PROVIDER_LENGTH = 32;
    public static final int MAX_MODEL_ID_LENGTH = 256;
    public static final int MAX_MODEL_PATH_LENGTH = 256;
    public static final int MAX_TEXTURE_ID_LENGTH = 256;
    public static final EmployeeAppearance DEFAULT = new EmployeeAppearance("", "", "", "");

    public EmployeeAppearance {
        provider = clean(provider);
        modelId = clean(modelId);
        modelPath = clean(modelPath);
        textureId = clean(textureId);
    }

    public boolean usesYsm() {
        return provider.equals(MODERN_YSM_PROVIDER)
                || provider.equals(LEGACY_YSM_PROVIDER);
    }

    public boolean isDefault() {
        return provider.isEmpty();
    }

    public boolean isStructurallyValid() {
        if (isDefault()) {
            return modelId.isEmpty() && modelPath.isEmpty() && textureId.isEmpty();
        }
        return usesYsm()
                && valid(provider, MAX_PROVIDER_LENGTH)
                && valid(modelId, MAX_MODEL_ID_LENGTH)
                && valid(modelPath, MAX_MODEL_PATH_LENGTH)
                && valid(textureId, MAX_TEXTURE_ID_LENGTH)
                && !modelId.isEmpty() && !modelPath.isEmpty() && !textureId.isEmpty();
    }

    public EmployeeAppearance sanitized() {
        return isStructurallyValid() ? this : DEFAULT;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (isDefault()) return tag;
        tag.putString("Provider", provider);
        tag.putString("ModelId", modelId);
        tag.putString("ModelPath", modelPath);
        tag.putString("TextureId", textureId);
        return tag;
    }

    public static EmployeeAppearance load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return DEFAULT;
        return new EmployeeAppearance(tag.getString("Provider"), tag.getString("ModelId"),
                tag.getString("ModelPath"), tag.getString("TextureId")).sanitized();
    }

    private static boolean valid(String value, int maxLength) {
        return value.length() <= maxLength && value.codePoints().noneMatch(character ->
                Character.isISOControl(character) || character == '\u00a7');
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
