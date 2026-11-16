package miniredis.server;

import miniredis.command.CommandParser;
import miniredis.store.Store;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Handles communication with a single connected client.
 * Executed as a task inside the thread pool.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final CommandParser commandParser;

    public ClientHandler(Socket socket, CommandParser commandParser) {
        this.socket = socket;
        this.commandParser = commandParser;
    }

    public ClientHandler(Socket socket) {
        this(socket, new CommandParser(new Store()));
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        System.out.println("[" + threadName + "] Started session for " + socket.getRemoteSocketAddress());

        // Auto-close streams when exiting the block
        try (
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true) // true = auto-flush
        ) {
            String line;
            // Read lines until client disconnects (in.readLine() returns null)
            while ((line = in.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                // Graceful client exit command
                if ("QUIT".equalsIgnoreCase(trimmed)) {
                    out.println("BYE");
                    break;
                }

                // Parse and execute database command
                String response = commandParser.parseAndExecute(line);
                out.println(response);
            }
        } catch (IOException e) {
            // Catches abrupt client disconnects (e.g. terminal killed, connection reset)
            System.out.println("[" + threadName + "] Client disconnected unexpectedly: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            System.out.println("[" + threadName + "] Session finished and connection closed.");
        }
    }
}
