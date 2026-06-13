package com.wzz.lobotocraft.client.renderer.item;

import com.wzz.lobotocraft.item.block.BaseGeoBlockItem;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class BaseGeoItemRenderer extends GeoItemRenderer<BaseGeoBlockItem> {

    public BaseGeoItemRenderer(GeoModel<BaseGeoBlockItem> geoModel) {
        super(geoModel);
    }
}