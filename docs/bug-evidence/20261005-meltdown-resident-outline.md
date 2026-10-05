# Todo第7条：融毁收容体红色发光轮廓（2026-10-05）

2026-10-04用户单独授权第7条：融毁的收容单元内，正常收容的那个异想体有红色发光边框，出逃不显示融毁红光。其余7项Todo不实施。

## 实现

- 复用权威 `FacilityDayData.activeMeltdowns`，不新建另一份融毁计时/来源记录。
- 公共异想体基类按住户UUID、正常收容状态、所在维度、既有信标绑定及收容扫描范围计算显示资格。附近同编号实体不共享资格；出逃、待回收、死亡、绑定失效或离开范围不显示红光。
- 用独立 `SynchedEntityData` 布尔标志传递显示状态。服务端实体tick按权威状态更新，客户端发光/轮廓颜色读取这个同步状态，无需客户端跨世界读取服务端SavedData。
- 发光走既有 `isCurrentlyGlowing/getTeamColor` 显示入口，颜色为 `0xFF0000`。不染红模型贴图，不用永久Glowing药水或 `setGlowingTag` 抢占发光来源，也不把实体移进临时计分板队伍。
- 融毁结束后最迟下个服务端实体tick撤下融毁显示。出逃 `setEscape(true)` 当场清掉红色标志，并保留原有出逃黄色轮廓；“出逃不显示”指本项红光，不删已有黄色机制。
- 若异想体原有独立Glowing药水/标志或队伍颜色，融毁结束后仍保持原状态。暂停时融毁仍活跃，因此显示保留。
- 新显示标志不写为持久融毁状态；存档重载后按既有SavedData及home-room信息重新计算，避免存档留下永久红光。
- 新增公共实体同步字段会改变子类同步字段索引，网络协议40→41防止旧客户端混用新布局。联机客户端、服务端需要更新同一构建。原融毁75秒、工作开始解除、工作中停表、目标选择和失败处罚不改。

## 改动文件

- `src/main/java/com/wzz/lobotocraft/entity/base/AbstractAbnormality.java`
- `src/main/java/com/wzz/lobotocraft/network/MessageLoader.java`
- 永久回归 `src/main/java/com/lobotocraft/facility/MeltdownOutlineGameTests.java`
- `CONTEXT.md` 和 `docs/bug-fix-tracker.html`

## 自动验证与证据

W = `D:\GongzuotaiD\CodexDesk\2026-09-12\d-mcmodfolder-lobotocraft-integration-9d26841-docx`

- 修复前4个功能回归失败：`W\work\run-meltdown-outline-red-20261005-a\gametest.log`。
- 首轮新显示及原融毁机制10/10：`W\work\run-meltdown-outline-green-20261005-b\gametest.log`，正常退出0。
- 最终扩展：`W\work\run-meltdown-outline-final-20261005-c\gametest.log`，48/48、0 optional失败，正常退出0；含融毁、旧发光迁移、出逃/回收及碧蓝新星生命周期相关用例。
- 最终编译及JUnit668/668：`W\work\build-meltdown-outline-final-20261005-c.log`。

6个新永久用例：

1. 活跃融毁只点亮绑定住户，附近同编号未登记实体不发光，解除后恢复。
2. 出逃立刻没有融毁红色（仍有原黄色），正常状态但离开收容范围也不保留红光。
3. 原有Glowing和蓝色队伍在融毁解除后仍存在，不被清理逻辑误删。
4. 同步数据能让无服务端home-room信息的显示副本获得红色轮廓；真实存读重新派生，过期后清理。
5. 暂停时红光保留，真正开始工作的生产入口解除融毁后红光撤下。
6. 信标删除后，即使原融毁记录仍存在，也不让失去登记绑定的实体继续红光。

## 验证边界

自动回归验证真实状态、显示资格、轮廓颜色及同步数据，不等于客户端像素截图/光效实机验收。未跑完整默认集合，未打发行JAR、未提交Git。总台账第7条只标代码完成与自动回归通过，Todo类别保留，其他Todo继续未改。
