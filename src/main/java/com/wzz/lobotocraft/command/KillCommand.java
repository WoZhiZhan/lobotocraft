package com.wzz.lobotocraft.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayerFactory;

public class KillCommand implements ICommand {

    private static final float DAMAGE_AMOUNT = 9999.0F;

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                .executes(context -> execute(context.getSource()));
    }

    @Override
    public String getName() {
        return "kill";
    }

    private static int execute(CommandSourceStack source) {
        Entity attacker = getAttacker(source);
        DamageSource damage = DamageHelper.getDamage(attacker, "lobotocraft:blue");
        int count = 0;

        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (isKillTarget(entity)) {
                    ((LivingEntity) entity).hurt(damage, DAMAGE_AMOUNT);
                    count++;
                }
            }
        }

        int finalCount = count;
        source.sendSuccess(() ->
                        Component.literal("已对 " + finalCount + " 个出逃异想体/生物造成 9999 点蓝色伤害"),
                true);
        return count;
    }

    private static boolean isKillTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living) || living instanceof Player || !living.isAlive()) {
            return false;
        }
        if (living instanceof AbstractAbnormality abnormality) {
            return abnormality.hasEscape();
        }
        return true;
    }

    private static Entity getAttacker(CommandSourceStack source) {
        Entity attacker = source.getEntity();
        if (attacker != null) {
            return attacker;
        }
        return FakePlayerFactory.getMinecraft(source.getLevel());
    }
}
