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
- Custom recipes in `config/recipes.yml` load again. Any file whose `recipes` map held at least
  one entry aborted this module's load with a `ClassCastException`, so no custom recipe was
  registered and `/recipe list`, `/recipe count` and `/recipe reload` fell through to Minecraft's
  own unrelated `/recipe` command; only the shipped empty default (`recipes: {}`) loaded. An entry
  whose structure cannot be read - a value that is not a mapping, an `output` that is not a
  mapping, an `output.amount` that is not a number, a `shape` that is not a list - is now skipped
  on its own, with a warning naming that entry and the reason, and every other entry in the file
  still registers (UltiKits/UltiRecipe#16).
- `/ul reload UltiRecipe` 现在会先重载本模块的配置（`config/recipes.yml`）并刷新其语言文件，再由本模块重新注册
  配方。此前本模块替换了框架的重载方法，这两步都不会执行。UltiTools 6.3.0 还会在此时报告
  `@ConditionalOnConfig` 漂移并输出框架自身的模块重载日志（UltiKits/UltiRecipe#11）。
- `/upm uninstall UltiRecipe` 仍会先移除本模块的自定义配方，之后现在还会真正移除其 `/recipe`（`/ultirecipe`）
  命令。此前本模块替换了框架的卸载方法，因此该命令会一直保持生效，直到服务器重启（UltiKits/UltiRecipe#11）。
- `config/recipes.yml` 中的自定义配方重新可以加载。此前只要 `recipes` 映射中存在任意一条配方，本模块的
  加载就会因 `ClassCastException` 中断，没有任何自定义配方被注册，`/recipe list`、`/recipe count` 和
  `/recipe reload` 都会落到 Minecraft 自带的、参数完全不同的 `/recipe` 命令上；只有随模块发布的空默认值
  （`recipes: {}`）能够加载。结构无法读取的配方条目——值不是映射、`output` 不是映射、`output.amount` 不是
  数字、`shape` 不是列表——现在会被单独跳过，并输出一条指明该条目及原因的警告，文件中的其余条目仍会正常注册
  （UltiKits/UltiRecipe#16）。
