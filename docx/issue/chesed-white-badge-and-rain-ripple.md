# Chesed White 徽标与雨幕

状态：ready-for-human

## 修复

- White 徽标尖端改为朝左，并将伤害类型到三角形方向的映射独立成可测试策略。
- 雨滴时间倍率提高到过渡期 `0.95`、稳定期 `0.55`，下落位移同步加速。
- 已按反馈删除水波与 UV 扭曲，仅保留雨滴、雨幕和深蓝色滤镜。
- F3/F4 调试覆盖层或 GUI 打开时，Chesed 后处理改为在 GUI 后执行，避免屏幕特效被覆盖而消失；普通游戏画面仍在 GUI 前执行，保证 HUD 可读性。

## 验证

`ChesedHudOrientationTest`、`ChesedPresentationResourcesTest` 和 `ChesedRainSoundPolicyTest` 定向测试通过。
