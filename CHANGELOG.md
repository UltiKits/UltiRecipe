# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

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
  it, is refused with `Invalid recipe definition for: <name>`. Such an entry used to register:
  the server replaces a shape character that has no ingredient with a space, so what got
  registered was a different recipe over a smaller grid than the file described, not the one
  the operator wrote (UltiKits/UltiRecipe#16).
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
  `Invalid recipe definition for: <name>` 被拒绝。这类条目以前会被注册：服务端会将没有对应材料的形状字符
  替换为空格，因此实际注册的是一条网格更小、与文件描述不同的配方，而非管理员写下的那一条
  （UltiKits/UltiRecipe#16）。
