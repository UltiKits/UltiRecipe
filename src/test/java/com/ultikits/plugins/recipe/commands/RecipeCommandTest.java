package com.ultikits.plugins.recipe.commands;

import com.ultikits.plugins.recipe.UltiRecipeTestHelper;
import com.ultikits.plugins.recipe.i18n.CatalogueText;
import com.ultikits.plugins.recipe.config.RecipeConfig;
import com.ultikits.plugins.recipe.service.RecipeService;
import com.ultikits.ultitools.abstracts.ReloadReport;
import com.ultikits.ultitools.exceptions.ConfigurationException;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RecipeCommand Tests")
class RecipeCommandTest {

    private RecipeCommand command;
    private RecipeService service;
    private CommandSender sender;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        UltiRecipeTestHelper.setUp();
        // These assertions quote the Chinese text: answer from the real zh catalogue.
        when(UltiRecipeTestHelper.getMockPlugin().i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));

        service = mock(RecipeService.class);
        command = new RecipeCommand();

        // Inject dependencies via reflection
        UltiRecipeTestHelper.setField(command, "plugin", UltiRecipeTestHelper.getMockPlugin());
        UltiRecipeTestHelper.setField(command, "recipeService", service);

        sender = UltiRecipeTestHelper.createMockSender("TestSender");
        player = UltiRecipeTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiRecipeTestHelper.tearDown();
    }

    // ==================== listRecipes ====================

    @Nested
    @DisplayName("list command")
    class ListCommand {

        @Test
        @DisplayName("Should display message when no recipes")
        void noRecipes() {
            when(service.getRecipeList()).thenReturn(Collections.emptyList());

            command.listRecipes(sender);

            verify(sender).sendMessage(contains("没有已注册的配方"));
        }

        @Test
        @DisplayName("Should list all recipes")
        void listRecipes() {
            List<String> recipes = Arrays.asList("custom_diamond", "golden_egg", "magic_sword");
            when(service.getRecipeList()).thenReturn(recipes);

            command.listRecipes(sender);

            verify(sender).sendMessage(contains("已注册的配方"));
            verify(sender).sendMessage(contains("custom_diamond"));
            verify(sender).sendMessage(contains("golden_egg"));
            verify(sender).sendMessage(contains("magic_sword"));
            verify(sender).sendMessage(contains("共"));
            verify(sender).sendMessage(contains("3"));
        }

        @Test
        @DisplayName("Should display correct count")
        void correctCount() {
            List<String> recipes = Arrays.asList("recipe1", "recipe2");
            when(service.getRecipeList()).thenReturn(recipes);

            command.listRecipes(sender);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender, atLeast(1)).sendMessage(captor.capture());

            boolean hasCount = captor.getAllValues().stream()
                    .anyMatch(msg -> msg.contains("2") && msg.contains("个配方"));
            assertThat(hasCount).isTrue();
        }
    }

    // ==================== reloadRecipes ====================

    @Nested
    @DisplayName("reload command")
    class ReloadCommand {

        @Test
        @DisplayName("Should reload recipes and display count")
        void reloadSuccess() {
            when(service.reloadRecipes(any(ReloadReport.class))).thenReturn(10);

            command.reloadRecipes(sender);

            verify(service).reloadRecipes(any(ReloadReport.class));
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());

            String message = captor.getValue();
            assertThat(message).contains("配方已重载");
            assertThat(message).contains("10");
        }

        @Test
        @DisplayName("Should handle zero recipes after reload")
        void reloadZero() {
            when(service.reloadRecipes(any(ReloadReport.class))).thenReturn(0);

            command.reloadRecipes(sender);

            verify(service).reloadRecipes(any(ReloadReport.class));
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());

            assertThat(captor.getValue()).contains("0");
        }

        @Test
        @DisplayName("Should handle large recipe count")
        void reloadLarge() {
            when(service.reloadRecipes(any(ReloadReport.class))).thenReturn(999);

            command.reloadRecipes(sender);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());

            assertThat(captor.getValue()).contains("999");
        }

        /**
         * UltiKits/UltiRecipe#33: a re-read of config/recipes.yml that failed is not a success. The reply
         * says the reload was partial, gives the count still registered and the reason, in the sender's
         * language, against the same contract as /ul reload (UltiKits/UltiRecipe#30).
         */
        @Test
        @DisplayName("a failed re-read is replied as a partial reload with its reason, not as success (#33)")
        void failedReReadIsRepliedAsPartial() {
            when(service.reloadRecipes(any(ReloadReport.class))).thenAnswer(invocation -> {
                invocation.getArgument(0, ReloadReport.class).partial("why it failed");
                return 3;
            });

            command.reloadRecipes(sender);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("why it failed").contains("3")
                    .doesNotContain("配方已重载！");
        }

        @Test
        @DisplayName("end to end: reload() throwing ConfigurationException gives the partial reply naming the file (#33)")
        void configurationExceptionGivesThePartialReply() throws Exception {
            RecipeService real = new RecipeService();
            RecipeConfig config = UltiRecipeTestHelper.createDefaultConfig();
            ConfigurationException failure = new ConfigurationException("Cannot load config/recipes.yml: AccessDeniedException");
            doThrow(failure).when(config).reload();
            UltiRecipeTestHelper.setField(real, "plugin", UltiRecipeTestHelper.getMockPlugin());
            UltiRecipeTestHelper.setField(real, "config", config);
            UltiRecipeTestHelper.setField(real, "pluginInstance", UltiRecipeTestHelper.getMockJavaPlugin());
            UltiRecipeTestHelper.setField(command, "recipeService", real);

            command.reloadRecipes(sender);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("config/recipes.yml").contains(failure.getMessage())
                    .doesNotContain("配方已重载！");
        }
    }

    // ==================== showCount ====================

    @Nested
    @DisplayName("count command")
    class CountCommand {

        @Test
        @DisplayName("Should display recipe count")
        void showCount() {
            when(service.getRecipeCount()).thenReturn(5);

            command.showCount(sender);

            verify(service).getRecipeCount();
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());

            String message = captor.getValue();
            assertThat(message).contains("5");
            assertThat(message).contains("个配方");
        }

        @Test
        @DisplayName("Should display zero count")
        void showZeroCount() {
            when(service.getRecipeCount()).thenReturn(0);

            command.showCount(sender);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());

            assertThat(captor.getValue()).contains("0");
        }

        @Test
        @DisplayName("Should display correct Chinese text")
        void chineseText() {
            when(service.getRecipeCount()).thenReturn(1);

            command.showCount(sender);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender).sendMessage(captor.capture());

            assertThat(captor.getValue()).contains("已注册");
        }
    }

    // ==================== handleHelp ====================

    @Nested
    @DisplayName("help command")
    class HelpCommand {

        @Test
        @DisplayName("Should display help messages")
        void showHelp() {
            command.handleHelp(sender);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender, atLeast(4)).sendMessage(captor.capture());

            List<String> messages = captor.getAllValues();
            assertThat(messages).anyMatch(msg -> msg.contains("UltiRecipe"));
            assertThat(messages).anyMatch(msg -> msg.contains("/recipe list"));
            assertThat(messages).anyMatch(msg -> msg.contains("/recipe reload"));
            assertThat(messages).anyMatch(msg -> msg.contains("/recipe count"));
        }

        @Test
        @DisplayName("Should include command descriptions")
        void includeDescriptions() {
            command.handleHelp(sender);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(sender, atLeast(4)).sendMessage(captor.capture());

            List<String> messages = captor.getAllValues();
            assertThat(messages).anyMatch(msg -> msg.contains("列出所有配方"));
            assertThat(messages).anyMatch(msg -> msg.contains("重载配方配置"));
            assertThat(messages).anyMatch(msg -> msg.contains("显示配方数量"));
        }
    }

    // ==================== suggest (tab completion) ====================

    @Nested
    @DisplayName("Tab Completion")
    class TabCompletion {

        @Test
        @DisplayName("Should suggest all subcommands for empty arg")
        void suggestAll() {
            Command cmd = mock(Command.class);
            String[] args = {""};

            List<String> suggestions = command.suggest(player, cmd, args);

            assertThat(suggestions).containsExactlyInAnyOrder("list", "reload", "count");
        }

        @Test
        @DisplayName("Should filter suggestions based on partial input")
        void filterSuggestions() {
            Command cmd = mock(Command.class);
            String[] args = {"re"};

            List<String> suggestions = command.suggest(player, cmd, args);

            assertThat(suggestions).containsExactly("reload");
            assertThat(suggestions).doesNotContain("list", "count");
        }

        @Test
        @DisplayName("Should handle exact match")
        void exactMatch() {
            Command cmd = mock(Command.class);
            String[] args = {"list"};

            List<String> suggestions = command.suggest(player, cmd, args);

            assertThat(suggestions).containsExactly("list");
        }

        @Test
        @DisplayName("Should be case insensitive")
        void caseInsensitive() {
            Command cmd = mock(Command.class);
            String[] args = {"LI"};

            List<String> suggestions = command.suggest(player, cmd, args);

            assertThat(suggestions).containsExactly("list");
        }

        @Test
        @DisplayName("Should return empty list for no matches")
        void noMatches() {
            Command cmd = mock(Command.class);
            String[] args = {"xyz"};

            List<String> suggestions = command.suggest(player, cmd, args);

            assertThat(suggestions).isEmpty();
        }

        @Test
        @DisplayName("Should not suggest for second argument")
        void noSecondArg() {
            Command cmd = mock(Command.class);
            String[] args = {"list", "extra"};

            List<String> suggestions = command.suggest(player, cmd, args);

            // Should delegate to super.suggest() which returns null or empty
            assertThat(suggestions).isNullOrEmpty();
        }

        @Test
        @DisplayName("Should suggest count when prefix matches")
        void suggestCount() {
            Command cmd = mock(Command.class);
            String[] args = {"co"};

            List<String> suggestions = command.suggest(player, cmd, args);

            assertThat(suggestions).containsExactly("count");
        }
    }

    // ==================== Integration Tests ====================

    @Nested
    @DisplayName("Integration")
    class Integration {

        @Test
        @DisplayName("Should handle multiple commands in sequence")
        void multipleCommands() {
            when(service.getRecipeList()).thenReturn(Arrays.asList("recipe1"));
            when(service.reloadRecipes(any(ReloadReport.class))).thenReturn(2);
            when(service.getRecipeCount()).thenReturn(2);

            command.listRecipes(sender);
            command.reloadRecipes(sender);
            command.showCount(sender);

            verify(service).getRecipeList();
            verify(service).reloadRecipes(any(ReloadReport.class));
            verify(service).getRecipeCount();
            verify(sender, atLeast(3)).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should work with different sender types")
        void differentSenders() {
            when(service.getRecipeCount()).thenReturn(5);

            command.showCount(sender);
            command.showCount(player);

            verify(service, times(2)).getRecipeCount();
            verify(sender).sendMessage(anyString());
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle service returning null gracefully")
        void handleNullService() {
            when(service.getRecipeList()).thenReturn(null);

            // Should not throw exception
            assertThatCode(() -> command.listRecipes(sender))
                    .doesNotThrowAnyException();
        }
    }

    // ==================== Service Interaction ====================

    @Nested
    @DisplayName("Service Interaction")
    class ServiceInteraction {

        @Test
        @DisplayName("list should call getRecipeList exactly once")
        void listCallsOnce() {
            when(service.getRecipeList()).thenReturn(Collections.emptyList());

            command.listRecipes(sender);

            verify(service, times(1)).getRecipeList();
        }

        @Test
        @DisplayName("reload should call reloadRecipes exactly once")
        void reloadCallsOnce() {
            when(service.reloadRecipes(any(ReloadReport.class))).thenReturn(0);

            command.reloadRecipes(sender);

            verify(service, times(1)).reloadRecipes(any(ReloadReport.class));
        }

        @Test
        @DisplayName("count should call getRecipeCount exactly once")
        void countCallsOnce() {
            when(service.getRecipeCount()).thenReturn(0);

            command.showCount(sender);

            verify(service, times(1)).getRecipeCount();
        }

        @Test
        @DisplayName("help should not call service")
        void helpNoService() {
            command.handleHelp(sender);

            verifyNoInteractions(service);
        }
    }
}
