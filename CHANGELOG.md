# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Changed

- `/recipe` deliberately takes over the vanilla `/recipe` command of the same name, as the maintainer
  decided; this is now stated in the documentation. The vanilla command stays reachable as
  `minecraft:recipe` (UltiKits/UltiRecipe#27).
- `/recipe` 按维护者决定有意接管同名的原版 `/recipe` 命令，现已写入文档；原版命令仍可用 `minecraft:recipe` 调用
  （UltiKits/UltiRecipe#27）。

- Language keys were renamed from Chinese sentences to ASCII keys (for example `recipe.help.header`).
  If you customised this module's messages in your own language file -- a copy of an official file
  whose name starts with that file's language code and a hyphen (for example `lang/zh-myserver.json`),
  selected with `language: zh-myserver` in `plugins/UltiTools/config.yml` -- re-apply those edits to the new
  keys; until then each renamed message shows the text of the official language the name starts with. A copy
  whose name does not start with an official language code and a hyphen is still read, but every message
  it lacks then shows in English, with one warning. An edit made directly in an official
  language file (`lang/en.json`, `lang/zh.json`) is not kept: the framework restores the official files at
  every start and on every module reload and keeps the edited file as `.bak` (UltiKits/UltiTools-Reborn#616). A server that never
  customised messages needs no action.
- 语言键已从中文句子改为 ASCII 键（例如 `recipe.help.header`）。如果你在自己的语言文件中自定义过本模块的消息——即把官方文件复制一份，文件名以该文件的语言代码加连字符开头
  （例如 `lang/zh-myserver.json`），并在 `plugins/UltiTools/config.yml` 中设置 `language: zh-myserver` 选择它——请把改动重新套到新键上；
  在此之前，改名的消息显示文件名开头那种官方语言的文本。文件名不以官方语言代码加连字符开头的副本仍会被读取，但其中缺少的消息
  都显示英文，并记录一条警告。
  直接在官方语言文件（`lang/en.json`、`lang/zh.json`）中做的修改不会保留：框架会在每次启动以及每次模块重载时恢复官方文件，并把修改过的文件
  保留为 `.bak`（UltiKits/UltiTools-Reborn#616）。从未自定义过消息的服务器无需任何操作。

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

- UltiRecipe loads on UltiTools-API 6.3.0. That framework refuses at module load a configuration value
  type it cannot store, and refused this module with `no config converter for
  ...RecipeConfig$RecipeDefinition` (file `config/recipes.yml`, key `recipes`); the module now registers a
  converter for its recipes. An existing `recipes.yml` loads with the same values and is not rewritten,
  and a recipe that cannot be read is skipped with the same warning as before. Two values Bukkit's parser
  used to drop without a word are now named: a recipe written with no value at all (`hollow:`) is skipped
  with `Skipped recipe 'hollow': expected a mapping, found nothing`, and an ingredient written with no
  material (`D:`) skips its recipe with `Skipped recipe '<name>': ingredients.D: has no value` (it used to
  disappear, which refused the recipe as an invalid definition or, beside other ingredients, left its
  shape letter empty). This version must be released together with UltiTools-API 6.3.0
  (UltiKits/UltiRecipe#32).
- UltiRecipe 可在 UltiTools-API 6.3.0 上加载。该框架在模块加载时拒绝它无法存储的配置值类型，此前会以
  `no config converter for ...RecipeConfig$RecipeDefinition`（文件 `config/recipes.yml`，键 `recipes`）拒绝本模块；
  现在本模块为配方注册了转换器。已有的 `recipes.yml` 读出的值不变，文件也不会被改写；读不成配方的条目仍以原来的警告跳过。
  Bukkit 解析器以前会悄悄丢掉的两种写法现在会被点名：完全没有值的配方（`hollow:`）以
  `Skipped recipe 'hollow': expected a mapping, found nothing` 跳过；没有写材料的配料（`D:`）以
  `Skipped recipe '<名称>': ingredients.D: has no value` 跳过该配方（此前它会消失，配方要么被当成无效定义拒绝，要么在有其他配料时
  让那个形状字母变成空格）。本版本必须与 UltiTools-API 6.3.0 一同发布（UltiKits/UltiRecipe#32）。

- The comments above the two keys of `config/recipes.yml` (`enabled`, `recipes`) now come from the
  module's language files: a server set to `language: en` writes English comments on a fresh install
  (they were Chinese in every language). The comments the framework wrote on these two keys, the Chinese
  ones earlier versions wrote included, switch to the server's language at the next start, and after you
  change `language` and run a bare `/ul reload`; values are untouched, and a comment you wrote yourself is kept as
  you wrote it (UltiKits/UltiTools-Reborn#611) (UltiKits/UltiRecipe#31).
- `config/recipes.yml` 中两个配置项（`enabled`、`recipes`）上方的注释现在取自模块的语言文件：`language: en` 的服务器全新安装时
  写入英文注释（此前所有语言下都是中文）。框架在这两项上写下的注释（包括旧版本写下的中文注释）会在下次启动时、以及你修改
  `language` 并执行不带参数的 `/ul reload` 后切换为服务器语言；配置值不变，你自己写的注释保持原样（UltiKits/UltiTools-Reborn#611）
  （UltiKits/UltiRecipe#31）。

- `/ul reload UltiRecipe` no longer replies a plain success when `config/recipes.yml` cannot be read again.
  An invalid or unreadable file is caught by the framework's own configuration reload, which runs before
  this module's reload code (UltiKits/UltiTools-Reborn#589): the reply is `Module UltiRecipe failed to
  reload: <cause>`, the cause naming `config/recipes.yml`, the console shows the framework's SEVERE line,
  and the recipes loaded before stay registered. If a later re-read by the module itself fails, the module
  records it in the framework's reload report, so the reply says the reload was partial and names the file
  and the error (UltiKits/UltiRecipe#30).
- 重新读取 `config/recipes.yml` 失败时，`/ul reload UltiRecipe` 不再一律回复成功。YAML 写错或文件不可读会在框架自己的配置重载中被发现，
  它在本模块的重载代码之前执行（UltiKits/UltiTools-Reborn#589）：回复为 `Module UltiRecipe failed to reload: <原因>`，原因写明
  `config/recipes.yml`，控制台显示框架的 SEVERE 行，之前加载的配方仍然有效。若之后本模块自己的再次读取失败，本模块会把它记进框架的
  重载报告，回复会说明这次重载只完成了一部分，并写明文件和错误（UltiKits/UltiRecipe#30）。

- `/recipe reload` no longer replies `Recipes reloaded!` when `config/recipes.yml` could not be read again
  (an unreadable file, invalid YAML): it replies `Recipes reloaded only in part, <n> recipes total: <reason>`,
  where the reason names the file and the error, and the recipes loaded before stay registered
  (UltiKits/UltiRecipe#33).
- 重新读取 `config/recipes.yml` 失败（文件不可读、YAML 写错）时，`/recipe reload` 不再回复「配方已重载！」，而是回复
  `配方只重载了一部分，共 <n> 个配方：<原因>`，原因写明文件和错误；之前加载的配方仍然有效（UltiKits/UltiRecipe#33）。

- A recipe in `config/recipes.yml` whose `shape` uses a letter with no entry under `ingredients` is no
  longer registered, and a console warning names the recipe and the letter as written, for example
  `Recipe 'sword' not registered: shape letter 'S' has no ingredient`; every such letter is named once.
  The server used to turn that letter into an empty slot, so the recipe crafted from a pattern nobody
  wrote (UltiKits/UltiRecipe#29).
- `config/recipes.yml` 中 `shape` 用到了 `ingredients` 下没有的字母的配方不再注册，控制台警告会点名该配方和按原样书写的字母，例如
  `Recipe 'sword' not registered: shape letter 'S' has no ingredient`；每个这样的字母只点名一次。此前服务器会把该字母当成空格，
  配方变成按一个谁也没写过的图案合成（UltiKits/UltiRecipe#29）。

- A fractional `output.amount` in `config/recipes.yml` (for example `2.5`) now refuses that recipe with a
  warning quoting the value as written: `Skipped recipe '<name>': output.amount: expected a whole number,
  found 2.5`. It used to be truncated without a word, so `2.5` registered a stack of 2, and `0.5` was
  reported as `value 0`, a number nobody wrote. `2.0` still means 2 (UltiKits/UltiRecipe#24).
- `config/recipes.yml` 中带小数的 `output.amount`（例如 `2.5`）现在会让该配方被拒绝，警告按原样引用所写的值：
  `Skipped recipe '<名称>': output.amount: expected a whole number, found 2.5`。此前它会被静默截断，`2.5` 注册为 2 个，
  `0.5` 则被报告为谁也没写过的 `value 0`。`2.0` 仍表示 2（UltiKits/UltiRecipe#24）。

- A recipe in `config/recipes.yml` with an ingredient the module cannot use — an unknown material, or
  an ingredient key longer than one character — is no longer registered. Each such ingredient is named
  as written in a console warning, for example
  `Recipe 'partial' not registered: ingredient 'S' names an unknown material 'NOT_A_MATERIAL'`.
  The ingredient used to be skipped and the rest of the recipe registered, which the server turned into
  a different recipe that could be crafted with fewer materials, or into an unexplained index error
  (UltiKits/UltiRecipe#21). The warnings for an unknown output material and for a shape without three
  rows now also quote the value as written.
- `config/recipes.yml` 中含有模块无法使用的材料（未知材料，或多于一个字符的材料键）的配方不再注册，控制台警告会按原样点名
  每一个这样的材料，例如 `Recipe 'partial' not registered: ingredient 'S' names an unknown material 'NOT_A_MATERIAL'`。
  此前会跳过该材料并注册配方的其余部分，服务器会把它变成一个用更少材料就能合成的另一张配方，或者报出无从解释的索引错误
  （UltiKits/UltiRecipe#21）。未知产出材料和形状不是三行的警告现在也会按原样引用所写的值。

- `/recipe reload` now reads `config/recipes.yml` again before it re-registers the recipes, so an
  edit made while the server is running takes effect without a restart. It used to re-register the
  recipes held in memory since start-up. If the file cannot be read, the recipes loaded before are
  registered again and a console warning names the error (UltiKits/UltiRecipe#12).
- `/recipe reload` 现在会先重新读取 `config/recipes.yml` 再重新注册配方，服务器运行中修改文件后无需重启即可生效。
  此前它只会重新注册启动时加载到内存中的配方。若文件无法读取，会重新注册之前已加载的配方，并在控制台警告中写明
  错误（UltiKits/UltiRecipe#12）。

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
