package miniredis.store;

/**
 * Represents a stored entry holding a string value and an optional expiration timestamp.
 */
public class Entry {
    private final String value;
    // Epoch timestamp in milliseconds when this key expires, or null if it never expires
    private final Long expiresAtMillis;

    public Entry(String value, Long expiresAtMillis) {
        this.value = value;
        this.expiresAtMillis = expiresAtMillis;
    }

    public Entry(String value) {
        this(value, null);
    }

    public String getValue() {
        return value;
    }

    public Long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean hasExpiry() {
        return expiresAtMillis != null;
    }

    public boolean isExpired() {
        return hasExpiry() && System.currentTimeMillis() >= expiresAtMillis;
    }

    /**
     * Calculates the remaining time-to-live in whole seconds.
     * Returns:
     *   -2 if already expired
     *   -1 if the entry never expires
     *   >= 0 remaining seconds
     */
    public long getTtlSeconds() {
        if (!hasExpiry()) {
            return -1;
        }
        long remainingMillis = expiresAtMillis - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            return -2;
        }
        // Round up to nearest whole second, matching Redis behavior
        return (remainingMillis + 999) / 1000;
    }
}
