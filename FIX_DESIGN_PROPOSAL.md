# 已指定问题的修复设计稿

状态：方案审查稿，**不包含代码修改授权**。

目标不是逐个堵点，而是把相同类别的问题归并为少量明确的服务端权威模块。每个模块都有生命周期所有者、持久化边界、失败语义和测试入口，避免本轮修复制造新的静态状态、重复包校验或第二套业务流程。

## 共同基础设施

### 1. 网络动作框架

建立 `PacketDescriptor<T>` 注册表，保存包 id、接收方向、解码器和最大字段/集合长度。网络层读到 id 后先用 `NetworkEvent.Context` 的接收方向匹配 descriptor，只有方向合法才实例化并解码包体。取消由 `IMessage.sendToClient/sendToServer` 在解码后的自报方向判定。

所有服务端动作统一为 `ServerAction`：包只解析请求 DTO，服务端主线程的 action 负责权限、维度、距离、速率、实体存活和业务规则。每种 action 有固定 `RequestThrottle` 通道。这样 SEC-01、SEC-02、SEC-03、NET-01、R-NET-02 至 R-NET-05 不会各自实现一套不一致的防护。

### 2. 设施访问策略

建立 `FacilityAccessPolicy`，定义四类能力：`MONITOR_VIEW`、`MONITOR_TRAVEL`、`FACILITY_EDIT`、`STAFF_COMMAND`。策略唯一负责检查公司维度、终端持有、部门可见性、管理员权限与可选距离/会话令牌。只读界面和写操作使用不同能力，避免把“能打开屏幕”误写成“拥有写权限”。

### 3. 临时效果租约

建立服务端 tick 驱动的 `EffectLeaseService`。租约键为 `(实体 UUID, effect kind, source UUID)`，值包含到期 game tick、原始状态和清理回调。`MOVEMENT_LOCK`、`NO_GRAVITY`、`NO_AI`、速度 modifier 等都由租约聚合，不再直接写共享 `NotMove` 布尔值。

租约在实体死亡、移除、维度改变、玩家登出和服务器停止时幂等释放；需要跨重登持续的效果才写入 `SavedData`。这同时解决 EGO-04、R-EGO-01、R-ABN-03、R-ABN-07 和 MIXIN-01 的共因。

### 4. 工作与工具调度服务

建立 `WorkStartService` 作为普通工作的唯一入口，输入 `WorkStartRequest`，统一执行实体有效性、同维度/距离、收容区访问、工具类型拒绝、精神/等级、占用、核心抑制、`WorkScreenOpenEvent`、`onOpenWorkScreen`、`WorkStartEvent` 和 `onWorkStart`。

建立独立的 `ToolDispatchService`，不复用四种普通工作。工具任务的状态为 `TOOL_USE`，记录员工 UUID、工具 UUID、维度、任务阶段和开始 tick；到达后调用工具专用的服务端接口，绝不调用 `WorkManager.startWork`。

## 发布与网络

### CRASH-01

**方案**：构建只产出一个带稳定版本和 Git SHA 的 jar；增加 `verifyReleaseJar`，检查 `SpecialKillSpawnEvent`、`LobotomySpawnSavedData`、mixin 配置和所有注册资源是否在 jar 内。增加 `installReleaseToPack` 任务：构建到临时文件、校验、移动旧 jar 到备份名、原子移动新 jar、最后删除过期同 mod id jar。

**失败语义**：验证失败时不碰 mods 目录；安装任务只允许目标目录有一个 `lobotocraft` jar。`SpecialKillSpawnEvent` 可临时用 `try/catch LinkageError` 记录错误并跳过该奖励，作为发布损坏时的防崩保险，但不能替代产物校验。

**验收**：新开 JVM 后击杀触发特殊生成；对 jar 执行 class 清单测试；故意删除 class 的 jar 必须在启动/安装阶段被拒绝，而非游戏内死亡时崩溃。

### SEC-01 与 SEC-02

**方案**：删除 `ObtainGiftPacket` 的发奖职责。工作完成时由服务端根据 `WorkOutcome`、观察等级、异想体 gift 数据和玩家已装备槽位生成 `GiftGrant`；grant 立即结算或由短期、单次、绑定玩家与工作会话的 token 消费。客户端仅显示结果。

图鉴解锁请求只携带可信目标标识和逻辑条目 id。`ManualUnlockService` 从服务器端图鉴定义查条目、价格、前置条件与是否已解锁，使用事务式“先检查，后扣 PE-BOX，后写 capability”。价格必须严格大于零，已解锁请求返回幂等成功但不重复扣费。

**验收**：重放旧包、负成本、伪造 code/索引、没有工作会话和背包满载均不能多发物品或改变图鉴。

### SEC-03

**方案**：现场右键、监控传送后开工、直接包和 AI 员工普通工作全部调用 `WorkStartService`。原 `StartWorkPacket` 只转发请求，不保留私有校验分支；没有合法 UI 调用的 `StartRoomWorkPacket` 删除注册与实现，防止历史客户端继续使用。

**验收**：四个入口对捕获单元、工具异想体、精灵盛宴限制、恐慌、等级不足和占用返回同一拒绝原因。

### NET-01

**方案**：`RequestRoomData` 使用 `room_monitor` 快照通道，500ms 默认冷却；快照构建按服务器 tick 缓存一次，再按玩家可见部门过滤。冷却内只丢弃请求，不排队。

**验收**：单客户端 100 请求/秒时，每玩家实际构建频率不超过配置；正常监控刷新不闪烁。

### R-NET-01

**方案**：采用共同网络动作框架。所有数组、矩阵、字符串、NBT、列表有常量上限，长度先检查再分配；clientbound 类型即使伪造 id 也不会在服务器解码。

**验收**：对每个注册包做方向矩阵和恶意长度模糊测试；服务端仅断开违规连接或记录拒绝，不分配巨量内存。

### R-NET-02、R-NET-03、R-NET-04、R-NET-05

**方案**：

- 房间图鉴只接受已在 `CompanyRoomData` 登记、对请求者可见且已加载的房间；不以客户端坐标触发 `getChunk`。未加载则回复“数据暂不可用”，由正常监控票机制预热。
- 删除 `StartRoomWorkPacket`；合法监控传送走 `RoomTeleportRequestPacket`，要求 `MONITOR_TRAVEL` 与冷却。
- 收容绑定仅允许 `FACILITY_EDIT`，只允许监控候选快照签发的候选 UUID，候选 token 绑定房间、玩家和短期过期时间。
- 楼层命名和电梯配置要求 `FACILITY_EDIT`；电梯距离经服务端限制为正数、最大高度范围内且配置后仍必须通过实际目的地验证。

### R-NET-06

**方案**：保留奇怪徽章物品注册、名称、贴图和配方，但移除其所有激活副作用：不发 `StrangeBadgeEffectPacket`，不启动 native/JNA 弹窗，不断开、不改存档、不创建 bat、不 `System.exit`。使用时仅返回普通 `PASS` 或无害短提示。

随后移除未再引用的 `StrangeBadgeEffectHandler`、`DisplayMessage` 及专属 native 调用路径；不要留下“客户端判断后偶尔执行”的死代码。

## E.G.O 与状态控制

### EGO-01、EGO-02

**方案**：`BaseEgoWeapon` 在任何攻击状态写入前检查 `canUseItem`；失败时返回“已处理但不造成伤害”的结果，禁止回落到原版 `PLAYER_ATTACK`。子类不再自行用 `false` 表示等级不足。

冷却中的 Q 键只取消丢弃，不再调用 `resetCooldown`。冷却写入和读取继续使用服务端 `gameTime`，换手/容器/丢弃不会改变到期 tick。

### EGO-04 与 R-EGO-01

**方案**：迁移到 `EffectLeaseService`。计时采用服务器 game tick 调度，`refresh` 通过同一 `(effect, source)` 租约延长到期 tick；不同来源可并存。结束、实体移除、卸载、死亡和停服都走同一 cleanup，回调只执行一次。

**迁移策略**：先为现有 `NotMove` 写兼容读取器，把旧 tag 作为一次性迁移输入；所有武器迁移完成后删除旧 tag 写入。不要把旧 `TimerEntry` 与新调度器并存。

### R-EGO-02

**方案**：E.G.O 投影装备使用“两阶段原子落位”：先计算所有需要槽位和可用库存位置；任一把武器不能放入稳定位置时不改变现有装备。多件套优先写入投影仓库/员工专属容器，不向世界丢弃原装备；只有玩家明确确认时才进行可回滚交换。

## 异想体、出逃与考验

### ABN-01

**方案**：`EntityLightFollower` 记录自己实际放置过的所有位置，且只删除自己放置、状态仍为匹配亮度的 light block。移动前清理旧位置；实体删除、区块卸载/重载、owner 消失和服务器停止都清理记录。为避免无界 NBT，跟随光源不应在路径上留灯，正常情况下只保持当前格；历史集合仅作为迁移/异常恢复。

### ABN-02

**方案**：不改碧蓝新星的出逃行为。当前代码已进入公共 `beginEscape`。补回归测试：触发碧蓝新星出逃后必须出现通用出逃事件、`EscapeTracker` 计数和回收流程，随后关闭该误报。

### ABN-04

**方案选择**：推荐删除“每个出逃实体扣每个玩家个人工作次数”的遗留惩罚。出逃压力已由战斗、收容失控和设施级系统表达；若仍希望有日进度惩罚，应只在**原始异想体首次出逃**时向 `FacilityDayData` 写一次显式设施级惩罚，不能由分身/重复事件叠加，也不能改无权威意义的个人计数。

### ABN-05、ABN-06

**方案**：`QliphothCounter` 用唯一状态转换函数计算 `newValue = clamp(old + delta, 0, max)`，只有 `old > 0 && newValue == 0` 执行 meltdown。`set` 同样 clamp。审判鸟的结果规则收敛为一个 `onWorkOutcome`：BAD 只执行一次随机表，不先固定减再额外减。

### R-ABN-01

**方案**：所有考验实体实现 `OrdealMember`，带 `ordealId`、`counted` 和持久化结算状态。`OrdealLifecycleService.finishMember(entity, resolution)` 是唯一扣剩余数入口；正常死亡、和平移除、删除棒 `discard` 和管理员 force-remove 都调用它，区块卸载不结算。`counted` 防止 `die` 后 `remove(KILLED)` 双扣。

删除棒对考验使用显式 `FORCED_CLEAR` 结算，而不是普通裸 `discard`。

### R-ABN-02

**方案**：建立 `SwallowedPlayerLedger`（按 server SavedData）记录玩家 UUID、狼 UUID、原始 GameType、吞入 tick 和释放结果。狼释放时先写 ledger 的待恢复状态，再恢复在线玩家；离线玩家登录时恢复原游戏模式、相机和标记。狼消失、跨维度或世界卸载也执行同一恢复路径。

### R-ABN-03、R-ABN-07

**方案**：血肉偶像和绞架均使用效果租约。血肉偶像创建 `NO_GRAVITY` 租约时保存原值；登出、死亡、工具删除、维度改变和任务结束都释放。绞架保存受害者 UUID 与维度，服务器端 tick 才绑定；移除时只释放租约，绝不依赖实体 id 或杀死解析到的未知实体。

### R-ABN-04、R-ABN-05、R-STATE-03

**方案**：临时惩罚和活动状态放入按 `MinecraftServer` 隔离的 `ServerRuntimeState`，统一监听 `ServerStopped` 清理。需要跨重登保留的状态改为对应 `SavedData`；不需要保留的（老妇人处罚）登出即取消并清理。深蓝正午不再有全局 `trialActive`，改为 `isTrialActive(server)` 从 `OrdealData` 推导。

### R-ABN-06

**方案**：黑森林门以 `BirdRole` 枚举为键保存捕获快照。只有三种唯一 role 各一只才允许召终末鸟；重复鸟不 discard、不覆盖快照，直接拒绝进入或退回原处。终末鸟结束时按 role 一对一恢复。

### R-ABN-08：保留部门生成，修正人数规模

当前落点确实来自 `OrdealSpawnPointService.openCorridorsByDepartment`，即已开放部门/区段走廊。错误只在蓝正午和紫黎明把生成组数设为 `server.getPlayerList().getPlayers().size()`。

**方案**：`EncounterPopulationPolicy` 接收触发维度的有效参与者列表（存活、非旁观、在公司维度），生成组数只由该列表计算。落点仍按开放部门分组轮转：先保证每个已开放部门可获得候选，再按参与者数量追加，设置全局上限。主世界、下界、旁观者不增加怪物数量。

## 换天、融毁与工作会话

### DAY-01、DAY-02

**方案**：替换可取消后才提交的换天模型为 `DayTransitionService.commit` 事务：

1. 在任何状态写入前，对投票、设施条件、核心抑制、所有需要的玩家条件做 preflight。
2. 通过后一次性写 `FacilityDayData` 与所有真实在线公司玩家的 daily data。
3. 发布不可取消的 `FacilityDayCommittedEvent`（全服一次）和 `PlayerEnteredFacilityDayEvent`（每个玩家一次）。
4. TT2、工作设备、终端、Buff 清理等按每玩家事件执行；全局设施重置只监听全服事件。

旧 `CompanyDailyData.advanceToNextDay()` 不再承担领域事件和设施写入，避免 actor 顺序决定世界状态。

### MELT-01、MELT-02

**方案**：`MeltdownTargetPolicy` 集中判断：存活、未出逃、非工具、未工作、计数器大于零、收容室可用、未在 active list、非特殊排除目标。`activateMeltdowns` 返回 `ActivationResult`。

只有 `targetsCreated > 0` 才发送标题、开始倒计时、完成当前阶段和预选下一阶段；为零时保留当前阶段并设短重试冷却，下一次有效工作重试，不刷无目标警报。

### R-WORK-01、R-WORK-02

**方案**：每 tick 在 `WorkSessionValidator` 验证玩家/目标都存活、未移除、同一 `ServerLevel`、目标仍允许普通工作且距离有效。失败调用 `cancel`，不调用任何完成结算。

工作结果改为明确枚举：`COMPLETED`、`INTERRUPTED`、`ABORTED_INVALID_TARGET`。只有 `COMPLETED` 才发 PE、属性、日工作数和 `onWorkComplete`；强制结束默认是 `INTERRUPTED`，除非某个异想体在服务端显式声明部分结算规则。

## 终末鸟、移动与员工

### COMBAT-01

**方案**：终末鸟创建 `EncounterParticipantSet`，记录同维度、非创造/旁观、在遭遇区域或被明确加入的玩家。全屏技能只伤该集合；登录/离开/死亡/离开维度实时更新。不要把 `server.getPlayerList()` 当作伤害目标集合。

### MIXIN-01

**方案**：移除对 `Entity.setPos`、`setPosRaw`、`teleportTo`、`moveTo`、`setBoundingBox` 的取消注入。玩家控制锁定在“客户端移动输入/服务端移动包处理”层拒绝玩家主动位置更新，同时通过 `EffectLeaseService` 设速度为零和清速度；服务器传送、重生、碰撞修正必须永远允许。非玩家定身使用 AI/导航停止和属性 modifier，不拦截底层实体 API。

### R-EMP-01

**方案**：每名员工维护 `nextThreatScanTick` 和缓存目标；无目标时也只能每 10--20 tick 扫描一次。扫描只查询已开放部门的局部 AABB，按风险和距离选一个目标；订单、目标改变或区域变更才提前失效缓存。

### R-EMP-02

**方案**：镇压订单的有效条件是 `AbstractAbnormality.hasEscape()`、存活、同维度且仍为威胁；回收到收容室、目标删除、跨维度或失去威胁时调用 `complete`，而不是继续攻击。

### R-EMP-03

**方案**：电梯导航根据 `TravelResult` 分类：`LOCKED_AREA`、`BLOCKED_DESTINATION`、`NO_DESTINATION` 都记为当前边不可用并重新规划其他电梯；只在真正动画开始后进入 `WAITING`。每条边有退避时间和最大重试，超限报告不可达。

### R-EMP-04

**方案**：命令入口验证 world border、构建高度和 `BlockPos` 安全范围；寻路距离计算使用 `long`/`double`，不做可能溢出的 `int` 差值。任何非法目标在命令阶段返回错误，导航快照对异常 bounds fail closed。

### R-EMP-05

**方案**：导航结果区分 `NO_PATH` 与 `WORLD_INCOMPLETE`。后者只在有限次数内延迟重试；设施目标可申请有上限、短 TTL 的导航区块票，超过预算或重试次数后以“区域未加载/无路”失败，不无限循环。

### R-EMP-06

**方案**：电梯路由查询只读取已加载 block entity 和已验证的持久化 marker，绝不在查询中 `getChunkAt`。未知/未加载目的地视为暂不可用，交给上述有限导航票预热；单次路线规划有候选上限。

### R-EMP-07：工具异想体员工派遣（按指定规则）

**方案**：监控中选中工具异想体后，客户端不显示四种工作类型，直接请求 `ToolCandidateSnapshot`。服务端返回可用、同部门权限、空闲、存活且能到达的员工列表；选中一个员工后窗口立即关闭，发送 `AssignToolEmployeePacket`。

服务端创建 `EmployeeTaskType.TOOL_USE`，员工导航到工具附近；到达后由 `ToolDispatchService` 执行 `tool.startUsing(employee)` 或新增 `tool.useByEmployee(employee)`。持续工具保存使用者与任务 lease，非持续工具执行一次后完成。失败、死亡、工具删除、到达超时均安全结束并给出状态；全程绕过普通四种工作、PE、属性、日工作数和 `WorkManager`。

### R-STATE-01、R-STATE-02

**方案**：`DamageHelper` 不再保存全局首次 `Level`，所有伤害源从受害者/攻击者当前 level 取得；火焰、kill 等判断改比较 `DamageType` resource key，不比较 `DamageSource` 对象身份。

死亡备份改成按 `MinecraftServer` 隔离的 `DeathCloneCache`：仅死亡前写入、仅 `PlayerEvent.Clone.isWasDeath()` 消费、消费后删除；登出、停服和 TTL 清理。非死亡 clone 始终从 original capability 复制，永不读取死亡缓存。

## 实施顺序与门槛

1. 发布校验、网络 descriptor、服务端授权和 SEC-01/02：先封闭崩服与刷物品。
2. 换天事务、工作服务、工具派遣：先消除双入口和错误结算。
3. 效果租约、移动控制、出逃/考验生命周期：迁移时每类状态只允许一个权威来源。
4. 融毁策略、终末鸟参与者、员工导航与电梯：进行专用服务器压力测试。
5. 清理旧融毁、旧冷却、双资源/构建债务。

每一步要求：单元测试覆盖纯策略；专用 Forge 服务器测试覆盖玩家登出、区块卸载、跨维度、创造/命令、恶意包和双人换天。任何迁移完成前不得删除旧存档兼容读取器；兼容读取器必须只读一次、写入新状态后清除旧状态。
