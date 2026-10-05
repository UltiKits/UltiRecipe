package com.ultikits.plugins.recipe.config;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * UltiRecipe loads on UltiTools-API 6.3.0 (UltiKits/UltiRecipe#32).
 * <p>
 * The 6.3.0 configuration layer checks every declared field type before it reads or creates a file, and
 * refuses a module whose type it cannot store: for this module that was {@code recipes}, declared
 * {@code Map<String, RecipeDefinition>}, refused with {@code no config converter for
 * ...RecipeConfig$RecipeDefinition} until the module registered a converter for it. Every case here runs
 * the framework's own module-load step and {@code RecipeConfig#init} ({@link FrameworkRecipeConfig}).
 * <p>
 * UltiRecipe 在 6.3.0 框架上能加载：配方表注册了转换器，配方以 {@code RecipeDefinition} 的形式读出，
 * 未改动的配方文件启动后一字节不变。
 */
@DisplayName("RecipeConfig loads through the 6.3.0 configuration layer (#32)")
class RecipeConfigFrameworkLoadTest {

    /**
     * A {@code recipes.yml} as a server running the current {@code master} holds it: the two comments the
     * 6.2 framework wrote above its keys (the module's literal Chinese comments) and two recipes written
     * by hand in the format the module's README documents.
     */
    static final String MASTER_FILE =
            "# 是否启用自定义配方功能\n"
            + "enabled: true\n"
            + "# 自定义配方列表\n"
            + "recipes:\n"
            + "  custom_sword:\n"
            + "    output:\n"
            + "      material: DIAMOND_SWORD\n"
            + "      amount: 1\n"
            + "      name: '&b&lHoly Blade'\n"
            + "      lore:\n"
            + "      - '&7A mighty sword'\n"
            + "    shape:\n"
            + "    - ' D '\n"
            + "    - ' D '\n"
            + "    - ' S '\n"
            + "    ingredients:\n"
            + "      D: DIAMOND\n"
            + "      S: STICK\n"
            + "  coal_block:\n"
            + "    output:\n"
            + "      material: COAL_BLOCK\n"
            + "    shape:\n"
            + "    - xxx\n"
            + "    - xxx\n"
            + "    - xxx\n"
            + "    ingredients:\n"
            + "      x: COAL\n";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("the module's config entities pass the framework's load-time type check")
    void moduleIsNotRefusedAtLoad() {
        UltiToolsPlugin plugin = FrameworkRecipeConfig.plugin(tempDir, "en");

        assertThatCode(() -> FrameworkRecipeConfig.prepare(plugin)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("recipes written for the current master load as RecipeDefinition values, every field as written")
    void recipesLoadAsTypedDefinitions() throws Exception {
        FrameworkRecipeConfig.write(tempDir, MASTER_FILE);

        RecipeConfig config = FrameworkRecipeConfig.load(tempDir, "zh");

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getRecipes()).containsOnlyKeys("custom_sword", "coal_block");
        RecipeConfig.RecipeDefinition sword = config.getRecipes().get("custom_sword");
        assertThat(sword.getOutput().getMaterial()).isEqualTo("DIAMOND_SWORD");
        assertThat(sword.getOutput().getAmount()).isEqualTo(1);
        assertThat(sword.getOutput().getName()).isEqualTo("&b&lHoly Blade");
        assertThat(sword.getOutput().getLore()).containsExactly("&7A mighty sword");
        assertThat(sword.getShape()).containsExactly(" D ", " D ", " S ");
        assertThat(sword.getIngredients()).containsExactly(
                new java.util.AbstractMap.SimpleEntry<>("D", "DIAMOND"),
                new java.util.AbstractMap.SimpleEntry<>("S", "STICK"));
        RecipeConfig.RecipeDefinition coal = config.getRecipes().get("coal_block");
        assertThat(coal.getOutput().getMaterial()).isEqualTo("COAL_BLOCK");
        assertThat(coal.getOutput().getAmount()).as("absent amount: the declared default").isEqualTo(1);
        assertThat(coal.getOutput().getName()).isNull();
        assertThat(coal.getOutput().getLore()).isNull();
        assertThat(coal.getShape()).isEqualTo(Arrays.asList("xxx", "xxx", "xxx"));
        assertThat(coal.getIngredients()).isEqualTo(Collections.singletonMap("x", "COAL"));
    }

    @Test
    @DisplayName("an unchanged recipes file is not rewritten by a start, nor marked for the shutdown save")
    void unchangedFileStaysByteIdentical() throws Exception {
        FrameworkRecipeConfig.write(tempDir, MASTER_FILE);
        Path file = FrameworkRecipeConfig.file(tempDir).toPath();
        FileTime before = FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() - 10_000L);
        Files.setLastModifiedTime(file, before);

        RecipeConfig first = FrameworkRecipeConfig.load(tempDir, "zh");

        assertThat(FrameworkRecipeConfig.read(tempDir)).isEqualTo(MASTER_FILE);
        assertThat(Files.getLastModifiedTime(file)).as("no write at all, not even an identical one").isEqualTo(before);
        assertThat(first.isModifiedSinceSnapshot()).as("the shutdown save writes only a changed entity").isFalse();

        RecipeConfig second = FrameworkRecipeConfig.load(tempDir, "zh");

        assertThat(FrameworkRecipeConfig.read(tempDir)).as("a second start").isEqualTo(MASTER_FILE);
        assertThat(second.getRecipes()).isEqualTo(first.getRecipes());
    }

    @Test
    @DisplayName("an entry that cannot be read as a recipe stays in the map beside the good one, and the file is left alone")
    void unreadableEntryDoesNotCostItsNeighbour() throws Exception {
        String text = MASTER_FILE + "  broken:\n    output:\n      material: DIAMOND\n      amount: 2.5\n";
        FrameworkRecipeConfig.write(tempDir, text);

        RecipeConfig config = FrameworkRecipeConfig.load(tempDir, "zh");

        assertThat(config.getRecipes()).containsOnlyKeys("custom_sword", "coal_block", "broken");
        assertThat(config.getRecipes().get("coal_block").getOutput().getMaterial()).isEqualTo("COAL_BLOCK");
        assertThat(config.isModifiedSinceSnapshot()).isFalse();
        assertThat(FrameworkRecipeConfig.read(tempDir)).isEqualTo(text);
    }
}
