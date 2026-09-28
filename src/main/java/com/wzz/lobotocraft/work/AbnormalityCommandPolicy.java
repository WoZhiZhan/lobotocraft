package com.wzz.lobotocraft.work;

/** Command identity is resolved before Malkuth's ordinary-work remapping. */
public final class AbnormalityCommandPolicy {
    public enum Action {
        SACRIFICE("V", "work.lobotocraft.sacrifice"),
        COMMISSION("S", "work.lobotocraft.commission"),
        PROTECTION("P", "work.lobotocraft.protection");
        public final String symbol;
        public final String labelKey;
        Action(String symbol, String labelKey) { this.symbol = symbol; this.labelKey = labelKey; }
    }

    private AbnormalityCommandPolicy() {}

    public static Action action(String code, int slot) {
        if ("O-03-89".equals(code) && slot == WorkType.REPRESSION.ordinal()) return Action.SACRIFICE;
        if ("F-01-57".equals(code) && slot == WorkType.ATTACHMENT.ordinal()) return Action.COMMISSION;
        if ("D-01-106".equals(code) && slot == WorkType.ATTACHMENT.ordinal()) return Action.PROTECTION;
        return null;
    }

    public static boolean isSacrificeSlot(String code, int slot) {
        return action(code, slot) == Action.SACRIFICE;
    }

    /** Both validated UI entry points use this before starting/remapping ordinary work. */
    public static boolean dispatch(net.minecraft.server.level.ServerPlayer operator,
                                   com.wzz.lobotocraft.entity.base.AbstractAbnormality source, int slot) {
        Action action = action(source.getAbnormalityCode(), slot);
        if (action == null) return false;
        String error;
        if (action == Action.SACRIFICE
                && source instanceof com.wzz.lobotocraft.entity.abnormality.EntityCensored censored) {
            error = UniqueWorkRequest.start(operator, censored);
        } else if (action == Action.PROTECTION
                && source instanceof com.wzz.lobotocraft.entity.abnormality.EntityArmyInBlack army) {
            error = UniqueWorkRequest.startProtection(operator, army, operator);
        } else if (action == Action.COMMISSION
                && source instanceof com.wzz.lobotocraft.entity.abnormality.EntityRedHoodMercenary hood) {
            error = UniqueWorkRequest.openCommission(operator, hood);
        } else {
            error = "message.lobotocraft.unique_work.unavailable";
        }
        if (error != null) operator.sendSystemMessage(net.minecraft.network.chat.Component.translatable(error));
        return true;
    }
}
