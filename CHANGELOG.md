# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

- Unloading or reloading this module (`/ul reload UltiRecipe`, or disabling the module) now
  actually runs the framework's own reload/unload steps (config reload, language refresh,
  `@ConditionalOnConfig` drift reporting, command/listener cleanup) before this module's own
  recipe cleanup/re-registration — previously these framework steps were silently skipped
  (UltiKits/UltiRecipe#11).
- 卸载或重载本模块（`/ul reload UltiRecipe`，或禁用本模块）现在会先真正执行框架自身的重载/卸载步骤
  （配置重载、语言刷新、`@ConditionalOnConfig` 漂移报告、命令/监听器清理），再执行本模块自身的配方
  清理/重新注册——此前这些框架步骤会被静默跳过（UltiKits/UltiRecipe#11）。
