package com.ultikits.plugins.recipe;

import com.ultikits.plugins.recipe.i18n.CatalogueText;

import com.ultikits.plugins.recipe.service.RecipeService;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("UltiRecipe Main Class Tests")
class UltiRecipeTest {

    @Nested
    @DisplayName("Lifecycle Methods")
    class LifecycleMethods {

        @Test
        @DisplayName("registerSelf should return true and init recipes")
        void registerSelf() throws Exception {
            UltiRecipe plugin = mock(UltiRecipe.class);
            PluginLogger logger = mock(PluginLogger.class);
            SimpleContainer context = mock(SimpleContainer.class);
            RecipeService service = mock(RecipeService.class);

            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.getContext()).thenReturn(context);
            when(context.getBean(RecipeService.class)).thenReturn(service);
            when(service.initRecipes()).thenReturn(5);
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));
            when(plugin.registerSelf()).thenCallRealMethod();

            boolean result = plugin.registerSelf();

            assertThat(result).isTrue();
            verify(service).initRecipes();
            verify(logger).info(contains("已注册"));
            verify(logger).info(contains("已启用"));
        }

        @Test
        @DisplayName("onUnregister should remove recipes exactly once (UltiKits/UltiRecipe#11 Test A)")
        void onUnregisterRemovesRecipes() throws Exception {
            UltiRecipe plugin = mock(UltiRecipe.class);
            PluginLogger logger = mock(PluginLogger.class);
            SimpleContainer context = mock(SimpleContainer.class);
            RecipeService service = mock(RecipeService.class);

            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.getContext()).thenReturn(context);
            when(context.getBean(RecipeService.class)).thenReturn(service);
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));
            doCallRealMethod().when(plugin).onUnregister();

            plugin.onUnregister();

            verify(service, times(1)).removeRecipes();
            verify(logger).info(contains("已禁用"));
        }

        @Test
        @DisplayName("onUnregister should handle null RecipeService bean gracefully (UltiKits/UltiRecipe#11 Test C)")
        void onUnregisterNullService() throws Exception {
            UltiRecipe plugin = mock(UltiRecipe.class);
            PluginLogger logger = mock(PluginLogger.class);
            SimpleContainer context = mock(SimpleContainer.class);

            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.getContext()).thenReturn(context);
            when(context.getBean(RecipeService.class)).thenReturn(null);
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));
            doCallRealMethod().when(plugin).onUnregister();

            // Should not throw exception
            plugin.onUnregister();

            verify(logger).info(contains("已禁用"));
        }

        @Test
        @DisplayName("onReload should reload recipes exactly once and log the returned count (UltiKits/UltiRecipe#11 Test B)")
        void onReloadReloadsRecipesAndLogsCount() throws Exception {
            UltiRecipe plugin = mock(UltiRecipe.class);
            PluginLogger logger = mock(PluginLogger.class);
            SimpleContainer context = mock(SimpleContainer.class);
            RecipeService service = mock(RecipeService.class);

            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.getContext()).thenReturn(context);
            when(context.getBean(RecipeService.class)).thenReturn(service);
            when(service.reloadRecipes()).thenReturn(8);
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));
            doCallRealMethod().when(plugin).onReload();

            plugin.onReload();

            verify(service, times(1)).reloadRecipes();
            verify(logger).info(contains("配方已重载"));
            verify(logger).info(contains("8"));
        }

        @Test
        @DisplayName("onReload should handle null RecipeService bean gracefully (UltiKits/UltiRecipe#11 Test C)")
        void onReloadNullService() throws Exception {
            UltiRecipe plugin = mock(UltiRecipe.class);
            PluginLogger logger = mock(PluginLogger.class);
            SimpleContainer context = mock(SimpleContainer.class);

            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.getContext()).thenReturn(context);
            when(context.getBean(RecipeService.class)).thenReturn(null);
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));
            doCallRealMethod().when(plugin).onReload();

            // Should not throw exception
            plugin.onReload();

            verify(logger, never()).info(contains("配方已重载"));
        }
    }

    @Nested
    @DisplayName("Service Integration")
    class ServiceIntegration {

        @Test
        @DisplayName("registerSelf should handle zero recipes")
        void registerSelfZeroRecipes() throws Exception {
            UltiRecipe plugin = mock(UltiRecipe.class);
            PluginLogger logger = mock(PluginLogger.class);
            SimpleContainer context = mock(SimpleContainer.class);
            RecipeService service = mock(RecipeService.class);

            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.getContext()).thenReturn(context);
            when(context.getBean(RecipeService.class)).thenReturn(service);
            when(service.initRecipes()).thenReturn(0);
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));
            when(plugin.registerSelf()).thenCallRealMethod();

            boolean result = plugin.registerSelf();

            assertThat(result).isTrue();
            verify(logger).info(contains("已注册 0"));
        }

        @Test
        @DisplayName("registerSelf should handle null context gracefully")
        void registerSelfNullContext() throws Exception {
            UltiRecipe plugin = mock(UltiRecipe.class);
            PluginLogger logger = mock(PluginLogger.class);

            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.getContext()).thenReturn(null);
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));
            when(plugin.registerSelf()).thenCallRealMethod();

            // Should throw NPE when trying to get bean from null context
            assertThatThrownBy(() -> plugin.registerSelf())
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
