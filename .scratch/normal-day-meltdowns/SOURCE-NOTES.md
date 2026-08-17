# 普通日逆卡巴拉熔毁源码对照

Status: ready-for-human

更新日期：2026-08-17

## 目标

移除“只有核心抑制日才生成普通熔毁”的模组旧机制，使普通日和核心抑制日都按照《脑叶公司》原作推进逆卡巴拉熔毁；已经完成核心抑制的部门获得普通熔毁免疫。

## 原作源码

现成反编译源码位于：

`F:\1pyCode\mcmodfolder\lobotomy\reference_assets\lobotomy_corporation\source`

主要依据：

- `CreatureOverloadManager.cs`
  - `OnStageStart()`：每天读取已完成或关闭的核心抑制任务，建立免疫部门集合。
  - `SetQliphothOverloadLevel()`：熔毁数量按 `(收容单元数 × 熔毁等级 + 9) / 10` 计算，并受可工作收容单元数量限制。
  - `ActivateOverload()`：普通日与核心抑制日共用同一套普通熔毁入口；当前核心抑制部门始终不进入候选池。
  - 当该阶段存在考验时启动考验，不再同时生成普通熔毁。
  - 已完成核心抑制的部门通常从候选池排除。
  - Tiphereth、Binah、Hokma 核心抑制期间暂时忽略此前完成部门的免疫，但当前抑制部门仍排除。
- `UseSkill.cs`
  - `OnWorkStart` 后，如果目标是普通工作型异想体且当前不处于熔毁状态，立即调用 `AddOverloadGague()`；因此计量推进点是工作成功开始，而不是工作结算。
- `BossEggBase.cs`
  - 终末鸟鸟蛋直接触发的熔毁使用独立候选逻辑，不读取已完成部门奖励，但仍排除当前核心抑制部门。

## 模组实现

1. `MeltdownPolicy.canActivate` 只检查当前阶段是否已经触发考验，不再要求核心抑制处于激活状态。
2. `WorkManagerMixin` 只在 `WorkManager.startWork(...)` 成功返回后调用 `FacilityManager.onWorkStarted(...)`，后者推进一格熔毁计量并在达到阈值时进入正常阶段触发路径，与原作 `UseSkill.cs` 的时机一致。
3. `QLIPHOTH_OVERLOAD` 不再属于“核心抑制结束即移除”的生命周期原因，因此普通日生成后不会在下一服务器 tick 被清理。
4. `MeltdownDepartmentPolicy` 维护 Sefira 与模组部门 ID 的明确映射：
   - Malkuth -> `control`
   - Yesod -> `information`
   - Hod -> `training`
   - Netzach -> `safety`
   - Tiphereth -> `central`
   - Chesed -> `welfare`
   - Geburah -> `disciplinary`
   - Binah -> `extraction`
   - Hokma -> `record`
5. 普通逆卡巴拉熔毁读取 `CoreSuppressionData.completedMask`，排除已经完成核心抑制的部门。
6. 当前正在核心抑制的部门对普通熔毁和独立特殊熔毁都免疫。
7. 终末鸟鸟蛋等独立特殊熔毁不继承“已完成部门免疫”，与原作 `BossEggBase` 保持一致。

## 保留差异

模组暂时继续排除所有工具型异想体、已出逃异想体、逆卡巴拉计数器不大于零的实体，以及尚未实现正常工作交互的特殊收容对象。原作只排除无法工作、已装备给员工的工具及少数硬编码对象；在模组补齐这些工具型工作流程前，直接照搬会生成无法解除的熔毁。

## 验证

- 普通日非考验阶段允许普通熔毁。
- 普通工作成功开始后推进一格熔毁计量，达到阈值后进入与调试推进相同的阶段触发路径。
- 普通逆卡巴拉熔毁脱离核心抑制仍保留并正常计时。
- 已完成部门在普通日被排除。
- 当前核心抑制部门始终被排除。
- Tiphereth、Binah、Hokma 的原作例外由纯策略测试锁定。
- 终末鸟鸟蛋不继承已完成部门免疫。
