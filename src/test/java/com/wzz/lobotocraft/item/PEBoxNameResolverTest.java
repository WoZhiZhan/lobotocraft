package com.wzz.lobotocraft.item;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class PEBoxNameResolverTest {
    @Test
    void translationKeyTakesPriorityOverStoredFallback() {
        Component name = PEBoxNameResolver.resolve(
            "entity.lobotocraft.onebad", "一罪与百善");

        TranslatableContents translated = assertInstanceOf(
            TranslatableContents.class, name.getContents());
        assertEquals("entity.lobotocraft.onebad", translated.getKey());
    }

    @Test
    void oldStacksKeepLiteralNameFallback() {
        Component name = PEBoxNameResolver.resolve("", "旧版名称");

        assertEquals("旧版名称", name.getString());
    }
}
