package miniredis.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerTest {
    private static final int TEST_PORT = 16380;
    private Server server;
    private Thread serverThread;

    @BeforeEach
    void setUp() throws InterruptedException {
        server = new Server(TEST_PORT);
        serverThread = new Thread(server::start);
        serverThread.start();
        Thread.sleep(250);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        server.stop();
        serverThread.join(1000);
    }

    @Test
    void testCommandsOverTcpSocket() throws IOException {
        try (
            Socket socket = new Socket("127.0.0.1", TEST_PORT);
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
        ) {
            out.println("SET name Asha");
            assertEquals("OK", in.readLine());

            out.println("GET name");
            assertEquals("Asha", in.readLine());

            out.println("DEL name");
            assertEquals("1", in.readLine());

            out.println("GET name");
            assertEquals("(nil)", in.readLine());

            out.println("QUIT");
            assertEquals("BYE", in.readLine());
        }
    }
}
