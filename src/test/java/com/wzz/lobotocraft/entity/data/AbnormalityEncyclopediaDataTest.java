package com.wzz.lobotocraft.entity.data;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbnormalityEncyclopediaDataTest {

    private static final Pattern LONG_ENGLISH_PASSAGE = Pattern.compile("[A-Za-z]{4,}(?:\\s+[^\\s]+){8,}");

    @Test
    void everySupportedOriginalAbnormalityHasBundledStories() {
        Map<String, Integer> expectedStoryCounts = Map.ofEntries(
                Map.entry("O-06-20", 10), Map.entry("O-03-03", 9),
                Map.entry("T-04-06", 4), Map.entry("T-09-85", 5),
                Map.entry("F-05-32", 5), Map.entry("T-09-79", 4),
                Map.entry("T-01-54", 5),
                Map.entry("O-02-40", 6), Map.entry("O-02-56", 2),
                Map.entry("0-02-62", 7), Map.entry("F-01-02", 5),
                Map.entry("F-04-83", 5), Map.entry("T-04-50", 11),
                Map.entry("O-04-08", 7), Map.entry("F-01-37", 4),
                Map.entry("D-02-107", 6), Map.entry("O-01-55", 6),
                Map.entry("O-01-67", 5), Map.entry("O-03-93", 3),
                Map.entry("O-02-98", 6), Map.entry("O-02-63", 3),
                Map.entry("T-05-41", 5), Map.entry("O-03-60", 9),
                Map.entry("F-02-58", 4), Map.entry("T-01-68", 4),
                Map.entry("F-01-57", 3), Map.entry("D-01-106", 5),
                Map.entry("O-05-61", 4), Map.entry("O-01-12", 4),
                Map.entry("T-01-75", 5)
        );

        expectedStoryCounts.forEach((code, expectedCount) -> {
            AbnormalityEncyclopediaData.StoryEntry[] stories =
                    AbnormalityEncyclopediaData.getStories(code, "zh_cn");
            assertAll(code,
                    () -> assertEquals(expectedCount, stories.length),
                    () -> assertTrue(stories.length > 0),
                    () -> assertTrue(java.util.Arrays.stream(stories)
                            .allMatch(entry -> entry.openLevel() >= 0
                                    && entry.openLevel() <= 4
                                    && entry.text() != null
                                    && !entry.text().isBlank())));
        });
    }

    @Test
    void chineseStoriesDoNotIncludeNestedFinalObservationDialogue() {
        AbnormalityEncyclopediaData.StoryEntry[] oldLady =
                AbnormalityEncyclopediaData.getStories("O-01-12", "zh_cn");
        AbnormalityEncyclopediaData.StoryEntry[] snowQueen =
                AbnormalityEncyclopediaData.getStories("F-01-37", "zh_cn");

        assertEquals(4, oldLady.length);
        assertEquals(4, snowQueen.length);
        assertTrue(java.util.Arrays.stream(oldLady)
                .noneMatch(entry -> entry.text().contains("There was some cracking")));
        assertTrue(java.util.Arrays.stream(snowQueen)
                .noneMatch(entry -> LONG_ENGLISH_PASSAGE.matcher(entry.text()).find()));
    }

    @Test
    void lumberjackHasOriginalChineseAndEnglishStories() {
        AbnormalityEncyclopediaData.StoryEntry[] chinese =
                AbnormalityEncyclopediaData.getStories("F-05-32", "zh_cn");
        AbnormalityEncyclopediaData.StoryEntry[] english =
                AbnormalityEncyclopediaData.getStories("F-05-32", "en_us");

        assertAll(
                () -> assertEquals(5, chinese.length),
                () -> assertEquals(5, english.length),
                () -> assertTrue(chinese[0].text().contains("完全由金属制成")),
                () -> assertTrue(english[0].text().contains("mechanical Abnormality")),
                () -> assertFalse(chinese[0].text().equals(english[0].text())));
    }

    @Test
    void unsupportedLanguagesFallBackToEnglishStories() {
        AbnormalityEncyclopediaData.StoryEntry[] english =
                AbnormalityEncyclopediaData.getStories("F-05-32", "en_us");
        AbnormalityEncyclopediaData.StoryEntry[] japanese =
                AbnormalityEncyclopediaData.getStories("F-05-32", "ja_jp");

        assertEquals(english[0].text(), japanese[0].text());
    }

    @Test
    void allBundledChineseStoriesAvoidLongEnglishPassages() {
        for (String code : AbnormalityEncyclopediaData.getRegisteredCodes()) {
            if (code.startsWith("S-")) continue;
            for (AbnormalityEncyclopediaData.StoryEntry story
                    : AbnormalityEncyclopediaData.getStories(code, "zh_cn")) {
                assertFalse(LONG_ENGLISH_PASSAGE.matcher(story.text()).find(), code);
            }
        }
    }

    @Test
    void everyOriginalAbnormalityHasMatchingEnglishStories() {
        for (String code : AbnormalityEncyclopediaData.getRegisteredCodes()) {
            if (code.startsWith("S-")) continue;
            AbnormalityEncyclopediaData.StoryEntry[] chinese =
                    AbnormalityEncyclopediaData.getStories(code, "zh_cn");
            AbnormalityEncyclopediaData.StoryEntry[] english =
                    AbnormalityEncyclopediaData.getStories(code, "en_us");

            assertAll(code,
                    () -> assertEquals(chinese.length, english.length),
                    () -> assertTrue(java.util.Arrays.stream(english)
                            .allMatch(entry -> entry.text() != null
                                    && !entry.text().isBlank()
                                    && !entry.text().equals("No original story archive is available."))));
        }
    }

    @Test
    void storyVisibilityFollowsOriginalOpenLevel() {
        AbnormalityEncyclopediaData.StoryEntry[] stories =
                AbnormalityEncyclopediaData.getStories("O-03-03", "zh_cn");
        int[] expectedVisibleCounts = {2, 3, 5, 7, 9};

        for (int observationLevel = 0; observationLevel <= 4; observationLevel++) {
            int level = observationLevel;
            long visible = java.util.Arrays.stream(stories)
                    .filter(entry -> entry.isUnlockedAt(level))
                    .count();
            assertEquals(expectedVisibleCounts[level], visible,
                    "unexpected visible story count at observation level " + level);
        }
        assertFalse(stories[8].isUnlockedAt(3));
        assertTrue(stories[8].isUnlockedAt(4));
    }

    @Test
    void callersCannotMutateTheCachedStoryArray() {
        AbnormalityEncyclopediaData.StoryEntry[] first =
                AbnormalityEncyclopediaData.getStories("O-03-03", "zh_cn");
        AbnormalityEncyclopediaData.StoryEntry[] second =
                AbnormalityEncyclopediaData.getStories("O-03-03", "zh_cn");

        assertNotSame(first, second);
        first[0] = new AbnormalityEncyclopediaData.StoryEntry(99, "corrupted");
        assertEquals(0, second[0].openLevel());
    }
}
