package com.wzz.lobotocraft.client.core;

final class GeburahMusicPolicy {
    private GeburahMusicPolicy() {
    }

    static Decision decide(int visualStage, Track current, boolean currentActive) {
        if (visualStage > 0) {
            return new Decision(Track.EGO, true,
                    current != Track.EGO || !currentActive);
        }
        if (currentActive && (current == Track.FIRST || current == Track.SECOND)) {
            return new Decision(current, false, false);
        }
        Track next = current == Track.FIRST ? Track.SECOND : Track.FIRST;
        return new Decision(next, false, true);
    }

    enum Track {
        FIRST,
        SECOND,
        EGO
    }

    record Decision(Track track, boolean looping, boolean shouldStart) {
    }
}
