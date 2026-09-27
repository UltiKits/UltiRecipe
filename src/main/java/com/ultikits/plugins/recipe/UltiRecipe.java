package com.ultikits.plugins.recipe;

import com.ultikits.plugins.recipe.service.RecipeService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;

/**
 * UltiRecipe - Custom recipe management plugin for Minecraft servers.
 * <p>
 * Features:
 * - Create custom shaped recipes via YAML configuration
 * - Support for all vanilla materials
 * - Hot reload recipes without server restart
 * - Simple and intuitive configuration format
 * </p>
 * <p>
 * This plugin is part of the UltiKits ecosystem and requires UltiTools-API.
 * </p>
 *
 * @author wisdomme
 * @version 1.0.0
 */
@UltiToolsModule(scanBasePackages = {"com.ultikits.plugins.recipe"})
public class UltiRecipe extends UltiToolsPlugin {

    @Override
    public boolean registerSelf() {
        // Initialize recipe service
        RecipeService recipeService = getContext().getBean(RecipeService.class);
        if (recipeService != null) {
            int count = recipeService.initRecipes();
            getLogger().info(String.format(i18n("recipe.log.registered_count"), count));
        }

        getLogger().info(i18n("recipe.log.enabled"));
        return true;
    }

    @Override
    protected void onUnregister() {
        // Remove all custom recipes
        RecipeService recipeService = getContext().getBean(RecipeService.class);
        if (recipeService != null) {
            recipeService.removeRecipes();
        }

        getLogger().info(i18n("recipe.log.disabled"));
    }

    @Override
    protected void onReload() {
        // Reload recipes
        RecipeService recipeService = getContext().getBean(RecipeService.class);
        if (recipeService != null) {
            int count = recipeService.reloadRecipes();
            getLogger().info(String.format(i18n("recipe.log.reloaded_count"), count));
        }
    }
}
