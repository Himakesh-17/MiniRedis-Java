package miniredis;

import miniredis.persistence.PersistenceLog;
import miniredis.server.Server;
import miniredis.store.Store;

import java.io.IOException;

public class Main {
    public static final int DEFAULT_PORT = 6380;

    public static void main(String[] args) {
        int maxKeys = Store.DEFAULT_MAX_SIZE;
        boolean aofEnabled = true;

        if (args.length > 0) {
            try {
                maxKeys = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("[Main] Invalid maxKeys argument, using default: " + maxKeys);
            }
        }

        if (args.length > 1) {
            if ("false".equalsIgnoreCase(args[1]) || "no-aof".equalsIgnoreCase(args[1]) || "off".equalsIgnoreCase(args[1])) {
                aofEnabled = false;
            }
        }

        Store store = new Store(maxKeys);
        PersistenceLog persistenceLog = null;

        if (aofEnabled) {
            try {
                persistenceLog = new PersistenceLog();
                System.out.println("[Main] Replaying Append-Only Log from " + persistenceLog.getLogFile().getName() + "...");
                int replayed = persistenceLog.replay(store);
                System.out.println("[Main] AOF replay complete. Replayed " + replayed + " commands. Store currently has " + store.size() + " active keys.");
            } catch (IOException e) {
                System.err.println("[Main] Could not initialize AOF persistence: " + e.getMessage());
            }
        } else {
            System.out.println("[Main] AOF Persistence is DISABLED.");
        }

        System.out.println("[Main] Initializing MiniRedis with LRU max capacity: " + maxKeys + " keys.");
        Server server = new Server(DEFAULT_PORT, store, persistenceLog);
        server.start();
    }
}
