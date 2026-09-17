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
- Unloading this module (`/upm uninstall UltiRecipe`, or server shutdown) still removes its custom
  recipes first, and now also unregisters its `/recipe` (`/ultirecipe`) command afterwards. Previously this module
  replaced the framework's unload method, so command unregistration was skipped on both paths;
  listener unregistration was also skipped on `/upm uninstall`, which has no effect here because the
  module registers no listeners (UltiKits/UltiRecipe#11).
- `/ul reload UltiRecipe` 现在会先重载本模块的配置（`config/recipes.yml`）并刷新其语言文件，再由本模块重新注册
  配方。此前本模块替换了框架的重载方法，这两步都不会执行。UltiTools 6.3.0 还会在此时报告
  `@ConditionalOnConfig` 漂移并输出框架自身的模块重载日志（UltiKits/UltiRecipe#11）。
- 卸载本模块（`/upm uninstall UltiRecipe` 或关闭服务器）时仍会先移除其自定义配方，之后现在还会注销其 `/recipe`
  （`/ultirecipe`）命令。此前本模块替换了框架的卸载方法，因此两条路径都跳过了命令注销；`/upm uninstall` 还跳过了监听器注销，
  但本模块没有注册任何监听器，因此对本模块没有影响（UltiKits/UltiRecipe#11）。
