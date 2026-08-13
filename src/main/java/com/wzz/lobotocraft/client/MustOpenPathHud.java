package com.wzz.lobotocraft.client;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.item.ego.must_open_path.MustOpenPathWeapon;
import com.wzz.lobotocraft.network.packet.MustOpenPathMeterSyncPacket;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class MustOpenPathHud {
    private MustOpenPathHud() {
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.renderDebug
                || !EgoArmorHelper.isFullEGOWithWeaponAnywhere(
                        player, MustOpenPathWeapon.SET_ID)) return;

        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        int meter = Math.max(0, Math.min(100,
                persisted.getInt(MustOpenPathMeterSyncPacket.CLIENT_METER_TAG)));
        String text = "必须维系的界限：" + meter + "%";
        int x = (event.getWindow().getGuiScaledWidth() - minecraft.font.width(text)) / 2;
        int y = event.getWindow().getGuiScaledHeight() - 61;
        event.getGuiGraphics().drawString(minecraft.font, text, x, y, 0x55DDF0, true);
    }
}
