# Todo 水滴 #8：监控终端警报等级

日期：2026-10-06。用户授权“监控终端警报等级第8条，顺手做了”。

## 范围与实现

要求：监控终端能持续查看当前设施警报等级，不以只闪4秒的警报图标代替。

- 复用服务端 `EscapeTracker`、`EmergencyAlarmPolicy` 和 `EscapeAlertPacket`。服务端 wire -1/0/1/2 对应NORMAL/I/II/III，界面不重新计算出逃数量或紧急分数。
- `EscapeAlertManager` 持有小型 `FacilityAlertClientState` 接收投影；每次包先更新投影，再执行已有音乐规则。核心抑制暂停BGM只清播放状态，不能把设施警报清成正常。
- `clear()` 沿已有世界卸载生命周期清投影为未知；关闭监控不清设施警报。未同步时明确“同步中”，不伪装正常。
- `OpenRoomMapPacket.sendTo` 首次打开补发现有警报包；普通刷新不加发。随后升级、降级、日切通过既有广播更新。
- 监控顶栏警报位于暂停按钮左侧，常驻只读文本＋边框，正常绿、I黄、II橙、III红，提供中英文。预留标签宽度，标题和统计按剩余空间裁剪，不覆盖暂停/TT2按钮。
- 不改包布局、白名单、服务器阈值/恢复、核心抑制音乐逻辑、闪烁时长或其他Todo。协议保持44。

## 自动验证

- `work/build-monitor-alert-20261006-b.log`：编译成功、JUnit **693/693**，0失败0错误。
- 新4项：首次未知及4档映射；升级后降级/日切正常/卸载未知；重复同步与边界；真实 `EscapeAlertManager.onAlertReceived` 在核心音乐占用时仍保留警报，暂停/tick不清，正常包与卸载正确更新。
- 核心音乐占用测试在无Minecraft窗口的JUnit中只暂设核心活动门，finally恢复；实际警报接收器没有mock。首轮用正常核心update会触发屏幕清理导致headless测试失败，已修测试夹具，未为测试绕过生产规则。
- 中英文键值、格式参数与生产静态引用由既有LocalizationResourcesTest检查。
- 按用户非高危约定，不跑全量GameTest。客户端真实显示、GUI缩放及联机实时变化仍待实机验收；未打新JAR。

## 状态

台账第8条已改、自动测试通过；保留Todo分类，未冒称实机验收。

改动前固定Git点：`69c23613f5eb59781243cfcde14347f31426a799`。本项review待完成。
