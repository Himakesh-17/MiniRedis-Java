package miniredis.store;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory key-value store.
 * Encapsulates ConcurrentHashMap so future phases (TTL expiry, LRU eviction)
 * can be plugged in without changing individual commands.
 */
public class Store {
    // ConcurrentHashMap allows safe concurrent reads and writes from multiple worker threads
    private final ConcurrentHashMap<String, String> data = new ConcurrentHashMap<>();

    public void put(String key, String value) {
        data.put(key, value);
    }

    public String get(String key) {
        return data.get(key);
    }

    public boolean delete(String key) {
        return data.remove(key) != null;
    }

    public boolean exists(String key) {
        return data.containsKey(key);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(data.keySet());
    }

    public void clear() {
        data.clear();
    }

    public int size() {
        return data.size();
    }

    /**
     * Atomically increments or decrements the integer value stored at key by delta.
     * If the key does not exist, it is initialized to 0 before applying the delta.
     * Uses Math.addExact to detect 64-bit integer overflow.
     *
     * @param key the key to increment or decrement
     * @param delta the amount to add (+1 for INCR, -1 for DECR)
     * @return the new value after the operation
     * @throws NumberFormatException if the existing value is not a valid 64-bit integer
     * @throws ArithmeticException if the operation would overflow a 64-bit integer
     */
    public long incrementBy(String key, long delta) throws NumberFormatException, ArithmeticException {
        String updated = data.compute(key, (k, existingVal) -> {
            long current = 0;
            if (existingVal != null) {
                current = Long.parseLong(existingVal);
            }
            long next = Math.addExact(current, delta);
            return String.valueOf(next);
        });
        return Long.parseLong(updated);
    }
}
