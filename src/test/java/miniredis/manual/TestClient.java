package miniredis.manual;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Interactive test client demonstrating MiniRedis commands:
 * SET, GET, EXISTS, KEYS, DEL, and QUIT.
 */
public class TestClient {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 6380;

        System.out.println("Connecting to MiniRedis on " + host + ":" + port + "...");

        try (
            Socket socket = new Socket(host, port);
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
        ) {
            System.out.println("Connected successfully!\n");

            String[] testCommands = {
                "SET name Asha",
                "GET name",
                "EXISTS name",
                "SET city Hyderabad",
                "KEYS",
                "DEL name",
                "GET name",
                "EXISTS name",
                "INCR visits",
                "INCR visits",
                "DECR visits",
                "INCR visits",
                "INCR city",
                "KEYS",
                "QUIT"
            };

            for (String cmd : testCommands) {
                System.out.println("-> Sending:  " + cmd);
                out.println(cmd);

                String reply = in.readLine();
                System.out.println("<- Received: " + reply);
                System.out.println();
            }

        } catch (Exception e) {
            System.err.println("TestClient error: " + e.getMessage());
        }
    }
}
