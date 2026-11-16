package miniredis;

import miniredis.server.Server;

public class Main {
    // Default port for MiniRedis
    public static final int DEFAULT_PORT = 6380;

    public static void main(String[] args) {
        Server server = new Server(DEFAULT_PORT);
        server.start();
    }
}
