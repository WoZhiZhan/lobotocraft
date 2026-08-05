package com.wzz.lobotocraft.item.ego;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EgoDamageSourcePolicyTest {
    private static final List<String> FORBIDDEN = List.of(
            "playerAttack(",
            "mobAttack(",
            "DamageTypes.PLAYER_ATTACK",
            "DamageTypes.MOB_ATTACK",
            "damageSources().generic("
    );

    @Test
    void egoItemsNeverCreateVanillaCombatDamageSources() throws IOException {
        Path root = Path.of("src", "main", "java", "com", "wzz", "lobotocraft", "item", "ego");
        assertTrue(Files.isDirectory(root), "E.G.O source root is missing");

        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file).replaceAll("\\s+", "");
                for (String forbidden : FORBIDDEN) {
                    if (source.contains(forbidden)) {
                        violations.add(root.relativize(file) + " contains " + forbidden);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }
}
