package com.ultikits.plugins.recipe.service;

import com.ultikits.plugins.recipe.config.RecipeConfig;
import com.ultikits.plugins.recipe.config.RecipeProblem;
import com.ultikits.ultitools.abstracts.ReloadReport;
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

import java.io.IOException;
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
        Map<String, RecipeConfig.RecipeDefinition> recipes = config.getRecipes();

        if (recipes == null || recipes.isEmpty()) {
            getLogger().info(plugin.i18n("recipe.log.none_configured"));
            return 0;
        }

        int count = 0;
        for (Map.Entry<String, RecipeConfig.RecipeDefinition> entry : recipes.entrySet()) {
            String recipeName = entry.getKey();
            RecipeConfig.RecipeDefinition definition = entry.getValue();

            // A value that could not be read as a recipe never reaches registration, and is named with
            // the line this module has always written for it (UltiKits/UltiRecipe#32: the converter
            // keeps such a value in the map instead of letting the framework drop it unnamed). A recipe
            // that reads correctly but cannot be registered (an unusable name, for example) still
            // reports through the message it always did.
            RecipeProblem unreadable = unreadable(definition);
            if (unreadable != null) {
                getLogger().warn(String.format(plugin.i18n("recipe.log.skipped"), recipeName, unreadable.render(text)));
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
     * Why a bound {@code recipes.<name>} value cannot be used as a recipe at all, or {@code null} when it
     * can be judged as one.
     * <p>
     * Three cases, each reported with the reason the 6.2 binder gave for the same value: an entry written
     * with no value ({@code hollow:}, which the framework keeps as {@code null}), a value the converter
     * could not read ({@link RecipeConfig.UnreadableRecipe}), and an ingredient written with no material
     * ({@code D:}), the first one in file order.
     */
    private static RecipeProblem unreadable(RecipeConfig.RecipeDefinition definition) {
        if (definition == null) {
            return RecipeProblem.entryWithNoValue();
        }
        if (definition instanceof RecipeConfig.UnreadableRecipe) {
            return ((RecipeConfig.UnreadableRecipe) definition).getProblem();
        }
        if (definition.getIngredients() != null) {
            for (Map.Entry<String, String> ingredient : definition.getIngredients().entrySet()) {
                if (ingredient.getValue() == null) {
                    return RecipeProblem.noValue("ingredients." + ingredient.getKey());
                }
            }
        }
        return null;
    }

    /**
     * Registers a single recipe.
     *
     * @param name       the recipe name
     * @param definition the recipe definition
     * @return true if registered successfully
     */
    private boolean registerRecipe(String name, RecipeConfig.RecipeDefinition definition) {
        // Validate definition: every part present and non-empty, and an output that names a material.
        // The converter keeps an absent part null and an empty one empty (UltiKits/UltiRecipe#32); the
        // 6.2 binder turned both into null for this guard, so the operator reads the same line.
        if (isAbsentOrEmpty(definition)) {
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
            getLogger().warn(String.format(plugin.i18n("recipe.log.refused_output_material"), name,
                    definition.getOutput().getMaterial()));
            return false;
        }

        // Create recipe - use plugin instance for NamespacedKey since UltiToolsPlugin doesn't extend JavaPlugin
        NamespacedKey key = new NamespacedKey(getPluginInstance(), "ultirecipe_" + name);
        ShapedRecipe recipe = new ShapedRecipe(key, output);

        // Set shape
        List<String> shape = definition.getShape();
        if (shape.size() != 3) {
            getLogger().warn(String.format(plugin.i18n("recipe.log.refused_shape_rows"), name, shape));
            return false;
        }
        recipe.shape(shape.get(0), shape.get(1), shape.get(2));

        // Resolve every ingredient before anything is registered. An ingredient the module cannot use
        // refuses the whole entry, naming the ingredient as written: skipping it and registering the
        // rest made the server register a different, craftable recipe the operator never wrote, or
        // fail with an opaque index error when nothing was left (UltiKits/UltiRecipe#21, maintainer
        // decision 2026-09-27). Every unusable ingredient is named, so one pass over the file finds
        // them all.
        Map<String, String> ingredients = definition.getIngredients();
        Map<Character, Material> resolved = new LinkedHashMap<>();
        boolean usable = true;
        for (Map.Entry<String, String> ingredient : ingredients.entrySet()) {
            String charKey = ingredient.getKey();
            String materialName = ingredient.getValue();

            if (charKey.length() != 1) {
                getLogger().warn(String.format(plugin.i18n("recipe.log.refused_ingredient_key"), name, charKey));
                usable = false;
                continue;
            }

            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                getLogger().warn(String.format(plugin.i18n("recipe.log.refused_ingredient_material"),
                        name, charKey, materialName));
                usable = false;
                continue;
            }

            resolved.put(charKey.charAt(0), material);
        }
        // Every shape letter needs an entry under ingredients. Paper turns a letter without one into an
        // empty slot (CraftShapedRecipe#replaceUndefinedIngredientsWithEmpty), so registering it anyway
        // made a recipe that crafts from a pattern the operator never wrote. Each missing letter is named
        // once, as written, in the order the shape first uses it (UltiKits/UltiRecipe#29, maintainer
        // rule of 2026-09-27); a space is an empty slot, not a letter.
        Set<Character> named = new LinkedHashSet<>();
        for (String row : shape) {
            for (char letter : row.toCharArray()) {
                if (letter != ' ' && !ingredients.containsKey(String.valueOf(letter)) && named.add(letter)) {
                    getLogger().warn(String.format(plugin.i18n("recipe.log.refused_shape_letter"), name, letter));
                    usable = false;
                }
            }
        }
        if (!usable) {
            return false;
        }
        for (Map.Entry<Character, Material> ingredient : resolved.entrySet()) {
            recipe.setIngredient(ingredient.getKey(), ingredient.getValue());
        }

        // Register recipe
        Bukkit.addRecipe(recipe);
        registeredRecipes.add(key);
        
        getLogger().info(String.format(plugin.i18n("recipe.log.registered"), name));
        return true;
    }

    /**
     * Whether a definition lacks a part a recipe needs: no output, an output with no material, or a shape
     * or ingredient map that is absent or empty.
     */
    private static boolean isAbsentOrEmpty(RecipeConfig.RecipeDefinition definition) {
        return definition.getOutput() == null || definition.getOutput().getMaterial() == null
                || definition.getShape() == null || definition.getShape().isEmpty()
                || definition.getIngredients() == null || definition.getIngredients().isEmpty();
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
     * Reloads all recipes: reads {@code config/recipes.yml} again, then removes the registered
     * recipes and registers the file's recipes.
     * <p>
     * {@code /recipe reload} calls this directly, outside the framework's own reload, so without the
     * read here it re-registered the recipes held in memory since start-up and an edit to the file
     * never took effect (UltiKits/UltiRecipe#12). The file is read before anything is removed: if it
     * cannot be read, the recipes loaded before are registered again and the error is logged.
     *
     * @return the number of recipes registered after reload
     */
    public int reloadRecipes() {
        return reloadRecipes(new ReloadReport());
    }

    /**
     * Reloads all recipes as {@link #reloadRecipes()} does, and records in {@code report} what did not
     * reload: a {@code config/recipes.yml} that could not be read again is recorded with
     * {@link ReloadReport#partial(String)}, naming the file and the error, so {@code /ul reload UltiRecipe}
     * replies that the reload was partial instead of a plain success (UltiKits/UltiRecipe#30, framework
     * UltiTools-Reborn#529). The recipes loaded before are still registered again, as before.
     *
     * @param report the report the framework handed to the module's reload hook
     * @return the number of recipes registered after reload
     */
    public int reloadRecipes(ReloadReport report) {
        try {
            config.reload();
        } catch (IOException e) {
            getLogger().warn(String.format(plugin.i18n("recipe.log.config_reload_failed"), e.getMessage()));
            report.partial(String.format(plugin.i18n("recipe.reload.partial_config"), e.getMessage()));
        }
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
