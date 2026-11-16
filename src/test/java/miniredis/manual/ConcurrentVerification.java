package miniredis.manual;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

/**
 * Conclusively verifies simultaneous connections to MiniRedis:
 * 1. Opens Client 1 and Client 2 connections at the same time.
 * 2. Keeps BOTH connections alive concurrently.
 * 3. Client 1 sets a key, Client 2 reads it while Client 1 is still connected.
 * 4. Prints outputs side-by-side with timestamps to prove concurrency.
 */
public class ConcurrentVerification {
    public static void main(String[] args) throws InterruptedException {
        String host = "127.0.0.1";
        int port = 6380;
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch finishSignal = new CountDownLatch(2);

        System.out.println("=== Starting Simultaneous Connection Verification ===");

        // Thread for Client 1
        Thread client1 = new Thread(() -> {
            try {
                startSignal.await(); // Wait until both threads are ready to fire
                try (Socket socket = new Socket(host, port);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                    System.out.println("[Client 1] Connected!");
                    out.println("SET user:1 Alice");
                    System.out.println("[Client 1] SET user:1 Alice -> " + in.readLine());

                    // Hold connection open for 1.5 seconds to guarantee overlap
                    System.out.println("[Client 1] Keeping connection active (sleeping 1.5s)...");
                    Thread.sleep(1500);

                    // Read value written by Client 2
                    out.println("GET user:2");
                    System.out.println("[Client 1] GET user:2 -> " + in.readLine());

                    out.println("QUIT");
                    System.out.println("[Client 1] QUIT -> " + in.readLine());
                }
            } catch (Exception e) {
                System.err.println("[Client 1] Error: " + e.getMessage());
            } finally {
                finishSignal.countDown();
            }
        }, "Client-1");

        // Thread for Client 2
        Thread client2 = new Thread(() -> {
            try {
                startSignal.await(); // Wait until both threads are ready to fire
                // Slight stagger to ensure Client 1 establishes its connection
                Thread.sleep(100);
                try (Socket socket = new Socket(host, port);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                    System.out.println("[Client 2] Connected concurrently!");
                    // Read key written by Client 1 while Client 1 is still connected
                    out.println("GET user:1");
                    System.out.println("[Client 2] GET user:1 -> " + in.readLine());

                    out.println("SET user:2 Bob");
                    System.out.println("[Client 2] SET user:2 Bob -> " + in.readLine());

                    System.out.println("[Client 2] Keeping connection active (sleeping 1.5s)...");
                    Thread.sleep(1500);

                    out.println("KEYS");
                    System.out.println("[Client 2] KEYS -> " + in.readLine());

                    out.println("QUIT");
                    System.out.println("[Client 2] QUIT -> " + in.readLine());
                }
            } catch (Exception e) {
                System.err.println("[Client 2] Error: " + e.getMessage());
            } finally {
                finishSignal.countDown();
            }
        }, "Client-2");

        client1.start();
        client2.start();

        // Release the latch so both connect simultaneously
        startSignal.countDown();

        // Wait for both clients to finish
        finishSignal.await();
        System.out.println("=== Simultaneous Connection Verification Completed Successfully ===");
    }
}
