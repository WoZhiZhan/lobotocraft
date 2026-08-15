package com.wzz.lobotocraft.client;

import com.wzz.lobotocraft.entity.base.IAbnormality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** Resolves server-provided abnormality names in the active client language. */
public final class AbnormalityNameResolver {
    private AbnormalityNameResolver() {
    }

    public static String resolve(int entityId, String fallback) {
        return resolve(entityId, "", fallback);
    }

    public static String resolve(int entityId, String translationKey, String fallback) {
        if (translationKey != null && !translationKey.isBlank()
                && I18n.exists(translationKey)) {
            return Component.translatable(translationKey).getString();
        }
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(entityId);
        if (entity instanceof IAbnormality) {
            return Component.translatable(entity.getType().getDescriptionId()).getString();
        }
        return fallback == null ? "" : fallback;
    }
}
