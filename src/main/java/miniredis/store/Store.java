package miniredis.store;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory key-value store with:
 * 1. TTL (Time-To-Live) lazy and active expiration.
 * 2. Maximum key capacity with custom LRU (Least Recently Used) eviction.
 */
public class Store {
    public static final int DEFAULT_MAX_SIZE = 1000;

    private final int maxSize;
    private final ConcurrentHashMap<String, Entry> data = new ConcurrentHashMap<>();
    private final LruCache lruCache = new LruCache();
    private final Object evictionLock = new Object();

    public Store(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("Max size must be greater than 0");
        }
        this.maxSize = maxSize;
    }

    public Store() {
        this(DEFAULT_MAX_SIZE);
    }

    /**
     * Stores a key-value pair without expiration.
     * Evicts the Least Recently Used key if capacity is reached.
     */
    public void put(String key, String value) {
        putWithExpiry(key, value, null);
    }

    /**
     * Stores a key-value pair that expires after ttlSeconds.
     * Evicts the Least Recently Used key if capacity is reached.
     */
    public void put(String key, String value, long ttlSeconds) {
        putWithExpiry(key, value, ttlSeconds);
    }

    private void putWithExpiry(String key, String value, Long ttlSeconds) {
        synchronized (evictionLock) {
            Entry existing = data.get(key);
            Long expiresAt = (ttlSeconds != null) ? System.currentTimeMillis() + (ttlSeconds * 1000L) : null;

            if (existing != null && !existing.isExpired()) {
                // Updating existing key does NOT trigger eviction; just update and touch
                data.put(key, new Entry(value, expiresAt));
                lruCache.touch(key);
                return;
            }

            // It's a brand new (or previously expired) key
            // First, purge any expired keys to see if space frees up naturally
            if (data.size() >= maxSize) {
                purgeExpiredKeys();
            }

            // If still full, evict the Least Recently Used key
            while (data.size() >= maxSize) {
                String evictedKey = lruCache.removeLast();
                if (evictedKey == null) {
                    break;
                }
                data.remove(evictedKey);
            }

            data.put(key, new Entry(value, expiresAt));
            lruCache.add(key);
        }
    }

    /**
     * Retrieves the value for key.
     * On HIT, moves key to front as Most Recently Used.
     * On EXPIRED, removes it from both data and LRU.
     */
    public String get(String key) {
        Entry entry = data.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            delete(key); // Cleans up from data and LRU
            return null;
        }
        lruCache.touch(key); // HIT -> touch
        return entry.getValue();
    }

    /**
     * Deletes a key from data and the LRU cache.
     */
    public boolean delete(String key) {
        synchronized (evictionLock) {
            boolean removed = data.remove(key) != null;
            lruCache.remove(key);
            return removed;
        }
    }

    /**
     * Checks if key exists. Refreshes LRU on hit.
     */
    public boolean exists(String key) {
        return get(key) != null;
    }

    /**
     * Returns all active, non-expired keys.
     */
    public Set<String> keys() {
        Set<String> validKeys = new HashSet<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Entry> e : data.entrySet()) {
            Entry entry = e.getValue();
            if (entry.hasExpiry() && now >= entry.getExpiresAtMillis()) {
                delete(e.getKey());
            } else {
                validKeys.add(e.getKey());
            }
        }
        return Collections.unmodifiableSet(validKeys);
    }

    /**
     * Returns TTL of key according to Redis spec.
     */
    public long ttl(String key) {
        Entry entry = data.get(key);
        if (entry == null) {
            return -2;
        }
        if (entry.isExpired()) {
            delete(key);
            return -2;
        }
        lruCache.touch(key);
        return entry.getTtlSeconds();
    }

    /**
     * Sets expiration on an existing key and refreshes its LRU position.
     */
    public boolean expire(String key, long ttlSeconds) {
        synchronized (evictionLock) {
            if (ttlSeconds <= 0) {
                return delete(key);
            }
            Entry entry = data.get(key);
            if (entry == null || entry.isExpired()) {
                delete(key);
                return false;
            }
            long expiresAt = System.currentTimeMillis() + (ttlSeconds * 1000L);
            data.put(key, new Entry(entry.getValue(), expiresAt));
            lruCache.touch(key);
            return true;
        }
    }

    /**
     * Atomically increments or decrements the numeric value stored at key.
     * Counts as usage (refreshes LRU position).
     */
    public long incrementBy(String key, long delta) throws NumberFormatException, ArithmeticException {
        synchronized (evictionLock) {
            Entry existing = data.get(key);
            long current = 0;
            Long preserveExpiry = null;
            boolean isNew = false;

            if (existing != null && !existing.isExpired()) {
                current = Long.parseLong(existing.getValue());
                preserveExpiry = existing.getExpiresAtMillis();
            } else {
                isNew = true;
                if (data.size() >= maxSize) {
                    purgeExpiredKeys();
                }
                while (data.size() >= maxSize) {
                    String evicted = lruCache.removeLast();
                    if (evicted == null) {
                        break;
                    }
                    data.remove(evicted);
                }
            }

            long next = Math.addExact(current, delta);
            data.put(key, new Entry(String.valueOf(next), preserveExpiry));

            if (isNew) {
                lruCache.add(key);
            } else {
                lruCache.touch(key);
            }

            return next;
        }
    }

    /**
     * Active expiration cleanup: purges expired keys from both data and LRU.
     *
     * @return count of keys purged
     */
    public int purgeExpiredKeys() {
        int purged = 0;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Entry> e : data.entrySet()) {
            Entry entry = e.getValue();
            if (entry.hasExpiry() && now >= entry.getExpiresAtMillis()) {
                if (data.remove(e.getKey(), entry)) {
                    lruCache.remove(e.getKey());
                    purged++;
                }
            }
        }
        return purged;
    }

    public void clear() {
        synchronized (evictionLock) {
            data.clear();
            lruCache.clear();
        }
    }

    public int size() {
        return keys().size();
    }

    public int getMaxSize() {
        return maxSize;
    }

    LruCache getLruCache() {
        return lruCache;
    }
}
