package com.ultikits.plugins.recipe.docs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The documents state what the maintainer decided about the {@code /recipe} name (2026-09-27): the
 * command deliberately takes over vanilla {@code /recipe}, which stays reachable as
 * {@code minecraft:recipe} (UltiKits/UltiRecipe#27).
 */
@DisplayName("/recipe's takeover of the vanilla command is documented (UltiKits/UltiRecipe#27)")
class RecipeCommandDocsTest {

    private static String read(String name) throws Exception {
        File file = new File(System.getProperty("basedir", "."), name);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("FEATURES.md's command row and the README state the takeover and the minecraft:recipe form")
    void takeoverIsDocumented() throws Exception {
        String features = read("FEATURES.md");
        String row = "| ultirecipe.recipe.command-gate |";
        int start = features.indexOf(row);
        assertThat(start).as("control: the row %s exists", row).isNotNegative();
        String line = features.substring(start, features.indexOf('\n', start));
        assertThat(line).contains("deliberately takes over").contains("minecraft:recipe");

        String readme = read("README.md");
        assertThat(readme).contains("/recipe").contains("minecraft:recipe");
    }
}
