package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.entity.abnormality.EntityChildrenGalaxy;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 友谊之证 / 亲吻 两个 Buff 的辅助工具,
 * 用于替代原"鹅卵石"和"冰片"物品的持有/添加/移除/计数操作。
 */
public class BuffUtil {

    // 无限持续时间(与原物品长期存在的语义一致)
    private static final int INFINITE_DURATION = -1;
    private static final String FRIEND_SOURCE = "lobotocraft_friendship_source";
    private static final String FRIEND_DIMENSION = "lobotocraft_friendship_dimension";

    // ===================== 友谊之证(原鹅卵石) =====================

    /**
     * 是否拥有"友谊之证"。
     */
    public static boolean hasFriendshipProof(Player player) {
        return player.hasEffect(ModMobEffects.FRIENDSHIP_PROOF.get());
    }

    /**
     * 读取绑定来源的当前计数器，不读取效果等级快照。
     * 未持有返回 -1；来源暂不可用／旧效果归属不明时返回 0，不猜来源或继续旧强度回血。
     */
    public static int getFriendshipProofCounter(Player player) {
        MobEffectInstance instance = player.getEffect(ModMobEffects.FRIENDSHIP_PROOF.get());
        if (instance == null) return -1;
        EntityChildrenGalaxy source = friendshipSource(player);
        // An explicitly bound but unloaded source must not become another galaxy
        // or keep healing from a stale display snapshot.
        return source == null ? 0 : source.getQliphothCounter();
    }

    /** Persistent ownership: the effect level is a projection, not the source of truth. */
    public static EntityChildrenGalaxy friendshipSource(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !hasFriendshipProof(player)) return null;
        var data = player.getPersistentData();
        if (data.hasUUID(FRIEND_SOURCE)) {
            var dimension = ResourceLocation.tryParse(data.getString(FRIEND_DIMENSION));
            if (dimension == null) return null;
            var level = serverPlayer.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null) return null;
            var entity = level.getEntity(data.getUUID(FRIEND_SOURCE));
            return entity instanceof EntityChildrenGalaxy galaxy && galaxy.isAlive() && !galaxy.isRemoved()
                    ? galaxy : null;
        }
        // Legacy effects have no ownership. Bind only if the local reference is
        // unambiguous; do not pick the first/nearest instance when two are present.
        var candidates = player.level().getEntitiesOfClass(EntityChildrenGalaxy.class,
                player.getBoundingBox().inflate(400), galaxy -> galaxy.isAlive() && !galaxy.isRemoved());
        if (candidates.size() != 1) return null;
        bindFriendshipSource(player, candidates.get(0));
        return candidates.get(0);
    }

    private static void bindFriendshipSource(Player player, EntityChildrenGalaxy source) {
        player.getPersistentData().putUUID(FRIEND_SOURCE, source.getUUID());
        player.getPersistentData().putString(FRIEND_DIMENSION, source.level().dimension().location().toString());
    }

    public static void giveFriendshipProof(Player player, EntityChildrenGalaxy source) {
        bindFriendshipSource(player, source);
        giveFriendshipProof(player, source.getQliphothCounter());
    }

    public static void bindLegacyProofOnWork(Player player, EntityChildrenGalaxy source) {
        if (hasFriendshipProof(player) && !player.getPersistentData().hasUUID(FRIEND_SOURCE))
            bindFriendshipSource(player, source);
    }

    public static void syncFriendshipProof(Player player, EntityChildrenGalaxy source) {
        if (friendshipSource(player) != source) return;
        var current = player.getEffect(ModMobEffects.FRIENDSHIP_PROOF.get());
        int amplifier = Math.max(0, source.getQliphothCounter() - 1);
        if (current != null && current.getAmplifier() != amplifier) {
            // MobEffectInstance.update never downgrades a stronger infinite effect.
            // Replace it deliberately so it cannot hide a stale stronger instance.
            player.removeEffect(ModMobEffects.FRIENDSHIP_PROOF.get());
            giveFriendshipProof(player, source.getQliphothCounter());
        }
    }

    /**
     * 添加/刷新"友谊之证",counter 决定其强度(counter>=1)。
     */
    public static void giveFriendshipProof(Player player, int counter) {
        int amplifier = Math.max(0, counter - 1);
        player.addEffect(new MobEffectInstance(
                ModMobEffects.FRIENDSHIP_PROOF.get(),
                INFINITE_DURATION,
                amplifier,
                false,
                false,
                true
        ));
    }

    /**
     * 移除"友谊之证"。
     */
    public static void removeFriendshipProof(Player player) {
        player.removeEffect(ModMobEffects.FRIENDSHIP_PROOF.get());
        player.getPersistentData().remove(FRIEND_SOURCE);
        player.getPersistentData().remove(FRIEND_DIMENSION);
    }

    // ===================== 亲吻(原冰片) =====================

    /**
     * 是否拥有"亲吻"。
     */
    public static boolean hasKiss(Player player) {
        return player.hasEffect(ModMobEffects.KISS.get());
    }

    /**
     * 添加"亲吻"标记。
     */
    public static void giveKiss(Player player) {
        player.addEffect(new MobEffectInstance(
                ModMobEffects.KISS.get(),
                INFINITE_DURATION,
                0,
                false,
                false,
                true
        ));
    }

    /**
     * 移除"亲吻"标记。
     */
    public static void removeKiss(Player player) {
        player.removeEffect(ModMobEffects.KISS.get());
    }
}
