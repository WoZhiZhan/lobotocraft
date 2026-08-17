# 废墟图书馆红雾素材定位

反编译程序集：

`F:\SteamLibrary\steamapps\common\Library Of Ruina\LibraryOfRuina_Data\Managed\Assembly-CSharp.dll`

Unity 资源：

`F:\SteamLibrary\steamapps\common\Library Of Ruina\LibraryOfRuina_Data\resources.assets`

## E.G.O 展现

`PassiveAbility_250422.PlayChangingEffect()` 切换 `TheRedMist` 皮肤并调用 `SetParticle()`。

`SetParticle()` 的行为：

- 实例化 `Prefabs/Battle/SpecialEffect/RedMistRelease_ActivateParticle`。
- 播放 `Sounds/Battle/Kali_Change`。
- 展现粒子实际引用 `붉은꽃잎_진` 与 `붉은꽃잎_백` 两张深红/白色花瓣贴图。

本目录的 `Kali_Change.wav` 为 Unity `AudioClip` Path ID `7252` 的原始 PCM 导出。正式模组资源为 `geburah_ego_manifest.ogg`。

## 殷红迷雾书页刀光

| 类型 | 原版效果对象 | 贴图 |
|---|---|---|
| 横斩 | `TheRedMist_H` | `붉은안개_횡베기` |
| 纵斩 | `TheRedMist_J` | `붉은안개_종베기` |
| 突刺 | `TheRedMist_Z` | `붉은안개_찌르기` |
| 大切断横 | `TheRedMist_S1` | `붉은안개_대절단 가로` |
| 大切断纵 | `TheRedMist_S2` | `붉은안개_대절단 세로` |

共同风格是黑红色粗糙笔触、撕裂边缘和嵌在刀光中的白色眼状高光。本模组刀光据此调整实时网格颜色、宽度和高光，不直接把整张二维效果图作为普通粒子播放。
