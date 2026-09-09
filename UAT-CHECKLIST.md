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
  reach the config file, see `ultirecipe.recipe.reload`'s own row) and never a `/ul reload`
  (which, for this module specifically, does not call `ConfigManager#reloadConfigs` at all, per
  `UltiKits/UltiRecipe#11`). Flipping the key and reloading instead of restarting will show no
  change and must not be recorded as a false fail.
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
| ultirecipe.recipe.command-gate | `enabled: true` in `config/recipes.yml` (shipped default) | Restart the server, then run `/recipe help` | `/recipe` (and its alias `/ultirecipe`) is a recognized command — `RecipeCommand` was registered at boot because `enabled` was `true` at component-scan time | server | |
| ultirecipe.recipe.command-gate.neg-disabled | `enabled: false` in `config/recipes.yml`, set BEFORE the restart below (a `/recipe reload` or `/ul reload` alone does NOT apply this — `@ConditionalOnConfig` is evaluated once, at boot) | Restart the server, then run `/recipe help` | `/recipe` (and `/ultirecipe`) is NOT a recognized command (Bukkit's own "unknown command" response) — `RecipeCommand` was never registered because `enabled` was `false` at component-scan time | server | |
| ultirecipe.recipe.count | `language: en` in config.yml; at least 2 custom recipes currently registered (run `ultirecipe.recipe.command-gate` first, then add at least one more recipe to `recipes.yml` and restart, since a fresh install ships zero recipes per `UltiKits/UltiRecipe#13`) | Run `/recipe count` | Chat/console line reads `Currently 2 recipes registered` (or the actual current count) in the exact `§7Currently §f<n> §7recipes registered` color/format template | server | |
| ultirecipe.recipe.list | `language: en` in config.yml; at least one custom recipe registered (see `ultirecipe.recipe.count`'s precondition) | Run `/recipe list` | Chat/console shows `=== Registered Recipes ===` (gold) followed by one `- <recipe-name>` line per registered recipe (gray dash, white name), then a `Total <n> recipes` summary line matching the count | server | |
| ultirecipe.recipe.list.neg-empty | `language: en` in config.yml; zero custom recipes currently registered — the shipped default state on a fresh install, since `RecipeConfig#initDefaults()` is dead code and never seeds the documented "golden_egg" example (`UltiKits/UltiRecipe#13`) | Run `/recipe list` | Chat/console shows exactly `No recipes registered` (gray) — no header line, no recipe entries | server | |
| ultirecipe.recipe.reload | `language: en` in config.yml; at least one custom recipe registered; edit `config/recipes.yml` to ADD a second, distinct recipe on disk (do not restart, do not use `/recipe reload` yet) | Run `/recipe reload`, then immediately run `/recipe list` | Console/chat shows `Recipes reloaded! <n> recipes total` where `<n>` is the ORIGINAL count, NOT incremented by the newly-added recipe — `/recipe list` immediately afterward still shows only the original recipe(s), because `RecipeService#reloadRecipes` re-registers the config bean's already-in-memory `recipes` map rather than re-reading `recipes.yml` from disk. A known product defect, `UltiKits/UltiRecipe#12` | server | |
| ultirecipe.recipe.service-gate | `enabled: true` in `config/recipes.yml` (shipped default); at least one recipe configured in `recipes.yml` before the restart | Restart the server, then attempt to craft the configured recipe's shape in a crafting table | The configured output item appears in the result slot — `RecipeService` registered the recipe with Bukkit at boot because `enabled` was `true` at component-scan time | server | |
| ultirecipe.recipe.service-gate.neg-disabled | `enabled: false` in `config/recipes.yml`, set BEFORE the restart below, with at least one recipe still configured in `recipes.yml` (its presence in the file is irrelevant once the gate is off) | Restart the server, then attempt to craft the same shape in a crafting table | No custom output appears — the shape yields nothing (or whatever vanilla recipe happens to share the shape, if any), because `RecipeService` itself was never created as a bean, so no custom `ShapedRecipe` was ever registered with Bukkit regardless of what `recipes.yml` still contains | server | |

## Configuration

One row for the module's single shipped yml file (D-06's config-per-file rule), covering both
`@ConfigEntry` keys (`enabled`, `recipes`) and the recipe-definition schema documented in
`FEATURES.md`'s `## Configuration` section (`output.material`, `output.amount`, `output.name`,
`output.lore`, `shape`, `ingredients`) at once, since none of those schema fields has an
independent `@ConfigEntry` binding of its own to exercise separately.

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultirecipe.config.recipes-yml | Fresh `config/recipes.yml` at its shipped default (`enabled: true`, `recipes: {}` — confirm the file truly ships an EMPTY `recipes` map, not the "golden_egg" example some documentation shows, per `UltiKits/UltiRecipe#13`) | Load the file; confirm both keys (`enabled`, `recipes`) are present at their documented defaults; then add one recipe entry named `Custom_Sword` (mixed case) under `recipes` with a 3x3 `shape`, an `ingredients` map, and an `output` block (`material`, `amount`, `name`, `lore`), restart, and attempt to craft it, then run `/recipe list`; separately, add a second recipe entry whose `output.amount` is set to `100` (outside the declared 1-64 `@Range`) and confirm it is still registered and craftable in a stack of 100 rather than being rejected or clamped (`UltiKits/UltiRecipe#14`); separately, add a third recipe entry whose `shape` has only 2 rows and confirm it is skipped with a warning rather than registered; separately, add a fourth recipe entry named `bad name` (containing a space) and confirm it is skipped with a warning rather than registered or crashing the server | `enabled` and `recipes` present at their documented defaults before any change (`recipes` genuinely empty); after the change, the well-formed recipe crafts the configured output with its custom name/lore, and `/recipe list` shows it as `custom_sword` (lowercased), not `Custom_Sword` — the recipe's own name became part of a Bukkit `NamespacedKey`, which lowercases its input; the `amount: 100` recipe crafts a stack of 100 unclamped, proving the declared `@Range` has no effect; the 2-row-shape recipe registers zero output and the server log shows `Recipe shape must have exactly 3 rows for: <name>`; the `bad name` recipe registers zero output and the server log shows `Failed to register recipe: bad name - ...` (a `NamespacedKey` construction failure, caught by `initRecipes`'s try/catch) | server | |
