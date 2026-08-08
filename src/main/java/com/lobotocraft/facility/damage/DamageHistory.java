package com.lobotocraft.facility.damage;

import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EgoArmorHelper;
import com.wzz.lobotocraft.util.MentalValueUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public final class DamageHistory {
    private static final int HISTORY_SIZE = 5;
    private static final Map<UUID, Deque<DamageRecord>> HISTORY = new HashMap<>();
    private static final Map<UUID, IdentityHashMap<DamageSource, Deque<PendingDamage>>> PENDING = new HashMap<>();

    private DamageHistory() {
    }

    public static void captureRaw(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DamageSource source = event.getSource();
        PendingDamage pending = new PendingDamage(
            damageType(source),
            event.getAmount(),
            resistance(player, source),
            riskMultiplier(player, source),
            player.getHealth(),
            MentalValueUtil.getMentalValue(player),
            sourceName(source),
            player.level().getGameTime()
        );
        PENDING.computeIfAbsent(player.getUUID(), ignored -> new IdentityHashMap<>())
            .computeIfAbsent(source, ignored -> new ArrayDeque<>()).addLast(pending);
    }

    public static void finishHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // 未取消且伤害>0：actuallyHurt 会继续触发 LivingDamageEvent，pending 留给 finishDamage 消费
        if (!event.isCanceled() && event.getAmount() > 0.0F) return;
        // 其余情况（取消型 / 最终伤害<=0 无后续伤害事件）必须在此弹出 pending，否则永久泄漏
        PendingDamage pending = removePending(player, event.getSource());
        if (pending == null) return;
        // 未取消但伤害<=0：pending 已回收，无实际扣血/扣神，不记录
        if (!event.isCanceled()) return;

        float healthAfter = player.getHealth();
        float mentalAfter = MentalValueUtil.getMentalValue(player);
        if (mentalAfter < pending.mentalBefore - 0.001F) {
            add(player, pending.toRecord(pending.mentalBefore - mentalAfter,
                pending.mentalBefore, mentalAfter, true));
        } else if (healthAfter < pending.healthBefore - 0.001F) {
            add(player, pending.toRecord(pending.healthBefore - healthAfter,
                pending.healthBefore, healthAfter, false));
        }
    }

    public static void finishDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PendingDamage pending = removePending(player, event.getSource());
        if (pending == null || event.isCanceled() || event.getAmount() <= 0.0F) return;

        float before = player.getHealth();
        float after = Math.max(0.0F, before - event.getAmount());
        add(player, pending.toRecord(event.getAmount(), before, after, false));
        // 黑色伤害双扣：HP 已记于上，此处补记 SP 那一半（MentalValueEventHandler 在本事件前已扣 SP）
        if (pending.type == DamageKind.BLACK) {
            float mentalAfter = MentalValueUtil.getMentalValue(player);
            if (mentalAfter < pending.mentalBefore - 0.001F) {
                add(player, pending.toRecord(pending.mentalBefore - mentalAfter,
                    pending.mentalBefore, mentalAfter, true));
            }
        }
    }

    public static Component decorateDeathMessage(ServerPlayer player, Component original) {
        Component hover = buildHover(player);
        // AI 员工死亡即永久退休（retireDead 延迟了历史清理），悬浮记录构建完成后在此清空
        if (player instanceof com.wzz.lobotocraft.employee.EmployeeServerPlayer) clear(player.getUUID());
        MutableComponent highlightedName = player.getDisplayName().copy().setStyle(
            player.getDisplayName().getStyle()
                .withBold(true)
                .withUnderlined(true)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
        );
        boolean[] replaced = {false};
        MutableComponent decorated = replacePlayerName(original, player, highlightedName, replaced);
        if (replaced[0]) return decorated;

        String text = original.getString();
        String name = player.getScoreboardName();
        int index = text.indexOf(name);
        if (index < 0) return highlightedName.append(" ").append(original);
        return Component.literal(text.substring(0, index))
            .append(highlightedName)
            .append(text.substring(index + name.length()));
    }

    public static void clear(UUID playerId) {
        PENDING.remove(playerId);
        HISTORY.remove(playerId);
    }

    private static MutableComponent replacePlayerName(Component component, ServerPlayer player,
                                                        Component replacement, boolean[] replaced) {
        ComponentContents contents = component.getContents();
        MutableComponent result;
        if (contents instanceof TranslatableContents translated) {
            Object[] args = translated.getArgs().clone();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component argument) {
                    if (!replaced[0] && isPlayerName(argument, player)) {
                        args[i] = replacement;
                        replaced[0] = true;
                    } else {
                        args[i] = replacePlayerName(argument, player, replacement, replaced);
                    }
                }
            }
            result = MutableComponent.create(new TranslatableContents(
                translated.getKey(), translated.getFallback(), args));
        } else {
            result = component.plainCopy();
        }
        result.setStyle(component.getStyle());
        for (Component sibling : component.getSiblings()) {
            result.append(replacePlayerName(sibling, player, replacement, replaced));
        }
        return result;
    }

    private static boolean isPlayerName(Component component, ServerPlayer player) {
        String text = component.getString();
        return text.equals(player.getScoreboardName())
            || text.equals(player.getDisplayName().getString())
            || text.contains(player.getScoreboardName());
    }

    private static Component buildHover(ServerPlayer player) {
        MutableComponent hover = Component.literal("最近 5 条受伤记录")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        List<DamageRecord> records = new ArrayList<>(
            HISTORY.getOrDefault(player.getUUID(), new ArrayDeque<>()));
        int missing = HISTORY_SIZE - records.size();
        for (int i = 0; i < missing; i++) {
            hover.append(Component.literal("\n-- 暂无记录 --").withStyle(ChatFormatting.DARK_GRAY));
        }
        for (DamageRecord record : records) hover.append("\n").append(record.asComponent());
        return hover;
    }

    private static void add(ServerPlayer player, DamageRecord record) {
        Deque<DamageRecord> history = HISTORY.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
        history.addLast(record);
        while (history.size() > HISTORY_SIZE) history.removeFirst();
    }

    private static PendingDamage removePending(ServerPlayer player, DamageSource source) {
        IdentityHashMap<DamageSource, Deque<PendingDamage>> bySource = PENDING.get(player.getUUID());
        if (bySource == null) return null;
        Deque<PendingDamage> queue = bySource.get(source);
        if (queue == null || queue.isEmpty()) return null;
        PendingDamage pending = queue.removeLast();
        if (queue.isEmpty()) bySource.remove(source);
        if (bySource.isEmpty()) PENDING.remove(player.getUUID());
        return pending;
    }

    private static DamageKind damageType(DamageSource source) {
        if (DamageHelper.isWhiteDamage(source)) return DamageKind.WHITE;
        if (DamageHelper.isBlackDamage(source)) return DamageKind.BLACK;
        if (DamageHelper.isBlueDamage(source)) return DamageKind.BLUE;
        return DamageKind.RED;
    }

    private static double resistance(ServerPlayer player, DamageSource source) {
        if (DamageHelper.isWhiteDamage(source)) {
            return player.getAttributeValue(ModAttributes.WHITE_DAMAGE_RESISTANCE.get());
        }
        if (DamageHelper.isBlackDamage(source)) {
            return player.getAttributeValue(ModAttributes.BLACK_DAMAGE_RESISTANCE.get());
        }
        if (DamageHelper.isBlueDamage(source)) {
            return player.getAttributeValue(ModAttributes.BLUE_DAMAGE_RESISTANCE.get());
        }
        return player.getAttributeValue(ModAttributes.RED_DAMAGE_RESISTANCE.get());
    }

    private static float riskMultiplier(ServerPlayer player, DamageSource source) {
        Entity attacker = source.getEntity();
        if (!(attacker instanceof IAbnormality abnormality)
            || EgoArmorHelper.getFullSetId(player) == null) return 1.0F;
        RiskLevel armor = EgoArmorHelper.getArmorRiskLevel(player);
        return EgoArmorHelper.applyRiskLevelSuppression(1.0F, armor, abnormality.getRiskLevel());
    }

    private static String sourceName(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof IAbnormality abnormality) return abnormality.getAbnormalityName();
        if (attacker != null) return attacker.getDisplayName().getString();
        Entity direct = source.getDirectEntity();
        if (direct != null) return direct.getDisplayName().getString();
        return source.getMsgId();
    }

    private static String number(float value) {
        if (!Float.isFinite(value)) return "∞";
        float rounded = Math.round(value);
        return Math.abs(value - rounded) < 0.01F
            ? Integer.toString((int) rounded)
            : String.format(Locale.ROOT, "%.1f", value);
    }

    private enum DamageKind {
        RED(0xFFFF5555),
        WHITE(0xFFFFFFFF),
        BLACK(0xFF888888),
        BLUE(0xFF55FFFF);

        private final int color;

        DamageKind(int color) {
            this.color = color;
        }
    }

    private record PendingDamage(DamageKind type, float rawDamage, double resistance,
                                 float riskMultiplier, float healthBefore, float mentalBefore,
                                 String source, long gameTime) {
        DamageRecord toRecord(float finalDamage, float before, float after, boolean mental) {
            return new DamageRecord(type, rawDamage, resistance, riskMultiplier,
                finalDamage, before, after, mental, after <= 0.0F, source, gameTime);
        }
    }

    private record DamageRecord(DamageKind type, float rawDamage, double resistance,
                                float riskMultiplier, float finalDamage, float before,
                                float after, boolean mental, boolean lethal,
                                String source, long gameTime) {
        Component asComponent() {
            MutableComponent line = Component.literal(type.name() + " ")
                .withStyle(style -> style.withColor(type.color));
            line.append(Component.literal(number(rawDamage) + " × "
                + String.format(Locale.ROOT, "%.1f", resistance) + "（抗性）"));
            if (Math.abs(riskMultiplier - 1.0F) > 0.001F) {
                line.append(Component.literal(" × " + String.format(Locale.ROOT, "%.1f", riskMultiplier)
                    + "（等级压制）"));
            }
            String resource = mental ? "  SP " : "  ";
            line.append(Component.literal(resource + number(before) + " → "));
            // 致命一击只把归零的数值标红加粗；整行不再刷红，避免与红色伤害类型混淆
            if (lethal) {
                line.append(Component.literal(number(after))
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            } else {
                line.append(Component.literal(number(after)));
            }
            line.append(Component.literal("  " + source));
            return line;
        }
    }
}
