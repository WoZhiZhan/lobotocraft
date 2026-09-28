# D-12 补齐进度（2026-09-16）

## 当前用户规格

权威决定见 `../player-reported-mod-bugs/PRODUCT-DECISIONS-20260915.md`；相关术语见根目录 `CONTEXT.md`。

献祭为随机抽奖，仅含 AI 员工与文职，明确排除真人玩家，替换第四个压迫槽；CENSORED 视觉与碰撞箱均对齐审判鸟；异想体信息解锁由全设施所有玩家共享。献祭候选范围已获用户确认。

## 第一批：监控暂停入口

已实施：

- 新增 `FacilityPauseButton`，管理界面与监控界面复用同一控件和请求路径。
- 监控顶部在 TT2 左侧增加按钮，并支持空格切换；住户选择和改名模态仍优先处理输入。
- ON/OFF、冷却及选中状态读取服务端同步数据，中英文文案均已补齐。
- 沿用 `ToggleFacilityPausePacket` 和服务端公司维度/40 tick 冷却校验。当前代码实际未要求 OP，未按过时报告新增权限限制。
- 窄屏隐藏右侧公司名称，为两个控制按钮留出空间。

验证：

- `run-d12-pause-unit-20260915`：compileJava、processResources、JUnit 635/635 通过。
- 随后仅调整监控窄屏布局，再由 `run-d12-pause-meltdown-20260915` 重编译；融毁专项 6/6 必需 GameTest 通过。
- 中英文 JSON 可解析；所改跟踪文件 `git diff --check` 通过。
- 尚未进行客户端点击/截图验收，自动化结果不代表已验证 GUI 外观。

运行目录根：
`D:\GongzuotaiD\CodexDesk\2026-09-12\d-mcmodfolder-lobotocraft-integration-9d26841-docx\work`

本轮环境为 PowerShell 7.6.5，runner 正常完成；之前交接提示的 PowerShell 5.1 stderr 问题仍需在使用旧版 shell 时注意。

## 第二批（2026-09-16）

已实施的内容：

- `AbnormalityCommandPolicy` 在普通工作映射前识别 CENSORED 第四槽、雇佣兵第三槽和军团第三槽。
- `UniqueWorkRequest` 负责随机分配、导航、动画结算、占用及清理；献祭只允许 AI 员工与文职，明确排除真人。军团保护使用同一分配/导航/清理流程；雇佣兵委托提交给既有追击、战斗和归还流程。
- 两个界面显示 V/S/P 特殊槽。献祭客户端只展示服务端抽签，不能选人；委托有目标选择窗，沿用目标道具消耗和既有目标限制；监控保护使用现有 AI 员工选择面板。
- 增加房间瞬态占用同步，献祭/保护派遣中禁止普通工作抢占。重启时解除没有对应请求的遗留无敌状态。
- CENSORED 碰撞箱为 2.0 × 5.7；渲染高/宽缩放为 1.5/2.0，与审判鸟注册参数一致。实际模型轮廓尚未实机验收。
- `FacilityKnowledgeData` 保存设施知识并合并登录玩家的旧资料；`FacilityKnowledgeService` 将资料同步给所有玩家及 AI 员工。个人装备授权、库存和余额不参与共享。
- 已打开的图鉴、工作窗和监控候选列表接收知识更新。图鉴不再把尚未获服务端确认的请求直接显示为已解锁。
- 网络协议已更新到 39，客户端和服务端需要使用同一版本。

阶段性验证：

- `run-d12-protected-fixture-20260916`：5/5 献祭专项通过。
- `run-d12-targeted-final-20260916`：20/20 相关 JUnit、6/6 献祭专项通过，包括重启清理。
- 随后加入雇佣兵、军团特殊槽与图鉴以外的知识刷新，`run-d12-unique-family2-20260916` 已通过 29/29 相关 JUnit 和 8/8 必需 GameTest。
- `run-d12-localization-20260916` 已通过 9/9 本地化资源测试。最终定向验证合计 38 项 JUnit、8 项 GameTest，未跑完整默认集合。
- 最终报告：`D:\GongzuotaiD\CodexDesk\2026-09-12\d-mcmodfolder-lobotocraft-integration-9d26841-docx\outputs\D12-implementation-20260916.md`。XML 和日志保存在该工作目录的 `outputs\D12-evidence-20260916`。

测试夹具要点：必须先通过 `StructureUpdater` 完成设施初始化，再搭场景；保存并暂时移除测试体积内的方块保护、保存方块实体 NBT；铺设后刷新 `FacilityNavigationCache`；结束时恢复方块、NBT、保护和缓存。随机测试隔离已有候选，并在结束时恢复它们，不能把生产抽签改成指定对象。

## 验证边界与后续

- 用户 2026-09-16 指示：非高危改动不必跑全量测试。后续按改动范围选择定向测试；runner 已增加 `-TestFilter`。
- 未跑客户端视觉/点击验收，也未做真实双客户端联机观察。渲染参数相同不代表不同模型的实际轮廓已经严格等高等宽。
- 当前仓库没有魔弹实体；原文中的魔弹特殊槽仍是未来接入案例，本轮不新增异想体。
- 未提交 git commit；不回退用户改动。默认集合绿午和 longsamefloor 的既有时序非稳定继续独立跟踪，本轮不宣称已解决。
