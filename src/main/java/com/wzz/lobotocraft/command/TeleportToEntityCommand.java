package com.wzz.lobotocraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.stream.Stream;

public class TeleportToEntityCommand implements ICommand {

    private static final Map<String, String> ENTITY_ALIASES = Map.ofEntries(
            Map.entry("cow", "cow"),
            Map.entry("pig", "pig"),
            Map.entry("chicken", "chicken"),
            Map.entry("sheep", "sheep"),
            Map.entry("villager", "villager"),
            Map.entry("zombie", "zombie"),
            Map.entry("skeleton", "skeleton"),
            Map.entry("creeper", "creeper"),
            Map.entry("spider", "spider"),
            Map.entry("enderman", "enderman"),
            Map.entry("horse", "horse"),
            Map.entry("wolf", "wolf"),
            Map.entry("cat", "cat"),
            Map.entry("iron_golem", "iron_golem"),
            Map.entry("wither", "wither"),
            Map.entry("dragon", "ender_dragon"),
            Map.entry("vacuole", "vacuole")
    );

    private static final SuggestionProvider<CommandSourceStack> ENTITY_SUGGESTIONS =
            (context, builder) -> {
                String input = builder.getRemaining().toLowerCase();
                List<String> nearbyEntities = getNearbyEntityTypes(context.getSource());

                Stream<String> aliasStream = ENTITY_ALIASES.keySet().stream()
                        .filter(alias -> alias.toLowerCase().contains(input));
                Stream<String> registryStream = ForgeRegistries.ENTITY_TYPES.getValues().stream()
                        .map(type -> ForgeRegistries.ENTITY_TYPES.getKey(type).getPath())
                        .filter(name -> !name.equals("player"))
                        .filter(name -> name.toLowerCase().contains(input));

                List<String> suggestions = Stream.of(
                                nearbyEntities.stream().filter(name -> name.toLowerCase().contains(input)),
                                aliasStream,
                                registryStream
                        )
                        .flatMap(s -> s)
                        .distinct()
                        .sorted((a, b) -> {
                            boolean aNear = nearbyEntities.contains(a);
                            boolean bNear = nearbyEntities.contains(b);
                            boolean aAlias = ENTITY_ALIASES.containsKey(a);
                            boolean bAlias = ENTITY_ALIASES.containsKey(b);
                            if (aNear && !bNear) return -1;
                            if (!aNear && bNear) return 1;
                            if (aAlias && !bAlias) return -1;
                            if (!aAlias && bAlias) return 1;
                            return a.compareTo(b);
                        })
                        .limit(20)
                        .toList();

                return SharedSuggestionProvider.suggest(suggestions, builder);
            };

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(getName())
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("entityType", StringArgumentType.string())
                        .suggests(ENTITY_SUGGESTIONS)
                        .executes(this::execute));
    }

    @Override
    public String getName() {
        return "tpentity";
    }

    private int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String entityTypeName = StringArgumentType.getString(context, "entityType");
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

        String actualName = ENTITY_ALIASES.getOrDefault(entityTypeName.toLowerCase(), entityTypeName.toLowerCase());

        Optional<EntityType<?>> entityTypeOpt = ForgeRegistries.ENTITY_TYPES.getValues().stream()
                .filter(type -> {
                    String path = ForgeRegistries.ENTITY_TYPES.getKey(type).getPath().toLowerCase();
                    String desc = type.getDescriptionId().toLowerCase();
                    return path.contains(actualName) || path.equals(actualName) || desc.contains(actualName);
                })
                .findFirst();

        if (entityTypeOpt.isEmpty()) {
            List<String> suggestions = ForgeRegistries.ENTITY_TYPES.getValues().stream()
                    .map(type -> ForgeRegistries.ENTITY_TYPES.getKey(type).getPath())
                    .filter(name -> name.toLowerCase().contains(actualName.substring(0, Math.min(3, actualName.length()))))
                    .limit(5)
                    .toList();
            String hint = suggestions.isEmpty() ? "" : " 你是否想要: " + String.join(", ", suggestions);
            player.sendSystemMessage(Component.literal("§c找不到实体类型: " + entityTypeName + hint));
            return 0;
        }

        EntityType<?> targetType = entityTypeOpt.get();
        Entity nearest = null;

        for (int range = 50; range <= 600; range += 50) {
            List<Entity> nearby = level.getEntitiesOfClass(Entity.class,
                    player.getBoundingBox().inflate(range),
                    entity -> entity.getType() == targetType && entity != player);
            if (!nearby.isEmpty()) {
                nearest = nearby.stream()
                        .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)))
                        .orElse(null);
                break;
            }
        }

        if (nearest == null) {
            player.sendSystemMessage(Component.literal("§c在附近600格内找不到 " + entityTypeName + " 类型的实体"));
            return 0;
        }

        Vec3 targetPos = nearest.position().add(2, 0, 0);
        BlockPos safePos = findSafePosition(level, BlockPos.containing(targetPos));
        double distance = Math.sqrt(player.distanceToSqr(nearest));

        player.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
        player.sendSystemMessage(Component.literal(
                String.format("§a已传送到最近的 %s 旁边 (距离: %.1f格)", entityTypeName, distance)));
        return 1;
    }

    private static List<String> getNearbyEntityTypes(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = source.getLevel();
            return level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(50))
                    .stream()
                    .filter(entity -> entity != player)
                    .map(entity -> Objects.requireNonNull(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())).getPath())
                    .distinct()
                    .sorted()
                    .limit(10)
                    .toList();
        } catch (CommandSyntaxException e) {
            return List.of();
        }
    }

    private static BlockPos findSafePosition(ServerLevel level, BlockPos pos) {
        for (int y = pos.getY(); y < pos.getY() + 10; y++) {
            BlockPos check = new BlockPos(pos.getX(), y, pos.getZ());
            if (level.getBlockState(check).isAir()
                    && level.getBlockState(check.above()).isAir()
                    && !level.getBlockState(check.below()).isAir()) {
                return check;
            }
        }
        return pos;
    }
}