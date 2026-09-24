package com.ultikits.plugins.recipe.service;

import com.ultikits.plugins.recipe.config.RecipeConfig;
import com.ultikits.plugins.recipe.config.RecipeProblem;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.ConditionalOnConfig;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing custom recipes.
 * <p>
 * Handles recipe registration, removal, and reloading.
 * Only active when enabled in recipes.yml configuration.
 * <p>
 * 仅在 recipes.yml 配置中启用时激活。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Service
@ConditionalOnConfig(value = "config/recipes.yml", path = "enabled")
public class RecipeService {

    @Autowired
    private UltiToolsPlugin plugin;

    @Autowired
    private RecipeConfig config;

    /**
     * Set of registered recipe keys for cleanup.
     */
    private final Set<NamespacedKey> registeredRecipes = new HashSet<>();

    /**
     * Plugin instance for NamespacedKey creation.
     * Package-private for testing.
     */
    Plugin pluginInstance;

    /**
     * The module's catalogue, for rendering a {@link RecipeProblem}: each problem looks its own text up
     * through a literal key, so this lookup only forwards the key it is given.
     */
    private final RecipeProblem.Text text = new RecipeProblem.Text() {
        @Override
        public String i18n(String key) {
            return plugin.i18n(key);
        }
    };

    private PluginLogger getLogger() {
        return plugin.getLogger();
    }

    /** Why a recipe value could not be bound, in the server's language. */
    private String reason(IllegalArgumentException e) {
        return e instanceof RecipeProblem.RecipeBindingException
                ? ((RecipeProblem.RecipeBindingException) e).getProblem().render(text)
                : e.getMessage();
    }

    /**
     * Get the plugin instance for NamespacedKey creation.
     * Uses Bukkit plugin manager lookup in production, can be overridden in tests.
     */
    Plugin getPluginInstance() {
        if (pluginInstance == null) {
            pluginInstance = Bukkit.getPluginManager().getPlugin("UltiTools");
        }
        return pluginInstance;
    }

    /**
     * Initializes all recipes from configuration.
     * Only called when the service is active (enabled in config).
     * <p>
     * 仅在服务激活时调用（配置中启用）。
     *
     * @return the number of recipes registered
     */
    public int initRecipes() {
        // Read through a wildcard view. The declared value type is RecipeDefinition, but the
        // framework's own binder (DefaultConfigParser#parse) fills this map with LinkedHashMaps,
        // so reading a value back AS a RecipeDefinition compiles to a checkcast that throws
        // ClassCastException for every non-empty recipes.yml (UltiKits/UltiRecipe#16). The
        // wildcard keeps every value at Object until it is bound explicitly below.
        Map<String, ?> recipes = config.getRecipes();

        if (recipes == null || recipes.isEmpty()) {
            getLogger().info(plugin.i18n("recipe.log.none_configured"));
            return 0;
        }

        int count = 0;
        for (Map.Entry<String, ?> entry : recipes.entrySet()) {
            String recipeName = entry.getKey();

            // Binding and registration are caught separately and on purpose: a value whose
            // SHAPE is wrong never reaches registration, and a recipe that is shaped correctly
            // but cannot be registered (an unusable name, for example) still reports through
            // the message it always did.
            RecipeConfig.RecipeDefinition definition;
            try {
                definition = RecipeConfig.RecipeDefinition.fromConfigValue(entry.getValue());
            } catch (IllegalArgumentException e) {
                getLogger().warn(String.format(plugin.i18n("recipe.log.skipped"), recipeName, reason(e)));
                continue;
            }

            try {
                if (registerRecipe(recipeName, definition)) {
                    count++;
                }
            } catch (Exception e) {
                getLogger().warn(String.format(plugin.i18n("recipe.log.register_failed"), recipeName, e.getMessage()));
            }
        }

        return count;
    }

    /**
     * Registers a single recipe.
     *
     * @param name       the recipe name
     * @param definition the recipe definition
     * @return true if registered successfully
     */
    private boolean registerRecipe(String name, RecipeConfig.RecipeDefinition definition) {
        // Validate definition
        if (definition.getOutput() == null || definition.getShape() == null || definition.getIngredients() == null) {
            getLogger().warn(String.format(plugin.i18n("recipe.log.invalid_definition"), name));
            return false;
        }

        // Enforce the constraints OutputItem's own fields declare (UltiKits/UltiRecipe#14). The
        // framework's validateFields() reads @NotEmpty/@Range only off the @ConfigEntry fields on
        // the entity itself and never recurses into a map value, so until this call the two
        // annotations on OutputItem were evaluated by nothing. This runs BEFORE createOutputItem
        // on purpose: an empty material used to reach Material.matchMaterial and come back as the
        // same "Invalid output material" line a misspelled material produces, so the log could
        // not tell a blank field from a typo. A violating entry is skipped on its own, named; the
        // rest of the file still registers, and nothing is clamped.
        RecipeProblem outputViolation = definition.getOutput().findConstraintViolation();
        if (outputViolation != null) {
            getLogger().warn(String.format(plugin.i18n("recipe.log.invalid_output"), name,
                    outputViolation.render(text)));
            return false;
        }

        // Create output item
        ItemStack output = createOutputItem(definition.getOutput());
        if (output == null) {
            getLogger().warn(String.format(plugin.i18n("recipe.log.invalid_output_material"), name));
            return false;
        }

        // Create recipe - use plugin instance for NamespacedKey since UltiToolsPlugin doesn't extend JavaPlugin
        NamespacedKey key = new NamespacedKey(getPluginInstance(), "ultirecipe_" + name);
        ShapedRecipe recipe = new ShapedRecipe(key, output);

        // Set shape
        List<String> shape = definition.getShape();
        if (shape.size() != 3) {
            getLogger().warn(String.format(plugin.i18n("recipe.log.shape_rows"), name));
            return false;
        }
        recipe.shape(shape.get(0), shape.get(1), shape.get(2));

        // Set ingredients
        Map<String, String> ingredients = definition.getIngredients();
        for (Map.Entry<String, String> ingredient : ingredients.entrySet()) {
            String charKey = ingredient.getKey();
            String materialName = ingredient.getValue();
            
            if (charKey.length() != 1) {
                getLogger().warn(String.format(plugin.i18n("recipe.log.ingredient_key_length"), name));
                continue;
            }
            
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                getLogger().warn(String.format(plugin.i18n("recipe.log.unknown_material"), materialName, name));
                continue;
            }
            
            recipe.setIngredient(charKey.charAt(0), material);
        }

        // Register recipe
        Bukkit.addRecipe(recipe);
        registeredRecipes.add(key);
        
        getLogger().info(String.format(plugin.i18n("recipe.log.registered"), name));
        return true;
    }

    /**
     * Creates an output ItemStack from configuration.
     *
     * @param outputConfig the output configuration
     * @return the created ItemStack, or null if invalid
     */
    private ItemStack createOutputItem(RecipeConfig.OutputItem outputConfig) {
        Material material = Material.matchMaterial(outputConfig.getMaterial());
        if (material == null) {
            return null;
        }

        ItemStack item = new ItemStack(material, outputConfig.getAmount());
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            // Set custom name
            if (outputConfig.getName() != null && !outputConfig.getName().isEmpty()) {
                meta.setDisplayName(translateColorCodes(outputConfig.getName()));
            }
            
            // Set lore
            if (outputConfig.getLore() != null && !outputConfig.getLore().isEmpty()) {
                List<String> lore = outputConfig.getLore().stream()
                        .map(this::translateColorCodes)
                        .collect(Collectors.toList());
                meta.setLore(lore);
            }
            
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Translates color codes (&) to Minecraft color codes (§).
     *
     * @param text the text to translate
     * @return the translated text
     */
    private String translateColorCodes(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Removes all registered custom recipes.
     */
    public void removeRecipes() {
        for (NamespacedKey key : registeredRecipes) {
            Bukkit.removeRecipe(key);
            getLogger().info(String.format(plugin.i18n("recipe.log.removed"), key.getKey()));
        }
        registeredRecipes.clear();
    }

    /**
     * Reloads all recipes (removes and re-registers).
     *
     * @return the number of recipes registered after reload
     */
    public int reloadRecipes() {
        removeRecipes();
        return initRecipes();
    }

    /**
     * Gets the list of registered recipe names.
     *
     * @return list of recipe names
     */
    public List<String> getRecipeList() {
        return registeredRecipes.stream()
                .map(key -> key.getKey().replace("ultirecipe_", ""))
                .collect(Collectors.toList());
    }

    /**
     * Gets the count of registered recipes.
     *
     * @return the number of registered recipes
     */
    public int getRecipeCount() {
        return registeredRecipes.size();
    }
}
