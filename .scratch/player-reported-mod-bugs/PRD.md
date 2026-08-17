# 玩家反馈模组问题诊断

状态：`ready-for-human`

## 诊断范围

针对当前存在未提交修改的工作树，判断玩家和测试人员反馈是否属实，并定位根因。诊断完成后，已修复全部五项确认缺陷，并保留永久回归测试。工作树中已有的用户修改均视为有效内容，不得回退。

## 证据标准

- `confirmed`：确定性测试或诊断工具复现了反馈中的症状。
- `current-tree-fixed`：已覆盖反馈中的准确症状，且该症状无法在当前工作树复现。
- `likely`：生产调用路径中确定存在反馈所述的不一致，但尚无端到端运行时复现。
- `unverified`：尚未建立可信的反馈循环。
- `needs-info`：缺少存档、日志、视频或更准确的复现步骤，无法区分缺陷与预期设计。
- `design-matches`：反馈中的行为与当前实现及当前百科文本一致。

## 问题列表

| ID | 反馈 | 诊断方法 | 当前结论 |
| --- | --- | --- | --- |
| BUG-01 | 惩罚鸟返回收容单元时，Y 坐标持续偏低。 | `PunishingBirdReturnPositionTest` 及收容返回调用路径。 | `current-tree-fixed` |
| BUG-02 | 碧蓝新星在员工进入附近时杀死低勇气/谨慎员工，随后模型或动画损坏。 | 低属性员工邻近 GameTest，同时断言死亡结果与最终动画状态。 | `current-tree-fixed` |
| BUG-03 | 碧蓝新星出逃后仍留在收容单元内。 | 使用真实收容绑定的 GameTest，断言实体实际移动到映射出口。 | `current-tree-fixed` |
| BUG-04 | 佩戴大坏狼饰品的员工对小红帽完成工作后，小红帽的逆卡巴拉计数器没有减少。 | 对照百科描述审计工作完成调用路径。 | `current-tree-fixed` |
| BUG-05 | 大坏狼首次成功吞噬员工并出逃后，模型或动画损坏。 | 首次吞噬到出逃的状态转换 GameTest，以及渲染器和资源审计。 | `current-tree-fixed` |
| BUG-06 | 大坏狼施放无敌技能的次数过多。 | 审计伤害阈值、治疗及技能状态调用路径。 | `needs-info` |
| BUG-07 | 伊莎玛拉出逃后，伊莎玛拉之泪只有被攻击后才会反击。 | 对照百科文本审计泪的 AI 与攻击入口。 | `design-matches` |
| BUG-08 | 蕾蒂希娅赠送礼物后，玩家自杀不会生成礼物怪物。 | 对照百科文本审计礼物事件的完整状态顺序。 | `design-matches` |
| BUG-09 | Malkuth 的工作类型打乱按部门分别生成，而预期应使用一份全局映射。 | `MalkuthWorkPolicyTest` 及服务器状态所有权和调用点审计。 | `current-tree-fixed` |
| BUG-10 | 核心抑制期间，收容单元会阻止释放异想体，但仍允许回收异想体。 | 对比捕获和释放两条调用路径。 | `current-tree-fixed` |
| REG-05 | 开启立即重生后，进入“我们可以改变一切”会导致无限工作、反复死亡和大量文职生成。 | 使用相同 UUID 和相同实体 ID 的重生 GameTest，检查工具会话所有权与死亡清理。 | `current-tree-fixed` |
| BUG-12 | 镇压伊莎玛拉后，装备被放入背包，而不是获得 E.G.O 授权。 | 审计镇压奖励路径，区分物品发放与 E.G.O 授权。 | `current-tree-fixed` |

## 最终诊断

| ID | 事实与根因 | 置信度 | 建议方向 |
| --- | --- | --- | --- |
| BUG-01 | 当前返回目标是巢穴上方的空气方块。最终返回收容单元时，会使用出逃前的收容位置并执行安全位置解析。当前工作树无法复现 Y 坐标偏低。 | 高 | 保留当前返回位置策略，将已有运行时返回断言升级为永久端到端回归测试。 |
| BUG-02 | 已复现。`killMob` 在验证目标和计时器之前就切换到攻击动画。下一次扫描仍可能找到已经死亡但尚未移除的员工，随后尝试启动无效计时器；计时器取消时没有恢复待机动画。 | 高 | 调度前排除死亡目标；仅在计时器成功启动后进入攻击动画；所有取消和结束路径都必须恢复待机动画。 |
| BUG-03 | 当前工作树通过了真实房间绑定和映射出口位移测试。旧实现只有在附近找到反应堆时才传送；当前实现将流程交给共享的 `beginEscape`，没有反应堆时会回退到收容出口。 | 高 | 保留共享出逃事务，并增加永久的准确位置回归测试。 |
| BUG-04 | 已确认代码与百科不一致。小红帽只在普通背包中查找 `SHEEPSKIN_CURIO`，但百科要求正常的 F-02-58 饰品；实际佩戴的饰品位于 Curios 槽。 | 高 | 通过 Curios 装备 API 检查 `BIG_BADWOLF_CURIO`，并增加反例测试，避免背包中的“羊皮”错误满足条件。 |
| BUG-05 | 首次吞噬会正确选择 `bigbadwolf_swallowed` 和 `idle2`；出逃会正确切换为 `bigbadwolf_big` 和 `idle`。当前无法复现原反馈。此前的渲染器修复已将陈旧的渲染器实体引用替换为 GeckoLib 当前正在渲染的实体。 | 高 | 保留当前渲染器修复，并将临时的首次吞噬到出逃测试转为永久回归测试。 |
| BUG-06 | 每累计损失 300 点生命值触发一次技能，技能持续 160 tick，结束后没有额外冷却。大坏狼拥有 2000 点生命值，因此一次出逃在不重置的情况下最多触发 6 次。反馈没有提供预期次数或冷却时间。 | 对实际行为判断为高；产品规格不足 | 先确认预期的触发上限或冷却时间，不能仅依据“次数过多”修改机制。 |
| BUG-07 | 伊莎玛拉之泪被明确实现为固定位置的反击型召唤物：没有自主索敌目标，只从 `hurt()` 进入攻击。百科也没有说明它会主动攻击。 | 高 | 除非设计发生变化，否则不修复。若要改为主动攻击，应先明确索敌规则、范围、攻击频率及其与 25 秒生命周期的关系。 |
| BUG-08 | 文档规定的顺序是：第一次对其他异想体工作时礼物损坏；再次对其他异想体工作时生成朋友并杀死员工。玩家自杀没有生成路径，重生还会清除损坏礼物。 | 高 | 当前应视为反馈者对机制的误解。除非明确修改设计，否则只需增加完整流程测试固定现有行为。 |
| BUG-09 | Malkuth 在主世界的 `CoreSuppressionData` 中只保存一份映射。真人员工与 AI 员工的工作路径都读取同一份全局映射，不存在部门键。 | 高 | 保留全局状态所有权，并增加跨两个部门、同时覆盖真人和 AI 员工的集成测试。 |
| BUG-10 | 已确认限制不对称。释放路径会检查 `isDeviceRestricted`，但实际捕获发生在 `ForgeModEvent.onPlayerRightEntity` 中；该路径直接序列化并移除异想体，没有执行限制检查。 | 高 | 在真实捕获边界执行限制检查，最好提取为捕获和释放路径共同使用的策略。 |
| REG-05 | 已按真实重生身份特征复现。Minecraft 重生会保留 UUID 和运行时实体 ID，因此替换后的玩家能通过铁处女的 `UUID + entityId` 所有权检查并继承死亡玩家的会话。每次重复死亡又会独立生成更多文职。 | 高 | 在玩家死亡、重生前结束工具会话；不要再把运行时实体 ID 当作生命周期令牌，应使用显式的会话代次或会话令牌。 |
| BUG-12 | 已确认 API 使用错误。伊莎玛拉死亡时调用 `MustOpenPathRewardService`，该服务通过 `player.addItem` 发放整套物品，完全没有写入玩家个人 E.G.O 研究或授权数据。 | 高 | 使用现有授权服务授予玩家个人武器和护甲授权；饰品奖励应单独确定规则并测试。 |

## 已实施修复方案

| ID | 实际修改 | 永久回归测试 |
| --- | --- | --- |
| BUG-02 | `EntityBlueStar.killMob` 现在会在调度前拒绝死亡或已移除目标；只有 `TimerEntry` 成功启动后才切换到攻击动画；计时器取消时统一恢复待机动画。这样后续扫描即使仍能看到待移除玩家，也不会重新污染动画状态。 | `blueStarLowTemperanceExecutionRestoresIdle`：真实等待处决完成和下一次扫描，断言员工死亡且碧蓝新星回到 `animation.blue_star.idle`。 |
| BUG-04 | `EntityRedHoodMercenary.onWorkComplete` 不再扫描普通背包中的 `SHEEPSKIN_CURIO`，改为通过 `CuriosUtil.hasCurios` 检查已佩戴的 `BIG_BADWOLF_CURIO`。 | `redHoodRequiresEquippedBigBadWolfAccessory`：先断言背包“羊皮”无效，再断言 Curios 中的大坏狼饰品使计数器减少 1。 |
| BUG-10 | `ForgeModEvent.onPlayerRightEntity` 在任何目标序列化和移除之前调用 `CoreSuppressionManager.isDeviceRestricted`；受限时设置 `InteractionResult.FAIL`、取消事件并立即返回。 | `coreSuppressionBlocksCaptureAtForgeEventBoundary`：在设施维度开启核心抑制，直接调用真实 Forge 事件，断言目标未移除且捕捉单元未写入实体 NBT。 |
| REG-05 | `IronMaidenSacrificeCleanup` 新增操作员死亡处理：玩家死亡事件发生时扫描当前维度中的铁处女，并调用 `endUsageForOperatorDeath` 立即进入结束流程、清空会话身份。当前仍保留 `UUID + entityId` 作为运行中身份校验；死亡边界负责阻止重生实体继承会话。本次没有增加持久化会话令牌，因为同步死亡事件已是更小且足够的修复边界。 | `ironMaidenDoesNotRecognizeRespawnedEntityAsSameSession`：构造同时继承 UUID 和实体 ID 的替换玩家，先派发死亡事件，再断言原会话已结束且替换玩家无法继承。 |
| BUG-12 | `MustOpenPathRewardService` 现在委托 `EgoAuthorizationService.grantSuppressionReward` 写入玩家个人武器和护甲授权，并继续维护全局 AI 研究库存；背包只直接获得设计明确的镇压饰品奖杯。 | `suppressionRewardsPersonalAuthorizationInsteadOfBackpackEquipment`：断言个人武器和护甲研究已写入，背包中的该套奖励物品只有 1 件饰品。 |

## 修复验证

- 修复前，近期修复命名空间新增的 4 个测试全部按预期失败，分别命中 BUG-02、BUG-04、BUG-10 和 REG-05。
- 修复前，必须开辟的道路命名空间新增测试在“未授予个人授权”断言上失败，命中 BUG-12。
- 修复后，`lobotocraft_recent_fixes` 共 `27/27` 个必需 GameTest 通过。
- 修复后，`lobotocraft_must_open_path` 共 `8/8` 个必需 GameTest 通过。
- `./gradlew test` 完整 JUnit 测试通过。
- `compileJava` 与 `processResources` 通过；未留下 `[DEBUG-...]` 日志或临时诊断探针。

## 已有证据

- `PunishingBirdReturnPositionTest` 已通过；当前位置策略使用巢穴上方的目标方块。
- 临时碧蓝新星位置 GameTest 使用了真实收容绑定，并与生命周期测试同时运行；两个必需测试全部通过，实体到达映射出口。证据记录后已删除临时探针。
- `MalkuthWorkPolicyTest` 已通过；当前映射保存在主世界的 `CoreSuppressionData` 中，确认其所有权为全局状态。完整多人和跨部门流程仍可补充集成测试。
- 小红帽的工作完成代码只在普通背包中检查 `SHEEPSKIN_CURIO`；百科要求正常的 F-02-58 饰品 `BIG_BADWOLF_CURIO`，且代码没有检查 Curios 佩戴槽。
- `REG-05` 在替换玩家同时继承旧 UUID 和旧实体 ID 时稳定复现，这与 Minecraft 1.20.1 的 `PlayerList.respawn` 行为一致。`EntityIronMaiden` 会将替换玩家识别为原操作员。每次玩家死亡又会调用 `ClerkEvent.respawnClerksAfterPlayerDeath`，因此文职数量会持续增长。
- `BUG-02` 在低属性员工死亡后稳定复现。下一次邻近扫描仍包含已经死亡但尚未移除的玩家。`killMob` 在验证目标或计时器能否启动之前就将碧蓝新星设置为 `attack`；无效计时器被取消时不会恢复待机动画。
- 临时大坏狼 GameTest 通过了准确的贴图、动画、旁观摄像机和吞噬玩家记录断言。将探针缩小到原反馈范围后，该命名空间的 25 个必需测试全部通过；随后已删除临时探针。

## 工作假设

以下内容是诊断过程中用于构造反馈循环的假设；最终结论以“最终诊断”章节为准。

1. 部分反馈来自旧版本，相关缺陷已在当前未提交工作树中修复。预期现有目标测试通过，而准确模拟旧入口的测试只会在旧实现上失败。
2. 某些实体生命周期转换绕过了共享的出逃或死亡清理。预期问题只会通过工作死亡、吞噬、立即重生或镇压入口出现，而直接调用 `triggerEscape` 的测试保持通过。
3. 装备和奖励代码使用了错误的所有权载体。预期 Curios 槽中的装备与普通背包物品表现不同，伊莎玛拉镇压路径会调用物品发放 API，而不是授权服务。
4. AI 激活由受伤事件驱动，而不是由出逃状态驱动。预期伊莎玛拉之泪只有在受伤事件修改战斗状态后才会攻击。
5. 会话身份错误地绑定到玩家实体标识，而不是稳定的生命周期令牌。预期立即重生的相同 UUID 玩家会继承死亡实体的工具或工作会话，并重复触发副作用。

## 诊断记录

- 2026-08-16：从本地 Codex 会话 `019fd12c-7c9d-7820-81c9-c30127e4e145` 恢复诊断。
- 2026-08-16：临时相同运行时实体 ID 的 GameTest 失败，错误为 `same-UUID respawn entity inherited the dead operator's session`；记录证据后已删除单行探针。
- 2026-08-16：临时低属性碧蓝新星 GameTest 杀死了员工，并以 `Blue Star did not restore idle animation after the execution` 失败；记录证据后已删除探针。
- 2026-08-16：临时碧蓝新星绑定出口探针及生命周期测试全部通过，共 `2/2` 个必需测试；临时 Java 和 SNBT 文件均已删除。
- 2026-08-16：临时大坏狼首次吞噬到出逃探针随完整近期修复命名空间运行，共 `25/25` 个必需测试通过；随后已删除探针。
- 2026-08-16：为 BUG-02、BUG-04、BUG-10、REG-05 和 BUG-12 添加永久回归测试；修复前共得到 5 个准确失败。
- 2026-08-16：实施五项修复后，近期修复命名空间 `27/27`、必须开辟的道路命名空间 `8/8` 全部通过，完整 JUnit 通过；状态更新为 `ready-for-human`。
