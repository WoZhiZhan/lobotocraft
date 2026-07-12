package com.wzz.lobotocraft.client.model.item;

import com.mojang.datafixers.util.Either;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class FlatCurioModel implements BakedModel {

    private static final ItemModelGenerator GENERATOR = new ItemModelGenerator();
    private static final Function<Material, TextureAtlasSprite> SPRITES = Material::sprite;

    /** 原版 minecraft:item/generated 的 display（translation 已 /16） */
    private static final ItemTransforms GENERATED = buildGenerated();

    private static final ModelBaker DUMMY_BAKER = new ModelBaker() {
        @Override public UnbakedModel getModel(ResourceLocation l) { throw new UnsupportedOperationException(); }
        @Override public @Nullable BakedModel bake(ResourceLocation l, ModelState s) { throw new UnsupportedOperationException(); }
        @Override public @Nullable BakedModel bake(ResourceLocation resourceLocation, ModelState modelState, Function<Material, TextureAtlasSprite> function) { throw new UnsupportedOperationException(); }
        @Override public Function<Material, TextureAtlasSprite> getModelTextureGetter() { return SPRITES; }
    };

    private final ItemTransforms wearTransforms;   // 来自你 json 里的 display
    private final ResourceLocation texture;        // lobotocraft:item/xxx_curio （无 textures/ 前缀、无 .png）
    private BakedModel flat;

    public FlatCurioModel(BakedModel original, ResourceLocation texture) {
        this.wearTransforms = original.getTransforms();
        this.texture = texture;
    }

    /** 穿戴时要用的 json display */
    public ItemTransforms getWearTransforms() {
        return this.wearTransforms;
    }

    /** 首次渲染时才烘焙——此时图集一定就绪 */
    private BakedModel flat() {
        if (this.flat == null) {
            Material mat = new Material(InventoryMenu.BLOCK_ATLAS, this.texture);
            BlockModel base = new BlockModel(null, List.of(),
                    Map.of("layer0", Either.left(mat), "particle", Either.left(mat)),
                    null, BlockModel.GuiLight.FRONT, GENERATED, List.of());
            base.name = this.texture.toString();

            BlockModel generated = GENERATOR.generateBlockModel(SPRITES, base);
            generated.name = base.name;

            this.flat = generated.bake(DUMMY_BAKER, generated, SPRITES,
                    BlockModelRotation.X0_Y0, this.texture, false);
        }
        return this.flat;
    }

    @Override public List<BakedQuad> getQuads(@Nullable BlockState s, @Nullable Direction d, RandomSource r) {
        return flat().getQuads(s, d, r);
    }
    @Override public TextureAtlasSprite getParticleIcon() { return flat().getParticleIcon(); }
    @Override public ItemTransforms getTransforms()       { return GENERATED; }
    @Override public boolean isCustomRenderer()           { return false; } // 关键：不再走 BEWLR
    @Override public boolean usesBlockLight()             { return false; } // 关键：等价 gui_light: front
    @Override public boolean isGui3d()                    { return false; }
    @Override public boolean useAmbientOcclusion()        { return false; }
    @Override public ItemOverrides getOverrides()         { return ItemOverrides.EMPTY; }

    private static ItemTransforms buildGenerated() {
        ItemTransform third = new ItemTransform(new Vector3f(0, 0, 0),
                new Vector3f(0, 3 / 16F, 1 / 16F), new Vector3f(0.55F, 0.55F, 0.55F));
        ItemTransform firstRight = new ItemTransform(new Vector3f(0, -90, 25),
                new Vector3f(1.13F / 16F, 3.2F / 16F, 1.13F / 16F), new Vector3f(0.68F, 0.68F, 0.68F));
        ItemTransform firstLeft = new ItemTransform(new Vector3f(0, 90, -25),
                new Vector3f(1.13F / 16F, 3.2F / 16F, 1.13F / 16F), new Vector3f(0.68F, 0.68F, 0.68F));
        ItemTransform head = new ItemTransform(new Vector3f(0, 180, 0),
                new Vector3f(0, 13 / 16F, 7 / 16F), new Vector3f(1, 1, 1));
        ItemTransform ground = new ItemTransform(new Vector3f(0, 0, 0),
                new Vector3f(0, 2 / 16F, 0), new Vector3f(0.5F, 0.5F, 0.5F));
        // 顺序：3PL, 3PR, 1PL, 1PR, head, gui, ground, fixed
        return new ItemTransforms(third, third, firstLeft, firstRight, head,
                ItemTransform.NO_TRANSFORM, ground, ItemTransform.NO_TRANSFORM);
    }
}