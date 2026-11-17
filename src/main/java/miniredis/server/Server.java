package miniredis.server;

import miniredis.command.CommandParser;
import miniredis.persistence.PersistenceLog;
import miniredis.store.ExpiryManager;
import miniredis.store.Store;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server {
    public static final int DEFAULT_WORKER_THREADS = 64;

    private final int port;
    private final Store store;
    private final CommandParser commandParser;
    private final ExpiryManager expiryManager;
    private final PersistenceLog persistenceLog;
    private final ExecutorService threadPool;

    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public Server(int port, Store store, PersistenceLog persistenceLog, int workerThreads) {
        this.port = port;
        this.store = store;
        this.persistenceLog = persistenceLog;
        this.commandParser = new CommandParser(store, persistenceLog);
        this.expiryManager = new ExpiryManager(store);
        this.threadPool = Executors.newFixedThreadPool(workerThreads);
    }

    public Server(int port, Store store, PersistenceLog persistenceLog) {
        this(port, store, persistenceLog, DEFAULT_WORKER_THREADS);
    }

    public Server(int port, Store store) {
        this(port, store, null, DEFAULT_WORKER_THREADS);
    }

    public Server(int port) {
        this(port, new Store());
    }

    public Server() {
        this(6380);
    }

    public void start() {
        this.running = true;
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("=========================================");
            System.out.println("  MiniRedis Server started on port " + port);
            System.out.println("  Ready to accept connections...");
            System.out.println("=========================================");

            // Clean shutdown when process receives SIGINT / Ctrl+C
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
            expiryManager.start();

            // Main accept loop: listen for clients continuously
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[Server] Accepted new connection from: " + clientSocket.getRemoteSocketAddress());
                    threadPool.submit(new ClientHandler(clientSocket, commandParser));
                } catch (IOException e) {
                    if (!running) {
                        break; // Expected when serverSocket.close() is called during shutdown
                    }
                    System.err.println("[Server] Error accepting client connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[Server] Could not start server on port " + port + ": " + e.getMessage());
        } finally {
            stop();
        }
    }

    public synchronized void stop() {
        if (!running && (serverSocket == null || serverSocket.isClosed())) {
            return;
        }
        running = false;
        System.out.println("[Server] Shutting down MiniRedis server...");
        expiryManager.stop();
        if (persistenceLog != null) {
            persistenceLog.close();
        }

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("[Server] Error closing server socket: " + e.getMessage());
            }
        }

        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(2, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[Server] Shutdown complete.");
    }

    public int getPort() {
        return port;
    }

    public Store getStore() {
        return store;
    }
}
