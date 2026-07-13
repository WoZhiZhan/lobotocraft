package com.wzz.lobotocraft.item.ego.butterfly_funeral;

import net.minecraft.world.item.ItemStack;

/**
 * GeckoLib 的 GeoItem 是单例 animatable，动画控制器的 predicate 里拿不到 ItemStack。
 * 渲染是单线程的，所以用一个静态字段在 renderByItem 期间记录"当前正在渲染的那一把枪"，
 * 让 predicate 和 getTextureLocation 都能读到它。
 * <p>
 * 这个类故意放在通用包、不引用任何客户端类，避免服务端加载时炸掉。
 */
public final class ButterflyRenderContext {

    /** 仅在客户端渲染线程内有效 */
    public static ItemStack CURRENT = ItemStack.EMPTY;

    private ButterflyRenderContext() {
    }
}