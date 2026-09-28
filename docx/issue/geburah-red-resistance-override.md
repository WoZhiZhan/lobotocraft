# Geburah 对玩家 RED 抗性结算

状态：ready-for-human

## 问题

红姐攻击玩家时，玩家的 RED 抗性应按 `1.0` 处理，但部分攻击仍会受到玩家装备抗性影响，普通阶段还可能重复应用抗性。

## 修复方案

- 仅 EGO 展现阶段的 Geburah 对玩家 RED 伤害进入中立 RED 抗性作用域；未 EGO 展现阶段正常使用玩家 RED 抗性。
- 玩家彩色伤害的实际结算入口读取该作用域，将 RED 抗性替换为 `1.0`。
- 删除旧的 Forge 事件二次正向乘算，保留负抗性对应的治疗/免疫分支。

## 验证

`GeburahDamageTuningTest` 与全部 `core_suppression.geburah` 定向测试通过。
