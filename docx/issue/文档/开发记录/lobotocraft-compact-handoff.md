# LobotoCraft Compact Handoff

你正在继续 LobotoCraft 的长期开发对话。不要重新开始或重复已完成工作。

## 必须遵守

- 正确工作区始终是 `D:\mcmodfolder\lobotocraft-integration-9d26841`。
- 即使环境显示当前目录在 F 盘，也不要在 F 盘项目工作；执行命令必须显式指定上述 D 盘目录。
- GitHub 仓库：`https://github.com/WoZhiZhan/lobotocraft`。
- 不操作前台窗口、鼠标或用户正在运行的客户端。
- 不覆盖、删除或回退用户已有改动；不要合并上游整个 `main`。
- 以当前项目为主体，只选择性迁移需要的上游功能。
- 修改后按风险运行编译、JUnit 和独立 GameTestServer。用户很重视服务端真实流程验证、根因修复、旧存档兼容和中英文适配。
- 不替用户点击 GitHub PR 的最终创建或合并按钮；分支可以按已授权范围提交和推送。

## Git 状态

- 当前分支：`feature-censored-port`
- 基础分支：`integration-stabilization`
- 工作区干净，当前 HEAD 与远端一致。
- `4262f4a fix: set lumberjack attack weights`
- `9db5feb feat: port CENSORED abnormality`
- 基线提交：`2a742e9 feat: stabilize integration systems and lifecycle handling`
- 原有分支 `integration-fixes-20260805` 必须保留。
- PR 创建页：`https://github.com/WoZhiZhan/lobotocraft/compare/integration-stabilization...feature-censored-port?expand=1`

## 已完成：数据删除迁移

上游来源提交为 `4c7f554 添加了三个实体`。没有合并上游 main，而是迁移素材并重写生命周期：

- 主实体 `EntityCensored` 使用单实体双模型。
- 收容状态使用 `o_03_89` 模型和动画；出逃状态使用 `censored` 模型和动画。
- 附属实体为 `EntityCensoredMinion`，使用 `censored1` 素材。
- O-03-89、ALEPH、BLACK、PE 28、计数器 2。
- 保留上游普攻、牺牲墓碑或尸体、范围 WHITE 伤害和生成附属体机制。
- 出逃时保证 `NoAI=false`；镇压、换天和跨维度返回复用当前 `AbstractAbnormality` 的统一收容返回生命周期。
- 已注册实体、属性、渲染器、刷怪蛋、音效和中英文名称。
- 上游没有设施自动投放、故事和 E.G.O，因此目前有意不加入这些内容；支持刷怪蛋和 `/summon`。
- 关键实现参考提交 `9db5feb`，不要重新迁移或恢复上游的三个主实体方案。

验证结果：

- `gradlew build` 成功。
- 数据删除 GameTest 2 条通过：收容/出逃/镇压状态，以及墓碑牺牲后生成附属体。
- 当时 `lobotocraft` 命名空间共 24 条必需 GameTest 全部通过。
- 构建产物：`D:\mcmodfolder\lobotocraft-integration-9d26841\build\libs\lobotocraft-0.2.1.jar`

## 已完成：热心的樵夫概率

提交 `4262f4a` 已将攻击选择从旧的动态机制：

`20% + 每次普通攻击增加 25%，最高 95%`

改为：

- 特殊攻击冷却允许时，特殊攻击固定 35%。
- 普通攻击固定 65%。
- 特殊攻击原有 8 至 15 秒冷却仍保留。
- 已移除旧的 `normalAttackStack` 及其 NBT 状态。
- `compileJava` 成功，热心的樵夫专用 GameTestServer 4/4 通过。

## 项目历史上下文

此前大量稳定性修复、E.G.O 描述多语言重构、融毁生命周期、展会恢复工具、员工 AI、异想体返回位置等工作已集中在 `2a742e9`。需要了解时查看该提交和仓库文档，不要凭 compact 后的缺失上下文重做。

用户通常希望：先准确检查当前实现和根因，再修改；高风险问题要说明为什么和方案；完成修改后用服务端 GameTest 模拟真实玩家流程。若用户明确要求只分析或等待批准，不要提前改代码。

## 下一步

当前请求已经完成。等待用户提出下一项任务；开始新任务前先在 D 盘工作区检查 `git status` 和相关代码，不要自动创建新的无关改动。

## Suggested Skills

- `diagnose`：用户报告具体 bug、崩溃、性能或同步问题时使用。
- `review`：用户要求审查分支、PR 或全量自检时使用。
- `improve-codebase-architecture`：用户明确要求架构重构或可维护性分析时使用。
