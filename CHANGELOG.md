# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Changed

- Language keys were renamed from Chinese sentences to ASCII keys (for example `recipe.help.header`).
  An operator who edited this module's `lang/en.json` or `lang/zh.json` must re-apply those edits to
  the new keys; until then the renamed messages show the new built-in text. A server whose language
  files were never edited needs no action.
- 语言键已从中文句子改为 ASCII 键（例如 `recipe.help.header`）。改过本模块 `lang/en.json` 或
  `lang/zh.json` 的运维需要把改动重新套到新键上；在此之前，这些消息显示新的内置文本。从未改过语言文件的服务器无需任何操作。

### Removed

- `RecipeConfig#initDefaults()`, which claimed in its own javadoc to be "called by the framework
  after instantiation" and to seed an example `golden_egg` recipe on first run. Nothing ever called
  it, so no install has ever received that example; the claim is gone rather than the behaviour. A
  fresh install still writes `config/recipes.yml` with `enabled: true` and an empty `recipes: {}`
  map, exactly as before — nothing an operator can observe changes. The README bullet that promised
  an auto-generated example configuration was reworded to say the same thing. **Shipping an example
  recipe on first run is not rejected, only deferred: it is tracked as UltiKits/UltiRecipe#23**
  (UltiKits/UltiRecipe#13).
- `RecipeConfig#initDefaults()` 已删除。该方法的 javadoc 自称「由框架在实例化后调用」并会在首次运行时写入一条
  `golden_egg` 示例配方，但从未有任何代码调用它，因此没有任何一次安装真正拿到过这条示例；这里删掉的是那句假声明，
  不是行为。全新安装写出的 `config/recipes.yml` 仍然是 `enabled: true` 加一个空的 `recipes: {}`，与此前完全一致，
  运维看不到任何差别。README 中「自动生成示例配置」那一条也改为与实际一致的说法。**「随插件附带一份示例配方」并未被
  否决，只是延后：由 UltiKits/UltiRecipe#23 跟踪**（UltiKits/UltiRecipe#13）。

### Fixed

- `language: en` now applies to the `/recipe` command description, which showed the Chinese
  sentence in every language because it was missing from both language files, and to the eleven
  console lines written while recipes are loaded, registered and removed (`Registered recipe: …`,
  `Skipped recipe '…': …`, `Invalid output for recipe: …`, `Unknown material '…' in recipe: …` and
  the rest), which were fixed English text, including the reason after the colon in
  `Skipped recipe` and `Invalid output for recipe` (`expected a mapping, found …`,
  `value 100 is out of range [1, 64]`, …). Their English wording is unchanged; under
  `language: zh` they are now Chinese, except the YAML path and the offending value, which are
  printed as written.
- `language: en` 现在对 `/recipe` 命令描述生效（它在两份语言文件里都缺失，所以任何语言下都显示中文句子），
  也对加载、注册、移除配方时写出的十一条控制台日志生效（`Registered recipe: …`、`Skipped recipe '…': …`
  等），这些日志原先写死为英文，包括 `Skipped recipe` 与 `Invalid output for recipe` 冒号后的原因说明。
  英文措辞不变；`language: zh` 下现在是中文，只有 YAML 路径和出错的取值按原样打印。

- A recipe's `output.material` and `output.amount` are now checked against the constraints
  declared on them (`@NotEmpty`, and `@Range(min = 1, max = 64)` inclusive at both ends). Neither
  was checked by anything before: the framework validates only a configuration class's own
  top-level keys and never recurses into a recipe entry, and this module added no check of its
  own. **What changes for an operator:** an `output.amount` outside 1-64 used to register and
  craft as written — `amount: 100` produced a stack of 100 — and is now refused with
  `Invalid output for recipe: <name> - output.amount: value 100 is out of range [1, 64]`. It is
  refused, not clamped: a server that relies on an out-of-range stack size must change that entry,
  and will be told exactly which one. An empty or whitespace-only `output.material` was already
  refused, but through `Material.matchMaterial` failing, so it produced the same
  `Invalid output material for recipe: <name>` line a misspelled material produces; it now reports
  `Invalid output for recipe: <name> - output.material: must not be empty`, so a blank field and a
  typo are finally distinguishable in the log. In both cases only the offending entry is skipped —
  every other recipe in the file still registers (UltiKits/UltiRecipe#14).
- `/ul reload UltiRecipe` now reloads this module's configuration (`config/recipes.yml`) and
  refreshes its language files before the module re-registers its recipes. Previously this module
  replaced the framework's reload method, so neither step ran. UltiTools 6.3.0 also reports
  `@ConditionalOnConfig` drift and logs its own per-module reload line at this point
  (UltiKits/UltiRecipe#11).
- `/upm uninstall UltiRecipe` still removes this module's custom recipes first, and now also really
  removes its `/recipe` (`/ultirecipe`) command afterwards. Previously this module replaced the
  framework's unload method, so the command stayed active until the server restarted
  (UltiKits/UltiRecipe#11).
- Custom recipes in `config/recipes.yml` now load. In every released build of this module, a file
  whose `recipes` map held at least one entry aborted the module's load with a
  `ClassCastException`, so no custom recipe was registered and `/recipe list`, `/recipe count` and
  `/recipe reload` fell through to Minecraft's own unrelated `/recipe` command; only the shipped
  empty default (`recipes: {}`) loaded. There is no earlier version to roll back to - the defect
  is present in this module's first commit. An entry whose structure cannot be read - a value that
  is not a mapping, an `output` that is not a mapping, an `output.amount` that is not a number, a
  `shape` that is not a list - is now skipped on its own, with a warning naming that entry and the
  reason, and every other entry in the file still registers. An entry that leaves out `output`,
  `shape` or `ingredients` entirely, or supplies an `ingredients` block with nothing usable under
  it, is refused with `Invalid recipe definition for: <name>`, which names the entry
  (UltiKits/UltiRecipe#16).
- 配方的 `output.material` 与 `output.amount` 现在会按其字段上声明的约束校验（`@NotEmpty`，以及两端均为闭区间的
  `@Range(min = 1, max = 64)`）。此前这两条约束不被任何代码执行：框架只校验配置类自身的顶层键，不会递归进入配方条目，
  而本模块也没有自己做检查。**对运维意味着什么：** 超出 1-64 的 `output.amount` 此前会照写注册并可合成——`amount: 100`
  真的产出一组 100 个——现在会被拒绝，并输出
  `Invalid output for recipe: <name> - output.amount: value 100 is out of range [1, 64]`。是拒绝而不是截断：
  若某台服务器依赖超范围的堆叠数量，必须自行修改该条目，而日志会指明是哪一条。空的或只有空白字符的 `output.material`
  此前也会被拒绝，但走的是 `Material.matchMaterial` 失败那条路，因此与材料名拼错输出同一行
  `Invalid output material for recipe: <name>`；现在输出
  `Invalid output for recipe: <name> - output.material: must not be empty`，留空与拼错终于可以在日志里区分。
  两种情况都只跳过出问题的那一条，文件中其余配方照常注册（UltiKits/UltiRecipe#14）。
- `/ul reload UltiRecipe` 现在会先重载本模块的配置（`config/recipes.yml`）并刷新其语言文件，再由本模块重新注册
  配方。此前本模块替换了框架的重载方法，这两步都不会执行。UltiTools 6.3.0 还会在此时报告
  `@ConditionalOnConfig` 漂移并输出框架自身的模块重载日志（UltiKits/UltiRecipe#11）。
- `/upm uninstall UltiRecipe` 仍会先移除本模块的自定义配方，之后现在还会真正移除其 `/recipe`（`/ultirecipe`）
  命令。此前本模块替换了框架的卸载方法，因此该命令会一直保持生效，直到服务器重启（UltiKits/UltiRecipe#11）。
- `config/recipes.yml` 中的自定义配方现在可以加载。在本模块已发布的每一个版本中，只要 `recipes` 映射中存在
  任意一条配方，模块的加载就会因 `ClassCastException` 中断，没有任何自定义配方被注册，`/recipe list`、
  `/recipe count` 和 `/recipe reload` 都会落到 Minecraft 自带的、参数完全不同的 `/recipe` 命令上；只有随
  模块发布的空默认值（`recipes: {}`）能够加载。不存在可供回退的更早版本——该缺陷在本模块的首次提交中即已存在。
  结构无法读取的配方条目——值不是映射、`output` 不是映射、`output.amount` 不是数字、`shape` 不是列表——现在
  会被单独跳过，并输出一条指明该条目及原因的警告，文件中的其余条目仍会正常注册。完全缺少 `output`、`shape`
  或 `ingredients` 的条目，以及 `ingredients` 下没有任何可用内容的条目，现在会以
  `Invalid recipe definition for: <name>` 被拒绝，该提示会指明是哪一条配方
  （UltiKits/UltiRecipe#16）。
