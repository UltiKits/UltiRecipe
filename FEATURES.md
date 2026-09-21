# UltiRecipe — Feature Inventory

This document catalogues every operator- or player-visible function, command, content item and
configuration key in this repository, as read directly from source. It is an internal reference
for UAT execution and issue reconciliation — the public description of these features lives on
<https://doc.ultikits.com/>. Update this file in the same pull request as any feature change.

## Conventions

- **ID grammar:** `<repo-slug>.<area>.<action>`, dot-separated, every segment lowercase ASCII
  drawn from `[a-z0-9-]`. `<repo-slug>` is the repository name lowercased with no separators —
  `ultirecipe` here, `ultichat`, `ultiessentials`, and `ultitools-example` for
  `UltiTools-External-Example`. `<area>` is the feature section's slug. `<action>` is the verb.
  A `config` row is the one shape that exceeds three segments and is exempt from the
  lowercase-ASCII rule for its key-path suffix:
  `<repo-slug>.config.<file-stem>.<yml key path>`, the key path keeping its own dots and its own
  casing verbatim from the yml file — a config ID is a citation of the key, not a re-derived slug,
  so lowercasing it would make it un-greppable against its own source line. An ID changes only
  when the feature's identity changes, never on rewording. IDs are unique within a repository.
  **Recipe-definition schema rows** (see `## Configuration` below) use a `<name>` placeholder
  segment in place of a concrete recipe key, because a recipe's own name is operator-defined, not
  a fixed key this document can cite verbatim — the placeholder documents the schema every recipe
  entry is parsed against, not one specific entry.
- **Kind**, exactly these eight values: `command`, `config`, `event`, `gui`, `scheduled`,
  `placeholder`, `persistence`, `gate`. Each maps one-to-one onto a reconciliation-table line.
  This module has no `@EventListener` class and no `@Scheduled` method, but does carry two
  `event`-Kind rows (see `## Lifecycle Hooks` below): `onUnregister()`/`onReload()` are
  framework-invoked callbacks, not player-triggered commands or config reads, so `event` is the
  closest-fitting Kind for a framework lifecycle hook the same way it would be for a Bukkit
  `@EventListener` — this module has no `gui` rows (no GUI page class), no `placeholder` rows (no
  PlaceholderAPI expansion registered), and no `persistence` rows beyond the ordinary config-file
  contract already captured by the `config` rows below (the in-memory `registeredRecipes` set is
  rebuilt from `recipes.yml` on every `registerSelf()`, which is expected bookkeeping, not a
  persistence guarantee distinct from the config file itself) — all eight Kinds stay in the
  vocabulary for cross-repository consistency even though four of them (`gui`, `scheduled`,
  `placeholder`, `persistence`) appear zero times below.
- **Tier**, exactly three: `player`, `admin`, `internal`. Judged from what the feature is for,
  not from whether it carries a permission string. All three of this module's commands are
  `admin` — recipe management is a server-owner activity, not something an ordinary player does,
  even though `list` and `count` are read-only.
- **Manual**, exactly three: `detailed`, `brief`, `none`.
- **Target**, exactly four: `player`, `console`, `both`, or `n/a` — the first three read straight
  off `@CmdTarget` for a `command` row; it is a property, not a tier. `n/a` is for every other
  Kind. This module's single `@CmdExecutor` class carries `@CmdTarget(BOTH)`, and no mapped method
  narrows it further (unlike UltiChat's `ChannelCommands`, none of `list`/`reload`/`count` reads
  `Player`-specific state), so all three command rows below carry Target `both`.
- **Permission:** the literal node string, `none`, or `n/a`, each optionally suffixed with the
  literal text `(requireOp=true)` (preceded by one space) when the row's class-level
  `@CmdExecutor` carries that flag. This module's one `@CmdExecutor` class (`RecipeCommand`) does
  not set `requireOp = true`, so no `command` row below carries the suffix; all three rely on the
  single literal permission node `ultirecipe.admin` alone. `n/a` is for every Kind that is not
  `command` — a config key or a gate has no permission node to declare in the first place. No
  permission node is declared in `plugin.yml` (this module has no `permissions:` section), so
  Bukkit's own undeclared-permission default applies: OP-only, unless a permissions plugin grants
  it explicitly.
- **Source:** `ClassName#member` — the class and member that actually reads or applies the
  feature — for every Kind, `config` included. Unlike a `@ConfigEntry`-annotated field's own
  class (which merely binds the key), a config row's Source cites whichever method actually
  reads or acts on the value.
- **Row order:** by section, then by ID ascending within the section.
- **No manual prose:** no troubleshooting column, no explanatory paragraphs, no draft page text.
  A hazard noticed while reading becomes a negative checklist row, not a note here. Where a
  feature's actual runtime behaviour genuinely diverges from what its own README or class javadoc
  describes it as doing, that fact is itself part of "what the feature does" and is stated here as
  a plain, sourced observation, with the filed issue number, never as advice on how to fix it.

### Reconciliation command family

The canonical form for counting an annotation site across this repository's real sources:

```bash
find <repo-root> -path '*/src/main/java/*' -name '*.java' -not -path '*/target/*' \
  -not -path '*/.worktrees/*' -print0 | xargs -0 grep -nE '^[[:space:]]*@AnnotationName\b' | wc -l
```

This module is a single-root Maven project (`src/main/java` only, 4 source files total), carries
no git worktree directory, and has no javadoc or string-literal mention of any of its own
annotation names — the naive (unanchored) and line-start counts are identical for every kind
measured below, but the anchored `find`/`grep` form is used regardless, so the same command is
trustworthy unmodified against every repository in the fan-out.

**Positive controls**, each confirmed by reading the cited line directly, not by trusting the
count alone:

| Annotation | Sites | Positive control |
|---|---|---|
| `@CmdExecutor` | 1 | `RecipeCommand.java:33`, class-level, `alias = {"recipe", "ultirecipe"}` |
| `@CmdMapping` | 3 | `RecipeCommand.java:57` (`list`), `:76` (`reload`), `:85` (`count`) |
| `@EventListener` | 0 | no event-driven behaviour exists anywhere in this module's 4 source files — confirmed by reading all 4 in full, not merely by an absent grep hit |
| `@Scheduled` | 0 | same — no background task exists in this module |
| `@ConfigEntity` | 1 | `RecipeConfig.java:44`, `@ConfigEntity("config/recipes.yml")` |
| `@ConditionalOnConfig` | 2 | `RecipeCommand.java:38` (gates the command class) and `RecipeService.java:33` (gates the service bean) — both read the same key, `config/recipes.yml`'s `enabled`, but are two independent annotation sites gating two independent beans |
| `@ConfigEntry` | 2 | `RecipeConfig.java:47` (`enabled`) and `:50` (`recipes`) |
| `@Table` | 0 | no ORM entity exists in this module — recipe state lives entirely in `recipes.yml` and Bukkit's own live crafting-recipe registry, never a database row |

This document's command-row count (3) matches the `@CmdMapping` site count exactly (3 against 3).
The `config` Kind's row count (8, see `## Configuration` below) exceeds the `@ConfigEntry` site
count (2) — this is a deliberate, explained imbalance, not a miscount: `recipes.yml`'s `recipes`
key is a single `@ConfigEntry`-bound `Map<String, RecipeDefinition>` field, but each map value is
itself a runtime-parsed nested schema (an output item, a shape, an ingredient map) with no
annotation of its own marking its sub-fields as configuration. This document catalogues that
nested schema at sub-key granularity, sourced to the code that actually reads each sub-field,
the same way a module with its own runtime-parsed content format (menu definitions, rule sets)
documents that content beyond its single top-level `@ConfigEntry` binding. The reconciliation
table in the pull request states this explanation once.

## Recipe Management

`RecipeCommand` — class-level `@CmdExecutor(alias = {"recipe", "ultirecipe"}, permission =
"ultirecipe.admin", description = "Manage custom recipes")` (the source annotation's own
`description` literal is in Chinese; translated here per this document's English-only rule),
`@CmdTarget(BOTH)`, gated by
`@ConditionalOnConfig(value = "config/recipes.yml", path = "enabled")`. `RecipeService` — the
class that actually registers, removes, and re-registers Bukkit `ShapedRecipe`s from the
`RecipeConfig` bean's in-memory `recipes` map — carries its own, independent
`@ConditionalOnConfig` site on the same key. Both beans are created (or not) together in normal
operation, since they read the identical config key, but they gate two functionally distinct
things: `RecipeCommand`'s gate controls whether the `/recipe` command surface exists at all;
`RecipeService`'s gate controls whether any custom recipe is ever registered into the crafting
system, independent of whether an operator can query it through a command.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultirecipe.recipe.command-gate | Register the `RecipeCommand` bean (and therefore the entire `/recipe`/`/ultirecipe` command and its three sub-commands below) only if `enabled` is `true` at component-scan time; a change to this key takes effect only on a full server restart, not on `/recipe reload` or `/ul reload` — see `## Reload Behaviour Outside /recipe` below for what `/ul reload` now does since `UltiKits/UltiRecipe#11`'s lifecycle-hook migration | gate | `enabled` in `plugins/UltiTools/UltiRecipe/config/recipes.yml`, applied only on a full server restart | n/a | n/a | admin | brief | RecipeCommand#RecipeCommand |
| ultirecipe.recipe.count | Show the number of currently registered custom recipes | command | `/recipe count` | ultirecipe.admin | both | admin | none | RecipeCommand#showCount |
| ultirecipe.recipe.list | List every currently registered custom recipe name, or a "no recipes" message if none are registered | command | `/recipe list` | ultirecipe.admin | both | admin | brief | RecipeCommand#listRecipes |
| ultirecipe.recipe.reload | Remove every currently-registered custom recipe from Bukkit's crafting system, then re-register the SAME set held in the already-loaded `RecipeConfig` bean's in-memory `recipes` map. Despite the command's name and the README's "hot reload without restart" description, this command never re-reads `config/recipes.yml` from disk — `RecipeService#reloadRecipes` calls neither `AbstractConfigEntity#reload()` nor `#init(...)` on the config bean, so an edit made to the file while the server is running has no effect until a full restart. A known product defect, UltiKits/UltiRecipe#12, not fixed here per this plan's zero-new-code rule | command | `/recipe reload` | ultirecipe.admin | both | admin | detailed | RecipeCommand#reloadRecipes, RecipeService#reloadRecipes |
| ultirecipe.recipe.service-gate | Create the `RecipeService` bean (and therefore all custom-recipe registration, removal, and reload behaviour) only if `enabled` is `true` at component-scan time; a change to this key takes effect only on a full server restart. Because this bean holds `RecipeCommand`'s own `@Autowired` dependency, both gates read the same key and in ordinary operation flip together — but they are two independent annotation sites on two independent beans, and the effect each controls is distinct: this gate removes every custom recipe from the crafting table entirely, not merely the `/recipe` command that reports on them | gate | `enabled` in `plugins/UltiTools/UltiRecipe/config/recipes.yml`, applied only on a full server restart | n/a | n/a | admin | brief | RecipeService#RecipeService |

## Reload Behaviour Outside `/recipe`

**Fixed by `UltiKits/UltiRecipe#11`'s wave-0 lifecycle-hook migration (this pull request).**
Before this migration, `UltiRecipe` overrode `UltiToolsPlugin#reloadSelf()`/`#unregisterSelf()`
directly without calling `super`, completely replacing the framework's own steps: on reload,
`ConfigManager#reloadConfigs` and the module's `language` object refresh never ran for this module;
on `/upm uninstall UltiRecipe`, command and listener unregistration were both skipped, so the
`/recipe` command stayed registered until the server restarted (this module registers no
listeners). Server shutdown was unaffected: there the framework unregistered listeners and commands
itself. As of 6.3.0,
`reloadSelf()`/`unregisterSelf()` are `final` template methods on `UltiToolsPlugin`; this module
now overrides the extension-point hooks `onReload()`/`onUnregister()` instead, with the same
bodies moved verbatim. `/ul reload UltiRecipe` (the framework's own command, not a
`@CmdMapping` site in this repository, so no `command`-Kind row is added here for it — the same
"do not add a row for a command this repository does not itself map" boundary the framework's own
`FEATURES.md` states for `/upm help`) now runs, in order: `ConfigManager#reloadConfigs`, the
module's `language` object refresh, `ConditionalRegistrationEvaluator`'s drift report for either
of this module's two gates above, the framework's own `Module 'UltiRecipe' reloaded.` INFO line,
and finally `onReload()` — which re-registers this module's recipes and logs its own count line,
exactly as before. (The drift report and the `Module 'UltiRecipe' reloaded.` line are new in
UltiTools 6.3.0; they did not exist in 6.2.5.) Unloading the module (`/upm uninstall UltiRecipe`
or server shutdown, both of which call `unregisterSelf()`) now runs `onUnregister()` (removing
every custom recipe, as before) followed by the framework's own command unregistration and then
listener unregistration for this module. See the two new
`ultirecipe.lifecycle.*` rows below for the hooks themselves.

## Lifecycle Hooks

`UltiRecipe#onUnregister()`/`#onReload()` are the extension-point hooks the framework's now-`final`
`UltiToolsPlugin#unregisterSelf()`/`#reloadSelf()` invoke (see `## Reload Behaviour Outside
/recipe` above for the full sequencing). Neither hook is reachable through a command this
repository maps itself — both are always invoked by the framework, either when the module is
unloaded (`/upm uninstall UltiRecipe`, or server shutdown) or when `/ul reload UltiRecipe` runs — so both rows below are `event`-Kind, not
`command`-Kind.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultirecipe.lifecycle.reload | Re-register this module's currently-configured recipes and log the resulting count when the module is reloaded via the framework's own `/ul reload` command | event | `/ul reload UltiRecipe` (framework calls `reloadSelf()`, which runs its own steps first, then invokes this hook) | n/a | n/a | admin | brief | UltiRecipe#onReload |
| ultirecipe.lifecycle.unload | Remove every custom recipe this module registered from Bukkit's crafting system when the module is unloaded | event | `/upm uninstall UltiRecipe`, or server shutdown (framework calls `unregisterSelf()`, which invokes this hook before its own command/listener cleanup) | n/a | n/a | admin | brief | UltiRecipe#onUnregister |

## Configuration

`RecipeConfig` (`config/recipes.yml`, the module's single `@ConfigEntity` class) declares two
top-level `@ConfigEntry` fields, `enabled` and `recipes`. The `recipes` map's values are a
runtime-parsed nested schema with no `@ConfigEntry` annotation of its own — see the reconciliation
note above. `RecipeConfig#initDefaults()` seeds one example recipe (`golden_egg`) into an
empty/null `recipes` map, but this method is called only from this repository's own test suite —
never from `UltiRecipe#registerSelf()`, `RecipeConfig`'s constructor, or the framework's own
`AbstractConfigEntity#init(...)`. A freshly-installed server therefore ships `recipes.yml` with an
EMPTY `recipes` map, not the "golden_egg" example the README and this class's own javadoc show. A
known product defect, UltiKits/UltiRecipe#13, not fixed here.

Separately, `OutputItem.material` carries `@NotEmpty` and `OutputItem.amount` carries
`@Range(min = 1, max = 64)`, but neither annotation is ever evaluated: the framework's
`AbstractConfigEntity#validateFields()` walks only the fields directly annotated `@ConfigEntry`
on the `RecipeConfig` class itself (`enabled`, `recipes`), never the nested `OutputItem` class
reached through a `recipes` map value, and `RecipeService#createOutputItem` applies no manual
bound check on `amount` either. A known product defect, UltiKits/UltiRecipe#14, not fixed here.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultirecipe.config.recipes.enabled | Master on/off switch for the entire module's recipe surface — see the two `-gate` rows above for exactly what registering `false` removes | config | `config/recipes.yml: enabled (default: true)` | n/a | n/a | admin | brief | RecipeCommand#RecipeCommand, RecipeService#RecipeService |
| ultirecipe.config.recipes.recipes | The recipe definitions map itself — one entry per custom recipe, keyed by an operator-chosen name, each value parsed against the schema documented in the five rows below. A recipe's own name is NOT an arbitrary string: it becomes the tail of a Bukkit `NamespacedKey` (`"ultirecipe_" + name`, constructed in `RecipeService#registerRecipe`), which lowercases the whole key and then rejects any character outside `[a-z0-9_-./]` — a name containing a space, colon, or other disallowed character throws at registration time, caught by `initRecipes`'s surrounding try/catch and logged as "Failed to register recipe: `<name>` - `<message>`", never registered; a name containing uppercase letters is silently accepted but the key (and therefore the name `/recipe list` displays) is lowercased; separately, `RecipeService#getRecipeList` derives the displayed name by calling `key.getKey().replace("ultirecipe_", "")` on the FULL key rather than stripping only the leading prefix it added, so a name that itself contains the substring `ultirecipe_` (anywhere, not just as a prefix) has EVERY occurrence of that substring removed from the displayed name, not merely the one this module prepended — for example a configured name of `my_ultirecipe_food` registers as key `ultirecipe_my_ultirecipe_food` and `/recipe list` displays `my_food`, silently dropping `ultirecipe_` from the middle of the operator-chosen name. Each entry is bound onto the declared schema by `RecipeConfig.RecipeDefinition#fromConfigValue`, which reads the structure only: an entry whose value is not a mapping, or whose `output`, `shape`, `ingredients`, `output.amount` or `output.lore` is of the wrong kind, is skipped on its own with `Skipped recipe '<name>': <reason>` while every other entry still registers, and an entry that omits `output`, `shape` or `ingredients` — or supplies an `ingredients` block with nothing usable under it — is refused with `Invalid recipe definition for: <name>` rather than registering a recipe no crafting table can produce. Declared with a Java-side default of an empty map; the "golden_egg" example shown in this class's own javadoc and in the README is NEVER actually generated on a fresh install (`UltiKits/UltiRecipe#13`) | config | `config/recipes.yml: recipes (default: {}, empty — despite documentation showing a "golden_egg" example, see UltiKits/UltiRecipe#13)` | n/a | n/a | admin | detailed | RecipeConfig#getRecipes, RecipeConfig.RecipeDefinition#fromConfigValue, RecipeService#initRecipes, RecipeService#registerRecipe |
| ultirecipe.config.recipes.recipes.\<name\>.ingredients | Character-to-material mapping used to fill in a shape's non-blank characters. Each key must be exactly one character (a multi-character key is skipped with a warning, the character is simply never placed); each value is matched against `Material.matchMaterial(...)` case-insensitively — an unrecognized material name is skipped with a warning rather than failing the whole recipe. The block as a whole is required: omitting it, or leaving nothing usable under it (Bukkit drops a mapping key whose value is empty, so `D:` with no material after it disappears), refuses the whole entry with `Invalid recipe definition for: <name>` — Paper accepts a `ShapedRecipe` whose shape characters have no ingredient, so an entry like that would otherwise register and never be craftable | config | `config/recipes.yml: recipes.<name>.ingredients (map, e.g. {D: DIAMOND, S: STICK})` | n/a | n/a | admin | brief | RecipeConfig.RecipeDefinition#fromConfigValue, RecipeService#registerRecipe |
| ultirecipe.config.recipes.recipes.\<name\>.output.amount | Output item stack size. `@Range(min = 1, max = 64)` is declared on this field but never enforced — the framework's own field-level validation does not recurse into a `Map` value's nested class, and `RecipeService#createOutputItem` applies no manual bound check either, so an out-of-range value (e.g. above Minecraft's own 64-item stack cap for most materials) passes through unclamped into the live `ItemStack`. A known product defect, UltiKits/UltiRecipe#14 | config | `config/recipes.yml: recipes.<name>.output.amount (default: 1, declared range 1-64, unenforced, see UltiKits/UltiRecipe#14)` | n/a | n/a | admin | detailed | RecipeService#createOutputItem |
| ultirecipe.config.recipes.recipes.\<name\>.output.lore | Optional custom lore lines for the output item, supporting `&`-prefixed color codes, applied in list order | config | `config/recipes.yml: recipes.<name>.output.lore (optional list of strings)` | n/a | n/a | admin | none | RecipeService#createOutputItem |
| ultirecipe.config.recipes.recipes.\<name\>.output.material | Output item's material name, matched case-insensitively against `Material.matchMaterial(...)`; an unrecognized name causes the whole recipe to be skipped with a warning rather than registered with a fallback material. `@NotEmpty` is declared on this field but is never evaluated by the framework's own validation (same cause as the `amount` row above) — the emptiness case happens to be caught anyway, but only because an empty string also fails `Material.matchMaterial`, not because the annotation fired. A known product defect, UltiKits/UltiRecipe#14 | config | `config/recipes.yml: recipes.<name>.output.material (required, no shipped default — a fresh install ships zero recipe entries at all, see UltiKits/UltiRecipe#13)` | n/a | n/a | admin | detailed | RecipeService#createOutputItem |
| ultirecipe.config.recipes.recipes.\<name\>.output.name | Optional custom display name for the output item, supporting `&`-prefixed color codes; when absent, Bukkit's own default item name applies | config | `config/recipes.yml: recipes.<name>.output.name (optional string)` | n/a | n/a | admin | none | RecipeService#createOutputItem |
| ultirecipe.config.recipes.recipes.\<name\>.shape | The crafting-grid shape, exactly 3 row strings, space meaning "empty slot"; `RecipeService#registerRecipe` itself checks only the row COUNT (a shape with any row count other than 3 is skipped with a warning, "Recipe shape must have exactly 3 rows for: `<name>`"). Per-row WIDTH is not separately checked in this module's own code, but is not silently accepted either: `Bukkit`'s own `ShapedRecipe#shape(String...)` throws `IllegalArgumentException` for a row outside 1-3 characters or for non-rectangular rows (differing widths across the 3 rows), and `RecipeService#initRecipes`'s surrounding try/catch turns that into a logged "Failed to register recipe: `<name>` - `<message>`" warning, exactly like any other malformed recipe — never a silent desynchronization | config | `config/recipes.yml: recipes.<name>.shape (list of exactly 3 strings, each string 1-3 characters, all three the same length)` | n/a | n/a | admin | detailed | RecipeService#registerRecipe |
