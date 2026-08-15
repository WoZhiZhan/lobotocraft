package com.wzz.lobotocraft.client.renderer.entity.abnormality;

import com.wzz.lobotocraft.client.model.entity.CensoredModel;
import com.wzz.lobotocraft.client.renderer.entity.AbnormalityRenderer;
import com.wzz.lobotocraft.entity.abnormality.EntityCensored;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class EntityCensoredRenderer extends AbnormalityRenderer<EntityCensored> {
    public EntityCensoredRenderer(EntityRendererProvider.Context context) {
        super(context, new CensoredModel());
    }
}
