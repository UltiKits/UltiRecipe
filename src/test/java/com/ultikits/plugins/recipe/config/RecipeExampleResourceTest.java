package com.ultikits.plugins.recipe.config;

import com.ultikits.plugins.recipe.UltiRecipe;
import com.ultikits.plugins.recipe.UltiRecipeTestHelper;
import com.ultikits.plugins.recipe.service.RecipeService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests the shipped template, not a second handwritten copy of its recipe (#23). */
class RecipeExampleResourceTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {"en", "zh"})
    void shippedTemplateKeepsExistingDefaultsInBothLanguages(String language) throws Exception {
        FrameworkRecipeConfig.write(tempDir, resourceText());

        RecipeConfig config = FrameworkRecipeConfig.load(tempDir, language);

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getRecipes()).isEmpty();
        assertThat(config.isModifiedSinceSnapshot()).isFalse();
        assertThat(FrameworkRecipeConfig.read(tempDir)).contains("#   example_egg:", "#       material: EGG");
        assertThat(resourceText()).doesNotContainPattern("[\\u4e00-\\u9fff]");
    }

    @Test
    void uncommentedShippedExampleRegistersTheExactRecipeWithoutFixedLanguageText() throws Exception {
        String shipped = resourceText();
        int exampleStart = shipped.indexOf("# recipes:\n");
        assertThat(exampleStart).as("one complete opt-in YAML block").isGreaterThanOrEqualTo(0);
        String activated = "enabled: true\n" + shipped.substring(exampleStart).replaceAll("(?m)^# ?", "");
        FrameworkRecipeConfig.write(tempDir, activated);
        RecipeConfig config = FrameworkRecipeConfig.load(tempDir, "en");
        assertThat(config.getRecipes()).containsOnlyKeys("example_egg");
        RecipeConfig.OutputItem output = config.getRecipes().get("example_egg").getOutput();
        assertThat(output.getName()).isNull();
        assertThat(output.getLore()).isNull();

        UltiRecipeTestHelper.setUp();
        try {
            RecipeService service = new RecipeService();
            UltiRecipeTestHelper.setField(service, "plugin", UltiRecipeTestHelper.getMockPlugin());
            UltiRecipeTestHelper.setField(service, "config", config);
            UltiRecipeTestHelper.setField(service, "pluginInstance", UltiRecipeTestHelper.getMockJavaPlugin());

            assertThat(service.initRecipes()).isEqualTo(1);
            ShapedRecipe recipe = (ShapedRecipe) Bukkit.getRecipe(
                    new NamespacedKey(UltiRecipeTestHelper.getMockJavaPlugin(), "ultirecipe_example_egg"));
            assertThat(recipe).isNotNull();
            assertThat(recipe.getShape()).containsExactly("AAA", "AWA", "W W");
            assertThat(recipe.getIngredientMap().get('A').getType()).isEqualTo(Material.APPLE);
            assertThat(recipe.getIngredientMap().get('W').getType()).isEqualTo(Material.DARK_OAK_WOOD);
            assertThat(recipe.getResult().getType()).isEqualTo(Material.EGG);
            assertThat(recipe.getResult().getAmount()).isEqualTo(1);
            assertThat(recipe.getResult().getItemMeta().hasDisplayName()).isFalse();
            assertThat(recipe.getResult().getItemMeta().hasLore()).isFalse();
        } finally {
            UltiRecipeTestHelper.tearDown();
        }
    }

    @Test
    void realFrameworkExtractionCreatesTheMissingConfigAndKeepsItOnSecondStart() throws Exception {
        extractWithFramework();
        assertThat(FrameworkRecipeConfig.read(tempDir)).isEqualTo(resourceText());
        RecipeConfig config = FrameworkRecipeConfig.load(tempDir, "en");
        assertThat(config.getRecipes()).isEmpty();
        byte[] firstStart = Files.readAllBytes(FrameworkRecipeConfig.file(tempDir).toPath());

        extractWithFramework();
        FrameworkRecipeConfig.load(tempDir, "en");

        assertThat(Files.readAllBytes(FrameworkRecipeConfig.file(tempDir).toPath())).isEqualTo(firstStart);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "# Operator chose no recipes\nenabled: true\n# My empty map\nrecipes: {}\n",
            "# Operator disabled the module\nenabled: false\n# My recipes\nrecipes: {}\n",
            "# Operator's enabled setting\nenabled: true\n# My recipe and notes\nrecipes:\n  own_recipe: not a mapping\n",
            "# Invalid YAML is still operator content\nrecipes: [unclosed\n"
    })
    void realFrameworkExtractionNeverOverwritesAnExistingOperatorFile(String operatorText) throws Exception {
        FrameworkRecipeConfig.write(tempDir, operatorText);
        byte[] before = Files.readAllBytes(FrameworkRecipeConfig.file(tempDir).toPath());

        extractWithFramework();
        extractWithFramework();
        if (!operatorText.contains("[unclosed")) {
            RecipeConfig config = FrameworkRecipeConfig.load(tempDir, "en");
            assertThat(config.isModifiedSinceSnapshot()).isFalse();
        }

        assertThat(Files.readAllBytes(FrameworkRecipeConfig.file(tempDir).toPath())).isEqualTo(before);
    }

    private static String resourceText() throws Exception {
        return new String(resourceBytes(FrameworkRecipeConfig.PATH), StandardCharsets.UTF_8);
    }

    private static byte[] resourceBytes(String path) throws Exception {
        try (InputStream input = RecipeExampleResourceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("bundled resource: " + path).isNotNull();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    /**
     * Give the real extraction method a JAR-backed module CodeSource, as production does. Only the
     * module instance is mocked (no constructor or singleton); the private extraction method runs
     * unchanged. No alternative extractor is implemented here. Existing operator bytes, including an
     * intentionally empty map or invalid YAML, must survive because the framework skips existing files.
     */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private void extractWithFramework() throws Exception {
        String className = UltiRecipe.class.getName();
        String classEntry = className.replace('.', '/') + ".class";
        Path jar = Files.createTempFile(tempDir, "example-module-", ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String path : new String[]{classEntry, FrameworkRecipeConfig.PATH}) {
                output.putNextEntry(new JarEntry(path));
                output.write(resourceBytes(path));
                output.closeEntry();
            }
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    if (!name.equals(className)) {
                        return super.loadClass(name, resolve);
                    }
                    Class<?> type = findLoadedClass(name);
                    if (type == null) {
                        type = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(type);
                    }
                    return type;
                }
            }
        }) {
            UltiToolsPlugin plugin = (UltiToolsPlugin) mock(loader.loadClass(className));
            assertThat(plugin.getClass().getProtectionDomain().getCodeSource().getLocation())
                    .isEqualTo(jar.toUri().toURL());
            when(plugin.getLogger()).thenReturn(mock(PluginLogger.class));
            UltiRecipeTestHelper.setField(plugin, "resourceFolderPath", tempDir.toString());
            Method extraction = UltiToolsPlugin.class.getDeclaredMethod("saveResources", boolean.class);
            extraction.setAccessible(true);
            extraction.invoke(plugin, false);
        }
    }
}
