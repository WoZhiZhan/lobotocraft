package com.wzz.lobotocraft.client.alert;

/** Last server emergency level, independent of music playback or its four-second flash. */
public final class FacilityAlertClientState {
    private Level level = Level.UNKNOWN;

    public Level level() { return level; }

    public void receive(int wireLevel) {
        level = switch (Math.max(-1, Math.min(2, wireLevel))) {
            case -1 -> Level.NORMAL;
            case 0 -> Level.I;
            case 1 -> Level.II;
            default -> Level.III;
        };
    }

    public void clear() { level = Level.UNKNOWN; }

    public enum Level {
        UNKNOWN("screen.lobotocraft.monitor.alert.unknown", 0xFF99A3AF),
        NORMAL("screen.lobotocraft.monitor.alert.normal", 0xFF73D69B),
        I("screen.lobotocraft.monitor.alert.i", 0xFFFFD166),
        II("screen.lobotocraft.monitor.alert.ii", 0xFFFF9C52),
        III("screen.lobotocraft.monitor.alert.iii", 0xFFFF5555);

        private final String translationKey;
        private final int color;

        Level(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
        }

        public String translationKey() { return translationKey; }
        public int color() { return color; }
    }
}
