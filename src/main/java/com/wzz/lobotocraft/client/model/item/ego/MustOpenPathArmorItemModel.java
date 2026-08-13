package com.wzz.lobotocraft.client.model.item.ego;

import com.wzz.lobotocraft.item.ego.must_open_path.MustOpenPathArmor;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class MustOpenPathArmorItemModel extends GeoModel<MustOpenPathArmor> {
    private static String part(MustOpenPathArmor armor) {
        return switch (armor.getType()) {
            case CHESTPLATE -> "chestplate";
            case LEGGINGS -> "leggings";
            case BOOTS -> "boots";
            default -> "chestplate";
        };
    }

    @Override
    public ResourceLocation getModelResource(MustOpenPathArmor armor) {
        return ResourceUtil.createInstance("geo/armor/must_open_path_" + part(armor) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(MustOpenPathArmor armor) {
        return ResourceUtil.createInstance("textures/armor/must_open_path_" + part(armor) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(MustOpenPathArmor armor) {
        return null;
    }
}
