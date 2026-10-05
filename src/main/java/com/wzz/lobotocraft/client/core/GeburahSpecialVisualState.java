package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.core_suppression.geburah.GeburahSpecialVisuals.*;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahSpecialVisuals;
import java.util.*;

/** Bounded, clock-driven cosmetic state, testable without a renderer or network connection. */
public final class GeburahSpecialVisualState {
    private final Map<UUID, Cast> casts = new LinkedHashMap<>();
    private final Map<UUID, Long> closed = new LinkedHashMap<>();
    private final ArrayDeque<Accent> accents = new ArrayDeque<>();

    public void accept(Snapshot snapshot, long now) {
        long lastClosed = closed.getOrDefault(snapshot.caster(), 0L);
        if (snapshot.castId() <= lastClosed) return;
        Cast old = casts.get(snapshot.caster());
        if (old != null && snapshot.castId() < old.snapshot.castId()) return;
        if (old != null && old.snapshot.equals(snapshot)) return;
        if (snapshot.event() == Event.STOP) {
            if (old == null || snapshot.castId() >= old.snapshot.castId()) casts.remove(snapshot.caster());
            closed.put(snapshot.caster(), snapshot.castId());
            while (closed.size() > 32) closed.remove(closed.keySet().iterator().next());
            return;
        }
        if (old == null || old.snapshot.castId() != snapshot.castId()) {
            if (casts.size() >= GeburahSpecialVisuals.MAX_CASTS) return;
            old = new Cast(snapshot, now);
            casts.put(snapshot.caster(), old);
        }
        if (snapshot.elapsed() < old.snapshot.elapsed()) return;
        old.snapshot = snapshot;
        old.received = now;
        if (snapshot.event() == Event.CUE && snapshot.cueIndex() > old.lastCue) {
            old.lastCue = snapshot.cueIndex();
            addAccent(new Accent(snapshot, now, 10));
        } else if (snapshot.event() == Event.DASH && !old.dashed) {
            old.dashed = true;
            addAccent(new Accent(snapshot, now, 7));
        }
    }
    private void addAccent(Accent accent) {
        if (accents.size() >= GeburahSpecialVisuals.MAX_ACCENTS) accents.removeFirst();
        accents.addLast(accent);
    }
    public List<Frame> frames(long now) {
        expire(now);
        List<Frame> frames = new ArrayList<>();
        for (Cast cast : casts.values()) {
            frames.add(new Frame(cast.snapshot, cast.snapshot.elapsed() + Math.max(0, now - cast.received)));
        }
        return List.copyOf(frames);
    }
    public List<Accent> accents(long now) {
        expire(now);
        return List.copyOf(accents);
    }
    private void expire(long now) {
        casts.entrySet().removeIf(entry -> {
            Cast cast = entry.getValue();
            boolean expired = cast.snapshot.elapsed() + Math.max(0, now - cast.received)
                    > cast.snapshot.skill().duration() + 2;
            if (expired) closed.put(entry.getKey(), cast.snapshot.castId());
            return expired;
        });
        while (closed.size() > 32) closed.remove(closed.keySet().iterator().next());
        accents.removeIf(accent -> now - accent.start >= accent.duration);
    }
    public void remove(UUID caster) {
        Cast cast = casts.remove(caster);
        if (cast != null) closed.put(caster, cast.snapshot.castId());
        accents.removeIf(a -> a.snapshot.caster().equals(caster));
    }
    public void clearAccents() { accents.clear(); }
    public void clear() { casts.clear(); closed.clear(); accents.clear(); }
    public record Frame(Snapshot snapshot, double elapsed) {}
    public record Accent(Snapshot snapshot, long start, int duration) {}
    private static final class Cast {
        Snapshot snapshot;
        long received;
        int lastCue = -1;
        boolean dashed;
        Cast(Snapshot snapshot, long received) { this.snapshot = snapshot; this.received = received; }
    }
}
