# 红姐 E.G.O 横斩／突刺试验增强与回退（2026-10-05）

用户授权：“试试，但保留回退的后路”。仅实施本轮讨论的横斩／突刺世界空间视觉增强。

## 回退优先用客户端开关

```text
/lobotocraft_client geburah_ego_attack_trial
/lobotocraft_client geburah_ego_attack_trial false
/lobotocraft_client geburah_ego_attack_trial true
```

无参数查询；`false` 当场清拖尾与新增接触特效，之后走本轮前的刀光绘制分支及6 tick拖尾查询。`true`重新启用。默认开启以供试用；命令保存客户端配置，重启后的开关保留仍需实机验收。

也可退出游戏后，在 `config/lobotocraft-client.toml` 的 `[visuals]` 设置：

```toml
geburahEgoAttackTrial = false
```

只影响自己的横斩／突刺表现。**不关闭砸地碎块、不改变服务端伤害、不回退网络协议**；双方仍需使用协议43的同构建。

## 表现

- 横斩：沿已有三个刀刃定位点的真实轨迹绘制，近期部分加宽，追加一层暗红撕裂边。试验采样寿命上限8 tick（旧6）；延长部分三次衰减，避免亮斩面像常驻伤害范围。采样总上限仍18个，实际可见尾长同时受帧采样上限影响。
- 突刺：枪尖定位点轨迹的肉质尾迹更窄，追加枪尖亮点和两条短速度线，保留原6 tick上限，不扩大为光柱。
- 有效命中：横斩短斩痕；突刺沿实际攻击方向垂直的小型冲击环和短向前碎光。接触点取目标碰撞箱近攻击者一侧的身体位置，属于视觉近似，不声称精确模型碰撞。
- 命中前记录位置，以免镇压后回收/传送使视觉出现在收容单元。成功承伤或成功镇压才记录，空挥／拒绝／未镇压零伤害没有新增接触效果。
- 当前E.G.O阶段和发动连招身份双检查；客户端只增强EGOslash/EGOstab，不增强EGOwillofbattle、砸地、蓄力、普通状态、力竭后的新采样。已发生效果自然快速消散，切换开关立即清除。
- 不改战斗范围、时序、攻速、骨骼动画、伤害/抗性/位移/出血、镜头、声音。不新增慢动作或全屏效果。

## 限制与生命周期

- 每轮最多4接触点，合成一个clientbound包发同维度48格内玩家；已有包ID不变，追加白名单末尾，协议42→43。
- 客户端最多12处接触效果，7 tick内清理，默认16段环、降低粒子8段、最少4段，24格外减量、48格外不显示；渲染单次批量提交、保留深度检测。
- 复用现有客户端tick及世界卸载清理入口；关试验或关总特效同样清理新增命中特效。
- 视觉种子由实体ID与游戏时间派生，不能调用实体的战斗随机数生成器；不影响伤害随机序列。

## 验证

- `build-geburah-ego-slash-stab-final-20261005-f.log`：编译、JUnit **680/680**、0失败0错误。
- 新7项单测覆盖严格动作/阶段资格、关闭后的旧拖尾等价及寿命、延长尾部与阶段切换、拒绝/零伤害/镇压、包往返/方向、非法参数、恶意计数在分配前拒绝。
- `run-geburah-ego-slash-stab-20261005-a/gametest.log`：首轮横斩夹具失败，因误用 `canEscape=false` 的一罪，基类拒绝普通攻击；不是试验导致生产伤害失效。更换为正常出逃的杀人魔并增加夹具合法状态断言，未改生产伤害限制。
- `run-geburah-ego-slash-stab-20261005-b/gametest.log` 与最终 `run-geburah-ego-slash-stab-final-20261005-c/gametest.log`：均 **2/2通过**、正常退出0。含上一轮砸地无破坏回归，以及本轮真实实体横斩/突刺入口、单次承伤事件和未额外消费战斗随机数的验证。最后减少普通/超预算目标的接触点分配后，最终源码再次通过。
- 台账3套脚本通过，Bug/Todo/归档为3/9/122；原8条水滴Todo的未授权6条未动，另加一条可回退试验记录，不误归档为已实机满意。
- 客户端截图、多人显示、帧耗与关闭/重启持久化未实机验收；不以自动测试替代视觉验收。

## 源码回退证据

改动前精确工作区文件副本：

`D:\GongzuotaiD\CodexDesk\2026-09-12\d-mcmodfolder-lobotocraft-integration-9d26841-docx\work\backup-geburah-ego-slash-stab-20261005`

共12份：客户端配置、命令、刀光渲染/状态、Geburah实体渲染/逻辑、包白名单/协议、客户端事件、台账、命令文档、CONTEXT。副本保留了此前砸地、融毁与其他轮次的未提交工作。基准HEAD仅供定位：`cc1d8528c67c7ce96811fb1a95f33ca2a58289fe`，**HEAD不是完整的本轮前状态**。

新增文件：`GeburahEgoAttackVisuals.java`、`GeburahEgoHitEffects.java`、`GeburahEgoContactPacket.java`、`GeburahEgoAttackVisualGameTests.java`、`GeburahEgoAttackTrialTest.java`、`GeburahEgoContactPacketTest.java`及本报告。

若将来要彻底移除源码，按副本对比本轮改动逐段移除，保留后续更改，并重新核对包白名单/协议。不要直接整文件覆盖、整仓库reset或删除公共配置文件。默认回退使用开关，安全且无需重编译。

未自动提交混杂工作区，未打发行JAR。
