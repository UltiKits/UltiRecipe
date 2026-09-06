package com.ultikits.plugins.recipe;

import com.ultikits.plugins.recipe.config.RecipeConfig;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test helper for mocking UltiTools framework dependencies.
 * <p>
 * Since the singleton pattern has been removed, this helper creates mock
 * UltiToolsPlugin instances for injection into services and commands.
 * <p>
 * Call {@link #setUp()} in {@code @BeforeEach} and {@link #tearDown()} in {@code @AfterEach}.
 * <p>
 * <b>This is the module's shared test-time bootstrap.</b> {@link #setUp()} also starts the
 * live {@code MockBukkit} server ({@link MockBukkit#mock()}) that {@link #tearDown()} tears
 * back down ({@link MockBukkit#unmock()}) — every test class that needs a real
 * {@code Bukkit.getServer()} (not just Mockito stand-ins) must go through this pair rather
 * than calling {@code MockBukkit.mock()}/{@code unmock()} itself, so that a regression here
 * is visible to every caller at once, including {@code UltiRecipeRegistrySentinelTest}.
 * <p>
 * <b>Call the pair exactly once per test, from the outermost {@code @BeforeEach} and
 * {@code @AfterEach}.</b> This helper is not re-entrant: {@link MockBukkit#mock()} throws
 * {@code IllegalStateException("Already mocking")} when a server is already up, which is
 * what a {@code @Nested} class that adds its own {@code setUp()} call on top of the
 * enclosing class's would hit — JUnit runs the outer {@code @BeforeEach} first.
 * <p>
 * That exception is deliberately not guarded away. A guard that unmocked and re-mocked
 * would be worse than the failure it hides: {@link #setUp()} also rebuilds
 * {@link #getMockPlugin()} and {@link #getMockLogger()}, so anything the outer setup had
 * already injected would go on holding the discarded mocks, and the resulting verification
 * failures would surface far from their cause. A {@code @Nested} class inherits the
 * enclosing class's bootstrap; it must not repeat it.
 */
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
public final class UltiRecipeTestHelper {

    private UltiRecipeTestHelper() {}

    private static UltiRecipe mockPlugin;
    private static PluginLogger mockLogger;
    private static Plugin mockJavaPlugin;

    /**
     * Set up UltiRecipe mock. Must be called before each test.
     */
    @SuppressWarnings("unchecked")
    public static void setUp() throws Exception {
        // Live test-time server, shared by every caller of this helper. Real ItemStack/
        // NamespacedKey/Registry resolution (e.g. RecipeService.createOutputItem()) needs
        // a live Bukkit.getServer(), not just Mockito's static-method mocks.
        MockBukkit.mock();

        // Mock UltiRecipe (abstract UltiToolsPlugin — mockable)
        mockPlugin = mock(UltiRecipe.class);

        // Mock logger
        mockLogger = mock(PluginLogger.class);
        lenient().when(mockPlugin.getLogger()).thenReturn(mockLogger);

        // Mock i18n to return the key as-is
        lenient().when(mockPlugin.i18n(anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        // Mock getDataOperator
        lenient().when(mockPlugin.getDataOperator(any()))
                .thenReturn(mock(DataOperator.class));

        // A real plugin object for NamespacedKey creation, not a Mockito stand-in.
        // NamespacedKey(Plugin, String) reads Plugin.namespace(), declared on
        // net.kyori.adventure.key.Namespaced (which Plugin extends). Paper implements it
        // one hop further out than the plugin class itself: PluginBase.namespace()
        // delegates to PluginMeta.namespace(), whose default body is
        // getName().toLowerCase(Locale.ROOT). PluginMock extends JavaPlugin, so that whole
        // chain runs for real here and the namespace is always derived from the plugin
        // name. A hand-written thenReturn("ultitools") is free to drift away from the name
        // it is supposed to mirror, and nothing in the suite would notice.
        mockJavaPlugin = MockBukkit.createMockPlugin("UltiTools");
    }

    /**
     * Clean up state.
     */
    public static void tearDown() throws Exception {
        mockPlugin = null;
        mockLogger = null;
        mockJavaPlugin = null;

        // Mirror of the MockBukkit.mock() call in setUp().
        MockBukkit.unmock();
    }

    public static UltiRecipe getMockPlugin() {
        return mockPlugin;
    }

    public static PluginLogger getMockLogger() {
        return mockLogger;
    }

    /**
     * Get the plugin instance used for NamespacedKey creation.
     * <p>
     * This is a live {@code MockBukkit} {@code PluginMock} rather than a Mockito mock, so
     * {@code namespace()} resolves through Paper's own implementation instead of a stub.
     */
    public static Plugin getMockJavaPlugin() {
        return mockJavaPlugin;
    }

    /**
     * Create a default RecipeConfig mock with recipe system enabled.
     */
    public static RecipeConfig createDefaultConfig() {
        RecipeConfig config = mock(RecipeConfig.class);
        lenient().when(config.isEnabled()).thenReturn(true);
        lenient().when(config.getRecipes()).thenReturn(new HashMap<>());
        return config;
    }

    /**
     * Create a RecipeConfig with sample recipes.
     */
    public static RecipeConfig createConfigWithSampleRecipes() {
        RecipeConfig config = mock(RecipeConfig.class);
        lenient().when(config.isEnabled()).thenReturn(true);

        // Create sample recipe
        RecipeConfig.RecipeDefinition recipe = new RecipeConfig.RecipeDefinition();
        RecipeConfig.OutputItem output = new RecipeConfig.OutputItem();
        output.setMaterial("DIAMOND");
        output.setAmount(1);
        output.setName("&bCustom Diamond");
        List<String> lore = new ArrayList<>();
        lore.add("&7A custom diamond");
        output.setLore(lore);
        recipe.setOutput(output);

        List<String> shape = new ArrayList<>();
        shape.add("xxx");
        shape.add("xyx");
        shape.add("xxx");
        recipe.setShape(shape);

        HashMap<String, String> ingredients = new HashMap<>();
        ingredients.put("x", "COAL");
        ingredients.put("y", "STICK");
        recipe.setIngredients(ingredients);

        HashMap<String, RecipeConfig.RecipeDefinition> recipes = new HashMap<>();
        recipes.put("custom_diamond", recipe);

        lenient().when(config.getRecipes()).thenReturn(recipes);
        return config;
    }

    /**
     * Create a mock Player with basic properties.
     */
    public static Player createMockPlayer(String name, UUID uuid) {
        Player player = mock(Player.class);
        lenient().when(player.getName()).thenReturn(name);
        lenient().when(player.getUniqueId()).thenReturn(uuid);
        lenient().when(player.hasPermission(anyString())).thenReturn(true);

        World world = mock(World.class);
        lenient().when(world.getName()).thenReturn("world");
        Location location = new Location(world, 100.5, 64.0, -200.5);
        lenient().when(player.getLocation()).thenReturn(location);
        lenient().when(player.getWorld()).thenReturn(world);

        PlayerInventory inventory = mock(PlayerInventory.class);
        lenient().when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
        lenient().when(player.getInventory()).thenReturn(inventory);

        return player;
    }

    /**
     * Create a mock CommandSender.
     */
    public static CommandSender createMockSender(String name) {
        CommandSender sender = mock(CommandSender.class);
        lenient().when(sender.getName()).thenReturn(name);
        return sender;
    }

    // --- Reflection ---

    public static void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        Field field = null;
        while (clazz != null) {
            try {
                field = clazz.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        if (field == null) {
            throw new NoSuchFieldException(fieldName);
        }
        field.setAccessible(true); // NOPMD - intentional reflection for test mock injection
        field.set(target, value);
    }

    public static Object getField(Object target, String fieldName) throws Exception {
        Class<?> clazz = target.getClass();
        Field field = null;
        while (clazz != null) {
            try {
                field = clazz.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        if (field == null) {
            throw new NoSuchFieldException(fieldName);
        }
        field.setAccessible(true); // NOPMD - intentional reflection for test field access
        return field.get(target);
    }
}
