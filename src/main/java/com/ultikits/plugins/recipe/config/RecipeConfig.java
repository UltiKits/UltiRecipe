package com.ultikits.plugins.recipe.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.annotations.config.Range;

import lombok.EqualsAndHashCode;
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
     * <p>
     * Bound from and written to {@code recipes.<name>} by {@link RecipeDefinitionConverter}, which the
     * framework discovers in this module's scan packages (UltiKits/UltiRecipe#32). The converter keeps
     * every part as written: an absent part is {@code null}, an empty list or mapping stays empty, and an
     * {@code output} block with no {@code material} is an {@link OutputItem} whose material is
     * {@code null}. Whether the result is a usable recipe is judged in one place,
     * {@code RecipeService#registerRecipe}, which refuses an absent or empty part with
     * {@code Invalid recipe definition for: <name>}. A value that cannot be read as a recipe at all is an
     * {@link UnreadableRecipe}.
     * <p>
     * Equality is by value, as the framework's round-trip contract requires: two definitions are equal
     * when their output, shape and ingredients are equal, and a definition is never equal to an
     * {@link UnreadableRecipe}.
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

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || other.getClass() != getClass()) {
                return false;
            }
            RecipeDefinition that = (RecipeDefinition) other;
            return Objects.equals(output, that.output)
                    && Objects.equals(shape, that.shape)
                    && Objects.equals(ingredients, that.ingredients);
        }

        @Override
        public int hashCode() {
            return Objects.hash(output, shape, ingredients);
        }
    }

    /**
     * A {@code recipes.<name>} value that cannot be read as a recipe, kept exactly as the operator wrote
     * it, with the reason (UltiKits/UltiRecipe#32).
     * <p>
     * The framework would otherwise drop such an entry from the map with its own generic warning, which
     * names the key and the raw value but not what is wrong with it. Carrying it instead lets
     * {@code RecipeService} skip it with the line this module has always written, in the server's
     * language - {@code Skipped recipe '<name>': <reason>} - and lets the converter write it back
     * unchanged, so nothing the operator wrote is lost if the framework ever writes the map.
     * <p>
     * Equal to another {@code UnreadableRecipe} holding the same value as written.
     */
    public static final class UnreadableRecipe extends RecipeDefinition {

        private final Object written;
        private final transient RecipeProblem problem;

        UnreadableRecipe(Object written, RecipeProblem problem) {
            this.written = RecipeDefinitionConverter.copyPlain(written);
            this.problem = problem;
            setShape(null);
            setIngredients(null);
        }

        /** @return the value as the operator wrote it, as a fresh copy */
        public Object getWritten() {
            return RecipeDefinitionConverter.copyPlain(written);
        }

        /** @return why the value cannot be read as a recipe */
        public RecipeProblem getProblem() {
            return problem;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof UnreadableRecipe && Objects.equals(written, ((UnreadableRecipe) other).written);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(written);
        }
    }

    /**
     * Output item configuration.
     */
    @Getter
    @Setter
    @EqualsAndHashCode
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
        static String describeBound(double value) {
            if (value == Math.rint(value) && !Double.isInfinite(value)
                    && Math.abs(value) < (double) Long.MAX_VALUE) {
                return Long.toString((long) value);
            }
            return Double.toString(value);
        }
    }
}
