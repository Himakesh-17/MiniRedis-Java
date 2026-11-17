package miniredis.store;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Active background expiration daemon.
 * Periodically scans the Store and removes expired keys to reclaim memory.
 */
public class ExpiryManager {
    private final Store store;
    private final ScheduledExecutorService scheduler;
    private final long intervalMs;

    public ExpiryManager(Store store, long intervalMs) {
        this.store = store;
        this.intervalMs = intervalMs;
        // Daemon thread factory ensures the background task will not block JVM exit
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "miniredis-expiry-daemon");
            t.setDaemon(true);
            return t;
        });
    }

    public ExpiryManager(Store store) {
        this(store, 100); // Check every 100 milliseconds by default
    }

    /**
     * Starts the periodic cleanup task.
     */
    public void start() {
        scheduler.scheduleAtFixedRate(store::purgeExpiredKeys, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Shuts down the background daemon cleanly.
     */
    public void stop() {
        scheduler.shutdownNow();
    }
}
