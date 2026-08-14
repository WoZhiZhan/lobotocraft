package com.wzz.lobotocraft.item.ego.fragment_of_the_universe;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.EgoTooltipUtil;
import com.wzz.lobotocraft.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class FragmentUniverseWeapon extends BaseEgoWeapon {
    private static final UUID REACH_UUID = UUID.fromString("d88005cb-44d8-4fee-8e22-9067d95266a4");
    public FragmentUniverseWeapon() {
        super(
                new ModTier.WeaponTier(),
                MathUtil.toDamageModifier(5),
                MathUtil.toSpeedModifier(1.7f),
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> originalModifiers = super.getAttributeModifiers(slot, stack);
        if (slot == EquipmentSlot.MAINHAND) {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
            builder.putAll(originalModifiers);
            builder.put(ForgeMod.ENTITY_REACH.get(),
                    new AttributeModifier(REACH_UUID, weaponName() + "_reach",
                            1.0D, AttributeModifier.Operation.ADDITION));
            return builder.build();
        }
        return originalModifiers;
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String weaponName() {
        return "fragment_of_the_universe";
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        boolean leftClick = super.onLeftClickEntity(stack, player, entity);
        if (!leftClick) return false;
        if (!(entity instanceof LivingEntity target)) {
            return true;
        }
        if (!canUseItem(player))
            return false;
        triggerAttackAnimation(player, stack);
        stack.hurtAndBreak(1, player,
                p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND)
        );
        float damage = 5 + new Random().nextInt(6);
        if (player instanceof ServerPlayer serverPlayer) {
            MentalValueUtil.addMentalValue(serverPlayer, 5f);
        }
        SoundUtil.playSound(player.level, player, ModSounds.FRAGMENT_OF_THE_UNIVERSE_WEAPON.get());
        if (target instanceof ServerPlayer serverPlayer && MentalValueUtil.isPanic(serverPlayer)) {
            MentalValueUtil.addMentalValue(serverPlayer, 3f);
            return true;
        }
        target.hurt(DamageHelper.getDamage(player, "black"), damage);
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            player.getCooldowns().addCooldown(this, 200);
            player.level().playSound(
                    null,
                    player.blockPosition(),
                    ModSounds.FRAGMENT_SING.get(),
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    1.0f,
                    1.0f
            );
            for (LivingEntity living : EntityUtil.findAllEntities(player, 20)) {
                if (living instanceof Warden warden) {
                    TimerEntry<Warden> timerEntry = new TimerEntry<>() {
                        @Override
                        public void onRunning(@NotNull Warden warden) {
                            if (player.isAlive() && warden.getTarget() == player) {
                                warden.setTarget(null);
                            }
                        }
                    };
                    timerEntry.addSkillTimer(warden, 0, 10000, 20);
                }
            }
        }
        return super.use(world, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, p_41423_, p_41424_);
        if (!ClientInputUtil.isShiftPressed()) {
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.fragments_somewhere_weapon.summary",
                    ChatFormatting.GOLD);
            for (int index = 1; index <= 5; index++) {
                EgoTooltipUtil.blank(p_41423_);
                EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.fragments_somewhere_weapon.story." + index,
                        ChatFormatting.GRAY);
            }
            EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.common.shift_details",
                    ChatFormatting.GRAY);
        } else {
            for (int index = 1; index <= 2; index++) {
                EgoTooltipUtil.add(p_41423_, "tooltip.lobotocraft.fragments_somewhere_weapon.detail." + index,
                        ChatFormatting.GOLD);
            }
        }
    }
}
