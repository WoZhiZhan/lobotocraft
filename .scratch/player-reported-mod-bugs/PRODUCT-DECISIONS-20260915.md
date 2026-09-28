# V027 产品裁决补充（2026-09-15）

来源：用户在解释 D-03～D-06 后明确回复：“2：开始工作的一瞬间就算解除；d03是的；d04保留”。

后续用户回复“d06:yes”，确认工作已经开始后才触发融毁的情形也在工作期间停表。

本记录补充 `docx/V027修复复查_交接_20260912.md` 与 09-15 交接中的待裁决结论；历史报告保留。

| 项目 | 裁决 | 边界 |
| --- | --- | --- |
| D-05 融毁解除时机 | 开始工作的一瞬间解除，不要求完成工作 | 未裁决融毁工作是否推进进度条等其他规则 |
| D-03 HERO 女王目标身份 | 同意 HERO 女王成为其他所有出逃异想体的合法攻击目标 | 保留各实体已有被动规则及状态限制，不要求每个实体无条件主动攻击 |
| D-04 大鸟受伤、惩戒鸟反击 | 保留现有规则 | 不扩大成三鸟主动索敌，也不撤销既有特殊状态保护 |
| D-06 工作中融毁停表 | 工作期间仍活跃的融毁暂停倒计时，包括工作开始后触发的融毁 | 工作结束后，仍活跃的融毁恢复计时；不改变 D-05 开始工作立即解除既有融毁的规则 |

## 当前代码核对

- `src/main/java/com/lobotocraft/facility/FacilityManager.java` 的 `onWorkStarted` 已对活跃融毁调用 `MeltdownLifecycle.resolve`，符合 D-05 时机决定。
- `src/main/java/com/wzz/lobotocraft/entity/abnormality/EntityQueenOfHatred.java` 的 `isAbnormalityCombatTarget` 已包含 HERO 状态并排除 HERO_DYING，保留。
- `src/main/java/com/wzz/lobotocraft/entity/abnormality/EntityLargeBird.java` 的 `hurt` 已对合法女王攻击放行，仍有黑森林事件保护。
- `src/main/java/com/wzz/lobotocraft/entity/abnormality/EntityPunishingBird.java` 的 `hurt` 已在出逃且被合法女王攻击时进入愤怒反击，仍有黑森林事件保护。

本轮仅记录决定与静态核对，未改运行逻辑、未运行测试。规格争议已明确，不代表补齐了 D-04 回归测试。

## D-06 补充核对

`FacilityManager.reconcileMeltdownTimeRates` 对允许工作中目标的活跃融毁，依据 `WorkManager.isAbnormalityBeingWorked` 在零速率与正常速率之间切换；仅速率改变时调用 `setTimeRate`。该分支符合用户确认的停表方向。本次为静态核对，未运行新增场景测试，不把规格确认等同于完整验证。

## D-12 用户补充规格

- 献祭以随机抽奖选中对象；用户进一步明确“只抽 AI 员工和文职”，真人玩家不得进入候选池。取消旧文档中的手选执行者方案；知识共享中的“所有玩家”不扩大献祭候选范围。
- 献祭替换第四个压迫槽，不新增第五槽；旧文档中“独立新增、不占普通工作”的描述由本决定覆盖。
- CENSORED 与审判鸟体积一致包括视觉大小与碰撞箱；这不额外授权改变伤害或攻击规则。
- 异想体信息解锁按全设施所有玩家共享处理；这不意味着共享个人 E.G.O 物品、授权或消费余额。
