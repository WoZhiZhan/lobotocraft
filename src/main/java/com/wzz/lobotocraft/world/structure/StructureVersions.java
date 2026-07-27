package com.wzz.lobotocraft.world.structure;

/**
 * 结构版本号。
 * <p>
 * <b>你每次改完公司的结构 nbt，就把对应的常量 +1。</b>
 * 玩家进入公司维度时，{@link StructureUpdater} 会拿存档里记录的版本和这里的常量比：
 * <ul>
 *   <li>没生成过 -> 完整生成（和以前一样）</li>
 *   <li>版本落后 -> <b>增量更新</b>：只补结构，不动玩家放的方块，不清实体，不重放异想体</li>
 *   <li>版本一致 -> 什么都不做</li>
 * </ul>
 * 老存档第一次进来时读不到版本号，会被当成版本 1。所以如果你现在发的这一版结构就是"当前版本"，
 * 把 {@link #LOBOTO} 保持为 1；等下次真的改了结构再改成 2。
 */
public final class StructureVersions {

    /** 公司主体结构的当前版本 */
    public static final int LOBOTO = 1;

    /** 存档里没有记录时按这个版本算（也就是"这个功能上线之前生成的老存档"） */
    public static final int LEGACY = 1;

    private StructureVersions() {
    }
}
