package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.ego.EgoLoadoutService;
import com.wzz.lobotocraft.ego.EgoProjectionRules;
import com.wzz.lobotocraft.ego.SpecialEgoCatalog;
import com.wzz.lobotocraft.ego.SpecialEgoService;
import com.wzz.lobotocraft.item.ego.base.IEgoArmor;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class EgoEquipmentEventHandler {
    private EgoEquipmentEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof ServerPlayer player)) return;
        if (event.phase == TickEvent.Phase.START) {
            SpecialEgoService.purgeActivePhysicalCopies(player);
        } else if (player.tickCount % 20 == 0) {
            SpecialEgoService.reconcile(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SpecialEgoService.reconcile(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SpecialEgoService.reconcile(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SpecialEgoService.releaseOnLogout(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickArmor(PlayerInteractEvent.RightClickItem event) {
        ItemStack held = event.getItemStack();
        Equipable equipable = Equipable.get(held);
        if (equipable == null) return;
        EquipmentSlot slot = equipable.getEquipmentSlot();
        boolean occupiedByProjection = EgoLoadoutService.isProjection(
                event.getEntity().getItemBySlot(slot));
        boolean wearingEgoArmor = hasEquippedEgoArmor(event.getEntity());
        if (EgoProjectionRules.mayAutoEquipInto(
                occupiedByProjection, wearingEgoArmor, slot)) return;

        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
    }

    private static boolean hasEquippedEgoArmor(net.minecraft.world.entity.player.Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack equipped = player.getItemBySlot(slot);
            if (equipped.getItem() instanceof IEgoArmor
                    || EgoLoadoutService.isArmorProjection(equipped)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (EgoLoadoutService.isProjection(event.getEntity().getItem())
                || SpecialEgoCatalog.isSpecialItem(event.getEntity().getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity item)) return;
        if (!EgoProjectionRules.mayExistAsItemEntity(
                EgoLoadoutService.isProjection(item.getItem()))
                || SpecialEgoCatalog.isSpecialItem(item.getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        event.getDrops().removeIf(item -> EgoLoadoutService.isProjection(item.getItem())
                || SpecialEgoCatalog.isSpecialItem(item.getItem()));
    }
}
