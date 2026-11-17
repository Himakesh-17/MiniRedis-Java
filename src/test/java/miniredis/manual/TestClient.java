package miniredis.manual;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Interactive test client demonstrating MiniRedis commands including TTL and EXPIRE.
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
                "TTL name",
                "SET otp 4821 EX 2",
                "GET otp",
                "TTL otp",
                "INCR visits",
                "EXPIRE visits 2",
                "TTL visits",
                "KEYS"
            };

            for (String cmd : testCommands) {
                System.out.println("-> Sending:  " + cmd);
                out.println(cmd);

                String reply = in.readLine();
                System.out.println("<- Received: " + reply);
                System.out.println();
            }

            System.out.println("Waiting 2.2 seconds for 'otp' and 'visits' to expire...");
            Thread.sleep(2200);

            String[] afterExpiryCommands = {
                "GET otp",
                "TTL otp",
                "GET visits",
                "KEYS",
                "QUIT"
            };

            for (String cmd : afterExpiryCommands) {
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
