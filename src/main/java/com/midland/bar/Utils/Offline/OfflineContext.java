package com.midland.bar.Utils.Offline;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * A request replayed from a device's offline queue carries when it really
 * happened (X-Client-Time, epoch millis), an id of its own (X-Op-Id) and
 * X-Offline: 1. Sales, payments and shifts take their times from here so a
 * sale made at 21:10 without internet counts at 21:10 - in its shift, its day
 * and its week - even if it reaches the server at 23:40.
 */
public final class OfflineContext {

    /** How far back a queued operation may claim to be from. */
    private static final long MAX_AGE_DAYS = 7;
    /** Device clocks drift; a time a little ahead of the server is accepted as now. */
    private static final long MAX_AHEAD_MINUTES = 5;

    private record Ctx(boolean replay, String opId, LocalDateTime clientTime) {}

    private static final ThreadLocal<Ctx> CURRENT = new ThreadLocal<>();

    private OfflineContext() {}

    static void set(String offline, String opId, String clientTime) {
        boolean replay = "1".equals(offline) || "true".equalsIgnoreCase(offline);
        LocalDateTime at = null;
        if (replay && clientTime != null && !clientTime.isBlank()) {
            try {
                at = LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(clientTime.trim())), ZoneId.systemDefault());
            } catch (NumberFormatException ignored) {
                // Not a time - the request is still taken, at server time.
            }
        }
        CURRENT.set(new Ctx(replay, opId == null || opId.isBlank() ? null : opId.trim(), at));
    }

    static void clear() {
        CURRENT.remove();
    }

    /** Whether this request is a queued operation sent after the device came back online. */
    public static boolean isReplay() {
        Ctx c = CURRENT.get();
        return c != null && c.replay();
    }

    /**
     * The operation's id, replayed or not: the device sends one with every
     * sale it might have to resend (a request whose answer was lost on a bad
     * line is sent again from the queue), so the second copy is recognised.
     */
    public static String opId() {
        Ctx c = CURRENT.get();
        return c == null ? null : c.opId();
    }

    /** When it happened: the device's time for a replayed operation (within reason), otherwise now. */
    public static LocalDateTime now() {
        LocalDateTime serverNow = LocalDateTime.now();
        Ctx c = CURRENT.get();
        if (c == null || !c.replay() || c.clientTime() == null)
            return serverNow;
        LocalDateTime at = c.clientTime();
        if (at.isAfter(serverNow.plusMinutes(MAX_AHEAD_MINUTES)) || at.isBefore(serverNow.minusDays(MAX_AGE_DAYS)))
            return serverNow;
        return at.isAfter(serverNow) ? serverNow : at;
    }

    public static LocalDate today() {
        return now().toLocalDate();
    }
}
