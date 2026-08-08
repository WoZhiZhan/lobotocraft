package com.wzz.lobotocraft.compat.ysm;

import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Finds obfuscated YSM implementation classes by stable JVM dependencies in their class files. */
public final class YsmClassLocator {
    private static final Map<String, Optional<String>> CACHE = new ConcurrentHashMap<>();

    private YsmClassLocator() {
    }

    public static Optional<Class<?>> find(String provider, String purpose, String... markers) {
        String key = provider + ":" + purpose;
        Optional<String> name = CACHE.computeIfAbsent(key, ignored -> locate(provider, markers));
        if (name.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Class.forName(name.get(), false,
                    Thread.currentThread().getContextClassLoader()));
        } catch (ClassNotFoundException | LinkageError error) {
            return Optional.empty();
        }
    }

    private static Optional<String> locate(String provider, String... markers) {
        var info = ModList.get().getModFileById(provider);
        if (info == null) return Optional.empty();
        Path file = info.getFile().getFilePath();
        try {
            return Files.isDirectory(file) ? locateDirectory(file, markers) : locateJar(file, markers);
        } catch (IOException | RuntimeException error) {
            return Optional.empty();
        }
    }

    private static Optional<String> locateJar(Path file, String... markers) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!candidate(entry.getName())) continue;
                byte[] bytes;
                try (var stream = zip.getInputStream(entry)) {
                    bytes = stream.readAllBytes();
                }
                if (containsAll(bytes, markers)) return Optional.of(className(entry.getName()));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> locateDirectory(Path root, String... markers) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (!candidate(relative)) continue;
                if (containsAll(Files.readAllBytes(path), markers)) {
                    return Optional.of(className(relative));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean candidate(String path) {
        if (!path.startsWith("com/elfmcys/yesstevemodel/") || !path.endsWith(".class")
                || path.contains("$")) return false;
        String remainder = path.substring("com/elfmcys/yesstevemodel/".length());
        return !remainder.contains("/");
    }

    private static boolean containsAll(byte[] bytes, String... markers) {
        String constants = new String(bytes, StandardCharsets.ISO_8859_1);
        for (String marker : markers) {
            if (!constants.contains(marker)) return false;
        }
        return true;
    }

    private static String className(String path) {
        return path.substring(0, path.length() - ".class".length()).replace('/', '.');
    }
}
