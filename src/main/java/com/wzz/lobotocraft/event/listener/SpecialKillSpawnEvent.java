package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.world.data.LobotomySpawnSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 特殊击杀 -> 一次性生成异想体。世界级标记存于 {@link LobotomySpawnSavedData}。
 *
 * 条件1: 玩家带"力量" 击杀 带"虚弱"的卫道士 -> 在玩家附近生成 一无所有 (整档一次)。
 * 条件2: 玩家带"饥饿" 累计击杀 卫道士/村民 达 10 -> 生成 微笑的尸山 (整档一次)。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public class SpecialKillSpawnEvent {

    private static final int HUNGER_KILL_REQUIRED = 10;

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // 击杀者(含投射物的拥有者)必须是玩家
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        LivingEntity victim = event.getEntity();
        LobotomySpawnSavedData data = LobotomySpawnSavedData.get(level);

        // ---- 条件1: 力量玩家 击杀 带虚弱的卫道士 -> 一无所有 ----
        if (!data.nothingThereSpawned
                && player.hasEffect(MobEffects.DAMAGE_BOOST)      // 力量
                && victim instanceof Vindicator
                && victim.hasEffect(MobEffects.WEAKNESS)) {        // 虚弱
            spawnNear(level, player, ModEntities.nothing_there.get());
            EntityUtil.broadcastMessage(level, "§c§l这里一无所有");
            data.nothingThereSpawned = true;
            data.setDirty();
        }

        // ---- 条件2: 饥饿玩家 累计击杀 卫道士/村民 达 10 -> 微笑的尸山 ----
        if (!data.smilingSpawned
                && player.hasEffect(MobEffects.HUNGER)             // 饥饿
                && (victim instanceof Vindicator || victim instanceof Villager)) {
            data.hungerKillCount++;
            data.setDirty();
            if (data.hungerKillCount >= HUNGER_KILL_REQUIRED) {
                spawnNear(level, player, ModEntities.smiling_corpse_mountain.get());
                EntityUtil.broadcastMessage(level, "§c§l那些尸体上弥漫着死亡的哀伤");
                data.smilingSpawned = true;
                data.setDirty();
            }
        }
    }

    /** 在玩家周围 2~5 格找一处安全地面生成该实体, 找不到就退而求其次。 */
    private static void spawnNear(ServerLevel level, ServerPlayer player, EntityType<?> type) {
        RandomSource rand = level.random;
        BlockPos base = player.blockPosition();
        BlockPos chosen = null;
        for (int i = 0; i < 12; i++) {
            int dx = Mth.nextInt(rand, 2, 5) * (rand.nextBoolean() ? 1 : -1);
            int dz = Mth.nextInt(rand, 2, 5) * (rand.nextBoolean() ? 1 : -1);
            BlockPos safe = EntityUtil.findSafeGroundPosition(level, base.offset(dx, 0, dz), 3);
            if (safe != null) {
                chosen = safe;
                break;
            }
        }
        if (chosen == null) chosen = base.offset(2, 0, 2);
        placeEntity(level, type, chosen);
    }

    private static void placeEntity(ServerLevel level, EntityType<?> type, BlockPos pos) {
        Entity entity = type.create(level);
        if (entity == null) return;
        entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.random.nextFloat() * 360F, 0F);
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        }
        level.addFreshEntity(entity);
    }
}