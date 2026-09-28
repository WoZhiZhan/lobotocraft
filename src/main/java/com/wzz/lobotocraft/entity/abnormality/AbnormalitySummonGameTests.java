package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.item.base.TextSpawnEggItem;
import com.wzz.lobotocraft.util.AbnormalitySpawnHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.registries.ForgeRegistries;

/** Server-side checks for the entity insertion path shared by spawn eggs and /summon. */
@GameTestHolder(ModMain.MODID)
public final class AbnormalitySummonGameTests {
    private AbnormalitySummonGameTests() {
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "black_forest_spawn")
    public static void endBirdDebugSummonDoesNotBreakBlackForestActorProtection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos testCenter = helper.absolutePos(new BlockPos(10, 2, 2));
        level.getEntitiesOfClass(EntityBlackForestDoor.class,
                new AABB(testCenter).inflate(400.0D)).forEach(Entity::discard);

        EntityLargeBird largeBird = ModEntities.large_bird.get().create(level);
        if (largeBird == null) {
            helper.fail("could not create large bird");
            return;
        }
        moveTo(helper, largeBird, new BlockPos(2, 2, 2));
        if (!level.addFreshEntity(largeBird)) {
            helper.fail("large bird insertion was cancelled");
            return;
        }

        EntityEndBird endBird = ModEntities.end_bird.get().create(level);
        if (endBird == null) {
            helper.fail("could not create apocalypse bird");
            return;
        }
        moveTo(helper, endBird, new BlockPos(8, 2, 2));
        if (!level.addFreshEntity(endBird)) {
            helper.fail("apocalypse bird insertion was cancelled while a large bird existed");
            return;
        }

        EntityBlackForestDoor firstDoor = ModEntities.black_forest_door.get().create(level);
        EntityBlackForestDoor secondDoor = ModEntities.black_forest_door.get().create(level);
        if (firstDoor == null || secondDoor == null) {
            helper.fail("could not create black forest doors");
            return;
        }
        moveTo(helper, firstDoor, new BlockPos(14, 2, 2));
        moveTo(helper, secondDoor, new BlockPos(18, 2, 2));
        if (!level.addFreshEntity(firstDoor)) {
            largeBird.discard();
            endBird.discard();
            firstDoor.discard();
            secondDoor.discard();
            helper.fail("black forest door duplicate protection rejected the first door");
            return;
        }
        boolean firstVisible = level.getEntity(firstDoor.getUUID()) == firstDoor;
        int nearbyDoors = level.getEntitiesOfClass(EntityBlackForestDoor.class,
                secondDoor.getBoundingBox().inflate(400.0D)).size();
        if (level.addFreshEntity(secondDoor)) {
            largeBird.discard();
            endBird.discard();
            firstDoor.discard();
            secondDoor.discard();
            helper.fail("black forest door duplicate protection accepted the second door: first="
                    + firstDoor.blockPosition() + ", visible=" + firstVisible + ", nearby=" + nearbyDoors
                    + ", chunkLoaded=" + level.hasChunkAt(firstDoor.blockPosition()));
            return;
        }

        largeBird.discard();
        endBird.discard();
        firstDoor.discard();
        secondDoor.discard();
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 100, required = true, batch = "black_forest_admission")
    public static void pendingChunkDoorAdmissionRejectsDuplicateAndReleasesRemovedDoor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int offset = 0; offset < 16; offset++) {
            BlockPos pos = helper.absolutePos(new BlockPos(4096 + offset, 2, 2));
            EntityBlackForestDoor first = ModEntities.black_forest_door.get().create(level);
            EntityBlackForestDoor second = ModEntities.black_forest_door.get().create(level);
            EntityBlackForestDoor replacement = ModEntities.black_forest_door.get().create(level);
            if (first == null || second == null || replacement == null) {
                throw new IllegalStateException("door creation failed");
            }
            first.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            second.moveTo(pos.getX() + 4.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            replacement.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            try {
                helper.assertTrue(level.addFreshEntity(first), "first pending-chunk door rejected");
                helper.assertFalse(level.addFreshEntity(second), "pending-chunk duplicate accepted");
                first.discard();
                helper.assertTrue(level.addFreshEntity(replacement), "removed door kept its admission");
            } finally {
                first.discard();
                second.discard();
                replacement.discard();
            }
        }
        helper.succeed();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 100, required = true, batch = "abnormality_spawn_eggs")
    public static void everyAbnormalitySpawnEggUsesTheDebugSpawnPath(GameTestHelper helper) {
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 14; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        }

        ServerLevel level = helper.getLevel();
        BlockPos spawnPos = helper.absolutePos(new BlockPos(24, 1, 7));
        level.getEntitiesOfClass(EntityBlackForestDoor.class,
                new AABB(spawnPos).inflate(400.0D)).forEach(Entity::discard);
        int checked = 0;
        for (var item : ForgeRegistries.ITEMS.getValues()) {
            if (!(item instanceof TextSpawnEggItem egg)) continue;
            EntityType<?> type = egg.getType(null);
            Entity probe = type.create(level);
            if (!(probe instanceof AbstractAbnormality)) continue;
            probe.discard();
            AbstractAbnormality spawned = AbnormalitySpawnHelper.spawnPersistent(
                    level, (EntityType) type, spawnPos, MobSpawnType.SPAWN_EGG,
                    AbnormalitySpawnHelper.SpawnPlacementPolicy.DEBUG_BYPASS);
            if (spawned == null) {
                helper.fail("debug spawn path rejected " + ForgeRegistries.ENTITY_TYPES.getKey(type));
                return;
            }
            checked++;
            spawned.discard();
        }
        if (checked == 0) {
            helper.fail("no abnormality spawn eggs were checked");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "navigation_empty", templateNamespace = ModMain.MODID,
            timeoutTicks = 40, required = true, batch = "abnormality_spawn_eggs")
    public static void debugAnchorSpawnSkipsGlassAndUsesConnectedRing(GameTestHelper helper) {
        BlockPos relativeAnchor = new BlockPos(8, 0, 8);
        BlockPos anchor = helper.absolutePos(relativeAnchor);
        for (int x = 5; x <= 13; x++) {
            for (int z = 6; z <= 10; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        // A glass decoration beside this large entity blocks the direct
        // position; the deterministic ring must stay in the connected room.
        helper.setBlock(relativeAnchor, ModBlocks.ESCAPE.get());
        helper.setBlock(relativeAnchor.above().east(), Blocks.GLASS);
        helper.setBlock(relativeAnchor.above(2).east(), Blocks.GLASS);

        var spawned = AbnormalitySpawnHelper.spawnPersistentFromAnchor(
                helper.getLevel(), ModEntities.blue_star.get(), anchor,
                MobSpawnType.SPAWN_EGG);
        if (spawned == null) {
            helper.fail("debug anchor search could not find the connected clear pocket");
            return;
        }
        try {
            helper.assertFalse(spawned.blockPosition().equals(anchor),
                    "spawned in the blocked anchor column instead of the clear ring: "
                            + spawned.blockPosition());
            helper.assertTrue(helper.getLevel().noCollision(spawned),
                    "debug anchor spawn still intersects a collision shape");
            helper.assertFalse(helper.getLevel().getBlockState(spawned.blockPosition())
                            .getBlock() instanceof net.minecraft.world.level.block.AbstractGlassBlock,
                    "debug anchor spawn entered a glass block");
            helper.succeed();
        } finally {
            spawned.discard();
        }
    }

    private static void moveTo(GameTestHelper helper, Entity entity, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        entity.moveTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
    }
}
