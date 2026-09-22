# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

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
