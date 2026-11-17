package miniredis.store;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory key-value store with TTL (Time-To-Live) expiration.
 * Backed by ConcurrentHashMap<String, Entry>.
 */
public class Store {
    // Maps each key to an Entry containing its value and expiration timestamp
    private final ConcurrentHashMap<String, Entry> data = new ConcurrentHashMap<>();

    /**
     * Stores a key-value pair with no expiration (persists indefinitely).
     */
    public void put(String key, String value) {
        data.put(key, new Entry(value));
    }

    /**
     * Stores a key-value pair that expires after the given number of seconds.
     */
    public void put(String key, String value, long ttlSeconds) {
        long expiresAt = System.currentTimeMillis() + (ttlSeconds * 1000L);
        data.put(key, new Entry(value, expiresAt));
    }

    /**
     * Retrieves the value for a key, applying lazy expiration.
     * If the key is expired, it is removed immediately and null is returned.
     */
    public String get(String key) {
        Entry entry = data.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            // Lazy expiration: delete upon access
            data.remove(key, entry);
            return null;
        }
        return entry.getValue();
    }

    public boolean delete(String key) {
        return data.remove(key) != null;
    }

    public boolean exists(String key) {
        // Must perform lazy expiration check so expired keys return false
        return get(key) != null;
    }

    /**
     * Returns an unmodifiable set of all active, non-expired keys.
     */
    public Set<String> keys() {
        Set<String> validKeys = new HashSet<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Entry> e : data.entrySet()) {
            Entry entry = e.getValue();
            if (entry.hasExpiry() && now >= entry.getExpiresAtMillis()) {
                // Passively clean expired keys encountered during iteration
                data.remove(e.getKey(), entry);
            } else {
                validKeys.add(e.getKey());
            }
        }
        return Collections.unmodifiableSet(validKeys);
    }

    /**
     * Returns the remaining time-to-live of a key in seconds according to Redis spec:
     *   -2 if the key does not exist (or has expired).
     *   -1 if the key exists but has no expiration.
     *   >= 0 remaining lifetime in seconds.
     */
    public long ttl(String key) {
        Entry entry = data.get(key);
        if (entry == null) {
            return -2;
        }
        if (entry.isExpired()) {
            data.remove(key, entry);
            return -2;
        }
        return entry.getTtlSeconds();
    }

    /**
     * Sets an expiration timeout in seconds on an existing key.
     *
     * @return true if the timeout was set, false if the key does not exist or expired.
     */
    public boolean expire(String key, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            // Setting TTL <= 0 deletes the key according to Redis specification
            return delete(key);
        }
        long expiresAt = System.currentTimeMillis() + (ttlSeconds * 1000L);
        Entry updated = data.computeIfPresent(key, (k, existing) -> {
            if (existing.isExpired()) {
                return null; // Key expired; remove it
            }
            return new Entry(existing.getValue(), expiresAt);
        });
        return updated != null;
    }

    /**
     * Atomically increments or decrements the integer value stored at key by delta.
     * If the key does not exist or has expired, it is initialized to 0.
     */
    public long incrementBy(String key, long delta) throws NumberFormatException, ArithmeticException {
        Entry updated = data.compute(key, (k, existing) -> {
            long current = 0;
            Long preserveExpiry = null;
            if (existing != null) {
                if (!existing.isExpired()) {
                    current = Long.parseLong(existing.getValue());
                    preserveExpiry = existing.getExpiresAtMillis();
                }
            }
            long next = Math.addExact(current, delta);
            return new Entry(String.valueOf(next), preserveExpiry);
        });
        return Long.parseLong(updated.getValue());
    }

    /**
     * Active expiration cleanup: iterates through keys and purges expired ones.
     * Called periodically by ExpiryManager.
     *
     * @return count of keys purged in this pass
     */
    public int purgeExpiredKeys() {
        int purged = 0;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Entry> e : data.entrySet()) {
            Entry entry = e.getValue();
            if (entry.hasExpiry() && now >= entry.getExpiresAtMillis()) {
                if (data.remove(e.getKey(), entry)) {
                    purged++;
                }
            }
        }
        return purged;
    }

    public void clear() {
        data.clear();
    }

    public int size() {
        return keys().size();
    }
}
