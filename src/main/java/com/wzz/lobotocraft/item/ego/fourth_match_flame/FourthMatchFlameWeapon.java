package com.wzz.lobotocraft.item.ego.fourth_match_flame;

import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.AiSpecialEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.FallBackCameraPacket;
import com.wzz.lobotocraft.network.packet.TriggerShakePacket;
import com.wzz.lobotocraft.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FourthMatchFlameWeapon extends BaseEgoWeapon implements AiSpecialEgoWeapon {
    @Override public double aiSpecialAttackRange() { return 15.0D; }
    public FourthMatchFlameWeapon() {
        super(
                new ModTier.WeaponTier(),
                20,
                -3.4f,
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String weaponName() {
        return "fourth_match_flame";
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        boolean leftClick = super.onLeftClickEntity(stack, player, entity);
        if (!leftClick) return false;
        if (entity instanceof LivingEntity target) {
            target.hurt(DamageHelper.getDamage(player, "red"), 1f);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.cooldowns.addCooldown(stack.getItem(), 100);
        player.playSound(ModSounds.FOURTH_MATCH_FLAME_WEAPON.get());
        if (!level.isClientSide) {
            MessageLoader.getLoader().sendToPlayer((ServerPlayer) player, new TriggerShakePacket(20));
            MessageLoader.getLoader().sendToPlayer((ServerPlayer) player, new FallBackCameraPacket());
            ParticleUtil.spawnLineParticles(level, player, ParticleTypes.FLAME, 40, 0.1d, 10);
            List<LivingEntity> list = EntityUtil.findAllEntitiesInLookDirection(player, 15, LivingEntity.class);
            for (LivingEntity target : list) {
                target.hurt(DamageHelper.getDamage(player, "red"), 20 + player.random.nextInt(10) + 1);
            }
        }
        return super.use(level, player, hand);
    }

    @Override
    protected boolean hasIdle() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag flag) {
        super.appendHoverText(stack, level, components, flag);
        for (int index = 1; index <= 4; index++) {
            EgoTooltipUtil.add(components, "tooltip.lobotocraft.fourth_match_flame_weapon.story." + index,
                    ChatFormatting.GRAY);
        }
    }
}
