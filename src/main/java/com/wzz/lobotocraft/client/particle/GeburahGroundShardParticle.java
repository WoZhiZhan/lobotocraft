package com.wzz.lobotocraft.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahGroundImpact;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Six atlas-textured faces, batched with vanilla terrain particles; no entity or physical collision. */
public final class GeburahGroundShardParticle extends TextureSheetParticle {
    private static final int[][] FACES = {
            {0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1},
            {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}
    };
    private static final float[] SHADING = {0.8F, 0.8F, 0.55F, 1.0F, 0.7F, 0.7F};
    private static final Direction[] DIRECTIONS = {
            Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH
    };
    private final GeburahGroundImpact.Shard shard;
    private final double startX;
    private final double startY;
    private final double startZ;
    private final double maxHeight;
    private final TextureAtlasSprite[] faceSprites = new TextureAtlasSprite[6];
    private final float[][] faceColors = new float[6][3];
    private final Vector3f[] corners = new Vector3f[8];
    private final Quaternionf rotation = new Quaternionf();

    public GeburahGroundShardParticle(ClientLevel level, double x, double y, double z,
                                     BlockState state, BlockPos floor,
                                     GeburahGroundImpact.Shard shard) {
        super(level, x, y, z);
        this.shard = shard;
        startX = x;
        startY = y;
        startZ = z;
        lifetime = shard.lifetime();
        hasPhysics = false;
        setSize(1.0F, 1.0F);
        var mc = Minecraft.getInstance();
        var model = mc.getBlockRenderer().getBlockModel(state);
        var fallback = mc.getBlockRenderer().getBlockModelShaper().getParticleIcon(state);
        // Cache per-face source textures/tints at spawn, not on every frame.
        var textureRandom = RandomSource.create(0);
        for (int face = 0; face < faceSprites.length; face++) {
            textureRandom.setSeed(0);
            var quads = model.getQuads(state, DIRECTIONS[face], textureRandom);
            var quad = quads.isEmpty() ? null : quads.get(0);
            faceSprites[face] = quad == null ? fallback : quad.getSprite();
            int tint = quad == null || !quad.isTinted() ? -1
                    : mc.getBlockColors().getColor(state, level, floor, quad.getTintIndex());
            faceColors[face][0] = tint == -1 ? 1 : ((tint >> 16) & 255) / 255.0F;
            faceColors[face][1] = tint == -1 ? 1 : ((tint >> 8) & 255) / 255.0F;
            faceColors[face][2] = tint == -1 ? 1 : (tint & 255) / 255.0F;
        }
        double clearance = 3.0D;
        for (int up = 1; up <= 4; up++) {
            BlockPos above = floor.above(up);
            if (!level.hasChunkAt(above)
                    || !level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
                clearance = Math.max(0.0D, up - 1.0D - shard.size() * 1.2D - 0.01D);
                break;
            }
        }
        maxHeight = clearance;
        for (int i = 0; i < corners.length; i++) corners[i] = new Vector3f();
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (++age >= lifetime) {
            remove();
            return;
        }
        double ticks = Math.max(0, age - shard.delay());
        setPos(startX, startY + shard.height(ticks, maxHeight), startZ);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        float ticks = age + partialTick - shard.delay();
        float opacity = shard.opacity(ticks, maxHeight);
        if (opacity <= 0.0F) return;
        float half = shard.size() * 0.5F;
        Vec3 view = camera.getPosition();
        float cx = (float) (Mth.lerp(partialTick, xo, x) - view.x);
        float cy = (float) (Mth.lerp(partialTick, yo, y) - view.y);
        float cz = (float) (Mth.lerp(partialTick, zo, z) - view.z);
        float tilt = shard.tiltAt(ticks, maxHeight);
        rotation.rotationXYZ(tilt, 0, tilt * 0.65F);
        float lowest = Float.POSITIVE_INFINITY;
        for (int i = 0; i < corners.length; i++) {
            corners[i].set((i & 4) == 0 ? -half : half,
                    (i & 2) == 0 ? -half : half,
                    (i & 1) == 0 ? -half : half).rotate(rotation);
            lowest = Math.min(lowest, corners[i].y);
        }
        // The visible cube's bottom touches the sampled floor after landing, not half-buried in it.
        for (Vector3f corner : corners) corner.add(cx, cy - lowest, cz);
        int light = getLightColor(partialTick);
        for (int face = 0; face < FACES.length; face++) {
            for (int vertex = 0; vertex < 4; vertex++) {
                Vector3f point = corners[FACES[face][vertex]];
                var texture = faceSprites[face];
                buffer.vertex(point.x, point.y, point.z)
                        .uv(vertex < 2 ? texture.getU0() : texture.getU1(),
                                vertex == 0 || vertex == 3 ? texture.getV0() : texture.getV1())
                        .color(faceColors[face][0] * SHADING[face], faceColors[face][1] * SHADING[face],
                                faceColors[face][2] * SHADING[face], opacity).uv2(light).endVertex();
            }
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }
}
