package com.ultikits.plugins.recipe.config;

import com.ultikits.ultitools.annotations.config.Range;
import com.ultikits.ultitools.config.convert.ConfigConverter;
import com.ultikits.ultitools.config.convert.ConfigConverterFor;
import com.ultikits.ultitools.config.convert.ConversionContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binds one {@code recipes.<name>} value of {@code config/recipes.yml} to a
 * {@link RecipeConfig.RecipeDefinition}, and writes one back (UltiKits/UltiRecipe#32).
 * <p>
 * UltiTools-API 6.3.0 checks every declared configuration field type before it reads a file and refuses
 * a module whose type it cannot store. {@code RecipeConfig.recipes} is a
 * {@code Map<String, RecipeDefinition>}; the framework finds this converter in the module's scan packages
 * ({@code @ConfigConverterFor}) before it constructs the configuration, and binds every value of that map
 * through it. The binding code is the one {@code RecipeDefinition#fromConfigValue} carried while the 6.2
 * framework handed the module raw maps; it reads the structure only, and whether a recipe is usable is
 * still judged in {@code RecipeService#registerRecipe}.
 * <p>
 * The framework's round-trip contract ({@code ConfigConverter}'s javadoc) holds:
 * <ul>
 *   <li>{@link #toPlain} writes the keys a recipe already uses, in the order the module's README writes
 *       them - {@code output} ({@code material}, {@code amount}, {@code name}, {@code lore}),
 *       {@code shape}, {@code ingredients} - and omits a part that is {@code null}. {@code amount} is
 *       always written, so an {@code output} block that left it out reads back with the declared default
 *       of 1 written out: that is the one normalisation, and it is stable.</li>
 *   <li>{@link #fromPlain} keeps every part as written (an empty list stays empty, a {@code null}
 *       ingredient material stays {@code null}), so {@code fromPlain(toPlain(x))} equals {@code x}.</li>
 *   <li>A value that cannot be read as a recipe - a scalar or a list where the recipe's mapping belongs,
 *       a part of the wrong kind, an {@code amount} that is not a whole number or does not fit a stack -
 *       binds to a {@link RecipeConfig.UnreadableRecipe} holding the value exactly as written and the
 *       reason, and is written back exactly as written. It is never thrown: the framework's own warning
 *       for a refused map entry names the key but not the reason, and would drop the entry from the map
 *       ({@code RecipeService} could then no longer name it, and a write of the map would delete it from
 *       the file). {@code RecipeService} skips it with {@code Skipped recipe '<name>': <reason>}, the line
 *       this module has always written for it.</li>
 * </ul>
 * Unknown keys inside a recipe are ignored, as the 6.2 binder ignored them; the framework never writes
 * this map unless something changes it, so a file is not rewritten because of them.
 * <p>
 * A top-level class with a public no-argument constructor, as the framework's discovery requires; it is
 * not an IoC bean and holds no state.
 * <p>
 * 配方表的转换器：框架在构造配置前从本模块扫描包里发现它，用它读写 {@code recipes.<name>}。读不成配方的值
 * 原样保留并附上原因，由 {@code RecipeService} 跳过并点名，写回时一字不改。
 */
@ConfigConverterFor(RecipeConfig.RecipeDefinition.class)
public class RecipeDefinitionConverter implements ConfigConverter<RecipeConfig.RecipeDefinition> {

    /** Required by the framework's converter discovery. */
    public RecipeDefinitionConverter() {
        // No state: every call binds or writes one value on its own.
    }

    @Override
    public Object toPlain(RecipeConfig.RecipeDefinition value, ConversionContext ctx) {
        if (value == null) {
            return null;
        }
        if (value instanceof RecipeConfig.UnreadableRecipe) {
            return ((RecipeConfig.UnreadableRecipe) value).getWritten();
        }
        Map<String, Object> plain = new LinkedHashMap<>();
        if (value.getOutput() != null) {
            plain.put("output", outputToPlain(value.getOutput()));
        }
        if (value.getShape() != null) {
            plain.put("shape", new ArrayList<>(value.getShape()));
        }
        if (value.getIngredients() != null) {
            plain.put("ingredients", new LinkedHashMap<>(value.getIngredients()));
        }
        return plain;
    }

    @Override
    public RecipeConfig.RecipeDefinition fromPlain(Object plain, ConversionContext ctx) {
        if (plain == null) {
            return null;
        }
        try {
            return bind(plain);
        } catch (RecipeProblem.RecipeBindingException e) {
            return new RecipeConfig.UnreadableRecipe(plain, e.getProblem());
        }
    }

    private static Map<String, Object> outputToPlain(RecipeConfig.OutputItem output) {
        Map<String, Object> plain = new LinkedHashMap<>();
        if (output.getMaterial() != null) {
            plain.put("material", output.getMaterial());
        }
        plain.put("amount", output.getAmount());
        if (output.getName() != null) {
            plain.put("name", output.getName());
        }
        if (output.getLore() != null) {
            plain.put("lore", new ArrayList<>(output.getLore()));
        }
        return plain;
    }

    /**
     * Binds one recipe value onto a definition, part by part, in the order the 6.2 binder read them:
     * {@code output}, then {@code shape}, then {@code ingredients}; the first part that cannot be read
     * names the reason.
     */
    private static RecipeConfig.RecipeDefinition bind(Object value) {
        if (!(value instanceof Map)) {
            throw RecipeProblem.of(RecipeProblem.Kind.ENTRY_EXPECTED_MAPPING, RecipeProblem.found(value))
                    .toException();
        }
        Map<?, ?> map = (Map<?, ?>) value;
        RecipeConfig.RecipeDefinition definition = new RecipeConfig.RecipeDefinition();
        Object output = map.get("output");
        definition.setOutput(output == null ? null : bindOutput(output));
        Object shape = map.get("shape");
        definition.setShape(shape == null ? null : toStringList("shape", shape));
        Object ingredients = map.get("ingredients");
        definition.setIngredients(ingredients == null ? null : toIngredientMap(ingredients));
        return definition;
    }

    private static RecipeConfig.OutputItem bindOutput(Object value) {
        if (!(value instanceof Map)) {
            throw RecipeProblem.of(RecipeProblem.Kind.EXPECTED_MAPPING, "output", RecipeProblem.found(value))
                    .toException();
        }
        Map<?, ?> map = (Map<?, ?>) value;
        RecipeConfig.OutputItem item = new RecipeConfig.OutputItem();
        Object material = map.get("material");
        if (material != null) {
            item.setMaterial(asText("output.material", material));
        }
        // Deliberate asymmetry, recorded so the next reader does not take one half for the house
        // style: material and name accept any SCALAR (asText refuses a mapping or a list), because a
        // material name is text and Material.matchMaterial judges it; amount refuses anything but a
        // whole number, because silently coercing a stack size is how an operator ends up with a
        // quantity they did not write: amount: "1" quoted as text skips the whole entry, and so does
        // amount: 2.5, named as written rather than truncated to 2 (UltiKits/UltiRecipe#24,
        // maintainer decision 2026-09-27).
        Object amount = map.get("amount");
        if (amount != null) {
            if (!(amount instanceof Number)) {
                throw RecipeProblem.of(RecipeProblem.Kind.EXPECTED_NUMBER, "output.amount",
                        RecipeProblem.found(amount)).toException();
            }
            item.setAmount(wholeAmount((Number) amount));
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
     * The whole number {@code amount} as written, for {@code output.amount}.
     * <p>
     * A value with a fractional part is refused, quoting the value as written; {@code 2.0} is the whole
     * number 2; a whole number outside the {@code int} range is refused as out of the field's declared
     * range, again as written, since no stack size can hold it (UltiKits/UltiRecipe#24).
     */
    private static int wholeAmount(Number amount) {
        BigDecimal exact;
        try {
            exact = new BigDecimal(amount.toString());
        } catch (NumberFormatException e) {
            // Infinity and NaN have no decimal form, and are no stack size
            throw RecipeProblem.of(RecipeProblem.Kind.NOT_WHOLE_NUMBER, "output.amount", amount).toException();
        }
        if (exact.signum() != 0 && exact.stripTrailingZeros().scale() > 0) {
            throw RecipeProblem.of(RecipeProblem.Kind.NOT_WHOLE_NUMBER, "output.amount", amount).toException();
        }
        if (exact.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0
                || exact.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            Range range;
            try {
                range = RecipeConfig.OutputItem.class.getDeclaredField("amount").getAnnotation(Range.class);
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("OutputItem has no amount field", e);
            }
            throw RecipeProblem.of(RecipeProblem.Kind.OUT_OF_RANGE, "output.amount", amount,
                    RecipeConfig.OutputItem.describeBound(range.min()),
                    RecipeConfig.OutputItem.describeBound(range.max())).toException();
        }
        return exact.intValueExact();
    }

    /**
     * Binds a list value onto a list of strings.
     *
     * @throws RecipeProblem.RecipeBindingException if the value is not a list, or holds a null element
     *         (a list element left empty in YAML)
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
     * Binds the {@code ingredients} mapping onto a map of strings, preserving the file's own order. An
     * ingredient written with no material ({@code D:}) is kept with a {@code null} value, so it
     * round-trips; {@code RecipeService} refuses it, naming it, with the line the 6.2 binder used.
     *
     * @throws RecipeProblem.RecipeBindingException if the value is not a mapping, or a material is a
     *         mapping or a list
     */
    private static Map<String, String> toIngredientMap(Object value) {
        if (!(value instanceof Map)) {
            throw RecipeProblem.of(RecipeProblem.Kind.EXPECTED_MAPPING, "ingredients", RecipeProblem.found(value))
                    .toException();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            String key = String.valueOf(entry.getKey());
            result.put(key, entry.getValue() == null ? null : asText("ingredients." + key, entry.getValue()));
        }
        return result;
    }

    /**
     * Binds a scalar onto text. A mapping or a list cannot be read as text, so it is refused here like any
     * other structural mismatch: {@code String.valueOf} used to render one as Java text, and an
     * indentation mistake under {@code name:} became the display name {@code {text=Blade}}.
     */
    private static String asText(String field, Object value) {
        if (value instanceof Map || value instanceof List) {
            throw RecipeProblem.of(RecipeProblem.Kind.EXPECTED_TEXT, field, RecipeProblem.found(value)).toException();
        }
        return String.valueOf(value);
    }

    /**
     * A deep copy of a plain configuration value: maps and lists are copied, scalars are shared (they are
     * immutable).
     *
     * @param value a plain value
     * @return its copy
     */
    static Object copyPlain(Object value) {
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                copy.put(String.valueOf(entry.getKey()), copyPlain(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>();
            for (Object element : (List<?>) value) {
                copy.add(copyPlain(element));
            }
            return copy;
        }
        return value;
    }
}
