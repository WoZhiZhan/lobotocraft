# 原版核心抑制素材清单

本目录从《脑叶公司》原版 Unity 资源中提取以下核心抑制素材：

- 福利部：Chesed
- 中央本部：Tiphereth
- 记录部：Hokma，原版资源内部拼写为 `Chokhmah` / `Chokma`
- 研发部：按当前需求对应 Binah；原版通常称为抽取部

原始游戏目录：

`F:\SteamLibrary\steamapps\common\LobotomyCorp\LobotomyCorp_Data`

主要资源文件：

`resources.assets`

反编译源码参照目录：

`F:\1pyCode\mcmodfolder\lobotomy\reference_assets\lobotomy_corporation\source`

## 目录说明

每个部门均包含：

- `audio/*.wav`：Unity `AudioClip` 解码出的原始 44.1 kHz 双声道 PCM WAV。
- `audio/*.ogg`：使用 FFmpeg `libvorbis -q:a 7` 转换的模组可用版本。
- `textures/core/`：从对应 `CoreAnim` 预制体沿材质依赖追踪到的实际战斗贴图。
- `textures/ui_candidates/`：按原版资源名筛选出的抑制框体、背景、滤镜和过载 UI；同时保留 `Texture2D` 原图与可裁切的 `Sprite` 导出。

完整的 Unity 类型、对象名、Path ID、尺寸、时长、重复对象和 SHA-256 位于 `EXPORT_INDEX.tsv`。

## 音乐

| 部门 | 阶段 | 原版资源路径 | 时长 |
|---|---:|---|---:|
| Chesed | 1 | `Sounds/BGM/Boss/Chesed/1_Theme_-_Blues_Man` | 125.337 秒 |
| Chesed | 2 | `Sounds/BGM/Boss/Chesed/2_Battle_-_Urgent_Encounter` | 117.632 秒 |
| Tiphereth | 1 | `Sounds/BGM/Boss/Tiphereth/1_Eternal` | 156.229 秒 |
| Tiphereth | 2 | `Sounds/BGM/Boss/Tiphereth/2_Dark Fantasy Scene` | 129.524 秒 |
| Hokma | 1 | `Sounds/BGM/Boss/Chokhmah/1_Tilarids - 090909090` | 156.056 秒 |
| Hokma | 2 | `Sounds/BGM/Boss/Chokhmah/2_Tilarids - circle-rombed oxygen` | 237.714 秒 |
| Binah | 1 | `Sounds/BGM/Boss/Binah/1_Jukai` | 114.064 秒 |
| Binah | 2 | `Sounds/BGM/Boss/Binah/2_Haunted Streets_1` | 263.542 秒 |

## 核心战斗贴图

| 部门 | CoreAnim 根对象 | 实际材质贴图 |
|---|---|---|
| Chesed | `ChesedCoreAnim` | `chesedBoss` |
| Tiphereth | `TipherethCoreAnim` | `tipeboss` |
| Hokma | `ChokhmahCoreAnim` | `chokma` |
| Binah | `BinahCoreAnim` | `bna`、`CFX_T_Flame1_BW_AB`、`CFX4_T_FireSoft A8`、`Spark`、`smokeTemp` |

Binah 的后四张图是其核心实体预制体实际引用的火焰、柔光火焰、火花与烟雾粒子贴图，不是按名称猜测加入。

## Chesed 伤害机制贴图

原版 `source/ChesedBossUI.cs` 的 `RWBPUnit` 为红、白、黑、蓝（Pale）四种危险类型分别保存 enabled/disabled Sprite。通过 `ChesedBossUI` 预制体的序列化引用追踪到的 Sprite 如下：

| 类型 | disabled | enabled |
|---|---:|---:|
| 红（R） | `UI_2_1`（Path ID 16182） | `UI_2_2`（Path ID 16343） |
| 白（W） | `UI_2_3`（Path ID 16437） | `UI_2_5`（Path ID 16528） |
| 黑（B） | `UI_2_7`（Path ID 16611） | `UI_2_9`（Path ID 16690） |
| 蓝（Pale） | `UI_2_12`（Path ID 16767） | `UI_2_13`（Path ID 16844） |

中央固定图标为 `UI_2_18`（Path ID 16915）。模组正式资源位于 `textures/gui/core_suppression/chesed/damage/`，并按 `red/white/black/pale_{enabled,disabled}.png` 与 `core.png` 命名。四个图标按原作方向在屏幕上方围绕中央图标向内排列。

Chesed 的相机雨幕使用 `CameraFilterPack_Atmosphere_Rain_FX`（Texture2D Path ID 865，1024×1024）。该贴图将雨线、风暴噪声和水滴飞溅分别打包在 R、G、B 通道；模组正式资源为 `textures/effect/core/chesed_rain_fx.png`。原 `CameraFilterPack/Atmosphere_Rain_Pro` 像素 shader（Shader Path ID 5250）的 DXBC 已反汇编，并据其五层 R 通道采样、B 通道边缘响应和 G 通道风暴采样结构移植。

## 导出结果

- BGM：8 首，保留 8 个 WAV 和 8 个 OGG。
- 核心战斗贴图：8 张。
- 专属 UI 候选图：55 张。
- PNG 已逐张完成尺寸与可读取性检查。
- OGG 已通过 `ffprobe` 检查，时长与 Unity `AudioClip.m_Length` 一致。

## 复现

重新导出时运行：

```powershell
python .scratch\original-core-suppression-assets\extract_assets.py
```

脚本会覆盖同名导出文件并重新生成 `EXPORT_INDEX.tsv`。当前素材仅存放于 `.scratch`，尚未注册到模组 `sounds.json` 或正式材质目录。
