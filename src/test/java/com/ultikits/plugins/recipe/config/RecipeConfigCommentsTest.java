package com.ultikits.plugins.recipe.config;

import com.ultikits.plugins.recipe.i18n.CatalogueText;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The comments above the keys of {@code config/recipes.yml} come from the module's language files, so a
 * server set to English writes English comments (UltiKits/UltiRecipe#31; framework UltiTools-Reborn#542,
 * rewritten on every load in the current language by the maintainer's decision of 2026-09-29). Every case
 * runs the framework's real load ({@link FrameworkRecipeConfig}) on a temporary folder and answers
 * {@code i18n} from the module's real catalogues.
 * <p>
 * 注释从语言文件取：英文服务器写入英文注释，中文服务器写入中文注释；升级时只改注释，不改值。
 */
@DisplayName("recipes.yml writes its comments in the server's language (#31)")
class RecipeConfigCommentsTest {

    /**
     * The file's two keys: the key, the catalogue key of the comment above it, the comment master wrote
     * (the zh text, verbatim) and its English translation. The expected texts are written out here, not
     * read from the catalogue, so a missing catalogue entry fails on the written file, not in the fixture;
     * {@link #theCataloguesHoldTheseTexts} ties them to the catalogue.
     */
    private static final String[][] KEYS = {
            {"enabled", "recipe.config.recipes.enabled", "\u662f\u5426\u542f\u7528\u81ea\u5b9a\u4e49\u914d\u65b9\u529f\u80fd",
                    "Whether the custom recipe feature is enabled"},
            {"recipes", "recipe.config.recipes.recipes", "\u81ea\u5b9a\u4e49\u914d\u65b9\u5217\u8868",
                    "List of custom recipes"}};

    @TempDir
    Path tempDir;

    /** The comment line directly above {@code key:} in the file text, without its leading {@code # }. */
    private static String commentAbove(String text, String key) {
        String[] lines = text.split("\\R");
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].startsWith(key + ":")) {
                String above = lines[i - 1];
                return above.startsWith("# ") ? above.substring(2) : above;
            }
        }
        throw new AssertionError("no line for key " + key + " in:\n" + text);
    }

    @Test
    @DisplayName("a fresh install under language: en writes the English comment above both keys")
    void freshInstallWritesEnglishComments() throws IOException {
        FrameworkRecipeConfig.load(tempDir, "en");

        String text = FrameworkRecipeConfig.read(tempDir);
        for (String[] key : KEYS) {
            assertThat(commentAbove(text, key[0])).as("comment above " + key[0])
                    .isEqualTo(key[3]);
        }
        assertThat(text).as("no Chinese comment is written").doesNotContainPattern("#.*[\\u4e00-\\u9fff]");
    }

    @Test
    @DisplayName("a fresh install under language: zh writes the Chinese comment above both keys, the text master wrote")
    void freshInstallWritesChineseComments() throws IOException {
        FrameworkRecipeConfig.load(tempDir, "zh");

        String text = FrameworkRecipeConfig.read(tempDir);
        for (String[] key : KEYS) {
            assertThat(commentAbove(text, key[0])).as("comment above " + key[0])
                    .isEqualTo(key[2]);
        }
    }

    @Test
    @DisplayName("an upgrade: a file master wrote gets the English comments, its values stay, and a second start leaves it byte for byte")
    void upgradeSwitchesTheCommentsAndKeepsTheValues() throws IOException {
        FrameworkRecipeConfig.write(tempDir, RecipeConfigFrameworkLoadTest.MASTER_FILE.replace("enabled: true", "enabled: false"));

        RecipeConfig first = FrameworkRecipeConfig.load(tempDir, "en");

        String afterFirst = FrameworkRecipeConfig.read(tempDir);
        for (String[] key : KEYS) {
            assertThat(commentAbove(afterFirst, key[0])).as("comment above " + key[0])
                    .isEqualTo(key[3]);
        }
        assertThat(first.isEnabled()).isFalse();
        assertThat(first.getRecipes()).containsOnlyKeys("custom_sword", "coal_block");
        assertThat(first.getRecipes().get("custom_sword").getOutput().getName()).isEqualTo("&b&lHoly Blade");
        assertThat(afterFirst).as("only the two comment lines changed")
                .isEqualTo(RecipeConfigFrameworkLoadTest.MASTER_FILE.replace("enabled: true", "enabled: false")
                        .replace("# " + KEYS[0][2], "# " + KEYS[0][3])
                        .replace("# " + KEYS[1][2], "# " + KEYS[1][3]));

        RecipeConfig second = FrameworkRecipeConfig.load(tempDir, "en");

        assertThat(FrameworkRecipeConfig.read(tempDir)).as("the second start").isEqualTo(afterFirst);
        assertThat(second.getRecipes()).isEqualTo(first.getRecipes());
    }

    @Test
    @DisplayName("the catalogues hold exactly these texts: zh the comment master wrote, en its translation")
    void theCataloguesHoldTheseTexts() {
        for (String[] key : KEYS) {
            assertThat(CatalogueText.entries("zh").get(key[1])).as("zh " + key[1]).isEqualTo(key[2]);
            assertThat(CatalogueText.entries("en").get(key[1])).as("en " + key[1]).isEqualTo(key[3]);
        }
    }
}
