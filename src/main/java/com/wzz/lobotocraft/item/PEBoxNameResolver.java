package com.wzz.lobotocraft.item;

import net.minecraft.network.chat.Component;

final class PEBoxNameResolver {
    private PEBoxNameResolver() {
    }

    static Component resolve(String translationKey, String fallback) {
        if (translationKey != null && !translationKey.isBlank()) {
            return Component.translatable(translationKey);
        }
        return Component.literal(fallback == null ? "" : fallback);
    }
}
