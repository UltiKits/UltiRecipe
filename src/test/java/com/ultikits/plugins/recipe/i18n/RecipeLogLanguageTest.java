package com.ultikits.plugins.recipe.i18n;

import com.ultikits.plugins.recipe.UltiRecipeTestHelper;
import com.ultikits.plugins.recipe.config.RecipeConfig;
import com.ultikits.plugins.recipe.service.RecipeService;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import com.ultikits.ultitools.interfaces.impl.pasers.DefaultConfigParser;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every console line {@code RecipeService} writes for the operator follows the framework's
 * {@code language} setting.
 * <p>
 * The module's {@code i18n} answers from the real shipped {@code zh} catalogue ({@link CatalogueText}),
 * and each test asserts the exact line logged. Before the language sweep these lines were English
 * literals, so each test fails by showing the English line that was actually logged.
 */
@DisplayName("RecipeService console lines follow the language setting")
class RecipeLogLanguageTest {

    private RecipeService service;
    private RecipeConfig config;
    private PluginLogger logger;

    @BeforeEach
    void setUp() throws Exception {
        UltiRecipeTestHelper.setUp();
        when(UltiRecipeTestHelper.getMockPlugin().i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));
        logger = UltiRecipeTestHelper.getMockLogger();
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
     * The zh catalogue line with its arguments filled in, or a marker naming the missing key, so a
     * failure shows the line that was actually logged next to what was expected.
     */
    private static String zh(String key, Object... args) {
        String text = CatalogueText.entries("zh").get(key);
        return text == null ? "<lang/zh.json has no " + key + ">" : String.format(text, args);
    }

    @SuppressWarnings("unchecked")
    private void givenRecipesYml(String yml) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(yml);
        Object raw = yaml.get("recipes");
        Object parsed = raw == null ? new LinkedHashMap<String, Object>() : new DefaultConfigParser().parse(raw);
        when(config.getRecipes()).thenReturn((Map<String, RecipeConfig.RecipeDefinition>) parsed);
    }

    private static String recipe(String name, String material, String amount, String... shapeAndIngredients) {
        StringBuilder b = new StringBuilder("recipes:\n  " + name + ":\n    output:\n      material: " + material + "\n");
        if (amount != null) {
            b.append("      amount: ").append(amount).append('\n');
        }
        for (String line : shapeAndIngredients) {
            b.append(line).append('\n');
        }
        return b.toString();
    }

    private static final String[] THREE_ROWS_D = {"    shape:", "      - \"DDD\"", "      - \"DDD\"", "      - \"DDD\"",
            "    ingredients:", "      D: DIAMOND"};

    private List<String> warnings() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger, atLeast(0)).warn(captor.capture());
        return captor.getAllValues();
    }

    private List<String> infos() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger, atLeast(0)).info(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("no recipes configured")
    void noneConfigured() {
        when(config.getRecipes()).thenReturn(null);
        service.initRecipes();
        assertThat(infos()).containsExactly(zh("recipe.log.none_configured"));
    }

    @Test
    @DisplayName("a recipe that is not a mapping is skipped")
    void skipped() throws Exception {
        givenRecipesYml("recipes:\n  e: hello\n");
        service.initRecipes();
        assertThat(warnings()).containsExactly(zh("recipe.log.skipped", "e",
                zh("recipe.reason.entry_expected_mapping", zh("recipe.found.text", "hello"))));
    }

    @Test
    @DisplayName("a recipe that cannot be registered")
    void registerFailed() throws Exception {
        givenRecipesYml(recipe("bad name", "DIAMOND", null, THREE_ROWS_D));
        service.initRecipes();
        assertThat(warnings()).singleElement().asString().startsWith(zh("recipe.log.register_failed", "bad name", ""));
    }

    @Test
    @DisplayName("a definition with no output")
    void invalidDefinition() {
        RecipeConfig.RecipeDefinition definition = new RecipeConfig.RecipeDefinition();
        definition.setOutput(null);
        Map<String, RecipeConfig.RecipeDefinition> recipes = new HashMap<>();
        recipes.put("invalid", definition);
        when(config.getRecipes()).thenReturn(recipes);
        service.initRecipes();
        assertThat(warnings()).containsExactly(zh("recipe.log.invalid_definition", "invalid"));
    }

    @Test
    @DisplayName("an output that violates its declared constraints")
    void invalidOutput() throws Exception {
        givenRecipesYml(recipe("big_stack", "DIAMOND", "100", THREE_ROWS_D));
        service.initRecipes();
        assertThat(warnings()).containsExactly(zh("recipe.log.invalid_output", "big_stack",
                zh("recipe.reason.out_of_range", "output.amount", 100, "1", "64")));
    }

    @Test
    @DisplayName("the reason a recipe value cannot be bound is in the server's language, down to what was found")
    void bindingReasons() throws Exception {
        givenRecipesYml("recipes:\n  a:\n    output: DIAMOND\n"
                + "  b:\n    output:\n      material: DIAMOND\n      amount: lots\n"
                + "  c:\n    output:\n      material: DIAMOND\n    shape: DDD\n"
                + "  d:\n    output:\n      material: DIAMOND\n    shape:\n      - [x]\n"
                + "  e:\n    output:\n      material: DIAMOND\n    ingredients: D\n"
                + "  g:\n    output:\n      material: DIAMOND\n    shape:\n      -\n"
                + "  h:\n    output:\n      material: \"\"\n" + String.join("\n", THREE_ROWS_D) + "\n"
                + "  i: 5\n  j: true\n  k: [1]\n");
        service.initRecipes();
        assertThat(warnings()).containsExactlyInAnyOrder(
                zh("recipe.log.skipped", "a", zh("recipe.reason.expected_mapping", "output", zh("recipe.found.text", "DIAMOND"))),
                zh("recipe.log.skipped", "b", zh("recipe.reason.expected_number", "output.amount", zh("recipe.found.text", "lots"))),
                zh("recipe.log.skipped", "c", zh("recipe.reason.expected_list", "shape", zh("recipe.found.text", "DDD"))),
                zh("recipe.log.skipped", "d", zh("recipe.reason.expected_text", "shape", zh("recipe.found.list"))),
                zh("recipe.log.skipped", "e", zh("recipe.reason.expected_mapping", "ingredients", zh("recipe.found.text", "D"))),
                zh("recipe.log.skipped", "g", zh("recipe.reason.empty_entry", "shape")),
                zh("recipe.log.invalid_output", "h", zh("recipe.reason.must_not_be_empty", "output.material")),
                zh("recipe.log.skipped", "i", zh("recipe.reason.entry_expected_mapping", zh("recipe.found.number"))),
                zh("recipe.log.skipped", "j", zh("recipe.reason.entry_expected_mapping", zh("recipe.found.boolean"))),
                zh("recipe.log.skipped", "k", zh("recipe.reason.entry_expected_mapping", zh("recipe.found.list"))));
    }

    @Test
    @DisplayName("an empty entry and an ingredient with no value (Bukkit's YAML drops both; a map built in code does not)")
    @SuppressWarnings("unchecked")
    void reasonsYamlCannotProduce() {
        Map<String, Object> ingredients = new LinkedHashMap<>();
        ingredients.put("D", null);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ingredients", ingredients);
        Map<String, Object> recipes = new LinkedHashMap<>();
        recipes.put("f", entry);
        recipes.put("l", null);
        when(config.getRecipes()).thenReturn((Map<String, RecipeConfig.RecipeDefinition>) (Map<String, ?>) recipes);
        service.initRecipes();
        assertThat(warnings()).containsExactly(
                zh("recipe.log.skipped", "f", zh("recipe.reason.no_value", "ingredients.D")),
                zh("recipe.log.skipped", "l", zh("recipe.reason.entry_expected_mapping", zh("recipe.found.nothing"))));
    }

    @Test
    @DisplayName("a mapping found where text belongs is named in the server's language")
    void foundMapping() throws Exception {
        givenRecipesYml("recipes:\n  m:\n    output:\n      material:\n        x: 1\n");
        service.initRecipes();
        assertThat(warnings()).containsExactly(zh("recipe.log.skipped", "m",
                zh("recipe.reason.expected_text", "output.material", zh("recipe.found.mapping"))));
    }

    @Test
    @DisplayName("an output material that does not exist")
    void invalidOutputMaterial() throws Exception {
        givenRecipesYml(recipe("no_such", "NOT_A_MATERIAL", null, THREE_ROWS_D));
        service.initRecipes();
        assertThat(warnings()).containsExactly(zh("recipe.log.invalid_output_material", "no_such"));
    }

    @Test
    @DisplayName("a shape without three rows")
    void shapeRows() throws Exception {
        givenRecipesYml(recipe("two_rows", "DIAMOND", null, "    shape:", "      - \"DD\"", "      - \"DD\"",
                "    ingredients:", "      D: DIAMOND"));
        service.initRecipes();
        assertThat(warnings()).containsExactly(zh("recipe.log.shape_rows", "two_rows"));
    }

    @Test
    @DisplayName("an ingredient key longer than one character, and an unknown ingredient material")
    void ingredientLines() throws Exception {
        givenRecipesYml(recipe("odd", "DIAMOND", null, "    shape:", "      - \"DDD\"", "      - \"DDD\"",
                "      - \"DDD\"", "    ingredients:", "      D: DIAMOND", "      xx: COAL", "      Q: NOPE_NOT_REAL"));
        service.initRecipes();
        assertThat(warnings()).containsExactlyInAnyOrder(
                zh("recipe.log.ingredient_key_length", "odd"),
                zh("recipe.log.unknown_material", "NOPE_NOT_REAL", "odd"));
    }

    @Test
    @DisplayName("a registered recipe, then its removal")
    void registeredAndRemoved() throws Exception {
        givenRecipesYml(recipe("good_one", "DIAMOND", null, THREE_ROWS_D));
        assertThat(service.initRecipes()).isEqualTo(1);
        service.removeRecipes();
        assertThat(infos()).containsExactly(
                zh("recipe.log.registered", "good_one"),
                zh("recipe.log.removed", "ultirecipe_good_one"));
    }
}
