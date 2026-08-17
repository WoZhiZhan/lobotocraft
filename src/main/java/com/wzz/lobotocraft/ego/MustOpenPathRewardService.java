package com.wzz.lobotocraft.ego;

import com.wzz.lobotocraft.init.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Grants the player-only curio trophy and the ordinary E.G.O suppression stock. */
public final class MustOpenPathRewardService {
    public static final String SKADI_CODE = "S-03-07";

    private MustOpenPathRewardService() {
    }

    public static GrantResult grantNormalSuppression(MinecraftServer server,
                                                     @Nullable ServerPlayer finalHitPlayer) {
        if (server == null) return new GrantResult(false, false, false);

        AiEgoRepositoryData repository = AiEgoRepositoryData.get(server);
        int weaponBefore = repository.total(SKADI_CODE, "weapon");
        int armorBefore = repository.total(SKADI_CODE, "armor");
        EgoAuthorizationService.grantSuppressionReward(server, finalHitPlayer, SKADI_CODE);

        boolean playerGranted = finalHitPlayer != null;
        if (finalHitPlayer != null) {
            give(finalHitPlayer, new ItemStack(ModItems.MUST_OPEN_PATH_CURIO.get()));
            finalHitPlayer.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.must_open_path_reward"));
        }

        boolean aiWeaponAdded = repository.total(SKADI_CODE, "weapon") > weaponBefore;
        boolean aiArmorAdded = repository.total(SKADI_CODE, "armor") > armorBefore;
        return new GrantResult(playerGranted, aiWeaponAdded, aiArmorAdded);
    }

    public static List<ItemStack> weaponReward() {
        return List.of(new ItemStack(ModItems.MUST_OPEN_PATH_WEAPON.get()));
    }

    /** Research and AI stock contain armor only; the curio remains player-suppression-only. */
    public static List<ItemStack> armorReward() {
        return List.of(
                new ItemStack(ModItems.MUST_OPEN_PATH_CHESTPLATE.get()),
                new ItemStack(ModItems.MUST_OPEN_PATH_LEGGINGS.get()),
                new ItemStack(ModItems.MUST_OPEN_PATH_BOOTS.get()));
    }

    private static void give(ServerPlayer player, ItemStack reward) {
        ItemStack stack = reward.copy();
        if (!player.addItem(stack) && !stack.isEmpty()) player.drop(stack, false);
    }

    public record GrantResult(boolean playerGranted, boolean aiWeaponAdded,
                              boolean aiArmorAdded) {
    }
}
