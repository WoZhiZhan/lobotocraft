package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.entity.abnormality.CleanerTimePolicy;
import com.wzz.lobotocraft.entity.abnormality.EntityCleaner;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.CuriosUtil;
import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 野怪清道夫的生成与掉落控制:
 *  - 仅夜间在主世界玩家附近刷新;玩家可视范围(48格)内最多12只。
 *  - 被清道夫击杀的生物不产生任何掉落物。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class CleanerEvent {

    private static final int SPAWN_INTERVAL = 100;     // 每5秒尝试一次
    private static final int MAX_VISIBLE = 12;          // 可视范围内上限
    private static final double VISIBLE_RANGE = 48.0;
    private static final float SPAWN_CHANCE = 0.30f;
    private static final float CURIO_DROP_CHANCE = 0.23f;
    private static final float CURIO_RECOVERY_CHANCE = 0.15f;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player_tick(event);
    }

    private static void Player_tick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (level.isClientSide) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        if (player.tickCount % SPAWN_INTERVAL != 0) return;
        // isNight() also returns true during daytime thunderstorms.
        if (!isNaturalSpawnTime(serverLevel.getDayTime())) return;

        // 可视范围内数量上限
        int nearby = serverLevel.getEntitiesOfClass(EntityCleaner.class,
                player.getBoundingBox().inflate(VISIBLE_RANGE)).size();
        if (nearby >= MAX_VISIBLE) return;

        if (serverLevel.getRandom().nextFloat() >= SPAWN_CHANCE) return;

        // 在玩家附近 16~32 格的随机地面生成
        BlockPos spawnPos = findSpawnPos(serverLevel, player.blockPosition());
        if (spawnPos == null) return;

        EntityCleaner cleaner = ModEntities.cleaner.get().create(serverLevel);
        if (cleaner == null) return;
        cleaner.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                serverLevel.getRandom().nextFloat() * 360f, 0f);
        cleaner.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos),
                MobSpawnType.NATURAL, null, null);
        serverLevel.addFreshEntity(cleaner);
    }

    static boolean isNaturalSpawnTime(long dayTime) {
        return CleanerTimePolicy.isNaturalSpawnTime(dayTime);
    }

    private static BlockPos findSpawnPos(ServerLevel level, BlockPos center) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = level.getRandom().nextInt(33) - 16;
            int dz = level.getRandom().nextInt(33) - 16;
            // 距离至少16格,避免贴脸生成
            if (Math.abs(dx) < 16 && Math.abs(dz) < 16) continue;
            BlockPos col = center.offset(dx, 0, dz);
            BlockPos ground = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col);
            if (level.isEmptyBlock(ground) && level.isEmptyBlock(ground.above())) {
                return ground;
            }
        }
        return null;
    }

    /** 被清道夫击杀的生物不产生掉落物 */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof EntityCleaner cleaner
                && cleaner.getRandom().nextFloat() < CURIO_DROP_CHANCE) {
            event.getDrops().add(new ItemEntity(cleaner.level(), cleaner.getX(), cleaner.getY(), cleaner.getZ(),
                    new ItemStack(ModItems.CLEANER_CURIO.get())));
        }
        if (event.getEntity().getPersistentData().getBoolean("cleaner_no_loot")) {
            event.setCanceled(true);
        }
    }

    /** 满蓄力 E.G.O 命中并实际造成伤害时，有概率恢复生命和精神。 */
    /** Called after final HP protection by DamageSettlement. */
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!(player.getMainHandItem().getItem() instanceof BaseEgoWeapon)) return;
        if (!BaseEgoWeapon.hasCurrentLeftClickAttack(player)
                || BaseEgoWeapon.currentLeftClickDamageMultiplier(player) < 0.999F) return;
        if (!CuriosUtil.hasCurios(player, ModItems.CLEANER_CURIO.get())) return;
        if (player.getRandom().nextFloat() >= CURIO_RECOVERY_CHANCE) return;

        player.heal(10.0F);
        MentalValueUtil.addMentalValue(player, 10.0F);
    }
}
