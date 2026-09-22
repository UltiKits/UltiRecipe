package com.ultikits.plugins.recipe.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.annotations.config.Range;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration entity for custom recipes.
 * <p>
 * Recipe format example:
 * <pre>
 * recipes:
 *   custom_sword:
 *     output:
 *       material: DIAMOND_SWORD
 *       amount: 1
 *       name: "&b&l神圣之剑"
 *       lore:
 *         - "&7一把强大的剑"
 *     shape:
 *       - " D "
 *       - " D "
 *       - " S "
 *     ingredients:
 *       D: DIAMOND
 *       S: STICK
 * </pre>
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Getter
@Setter
@ConfigEntity("config/recipes.yml")
public class RecipeConfig extends AbstractConfigEntity {

    @ConfigEntry(path = "enabled", comment = "是否启用自定义配方功能")
    private boolean enabled = true;

    @ConfigEntry(path = "recipes", comment = "自定义配方列表")
    private Map<String, RecipeDefinition> recipes = new HashMap<>();

    public RecipeConfig(String configFilePath) {
        super(configFilePath);
    }

    /**
     * Recipe definition for a single custom recipe.
     */
    @Getter
    @Setter
    public static class RecipeDefinition {
        /**
         * Output item configuration.
         */
        private OutputItem output;

        /**
         * Recipe shape (3 rows of 3 characters each).
         * Example: [" D ", " D ", " S "]
         */
        private List<String> shape = new ArrayList<>();

        /**
         * Ingredient mappings (character to material).
         * Example: {D: DIAMOND, S: STICK}
         */
        private Map<String, String> ingredients = new HashMap<>();

        /**
         * Binds one parsed {@code recipes.<name>} value onto a {@code RecipeDefinition}.
         * <p>
         * The framework binds the {@code recipes} key through
         * {@code DefaultConfigParser#parse}, which turns every nested YAML mapping into a
         * {@code LinkedHashMap} and never into the declared value type. The declared
         * {@code Map<String, RecipeDefinition>} field therefore holds maps at runtime, and
         * reading a value back as a {@code RecipeDefinition} throws {@code ClassCastException}
         * (UltiKits/UltiRecipe#16). This method binds the value explicitly.
         * <p>
         * Only the structure is bound here. Whether the resulting recipe is usable - the
         * output material resolves, the shape has the row count the service requires, an
         * ingredient key is one character - stays where it already lives, in
         * {@code RecipeService#registerRecipe}.
         * <p>
         * All three of {@code output}, {@code shape} and {@code ingredients} bind to
         * {@code null} when the operator did not supply them, or supplied them with nothing
         * usable under them. The field's own non-null default is not used for those cases, so
         * that {@code RecipeService#registerRecipe}'s existing
         * {@code output == null || shape == null || ingredients == null} guard still sees them.
         * "Nothing usable" means an empty mapping or list, and for {@code output} it means a
         * block carrying no {@code material}. Note that Bukkit DROPS a mapping key whose value
         * is empty, so {@code ingredients:} with only {@code D:} under it arrives here as an
         * empty mapping; "empty" and "absent" therefore bind alike. The decision about what
         * to do with that null is not made here.
         * <p>
         * Which sub-key values bind to null, and which do not. One row per case, each
         * independently checkable against the measurement named beside it; the artefacts are in
         * {@code revert-proofs/w1/UltiRecipe-16-scripts/}.
         * <pre>
         * value written in recipes.yml   binds to   the operator reads        measured by
         * ----------------------------   ---------  -----------------------   -----------------------
         * ingredients: absent or empty   null       Invalid recipe            RecipeYamlLoadTest
         * shape:       absent or empty   null       definition for: &lt;name&gt;    $RequiredKeyAbsent
         * output:      absent, {}, or               (all three cases)         (each case has its
         *              no material       null                                  own test there)
         *
         * material: ""                   itself     Invalid output material   emptyMaterialString-
         * material: NOT_A_MATERIAL       itself     for recipe: &lt;name&gt;        KeepsTheSpecificMessage,
         *                                                                     unusableMaterialKeeps-
         *                                                                     TheSpecificMessage
         * </pre>
         * The second group is not folded into the null guard: those two shapes have a message of
         * their own that names the offending sub-key.
         * <p>
         * What each folded case would report without the null binding. One row per case, each
         * measured; these are the mutation logs of the three bindings above.
         * <pre>
         * case                      without the null binding                        measured by
         * -----------------------   ---------------------------------------------   -------------------
         * ingredients absent/empty  Failed to register recipe: &lt;name&gt; -             paper-1.21.11-
         *                           Index 0 out of bounds for length 0              probe.out, case B
         * shape absent/empty        Recipe shape must have exactly 3 rows            MUTATION-shape-
         *                           for: &lt;name&gt;                                     absent-keeps-the-
         *                                                                           default
         * output empty/no material  Failed to register recipe: &lt;name&gt; -    MUTATION-output-
         *                           Name cannot be null                     with-no-material-kept
         * </pre>
         * <p>
         * Why the {@code ingredients} row of that second table says what it says. Measured
         * against Paper 1.21.11 itself, bootstrapped:
         * {@code CraftShapedRecipe#replaceUndefinedIngredientsWithEmpty} replaces every shape
         * character that has no ingredient with a space, and
         * {@code CraftServer#addRecipe} carries no exception table around
         * {@code addToCraftingManager}. One row per case:
         * <pre>
         * shape and ingredients         becomes      ShapedRecipePattern.of        probe row
         * ---------------------------   ----------   ---------------------------   ---------
         * "DSD" x3, S undefined         "D D" x3     accepts, width 3, height 3    case A
         * "DDD" x3, all undefined       "   " x3     throws ArrayIndexOutOf-       case B
         *                                            BoundsException: Index 0
         *                                            out of bounds for length 0
         * "DDD" x3, D defined           not run      accepts, width 3, height 3    control
         *                                (fed direct)
         * </pre>
         * MockBukkit cannot observe any of that: its {@code ServerMock#addRecipe} stores the
         * object and never reaches that code. Two earlier revisions of this paragraph were wrong
         * here, for different reasons - {@code 485d016} stated a MockBukkit observation as server
         * behaviour, and {@code c1c0cae} applied a real Paper measurement to a case it had not
         * been taken on. Neither is a count of anything; both are checkable with
         * {@code git show <sha>:src/main/java/.../RecipeConfig.java}.
         *
         * @param value the parsed configuration value, or an already-built definition
         * @return the bound definition
         * @throws IllegalArgumentException if {@code value} cannot be bound, with a message
         *                                  naming the offending sub-key and what was found
         */
        public static RecipeDefinition fromConfigValue(Object value) {
            // Unreachable from the framework's own binder, which never produces a typed value:
            // DefaultConfigParser#parse returns a LinkedHashMap for a section, a List for a
            // sequence and the value itself for a scalar. It is kept because a caller that
            // already holds a definition should not be refused for holding the right type, and
            // because module code and tests construct definitions directly. Do not read it as
            // evidence that the framework sometimes binds the declared type - it does not.
            //
            // A definition handed in this way skips the binding below, so the same operator
            // situation reports differently depending on which side it came from. One row per
            // case; the third row is what happens when BOTH fields deviate, which the first two
            // rows do not cover - createOutputItem runs before the shape check
            // (RecipeService:132 and :144 respectively), so only the material message appears:
            //
            //   hand-built definition          reports
            //   ----------------------------   --------------------------------------------
            //   shape left at its empty        Recipe shape must have exactly 3 rows for: <name>
            //     default, material set
            //   material null, shape well      Failed to register recipe: <name> -
            //     formed                         Name cannot be null
            //   both                           Failed to register recipe: <name> -
            //                                    Name cannot be null   (the shape check is
            //                                    never reached)
            //
            //   the same values written in a config file, for any of those three rows:
            //                                  Invalid recipe definition for: <name>
            //
            // A property of this early return, not a defect in any of those messages.
            if (value instanceof RecipeDefinition) {
                return (RecipeDefinition) value;
            }
            if (!(value instanceof Map)) {
                throw new IllegalArgumentException(
                        "expected a mapping, found " + describeType(value));
            }
            Map<?, ?> map = (Map<?, ?>) value;
            RecipeDefinition definition = new RecipeDefinition();
            Object output = map.get("output");
            OutputItem boundOutput = output == null ? null : OutputItem.fromConfigValue(output);
            definition.setOutput(
                    boundOutput == null || boundOutput.getMaterial() == null ? null : boundOutput);
            Object shape = map.get("shape");
            List<String> boundShape = shape == null ? null : toStringList("shape", shape);
            definition.setShape(boundShape == null || boundShape.isEmpty() ? null : boundShape);
            Object ingredients = map.get("ingredients");
            Map<String, String> boundIngredients =
                    ingredients == null ? null : toStringMap("ingredients", ingredients);
            definition.setIngredients(
                    boundIngredients == null || boundIngredients.isEmpty() ? null : boundIngredients);
            return definition;
        }
    }

    /**
     * Output item configuration.
     */
    @Getter
    @Setter
    public static class OutputItem {
        /**
         * Material name (e.g., DIAMOND_SWORD).
         */
        @NotEmpty
        private String material;

        /**
         * Output amount.
         */
        @Range(min = 1, max = 64)
        private int amount = 1;

        /**
         * Custom display name (supports color codes with &).
         */
        private String name;

        /**
         * Custom lore lines (supports color codes with &).
         */
        private List<String> lore;

        /**
         * Binds one parsed {@code recipes.<name>.output} value onto an {@code OutputItem}.
         * See {@link RecipeDefinition#fromConfigValue(Object)} for why this binding is
         * explicit.
         *
         * @param value the parsed configuration value, or an already-built output item
         * @return the bound output item
         * @throws IllegalArgumentException if {@code value} cannot be bound
         */
        public static OutputItem fromConfigValue(Object value) {
            if (value instanceof OutputItem) {
                return (OutputItem) value;
            }
            if (!(value instanceof Map)) {
                throw new IllegalArgumentException(
                        "output: expected a mapping, found " + describeType(value));
            }
            Map<?, ?> map = (Map<?, ?>) value;
            OutputItem item = new OutputItem();
            Object material = map.get("material");
            if (material != null) {
                item.setMaterial(asText("output.material", material));
            }
            // Deliberate asymmetry, recorded so the next reader does not take one half for the
            // house style: material and name accept any SCALAR (asText refuses a mapping or a
            // list), because a material name is text and Material.matchMaterial judges it;
            // amount refuses a non-number outright, because silently coercing a stack size is
            // how an operator ends up with a quantity they did not write. Two consequences
            // follow, both accepted: amount: "1" quoted as text skips the whole entry, and
            // amount: 2.5 is truncated to 2 by intValue() without a warning.
            Object amount = map.get("amount");
            if (amount != null) {
                if (!(amount instanceof Number)) {
                    throw new IllegalArgumentException(
                            "output.amount: expected a number, found " + describeType(amount));
                }
                item.setAmount(((Number) amount).intValue());
            }
            Object name = map.get("name");
            if (name != null) {
                item.setName(asText("output.name", name));
            }
            Object lore = map.get("lore");
            if (lore != null) {
                item.setLore(toStringList("output.lore", lore));
            }
            return item;
        }
    }

    /**
     * Binds a parsed list value onto a list of strings.
     *
     * @param field the sub-key being bound, used in the failure message
     * @param value the parsed configuration value
     * @return the bound list
     * @throws IllegalArgumentException if the value is not a list, or holds a null element
     */
    private static List<String> toStringList(String field, Object value) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(
                    field + ": expected a list, found " + describeType(value));
        }
        List<String> result = new ArrayList<>();
        for (Object element : (List<?>) value) {
            if (element == null) {
                throw new IllegalArgumentException(field + ": contains an empty entry");
            }
            result.add(asText(field, element));
        }
        return result;
    }

    /**
     * Binds a parsed mapping value onto a map of strings, preserving the file's own order.
     *
     * @param field the sub-key being bound, used in the failure message
     * @param value the parsed configuration value
     * @return the bound map
     * @throws IllegalArgumentException if the value is not a mapping, or holds a null value
     */
    private static Map<String, String> toStringMap(String field, Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(
                    field + ": expected a mapping, found " + describeType(value));
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (entry.getValue() == null) {
                throw new IllegalArgumentException(
                        field + "." + entry.getKey() + ": has no value");
            }
            result.put(String.valueOf(entry.getKey()),
                       asText(field + "." + entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /**
     * Binds a parsed scalar onto text.
     * <p>
     * Every caller of this method used to call {@code String.valueOf} directly, which renders a
     * {@code Map} or {@code List} as Java text - an indentation mistake under {@code name:}
     * became the display name {@code {text=Blade}} and the recipe registered with no warning
     * (Codex P2 on pull request #22, measured). A compound value cannot be read as text, so it
     * is refused here like any other structural mismatch. Scalars are unaffected, which is why
     * {@code material: ""} and {@code material: NOT_A_MATERIAL} still reach
     * {@code Material.matchMaterial} and still report
     * {@code Invalid output material for recipe: <name>}.
     *
     * @param field the sub-key being bound, used in the failure message
     * @param value the parsed configuration value, never {@code null}
     * @return the value as text
     * @throws IllegalArgumentException if the value is a mapping or a list
     */
    private static String asText(String field, Object value) {
        if (value instanceof Map || value instanceof List) {
            throw new IllegalArgumentException(
                    field + ": expected text, found " + describeType(value));
        }
        return String.valueOf(value);
    }

    /**
     * Names what was found where a different shape was expected, for a binding failure
     * message an operator can act on.
     *
     * @param value the offending value
     * @return a short description of the value's kind
     */
    private static String describeType(Object value) {
        if (value == null) {
            return "nothing";
        }
        if (value instanceof Map) {
            return "a mapping";
        }
        if (value instanceof List) {
            return "a list";
        }
        if (value instanceof Number) {
            return "a number";
        }
        if (value instanceof Boolean) {
            return "a true/false value";
        }
        return "the text '" + value + "'";
    }

    /**
     * Initialize default values after construction.
     * Called by the framework after instantiation.
     */
    public void initDefaults() {
        if (recipes == null || recipes.isEmpty()) {
            recipes = new HashMap<>();
            // Add example recipe
            RecipeDefinition example = new RecipeDefinition();
            
            OutputItem output = new OutputItem();
            output.setMaterial("EGG");
            output.setAmount(1);
            output.setName("&e&l金苹果蛋");
            List<String> lore = new ArrayList<>();
            lore.add("&7由苹果和木头合成的神奇蛋");
            output.setLore(lore);
            example.setOutput(output);
            
            List<String> shape = new ArrayList<>();
            shape.add("xxx");
            shape.add("xyx");
            shape.add("y y");
            example.setShape(shape);
            
            Map<String, String> ingredients = new HashMap<>();
            ingredients.put("x", "APPLE");
            ingredients.put("y", "DARK_OAK_WOOD");
            example.setIngredients(ingredients);
        
            recipes.put("golden_egg", example);
        }
    }
}
