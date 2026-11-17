package miniredis;

import miniredis.server.Server;
import miniredis.store.Store;

public class Main {
    // Default port for MiniRedis
    public static final int DEFAULT_PORT = 6380;

    public static void main(String[] args) {
        int maxKeys = Store.DEFAULT_MAX_SIZE;
        if (args.length > 0) {
            try {
                maxKeys = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid maxKeys argument, using default: " + maxKeys);
            }
        }
        System.out.println("[Main] Initializing MiniRedis with LRU max capacity: " + maxKeys + " keys.");
        Server server = new Server(DEFAULT_PORT, new Store(maxKeys));
        server.start();
    }
}
