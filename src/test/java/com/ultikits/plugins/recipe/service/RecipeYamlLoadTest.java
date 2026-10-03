package com.ultikits.plugins.recipe.service;

import com.ultikits.plugins.recipe.UltiRecipeTestHelper;
import com.ultikits.plugins.recipe.config.FrameworkRecipeConfig;
import com.ultikits.plugins.recipe.config.RecipeConfig;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.annotations.config.Range;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Loading a real, non-empty {@code recipes.yml} (UltiKits/UltiRecipe#16, UltiKits/UltiRecipe#32).
 *
 * <p>Every case here feeds {@code RecipeService} the value the FRAMEWORK actually produces for the
 * {@code recipes} key: it writes the YAML to a temporary {@code config/recipes.yml} and loads it with
 * {@link FrameworkRecipeConfig}, which runs the framework's own module-load preparation (converter
 * discovery and the declared-type check) and then {@code RecipeConfig#init}. On UltiTools-API 6.3.0 the
 * declared {@code Map<String, RecipeDefinition>} is bound through the module's
 * {@code RecipeDefinitionConverter}; before that converter existed the framework refused the module at
 * load, so every case here failed in its fixture (UltiKits/UltiRecipe#32). {@code RecipeServiceTest}
 * builds its fixtures by calling {@code new RecipeDefinition()} and the setters, which is a shape no
 * server produces on its own - that is why this class exists beside it.
 *
 * <p>Up to UltiTools-API 6.2 this class reproduced the 6.2 binder instead ({@code YamlConfiguration} and
 * {@code DefaultConfigParser#parse}, which left every recipe a {@code LinkedHashMap}). The operator
 * messages it asserts were captured against that binder and are unchanged: the converter carries a
 * value it cannot read into the map as written, and {@code RecipeService} skips it with the same line.
 *
 * <p>The class deliberately touches nothing but {@code RecipeService}'s own public surface and the
 * framework, so it compiles unchanged against the pre-fix sources and the revert proof's RED is a real
 * test failure rather than a compilation error.
 *
 * <p>No Bukkit static is stubbed. {@code Material.matchMaterial} and {@code Bukkit.addRecipe} run
 * for real against the live test server {@link UltiRecipeTestHelper#setUp()} starts, and the
 * registered recipe is read back out of that server's own registry. An assertion that only
 * counted registrations could pass while the output item was built from nothing; reading the
 * result item back is what makes that impossible.
 */
@DisplayName("RecipeService — loading a real recipes.yml (UltiKits/UltiRecipe#16)")
class RecipeYamlLoadTest {

    /**
     * A well-formed entry, used by {@code NestedOutputConstraints} as the surviving neighbour
     * beside each violating one. It lives on the enclosing class for the same reason
     * {@code unbindableEntries()} does: a {@code @Nested} class is an inner class.
     */
    private static final String GOOD_ENTRY =
            "  good:\n"
            + "    output:\n"
            + "      material: DIAMOND_SWORD\n"
            + "      amount: 1\n"
            + "    shape:\n"
            + "      - \" D \"\n"
            + "      - \" D \"\n"
            + "      - \" S \"\n"
            + "    ingredients:\n"
            + "      D: DIAMOND\n"
            + "      S: STICK\n";

    /** The legal mixed-case entry from the issue body. */
    private static final String LEGAL_SWORD =
            "recipes:\n"
            + "  Custom_Sword:\n"
            + "    output:\n"
            + "      material: DIAMOND_SWORD\n"
            + "      amount: 1\n"
            + "      name: \"&bHoly Blade\"\n"
            + "      lore:\n"
            + "        - \"&7A mighty sword\"\n"
            + "    shape:\n"
            + "      - \" D \"\n"
            + "      - \" D \"\n"
            + "      - \" S \"\n"
            + "    ingredients:\n"
            + "      D: DIAMOND\n"
            + "      S: STICK\n";

    private RecipeService service;
    private RecipeConfig config;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        UltiRecipeTestHelper.setUp();
        config = UltiRecipeTestHelper.createDefaultConfig();
        service = new RecipeService();
        UltiRecipeTestHelper.setField(service, "plugin", UltiRecipeTestHelper.getMockPlugin());
        UltiRecipeTestHelper.setField(service, "config", config);
        UltiRecipeTestHelper.setField(service, "pluginInstance", UltiRecipeTestHelper.getMockJavaPlugin());
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiRecipeTestHelper.tearDown();
    }

    /**
     * Java 8 forbids a static member on an inner class, and a {@code @Nested} class is one, so
     * this provider lives on the enclosing class and is cited by its fully qualified name.
     */
    static Stream<Arguments> unbindableEntries() {
        return Stream.of(
                Arguments.of("a scalar where a mapping belongs",
                        "recipes:\n  e: hello\n",
                        "Skipped recipe 'e': expected a mapping, found the text 'hello'"),
                Arguments.of("a number where a mapping belongs",
                        "recipes:\n  e: 7\n",
                        "Skipped recipe 'e': expected a mapping, found a number"),
                Arguments.of("a boolean where a mapping belongs",
                        "recipes:\n  e: true\n",
                        "Skipped recipe 'e': expected a mapping, found a true/false value"),
                Arguments.of("a list where a mapping belongs",
                        "recipes:\n  e:\n    - one\n    - two\n",
                        "Skipped recipe 'e': expected a mapping, found a list"),
                Arguments.of("output that is not a mapping",
                        "recipes:\n  e:\n    output: DIAMOND\n",
                        "Skipped recipe 'e': output: expected a mapping, found the text 'DIAMOND'"),
                Arguments.of("output.amount that is not a number",
                        "recipes:\n  e:\n    output:\n      material: DIAMOND\n      amount: lots\n",
                        "Skipped recipe 'e': output.amount: expected a number, found the text 'lots'"),
                Arguments.of("output.lore that is not a list",
                        "recipes:\n  e:\n    output:\n      material: DIAMOND\n      lore: a line\n",
                        "Skipped recipe 'e': output.lore: expected a list, found the text 'a line'"),
                Arguments.of("shape that is not a list",
                        "recipes:\n  e:\n    output:\n      material: DIAMOND\n    shape: DDD\n",
                        "Skipped recipe 'e': shape: expected a list, found the text 'DDD'"),
                Arguments.of("a shape row that is empty",
                        "recipes:\n  e:\n    output:\n      material: DIAMOND\n    shape:\n      - \"DDD\"\n      -\n",
                        "Skipped recipe 'e': shape: contains an empty entry"),
                Arguments.of("ingredients that is not a mapping",
                        "recipes:\n  e:\n    output:\n      material: DIAMOND\n    ingredients: D\n",
                        "Skipped recipe 'e': ingredients: expected a mapping, found the text 'D'"),
                // Compound values where a scalar belongs. Every one of these reaches a
                // String.valueOf in the binder, which renders a Map or List as Java text -
                // `{text=Blade}` - and for `name` that text became the item's display name with
                // no warning at all. Measured: mapping and list both registered.
                Arguments.of("output.name as a mapping",
                        "recipes:\n  e:\n    output:\n      material: DIAMOND\n      name:\n        text: Blade\n",
                        "Skipped recipe 'e': output.name: expected text, found a mapping"),
                Arguments.of("output.name as a list",
                        "recipes:\n  e:\n    output:\n      material: DIAMOND\n      name:\n        - Blade\n",
                        "Skipped recipe 'e': output.name: expected text, found a list"),
                Arguments.of("output.material as a mapping",
                        "recipes:\n  e:\n    output:\n      material:\n        text: DIAMOND\n",
                        "Skipped recipe 'e': output.material: expected text, found a mapping"),
                Arguments.of("an output.lore entry that is a mapping",
                        "recipes:\n  e:\n    output:\n      material: DIAMOND\n      lore:\n        - text: line\n",
                        "Skipped recipe 'e': output.lore: expected text, found a mapping"),
                Arguments.of("a shape row that is a mapping",
                        "recipes:\n  e:\n    output:\n      material: DIAMOND\n    shape:\n      - DDD\n      - text: DDD\n",
                        "Skipped recipe 'e': shape: expected text, found a mapping"),
                Arguments.of("an ingredient whose material is a mapping",
                        "recipes:\n  e:\n    output:\n      material: DIAMOND\n    ingredients:\n      D:\n        text: DIAMOND\n",
                        "Skipped recipe 'e': ingredients.D: expected text, found a mapping"));
    }

    // --- fixtures -------------------------------------------------------------------------

    /**
     * Writes a {@code recipes.yml} body, loads it through the framework as a module start does, and hands
     * the bound map to the config bean the service reads.
     */
    private void givenRecipesYml(String yml) throws Exception {
        FrameworkRecipeConfig.write(tempDir, yml);
        RecipeConfig loaded = FrameworkRecipeConfig.load(tempDir, "en");
        when(config.getRecipes()).thenReturn(loaded.getRecipes());
    }

    /** Hands the config bean a map built in code rather than loaded from a file. */
    private void givenRecipes(Map<String, RecipeConfig.RecipeDefinition> recipes) {
        when(config.getRecipes()).thenReturn(recipes);
    }

    private List<String> warnings() {
        PluginLogger logger = UltiRecipeTestHelper.getMockLogger();
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger, org.mockito.Mockito.atLeast(0)).warn(captor.capture());
        return captor.getAllValues();
    }

    private ShapedRecipe registered(String key) {
        Recipe recipe = Bukkit.getRecipe(
                new NamespacedKey(UltiRecipeTestHelper.getMockJavaPlugin(), "ultirecipe_" + key));
        assertThat(recipe).as("recipe '%s' in the server's own registry", key).isNotNull();
        return (ShapedRecipe) recipe;
    }

    // --- the four shapes from the issue body ---------------------------------------------

    @Nested
    @DisplayName("the four recipe shapes UltiRecipe#16 was reproduced with")
    class TheFourShapes {

        @Test
        @DisplayName("1 — a legal mixed-case entry registers, with its output item fully bound")
        void legalEntryRegisters() throws Exception {
            givenRecipesYml(LEGAL_SWORD);

            assertThat(service.initRecipes()).isEqualTo(1);

            assertThat(service.getRecipeList()).containsExactly("custom_sword");
            verify(UltiRecipeTestHelper.getMockLogger(), never()).warn(anyString());

            ShapedRecipe recipe = registered("custom_sword");
            assertThat(recipe.getShape()).containsExactly(" D ", " D ", " S ");
            assertThat(recipe.getIngredientMap().get('D').getType()).isEqualTo(Material.DIAMOND);
            assertThat(recipe.getIngredientMap().get('S').getType()).isEqualTo(Material.STICK);

            ItemStack result = recipe.getResult();
            assertThat(result.getType()).isEqualTo(Material.DIAMOND_SWORD);
            assertThat(result.getAmount()).isEqualTo(1);
            assertThat(result.getItemMeta().getDisplayName()).isEqualTo("\u00a7bHoly Blade");
            assertThat(result.getItemMeta().getLore()).containsExactly("\u00a77A mighty sword");
        }

        /**
         * Shape 2 of UltiKits/UltiRecipe#16's four, re-pointed by UltiKits/UltiRecipe#14. When
         * this case was first written the entry registered a stack of 100 unclamped, and the
         * assertion said so, because the declared {@code @Range(min = 1, max = 64)} on
         * {@code OutputItem.amount} was never evaluated by anything. It is evaluated now, so the
         * same file refuses the entry instead. The full behaviour - a violating entry skipped
         * beside a valid one that still loads, both bounds inclusive - lives in
         * {@code NestedOutputConstraints} below; this case stays here so the four shapes the
         * original issue was reproduced with remain readable as a set.
         */
        @Test
        @DisplayName("2 — output.amount 100 is refused by the declared @Range (UltiKits/UltiRecipe#14)")
        void amountAboveTheDeclaredRangeIsRefused() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  big_stack:\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "      amount: 100\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n");

            assertThat(service.initRecipes()).isZero();
            assertThat(service.getRecipeList()).isEmpty();
            assertThat(warnings()).containsExactly(
                    "Invalid output for recipe: big_stack - output.amount: value 100 is out of range [1, 64]");
        }

        @Test
        @DisplayName("3 — a 2-row shape is skipped by the row-count check that already existed")
        void twoRowShapeIsSkipped() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  two_rows:\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "    shape:\n"
                    + "      - \"DD\"\n"
                    + "      - \"DD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n");

            assertThat(service.initRecipes()).isZero();
            assertThat(service.getRecipeList()).isEmpty();
            assertThat(warnings()).containsExactly(
                    "Recipe 'two_rows' not registered: the shape must have exactly 3 rows, found [DD, DD]");
        }

        @Test
        @DisplayName("4 — a name with a space is skipped, keeping the existing registration-failure wording")
        void nameWithASpaceIsSkipped() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  bad name:\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n");

            assertThat(service.initRecipes()).isZero();
            assertThat(service.getRecipeList()).isEmpty();
            // The literal `ultirecipe.config.recipes-yml` asserts verbatim. A recipe whose SHAPE
            // binds but which cannot be registered must keep reporting through this message, not
            // through the binding message below.
            assertThat(warnings()).hasSize(1);
            assertThat(warnings().get(0)).startsWith("Failed to register recipe: bad name - ");
        }
    }

    // --- one bad entry never costs the good ones -----------------------------------------

    @Nested
    @DisplayName("a malformed entry is skipped without taking the file down")
    class PartialFailure {

        @Test
        @DisplayName("a malformed entry beside a valid one yields one registration and one warning")
        void malformedEntryBesideAValidOne() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  broken: not a mapping\n"
                    + "  good:\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n");

            assertThat(service.initRecipes()).isEqualTo(1);
            assertThat(service.getRecipeList()).containsExactly("good");
            assertThat(warnings())
                    .containsExactly("Skipped recipe 'broken': expected a mapping, found the text 'not a mapping'");
        }

        @Test
        @DisplayName("the whole file still loads when EVERY entry is malformed")
        void everyEntryMalformed() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  a: 1\n"
                    + "  b: 2\n");

            assertThat(service.initRecipes()).isZero();
            assertThat(warnings()).hasSize(2);
        }

        @Test
        @DisplayName("an empty recipes map registers nothing and warns about nothing")
        void emptyMapRegistersNothing() throws Exception {
            givenRecipesYml("recipes: {}\n");

            assertThat(service.initRecipes()).isZero();
            assertThat(service.getRecipeList()).isEmpty();
            verify(UltiRecipeTestHelper.getMockLogger(), never()).warn(anyString());
            verify(UltiRecipeTestHelper.getMockLogger()).info("No custom recipes configured");
        }
    }

    // --- every way an entry can fail to bind ----------------------------------------------

    @Nested
    @DisplayName("an entry that cannot be bound names its own key and reason")
    class BindingFailures {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.ultikits.plugins.recipe.service.RecipeYamlLoadTest#unbindableEntries")
        void unbindableEntryIsSkippedWithItsReason(String description, String yml, String expectedWarning)
                throws Exception {
            givenRecipesYml(yml);

            assertThat(service.initRecipes()).isZero();
            assertThat(service.getRecipeList()).isEmpty();
            assertThat(warnings()).containsExactly(expectedWarning);
        }

        // The two cases below could not be written in YAML before 6.3.0: Bukkit's parser DROPPED a
        // mapping key whose value is empty (`hollow:`, `D:`), so the key never reached the binder. The
        // framework's own storage keeps such a key with a null value, so both are now operator cases,
        // and both still report what the 6.2 binder reported for the same value built in code.

        @Test
        @DisplayName("an entry with no value at all (`hollow:`) is skipped, naming it")
        void nullEntryIsSkipped() throws Exception {
            givenRecipesYml("recipes:\n  hollow:\n");

            assertThat(service.initRecipes()).isZero();
            assertThat(warnings()).containsExactly("Skipped recipe 'hollow': expected a mapping, found nothing");
        }

        @Test
        @DisplayName("an ingredient with no material (`D:`) is skipped, naming it")
        void nullIngredientValueIsSkipped() throws Exception {
            givenRecipesYml("recipes:\n  e:\n    ingredients:\n      D:\n");

            assertThat(service.initRecipes()).isZero();
            assertThat(warnings()).containsExactly("Skipped recipe 'e': ingredients.D: has no value");
        }
    }

    // --- a required key that is absent must not produce a false success -------------------

    @Nested
    @DisplayName("an entry missing a required key never registers a recipe nobody can craft")
    class RequiredKeyAbsent {

        /**
         * The two instruments disagree for this fixture, so each is stated with its own
         * observation. One row per instrument (measured for UltiKits/UltiRecipe#16).
         *
         * <pre>
         * instrument         before the fix, entry with no `ingredients`       measured by
         * ----------------   ---------------------------------------------     ----------------
         * MockBukkit         registers; logs `Registered recipe: &lt;name&gt;`;       the tests below,
         * (what the tests    appears in `/recipe list`; no warn call            before `485d016`
         * below assert)
         *
         * Paper 1.21.11      `ShapedRecipePattern.of` throws                    paper-1.21.11-
         *                    `ArrayIndexOutOfBoundsException: Index 0 out       probe.out, case B
         *                    of bounds for length 0`; reaches
         *                    `initRecipes`'s catch; the operator reads
         *                    `Failed to register recipe: &lt;name&gt; - Index 0
         *                    out of bounds for length 0`; does not register
         * </pre>
         *
         * `ServerMock#addRecipe` null-checks and stores; it never reaches
         * `CraftShapedRecipe#addToCraftingManager`, which is where the Paper row happens. Neither
         * row may be restated from the other - two earlier revisions of this javadoc stated the
         * MockBukkit row as server behaviour.
         *
         * <p>`registerRecipe` refuses a definition whose output (or the output's material), shape or
         * ingredients is absent or empty, with the one line asserted below. Up to UltiTools-API 6.2 the
         * binder turned each of those into null for that guard; since UltiKits/UltiRecipe#32 the
         * converter keeps the value as written (so a recipe round-trips through the framework) and the
         * guard reads "absent or empty" itself. The operator reads the same line either way.
         */
        private void assertRefusedWithoutRegistering(String key) {
            assertThat(service.initRecipes()).isZero();
            assertThat(service.getRecipeList()).isEmpty();
            // The harm is the false success, so assert its absence directly.
            verify(UltiRecipeTestHelper.getMockLogger(), never()).info(startsWith("Registered recipe"));
            assertThat(warnings()).containsExactly("Invalid recipe definition for: " + key);
        }

        @Test
        @DisplayName("no `ingredients` key at all")
        void ingredientsKeyAbsent() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  no_ingredients:\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n");

            assertRefusedWithoutRegistering("no_ingredients");
        }

        @Test
        @DisplayName("`ingredients:` with nothing under it")
        void ingredientsKeyWithNoValue() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  hollow_ingredients:\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n");

            assertRefusedWithoutRegistering("hollow_ingredients");
        }

        @Test
        @DisplayName("`ingredients: {}` written out as an empty mapping")
        void ingredientsExplicitlyEmpty() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  empty_ingredients:\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients: {}\n");

            assertRefusedWithoutRegistering("empty_ingredients");
        }

        @Test
        @DisplayName("no `shape` key at all")
        void shapeKeyAbsent() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  no_shape:\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n");

            assertRefusedWithoutRegistering("no_shape");
        }

        @Test
        @DisplayName("`output: {}` - supplied, but carrying no material")
        void outputEmptyMapping() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  empty_output:\n"
                    + "    output: {}\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n");

            assertRefusedWithoutRegistering("empty_output");
        }

        @Test
        @DisplayName("an `output` block with everything except `material`")
        void outputWithoutMaterial() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  no_material:\n"
                    + "    output:\n"
                    + "      amount: 2\n"
                    + "      name: \"&bNameless\"\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n");

            assertRefusedWithoutRegistering("no_material");
        }

        @Test
        @DisplayName("`shape: []` written out as an empty list")
        void shapeExplicitlyEmpty() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  empty_shape:\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "    shape: []\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n");

            assertRefusedWithoutRegistering("empty_shape");
        }

        /**
         * The boundary of "nothing usable" has three sides now, not two, and this case moved
         * across one of them on purpose (UltiKits/UltiRecipe#14). An {@code output} block with no
         * {@code material} at all still binds to a null definition-output and reports
         * {@code Invalid recipe definition for: <name>}. A material that is present and unusable
         * still reports {@code Invalid output material for recipe: <name>} - pinned by the case
         * below this one, which is the control proving only the empty side moved. An EMPTY
         * material used to report that same second message, because an empty string also fails
         * {@code Material.matchMaterial}; it now reports the {@code @NotEmpty} declared on the
         * field, which names the sub-key. The two mistakes were indistinguishable in the log
         * before: an operator who left {@code material:} blank and an operator who misspelled
         * {@code DIAMOND_SWORD} read the identical line.
         */
        @Test
        @DisplayName("an empty material string now names the declared @NotEmpty, not the match failure")
        void emptyMaterialStringNamesTheDeclaredConstraint() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  blank_material:\n"
                    + "    output:\n"
                    + "      material: \"\"\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n");

            assertThat(service.initRecipes()).isZero();
            assertThat(service.getRecipeList()).isEmpty();
            verify(UltiRecipeTestHelper.getMockLogger(), never()).info(startsWith("Registered recipe"));
            assertThat(warnings()).containsExactly(
                    "Invalid output for recipe: blank_material - output.material: must not be empty");
        }

        @Test
        @DisplayName("a material that is present but unusable keeps its own, more specific message")
        void unusableMaterialKeepsTheSpecificMessage() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  bad_material:\n"
                    + "    output:\n"
                    + "      material: NOT_A_MATERIAL\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n");

            assertThat(service.initRecipes()).isZero();
            assertThat(service.getRecipeList()).isEmpty();
            verify(UltiRecipeTestHelper.getMockLogger(), never()).info(startsWith("Registered recipe"));
            assertThat(warnings()).containsExactly(
                    "Recipe 'bad_material' not registered: unknown output material 'NOT_A_MATERIAL'");
        }

        @Test
        @DisplayName("no `output` key at all - the one arm of that guard the binder never broke")
        void outputKeyAbsent() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  no_output:\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n");

            assertRefusedWithoutRegistering("no_output");
        }

        @Test
        @DisplayName("the refusal costs the well-formed entry beside it nothing")
        void aRefusedEntryDoesNotCostItsNeighbour() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  no_ingredients:\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "  good:\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n");

            // The neighbour is this case's own control: if the fixture or the path were broken,
            // this count would be 0 rather than 1 and the refusal above would prove nothing.
            assertThat(service.initRecipes()).isEqualTo(1);
            assertThat(service.getRecipeList()).containsExactly("good");
            assertThat(warnings()).containsExactly("Invalid recipe definition for: no_ingredients");
        }
    }

    // --- a defect this change does not fix, pinned so a later fix can see the contract ----

    /**
     * `RecipeService#registerRecipe`'s ingredient loop uses `continue` for both of its failure
     * branches - a key that is not one character, and a material that does not resolve - and then
     * registers the recipe regardless of how many ingredients were actually set. Filed as
     * UltiKits/UltiRecipe#21; NOT fixed here, because that loop is recipe validation and belongs
     * with #13/#14.
     *
     * <p>Pinned rather than left unrecorded because the fix for #13/#14 touches exactly this method, and
     * without a test nothing in the tree says that "warn, skip the ingredient, register anyway" is
     * the contract today.
     *
     * <p><b>What this harness can and cannot see.</b> MockBukkit's `ServerMock#addRecipe`
     * null-checks and stores, so what the tests below assert is what the module handed over. It
     * never reaches `CraftShapedRecipe#addToCraftingManager`, so it observes nothing of the rows
     * below, which were measured against a bootstrapped Paper 1.21.11
     * (`paper-1.21.11-probe.out`). One row per case:
     *
     * <pre>
     * case                              on Paper 1.21.11                        probe row
     * -------------------------------   -------------------------------------   ---------
     * `DSD` x3, `S` unusable            registers as `D D` x3, accepted at      case A
     *   (part of the shape defined)     width 3, height 3; craftable
     *   -> unknownIngredientMaterial-
     *      StillRegisters
     *
     * `DDD` x3, key `DD` unusable       `ShapedRecipePattern.of` throws          case B
     *   (nothing defined)               `ArrayIndexOutOfBoundsException:
     *   -> multiCharacterIngredient-    Index 0 out of bounds for length 0`;
     *      KeyStillRegisters            does not register
     * </pre>
     *
     * Each test below names its own row. Do not quote one for the other; both are recorded in
     * #21 with the same measurement.
     */
    @Nested
    @DisplayName("an entry with an unusable ingredient is refused, naming the ingredient as written (UltiKits/UltiRecipe#21)")
    class UnusableIngredientRefused {

        /**
         * The FIRST row of the table on this class: `S` is unusable but `D` is defined. Registering
         * it anyway made Paper 1.21.11 register `D D` x3, a craftable recipe the operator never
         * wrote. It is now refused before anything reaches the server.
         */
        @Test
        @DisplayName("an unknown ingredient material: not registered, the key and the material named; a good neighbour still registers")
        void unknownIngredientMaterialIsRefused() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  partial:\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "    shape:\n"
                    + "      - \"DSD\"\n"
                    + "      - \"DSD\"\n"
                    + "      - \"DSD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n"
                    + "      S: NOT_A_MATERIAL\n"
                    + GOOD_ENTRY);

            assertThat(service.initRecipes()).isEqualTo(1);
            assertThat(warnings()).containsExactly(
                    "Recipe 'partial' not registered: ingredient 'S' names an unknown material 'NOT_A_MATERIAL'");
            verify(UltiRecipeTestHelper.getMockLogger(), never()).info("Registered recipe: partial");
            assertThat(service.getRecipeList()).containsExactly("good");
            assertThat(Bukkit.getRecipe(new NamespacedKey(UltiRecipeTestHelper.getMockJavaPlugin(),
                    "ultirecipe_partial"))).isNull();
        }

        /**
         * The second row: nothing in the shape is defined. On Paper 1.21.11 this threw from
         * `ShapedRecipePattern.of` and was reported as an opaque index error; it is now refused
         * with the key as written.
         */
        @Test
        @DisplayName("a multi-character ingredient key: not registered, the key named as written")
        void multiCharacterIngredientKeyIsRefused() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  wide_key:\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n"
                    + "      DD: DIAMOND\n");

            assertThat(service.initRecipes()).isZero();
            assertThat(warnings()).containsExactly(
                    "Recipe 'wide_key' not registered: ingredient key 'DD' is not a single character");
            assertThat(service.getRecipeList()).isEmpty();
        }

        @Test
        @DisplayName("every unusable ingredient of one entry is named, and the entry is refused once")
        void everyUnusableIngredientIsNamed() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  odd:\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n"
                    + "      xx: COAL\n"
                    + "      Q: NOPE_NOT_REAL\n");

            assertThat(service.initRecipes()).isZero();
            assertThat(warnings()).containsExactlyInAnyOrder(
                    "Recipe 'odd' not registered: ingredient key 'xx' is not a single character",
                    "Recipe 'odd' not registered: ingredient 'Q' names an unknown material 'NOPE_NOT_REAL'");
            assertThat(service.getRecipeList()).isEmpty();
        }
    }

    // --- operator values the module cannot use: the output material and the shape -----------

    @Nested
    @DisplayName("an unusable output material or shape is refused, quoting the value as written")
    class UnusableOutputOrShapeNamed {

        @Test
        @DisplayName("an unknown output material is named")
        void unknownOutputMaterialNamed() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  no_such:\n"
                    + "    output:\n"
                    + "      material: DIAMUND\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n");

            assertThat(service.initRecipes()).isZero();
            assertThat(warnings()).containsExactly(
                    "Recipe 'no_such' not registered: unknown output material 'DIAMUND'");
        }

        @Test
        @DisplayName("a shape without three rows is quoted")
        void shapeRowsQuoted() throws Exception {
            givenRecipesYml(
                    "recipes:\n"
                    + "  two_rows:\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "    shape:\n"
                    + "      - \"DD\"\n"
                    + "      - \"DD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n");

            assertThat(service.initRecipes()).isZero();
            assertThat(warnings()).containsExactly(
                    "Recipe 'two_rows' not registered: the shape must have exactly 3 rows, found [DD, DD]");
        }
    }

    // --- values already in their declared shape still work --------------------------------

    @Nested
    @DisplayName("a map holding definitions built in code is accepted")
    class AlreadyBound {

        @Test
        @DisplayName("a RecipeDefinition built in code registers unchanged")
        void builtDefinitionRegisters() throws Exception {
            RecipeConfig.RecipeDefinition definition = new RecipeConfig.RecipeDefinition();
            RecipeConfig.OutputItem output = new RecipeConfig.OutputItem();
            output.setMaterial("DIAMOND");
            definition.setOutput(output);
            definition.setShape(Arrays.asList("DDD", "DDD", "DDD"));
            Map<String, String> ingredients = new LinkedHashMap<>();
            ingredients.put("D", "DIAMOND");
            definition.setIngredients(ingredients);

            Map<String, RecipeConfig.RecipeDefinition> recipes = new LinkedHashMap<>();
            recipes.put("built", definition);
            givenRecipes(recipes);

            assertThat(service.initRecipes()).isEqualTo(1);
            assertThat(service.getRecipeList()).containsExactly("built");
        }
    }

    // --- the declared constraints on OutputItem's own fields (UltiKits/UltiRecipe#14) -------

    /**
     * {@code OutputItem.material} declares {@code @NotEmpty} and {@code OutputItem.amount}
     * declares {@code @Range(min = 1, max = 64)}, and until UltiKits/UltiRecipe#14 neither was
     * ever evaluated: the framework's {@code AbstractConfigEntity#validateFields()} walks only
     * the {@code @ConfigEntry} fields on the entity itself and never recurses into a map value's
     * object graph, and {@code RecipeService} applied no bound check of its own.
     *
     * <p>Every case here puts the violating entry BESIDE a valid one in the same file, because
     * the two halves of the contract are separable and a test that only wrote the bad entry
     * would prove just one of them: the violating entry must be skipped, AND the rest of the
     * file must still register. A refusal that took the whole file down with it would satisfy
     * the first half and fail the operator.
     *
     * <p>Like the rest of this class, nothing here names the code that performs the check, so
     * the revert proof's RED is a behaviour failure rather than a compilation error.
     */
    @Nested
    @DisplayName("the declared @NotEmpty/@Range on OutputItem are enforced (UltiKits/UltiRecipe#14)")
    class NestedOutputConstraints {

        private String withNeighbour(String offendingEntry) {
            return "recipes:\n" + GOOD_ENTRY + offendingEntry;
        }

        private String entryWithAmount(String name, String amount) {
            return "  " + name + ":\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "      amount: " + amount + "\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n";
        }

        @Test
        @DisplayName("an amount above the declared maximum is skipped; its neighbour still loads")
        void amountAboveTheMaximumIsSkippedBesideAValidRecipe() throws Exception {
            givenRecipesYml(withNeighbour(entryWithAmount("big_stack", "100")));

            assertThat(service.initRecipes()).isEqualTo(1);
            assertThat(service.getRecipeList()).containsExactly("good");
            assertThat(warnings()).containsExactly(
                    "Invalid output for recipe: big_stack - output.amount: value 100 is out of range [1, 64]");
            assertThat(registered("good").getResult().getType()).isEqualTo(Material.DIAMOND_SWORD);
        }

        @Test
        @DisplayName("an amount below the declared minimum is skipped; its neighbour still loads")
        void amountBelowTheMinimumIsSkippedBesideAValidRecipe() throws Exception {
            givenRecipesYml(withNeighbour(entryWithAmount("zero_stack", "0")));

            assertThat(service.initRecipes()).isEqualTo(1);
            assertThat(service.getRecipeList()).containsExactly("good");
            assertThat(warnings()).containsExactly(
                    "Invalid output for recipe: zero_stack - output.amount: value 0 is out of range [1, 64]");
        }

        /**
         * The bounds are inclusive, the same way the framework's own {@code @Range} check is
         * ({@code num < min || num > max}). Without this case a check written one comparison too
         * strict would refuse a legal stack of 64 and every other case here would still pass.
         */
        @Test
        @DisplayName("both declared bounds are inclusive - 1 and 64 register")
        void theDeclaredBoundsAreInclusive() throws Exception {
            givenRecipesYml("recipes:\n"
                    + entryWithAmount("at_min", "1")
                    + entryWithAmount("at_max", "64"));

            assertThat(service.initRecipes()).isEqualTo(2);
            assertThat(service.getRecipeList()).containsExactlyInAnyOrder("at_min", "at_max");
            assertThat(warnings()).isEmpty();
            assertThat(registered("at_min").getResult().getAmount()).isEqualTo(1);
            assertThat(registered("at_max").getResult().getAmount()).isEqualTo(64);
        }

        @Test
        @DisplayName("an empty material is skipped naming the field; its neighbour still loads")
        void emptyMaterialIsSkippedBesideAValidRecipe() throws Exception {
            givenRecipesYml(withNeighbour(
                    "  blank:\n"
                    + "    output:\n"
                    + "      material: \"\"\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n"));

            assertThat(service.initRecipes()).isEqualTo(1);
            assertThat(service.getRecipeList()).containsExactly("good");
            assertThat(warnings()).containsExactly(
                    "Invalid output for recipe: blank - output.material: must not be empty");
            assertThat(registered("good").getResult().getType()).isEqualTo(Material.DIAMOND_SWORD);
        }

        /**
         * {@code @NotEmpty}'s own semantics are "null, or empty after trimming" - the framework
         * spells that out in {@code isNotEmptyViolation}. A whitespace-only material reaches
         * exactly the same refusal, and it is worth its own case because it is the one spelling
         * that a plain {@code isEmpty()} check would let through into
         * {@code Material.matchMaterial}.
         */
        @Test
        @DisplayName("a whitespace-only material is empty too, per @NotEmpty's own semantics")
        void whitespaceOnlyMaterialIsSkipped() throws Exception {
            givenRecipesYml(withNeighbour(
                    "  spaces:\n"
                    + "    output:\n"
                    + "      material: \"   \"\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n"));

            assertThat(service.initRecipes()).isEqualTo(1);
            assertThat(service.getRecipeList()).containsExactly("good");
            assertThat(warnings()).containsExactly(
                    "Invalid output for recipe: spaces - output.material: must not be empty");
        }

        /**
         * The coverage guard: every constraint annotation anywhere in this configuration's object
         * graph must be read by somebody, and this case names who.
         *
         * <p>There are exactly two readers, and between them they do not cover the graph:
         * <pre>
         * class               reached by                          reads config-package annotations on
         * -----------------   ---------------------------------   -----------------------------------
         * RecipeConfig        AbstractConfigEntity#validateFields  its @ConfigEntry fields, all four
         *                                                          annotations
         * RecipeConfig        - (the framework walks @ConfigEntry  nothing: a constraint on a field
         *   non-@ConfigEntry    fields only)                       the framework never visits is read
         *   field                                                  by nobody
         * OutputItem          RecipeService#registerRecipe, via    @NotEmpty and @Range only
         *                     findConstraintViolation()
         * RecipeDefinition    - (nothing reads it)                 nothing
         * </pre>
         *
         * <p>The first revision of this case walked {@code OutputItem} alone, which left the row
         * that matters most uncovered. {@code RecipeDefinition} is unreachable by the framework's
         * validator for exactly the reason {@code OutputItem} is - {@code validateFields()} never
         * recurses into a nested object graph - so a later author following the direction
         * UltiKits/UltiRecipe#14 sets, and replacing {@code registerRecipe}'s hand-written
         * three-row shape check with {@code @Size(min = 3, max = 3)} on
         * {@code RecipeDefinition.shape}, would ship a fresh silent no-op with the suite green.
         * Widening the walk to {@code RecipeConfig} and every class it declares removes the class
         * of defect rather than the one instance of it.
         *
         * <p>A constraint being read is necessary and not sufficient, so the walk makes a second
         * assertion. {@code @Range} is gated on {@code value instanceof Number} by this module's
         * reader and by the framework's own {@code isRangeViolation} alike, so {@code @Range(min
         * = 1, max = 3)} on {@code lore} - a plausible confusion with {@code @Size}, which is the
         * annotation that actually takes a length - is read, skipped, and enforces nothing. The
         * first assertion cannot see that, because {@code @Range} on {@code OutputItem} is by
         * construction "read". The two assertions together are what makes the guard cover the
         * defect rather than one of its spellings.
         *
         * <p>It is green both before and after the fix: it guards the fix's scope, it does not
         * prove it. That each assertion can actually fail was shown separately, by mutating the
         * config (an {@code @Size} on {@code RecipeDefinition}, an {@code @Range} on {@code
         * lore}) and watching it go red.
         */
        @Test
        @DisplayName("every constraint declared in this config's object graph is read by something")
        void everyDeclaredConstraintIsReadBySomething() {
            List<Class<?>> graph = new ArrayList<>();
            graph.add(RecipeConfig.class);
            graph.addAll(Arrays.asList(RecipeConfig.class.getDeclaredClasses()));

            List<String> readByNobody = new ArrayList<>();
            List<String> unjudgeable = new ArrayList<>();
            for (Class<?> owner : graph) {
                for (Field field : owner.getDeclaredFields()) {
                    for (Annotation annotation : field.getAnnotations()) {
                        Class<? extends Annotation> type = annotation.annotationType();
                        Package declaring = type.getPackage();
                        if (declaring == null
                                || !"com.ultikits.ultitools.annotations.config".equals(declaring.getName())) {
                            continue;
                        }
                        String site = owner.getSimpleName() + "." + field.getName()
                                + " @" + type.getSimpleName();
                        boolean theFrameworkReadsIt = owner == RecipeConfig.class
                                && field.isAnnotationPresent(ConfigEntry.class);
                        boolean thisModuleReadsIt = owner == RecipeConfig.OutputItem.class
                                && (type == NotEmpty.class || type == Range.class);
                        if (!theFrameworkReadsIt && !thisModuleReadsIt) {
                            readByNobody.add(site);
                        } else if (type == Range.class && !judgeableAsANumber(field.getType())) {
                            unjudgeable.add(site + " on " + field.getType().getSimpleName());
                        }
                    }
                }
            }
            assertThat(readByNobody)
                    .as("a constraint annotation nothing reads is a silent no-op. The framework reads "
                            + "only RecipeConfig's own @ConfigEntry fields, and this module reads only "
                            + "@NotEmpty/@Range on OutputItem - so teach the reader about it, or take "
                            + "it off the field")
                    .isEmpty();
            assertThat(unjudgeable)
                    .as("@Range is gated on `value instanceof Number` by this module's reader and by "
                            + "the framework's own isRangeViolation alike, so on a non-numeric field it "
                            + "is read and then silently skipped - the same no-op with an extra step. "
                            + "@Size is the annotation that takes a length")
                    .isEmpty();
        }

        /**
         * Whether a field's declared type can reach {@code @Range}'s {@code value instanceof
         * Number} gate once reflection has boxed it. Primitives box to a {@code Number} subclass
         * and so qualify, apart from {@code boolean} and {@code char}, whose boxes do not extend
         * {@code Number}.
         *
         * @param type the field's declared type
         * @return whether a {@code @Range} on a field of this type would ever be evaluated
         */
        private boolean judgeableAsANumber(Class<?> type) {
            return Number.class.isAssignableFrom(type)
                    || type == byte.class || type == short.class || type == int.class
                    || type == long.class || type == float.class || type == double.class;
        }

        /**
         * The graph the guard above walks is not empty and not a singleton - a walk that found no
         * class at all, or only {@code RecipeConfig}, would pass the guard for the wrong reason
         * and look identical in the report.
         */
        @Test
        @DisplayName("the guard's walk reaches both nested classes, not just the entity")
        void theGuardWalksBothNestedClasses() {
            List<Class<?>> declared = Arrays.asList(RecipeConfig.class.getDeclaredClasses());

            assertThat(declared)
                    .as("if either nested class stops being reached, the guard above silently "
                            + "narrows back to the single-class walk its first revision made")
                    .contains(RecipeConfig.OutputItem.class, RecipeConfig.RecipeDefinition.class);
        }
    }

    // --- UltiKits/UltiRecipe#12 ------------------------------------------------------------

    @Nested
    @DisplayName("/recipe reload reads config/recipes.yml again (UltiKits/UltiRecipe#12)")
    class ReloadReadsTheFile {

        @Test
        @DisplayName("an edit made after start-up is what the reload registers")
        void editedFileIsRegistered() throws Exception {
            givenRecipesYml(LEGAL_SWORD);
            assertThat(service.initRecipes()).isEqualTo(1);
            // The operator edits the file: the next read of it yields only the 'good' recipe
            org.mockito.Mockito.doAnswer(invocation -> {
                givenRecipesYml("recipes:\n" + GOOD_ENTRY);
                return null;
            }).when(config).reload();

            int count = service.reloadRecipes();

            assertThat(count).isEqualTo(1);
            assertThat(service.getRecipeList()).containsExactly("good");
            assertThat(registered("good").getResult().getType()).isEqualTo(Material.DIAMOND_SWORD);
            assertThat(Bukkit.getRecipe(new NamespacedKey(UltiRecipeTestHelper.getMockJavaPlugin(),
                    "ultirecipe_Custom_Sword"))).as("the recipe the edit removed").isNull();
        }

        @Test
        @DisplayName("a file that cannot be read again keeps the recipes loaded before, and says so")
        void unreadableFileKeepsWhatWasLoaded() throws Exception {
            givenRecipesYml(LEGAL_SWORD);
            service.initRecipes();
            org.mockito.Mockito.doThrow(new java.io.IOException("disk gone")).when(config).reload();

            int count = service.reloadRecipes();

            assertThat(count).isEqualTo(1);
            // the registry key is lower-cased, as every NamespacedKey is
            assertThat(service.getRecipeList()).containsExactly("custom_sword");
            assertThat(warnings()).anySatisfy(line -> assertThat(line).contains("disk gone"));
        }
    }

    // --- UltiKits/UltiRecipe#24 ------------------------------------------------------------

    @Nested
    @DisplayName("a fractional output.amount is refused, quoting it as written (UltiKits/UltiRecipe#24)")
    class FractionalAmountRefused {

        private String entry(String name, String amount) {
            return "  " + name + ":\n"
                    + "    output:\n"
                    + "      material: DIAMOND\n"
                    + "      amount: " + amount + "\n"
                    + "    shape:\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "      - \"DDD\"\n"
                    + "    ingredients:\n"
                    + "      D: DIAMOND\n";
        }

        @Test
        @DisplayName("2.5 is refused with 2.5 in the warning, and a good neighbour still registers")
        void twoPointFiveIsRefused() throws Exception {
            givenRecipesYml("recipes:\n" + entry("frac", "2.5") + GOOD_ENTRY);

            assertThat(service.initRecipes()).isEqualTo(1);
            assertThat(service.getRecipeList()).containsExactly("good");
            assertThat(warnings()).containsExactly(
                    "Skipped recipe 'frac': output.amount: expected a whole number, found 2.5");
        }

        @Test
        @DisplayName("0.5 is refused naming 0.5, not the 0 it used to be truncated to")
        void zeroPointFiveNamesWhatWasWritten() throws Exception {
            givenRecipesYml("recipes:\n" + entry("half", "0.5"));

            assertThat(service.initRecipes()).isZero();
            assertThat(warnings()).containsExactly(
                    "Skipped recipe 'half': output.amount: expected a whole number, found 0.5");
        }

        @Test
        @DisplayName("a whole number written with a decimal point (2.0) is still a stack of 2")
        void twoPointZeroIsTwo() throws Exception {
            givenRecipesYml("recipes:\n" + entry("two", "2.0"));

            assertThat(service.initRecipes()).isEqualTo(1);
            assertThat(registered("two").getResult().getAmount()).isEqualTo(2);
            assertThat(warnings()).isEmpty();
        }

        @Test
        @DisplayName("a whole number too large for a stack size is out of range as written, not wrapped")
        void hugeNumberIsOutOfRangeAsWritten() throws Exception {
            givenRecipesYml("recipes:\n" + entry("huge", "3000000000"));

            assertThat(service.initRecipes()).isZero();
            assertThat(warnings()).containsExactly(
                    "Skipped recipe 'huge': output.amount: value 3000000000 is out of range [1, 64]");
        }
    }
}
