package com.wzz.lobotocraft.localization;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
            "\"([a-z0-9_]+\\.lobotocraft\\.[a-z0-9_.-]+)\"");
    private static final Pattern TOP_LEVEL_KEY = Pattern.compile(
            "(?m)^\\s*\"((?:\\\\.|[^\"\\\\])+)\"\\s*:");
    private static final Pattern HAN_CHARACTER = Pattern.compile("\\p{IsHan}");
    private static final Pattern HARDCODED_HAN_COMPONENT = Pattern.compile(
            "Component\\.literal\\s*\\(\\s*\"[^\"]*\\p{IsHan}[^\"]*\"");
    private static final Pattern HARDCODED_SCREEN_STATUS = Pattern.compile(
            "\"(?:LOCKED|CURRENT|EMPTY|UNKNOWN|LEVEL |LLM READY|LLM OFFLINE)\"");
    private static final Pattern REGISTRATION_ID = Pattern.compile(
            "register\\s*\\(\\s*\"([a-z0-9_]+)\"");

    @Test
    void headAndHeadwearHaveDistinctAuthoritativeNames() throws IOException {
        Map<String, String> chinese = readLanguage("zh_cn.json");
        Map<String, String> english = readLanguage("en_us.json");
        assertEquals("头部", chinese.get("curios.identifier.head"));
        assertEquals("头饰", chinese.get("curios.identifier.headwear"));
        assertEquals("Head", english.get("curios.identifier.head"));
    }

    @Test
    void languageFilesDoNotContainDuplicateKeys() throws IOException {
        assertNoDuplicateKeys("zh_cn.json");
        assertNoDuplicateKeys("en_us.json");
    }

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
                        if (key.endsWith(".")) continue;
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

    @Test
    void egoItemsDoNotUseHardcodedComponents() throws IOException {
        Set<String> hardcoded = new HashSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("item" + java.io.File.separator + "ego"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            if (source.contains("Component.literal(")) {
                                hardcoded.add(path.toString());
                            }
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        }
        assertTrue(hardcoded.isEmpty(), "E.G.O items still contain hardcoded components: " + hardcoded);
    }

    @Test
    void englishLanguageDoesNotContainChineseText() throws IOException {
        Set<String> invalid = new HashSet<>();
        readLanguage("en_us.json").forEach((key, value) -> {
            if (HAN_CHARACTER.matcher(value).find()) invalid.add(key);
        });
        assertTrue(invalid.isEmpty(), "English entries contain Chinese text: " + invalid);
    }

    @Test
    void productionComponentsDoNotHardcodeChineseText() throws IOException {
        Set<String> hardcoded = new HashSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("command" + java.io.File.separator))
                    .filter(path -> !path.toString().contains(
                            "item" + java.io.File.separator + "debug" + java.io.File.separator))
                    .forEach(path -> {
                        try {
                            String source = stripComments(Files.readString(path));
                            if (HARDCODED_HAN_COMPONENT.matcher(source).find()) {
                                hardcoded.add(path.toString());
                            }
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        }
        assertTrue(hardcoded.isEmpty(), "Production components hardcode Chinese text: " + hardcoded);
    }

    @Test
    void formalScreensDoNotHardcodeCommonStatusLabels() throws IOException {
        Set<String> hardcoded = new HashSet<>();
        try (Stream<Path> files = Files.walk(Path.of(
                "src/main/java/com/wzz/lobotocraft/client/screen"))) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = stripComments(Files.readString(path));
                    if (HARDCODED_SCREEN_STATUS.matcher(source).find()) {
                        hardcoded.add(path.toString());
                    }
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
        assertTrue(hardcoded.isEmpty(),
                "Formal screens hardcode common status labels: " + hardcoded);
    }

    @Test
    void registeredContentHasLanguageKeys() throws IOException {
        Set<String> keys = readLanguage("en_us.json").keySet();
        assertRegistryKeys(keys, "src/main/java/com/wzz/lobotocraft/init/ModEntities.java",
                "entity.lobotocraft.", null);
        assertRegistryKeys(keys, "src/main/java/com/wzz/lobotocraft/init/ModBlocks.java",
                "block.lobotocraft.", null);
        assertRegistryKeys(keys, "src/main/java/com/wzz/lobotocraft/init/ModMobEffects.java",
                "effect.lobotocraft.", null);
        assertRegistryKeys(keys, "src/main/java/com/wzz/lobotocraft/init/ModItems.java",
                "item.lobotocraft.", "block.lobotocraft.");
    }

    @Test
    void workLogTranslationsCoverEveryRuntimeList() throws IOException {
        Set<String> keys = readLanguage("en_us.json").keySet();
        Map<String, Integer> expectedCounts = Map.ofEntries(
                Map.entry("abandoned_murderer", 9), Map.entry("approval_bird", 8),
                Map.entry("army_in_black", 3), Map.entry("bigbadwolf", 4),
                Map.entry("blue_star", 7), Map.entry("butterfly_funeral", 15),
                Map.entry("crumbling_armor", 8), Map.entry("fragment_of_the_universe", 4),
                Map.entry("fourth_match_flame", 14), Map.entry("happy_teddy", 8),
                Map.entry("helper", 15), Map.entry("isharmla", 1),
                Map.entry("large_bird", 7), Map.entry("leticia", 3),
                Map.entry("nothing_there", 19), Map.entry("onebad", 17),
                Map.entry("ppodae", 4), Map.entry("punishing_bird", 8),
                Map.entry("queen_bee", 8), Map.entry("red_shoes", 19),
                Map.entry("redhat_mercenary", 9), Map.entry("skadi_corrupted", 6),
                Map.entry("smiling_corpse_mountain", 2), Map.entry("snowqueen", 3),
                Map.entry("the_lady_facing_the_wall", 3), Map.entry("thorn_bus", 4),
                Map.entry("wingbeat", 4));
        expectedCounts.forEach((entity, count) -> assertIndexedKeys(keys,
                "work_log.lobotocraft." + entity + ".", count));
        assertIndexedKeys(keys, "work_log.lobotocraft.children_galaxy.positive.", 4);
        assertIndexedKeys(keys, "work_log.lobotocraft.children_galaxy.depleted.", 5);
    }

    private static Map<String, String> readLanguage(String fileName) throws IOException {
        JsonObject object = JsonParser.parseString(Files.readString(
                LANG_ROOT.resolve(fileName), StandardCharsets.UTF_8)).getAsJsonObject();
        return object.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().getAsString()));
    }

    private static void assertNoDuplicateKeys(String fileName) throws IOException {
        String json = Files.readString(LANG_ROOT.resolve(fileName), StandardCharsets.UTF_8);
        Map<String, Integer> counts = new LinkedHashMap<>();
        Matcher matcher = TOP_LEVEL_KEY.matcher(json);
        while (matcher.find()) counts.merge(matcher.group(1), 1, Integer::sum);
        Map<String, Integer> duplicates = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
        assertTrue(duplicates.isEmpty(), fileName + " duplicate keys: " + duplicates);
    }

    private static List<String> placeholders(String value) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) result.add(matcher.group());
        return List.copyOf(result);
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }

    private static void assertRegistryKeys(Set<String> languageKeys, String sourcePath,
                                           String primaryPrefix, String fallbackPrefix)
            throws IOException {
        Set<String> missing = new HashSet<>();
        Matcher matcher = REGISTRATION_ID.matcher(Files.readString(Path.of(sourcePath)));
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!languageKeys.contains(primaryPrefix + id)
                    && (fallbackPrefix == null || !languageKeys.contains(fallbackPrefix + id))) {
                missing.add(id);
            }
        }
        assertTrue(missing.isEmpty(), sourcePath + " missing language keys: " + missing);
    }

    private static void assertIndexedKeys(Set<String> languageKeys, String prefix, int count) {
        for (int index = 0; index < count; index++) {
            assertTrue(languageKeys.contains(prefix + index), "Missing translation key: " + prefix + index);
        }
        assertTrue(!languageKeys.contains(prefix + count), "Unexpected extra translation key: " + prefix + count);
    }
}
