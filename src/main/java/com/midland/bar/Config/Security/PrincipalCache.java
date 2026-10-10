package com.midland.bar.Config.Security;

import com.midland.bar.Uaa.Model.User;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.beans.BeanUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Who is signed in, kept for a short while so every request does not read
 * the user, their roles and permissions and their branch from the database
 * again - those reads were most of the database's work (a phone polling every
 * few seconds is thousands of requests an hour per branch).
 *
 * Entries live 30 seconds and the whole cache is dropped the moment a user,
 * role, branch or staff member is saved (see {@link Evict}), so a block, a
 * new role or a removed staff member still counts at once. Each request gets
 * its own copy of the user: the filter sets the active branch on it, and two
 * devices of one user may work in two branches.
 */
public final class PrincipalCache {

    private static final long TTL_MS = 30_000;
    /** A safety cap: past it the cache starts over rather than growing. */
    private static final int MAX = 20_000;

    private record Entry(Object value, long until) {}

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private PrincipalCache() {}

    /** The user for a token's username - a fresh copy each call; null is never kept. */
    public static User user(String username, Supplier<User> load) {
        User cached = get("u:" + username, load);
        if (cached == null)
            return null;
        User copy = new User();
        BeanUtils.copyProperties(cached, copy);
        return copy;
    }

    /** Any other lookup the filter makes on every request (a staff session's branch, whether the staff member is still there). */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Supplier<T> load) {
        long now = System.currentTimeMillis();
        Entry e = CACHE.get(key);
        if (e != null && e.until() > now)
            return (T) e.value();
        T value = load.get();
        if (value != null) {
            if (CACHE.size() >= MAX)
                CACHE.clear();
            CACHE.put(key, new Entry(value, now + TTL_MS));
        }
        return value;
    }

    /** Forget everything - after any change to who may do what. */
    public static void evictAll() {
        CACHE.clear();
    }

    /** On User, Role, Branch and BarStaff: any save or delete drops the cache. */
    public static class Evict {
        @PostPersist
        @PostUpdate
        @PostRemove
        public void changed(Object entity) {
            evictAll();
        }
    }
}
