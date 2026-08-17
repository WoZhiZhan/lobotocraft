package com.wzz.lobotocraft.client.renderer.entity.abnormality;

import com.wzz.lobotocraft.client.model.entity.BaseModel;
import com.wzz.lobotocraft.client.renderer.entity.AbnormalityRenderer;
import com.wzz.lobotocraft.entity.abnormality.EntityQueenOfHatred;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.GeoModel;

/** Selects the human or snake asset without replacing the server entity. */
public final class EntityQueenOfHatredRenderer extends AbnormalityRenderer<EntityQueenOfHatred> {
    private final GeoModel<EntityQueenOfHatred> humanModel =
            new BaseModel<>("the_queen_of_hatred_human", "the_queen_of_hatred_human");
    private final GeoModel<EntityQueenOfHatred> snakeModel =
            new BaseModel<>("the_queen_of_hatred_snake", "the_queen_of_hatred_snake");

    public EntityQueenOfHatredRenderer(EntityRendererProvider.Context context) {
        super(context, 1.0F, 1.0F);
        this.shadowRadius = 0.8F;
    }

    @Override
    public GeoModel<EntityQueenOfHatred> getGeoModel() {
        EntityQueenOfHatred entity = getAnimatable();
        return entity != null && entity.isSnakeForm() ? snakeModel : humanModel;
    }
}
