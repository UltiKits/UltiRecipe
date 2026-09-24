package com.ultikits.plugins.recipe.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every kind of recipe-binding reason has its own text in both catalogues, and the catalogues hold no
 * reason text that no kind produces.
 * <p>
 * The reasons are looked up by literal keys, which language guard 1 checks one by one; this test adds
 * the other direction, that no kind falls through to a missing case. It reaches {@code RecipeProblem}
 * by name so that it still compiles, and fails, on a tree without it.
 */
@DisplayName("Recipe-binding reasons: one catalogue text per kind")
class RecipeProblemKeysTest {

    private static final String PROBLEM = "com.ultikits.plugins.recipe.config.RecipeProblem";

    @Test
    @DisplayName("each kind renders through exactly its own key, and the keys are exactly the catalogue's reason keys")
    void everyKindHasItsOwnKey() throws Exception {
        Class<?> problem = Class.forName(PROBLEM);
        Class<?> kind = Class.forName(PROBLEM + "$Kind");
        Class<?> text = Class.forName(PROBLEM + "$Text");
        Method of = problem.getDeclaredMethod("of", kind, Object[].class);
        of.setAccessible(true); // NOPMD - package-private factory, reached from the test package
        Method render = problem.getMethod("render", text);
        final Set<String> asked = new TreeSet<>();
        Object lookup = Proxy.newProxyInstance(text.getClassLoader(), new Class<?>[]{text}, (proxy, method, args) -> {
            asked.add((String) args[0]);
            return (String) args[0];
        });
        Set<String> rendered = new TreeSet<>();
        for (Object k : kind.getEnumConstants()) {
            asked.clear();
            Object p = of.invoke(null, k, new Object[]{new Object[]{"a", "b", "c", "d"}});
            rendered.add((String) render.invoke(p, lookup));
            assertThat(asked).as("keys looked up for " + k).hasSize(1);
        }
        assertThat(rendered).hasSize(kind.getEnumConstants().length);
        for (String code : new String[]{"en", "zh"}) {
            Set<String> reasonKeys = new TreeSet<>();
            for (Map.Entry<String, String> e : CatalogueText.entries(code).entrySet()) {
                if (e.getKey().startsWith("recipe.reason.") || e.getKey().startsWith("recipe.found.")) {
                    reasonKeys.add(e.getKey());
                }
            }
            assertThat(reasonKeys).as("lang/" + code + ".json reason keys").isEqualTo(rendered);
        }
    }
}
