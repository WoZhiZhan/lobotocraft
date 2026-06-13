package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.ModMain;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Optional;

public class ResourceUtil {
    public static ResourceLocation createInstance(String path) {
        return ResourceLocation.fromNamespaceAndPath(ModMain.MODID, path);
    }

    public static ResourceLocation createInstanceWithColon(String all) {
        String namespace;
        String path;
        if (all.contains(":")) {
            int colonIndex = all.indexOf(":");
            namespace = all.substring(0, colonIndex);
            path = all.substring(colonIndex + 1);
        } else {
            namespace = ModMain.MODID;
            path = all;
        }
        return createInstance(namespace, path);
    }

    public static ResourceLocation createInstance(String name, String path) {
        return ResourceLocation.fromNamespaceAndPath(name, path);
    }

    public static ResourceLocation createInstanceNoNamespace(String path) {
        return ResourceLocation.parse(path);
    }

    public static ResourceLocation createEmptyTexture() {
        return ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "textures/air.png");
    }

    public static boolean exists(ResourceLocation resourceLocation) {
        try {
            ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
            Optional<Resource> resource = resourceManager.getResource(resourceLocation);
            return resource.isPresent();
        } catch (Exception e) {
            return false;
        }
    }
}
