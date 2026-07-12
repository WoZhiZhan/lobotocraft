package com.wzz.lobotocraft.init;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.effect.*;
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

    // 蜂后孢子
    public static final RegistryObject<MobEffect> QUEEN_BEE_SPORE =
            EFFECTS.register("queen_bee_spore", QueenBeeSporeEffect::new);

    // 血之渴望
    public static final RegistryObject<MobEffect> RED_SHOES_BLOODLUST =
            EFFECTS.register("red_shoes_bloodlust", RedShoesBloodlustEffect::new);

    // 蕾蒂希雅的礼物
    public static final RegistryObject<MobEffect> LETICIA_GIFT =
            EFFECTS.register("leticia_gift", LeticiaGiftEffect::new);

    // 蕾蒂希雅的破碎礼物
    public static final RegistryObject<MobEffect> LETICIA_BROKEN_GIFT =
            EFFECTS.register("leticia_broken_gift", LeticiaBrokenGiftEffect::new);

    public static final RegistryObject<MobEffect> MENACE = EFFECTS.register("menace", MenaceEffect::new);
    public static final RegistryObject<MobEffect> SPORE = EFFECTS.register("spore", SporeEffect::new);
    public static final RegistryObject<MobEffect> LONELINESS = EFFECTS.register("loneliness", LonelinessEffect::new);
}
