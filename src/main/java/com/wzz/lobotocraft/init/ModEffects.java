package com.wzz.lobotocraft.init;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.effect.FriendshipProofEffect;
import com.wzz.lobotocraft.effect.KissEffect;
import com.wzz.lobotocraft.effect.WishWithoutLightEffect;
import com.wzz.lobotocraft.effect.ButterflyShroudEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 自定义药水效果(Buff)注册。
 * FRIENDSHIP_PROOF "友谊之证" —— 由原"鹅卵石"物品改造而来,持续回血与回精神值。
 * KISS "亲吻" —— 由原"冰雪女皇的冰片"物品改造而来,作为冰雪女皇决斗流程的标记。
 */
public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ModMain.MODID);

    // 友谊之证(原鹅卵石)
    public static final RegistryObject<MobEffect> FRIENDSHIP_PROOF =
            EFFECTS.register("friendship_proof", FriendshipProofEffect::new);

    // 亲吻(原冰片)
    public static final RegistryObject<MobEffect> KISS =
            EFFECTS.register("kiss", KissEffect::new);

    // 同葬无光之愿(浊心斯卡蒂的祝福)
    public static final RegistryObject<MobEffect> WISH_WITHOUT_LIGHT =
            EFFECTS.register("wish_without_light", WishWithoutLightEffect::new);

    // 蝴蝶缠身(亡蝶葬仪处决)
    public static final RegistryObject<MobEffect> BUTTERFLY_SHROUD =
            EFFECTS.register("butterfly_shroud", ButterflyShroudEffect::new);
}
