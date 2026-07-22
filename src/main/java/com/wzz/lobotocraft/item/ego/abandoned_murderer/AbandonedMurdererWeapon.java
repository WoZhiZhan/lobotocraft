package com.wzz.lobotocraft.item.ego.abandoned_murderer;

import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.init.ModTier;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AbandonedMurdererWeapon extends BaseEgoWeapon {
    public AbandonedMurdererWeapon() {
        super(
                new ModTier.WeaponTier(),
                2,
                -3.2f,
                new Properties().stacksTo(1).fireResistant()
        );
    }

    @Override
    public boolean hasAnimatable() {
        return false;
    }

    @Override
    public String weaponName() {
        return "abandoned_murderer";
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        boolean leftClick = super.onLeftClickEntity(stack, player, entity);
        if (!leftClick) return false;
        if (entity instanceof LivingEntity living) {
            living.hurt(DamageHelper.getDamage(player, "red"), 15f);
            player.playSound(ModSounds.ABANDONED_MURDERER_ATTACK.get());
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag flag) {
        super.appendHoverText(stack, level, components, flag);
        components.add(Component.literal("§7秘密研究是从一个地下室开始的，而那项研究有着改变人类未来的无限可能。"));
        components.add(Component.literal("§7为了一个更远大的目标，他们放下了道德和尊严。"));
        components.add(Component.literal("§7虽不人道，可没人会感到后悔...除了他们。"));
        components.add(Component.literal("§7看上去连仁慈的Carmen都默许了这一切。"));
        components.add(Component.literal("§7他们再也不能回到正常的生活当中。"));
        components.add(Component.literal("§7对一个连葬礼都没有的人而言，唯有深深的悔恨才是最后的悼唁。"));
        components.add(Component.literal("§c红色伤害：15"));
    }
}