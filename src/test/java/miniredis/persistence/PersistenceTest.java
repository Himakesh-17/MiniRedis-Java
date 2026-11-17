package miniredis.persistence;

import miniredis.command.CommandParser;
import miniredis.store.Store;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {

    private static final String TEST_LOG_PATH = "target/junit_test_aof.log";
    private File testLogFile;

    @BeforeEach
    void setUp() {
        testLogFile = new File(TEST_LOG_PATH);
        if (testLogFile.exists()) {
            testLogFile.delete();
        }
    }

    @AfterEach
    void tearDown() {
        if (testLogFile != null && testLogFile.exists()) {
            testLogFile.delete();
        }
    }

    @Test
    void testWriteCommandsAreLoggedAndReplayed() throws IOException {
        Store store1 = new Store(100);
        PersistenceLog log1 = new PersistenceLog(TEST_LOG_PATH, PersistenceLog.FsyncPolicy.ALWAYS);
        CommandParser parser1 = new CommandParser(store1, log1);

        parser1.parseAndExecute("SET user Alice");
        parser1.parseAndExecute("SET role Admin");
        parser1.parseAndExecute("INCR counter");
        parser1.parseAndExecute("INCR counter");
        parser1.parseAndExecute("DECR counter");
        parser1.parseAndExecute("DEL role");
        log1.close();

        // Replay into a fresh Store
        Store store2 = new Store(100);
        PersistenceLog log2 = new PersistenceLog(TEST_LOG_PATH, PersistenceLog.FsyncPolicy.ALWAYS);
        int replayed = log2.replay(store2);
        log2.close();

        assertEquals(6, replayed);
        assertEquals("Alice", store2.get("user"));
        assertNull(store2.get("role"));
        assertEquals("1", store2.get("counter"));
    }

    @Test
    void testExpiredKeySkippedOnReplay() throws IOException, InterruptedException {
        Store store1 = new Store(100);
        PersistenceLog log1 = new PersistenceLog(TEST_LOG_PATH, PersistenceLog.FsyncPolicy.ALWAYS);
        CommandParser parser1 = new CommandParser(store1, log1);

        parser1.parseAndExecute("SET otp 1234 EX 1");
        parser1.parseAndExecute("SET permanent data");
        log1.close();

        // Wait for otp to expire
        Thread.sleep(1100);

        Store store2 = new Store(100);
        PersistenceLog log2 = new PersistenceLog(TEST_LOG_PATH, PersistenceLog.FsyncPolicy.ALWAYS);
        log2.replay(store2);
        log2.close();

        assertNull(store2.get("otp"), "Expired key should be skipped during AOF replay");
        assertEquals("data", store2.get("permanent"));
    }

    @Test
    void testCompactionRewritesOnlyActiveKeys() throws IOException {
        Store store = new Store(100);
        PersistenceLog log = new PersistenceLog(TEST_LOG_PATH, PersistenceLog.FsyncPolicy.ALWAYS);
        CommandParser parser = new CommandParser(store, log);

        // Perform 50 INCRs (50 log lines for 1 key)
        for (int i = 0; i < 50; i++) {
            parser.parseAndExecute("INCR count");
        }
        int linesBefore = Files.readAllLines(testLogFile.toPath()).size();
        assertTrue(linesBefore >= 50);

        // Compact log
        assertEquals("OK", parser.parseAndExecute("REWRITELOG"));
        int linesAfter = Files.readAllLines(testLogFile.toPath()).size();
        assertEquals(1, linesAfter, "Compacted log should only have 1 line for 'count'");

        log.close();

        // Replay compacted log into fresh store
        Store freshStore = new Store(100);
        PersistenceLog freshLog = new PersistenceLog(TEST_LOG_PATH, PersistenceLog.FsyncPolicy.ALWAYS);
        freshLog.replay(freshStore);
        freshLog.close();

        assertEquals("50", freshStore.get("count"));
    }

    @Test
    void testConcurrent100Threads1000Incr() throws Exception {
        int threads = 100;
        int incrsPerThread = 1000;
        int expectedTotal = threads * incrsPerThread; // 100,000

        Store store1 = new Store(1000);
        PersistenceLog log1 = new PersistenceLog(TEST_LOG_PATH, PersistenceLog.FsyncPolicy.EVERYSEC);
        CommandParser parser1 = new CommandParser(store1, log1);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < incrsPerThread; i++) {
                        parser1.parseAndExecute("INCR shared_counter");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(completed);

        assertEquals(String.valueOf(expectedTotal), store1.get("shared_counter"));
        log1.close();

        // Verify that replaying the log restores exactly 100,000!
        Store store2 = new Store(1000);
        PersistenceLog log2 = new PersistenceLog(TEST_LOG_PATH, PersistenceLog.FsyncPolicy.OFF);
        log2.replay(store2);
        log2.close();

        assertEquals(String.valueOf(expectedTotal), store2.get("shared_counter"));
    }
}
