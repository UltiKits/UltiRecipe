package com.ultikits.plugins.recipe.config;

import java.util.Arrays;

/**
 * Why one entry in {@code recipes.yml} cannot be used, as a language key plus its arguments.
 * <p>
 * The operator reads this reason in the server's language: {@code RecipeService} renders it through
 * the module's catalogue and prints it after the recipe's name. An argument that is itself a
 * {@code RecipeProblem} (what was found where something else was expected) is rendered the same way;
 * any other argument - a YAML path, the offending value, a bound - is printed as written.
 * <p>
 * Each kind looks its text up through one literal {@code i18n("...")} call in {@link #template(Text)},
 * so the language guard checks every one of those keys against both catalogues.
 */
public final class RecipeProblem {

    /**
     * The module's catalogue lookup. In production it forwards to {@code UltiToolsPlugin#i18n}.
     */
    public interface Text {
        /**
         * @param key a catalogue key
         * @return the key's text in the server's language
         */
        String i18n(String key);
    }

    /** Every reason, and every description of a found value, that binding a recipe can report. */
    enum Kind {
        ENTRY_EXPECTED_MAPPING, EXPECTED_MAPPING, EXPECTED_NUMBER, EXPECTED_LIST, EXPECTED_TEXT,
        EMPTY_ENTRY, NO_VALUE, OUT_OF_RANGE, MUST_NOT_BE_EMPTY,
        FOUND_NOTHING, FOUND_MAPPING, FOUND_LIST, FOUND_NUMBER, FOUND_BOOLEAN, FOUND_TEXT
    }

    private final Kind kind;
    private final Object[] args;

    private RecipeProblem(Kind kind, Object[] args) {
        this.kind = kind;
        this.args = args.clone();
    }

    static RecipeProblem of(Kind kind, Object... args) {
        return new RecipeProblem(kind, args);
    }

    /** Describes a value found where a different shape was expected. */
    static RecipeProblem found(Object value) {
        if (value == null) {
            return of(Kind.FOUND_NOTHING);
        }
        if (value instanceof java.util.Map) {
            return of(Kind.FOUND_MAPPING);
        }
        if (value instanceof java.util.List) {
            return of(Kind.FOUND_LIST);
        }
        if (value instanceof Number) {
            return of(Kind.FOUND_NUMBER);
        }
        if (value instanceof Boolean) {
            return of(Kind.FOUND_BOOLEAN);
        }
        return of(Kind.FOUND_TEXT, value);
    }

    /** This reason as the exception the binder throws. */
    RecipeBindingException toException() {
        return new RecipeBindingException(this);
    }

    /**
     * Renders this reason in the language {@code text} looks up.
     *
     * @param text the catalogue lookup
     * @return the reason, arguments filled in
     */
    public String render(Text text) {
        Object[] rendered = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            rendered[i] = args[i] instanceof RecipeProblem ? ((RecipeProblem) args[i]).render(text) : args[i];
        }
        return String.format(template(text), rendered);
    }

    private String template(Text text) {
        switch (kind) {
            case ENTRY_EXPECTED_MAPPING:
                return text.i18n("recipe.reason.entry_expected_mapping");
            case EXPECTED_MAPPING:
                return text.i18n("recipe.reason.expected_mapping");
            case EXPECTED_NUMBER:
                return text.i18n("recipe.reason.expected_number");
            case EXPECTED_LIST:
                return text.i18n("recipe.reason.expected_list");
            case EXPECTED_TEXT:
                return text.i18n("recipe.reason.expected_text");
            case EMPTY_ENTRY:
                return text.i18n("recipe.reason.empty_entry");
            case NO_VALUE:
                return text.i18n("recipe.reason.no_value");
            case OUT_OF_RANGE:
                return text.i18n("recipe.reason.out_of_range");
            case MUST_NOT_BE_EMPTY:
                return text.i18n("recipe.reason.must_not_be_empty");
            case FOUND_NOTHING:
                return text.i18n("recipe.found.nothing");
            case FOUND_MAPPING:
                return text.i18n("recipe.found.mapping");
            case FOUND_LIST:
                return text.i18n("recipe.found.list");
            case FOUND_NUMBER:
                return text.i18n("recipe.found.number");
            case FOUND_BOOLEAN:
                return text.i18n("recipe.found.boolean");
            case FOUND_TEXT:
                return text.i18n("recipe.found.text");
            default:
                throw new IllegalStateException("no text for " + kind);
        }
    }

    /** Developer-facing form: the kind and its raw arguments. */
    @Override
    public String toString() {
        return kind + Arrays.toString(args);
    }

    /**
     * Thrown when a {@code recipes.yml} value cannot be bound; carries the reason for the operator.
     */
    public static final class RecipeBindingException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        private final transient RecipeProblem problem;

        RecipeBindingException(RecipeProblem problem) {
            super(problem.toString());
            this.problem = problem;
        }

        /** @return why the value cannot be bound */
        public RecipeProblem getProblem() {
            return problem;
        }
    }
}
