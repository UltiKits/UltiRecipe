package com.ultikits.plugins.recipe.config;

import com.ultikits.plugins.recipe.UltiRecipe;
import com.ultikits.plugins.recipe.i18n.CatalogueText;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;
import com.ultikits.ultitools.config.convert.ConverterRegistry;

import org.mockito.Answers;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test support: loads {@code config/recipes.yml} through the framework's own configuration layer, the
 * way a server running UltiTools-API 6.3.0 loads it.
 * <p>
 * Two framework steps run for real, in the order the framework runs them when it loads a module:
 * {@link ConverterRegistry#prepareSelectedConfigs} discovers the converters in the module's scan
 * packages and checks every {@code @ConfigEntity}'s declared field types (this is where a type with no
 * converter is refused, before any file is read), then {@link RecipeConfig#init} reads the file and
 * binds every key through that registry. Only the module object is a stand-in: a Mockito double whose
 * {@code getConfigFile} points into a temporary folder and whose {@code i18n} answers from the module's
 * real catalogues in the language a test names.
 * <p>
 * The registry is an {@code @ApiStatus.Internal} framework class; the module's production code never
 * touches it, and this helper calls it only to reproduce the framework's module-load step.
 * <p>
 * 测试辅助：按 6.3.0 框架加载模块时的真实顺序（先发现转换器并检查字段类型，再读文件绑定）加载
 * {@code config/recipes.yml}。
 */
public final class FrameworkRecipeConfig {

    /** The module-relative path of the file, as {@code RecipeConfig} declares it. */
    public static final String PATH = RecipeConfig.class
            .getAnnotation(com.ultikits.ultitools.annotations.ConfigEntity.class).value();

    private FrameworkRecipeConfig() {
    }

    /** The file {@link #load} reads, under {@code dir}. */
    public static File file(Path dir) {
        return new File(dir.toFile(), PATH);
    }

    /** Writes {@code text} as {@code config/recipes.yml} under {@code dir}, creating the folder. */
    public static void write(Path dir, String text) throws IOException {
        File file = file(dir);
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    /** The bytes of {@code config/recipes.yml} under {@code dir}, as text. */
    public static String read(Path dir) throws IOException {
        return new String(Files.readAllBytes(file(dir).toPath()), StandardCharsets.UTF_8);
    }

    /**
     * A stand-in module whose configuration folder is {@code dir} and whose catalogue is the module's
     * real one for {@code language}.
     */
    public static UltiToolsPlugin plugin(final Path dir, final String language) {
        return Mockito.mock(UltiToolsPlugin.class, invocation -> {
            String name = invocation.getMethod().getName();
            if ("getConfigFolder".equals(name)) {
                return dir.toString();
            }
            if ("getConfigFile".equals(name)) {
                return new File(dir.toFile(), invocation.<String>getArgument(0));
            }
            if ("i18n".equals(name)) {
                return CatalogueText.answer(language).answer(invocation);
            }
            if ("getPluginName".equals(name)) {
                return "UltiRecipe";
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }

    /**
     * Runs the framework's module-load preparation for {@code plugin}: converter discovery over the
     * module's declared scan packages, then the declared-type check of every config entity there.
     *
     * @throws com.ultikits.ultitools.exceptions.ConfigurationException when a declared type has no
     *         converter -- the refusal an operator reads at module load
     */
    public static void prepare(UltiToolsPlugin plugin) {
        String[] packages = UltiRecipe.class.getAnnotation(UltiToolsModule.class).scanBasePackages();
        ConverterRegistry.prepareSelectedConfigs(plugin, packages, FrameworkRecipeConfig.class.getClassLoader());
    }

    /**
     * Loads {@code config/recipes.yml} under {@code dir} through the framework, as one module start
     * does, under the server language {@code language}.
     */
    public static RecipeConfig load(Path dir, String language) throws IOException {
        UltiToolsPlugin plugin = plugin(dir, language);
        prepare(plugin);
        RecipeConfig config = new RecipeConfig(PATH);
        config.init(plugin);
        return config;
    }
}
