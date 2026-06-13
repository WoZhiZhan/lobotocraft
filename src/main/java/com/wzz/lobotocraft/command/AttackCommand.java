package com.wzz.lobotocraft.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.wzz.lobotocraft.util.DamageHelper.getDamage;

public class AttackCommand implements ICommand{

    private static final Map<String, String> DAMAGE_MAP = Map.of(
            "blue",  "lobotocraft:blue",
            "red",   "lobotocraft:red",
            "black", "lobotocraft:black",
            "white", "lobotocraft:white"
    );

    private static final List<String> DAMAGE_TYPES = List.of(
            "blue",
            "red",
            "black",
            "white"
    );

    private static int executeBatchAttack(CommandSourceStack source,
                                          Collection<? extends Entity> targets,
                                          String type, float amount) {

        String damageKey = DAMAGE_MAP.get(type.toLowerCase());
        if (damageKey == null) {
            source.sendFailure(Component.literal("未知伤害类型: " + type));
            return 0;
        }
        Entity attacker = getAttacker(source);
        DamageSource ds = getDamage(attacker, damageKey);
        int count = 0;
        for (Entity e : targets) {
            if (e instanceof LivingEntity living) {
                living.hurt(ds, amount);
                count++;
            }
        }
        int finalCount = count;
        source.sendSuccess(() ->
                        Component.literal("已对 " + finalCount + " 个生物造成 " + amount + " 点 " + type + " 伤害"),
                true);
        return count;
    }

    private static Entity getAttacker(CommandSourceStack source) {
        Entity attacker = source.getEntity();
        if (attacker != null)
            return attacker;
        ServerLevel level = source.getLevel();
        return FakePlayerFactory.getMinecraft(level);
    }

    @Override
    public String getName() {
        return "attack";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        SharedSuggestionProvider.suggest(DAMAGE_TYPES, builder))
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0.1F))
                                        .executes(ctx -> {
                                            Collection<? extends Entity> targets =
                                                    EntityArgument.getEntities(ctx, "targets");
                                            String type = StringArgumentType.getString(ctx, "type");
                                            float amount = FloatArgumentType.getFloat(ctx, "amount");
                                            return executeBatchAttack(ctx.getSource(), targets, type, amount);
                                        }))));
    }
}
