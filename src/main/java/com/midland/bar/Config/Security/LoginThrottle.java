package com.midland.bar.Config.Security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down guessing at the sign-in doors (owner, 2026-10-18): a username
 * that gets its password wrong MAX_PER_USER times in a window is closed for
 * LOCK; so is an address that fails MAX_PER_ADDRESS times on any login
 * (password, staff code + PIN), so one machine cannot spray codes and
 * usernames. A good sign-in clears the username, not the address.
 *
 * Kept in memory: the backend runs as one instance, and a restart forgiving
 * the counts is harmless - the lock is time, not a decision.
 */
@Component
@Slf4j
public class LoginThrottle {

    public static final int MAX_PER_USER = 5;
    public static final int MAX_PER_ADDRESS = 30;
    public static final Duration WINDOW = Duration.ofMinutes(15);
    public static final Duration LOCK = Duration.ofMinutes(15);

    /** key -> [failures in the window, window start ms, locked until ms]. */
    private final Map<String, long[]> counts = new ConcurrentHashMap<>();

    /** Minutes left on a lock for this username or address, or 0 when it may try. */
    public long lockedMinutes(String username, HttpServletRequest request) {
        long now = System.currentTimeMillis();
        long until = Math.max(lockedUntil(userKey(username)), lockedUntil(addressKey(request)));
        return until > now ? Math.max(1, (until - now + 59_999) / 60_000) : 0;
    }

    /** A wrong password or PIN. Returns the minutes now locked, or 0. */
    public long failed(String username, HttpServletRequest request) {
        long user = username == null || username.isBlank() ? 0 : bump(userKey(username), MAX_PER_USER);
        long address = bump(addressKey(request), MAX_PER_ADDRESS);
        if (user > 0 || address > 0)
            log.warn("Sign-in locked for {} min: user={} address={}", LOCK.toMinutes(), username, address(request));
        return Math.max(user, address);
    }

    public void succeeded(String username) {
        if (username != null)
            counts.remove(userKey(username));
    }

    private long bump(String key, int max) {
        long now = System.currentTimeMillis();
        long[] c = counts.compute(key, (k, v) -> {
            if (v == null || now - v[1] > WINDOW.toMillis())
                v = new long[]{0, now, 0};
            if (v[2] > now)
                return v;
            v[0]++;
            if (v[0] >= max) {
                v[0] = 0;
                v[1] = now;
                v[2] = now + LOCK.toMillis();
            }
            return v;
        });
        if (counts.size() > 50_000)
            counts.entrySet().removeIf(e -> now - e.getValue()[1] > WINDOW.toMillis() && e.getValue()[2] < now);
        return c[2] > now ? LOCK.toMinutes() : 0;
    }

    private long lockedUntil(String key) {
        long[] c = counts.get(key);
        return c == null ? 0 : c[2];
    }

    private static String userKey(String username) {
        return "u:" + (username == null ? "" : username.trim().toLowerCase());
    }

    private static String addressKey(HttpServletRequest request) {
        return "a:" + address(request);
    }

    /** The caller's address: Railway's edge sets X-Real-IP (and puts it first in X-Forwarded-For). */
    static String address(HttpServletRequest request) {
        if (request == null)
            return "?";
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank())
            return real.trim();
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank())
            return fwd.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
