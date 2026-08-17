# Geburah 核心抑制实现记录

更新日期：2026-08-17

状态：首轮服务端垂直切片完成，完整招式 AI 与屏幕效果尚未完成。

## 1. 本轮目标

本轮先建立一条可编译、可测试、可由现有核心抑制入口启动和结束的 Geburah 遭遇链路，重点解决生命周期、状态归属和清理边界。没有把 Geburah 的大量条件继续堆进 `CoreSuppressionManager`。

## 2. 架构边界

- `CoreSuppressionEncounter`：核心抑制专属遭遇的通用生命周期接口。
- `CoreSuppressionEncounterRegistry`：按核心抑制类型注册专属遭遇。通用管理器只调用校验、启动、tick、结束、玩家生命周期和视觉阶段委托。
- `GeburahEncounterService`：Geburah 遭遇的唯一生命周期入口，负责生成、实体 UUID、参与者、Boss Bar、AI 员工冻结、胜负和幂等清理。
- `GeburahEncounterData`：独立 `SavedData`，保存实体 UUID、参与者、观察者、起始人数、共享生命和淘汰状态。
- `GeburahCombatState`：不依赖 Minecraft 世界的纯状态机，负责生命、架势、EGO、失衡和衰竭计时。
- `EntityGeburah`：实体同步、合法伤害入口、目标选择、阶段属性和动画状态。后续动作控制器应组合进实体，不把招式条件写回通用管理器。

## 3. 已实现内容

### 3.1 生成与清理

- Geburah 注册为独立实体，最大生命值 `6000`。
- 在 `disciplinary/upper-main` 中生成。
- 生成点从与正常出口相连的可站立单元反向遍历，要求完整碰撞箱无碰撞、脚下可靠、身体无液体，并排除玻璃方块。
- 封闭装饰玻璃夹层即使内部为空气，也不会进入出口连通分量。
- 正常击败、失败、实体丢失、服务器重启和调试结束统一调用 `GeburahEncounterService.finish`。
- 清理入口可重复调用，不重复恢复或反转结果。

### 3.2 生命、架势与 EGO

- 第一条生命归零后截断溢出伤害，恢复 `50%` 最大生命并进入 EGO。
- 后续生命归零判定最终击败。
- 普通抗性为 `0.6`；EGO 红白黑 `0.2`、蓝 `0.3`；衰竭全部 `1.5`。
- 架势上限 `100`，支持前摇、攻击生效和后摇倍率以及单次最多 `15` 点架势伤害。
- 失衡持续 `3` 秒，结束恢复到 `50`，随后保护 `2` 秒。
- EGO 失衡额外减少 `10` 点 EGO。
- EGO 有有效目标时每 `40` tick 消耗 `1`，每 `100` tick 恢复 `5` 架势。
- 衰竭冷却 `1500` tick，黄色条从空逐渐上涨，完成后再次进入 EGO。
- EGO 展现动画期间拒绝伤害。

### 3.3 合法伤害

- 仅参与者和出逃异想体可伤害 Geburah。
- 玩家普通近战要求服务端蓄力 `>= 0.9`。
- 实际责任人距离超过 `5` 格时整次伤害无效，并恢复 `33` 架势。
- 调试伤害通过实体正常 `hurt -> LivingDamageEvent -> GeburahCombatState` 链路结算，可观察真实阶段切换。
- `DotHelper` 的施加者已扩展为任意 `LivingEntity`，没有修改 `red_shoes_bleed` 的 ID 或结算规则。
- 新增独立 `geburah_bleed` 和治疗减半入口，具体招式叠层将在动作系统接入。

### 3.4 参与者会话

- 启动前要求全部在线真人玩家位于设施维度。
- 启动人数不超过 `5` 时共享 `5` 次复活；超过 `5` 时不提供共享复活。
- 战斗死亡由遭遇服务接管，因此物品栏、经验和装备不依赖 `keepInventory` 也不会掉落。
- 有复活次数时立即恢复战斗；无次数时转旁观。
- 中途登录的非参与者不加入名单并转旁观。
- 参与者退出不扣生命；重新登录恢复其存活或淘汰状态。
- 场上没有在线、非旁观、未淘汰参与者时立即失败。
- 结束后在线参与者和观察者恢复生存并传送到有效个人出生点；失效时回退到世界出生点。
- 离线旁观者使用玩家持久标记在下次登录时完成一次恢复。

### 3.5 其他系统

- AI 员工在遭遇期间停止运行时 AI，获得无敌，并在结束时恢复原无敌状态。
- 黑森林事件在 Geburah 核心抑制期间不响应鸟类出逃或重评估，结束后自动恢复。
- 三条固定 Boss Bar 顺序为生命、架势、EGO。
- 从原 `.bbmodel` 导出了 `77` 根骨骼、`526` 个立方体和 `35` 段动画；转换脚本保留在本目录。
- 从原游戏只读导出并转码了 `Distorted Night` 与 `Insignia Decay`。
- 第一阶段按两首曲目顺序播放并循环整个队列；进入 EGO 后切换为 `EGO解放.ogg` 单曲循环。
- 已导入并注册九个来源招式音效。

## 4. 调试命令

```text
/lobotocraft debug suppression geburah status
/lobotocraft debug suppression geburah set health <value>
/lobotocraft debug suppression geburah set posture <0..100>
/lobotocraft debug suppression geburah set ego <0..100>
/lobotocraft debug suppression geburah set phase <normal|ego|exhausted>
/lobotocraft debug suppression geburah set ego_cooldown <ticks>
/lobotocraft debug suppression geburah set combo_depth <0..6>
/lobotocraft debug suppression geburah set cooldown <move> <ticks>
/lobotocraft debug suppression geburah damage <amount>
```

`combo_depth` 和招式冷却目前是动作控制器的调试占位数据，已经保存在实体上，但尚未驱动未完成的招式 AI。

## 5. 下一批实现

以下内容仍按 `PRD.md` 实现，不能把本轮切片误认为完整 Boss 战：

- `GeburahMove` 数据表、效用评分和动作时间轴。
- 普通 1 至 3 段、EGO 3 至 6 段无缝连招，邻接招式不得相同。
- 陷阵之志、血雾弥漫、尸横遍野及其冷却、位移、多人条件和命中分支。
- 每个责任人的三次远程反制计数、安全背后传送和高速追击降级。
- 独立控制锁、流血叠层触发、异常状态转速度、速度/缓慢增伤和异想体伤害策略。
- 跨楼层设施导航和安全动作节点切换目标。
- 统一客户端屏幕效果合成器、网络事件和完整/降低/关闭设置。
- 完成奖励仍是 PRD 占位项，本轮没有擅自实现。

## 6. 验证

- `GeburahCombatStateTest` 覆盖首次转阶段、最终击败、架势倍率、失衡保护、EGO 消耗、衰竭恢复和抗性表。
- `GeburahEncounterDataTest` 覆盖共享五次复活和大于五人无共享复活。
- 模型、动画、声音 JSON 可解析。
- 两首原游戏 BGM 的导出时长分别约为 `151.43` 秒和 `252.76` 秒。
- Forge GameTest 服务器能够完成注册表、世界和设施导航缓存启动；完整套件超过 5 分钟工具上限，执行到设施导航路线 `8/35` 时被停止。
- GameTest 日志中已经出现两个与本功能无关的既有失败：黑森林门重复保护和 Green Noon 隔墙射击恢复。本轮没有修改这些失败。
