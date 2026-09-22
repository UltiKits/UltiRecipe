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
- `/ul reload UltiRecipe` 现在会先重载本模块的配置（`config/recipes.yml`）并刷新其语言文件，再由本模块重新注册
  配方。此前本模块替换了框架的重载方法，这两步都不会执行。UltiTools 6.3.0 还会在此时报告
  `@ConditionalOnConfig` 漂移并输出框架自身的模块重载日志（UltiKits/UltiRecipe#11）。
- `/upm uninstall UltiRecipe` 仍会先移除本模块的自定义配方，之后现在还会真正移除其 `/recipe`（`/ultirecipe`）
  命令。此前本模块替换了框架的卸载方法，因此该命令会一直保持生效，直到服务器重启（UltiKits/UltiRecipe#11）。
