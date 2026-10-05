package com.ultikits.plugins.recipe.config;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.config.convert.ConversionException;
import com.ultikits.ultitools.config.convert.ConverterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The recipe map's converter honours the framework's round-trip contract and reads every recipe the
 * 6.2 binder read (UltiKits/UltiRecipe#32).
 * <p>
 * Every case goes through the module's converter registry exactly as the framework prepares it at module
 * load ({@link FrameworkRecipeConfig#prepare}), never through the converter class by name, so the same
 * tests run unchanged against the sources from before the converter existed: there the registry has no
 * converter for {@code RecipeDefinition} and every case fails.
 * <p>
 * The contract, from {@code ConfigConverter}'s javadoc: (a) {@code fromPlain(toPlain(x))} equals
 * {@code x} for every definition whose collections hold no null element; (b) {@code toPlain(fromPlain(p))}
 * equals {@code p} for every plain value {@code p} the converter writes; (c) input it accepts in another
 * form is normalised once and stays put. The expected definitions for the shapes the 6.2 binder accepted
 * were captured from {@code RecipeDefinition#fromConfigValue} at {@code master} {@code 2caa44f}
 * (evidence {@code UltiRecipe-32-master-capture.log}).
 * <p>
 * 转换器满足框架的往返约定，并且读出 6.2 绑定器能读出的每一个配方。
 */
@DisplayName("RecipeDefinition converter: round trip and 6.2 parity (#32)")
class RecipeDefinitionConverterTest {

    private static final List<String> AT = Arrays.asList("recipes", "sample");

    @TempDir
    Path tempDir;

    private ConverterRegistry registry;

    @BeforeEach
    void setUp() {
        UltiToolsPlugin plugin = FrameworkRecipeConfig.plugin(tempDir, "en");
        FrameworkRecipeConfig.prepare(plugin);
        registry = ConverterRegistry.forModule(plugin);
    }

    private Object toPlain(RecipeConfig.RecipeDefinition value) throws ConversionException {
        return registry.toPlain(value, RecipeConfig.RecipeDefinition.class, FrameworkRecipeConfig.PATH, AT);
    }

    private RecipeConfig.RecipeDefinition fromPlain(Object plain) throws ConversionException {
        return registry.fromPlain(plain, RecipeConfig.RecipeDefinition.class, FrameworkRecipeConfig.PATH, AT);
    }

    // --- fixtures ----------------------------------------------------------------------------

    static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    static RecipeConfig.OutputItem output(String material, int amount, String name, List<String> lore) {
        RecipeConfig.OutputItem output = new RecipeConfig.OutputItem();
        output.setMaterial(material);
        output.setAmount(amount);
        output.setName(name);
        output.setLore(lore);
        return output;
    }

    static RecipeConfig.RecipeDefinition definition(RecipeConfig.OutputItem output, List<String> shape,
                                                    Map<String, String> ingredients) {
        RecipeConfig.RecipeDefinition definition = new RecipeConfig.RecipeDefinition();
        definition.setOutput(output);
        definition.setShape(shape);
        definition.setIngredients(ingredients);
        return definition;
    }

    static Map<String, String> ingredients(String... keysAndValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static final List<String> DDD = Arrays.asList("DDD", "DDD", "DDD");

    // --- (a) and (b): a definition round-trips, and so does its plain form --------------------

    @Test
    @DisplayName("a recipe using every field writes the shape recipes.yml uses, and reads back equal")
    void everyFieldRoundTrips() throws ConversionException {
        RecipeConfig.RecipeDefinition sword = definition(
                output("DIAMOND_SWORD", 1, "&bHoly Blade", Collections.singletonList("&7A mighty sword")),
                Arrays.asList(" D ", " D ", " S "), ingredients("D", "DIAMOND", "S", "STICK"));

        Object plain = toPlain(sword);

        assertThat(plain).isEqualTo(map(
                "output", map("material", "DIAMOND_SWORD", "amount", 1, "name", "&bHoly Blade",
                        "lore", Collections.singletonList("&7A mighty sword")),
                "shape", Arrays.asList(" D ", " D ", " S "),
                "ingredients", map("D", "DIAMOND", "S", "STICK")));
        assertThat(new ArrayList<Object>(((Map<?, ?>) plain).keySet())).as("key order, as the module's README writes a recipe")
                .containsExactly("output", "shape", "ingredients");
        assertThat(new ArrayList<Object>(((Map<?, ?>) ((Map<?, ?>) plain).get("output")).keySet()))
                .containsExactly("material", "amount", "name", "lore");
        assertThat(fromPlain(plain)).as("(a)").isEqualTo(sword);
        assertThat(toPlain(fromPlain(plain))).as("(b)").isEqualTo(plain);
    }

    static Stream<Arguments> sparseDefinitions() {
        return Stream.of(
                Arguments.of("no name and no lore", definition(output("DIAMOND", 2, null, null), DDD,
                        ingredients("D", "DIAMOND"))),
                Arguments.of("an empty lore", definition(output("DIAMOND", 1, null, new ArrayList<String>()), DDD,
                        ingredients("D", "DIAMOND"))),
                Arguments.of("no output", definition(null, DDD, ingredients("D", "DIAMOND"))),
                Arguments.of("an output with no material", definition(output(null, 1, "&bNameless", null), DDD,
                        ingredients("D", "DIAMOND"))),
                Arguments.of("no shape", definition(output("DIAMOND", 1, null, null), null,
                        ingredients("D", "DIAMOND"))),
                Arguments.of("an empty shape", definition(output("DIAMOND", 1, null, null),
                        new ArrayList<String>(), ingredients("D", "DIAMOND"))),
                Arguments.of("no ingredients", definition(output("DIAMOND", 1, null, null), DDD, null)),
                Arguments.of("empty ingredients", definition(output("DIAMOND", 1, null, null), DDD, ingredients())),
                Arguments.of("an ingredient with no material (a null map value)",
                        definition(output("DIAMOND", 1, null, null), DDD, ingredients("D", null))),
                Arguments.of("nothing at all", definition(null, null, null)),
                Arguments.of("a default-constructed definition", new RecipeConfig.RecipeDefinition()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sparseDefinitions")
    @DisplayName("a definition with absent or empty parts round-trips too")
    void sparseDefinitionRoundTrips(String description, RecipeConfig.RecipeDefinition x) throws ConversionException {
        Object plain = toPlain(x);

        assertThat(fromPlain(plain)).as("(a)").isEqualTo(x);
        assertThat(toPlain(fromPlain(plain))).as("(b)").isEqualTo(plain);
    }

    @Test
    @DisplayName("equality is by value: two separately built recipes with the same fields are equal")
    void equalityIsByValue() {
        RecipeConfig.RecipeDefinition one = definition(output("DIAMOND", 1, null, null), DDD, ingredients("D", "DIAMOND"));
        RecipeConfig.RecipeDefinition two = definition(output("DIAMOND", 1, null, null),
                new ArrayList<>(DDD), ingredients("D", "DIAMOND"));
        RecipeConfig.RecipeDefinition other = definition(output("DIAMOND", 2, null, null), DDD, ingredients("D", "DIAMOND"));

        assertThat(one).isEqualTo(two).hasSameHashCodeAs(two).isNotEqualTo(other);
    }

    // --- parity: every usable recipe the 6.2 binder read binds to the same recipe ----------------

    static Stream<Arguments> usableShapesMasterAccepts() {
        return Stream.of(
                Arguments.of("full",
                        map("output", map("material", "DIAMOND_SWORD", "amount", 1, "name", "&bHoly Blade",
                                "lore", Collections.singletonList("&7A mighty sword")),
                                "shape", Arrays.asList(" D ", " D ", " S "), "ingredients", map("D", "DIAMOND", "S", "STICK")),
                        definition(output("DIAMOND_SWORD", 1, "&bHoly Blade", Collections.singletonList("&7A mighty sword")),
                                Arrays.asList(" D ", " D ", " S "), ingredients("D", "DIAMOND", "S", "STICK"))),
                Arguments.of("minimal",
                        map("output", map("material", "DIAMOND"), "shape", DDD, "ingredients", map("D", "DIAMOND")),
                        definition(output("DIAMOND", 1, null, null), DDD, ingredients("D", "DIAMOND"))),
                Arguments.of("amount-2.0",
                        map("output", map("material", "DIAMOND", "amount", 2.0), "shape", DDD, "ingredients", map("D", "DIAMOND")),
                        definition(output("DIAMOND", 2, null, null), DDD, ingredients("D", "DIAMOND"))),
                Arguments.of("amount-long",
                        map("output", map("material", "DIAMOND", "amount", 3L), "shape", DDD, "ingredients", map("D", "DIAMOND")),
                        definition(output("DIAMOND", 3, null, null), DDD, ingredients("D", "DIAMOND"))),
                Arguments.of("scalars-as-text",
                        map("output", map("material", 123, "name", true, "lore", Arrays.asList(1, "x")),
                                "shape", DDD, "ingredients", map("1", 5)),
                        definition(output("123", 1, "true", Arrays.asList("1", "x")), DDD, ingredients("1", "5"))),
                Arguments.of("extra-key",
                        map("output", map("material", "DIAMOND", "colour", "red"), "permission", "x",
                                "shape", DDD, "ingredients", map("D", "DIAMOND")),
                        definition(output("DIAMOND", 1, null, null), DDD, ingredients("D", "DIAMOND"))),
                Arguments.of("amount-null",
                        map("output", map("material", "DIAMOND", "amount", null, "name", null, "lore", null),
                                "shape", DDD, "ingredients", map("D", "DIAMOND")),
                        definition(output("DIAMOND", 1, null, null), DDD, ingredients("D", "DIAMOND"))),
                Arguments.of("amount-zero (refused later, by @Range in RecipeService)",
                        map("output", map("material", "DIAMOND", "amount", 0), "shape", DDD, "ingredients", map("D", "DIAMOND")),
                        definition(output("DIAMOND", 0, null, null), DDD, ingredients("D", "DIAMOND"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("usableShapesMasterAccepts")
    @DisplayName("every recipe shape the 6.2 binder read binds to the recipe it bound, and normalises once")
    void everyUsableShapeMasterAcceptsBindsTheSameRecipe(String description, Map<String, Object> plain,
                                                         RecipeConfig.RecipeDefinition expected)
            throws ConversionException {
        RecipeConfig.RecipeDefinition bound = fromPlain(plain);

        assertThat(bound).isEqualTo(expected);
        assertThat(fromPlain(toPlain(bound))).as("(c) normalisation is stable").isEqualTo(bound);
    }

    /**
     * The 6.2 binder turned an empty shape, empty ingredients and an output with no material into null,
     * so that {@code RecipeService}'s guard saw them. The converter keeps them as written (so they
     * round-trip) and the guard reads "absent or empty" itself; {@code RecipeYamlLoadTest$RequiredKeyAbsent}
     * pins that the operator still reads {@code Invalid recipe definition for: <name>} for every one.
     */
    @Test
    @DisplayName("the shapes the 6.2 binder emptied into null are kept as written")
    void emptiedShapesAreKeptAsWritten() throws ConversionException {
        assertThat(fromPlain(map("output", map("material", "DIAMOND"), "shape", new ArrayList<>(),
                "ingredients", map("D", "DIAMOND"))).getShape()).isEmpty();
        assertThat(fromPlain(map("output", map("material", "DIAMOND"), "shape", DDD,
                "ingredients", map())).getIngredients()).isEmpty();
        RecipeConfig.RecipeDefinition noMaterial = fromPlain(map("output", map("amount", 2, "name", "&bNameless"),
                "shape", DDD, "ingredients", map("D", "DIAMOND")));
        assertThat(noMaterial.getOutput()).isEqualTo(output(null, 2, "&bNameless", null));
        assertThat(fromPlain(map())).isEqualTo(definition(null, null, null));
    }

    // --- a value that cannot be read as a recipe is kept as written --------------------------

    static Stream<Arguments> unreadableValues() {
        return Stream.of(
                Arguments.of("a scalar where a mapping belongs", "hello"),
                Arguments.of("a number where a mapping belongs", 7),
                Arguments.of("a list where a mapping belongs", Arrays.asList("one", "two")),
                Arguments.of("output that is not a mapping", map("output", "DIAMOND")),
                Arguments.of("output.amount that is not a number", map("output", map("material", "DIAMOND", "amount", "lots"))),
                Arguments.of("output.amount with a fractional part", map("output", map("material", "DIAMOND", "amount", 2.5))),
                Arguments.of("output.amount too large for a stack", map("output", map("material", "DIAMOND", "amount", 3000000000L))),
                Arguments.of("output.lore that is not a list", map("output", map("material", "DIAMOND", "lore", "a line"))),
                Arguments.of("shape that is not a list", map("output", map("material", "DIAMOND"), "shape", "DDD")),
                Arguments.of("a shape row that is empty", map("shape", Arrays.asList("DDD", null))),
                Arguments.of("ingredients that is not a mapping", map("ingredients", "D")),
                Arguments.of("output.name as a mapping", map("output", map("material", "DIAMOND", "name", map("text", "Blade")))),
                Arguments.of("an ingredient whose material is a list", map("ingredients", map("D", Arrays.asList("x")))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unreadableValues")
    @DisplayName("a value that cannot be read as a recipe binds without dropping anything, and writes back unchanged")
    void unreadableValueIsKeptAsWritten(String description, Object plain) throws ConversionException {
        RecipeConfig.RecipeDefinition bound = fromPlain(plain);

        assertThat(bound).as("kept in the map, so RecipeService can name it; never null").isNotNull();
        assertThat(toPlain(bound)).as("written back exactly as the operator wrote it").isEqualTo(plain);
        assertThat(fromPlain(toPlain(bound))).as("(a) on what it bound").isEqualTo(bound);
    }
}
