package com.wzz.lobotocraft.client.renderer.item;

import com.wzz.lobotocraft.client.model.item.ego.MustOpenPathArmorItemModel;
import com.wzz.lobotocraft.item.ego.must_open_path.MustOpenPathArmor;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public final class MustOpenPathArmorItemRenderer extends GeoItemRenderer<MustOpenPathArmor> {
    public MustOpenPathArmorItemRenderer() {
        super(new MustOpenPathArmorItemModel());
    }
}
