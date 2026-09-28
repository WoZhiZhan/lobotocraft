# 圣宣 EGO 武器等级

状态：ready-for-human

## 修复

圣宣异想体与护甲等级保持原有定义，EGO 武器研究数据单独标记为 `WAW`，不再复用异想体的 `HE` 等级。

## 验证

`ButterflyFuneralEquipmentTuningTest` 断言武器等级为 `RiskLevel.WAW`。
