# Lobotocraft 命令清单（功能命令优先）

> 依据当前源码注册树整理，更新时间：2026-09-24。  
> 服务端命令实现位于 `src/main/java/com/wzz/lobotocraft/command/`，客户端命令实现位于
> `src/main/java/com/wzz/lobotocraft/client/command/`。

本文先列出可以直接用于设施运行、管理和演示的命令；`debug` 下具有实际操作功能的命令集中在第 8 节，纯诊断、结构工具和视觉原型放在后面。这里的“已注册”来自源码核对，不代表本次在游戏中逐条执行过，也不保证旧 JAR 包包含这些入口。

阅读约定：

- `<参数>` 必填，`[参数]` 可选，`a|b` 表示任选一个值；输入时不要包含这些括号或竖线。
- `<player>` 是单个在线玩家参数，可用玩家名或 `@s`；`<target>` 是单个实体，可用 UUID 或带 `limit=1` 的选择器；只有 `<targets>` 支持多目标。
- `<employee>` 是名册中的 AI 员工名称，不是实体选择器；这些名称参数使用 `word()`，命令行仅接受不含空格的英文字母、数字及 `_ . + -`，不要给名称加引号。界面创建的中文/含空格名字不能直接通过此参数输入。
- 坐标写作 `<x y z>`，可使用 `~ ~ ~` 等 Minecraft 坐标语法；信标/电梯等方块坐标要求目标位置已加载。
- 涉及删除、清场、强制结算、跳日或放置结构的命令，建议先备份存档。本文中的命令不会自动替你建立备份。

## 1. 命令根与权限

| 命令根 | 运行侧 | 权限/限制 | 用途 |
|---|---|---|---|
| `/lobotocraft ...` | 服务端 | 根节点要求权限等级 2 | 模组管理、员工、属性、维护和测试命令 |
| `/facility ...` | 服务端 | 必须由玩家执行；`expansion set` 额外要求权限等级 2 | 打开设施管理/扩建界面 |
| `/exhibition ...` | 服务端 | 权限等级 2 | 展会演示保护与现场恢复 |
| `/lobotocraft exhibition ...` | 服务端 | 权限等级 2 | `/exhibition` 的同功能别名 |
| `/lobotocraft_client ...` | 客户端 | 客户端命令，无服务端权限要求 | 本地伤害边框和 Geburah 刀痕调试显示 |
| `/lobotocraft-client ...` | 客户端 | 客户端命令，无服务端权限要求 | 本地性能统计与日志 |

`/lobotocraft` 的权限要求由根节点统一施加；因此其下的普通功能命令也不能由权限等级低于 2 的来源执行。`/lobotocraft llmconfig` 还要求
`LlmConfigService.CONFIG_PERMISSION_LEVEL = 4`。

## 2. 设施与 AI 员工功能

### 2.1 设施管理与扩建

```text
/facility
/facility expansion
/facility expansion set <department> <level>
```

- `/facility`：在公司维度打开设施管理界面；其他维度会提示先返回公司。打开界面不等于拥有全部管理按钮权限。
- `/facility expansion`：打开部门扩建界面；通过界面确认扩建仍要求权限等级 2。
- `/facility expansion set`：直接设置部门等级，`<level>` 为 `0..5`。部门 ID 使用代码中的英文小写值：
  `control`、`information`、`training`、`safety`、`central`、`welfare`、`disciplinary`、`record`、`extraction`、`architecture`。
- 控制部不能设置为 0，`unassigned` 不能设置扩建等级。`set` 是直接改进度的管理入口，不要求部门原本已开放，也不走正常扩建顺序。

部门对照：`control` 控制部、`information` 情报部、`training` 培训部、`safety` 安保部、`central` 中央本部、`welfare` 福利部、`disciplinary` 惩戒部、`record` 记录部、`extraction` 研发部、`architecture` 构筑部。

实现：`FacilityCommand.java`。

### 2.2 AI 员工部署与指挥

```text
/lobotocraft employee create <name> <department>
/lobotocraft employee move <employee> <x y z>
/lobotocraft employee work <employee> <target> <type>
/lobotocraft employee suppress <employee> <target>
/lobotocraft employee remove <employee>
```

- `create`：由公司维度中的玩家招聘并部署 AI 员工，扣除背包中的 **1 个绿宝石**，初始四维各为 20；不是免费召唤。名称须唯一且不超过 24 个字符。部门必须已开放且未达到容量；可用部门 ID 同上，也可使用 `unassigned`，`central` 对应中央本部上部。
- `move`：给指定 AI 员工下达移动目标。
- `work`：让 AI 员工对异想体执行工作。`<type>` 可用：
  `instinct`（本能）、`insight`（洞察）、`attachment`（沟通）、`repression`（压迫）。命令行请用英文值。
- `suppress`：让 AI 员工镇压指定生物/实体。
- `remove`：删除 AI 员工名册记录及运行实体，释放其 E.G.O 仓库分配；不是临时收回，也没有对应撤销命令。

工作和镇压仍会经过正式的服务端调度校验，不是无条件瞬移或完成。这里的 `work` 只负责普通工作，不是献祭、委托、保护等特殊命令槽的替代入口。

实现：`EmployeeCommand.java`。

## 3. 展会演示控制

以下两种前缀等价：

```text
/exhibition <subcommand>
/lobotocraft exhibition <subcommand>
```

| 命令 | 功能 |
|---|---|
| `/exhibition` 或 `/exhibition status` | 打开状态面板，并显示保护状态与恢复次数 |
| `/exhibition start` | 开启展会保护，执行一次现场恢复并保存世界 |
| `/exhibition recover` | 一键恢复：停止工作/AI 命令、清除控制状态、回收出逃异想体、清理考验与融毁，并恢复人员状态 |
| `/exhibition stop-work` | 停止玩家工作、AI 工作和当前 AI 命令 |
| `/exhibition clear-status` | 清除恐慌、魅惑、冰封等控制状态，并恢复生命与精神 |
| `/exhibition return-abnormalities` | 将出逃异想体回收到收容室 |
| `/exhibition reset-ai` | 清空 AI 员工的命令、战斗状态和寻路状态，让其重新规划 |
| `/exhibition cleanup-events` | 终止考验并清理残留事件实体 |
| `/exhibition gauge` | 按一次普通工作增加一格逆流进度；填满时按正式规则推进警报等级 |
| `/exhibition max-ai` | 将所有已招募 AI 员工的四项属性设为 110 |
| `/exhibition stop` | 关闭展会保护并保存世界 |

这些操作作用于整个服务器的相关状态，不只执行者附近。`clear-status` 恢复的是在线玩家和已部署 AI 员工，会清除其全部药水效果，不能复活已死亡人员；`return-abnormalities` 对终末鸟采用移除遭遇实体的特殊处理。`recover` 本身不改变展会保护开关；`stop` 也不会撤销此前恢复、清理或满级操作。

保护开启时，自动考验和自动融毁暂停；手动演示、普通工作和手动出逃仍可使用。现场操作说明见
[展会操作指南](exhibition-operator-guide.md)。

实现：`ExhibitionCommand.java`、`ExhibitionSafetyService.java`。

## 4. E.G.O 与 LLM 配置

### 4.1 E.G.O 授权

```text
/lobotocraft ego grant-all
/lobotocraft ego grant-all <player>
/lobotocraft ego ai-max
```

- `grant-all`：为执行者或指定玩家授予全部普通 E.G.O 的个人研究/使用授权，不直接向背包发放装备，也不等于解锁全部异想体图鉴。不传玩家参数时只能由玩家执行。
- `ai-max`：将 AI E.G.O 仓库中的全部普通武器与护甲推进到研发上限，并补充对应库存。

这两个命令会直接修改持久化授权或 AI 仓库数据，适合管理员初始化或测试，不建议在正常生存流程中使用。

实现：`EgoAuthorizationCommand.java`。

### 4.2 LLM 配置界面

```text
/lobotocraft llmconfig
```

仅限玩家执行，且需要权限等级 4。命令打开 LLM 配置界面；它本身不执行员工指令，也不授予普通玩家部门访问权。

实现：`LlmConfigCommand.java`、`LlmConfigService.java`。

## 5. 玩家/员工状态与战斗管理

这些是服务端管理员状态工具，属于实际可用功能，但会直接改写目标状态。

### 5.1 精神值

```text
/lobotocraft mentalvalue set <player> <value>
/lobotocraft mentalvalue add <player> <amount>
/lobotocraft mentalvalue get <player>
/lobotocraft mentalvalue setmax <player> <value>
```

`set` 和 `setmax` 不接受负数；`add` 可使用负数扣减精神值。执行后会同步目标客户端。

### 5.2 四色伤害抗性

```text
/lobotocraft resistance red set <player> <value>
/lobotocraft resistance red add <player> <amount>
/lobotocraft resistance red get <player>
```

将 `red` 替换为 `white`、`black` 或 `blue` 可操作其他伤害类型；另有：

```text
/lobotocraft resistance getall <player>
```

- `set` 的范围为 `-1..10`。
- `add` 后会截断到 `-1..10`。
- `set/add/get` 操作或读取基础属性；`getall` 读取带属性修饰器的当前值，因此装备等因素可能使两种查询结果不同。
- `-1` 表示免疫并吸收，`0` 表示完全免疫，`1` 为正常伤害，超过 `1` 为易伤。
- 代码中的 `blue` 在状态文本中显示为 PALE。

### 5.3 员工四维

```text
/lobotocraft employeestats set <player> <fortitude|prudence|temperance|justice> <value>
/lobotocraft employeestats add <player> <fortitude|prudence|temperance|justice> <amount>
/lobotocraft employeestats get <player>
```

`set` 的输入范围为 `20..110`；`add` 可为负数，基础值限制在同一范围内。四项分别对应勇气、谨慎、自律、正义，修改后重新应用属性效果。Hod 等临时减益可能让显示的有效值低于基础值。

### 5.4 直接伤害、清理和传送

```text
/lobotocraft attack <targets> <red|white|black|blue> <amount>
/lobotocraft kill
/lobotocraft tpentity <entityType>
```

- `attack`：向选中的生物实体调用指定颜色的伤害管线，输入最低为 `0.1`。仍受抗性、无敌和目标自身规则影响；反馈中的数量是尝试处理数量，不保证每个目标实际扣血。
- `kill`：**扫描所有维度已加载的生物**，不限距离、不限敌对。排除玩家（含 AI 员工）、`EntityClerk` 文职、`EntityCoreSuppressionNpc` 及未出逃的 `AbstractAbnormality`；其他生物包括普通动物、村民等也会被加入队列。每 Tick 最多调用 64 次 `9999` 点 `genericKill` 伤害，不是无条件删除实体。清场风险很高。
- `tpentity`：仅限玩家执行，在当前维度逐步扩大 50..600 格的方形搜索范围，寻找所匹配类型的已加载实体并传送到附近。类型采用别名/注册路径/描述键的模糊匹配，取第一个匹配类型；建议用 Tab 给出的路径名，例如 `cow` 或 `vacuole`，不要假定支持完整的 `namespace:id`。附近安全点搜索失败时会回退到目标旁坐标，因此并不保证完全安全。

这三个命令可能改变战斗或世界状态，应只用于管理员操作、复现和清场。

实现：`MentalValueCommand.java`、`ResistanceCommand.java`、`EmployeeStatsCommand.java`、`AttackCommand.java`、`KillCommand.java`、`TeleportToEntityCommand.java`。

## 6. 维护与流程快捷命令

```text
/lobotocraft reload
/lobotocraft completedailywork
/lobotocraft completecoresuppression
```

- `reload`：重置 Lobotomy Corporation 维度中的文职日状态，包括清墓碑、状态重置和补充/移除文职。**不是重载模组配置、资源或数据包**。
- `completedailywork`：仅限玩家执行，直接将当天工作次数补到要求值；不会替玩家睡觉或推进日期。
- `completecoresuppression`：为当前正在进行的非 Boss 核心抑制补齐能源和融毁等级条件，不直接调用成功结算。当前适用于 Malkuth、Yesod、Hod、Netzach、Chesed；Geburah 等需要 Boss 阶段的类型以及尚未实装类型会被拒绝。

后两个命令是管理/测试快捷方式，不属于普通玩家流程。

实现：`ReloadCommand.java`、`CompleteDailyWorkCommand.java`、`CompleteCoreSuppressionCommand.java`。

## 7. 客户端本地命令

### 7.1 伤害边框与 Geburah 刀痕

```text
/lobotocraft_client damage_border
/lobotocraft_client damage_border <true|false>
/lobotocraft_client geburah_trail_markers
/lobotocraft_client geburah_trail_markers <true|false>
/lobotocraft_client geburah_trail_surface
/lobotocraft_client geburah_trail_surface <true|false>
```

不带布尔参数时读取当前状态；带参数时修改当前客户端显示。`damage_border` 是伤害边框开关；`geburah_trail_markers` 显示刀痕调试标记，`geburah_trail_surface` 控制刀痕表面绘制。后两者主要用于视觉调试。只影响执行命令的客户端，不改变服务端战斗逻辑。

### 7.2 客户端性能统计

```text
/lobotocraft-client performance
/lobotocraft-client performance status
/lobotocraft-client performance dump
/lobotocraft-client performance reset
/lobotocraft-client performance path
```

- `performance` / `status`：显示慢帧、慢 Tick、FPS、实体数、区块数、当前界面和监测分段。
- `dump`：将当前性能摘要排队写入客户端日志，同时清零该周期的慢帧/慢 Tick 数量及耗时峰值。
- `reset`：清空当前统计周期的计数器与分段统计，不删除旧日志。
- `path`：显示日志文件路径，默认是 `logs/lobotocraft-client-performance.log`。

这些命令是本地客户端命令，联机时不会发送到服务器。

实现：`LobotocraftClientCommand.java`、`ClientDebugCommand.java`。

## 8. `debug` 下有实际操作功能的管理命令

下面全部要求权限等级 2。虽然归在 `debug` 下，它们会真实修改游戏状态，不是只输出日志。只读查询集中在第 9 节。

### 8.1 工作会话

| 完整命令 | 功能与限制 |
|---|---|
| `/lobotocraft debug work start <player> <target> <type>` | 为指定玩家启动正式普通工作；`type` 同第 2.2 节，仍检查距离、登记、精神、占用等条件 |
| `/lobotocraft debug work cancel <player>` | 取消已有正式工作会话 |
| `/lobotocraft debug work force-complete <player> <result>` | 请求把已有工作按指定结果强制结算；`result` 为 `good`、`normal`、`bad` |
| `/lobotocraft debug work stop-continuous <player>` | 请求停止连续工作，不等同于立刻取消当前正式会话 |

### 8.2 AI 员工

| 完整命令 | 功能与限制 |
|---|---|
| `/lobotocraft debug employee cancel <employee>` | 取消可取消的 AI 待命令；已进入正式工作或不可取消状态时拒绝 |
| `/lobotocraft debug employee persist <employee>` | 立即把已部署员工的运行状态写回持久化记录 |
| `/lobotocraft debug employee spawn <employee>` | 按保存位置重新部署已有、未部署且未死亡/退休的员工；不是招聘新员工或复活 |
| `/lobotocraft debug employee move <employee> <x y z>` | 下达移动任务；此版本要求目标方块已加载 |
| `/lobotocraft debug employee work <employee> <target> <type>` | 下达普通工作任务，沿用正式校验 |
| `/lobotocraft debug employee suppress <employee> <target>` | 下达镇压任务，目标须为生物 |
| `/lobotocraft debug employee follow <employee> <target>` | 下达跟随实体任务 |
| `/lobotocraft debug employee tool <employee> <target>` | 对工具型异想体下达使用任务 |

`move/work/suppress` 与顶层 `employee` 命令是功能重叠的入口；`follow/tool/cancel/persist/spawn` 当前只有此调试树提供。

### 8.3 异想体与收容室

| 完整命令 | 功能与限制 |
|---|---|
| `/lobotocraft debug abnormality qliphoth <target> <value>` | 设置单个异想体的逆卡巴拉计数器；输入整数 ≥0，不自动调用出逃 |
| `/lobotocraft debug abnormality escape <target>` | 调用目标的正式出逃流程，是否能出逃取决于该异想体规则 |
| `/lobotocraft debug abnormality return <target>` | 调用目标的正式停止出逃/回收流程 |
| `/lobotocraft debug abnormality heal <target>` | 将目标生命值设为最大值，不改变计数器或出逃状态 |
| `/lobotocraft debug room scan [x y z]` | 立即扫描收容室信标，可能更新登记，不是只读查询 |
| `/lobotocraft debug room bind <x y z> <target>` | 将信标绑定到指定异想体，检查目标、维度和收容单元开放状态 |
| `/lobotocraft debug room clear <x y z>` | 请求清除该信标的绑定，不删除异想体实体 |

`room scan` 省略坐标时用执行玩家所在方块坐标，不是准星指向的方块；控制台必须传坐标。其余坐标也均指信标所在位置。

### 8.4 电梯

```text
/lobotocraft debug elevator try <x y z> <up|down>
```

玩家实际尝试乘坐该电梯，执行交互距离、冷却、目的地和落点碰撞校验；只能由玩家执行。只检查路线请使用第 9 节的 `elevator route`。

### 8.5 设施日程、融毁、部门与外出计数

| 完整命令 | 功能与参数 |
|---|---|
| `/lobotocraft debug facility next-day [count]` | 直接调用开日流程，推进 `count` 天；默认 1，范围 `1..50`。绕过正常结束日条件，不是补工作次数 |
| `/lobotocraft debug facility energy <value>` | 设置设施能源，整数 ≥0，并同步 |
| `/lobotocraft debug facility gauge <value>` | 直接设置设施融毁条进度，整数 ≥0；本次命令不主动执行阶段触发 |
| `/lobotocraft debug facility gauge add <amount>` | 增加 `1..1000` 格进度；达到阈值会走正式阶段触发流程 |
| `/lobotocraft debug facility trigger-stage` | 填满当前进度并尝试触发当前阶段，可产生融毁或考验；不保证每次都有实体生成 |
| `/lobotocraft debug facility stage add <amount>` | 重复上述阶段推进 `1..10` 次；不能继续推进时停止 |
| `/lobotocraft debug facility clear-meltdowns` | 解除当前所有活跃融毁，不改设施日期或进度条 |
| `/lobotocraft debug facility sync` | 向在线玩家重新同步设施状态 |
| `/lobotocraft debug department set <department> <level>` | 直接设置部门等级 `0..5`；限制同 `/facility expansion set`，但此入口也可由控制台执行 |
| `/lobotocraft debug counter set <player> <value>` | 设置外出抑制器计数，整数 ≥0 |
| `/lobotocraft debug counter add <player> <amount>` | 增减外出计数，允许负整数 |
| `/lobotocraft debug counter full <player>` | 按当日上限回满外出计数 |

三个 `counter` 写操作均按当前上限截断并同步 HUD。`counter`（外出计数）、`abnormality qliphoth`（单体计数器）、`facility gauge`（设施融毁条）是三个不同数值，不要混用。

### 8.6 考验与核心抑制

```text
/lobotocraft debug ordeal trigger <tier> [color]
/lobotocraft debug suppression start <type>
/lobotocraft debug suppression end <true|false>
/lobotocraft debug suppression sync
```

考验参数：

| `tier` | 解锁日 | `color` / 当前行为 |
|---|---|---|
| `dawn`（黎明） | Day 3 | `amber`、`crimson`、`green`、`violet`；省略颜色为 `amber` |
| `noon`（正午） | Day 11 | `crimson`、`blue`（也接受 `indigo`）、`violet`、`green`；省略颜色为 `crimson` |
| `dusk`（黄昏） | Day 21 | 此触发入口只显示“未完工”提示，不生成黄昏战斗；颜色参数不参与选择 |
| `midnight`（午夜） | Day 26 | 此触发入口只显示“未完工”提示，不生成午夜战斗；颜色参数不参与选择 |

手动触发仍检查当天解锁和同等级考验是否正在运行，实际生成还取决于生成位置。黄昏/午夜可能返回已触发的反馈，但其含义只是显示占位提示，不能当成已有可玩的考验。

核心抑制：

- `start` 当前可用类型为 `malkuth`、`yesod`、`hod`、`netzach`、`chesed`、`geburah`。`tiphereth`、`binah`、`hokma` 虽有类型定义，尚未实装，不能启动。
- `start` 只能由玩家执行，是强制挑战入口，但仍检查 AI 配置等条件并等待全员视觉安全确认。真正启动后会重置挑战进度、清理正在运行的考验并将在线玩家传送回公司。
- `end true` 不检查完成条件，直接按成功结算，会写入完成状态、发放相应进度并触发清场；`end false` 按失败结束。两者都要求当前已有活跃挑战。
- `sync` 仅重发核心抑制状态。

本节实现：`DebugCommand.java` 及其调用的设施/员工服务。

## 9. 只读诊断与状态同步入口

主要用于定位问题，不列为日常玩法命令。省略 `[player]` 时查询执行玩家，控制台必须显式指定玩家。

| 完整命令 | 查询内容 |
|---|---|
| `/lobotocraft debug` 或 `/lobotocraft debug help` | 显示调试分组帮助 |
| `/lobotocraft debug status [player]` | 坐标、维度、生命/精神、四维、当天工作和正式工作会话等总状态 |
| `/lobotocraft debug work list` | 当前在线玩家列表中的正式工作会话 |
| `/lobotocraft debug work status [player]` | 工作目标、类型、进度、成功/失败次数和连续工作状态 |
| `/lobotocraft debug employee list` | AI 员工名册、ID、部门、任务和部署状态 |
| `/lobotocraft debug employee status <employee>` | 指定 AI 员工记录与运行状态 |
| `/lobotocraft debug abnormality list [radius]` | 当前维度附近存活异想体；默认范围 128，允许 `1..2048`，最多显示 128 个 |
| `/lobotocraft debug abnormality status <target>` | 异想体生命、出逃、工作和登记状态 |
| `/lobotocraft debug abnormality register-check <target>` | 正式登记校验结果及 home 信标信息 |
| `/lobotocraft debug room status [x y z]` | 信标绑定和扫描范围 |
| `/lobotocraft debug elevator status [x y z]` | 电梯设置；省略坐标用玩家所在方块 |
| `/lobotocraft debug elevator route <x y z> <direction>` | 检查 `up` 或 `down` 方向的目的电梯，不传送玩家 |
| `/lobotocraft debug facility status` | 日期、能源、融毁条、融毁等级与考验预定表 |
| `/lobotocraft debug facility requirements [player]` | 列出当天无法结束的具体原因；查询前会同步玩家设施日数据，不负责补齐条件 |
| `/lobotocraft debug counter status [player]` | 外出抑制器计数和上限 |
| `/lobotocraft debug ordeal status` | 各类考验的触发/完成记录及追踪实体数 |
| `/lobotocraft debug suppression status` | 当前核心抑制、挑战归属和完成条件 |
| `/lobotocraft debug department status` | 部门等级、开放、容量与扩建资格 |
| `/lobotocraft debug ego status [player]` | 装备、投影标记、等级需求、AI 仓库分配 |
| `/lobotocraft debug performance [path]` | 提示客户端日志路径和卡顿阈值；不采集服务端性能 |

`room status` 的默认坐标规则与 `room scan` 相同。同步操作 `facility sync`、`suppression sync` 见第 8 节；客户端性能诊断见第 7.2 节。

## 10. 结构工具、战斗调试与视觉原型

这些入口仍有注册，但不当作正常玩法功能。

### 10.1 结构加载工具 `test`

尽管命名为 `test`，它实际上是**放置结构**，不是执行自动化测试。完整语法：

```text
/lobotocraft test <structure>
/lobotocraft test <structure> autoselect
/lobotocraft test <structure> <x y z>
/lobotocraft test <structure> <x y z> autoselect
/lobotocraft test <structure> <x y z> <rotation>
/lobotocraft test <structure> <x y z> <rotation> autoselect
```

`structure` 是结构加载器使用的名称，需有对应资源；省略坐标使用命令来源当前位置，默认旋转为 `none`。`rotation` 可为 `none`、`clockwise_90`、`clockwise_180`、`counterclockwise_90`。

`autoselect` 在加载完成后尝试把范围写入玩家主/副手的结构保存工具，没有工具只提示无法圈选，不会撤销加载。结构会影响目标区域方块，应在测试存档或备份后使用；控制台可加载结构，但没有玩家工具可供自动圈选。

### 10.2 Geburah 战斗参数

以下命令作用于核心抑制服务追踪的活跃 Geburah 遭遇实体，不能通过目标选择器任选实体：

```text
/lobotocraft debug suppression geburah status
/lobotocraft debug suppression geburah damage <amount>
/lobotocraft debug suppression geburah set health <value>
/lobotocraft debug suppression geburah set posture <value>
/lobotocraft debug suppression geburah set ego <value>
/lobotocraft debug suppression geburah set phase <normal|ego|exhausted>
/lobotocraft debug suppression geburah set ego_cooldown <ticks>
/lobotocraft debug suppression geburah set combo_depth <value>
/lobotocraft debug suppression geburah set kill_bonus <value>
/lobotocraft debug suppression geburah set cooldown <move> <ticks>
```

- `status` 只读；其余命令直接影响战斗状态。
- `damage` 最低 `0.01`，调用专用调试伤害入口；`health` ≥0。测试最终击败应使用 `damage`，不能通过 `phase defeated` 伪造。
- `posture`、`ego` 为 `0..100`；`combo_depth` 为 `0..6`；`kill_bonus` 为 `0..5`。
- 两类冷却 `ticks` 均为非负整数；正常 20 TPS 下 20 Tick 约为 1 秒。`move` 是内部招式键，例如 `willofbattle`、`ego_willofbattle`、`bloodmist`、`egobloodmist`、`bodieseverywhere`。

另有独立刀痕测试实体入口，只能由玩家执行：

```text
/lobotocraft debug suppression geburah trail play <animation> <yaw> [speed]
/lobotocraft debug suppression geburah trail clear
```

`play` 在玩家前方创建或复用带测试标记的 Geburah，关闭其 AI 来播放动画；不要求正在进行核心抑制。`animation` 区分大小写，可用 `slash`、`stab`、`verticalslash`、`willofbattle`、`EGOslash`、`EGOstab`、`EGOverticalslash`、`EGOwillofbattle`。`yaw` 为 `-180..180`，`speed` 为 `0.05..2.0`，默认 `0.12`。`clear` 移除当前玩家 256 格范围内带该测试标记的实体，不用于结束正式遭遇。

### 10.3 憎恶女王视觉预览

```text
/lobotocraft queen_laser
/lobotocraft queen_laser preview
/lobotocraft queen_laser max
/lobotocraft queen_laser clear
/lobotocraft queen_star
/lobotocraft queen_star original
/lobotocraft queen_star clear
```

两个不带子命令的入口均显示帮助。`preview/max` 沿玩家视线播放普通/加强激光预览，`original` 播放星弹预览，`clear` 清除相应效果。播放要求玩家瞄准，效果广播给同维度玩家；这些命令本身只发视觉效果包，不造成战斗伤害，也不启动真实女王的攻击生命周期。

## 11. 常用示例

以下示例分别执行，不是需要连续运行的一套流程：

```mcfunction
/facility
/lobotocraft debug facility requirements @s
/lobotocraft mentalvalue get @s
/lobotocraft employeestats set @s fortitude 110
/lobotocraft ego grant-all @s
/lobotocraft debug counter full @s
/lobotocraft debug employee list
/lobotocraft_client damage_border false
/lobotocraft-client performance dump
```

当前没有注册 `/lobotocraft facility`、`/lobotocraft pause`、`/lobotocraft sacrifice` 等入口：设施面板用独立 `/facility`；暂停及献祭/委托/保护走相应界面，不要把 Java 中的“特殊命令”误认为聊天斜杠命令。

## 12. 源码注册总表与覆盖范围

| 命令族 | 注册实现 |
|---|---|
| `/lobotocraft ...` | [CommandRegistry.java](../src/main/java/com/wzz/lobotocraft/command/CommandRegistry.java) |
| `/facility ...` | [FacilityCommand.java](../src/main/java/com/wzz/lobotocraft/command/FacilityCommand.java) |
| `/exhibition ...` 与 `/lobotocraft exhibition ...` | [ExhibitionCommand.java](../src/main/java/com/wzz/lobotocraft/command/ExhibitionCommand.java) |
| `/lobotocraft debug ...` | [DebugCommand.java](../src/main/java/com/wzz/lobotocraft/command/DebugCommand.java) |
| `/lobotocraft_client ...` | [LobotocraftClientCommand.java](../src/main/java/com/wzz/lobotocraft/client/command/LobotocraftClientCommand.java) |
| `/lobotocraft-client ...` | [ClientDebugCommand.java](../src/main/java/com/wzz/lobotocraft/client/command/ClientDebugCommand.java) |

`/lobotocraft` 当前注册的 17 个一级子命令均已覆盖：`mentalvalue`、`resistance`、`attack`、`kill`、`employeestats`、`test`、`tpentity`、`completedailywork`、`completecoresuppression`、`reload`、`employee`、`llmconfig`、`ego`、`exhibition`、`debug`、`queen_laser`、`queen_star`；另覆盖上述独立服务端入口与两套客户端命令。

旧的 [DEBUG_COMMANDS.md](../DEBUG_COMMANDS.md) 可作历史参考，但没有覆盖新增的全部调试子命令。本文以当前注册树和执行逻辑为依据；以后源码更新时仍应以源码和游戏内补全为准。本次仅整理文档，未修改命令实现，未启动游戏执行命令或运行全量测试。
