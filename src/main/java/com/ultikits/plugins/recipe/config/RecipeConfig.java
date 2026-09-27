package com.ultikits.plugins.recipe.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
         * independently checkable against the measurement named beside it (taken for
         * UltiKits/UltiRecipe#16).
         * <pre>
         * value written in recipes.yml   binds to   the operator reads        measured by
         * ----------------------------   ---------  -----------------------   -----------------------
         * ingredients: absent or empty   null       Invalid recipe            RecipeYamlLoadTest
         * shape:       absent or empty   null       definition for: &lt;name&gt;    $RequiredKeyAbsent
         * output:      absent, {}, or               (all three cases)         (each case has its
         *              no material       null                                  own test there)
         *
         * material: ""                   itself     Invalid output for        emptyMaterialString-
         *                                           recipe: &lt;name&gt; -         NamesTheDeclared-
         *                                           output.material: must    Constraint
         *                                           not be empty
         *
         * material: NOT_A_MATERIAL       itself     Invalid output material   unusableMaterialKeeps-
         *                                           for recipe: &lt;name&gt;       TheSpecificMessage
         * </pre>
         * The second group is not folded into the null guard: each of those shapes has a message
         * of its own that names the offending sub-key. The two used to share the second message,
         * which meant a blank field and a typo read identically in the log; the empty shape moved
         * onto the {@code @NotEmpty} the field declares when UltiKits/UltiRecipe#14 made that
         * annotation actually run (see {@link OutputItem#findConstraintViolation()}). The
         * unusable shape did not move, and its test is the control that says so.
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
         * That third row is the measurement taken for UltiKits/UltiRecipe#16, and
         * UltiKits/UltiRecipe#14 has since moved what it would report: with the null binding removed,
         * an output carrying no material now meets {@code @NotEmpty} first and reports {@code Invalid
         * output for recipe: &lt;name&gt; - output.material: must not be empty} instead of the NPE
         * the raw {@code Material.matchMaterial(null)} call produced. The row is left as it was
         * measured, with this note, rather than rewritten to a number nobody re-ran.
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
            // rows do not cover - the output constraints are read before the shape check
            // (RecipeService:139 and :158 respectively), so only the material message appears:
            //
            //   hand-built definition          reports
            //   ----------------------------   --------------------------------------------
            //   shape left at its empty        Recipe shape must have exactly 3 rows for: <name>
            //     default, material set
            //   material null, shape well      Invalid output for recipe: <name> -
            //     formed                         output.material: must not be empty
            //   both                           Invalid output for recipe: <name> -
            //                                    output.material: must not be empty
            //                                    (the shape check is never reached)
            //
            // The two material rows read "Failed to register recipe: <name> - Name cannot be
            // null" before UltiKits/UltiRecipe#14: a null material used to reach
            // Material.matchMaterial(null) and surface that call's own NPE message through
            // initRecipes' catch. The declared @NotEmpty now refuses it first, by name.
            //
            //   the same values written in a config file, for any of those three rows:
            //                                  Invalid recipe definition for: <name>
            //
            // A property of this early return, not a defect in any of those messages.
            if (value instanceof RecipeDefinition) {
                return (RecipeDefinition) value;
            }
            if (!(value instanceof Map)) {
                throw RecipeProblem.of(RecipeProblem.Kind.ENTRY_EXPECTED_MAPPING, RecipeProblem.found(value))
                        .toException();
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
                throw RecipeProblem.of(RecipeProblem.Kind.EXPECTED_MAPPING, "output", RecipeProblem.found(value))
                        .toException();
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
                    throw RecipeProblem.of(RecipeProblem.Kind.EXPECTED_NUMBER, "output.amount",
                            RecipeProblem.found(amount)).toException();
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

        /**
         * Describes the first declared configuration constraint this output item violates, or
         * {@code null} when it violates none.
         * <p>
         * The framework's own {@code AbstractConfigEntity#validateFields()} reads the
         * {@code annotations.config} constraints, but only off the {@code @ConfigEntry} fields
         * the entity itself declares - here that is {@code RecipeConfig.enabled} and
         * {@code RecipeConfig.recipes}, neither of which carries one. It never recurses into a
         * map value's object graph, so the {@code @NotEmpty} on {@code material} and the
         * {@code @Range} on {@code amount} were evaluated by nothing at all
         * (UltiKits/UltiRecipe#14). This method reads them, with the framework's own semantics:
         * {@code @Range} is inclusive at both ends and ignores a non-numeric value, and
         * {@code @NotEmpty} treats null and whitespace-only alike.
         * <p>
         * It walks the declared fields rather than naming the two, so adding a third field with
         * either annotation needs no change here. The other two annotations in that package,
         * {@code @Size} and {@code @Pattern}, are not read, because no field on this class
         * declares one - and that is not left to a promise: {@code RecipeYamlLoadTest}'s
         * {@code onlyTheConstraintsThisModuleReadsAreDeclared} fails the moment a field declares
         * a constraint this method does not read, which is the same silent-no-op defect one
         * level up.
         * <p>
         * Reporting, not refusing, is the whole contract: the caller
         * ({@code RecipeService#registerRecipe}) skips that one recipe with a warning naming the
         * sub-key, the offending value and the bounds, and every other entry in the file still
         * registers. Nothing is clamped - an operator who wrote {@code amount: 100} gets told so,
         * rather than quietly receiving 64.
         *
         * @return the violation, naming the operator's own {@code output.<sub-key>} path, or
         *         {@code null} if every declared constraint is satisfied
         */
        @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
        // Reads this class's own private fields reflectively, which is the point: naming them
        // one by one is what lets a later field be added with a constraint that nothing reads.
        public RecipeProblem findConstraintViolation() {
            for (Field field : OutputItem.class.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object value;
                try {
                    value = field.get(this);
                } catch (IllegalAccessException e) {
                    throw new IllegalArgumentException(
                            "output." + field.getName() + ": cannot be read for validation", e);
                }
                Range range = field.getAnnotation(Range.class);
                if (range != null && value instanceof Number) {
                    double number = ((Number) value).doubleValue();
                    if (number < range.min() || number > range.max()) {
                        return RecipeProblem.of(RecipeProblem.Kind.OUT_OF_RANGE, "output." + field.getName(), value,
                                describeBound(range.min()), describeBound(range.max()));
                    }
                }
                if (field.getAnnotation(NotEmpty.class) != null
                        && (value == null || value.toString().trim().isEmpty())) {
                    return RecipeProblem.of(RecipeProblem.Kind.MUST_NOT_BE_EMPTY, "output." + field.getName());
                }
            }
            return null;
        }

        /**
         * Renders a {@code @Range} bound the way the operator wrote it in the annotation.
         * {@code min()} and {@code max()} are declared {@code double}, so a stack-size bound of
         * {@code 1} prints as {@code 1.0} unless it is trimmed - and the refusal message is read
         * by someone comparing it against an integer they typed into a yml file.
         *
         * @param value the bound
         * @return the bound without a redundant fractional part
         */
        private static String describeBound(double value) {
            if (value == Math.rint(value) && !Double.isInfinite(value)
                    && Math.abs(value) < (double) Long.MAX_VALUE) {
                return Long.toString((long) value);
            }
            return Double.toString(value);
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
            throw RecipeProblem.of(RecipeProblem.Kind.EXPECTED_LIST, field, RecipeProblem.found(value)).toException();
        }
        List<String> result = new ArrayList<>();
        for (Object element : (List<?>) value) {
            if (element == null) {
                throw RecipeProblem.of(RecipeProblem.Kind.EMPTY_ENTRY, field).toException();
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
            throw RecipeProblem.of(RecipeProblem.Kind.EXPECTED_MAPPING, field, RecipeProblem.found(value)).toException();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (entry.getValue() == null) {
                throw RecipeProblem.of(RecipeProblem.Kind.NO_VALUE, field + "." + entry.getKey()).toException();
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
     * (measured). A compound value cannot be read as text, so it is refused here like any other
     * structural mismatch. Scalars are unaffected, which is why {@code material: ""} and {@code
     * material: NOT_A_MATERIAL} still bind, and are still judged afterwards rather than here:
     * the empty one by the {@code @NotEmpty} the field declares ({@link
     * OutputItem#findConstraintViolation()}, UltiKits/UltiRecipe#14), the unusable one by
     * {@code Material.matchMaterial} as before.
     *
     * @param field the sub-key being bound, used in the failure message
     * @param value the parsed configuration value, never {@code null}
     * @return the value as text
     * @throws IllegalArgumentException if the value is a mapping or a list
     */
    private static String asText(String field, Object value) {
        if (value instanceof Map || value instanceof List) {
            throw RecipeProblem.of(RecipeProblem.Kind.EXPECTED_TEXT, field, RecipeProblem.found(value)).toException();
        }
        return String.valueOf(value);
    }
}
