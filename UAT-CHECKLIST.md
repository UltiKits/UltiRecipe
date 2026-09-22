# UltiRecipe — UAT Checklist

This document is the executable companion to `FEATURES.md`: one row per feature stating the
steps to exercise it and the observable truth that proves it works. It is an internal reference
for real-machine verification, not user-facing documentation.

> Batches are dispatched at 60 rows or fewer, and a batch never spans two repositories. There are
> exactly two legitimate exits to `human-uat-pending`: a row needing the pixel layer while the
> real-client harness is not ready, and a row needing personal credentials. Every other row must
> reach `pass`, `fail`, or `blocked`.

## Conventions

- **Columns:** `ID`, `Preconditions`, `Steps`, `Expected`, `Layer`, `Covers`.
- **ID:** cites its `FEATURES.md` ID verbatim. A negative case suffixes the checklist ID only,
  as `.neg-<slug>` — a negative case still tests the same feature, so the base ID is unchanged.
- **Layer**, copied verbatim from Laojun's own `ultitools-real-client-uat` skill so no
  translation step exists at dispatch time: `protocol`, `java-client`, `os-input`, `pixel`,
  `server`, `human`.
- **Human-authenticated-session rows (D-27b):** a row whose Steps can only be exercised through
  the maintainer's own authenticated UltiCloud panel session carries the fixed Preconditions
  phrase `maintainer-authenticated UltiCloud panel session (personal credentials)` and Layer
  `human`, ending at `human-uat-pending` by design. UltiRecipe has no panel-capability rows of its
  own, so no row below is currently affected; the convention is stated here for template
  consistency with the framework's checklist.
- **Expected** must name an observable truth — an exact chat line, a log line, a database row,
  an inventory slot — and never the words "it works".
- **Covers** back-references a Phase 9 GUI-excluded class name; left blank when no such class
  applies. UltiRecipe is not one of the nine modules in Phase 9's GUI-exclusion register
  (confirmed by reading
  `.planning/phases/09-module-ecosystem-readiness-and-test-coverage/gui-exclusions/` — no
  `UltiRecipe.md` file exists there, and no file in that directory names UltiRecipe), so every
  row below leaves `Covers` blank.
- A row whose Preconditions name a prior row must appear after that row in file order — asserted
  mechanically: for every row, every checklist ID cited in its Preconditions cell must have a
  strictly smaller line number in this file than the row citing it (sweep class 8, D-27a).
- **Config-per-file rule (D-06):** one checklist row per `@ConfigEntity`-annotated class or per
  shipped yml file, never one row per key. This module ships exactly one config file
  (`config/recipes.yml`), so exactly one config-per-file row exists below
  (`ultirecipe.config.recipes-yml`), aggregating both `@ConfigEntry` keys and the recipe-
  definition schema documented in `FEATURES.md`'s `## Configuration` section.
- **`@ConditionalOnConfig` is evaluated once, at component-scan time (boot).** Every row below
  that exercises `ultirecipe.recipe.command-gate` or `ultirecipe.recipe.service-gate` requires a
  full server RESTART after editing `enabled` — never a `/recipe reload` (which does not even
  reach the config file, see `ultirecipe.recipe.reload`'s own row). As of
  `UltiKits/UltiRecipe#11`'s lifecycle-hook migration, `/ul reload UltiRecipe` now DOES call
  `ConfigManager#reloadConfigs` and reports `@ConditionalOnConfig` drift for these two gates (see
  `ultirecipe.lifecycle.reload`'s own row) — but drift reporting only LOGS that the watched key
  changed since boot; it never re-registers or rebuilds the gated bean itself. Flipping the key
  and reloading instead of restarting will therefore still show no change in the gated behaviour
  (the command/service either stays present or stays absent, whichever it was at boot) and must
  not be recorded as a false fail — only the drift-report log line is new, not the gate's actual
  state.
- This repository's shipped default is `language: "zh"` in the framework's own `config.yml`.
  Every row below whose Expected quotes a literal in-game or console line therefore carries the
  precondition `language: en` set in `plugins/UltiTools/config.yml`, so the observed line matches
  this document's English-only text exactly, character for character.
- `plugin.i18n(...)` in this framework is a raw dictionary lookup with **no** `MessageFormat`
  processing; this module's own `lang/en.json`/`lang/zh.json` contain no doubled single-quotes,
  so this note (carried from the framework's and UltiChat's own checklists for template
  consistency) has no live instance in this document.

## Recipe Management

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultirecipe.recipe.command-gate | `enabled: true` in `config/recipes.yml` (shipped default) | Restart the server, then run `/recipe help` and `/ultirecipe help` | `/recipe` (and its alias `/ultirecipe`) is a recognized command — `RecipeCommand` was registered at boot because `enabled` was `true` at component-scan time, and both invocations return the same help output | server | |
| ultirecipe.recipe.command-gate.neg-disabled | `enabled: false` in `config/recipes.yml`, set BEFORE the restart below (a `/recipe reload` or `/ul reload` alone does NOT apply this — `@ConditionalOnConfig` is evaluated once, at boot) | Restart the server, then run `/recipe help` and `/ultirecipe help` | Neither `/recipe` nor `/ultirecipe` is a recognized command (Bukkit's own "unknown command" response for both) — `RecipeCommand` was never registered because `enabled` was `false` at component-scan time | server | |
| ultirecipe.recipe.count | `language: en` in config.yml; exactly 2 custom recipes currently registered (run `ultirecipe.recipe.command-gate` first, then add TWO well-formed recipes to `recipes.yml` and restart — a fresh install ships zero recipes per `UltiKits/UltiRecipe#13`, so two must be added, not one; before running the step, confirm the console logged `[UltiTools] [UltiRecipe] Registered recipe:` twice and logged no `Skipped recipe` or `Failed to register recipe` line, so the number under test is known to be 2) | Run `/recipe count` | Chat/console line reads `Currently 2 recipes registered` in the exact `§7Currently §f<n> §7recipes registered` color/format template — the number must be 2, matching the two entries the precondition confirmed registered; any other number is a `failed` verdict for this row, because the count is the only thing `/recipe count` computes. This line is sent with `sendMessage`, not through the plugin logger, so it carries no `[UltiTools] [UltiRecipe]` prefix | server | |
| ultirecipe.recipe.list | `language: en` in config.yml; at least one custom recipe registered (see `ultirecipe.recipe.count`'s precondition) | Run `/recipe list` | Chat/console shows `=== Registered Recipes ===` (gold) followed by one `- <recipe-name>` line per registered recipe (gray dash, white name), then a `Total <n> recipes` summary line matching the count | server | |
| ultirecipe.recipe.list.neg-empty | `language: en` in config.yml; zero custom recipes currently registered — the shipped default state on a fresh install, because this module ships no `config/recipes.yml` resource and the file the framework writes on first boot carries only `enabled: true` and `recipes: {}`. The dead `RecipeConfig#initDefaults()` seeder that claimed to produce a "golden_egg" example was removed by `UltiKits/UltiRecipe#13`; because it never ran, this row's observable is identical before and after that removal (shipping an example is tracked as `UltiKits/UltiRecipe#23`) | Run `/recipe list` | Chat/console shows exactly `No recipes registered` (gray) — no header line, no recipe entries | server | |
| ultirecipe.recipe.reload | `language: en` in config.yml; at least one custom recipe registered; edit `config/recipes.yml` to ADD a second, distinct recipe on disk (do not restart, do not use `/recipe reload` yet) | Run `/recipe reload`, then immediately run `/recipe list` | Console/chat shows `Recipes reloaded! <n> recipes total` where `<n>` is the ORIGINAL count, NOT incremented by the newly-added recipe — `/recipe list` immediately afterward still shows only the original recipe(s), because `RecipeService#reloadRecipes` re-registers the config bean's already-in-memory `recipes` map rather than re-reading `recipes.yml` from disk. A known product defect, `UltiKits/UltiRecipe#12` | server | |
| ultirecipe.recipe.service-gate | `enabled: true` in `config/recipes.yml` (shipped default); at least one recipe configured in `recipes.yml` before the restart | Restart the server, then attempt to craft the configured recipe's shape in a crafting table | The configured output item appears in the result slot — `RecipeService` registered the recipe with Bukkit at boot because `enabled` was `true` at component-scan time | server | |
| ultirecipe.recipe.service-gate.neg-disabled | `enabled: false` in `config/recipes.yml`, set BEFORE the restart below, with at least one recipe still configured in `recipes.yml` (its presence in the file is irrelevant once the gate is off) | Restart the server, then attempt to craft the same shape in a crafting table | No custom output appears — the shape yields nothing (or whatever vanilla recipe happens to share the shape, if any), because `RecipeService` itself was never created as a bean, so no custom `ShapedRecipe` was ever registered with Bukkit regardless of what `recipes.yml` still contains | server | |

## Lifecycle Hooks

Both rows below exercise `UltiKits/UltiRecipe#11`'s wave-0 lifecycle-hook migration:
`unregisterSelf()`/`reloadSelf()` are now `final` framework template methods, and this module's
own cleanup/reload work moved verbatim into the `onUnregister()`/`onReload()` extension-point
hooks those template methods invoke. See `FEATURES.md`'s `## Lifecycle Hooks` and
`## Reload Behaviour Outside /recipe` sections for the full sequencing.

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultirecipe.lifecycle.unload | `language: en` in config.yml; module loaded with at least one custom recipe registered (add one entry to `recipes.yml` and restart if none exists yet — one recipe is sufficient to exercise this hook). Additionally: the module is loaded from its jar in `plugins/UltiTools/plugins/`, and that folder holds nothing but module jars that each contain a `plugin.yml` (any other file or subdirectory makes `/upm uninstall` stop with a red "delete failed, file access error" reply before it reaches this module's jar); a player is online next to a crafting table; BEFORE step 1, copy this module's jar out of `plugins/UltiTools/plugins/` to a location outside the server directory, because step 2 deletes it. | 1. As the player, craft the registered recipe's shape and confirm the custom output appears in the result slot (if it does not, the recipe was never registered: record this row `blocked`, never `pass`). 2. On the SAME running server (no restart, no stop), run `/upm uninstall UltiRecipe` from the server console (`UltiRecipe` is this module's runtime name, the `name:` in its `plugin.yml`). 3. As the player, craft the same shape again. 4. Cleanup: copy the saved jar back into `plugins/UltiTools/plugins/` and restart the server (not `/ul reload`, which does not load jars). Do NOT substitute a server stop or restart for step 2: a restart rebuilds Bukkit's crafting-recipe registry from scratch regardless of whether `onUnregister()` ran, so the row would false-pass — only an in-process unload on the running server distinguishes "the hook removed it" from "the registry was rebuilt anyway". A Bukkit plugin manager such as PlugMan cannot be used either: `UltiRecipe` is an UltiTools module, not a Bukkit plugin. | Step 2: the reply is the framework's green uninstall-success message (not its red uninstall-failed message, which would mean the runtime name did not match), and `plugins/UltiTools/plugins/` no longer contains this module's jar. Step 3: the crafting result slot is empty for that shape (or whatever vanilla recipe happens to share the shape, if any) — the previously-registered custom recipe no longer exists in Bukkit's crafting registry, because `RecipeService#removeRecipes()` was called from `onUnregister()`, which the framework's own (now-final) `unregisterSelf()` invokes before its own command/listener cleanup for this module | server | |
| ultirecipe.lifecycle.reload | `language: en` in config.yml; module loaded with at least one custom recipe registered — the same recipe precondition as `ultirecipe.lifecycle.unload` above (its jar-backup and online-player preconditions do not apply to this row). | Run `/ul reload UltiRecipe` through the framework's own reload command | Console shows, in this order: the framework's own per-module reload line `Module 'UltiRecipe' reloaded.`, then this module's own count line in the exact `[UltiTools] [UltiRecipe] Recipes reloaded, <n> recipes total` console format for the current recipe count — both bracketed prefixes are always present (`[UltiTools]` from Bukkit's own per-plugin logger, `[UltiRecipe]` added on top by `PluginLogger`; confirmed verbatim on a real Paper 1.21.11 server in `17-TRACER-LOAD-PROBE.md`) — the framework line appearing BEFORE this module's own line is exactly what proves `reloadSelf()`'s final template method ran its own steps (config reload, language refresh, `@ConditionalOnConfig` drift report) first, before invoking `onReload()`, which re-registers the module's recipes | server | |

## Configuration

One row for the module's single shipped yml file (D-06's config-per-file rule), covering both
`@ConfigEntry` keys (`enabled`, `recipes`) and the recipe-definition schema documented in
`FEATURES.md`'s `## Configuration` section (`output.material`, `output.amount`, `output.name`,
`output.lore`, `shape`, `ingredients`) at once, since none of those schema fields has an
independent `@ConfigEntry` binding of its own to exercise separately.

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultirecipe.config.recipes-yml | Fresh `config/recipes.yml` at its shipped default (`enabled: true`, `recipes: {}` — confirm the file truly ships an EMPTY `recipes` map, not the "golden_egg" example the README shows as a schema illustration; this module ships no `config/recipes.yml` resource and the dead seeder that claimed to write one was removed by `UltiKits/UltiRecipe#13`, with shipping an example tracked as `UltiKits/UltiRecipe#23`) | Load the file; confirm both keys (`enabled`, `recipes`) are present at their documented defaults; then add one recipe entry named `Custom_Sword` (mixed case) under `recipes` with a 3x3 `shape`, an `ingredients` map, and an `output` block (`material`, `amount`, `name`, `lore`), restart, and attempt to craft it, then run `/recipe list`; separately, add a second recipe entry whose `output.amount` is set to `100` (outside the declared 1-64 `@Range`) and confirm it is still registered and craftable in a stack of 100 rather than being rejected or clamped (`UltiKits/UltiRecipe#14`); separately, add a third recipe entry whose `shape` has only 2 rows and confirm it is skipped with a warning rather than registered; separately, add a fourth recipe entry named `bad name` (containing a space) and confirm it is skipped with a warning rather than registered or crashing the server; finally, leaving the well-formed `Custom_Sword` entry in place, add a fifth entry whose key is `broken` and whose whole value is the bare text `not a mapping` (no `output`, `shape` or `ingredients` beneath it), restart, and run `/recipe list`; lastly, delete the whole `ingredients:` block from the `Custom_Sword` entry, restart once more, run `/recipe list`, and attempt to craft the `Custom_Sword` shape | `enabled` and `recipes` present at their documented defaults before any change (`recipes` genuinely empty); after the change, the well-formed recipe crafts the configured output with its custom name/lore, and `/recipe list` shows it as `custom_sword` (lowercased), not `Custom_Sword` — the recipe's own name became part of a Bukkit `NamespacedKey`, which lowercases its input; the `amount: 100` recipe crafts a stack of 100 unclamped, proving the declared `@Range` has no effect; the 2-row-shape recipe registers zero output and the server log shows `[UltiTools] [UltiRecipe] Recipe shape must have exactly 3 rows for: <name>`; the `bad name` recipe registers zero output and the server log shows `[UltiTools] [UltiRecipe] Failed to register recipe: bad name - ...` (a `NamespacedKey` construction failure, caught by `initRecipes`'s try/catch); the `broken` entry registers nothing and the server log shows `[UltiTools] [UltiRecipe] Skipped recipe 'broken': expected a mapping, found the text 'not a mapping'`, while `/recipe list` still lists `custom_sword` and the module finishes loading (`UltiKits/UltiRecipe#16`); after the `ingredients:` block is deleted, the log shows `[UltiTools] [UltiRecipe] Invalid recipe definition for: Custom_Sword` and NO `Registered recipe: Custom_Sword` line, `/recipe list` does not list it, and the crafting table produces nothing for that shape (`UltiKits/UltiRecipe#16`, gate 1 WR-01) | server | |
