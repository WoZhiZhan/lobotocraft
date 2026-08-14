package com.wzz.lobotocraft.localization;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationResourcesTest {
    private static final Path LANG_ROOT = Path.of(
            "src/main/resources/assets/lobotocraft/lang");
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[a-zA-Z]");
    private static final Pattern STATIC_TRANSLATION_KEY = Pattern.compile(
            "Component\\.translatable\\(\\s*\"([^\"]+)\"\\s*[,)]");

    @Test
    void chineseAndEnglishContainTheSameKeysAndFormatArguments() throws IOException {
        Map<String, String> chinese = readLanguage("zh_cn.json");
        Map<String, String> english = readLanguage("en_us.json");

        assertEquals(chinese.keySet(), english.keySet());
        for (String key : chinese.keySet()) {
            assertEquals(placeholders(chinese.get(key)), placeholders(english.get(key)), key);
        }
    }

    @Test
    void staticTranslationKeysUsedByProductionCodeExist() throws IOException {
        Set<String> languageKeys = readLanguage("zh_cn.json").keySet();
        Set<String> missing = new HashSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    Matcher matcher = STATIC_TRANSLATION_KEY.matcher(Files.readString(path));
                    while (matcher.find()) {
                        String key = matcher.group(1);
                        if (key.startsWith("lobotocraft.") || key.contains(".lobotocraft.")) {
                            if (!languageKeys.contains(key)) missing.add(key);
                        }
                    }
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
        assertTrue(missing.isEmpty(), "Missing translation keys: " + missing);
    }

    private static Map<String, String> readLanguage(String fileName) throws IOException {
        JsonObject object = JsonParser.parseString(Files.readString(
                LANG_ROOT.resolve(fileName), StandardCharsets.UTF_8)).getAsJsonObject();
        return object.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().getAsString()));
    }

    private static List<String> placeholders(String value) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) result.add(matcher.group());
        return List.copyOf(result);
    }
}
