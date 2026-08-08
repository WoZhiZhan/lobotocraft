# Lobotocraft 可维护性与 Bug 审查交接

> 审查日期：2026-07-29
> 审查基线：`9d26841` 加当前工作区未提交改动
> 用途：交给后续开发者实施修复，不是发布说明。
> 状态：本文只记录审查结论；审查过程未修改业务代码。

## 1. 范围和约束

本轮覆盖服务端信任边界、网络包、工作系统、E.G.O 武器、异想体生命周期、出逃、天数、核心抑制融毁、Mixin、资源和构建配置。

明确不审查以下正在开发的内容：

- A* / Pathfinder；
- 电梯寻路、跨楼层路径规划和对应导航测试；
- 由上述未完成代码直接导致的临时行为。

现有测试报告为 47 个测试套件、152 个测试，失败 0、错误 0、跳过 0。测试通过不代表下列问题不存在：多数问题发生在 Forge 事件优先级、恶意客户端发包、多人同步、实体卸载、真实时间计时和世界方块副作用中，现有单元测试没有覆盖这些组合。

工作区原本有大量未提交改动。修复人员必须基于当前文件逐项合并，不能使用 `git reset --hard`、`git checkout --` 等方式清掉现有改动。

## 2. 优先级总览

| ID | 优先级 | 子系统 | 结论 |
|---|---|---|---|
| SEC-01 | P0 | 网络/饰品 | 客户端可无限生成 E.G.O 饰品 |
| SEC-02 | P0 | 网络/图鉴 | 解锁费用和条目均由客户端决定，可零成本解锁 |
| DAY-01 | P0 | 天数/核心抑制 | 可取消事件先写世界状态，核心抑制失败仍可能换天 |
| ABN-01 | P0 | 实体/世界 | 跟随光源沿路永久遗留隐形光源方块 |
| SEC-03 | P1 | 工作 | 直接工作包绕过统一入口和开屏事件 |
| NET-01 | P1 | 监控 | 监控完整快照请求无节流，可用于拖垮主线程和网络 |
| COMBAT-01 | P1 | 战斗 | 终末鸟全服攻击会伤到其他维度玩家 |
| ABN-02 | P1 | 出逃 | 碧蓝新星绕过公共出逃生命周期 |
| DAY-02 | P1 | 多人换天 | 新一天副作用只发给一个投票执行者 |
| MELT-01 | P1 | 融毁 | 没有目标仍警报并消耗融毁阶段 |
| EGO-01 | P1 | E.G.O | 等级不足时仍可能退回原版近战伤害 |
| EGO-02 | P1 | E.G.O | 丢弃键可清除公共武器冷却 |
| EGO-03 | P1 | E.G.O | 真实时间计时器不服从服务器 tick，且状态不可恢复 |
| ABN-03 | P1 | 出逃警报 | 警报是全 JVM 临时整数，重启/切服后失真 |
| MELT-02 | P2 | 融毁 | 候选可包含计数器为 0 或特殊禁止目标 |
| MELT-03 | P2 | 融毁 | 目标卸载后静默扣能源但不执行实体惩罚 |
| ABN-04 | P2 | 出逃惩罚 | 仍扣废弃的个人工作次数，对结束日没有效果 |
| ABN-05 | P2 | 计数器 | 边界和零值转换不幂等，可重复触发副作用 |
| ABN-06 | P2 | 工作回调 | 审判鸟一次 BAD 可能扣两次计数器 |
| EGO-04 | P2 | 控制状态 | `NotMove` 是无所有者布尔量，重叠控制互相提前解除 |
| EGO-05 | P2 | 冷却 | 同时存在三套不兼容的冷却机制 |
| EGO-06 | P2 | 状态机 | 大型武器把持久状态、动画、伤害和计时混在单类中 |
| EGO-07 | P2 | 数值 | 图鉴、构造器、伤害实现存在多套数值事实来源 |
| AUTH-01 | P2 | 公司终端 | 服务端未验证终端持有/天数，客户端可提前打开只读界面 |
| MELT-04 | P2 | 维护性 | 新旧两套融毁实现并存，旧实现含不可达分支 |
| MIXIN-01 | P2 | Mixin | 高侵入移动 Mixin 取消底层定位和碰撞方法 |
| RES-01 | P3 | 资源 | 同一功能拆成两个资源命名空间，查找规则不统一 |
| BUILD-01 | P3 | 构建 | 全部 Java 编译禁用增量，且构建前删除旧 jar |

## 3. 服务端信任边界和网络包

### SEC-01：E.G.O 饰品可无限生成

**位置**：`src/main/java/com/wzz/lobotocraft/network/packet/ObtainGiftPacket.java:37`

**根因**：服务端只用客户端给出的实体 ID 找到异想体，随后直接创建该异想体的饰品。没有校验距离、观察等级、获得概率、是否已经持有、工作会话或一次性授权。包内的 `giftItemId` 甚至没有参与服务端判定。

**玩家可见表现**：改装客户端重复发送同一个包，可在背包中不断生成同一种饰品；背包满后会持续掉落到脚下。

**建议修复**：删除“点击后直接领取”的模型。饰品掉落必须由工作结算服务端随机产生；如果 UI 确实需要领取，则由服务端签发短期、单次、绑定玩家和工作会话的 grant token，消费 token 后才发放。附加同槽位/同饰品去重。

**性能影响**：修复会减少实体物品和背包同步，性能改善。

**验收**：直接重放 `ObtainGiftPacket` 不能产生物品；正常工作掉落仍只结算一次。

### SEC-02：管理须知可零成本或越界解锁

**位置**：

- `src/main/java/com/wzz/lobotocraft/network/packet/UnlockManualPacket.java:18`
- `src/main/java/com/wzz/lobotocraft/item/PEBoxItem.java:93`
- `src/main/java/com/wzz/lobotocraft/capability/PlayerAbnormalityData.java:104`

**根因**：`abnormalityCode`、`entryIndex` 和 `peBoxCost` 全由客户端传入。客户端传入 0 或负费用时，`consumePEBoxes` 会成功且不消耗物品；服务端也没有根据真实异想体重新计算条目范围和费用。

**玩家可见表现**：玩家没有对应 PE-BOX，也能把基础信息、工作偏好、敏感信息和管理须知全部点亮，观察等级随之提升并提前解锁装备。

**建议修复**：包只传实体/房间标识和逻辑条目 ID。服务端解析真实异想体、验证条目范围、检查未解锁状态、计算权威费用，再原子扣费并解锁。费用必须 `> 0`，重复请求应幂等。

**性能影响**：常数次查表和背包扫描，与当前扣费成本相同。

**验收**：费用 0、负数、超大/负条目索引、伪造 code、重复解锁均被拒绝且不改变背包和观察数据。

### SEC-03：工作开始存在多个安全边界

**位置**：

- `src/main/java/com/wzz/lobotocraft/network/packet/StartWorkPacket.java:42`
- `src/main/java/com/wzz/lobotocraft/network/packet/StartRoomWorkPacket.java:63`
- `src/main/java/com/wzz/lobotocraft/work/AbnormalityWorkHandler.java:32`
- `src/main/java/com/wzz/lobotocraft/work/WorkManager.java:238`

**根因**：现场交互、监控远程工作、直接 `StartWorkPacket` 和 `WorkManager.startWork` 各自维护一部分校验。直接包不会触发 `WorkScreenOpenEvent`，也不保证执行工具类型和所有 `onOpenWorkScreen` 限制。新增规则很容易只加到某一个入口。

**玩家可见表现**：正常 UI 显示“不能工作”的场景，改装客户端直接发送开始工作包仍可能进入工作流程；例如绕过捕获单元或异想体专属的开屏拦截。

**建议修复**：建立服务端唯一入口 `WorkStartService.validateAndStart(request)`。现场交互和监控只负责解析目标/传送，最后必须调用同一验证器。验证器统一处理维度、距离、设施访问、工具类型、精神状态、等级、占用、核心抑制、开屏/开始事件及异想体回调。

**性能影响**：无新增扫描，只消除重复判断。

**验收**：从三种入口发起同一工作请求，接受/拒绝结果和原因完全一致。

### NET-01：监控快照可以高频刷包

**位置**：

- `src/main/java/com/wzz/lobotocraft/network/packet/RequestRoomDataPacket.java:22`
- `src/main/java/com/wzz/lobotocraft/network/packet/OpenRoomMapPacket.java:69`
- `src/main/java/com/wzz/lobotocraft/network/RequestThrottle.java:53`

**根因**：设施管理、LLM 配置等快照已经接入 `RequestThrottle`，但 `RequestRoomDataPacket` 没有。每次请求都会整理房间、楼层、区域、全部员工、危险区域，并刷新房间强加载票。

**玩家可见表现**：单个改装客户端每秒发送数百次刷新，服务器 TPS 下跌、网络发送量突增；其他玩家看到实体卡顿和工作结算延迟。

**建议修复**：在服务端 handler 首行接入独立 `room_monitor` 通道，建议 500~1000ms；同一 tick/同一玩家只构建一次。进一步可缓存设施级公共快照，只为每个玩家过滤可见部门。

**性能影响**：显著降低最坏情况 CPU、NBT/网络分配和区块票更新。

**验收**：压测每秒 100 个请求时，实际构建频率不超过配置值，正常 UI 刷新无明显延迟。

### AUTH-01：公司终端的服务端打开条件不完整

**位置**：

- `src/main/java/com/wzz/lobotocraft/network/packet/RequestFacilityManagementPacket.java:48`
- `src/main/java/com/wzz/lobotocraft/employee/FacilityManagementService.java:40`
- `src/main/java/com/wzz/lobotocraft/event/ForgeModEvent.java:1042`

**根因**：终端按设计 Day 5 发放，但请求包只检查玩家位于公司；不检查当前天数、是否持有终端或是否从合法菜单进入。写操作多数另有 OP 权限校验，因此当前主要是提前读取和打开 UI，而不是直接获得管理写权限。

**玩家可见表现**：Day 1 玩家可通过发包提前打开公司管理界面，查看员工、E.G.O 库存和设施状态，即使从未获得终端。

**建议修复**：明确产品规则。如果终端是访问凭证，建立 `FacilityAccessPolicy.canOpenManagement(player)`，服务端统一验证天数/持有物/权限；所有打开包和物品入口复用该策略。不要只在客户端隐藏按钮。

**性能影响**：常数判断，无影响。

## 4. E.G.O 武器专项

审查范围约 29 个武器实现、约 5527 行。主要问题不是单个武器数值，而是公共协议不完整，导致每个武器重复实现状态和时序。

### EGO-01：等级门槛可以退化成原版攻击

**位置**：

- `src/main/java/com/wzz/lobotocraft/item/ego/base/BaseEgoWeapon.java:195`
- 代表调用：`src/main/java/com/wzz/lobotocraft/item/ego/largebird/LargeBirdWeapon.java:57`

**根因**：基类 `onLeftClickEntity` 先标记并返回 `true`，子类随后才调用 `canUseItem`；不少子类在不满足等级时返回 `false`。Forge/原版语义中 `false` 可能把攻击重新交还给原版路径，形成普通物理/红伤害，而不是彻底拒绝。

**玩家可见表现**：低等级玩家拿高阶 E.G.O，技能和彩色伤害不触发，但仍能把它当高面板普通武器砍人。

**建议修复**：基类在任何攻击状态写入前统一做 `canUseItem`；不满足要求时应消费本次 E.G.O 攻击并明确阻止原版伤害。子类不再重复门槛判断。

**性能影响**：无影响。

**验收**：四维/员工等级不足时，左键、右键、蓄力、投射物和被动全部不能造成伤害或状态。

### EGO-02：Q 键可清除冷却

**位置**：`src/main/java/com/wzz/lobotocraft/item/ego/base/BaseEgoWeapon.java:162`

**根因**：`onDroppedByPlayer` 检测到冷却后调用 `resetCooldown(item)`，随后返回 `false` 阻止丢弃。玩家只需按 Q 就能清掉 `LastUseTime`。

**玩家可见表现**：释放 30~60 秒冷却技能后连续按 Q，可立即再次释放。

**建议修复**：冷却中只返回 `false`，绝不能重置冷却。若该逻辑原本用于“刷新技能”，应使用明确按键和服务端校验，不应复用丢弃事件。

**性能影响**：无影响。

**验收**：冷却中按 Q、换手、换槽、重登、丢入容器都不能缩短冷却。

### EGO-03：`TimerEntry` 使用真实毫秒而不是服务器 tick

**位置**：

- `src/main/java/com/wzz/lobotocraft/util/TimerEntry.java:17`
- 代表调用：`ApprovalBirdWeapon.java:103`、`SmilingCorpseMountainWeapon.java:110`、`EndBirdWeapon.java:392`

**根因**：技能用独立 `ScheduledExecutorService` 按墙钟毫秒调度，再把回调塞回主线程。调用点同时混用“tick * 50”和裸毫秒，接口没有单位类型；服务器暂停或卡顿时墙钟仍前进，积压回调会在主线程恢复后集中执行。计时状态也不随世界保存。

**玩家可见表现**：服务器卡顿后技能多段伤害突然一起爆发；暂停/重启会让眩晕、动画或连段窗口提前结束、永久卡住或直接丢失。

**建议修复**：游戏效果统一用 server game tick 调度器，API 参数使用 `DurationTicks`/明确 tick 整数；墙钟线程仅用于外部 I/O 超时。技能状态需要按风险决定是否持久化或在重登时安全取消。

**性能影响**：去掉至少 6 个调度线程和跨线程任务，减少竞争与回调堆积，通常改善性能。

**验收**：人为降低 TPS 时，技能按游戏 tick 次序执行且不扎堆；停服重启后没有永久锁定标记。

### EGO-04：`NotMove` 重叠控制会互相覆盖

**位置**：

- `src/main/java/com/wzz/lobotocraft/item/ego/bigbadwolf/BigBadwolfWeapon.java:186`
- `src/main/java/com/wzz/lobotocraft/mixin/MixinLivingEntity.java:17`
- `src/main/java/com/wzz/lobotocraft/mixin/MixinEntity.java:29`

**根因**：控制效果共享一个 NBT 布尔值。两个来源同时设为 `true` 后，任意一个先结束就写 `false`，提前解除另一个效果。异常取消路径也没有所有者信息可清理。

**玩家可见表现**：目标同时被两个定身技能命中，其中短控制结束时，长控制也立即失效；反向情况则可能永久不能移动。

**建议修复**：实现带来源 UUID/效果 ID/到期 tick 的控制租约集合；最终移动锁定条件为“至少一个有效租约”。提供统一 acquire/release/cleanup API。

**性能影响**：每个受控实体通常只有少量租约，常数级开销；避免全局 Mixin 状态错误。

### EGO-05：冷却存在三套事实来源

**位置**：

- 基类世界时间戳：`BaseEgoWeapon.COOLDOWN_TAG`；
- ItemStack 每 tick 递减：如 `ArmyInBlackWeapon.java:241`、`RedhatMercenaryWeaponGun.java:128`；
- 原版玩家物品冷却：如 `ChildrenGalaxyWeapon.java:72`、`FourthMatchFlameWeapon.java:58`。

**玩家可见表现**：有些武器换槽仍冷却，有些必须放在背包里才递减，有些同类所有副本一起冷却；提示单位和重登行为也不同。

**建议修复**：定义两种明确策略即可：按玩家+技能共享冷却，或按物品实例冷却。统一服务、同步包、提示单位和持久化，不允许武器自行发明 NBT 键。

**性能影响**：用到期 tick 取代逐 tick 递减可减少每个物品的 `inventoryTick` 写 NBT，性能改善。

### EGO-06：大型武器状态机不可维护

**位置**：

- `EndBirdWeapon.java` 约 640 行；
- `ButterflyFuneralWeapon.java` 约 467 行；
- `BlueStarWeapon.java` 约 342 行。

**根因**：一个类同时负责输入、NBT 状态、动画帧、目标选择、伤害、声音、粒子、冷却和 tooltip。以终末鸟为例，`ChargeStage`、`CanChargeUp`、`IsAnimating`、`UseTick` 与多个异步计时器共同表示状态，缺少合法转换表和统一取消路径。

**玩家可见表现**：换手、死亡、切维度或重登发生在连段中时，可能一直显示“动画中”、无法再次蓄力，或伤害在武器不再持有时继续结算。

**建议修复**：提取纯状态机（状态、事件、转换、截止 tick），服务器 tick 驱动；渲染和音效只消费状态事件。为换槽、死亡、掉落、维度切换、断线统一执行 cancel。

**性能影响**：状态判断更少，取消后不再保留无效计时任务；中性到改善。

### EGO-07：数值有多个事实来源

**位置**：异想体的 `getEGOWeaponData()`、武器构造器攻击属性、武器事件内硬编码 `float damage`、tooltip 文本。

**根因**：显示攻击力、原版属性和实际彩色伤害分别维护。例如多个武器在实现中直接写 `damage = 16/24/30`，调整图鉴数据不会改变实战。

**玩家可见表现**：图鉴或 tooltip 显示一个伤害区间，实战命中是另一个值；平衡修改只改一处后表现更混乱。

**建议修复**：建立 `EgoWeaponSpec` 作为服务端权威数据，包含等级、伤害段、颜色、范围、冷却和研发显示；tooltip 和图鉴由同一 spec 生成。随机区间也应结构化，不用字符串描述。

**性能影响**：静态查表替代重复构造，基本无影响。

## 5. 异想体、出逃和计数器

### ABN-01：跟随光源永久污染世界

**位置**：

- `src/main/java/com/wzz/lobotocraft/entity/EntityLightFollower.java:39`
- 生成点：`EntityLargeBird.java:255`、`EntityPunishingBird.java:251`、`EntitySmilingCorpseMountain.java:638`

**根因**：实体每次换格都会在当前位置放置 `minecraft:light`，但只在 follower 被移除时清理最后一个位置；旧位置永远保留。同时三种异想体的 `triggerEscape()` 在基类拒绝重复出逃后仍无条件生成新 follower。

**玩家可见表现**：大鸟、惩戒鸟或尸山走过的通道长期自发发亮，建筑拆除后仍有看不见的光源；重复触发后出现更多隐形实体。

**建议修复**：记录 `previousLightPos`，换格时只清理属于自己的旧光源再放新光源；停服/卸载时清理。每个 owner UUID 最多一个 follower，子类仅在 `beginEscape` 返回成功时生成。

**性能影响**：移动时多一次定点清理，但防止无限方块、光照传播、区块保存和实体 tick，长期显著改善。

**验收**：异想体移动 100 格并回收后，路径上没有遗留 `minecraft:light`，对应 follower 数为 0。

### ABN-02：碧蓝新星绕过公共出逃生命周期

**位置**：

- `src/main/java/com/wzz/lobotocraft/entity/abnormality/EntityBlueStar.java:132`
- `src/main/java/com/wzz/lobotocraft/entity/base/AbstractAbnormality.java:498`

**根因**：碧蓝新星手工 `setEscape(true)` 和传送，没有调用 `beginEscape`。因此漏掉出逃广播、`AbnormalityEscapeEvent`、`EscapeTracker` 和原收容维度记录。

**玩家可见表现**：碧蓝新星已经出逃，但 HUD 警报不变，Carmen 出逃对话和红帽响应不触发；跨维度后回收位置也可能错误。

**建议修复**：先寻找反应堆目的地，再 `if (beginEscape(destination))` 执行专属状态。禁止子类复制公共生命周期。

**性能影响**：无影响。

### ABN-03：出逃警报不持久且不按服务器隔离

**位置**：`src/main/java/com/wzz/lobotocraft/entity/base/EscapeTracker.java:14`

**根因**：全 JVM 单例只保存三个整数；不持久化、不按 `MinecraftServer` 隔离、不在启动/实体加载时重建，也没有 ServerStopped 清理。

**玩家可见表现**：重启后仍在外面的异想体不再显示警报；同一 JVM 返回标题或切换世界时可能带着上一个世界的警报等级。

**建议修复**：使用每服务器 SavedData 保存“出逃 UUID -> 风险等级”，实体开始/停止/加载/卸载时幂等更新。启动只需对已加载实体做一次重建，不要每 tick 全图扫描。

**性能影响**：事件驱动 O(1)，几乎无影响。

### ABN-04：出逃惩罚扣的是废弃个人工作次数

**位置**：

- `src/main/java/com/wzz/lobotocraft/entity/base/AbstractAbnormality.java:493`
- `src/main/java/com/lobotocraft/facility/mixin/CompanyDailyDataMixin.java:23`
- `src/main/java/com/lobotocraft/facility/api/DayCompletionRequirements.java:15`

**根因**：出逃遍历所有在线玩家扣 `todayWorkCount`，但结束日现在只看设施能源和部门扩建，旧数字不影响投票资格。

**玩家可见表现**：聊天中的工作次数减一，能源条不变，玩家仍可立即结束当天。

**建议修复**：先确认产品规则。若出逃只应有战斗后果，删除旧扣减；若应拖慢当日进度，改成一次性扣全局设施能源并同步明确提示。

**性能影响**：改为设施能源 O(1) 后，少掉全员遍历和发包，性能改善。

### ABN-05：Qliphoth 计数器转换不幂等

**位置**：`src/main/java/com/wzz/lobotocraft/entity/base/AbstractAbnormality.java:922`

**根因**：已经为 0 时继续 `decreaseQliphothCounter` 仍再次调用 `onQliphothMeltdown`；增加只在增加前已经满时截断，接口本身不能保证结果不超过最大值；`setQliphothCounter` 也不 clamp。

**玩家可见表现**：重复外部事件可能再次执行出逃副作用、生成 follower 或重置阶段；未来传入大于 1 的增加量时 HUD 可出现 `4/3`。

**建议修复**：统一计算 `new = clamp(old + delta, 0, max)`；只有 `old > 0 && new == 0` 时触发归零事件。`set`、`increase`、`decrease` 全走一个状态转换函数。

**性能影响**：减少重复事件和实体，性能改善。

### ABN-06：审判鸟 BAD 结果重复扣计数器

**位置**：

- `src/main/java/com/wzz/lobotocraft/work/WorkManager.java:660`
- `src/main/java/com/wzz/lobotocraft/work/WorkManager.java:803`
- `src/main/java/com/wzz/lobotocraft/entity/abnormality/EntityApprovalBird.java:280`

**根因**：`WorkManager` 先按 BAD 调用 `onBadWork`，再调用 `onWorkComplete`。审判鸟的 BAD 因此前者必减 1，后者还有 60% 再减 1。

**玩家可见表现**：计数器 `2/2` 时，一次 BAD 有 60% 概率直接归零出逃，而不是变成 `1/2`。

**建议修复**：结果特定变化只放在 `onGood/Normal/BadWork`；`onWorkComplete` 只处理不依赖结果的收尾，或传入统一的 `WorkOutcomeContext` 一次结算。

**性能影响**：无影响。

## 6. 天数和多人同步

### DAY-01：核心抑制取消发生在世界状态写入之后

**位置**：

- `src/main/java/com/lobotocraft/facility/FacilityEvents.java:34`
- `src/main/java/com/wzz/lobotocraft/core_suppression/CoreSuppressionManager.java:570`
- `src/main/java/com/lobotocraft/facility/day/DayTransitionService.java:100`
- `src/main/java/com/wzz/lobotocraft/capability/CompanyDailyData.java:88`

**根因**：`CompanyDayAdvanceEvent` 可取消。设施监听器在默认优先级先调用 `FacilityManager.beginDay`，清空能源、融毁和出逃状态；核心抑制监听器到 `LOWEST` 才验证并取消。`advanceToNextDay` 返回后只检查设施天数是否已经变化，因此会继续同步新一天。

**玩家可见表现**：核心抑制目标没完成，系统提示“不能进入下一天”，但同时出现 `DAY N+1`，能源、融毁等级、考验日程和出逃实体已经重置。

**建议修复**：

1. 把核心抑制完成条件注册进 `DayCompletionRequirements`，投票开始和提交时都检查；
2. 将事件拆成可取消的 pre-check 和不可取消的 committed 通知；
3. 只有 pre-check 全部通过后，由单一事务同时推进 `FacilityDayData` 和玩家天数；
4. 换天副作用不得挂在仍可取消的事件上。

**性能影响**：只在换天时增加常数判断，无影响。

**验收**：核心条件不满足时，设施天数、玩家天数、能源、融毁、出逃、物品和 Buff 全部不变。

### DAY-02：新一天事件只作用于投票 actor

**位置**：

- `src/main/java/com/lobotocraft/facility/day/DayTransitionService.java:105`
- `src/main/java/com/wzz/lobotocraft/event/ForgeModEvent.java:1024`

**根因**：只有 actor 调用 `daily.advanceToNextDay()` 并发布事件；其他在线玩家直接 `setCurrentDay(nextDay)`。TT2、工作装置、Day 5 终端、抑制计数和部分 Buff 清理因此只作用于 actor。

**玩家可见表现**：多人投票进入 Day 5，只有系统选中的第一名玩家收到公司终端，其他在线玩家没有。

**建议修复**：明确区分 `FacilityDayCommittedEvent`（每服一次）和 `PlayerEnteredFacilityDayEvent`（每名玩家一次）。全局恢复只执行一次，玩家奖励/清理由服务统一遍历所有真实在线公司玩家；离线玩家登录时补发幂等奖励。

**性能影响**：换天时 O(在线玩家)，当前本就遍历全部玩家，无明显增加。

## 7. 融毁机制

### MELT-01：空目标仍消耗融毁阶段

**位置**：`src/main/java/com/lobotocraft/facility/FacilityManager.java:223`

**根因**：`activateMeltdowns` 候选为空时直接返回，但调用者仍发送标题并执行 `finishMeltdownStage()`。

**玩家可见表现**：提示“第 III 级融毁，请在 60 秒内工作”，监控显示目标数 0，融毁等级仍跳到 IV。

**建议修复**：让激活函数返回实际目标列表/数量。为 0 时不发警报、不推进阶段，并保留本阶段等待下一次有效工作重试。

**性能影响**：无新增扫描，减少无效同步。

### MELT-02：候选过滤缺少计数器和特殊类型策略

**位置**：`src/main/java/com/lobotocraft/facility/FacilityManager.java:249`

**根因**：新实现只排除死亡、出逃、工具、正在工作和已融毁实体，没有检查 `getQliphothCounter() > 0`、设施区域或特殊禁止目标。旧实现曾检查这些条件，并特别排除 `EntityDarkSkadi`。

**玩家可见表现**：计数器本来就是 0 的收容单元进入融毁；超时只扣能源，异想体完全没有变化。特殊异想体可能错误触发转化机制。

**建议修复**：集中实现 `MeltdownTargetPolicy.isEligible(entity, facility)`，把所有例外写成有测试的策略，不在激活器内散落 `instanceof`。

**性能影响**：候选扫描中增加常数判断，可忽略。

### MELT-03：目标卸载时失败惩罚只执行一半

**位置**：`src/main/java/com/lobotocraft/facility/FacilityManager.java:275`

**根因**：到期先删除 active 记录并扣能源，再按 UUID 查已加载实体。区块卸载或实体暂时不可见时查找返回 null，计数器不归零且没有失败消息。

**玩家可见表现**：玩家离开区域后能源突然减少；返回时异想体仍正常收容，聊天也没有说明哪次融毁失败。

**建议修复**：不要为此永久强加载区块。把失败写成持久化的 pending penalty（UUID、维度、失败等级），实体加载时补执行；若产品规则允许，也可在卸载时暂停倒计时，但必须明确同步 HUD。

**性能影响**：少量 SavedData 条目，远低于强加载区块；不推荐新增 chunk ticket。

### MELT-04：新旧融毁实现并存

**位置**：`src/main/java/com/wzz/lobotocraft/core_suppression/CoreSuppressionManager.java:249`

**根因**：当前权威已经是 `FacilityDayData.activeMeltdowns`，但核心抑制管理器仍保留实体 PersistentData、红色 scoreboard team、触发玩家 UUID 和扣个人工作次数的旧实现。`tickMeltdown` 目前只负责清旧数据，`applyPendingMeltdownPenalty` 存在逻辑不可达分支。

**玩家可见表现**：当前主要是维护风险；未来调用错入口时会出现监控显示一套状态、实体发光和处罚走另一套状态。

**建议修复**：保留一次明确的旧存档迁移器，迁移完成后删除旧常量、触发器、视觉队伍和个人惩罚代码。所有 UI、工作验证和超时只查询 `FacilityDayData`。

**性能影响**：减少实体 tick 中的旧 NBT 清理，轻微改善。

## 8. 其他战斗、Mixin、资源和构建问题

### COMBAT-01：终末鸟攻击跨维度命中

**位置**：`src/main/java/com/wzz/lobotocraft/entity/abnormality/EntityEndBird.java:591`

**根因**：`dealScaleDamage` 遍历 `server.getPlayerList().getPlayers()`，没有限制玩家维度、公司区域、创造/旁观状态或事件参与资格。奖励玩家的兜底距离比较同样跨维度，距离值没有空间意义。

**玩家可见表现**：终末鸟在公司发动全屏攻击时，主世界、下界或其他副本中的玩家也无缘无故受到灵魂伤害。

**建议修复**：建立 encounter participant 集合，或至少限制 `player.serverLevel() == this.serverLevel()` 且处于公司/事件区域。全服广播和全服伤害必须使用不同 API。

**性能影响**：过滤后目标更少，性能改善。

### MIXIN-01：移动锁定侵入底层实体定位

**位置**：

- `src/main/java/com/wzz/lobotocraft/mixin/MixinEntity.java:29`
- `src/main/java/com/wzz/lobotocraft/mixin/MixinLivingEntity.java:17`

**根因**：工作或 `NotMove` 时直接取消 `setPos`、`setPosRaw`、`setBoundingBox`、`teleportTo`、`moveTo`、`aiStep` 和 `travel`。这些是维度传送、反作弊纠正、重生和碰撞更新的基础方法；任何漏清理状态都会让服务端无法修正玩家位置。

**玩家可见表现**：工作/控制期间被管理员传送、切维度或重生时可能原地回弹、卡在旧碰撞箱，甚至持续无法移动。

**建议修复**：只在输入/移动意图层阻止玩家主动移动；服务器权威传送、重生和碰撞更新必须允许。工作锁和战斗控制使用带来源租约，不直接取消底层 `Entity` 方法。

**性能影响**：减少每次实体定位调用中的 Mixin 判断，轻微改善。

### RES-01：两个资源命名空间增加维护成本

**位置**：

- `src/main/resources/assets/lobotocraft/`
- `src/main/resources/assets/lobotocraft_facility/`
- `src/main/java/com/lobotocraft/facility/FacilityPatch.java:13`

**根因**：代码 Mod ID 是 `lobotocraft`，设施 UI 资产另用 `lobotocraft_facility`。目前前者约 1304 个文件、后者约 29 个文件；通用 `ResourceUtil` 默认又总是生成 `lobotocraft`，新代码容易引用错命名空间。

**玩家可见表现**：当前不一定必现；引用错时会显示紫黑纹理、缺失字体/布局或静默回退。

**建议修复**：长期统一到一个资产命名空间；迁移期提供具名 helper（`facilityAsset`），禁止裸字符串。增加资源存在性测试，扫描 Java 中引用的纹理/模型/声音。

**性能影响**：运行时无明显影响，主要降低发布缺资源风险。

### BUILD-01：构建不可复现且每次全量编译

**位置**：`build.gradle:146`、`build.gradle:159`、`build.gradle:178`

**根因**：

- `outputs.upToDateWhen { false }` 对全部 `JavaCompile` 禁用增量和 up-to-date；
- jar 版本包含当前时间，无法对相同源码生成相同产物名；
- `jar` 前删除 `build/libs` 下所有 jar，会影响并行任务、历史产物和调试对比。

**玩家可见表现**：不是游戏内 bug；开发者看到每次测试/构建都重新编译，CI 缓存失效，难以确认两个 jar 是否来自相同源码。

**建议修复**：恢复 Gradle 增量编译；仅在发布任务做 `clean`，不要在 `jar` 中删除目录；版本使用 Git SHA/显式构建号，时间放 manifest 而不是 artifact identity。

**性能影响**：显著缩短本地和 CI 构建时间，不影响服务端运行。

## 9. 已确认的设计规则，不要当成 Bug 直接改

以下行为已有测试或代码注释固定，若要改变应先做产品确认：

1. 普通公司日不生成融毁；只有核心抑制激活且该阶段没有触发考验时生成融毁。见 `MeltdownPolicyTest`。
2. 结束日投票超时时，未投票者按赞成计算。见 `DayVoteStateTest`。命名容易误解，但目前是刻意规则。
3. 出逃回收已经支持保存收容维度/位置、跨维度返回、区块加载和延迟重试。不要退回“杀旧实体再生成新实体”的实现。
4. 当前融毁在“工作完成”时解除，而不是点击开始工作即解除；如要改回开始即解除，需要同步 UI 文案和测试。

## 10. 推荐实施顺序

### 第一批：封堵可利用问题

1. SEC-01 饰品发放改为服务端工作结算；
2. SEC-02 图鉴解锁服务端重算费用；
3. EGO-02 删除 Q 键清冷却；
4. SEC-03 合并工作验证入口；
5. NET-01 增加监控快照节流；
6. COMBAT-01 限制终末鸟参与者。

这一批应单独提交，便于安全审查和紧急发布。

### 第二批：修复状态一致性

1. DAY-01 原子换天；
2. DAY-02 全员换天事件；
3. ABN-01 光源清理；
4. ABN-02/03 公共出逃生命周期和持久警报；
5. MELT-01/02/03 融毁目标和卸载语义；
6. ABN-05/06 计数器与工作结果一次结算。

### 第三批：偿还公共基础设施债务

1. EGO-03 用 game tick 调度器替换 `TimerEntry` 游戏效果；
2. EGO-04/MIXIN-01 引入控制租约并缩小 Mixin；
3. EGO-05 统一冷却；
4. EGO-06/07 拆状态机并建立 `EgoWeaponSpec`；
5. MELT-04 删除旧融毁实现；
6. RES-01、BUILD-01 做资源和构建整理。

## 11. 测试和验收要求

每个修复 PR 至少加入对应的纯逻辑测试；涉及 Forge 集成的项目还需进行专用服务器人工/自动集成验证。

建议新增测试矩阵：

- 网络：恶意费用、非法索引、重放、伪造实体、远距离、跨维度、100 req/s；
- 工作：现场、监控、直接包三个入口的规则一致性；
- E.G.O：等级不足、Q 键、换槽、双持、重登、死亡、低 TPS、两个控制重叠；
- 出逃：重复 trigger、跨维度回收、重启后警报重建、同 JVM 两个服务器隔离；
- 光源：移动、区块卸载、实体回收后无遗留方块和 follower；
- 换天：单人、多人、actor 离线、核心条件失败、事件取消、离线玩家补发；
- 融毁：无候选、计数器 0、特殊目标、目标卸载、截止 tick 同时完成工作；
- 战斗：终末鸟同维度参与者命中，其他维度/旁观/创造玩家不命中；
- 构建：`test`、`build`、产物包含两个 Mixin 配置及全部必要资源。

每批修复完成后运行：

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

最后在专用服务器至少进行一次双人换天、核心抑制、异想体出逃回收和 E.G.O 连段冒烟测试。不要仅用单人 `runClient` 作为多人同步与服务端权限的验收依据。
