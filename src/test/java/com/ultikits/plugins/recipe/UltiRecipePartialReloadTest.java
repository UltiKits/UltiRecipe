package com.ultikits.plugins.recipe;

import com.ultikits.plugins.recipe.config.RecipeConfig;
import com.ultikits.plugins.recipe.i18n.CatalogueText;
import com.ultikits.plugins.recipe.service.RecipeService;
import com.ultikits.ultitools.abstracts.ReloadReport;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /ul reload UltiRecipe} reports a partial reload when {@code config/recipes.yml} could not be
 * read again (UltiKits/UltiRecipe#30; framework UltiTools-Reborn#529).
 * <p>
 * The framework hands the module's reload hook a fresh {@link ReloadReport} and replies to the sender with
 * the parts the hook recorded, instead of a plain success, when there are any. Each case calls that hook
 * the way the framework does - {@code UltiToolsPlugin#onReload(ReloadReport)}, through reflection, because
 * it is {@code protected} in another package - on a module double whose reload hooks run for real, with a
 * real {@code RecipeService} behind it. Nothing here names code added by the fix, so the same tests run
 * against the sources from before it: there the hook only logged the failure and the report stayed empty.
 * <p>
 * 重新读取 recipes.yml 失败时，{@code /ul reload UltiRecipe} 报告「部分重载」并写明原因，而不是一律回复成功。
 */
@DisplayName("/ul reload UltiRecipe reports a failed re-read as a partial reload (#30)")
class UltiRecipePartialReloadTest {

    private RecipeConfig config;
    private PluginLogger logger;
    private UltiRecipe plugin;

    @BeforeEach
    void setUp() throws Exception {
        UltiRecipeTestHelper.setUp();
        logger = mock(PluginLogger.class);
        config = UltiRecipeTestHelper.createDefaultConfig();
        final SimpleContainer context = mock(SimpleContainer.class);
        plugin = mock(UltiRecipe.class, invocation -> {
            String name = invocation.getMethod().getName();
            if ("onReload".equals(name)) {
                return invocation.callRealMethod();
            }
            if ("getContext".equals(name)) {
                return context;
            }
            if ("getLogger".equals(name)) {
                return logger;
            }
            if ("i18n".equals(name)) {
                return CatalogueText.answer("en").answer(invocation);
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        RecipeService service = new RecipeService();
        UltiRecipeTestHelper.setField(service, "plugin", plugin);
        UltiRecipeTestHelper.setField(service, "config", config);
        UltiRecipeTestHelper.setField(service, "pluginInstance", UltiRecipeTestHelper.getMockJavaPlugin());
        when(context.getBean(RecipeService.class)).thenReturn(service);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiRecipeTestHelper.tearDown();
    }

    /** Calls the module's reload hook as the framework's {@code reloadSelf()} does, with a fresh report. */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // the hook is protected in the framework's package
    private ReloadReport reload() throws Exception {
        ReloadReport report = new ReloadReport();
        Method hook = UltiToolsPlugin.class.getDeclaredMethod("onReload", ReloadReport.class);
        hook.setAccessible(true);
        try {
            hook.invoke(plugin, report);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
        return report;
    }

    private List<String> warnings() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger, atLeast(0)).warn(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("recipes.yml that cannot be read again: the report is partial and names the file and the error")
    void failedReReadIsReportedAsPartial() throws Exception {
        doThrow(new IOException("disk gone")).when(config).reload();

        ReloadReport report = reload();

        assertThat(report.isPartial()).as("the reply is the partial-reload reply, not the plain success").isTrue();
        assertThat(report.getPartialReasons()).containsExactly(
                "config/recipes.yml could not be read again (disk gone); the recipes loaded before stay registered");
        assertThat(warnings()).as("the console still names the error, as before").containsExactly(
                "Could not read config/recipes.yml again, so the recipes loaded before are registered again: disk gone");
    }

    @Test
    @DisplayName("control: a re-read that succeeds leaves the report complete")
    void successfulReReadIsComplete() throws Exception {
        when(config.getRecipes()).thenReturn(Collections.<String, RecipeConfig.RecipeDefinition>emptyMap());

        ReloadReport report = reload();

        assertThat(report.isPartial()).isFalse();
        verify(config).reload();
        verify(logger).info("Recipes reloaded, 0 recipes total");
    }

    @Test
    @DisplayName("the hook reloads the recipes once and logs the count it registered (UltiKits/UltiRecipe#11 Test B)")
    void reloadReRegistersAndLogsTheCount() throws Exception {
        RecipeConfig.RecipeDefinition recipe = new RecipeConfig.RecipeDefinition();
        RecipeConfig.OutputItem output = new RecipeConfig.OutputItem();
        output.setMaterial("DIAMOND");
        recipe.setOutput(output);
        recipe.setShape(java.util.Arrays.asList("DDD", "DDD", "DDD"));
        recipe.setIngredients(Collections.singletonMap("D", "DIAMOND"));
        when(config.getRecipes()).thenReturn(Collections.singletonMap("gem", recipe));

        ReloadReport report = reload();

        assertThat(report.isPartial()).isFalse();
        verify(config).reload();
        verify(logger).info("Registered recipe: gem");
        verify(logger).info("Recipes reloaded, 1 recipes total");
    }

    @Test
    @DisplayName("no RecipeService bean (enabled: false at start-up): the hook does nothing and the report is complete (UltiKits/UltiRecipe#11 Test C)")
    void noServiceBean() throws Exception {
        when(plugin.getContext().getBean(RecipeService.class)).thenReturn(null);

        ReloadReport report = reload();

        assertThat(report.isPartial()).isFalse();
        verify(logger, org.mockito.Mockito.never()).info(org.mockito.ArgumentMatchers.startsWith("Recipes reloaded"));
    }
}
