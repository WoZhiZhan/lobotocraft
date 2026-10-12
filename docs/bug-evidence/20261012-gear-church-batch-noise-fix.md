# 齿轮教堂：精确批量噪声性能修复（2026-10-12）

用户在实际验收不通过后授权“继续修复”。首先处理成功候选47秒及拒绝候选4秒级开销，不扩大地形预算/删除安全条件或再次加密spacing。

## 本轮实现与边界

- 确切原版NoiseBasedChunkGenerator且噪声裁剪范围对齐时，粗筛继续原来的精确单列查询；进入完整检查才按16×16计算高度tile。
- 批量使用原版NoiseChunk、同BeardifierMarker/Blender.empty和原生成器同一FluidPicker，读取两个原Heightmap谓词；不复制流体选择规则、不简化高度或采样精度。
- 一个窄Mixin accessor仅读取globalFluidPicker supplier，无字段写入、无实体/世界状态强制绕过；与项目既有Mixin配置同一加载周期。
- Forge access transformer仅开放原版BeardifierMarker类的可见性，直接复用原版单列读取所用的marker；未全局开放所有噪声字段/方法，不替换beardifier或更改生成状态。
- 每tile仅存512个高度int，不保留384层方块数组；所有tile与旧单列缓存均限本次terrain/context。
- 完整选址仍检查每个模板列、相同水面/群系/门外/削12填12，洞穴柱16不变；不增加邻区块加载或生产全局缓存。
- 自定义生成器/子类、噪声范围未对齐或tile不能整齐覆盖cell宽度时保留原getBaseHeight覆盖。
- 验证：三种子水陆/负坐标完整tile逐列与原版对照、既有空/裁剪边界/custom子类、完整已接受footprint、near/far耗时统计和真实生成复验。

原性能失败已有两次实测（44秒、47秒），作为修改前红证据，不用机器速度阈值代替数值正确断言。批量优化未证实数值等价前不能判完成。

## 待收尾

精确差分、性能、自然功能复验。生成率问题仍需随后用相同近域完整调查评估；本次无损优化不应改变选址合格集合，不能声称它直接提高概率。
