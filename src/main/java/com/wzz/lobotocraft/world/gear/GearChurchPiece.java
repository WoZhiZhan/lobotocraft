package com.wzz.lobotocraft.world.gear;

import com.wzz.lobotocraft.init.ModWorldGen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

public final class GearChurchPiece extends TemplateStructurePiece {
    private final BlockPos anchor;
    private final Rotation rotation;
    private final BlockPos offset;
    public GearChurchPiece(StructureTemplateManager manager, GearChurchLayout.Part part, GearChurchSite.Site site) {
        super(ModWorldGen.GEAR_CHURCH_PIECE.get(), 0, manager, part.template(), part.template().toString(),
                settings(site.rotation()), GearChurchLayout.world(part.offset(), site.anchor(), site.rotation()));
        anchor = site.anchor(); rotation = site.rotation(); offset = part.offset();
    }
    public GearChurchPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModWorldGen.GEAR_CHURCH_PIECE.get(), tag, context.structureTemplateManager(),
                id -> settings(Rotation.valueOf(tag.getString("ChurchRotation"))));
        anchor = BlockPos.of(tag.getLong("ChurchAnchor"));
        rotation = Rotation.valueOf(tag.getString("ChurchRotation"));
        offset = BlockPos.of(tag.getLong("ChurchOffset"));
    }
    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings().setRotation(rotation).setRotationPivot(BlockPos.ZERO)
                .setIgnoreEntities(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }
    public BlockPos anchor() { return anchor; }
    public Rotation rotation() { return rotation; }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putLong("ChurchAnchor", anchor.asLong()); tag.putString("ChurchRotation", rotation.name());
        tag.putLong("ChurchOffset", offset.asLong());
    }
    @Override protected void handleDataMarker(String metadata, BlockPos pos, ServerLevelAccessor level,
                                              RandomSource random, BoundingBox box) {}
    @Override public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                      RandomSource random, BoundingBox writable, ChunkPos chunk, BlockPos pivot) {
        // Clear only the authored foundation, only this writable chunk, and only six blocks
        // above its floor. Do this BEFORE template placement so furniture/soil are preserved.
        for (int x = 0; x < template.getSize().getX(); x++) for (int z = 0; z < template.getSize().getZ(); z++) {
            int lx = offset.getX() + x, lz = offset.getZ() + z;
            if (!GearChurchLayout.foundation(lx, lz)) continue;
            var base = GearChurchLayout.world(new BlockPos(lx, 0, lz), anchor, rotation);
            for (int up = 1; up <= GearChurchSite.MAX_CUT; up++) {
                var pos = base.above(up);
                if (writable.isInside(pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        super.postProcess(level, manager, generator, random, writable, chunk, pivot);
        GearChurchResidents.registerPlaced(level.getLevel(), anchor, rotation);
        // Column-local bounded support, never accesses/loads a neighboring chunk during placement.
        for (int x = 0; x < template.getSize().getX(); x++) for (int z = 0; z < template.getSize().getZ(); z++) {
            int lx = offset.getX() + x, lz = offset.getZ() + z;
            if (!GearChurchLayout.foundation(lx, lz)) continue;
            BlockPos base = GearChurchLayout.world(new BlockPos(lx, 0, lz), anchor, rotation);
            if (!writable.isInside(base)) continue;
            int groundDepth = -1;
            for (int down = 1; down <= GearChurchSite.MAX_SUPPORT + 1; down++) {
                BlockPos pos = base.below(down);
                if (pos.getY() < level.getMinBuildHeight()) break;
                var state = level.getBlockState(pos);
                if (state.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP)) { groundDepth = down; break; }
            }
            // Never leave a decorative floating twelve-block pillar if a carver changed the sampled terrain.
            if (groundDepth < 0) continue;
            for (int down = 1; down < groundDepth; down++)
                level.setBlock(base.below(down), Blocks.TERRACOTTA.defaultBlockState(), 2);
        }
    }
}
