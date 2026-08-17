# 憎恶女王实现说明

## 1. 实装范围

本次实装对象为 `O-01-04 憎恶女王` 本体，包含：

- 收容、工作、逆卡巴拉计数器与歇斯底里机制
- 二级警报时主动外出协助镇压
- 人形 HERO 战斗、歇斯底里失控与蛇形出逃
- 蛇形镇压后的双阶段恢复和返回收容室
- 人形/蛇形模型、动画、碰撞箱、音效、星弹和激光视觉
- 中英文故事、管理须知、图鉴肖像、刷怪蛋与多语言名称
- 服务端 NBT 持久化与客户端同步

E.G.O 暂未开放。没有注册武器、护甲、饰品、研发条目或掉落入口。

## 2. 架构

### 2.1 核心模块

| 模块 | 职责 |
| --- | --- |
| `EntityQueenOfHatred` | 权威状态机、计时器、工作结果、战斗、变身、回收、NBT |
| `QueenOfHatredPolicy` | 纯规则：阶段死亡阈值、20% 死亡率、16 PE 分界、成功率惩罚 |
| `QueenOfHatredStaff` | 统一识别玩家员工、AI 员工和文职，并提供 HP/SP 治疗 |
| `QueenOfHatredCombat` | 目标阵营、直线命中、方块遮挡、BLACK 伤害 |
| `QueenOfHatredLifecycleEvents` | 接收工作完成、职员死亡和设施逆流阶段事件 |
| `FacilityQliphothStageEvent` | 设施每次推进逆流阶段时向异想体广播一次权威事件 |
| `EntityQueenOfHatredRenderer` | 根据同步形态字段动态选择人形或蛇形模型 |
| Queen laser/star visual subsystem | 客户端星弹、分层法阵、激光、遮挡与屏幕压暗效果 |

### 2.2 设计原则

1. 服务端权威：机制、伤害、治疗、状态转换和目标选择全部在服务端执行。
2. 单实体双形态：变蛇时不替换实体，UUID、收容绑定、计数器、设施追踪和 NBT 不丢失。
3. HERO 不是出逃：协助镇压期间 `hasEscape=false`，不会被员工 AI、警报或其他镇压逻辑视为敌人。
4. 蛇形才进入标准出逃生命周期：调用通用出逃入口，接入警报、广播和镇压系统。
5. 动画时长优先：动作状态的持续时间按提供的 Minecraft 动画长度设置，而不是照搬原作秒数。
6. 工作使用精确 PE：通过 `WorkCompleteEvent` 取得实际产量，避免仅凭差/良/优结果猜测 16 PE 分界。

## 3. 静态数据

| 项目 | 数值 |
| --- | --- |
| 编号 | O-01-04 |
| 危险等级 | WAW |
| 最大 PE | 22 |
| 结果区间 | 差 0-8，良 9-14，优 15-22 |
| 工作伤害 | BLACK 3-4 |
| 计数器极值 | 2 |
| 最大生命值 | 1200 |
| 抗性 | RED 0.7 / WHITE 1.2 / BLACK 0.3 / PALE 1.5 |
| 计数器为 1 | 所有工作基础成功率降低 20 个百分点 |

“职员”统一包含玩家员工、AI 员工和 `EntityClerk` 文职。创造/旁观玩家不会成为蛇形攻击目标。

## 4. 生命周期

### 4.1 收容状态

```text
CONTAINED
  -> 玩家首次进入附近：CONTAINED_GREETING -> CONTAINED
  -> 计数器 2 完成工作：CONTAINED_KISS -> 治疗 -> CONTAINED
  -> 计数器降为 1：CONTAINED_HYSTERIA_START -> CONTAINED_HYSTERIC
  -> 歇斯底里工作 >=16 PE：CONTAINED_RESTORE -> CONTAINED
  -> 歇斯底里工作 <16 PE：VILLAIN_IDLE（蛇形出逃）
```

`kiss` 动画持续 2.5 秒。HP/SP 在动画结束后才回满，随后才结算本次工作的计数器变化。

每次设施推进逆流阶段时检查上一阶段死亡人数：

- 少于 3 名职员：计数器减 1
- 至少 3 名职员：计数器不变
- 检查后清空阶段死亡计数和本阶段出动标记

### 4.2 HERO 协助镇压

触发条件：

- 当前为二级或三级警报（内部等级 `>= 1`）
- 计数器为 2
- 本逆流阶段尚未出动
- 当前没有对她进行工作
- 本维度存在出逃异想体或考验敌人

出动时记录职员总数。HERO 不进入 `EscapeTracker`，会寻找敌对异想体和考验单位：

- 普攻：星弹造成 BLACK 13-16；经过友方职员时恢复 HP/SP 13-15
- 激光第一阶段：BLACK 12-15
- 激光第二阶段：BLACK 15-18
- 激光会被实体方块遮挡，不穿墙结算或显示
- HERO 激光期间免疫普通伤害，但保留强制击杀通道

若协助期间死亡职员达到出动时职员总数的 20%，进入：

```text
HERO_HYSTERIA_START
  -> HERO_HYSTERIC（25-30 秒）
  -> 原地切换为蛇形并进入标准出逃生命周期
```

HERO 被击倒时播放 `heal`，动画结束后为全体存活职员回满 HP/SP，再返回收容室。

### 4.3 蛇形出逃

收容状态计数归零时，从标准最近可用出逃方块出逃；由 HERO 失控变蛇时保留原地位置，不被通用出逃传送再次移动。

蛇形状态：

```text
VILLAIN_IDLE
  -> charge -> charge2 -> charge3
  -> VILLAIN_LASER / charge4
  -> VILLAIN_WEAK / charge5
  -> VILLAIN_IDLE
```

蛇形激光三阶段 BLACK 伤害为 4-6、6-8、8-10。命中出逃异想体或考验单位时附加短暂缓慢。

生命比例首次跨过 70%、40%、10% 时，会传送至随机存活职员附近，并在半径 4 内造成 BLACK 20-26。每个阈值只触发一次。

蛇形被镇压后不直接消失：

```text
VILLAIN_RETURN_SNAKE（蛇形 human，2.25 秒）
  -> 切换人形
VILLAIN_RETURN_HUMAN（人形 human，4.5 秒）
  -> 返回收容室、恢复计数器和生命值、结束警报追踪
```

出逃时始终明确设置 `NoAI=false`。

## 5. 动画、模型与音效

人形使用 `the_queen_of_hatred_human`，蛇形使用 `the_queen_of_hatred_snake`。服务端同步 `DATA_SNAKE_FORM`，客户端渲染器据此切换 geo、animation 和 texture。

实体状态使用短动画 ID；客户端控制器按形态补全 Blockbench 完整键，例如：

```text
idle_inside
-> animation.the_queen_of_hatred_human.idle_inside

charge4
-> animation.the_queen_of_hatred_snake.charge4
```

已接入 14 个人形动画、8 个蛇形动画和 23 个原版音效。循环音频按源音频时长重新播放，避免长激光或长歇斯底里后半段静音。

激光网络包携带独立 `castTicks`，人形使用 90 tick，蛇形使用 65 tick。法阵各层仍按相对进度依次出现，因此两种前摇共用视觉实现但不会错时。

## 6. 网络与存档

网络协议由 29 升至 30，因为激光包新增了 `castTicks` 字段。服务端向同维度玩家广播星弹/激光开始和清理消息。

NBT 保存：

- 当前状态、动作剩余时间和已流逝时间
- HERO/蛇形各类冷却
- 阶段死亡数、HERO 死亡数和出动时职员数
- 蛇形传送阈值进度
- 本阶段是否已出动
- 收容原点
- `kiss` 目标 UUID 与待结算工作结果

重载时不会从激光或攻击动画中间继续，危险的瞬时动作会归一到对应稳定空闲状态；形态、出逃标记和长期机制数据仍会恢复。

## 7. Mod 接入点

- `ModEntities`：实体类型
- `ModItems` / `ModTabs`：刷怪蛋和创造栏
- `ModCommonEvent`：实体属性
- `ModClientEvent`：动态双模型渲染器
- `ModSounds` / `sounds.json`：原版音效
- `AbnormalityEncyclopediaData`：7 条管理须知和敏感信息
- `stories/cn|en/MagicalGirl_*.xml`：原版中英文故事
- `zh_cn.json` / `en_us.json`：实体与刷怪蛋名称

本次还修正了 `ModAttributes` 的公共注册根因：过去会无条件重加默认抗性，覆盖实体在 `createAttributes()` 中声明的四抗；现在只为缺少该属性的实体补默认值。

## 8. 验证

已完成：

- 全量 Gradle `build`：通过
- JUnit：全量通过
- 憎恶女王专用 GameTest：6/6 通过
- 资源审计：14 个人形动画、8 个蛇形动画、23 个音效均存在
- JSON 校验：中英文语言文件和 `sounds.json` 均可解析

GameTest 覆盖：静态数值与抗性、16/15 PE 分界、亲吻延迟治疗、阶段死亡保护、HERO 20% 失控、HERO 原地变蛇、出逃时 NoAI、蛇形双阶段回收、NBT 重载。

## 9. 已知边界

- 当前星弹和激光客户端管理器各维护一个活动效果，基于设施中同编号异想体唯一的正常玩法前提。调试命令同时播放同类效果时，后发效果会覆盖前发效果。
- 无头 GameTest 可验证服务端机制和资源完整性，但不能代替实际客户端对模型比例、骨骼观感和特效构图的人工验收。
- 当前图鉴界面仍只接收一个显示名称，因此低观察等级“魔法少女”、高观察等级“憎恶女王”的动态名称切换尚未纳入本次本体实装。
- E.G.O 明确保持关闭，后续开放时应单独接入研发、装备、描述和掉落生命周期。
