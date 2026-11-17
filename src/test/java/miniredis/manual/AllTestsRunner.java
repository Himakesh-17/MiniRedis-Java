package miniredis.manual;

import miniredis.command.CommandParser;
import miniredis.persistence.PersistenceLog;
import miniredis.store.Store;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Standalone test runner that executes and validates the full MiniRedis test suite
 * directly with plain java (no Maven or external jars needed).
 */
public class AllTestsRunner {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   MiniRedis Complete Test Suite Runner (Day 1-6)");
        System.out.println("=================================================\n");

        runTest("Day 2: SET, GET, DEL, EXISTS, KEYS", AllTestsRunner::testBasicCommands);
        runTest("Day 3: Atomic INCR/DECR Race Condition Prevention (100 threads)", AllTestsRunner::testConcurrentIncr);
        runTest("Day 4: TTL Key Expiration & Lazy Cleanup", AllTestsRunner::testTtlExpiration);
        runTest("Day 5: Memory Limit with LRU Eviction", AllTestsRunner::testLruEviction);
        runTest("Day 6: AOF Persistence Replay & Absolute TTL", AllTestsRunner::testAofReplay);
        runTest("Day 6: AOF Log Compaction (REWRITELOG)", AllTestsRunner::testAofCompaction);
        runTest("Day 6: Concurrency 100 threads x 1,000 INCR with AOF Replay", AllTestsRunner::testConcurrent100k);

        System.out.println("\n=================================================");
        System.out.println("   Test Suite Finished: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("=================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void runTest(String name, TestCase testCase) {
        System.out.print("[TEST] " + name + "... ");
        try {
            testCase.run();
            System.out.println("PASSED");
            passed++;
        } catch (Throwable t) {
            System.out.println("FAILED! (" + t.getMessage() + ")");
            t.printStackTrace(System.err);
            failed++;
        }
    }

    @FunctionalInterface
    interface TestCase {
        void run() throws Throwable;
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Expected: <" + expected + "> but was: <" + actual + ">");
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) throw new AssertionError(msg);
    }

    private static void testBasicCommands() {
        Store store = new Store(100);
        CommandParser parser = new CommandParser(store);

        assertEquals("OK", parser.parseAndExecute("SET name Asha"));
        assertEquals("Asha", parser.parseAndExecute("GET name"));
        assertEquals("1", parser.parseAndExecute("EXISTS name"));
        assertEquals("1", parser.parseAndExecute("DEL name"));
        assertEquals("(nil)", parser.parseAndExecute("GET name"));
        assertEquals("0", parser.parseAndExecute("EXISTS name"));
    }

    private static void testConcurrentIncr() throws Exception {
        Store store = new Store(100);
        CommandParser parser = new CommandParser(store);
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    parser.parseAndExecute("INCR hits");
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(5, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals("100", parser.parseAndExecute("GET hits"));
    }

    private static void testTtlExpiration() throws Exception {
        Store store = new Store(100);
        CommandParser parser = new CommandParser(store);
        parser.parseAndExecute("SET otp 4821 EX 1");
        assertEquals("4821", parser.parseAndExecute("GET otp"));
        Thread.sleep(1100);
        assertEquals("(nil)", parser.parseAndExecute("GET otp"));
        assertEquals("-2", parser.parseAndExecute("TTL otp"));
    }

    private static void testLruEviction() {
        Store store = new Store(3);
        CommandParser parser = new CommandParser(store);
        parser.parseAndExecute("SET a 1");
        parser.parseAndExecute("SET b 2");
        parser.parseAndExecute("SET c 3");
        parser.parseAndExecute("GET a"); // 'a' is MRU, 'b' is LRU
        parser.parseAndExecute("SET d 4"); // evicts 'b'
        assertEquals("(nil)", parser.parseAndExecute("GET b"));
        assertEquals("1", parser.parseAndExecute("GET a"));
        assertEquals("3", parser.parseAndExecute("GET c"));
        assertEquals("4", parser.parseAndExecute("GET d"));
        assertEquals("3", parser.parseAndExecute("DBSIZE"));
    }

    private static void testAofReplay() throws Exception {
        File file = new File("target/runner_aof.log");
        if (file.exists()) file.delete();

        Store store1 = new Store(100);
        PersistenceLog aof1 = new PersistenceLog(file.getPath(), PersistenceLog.FsyncPolicy.ALWAYS);
        CommandParser parser1 = new CommandParser(store1, aof1);

        parser1.parseAndExecute("SET key1 Hello");
        parser1.parseAndExecute("SET key2 World EX 60");
        parser1.parseAndExecute("INCR visits");
        aof1.close();

        Store store2 = new Store(100);
        PersistenceLog aof2 = new PersistenceLog(file.getPath(), PersistenceLog.FsyncPolicy.ALWAYS);
        int replayed = aof2.replay(store2);
        aof2.close();

        assertEquals(3, replayed);
        assertEquals("Hello", store2.get("key1"));
        assertEquals("World", store2.get("key2"));
        assertEquals("1", store2.get("visits"));
        file.delete();
    }

    private static void testAofCompaction() throws Exception {
        File file = new File("target/runner_compact.log");
        if (file.exists()) file.delete();

        Store store = new Store(100);
        PersistenceLog aof = new PersistenceLog(file.getPath(), PersistenceLog.FsyncPolicy.ALWAYS);
        CommandParser parser = new CommandParser(store, aof);

        for (int i = 0; i < 25; i++) {
            parser.parseAndExecute("INCR counter");
        }
        int linesBefore = Files.readAllLines(file.toPath()).size();
        assertTrue(linesBefore >= 25, "Log should have >= 25 lines before compaction");

        assertEquals("OK", parser.parseAndExecute("REWRITELOG"));
        int linesAfter = Files.readAllLines(file.toPath()).size();
        assertEquals(1, linesAfter);

        aof.close();
        file.delete();
    }

    private static void testConcurrent100k() throws Exception {
        File file = new File("target/runner_100k.log");
        if (file.exists()) file.delete();

        int threads = 100;
        int opsPerThread = 1000;
        Store store1 = new Store(1000);
        PersistenceLog aof1 = new PersistenceLog(file.getPath(), PersistenceLog.FsyncPolicy.EVERYSEC);
        CommandParser parser1 = new CommandParser(store1, aof1);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < opsPerThread; i++) {
                        parser1.parseAndExecute("INCR total_count");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(20, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals("100000", parser1.parseAndExecute("GET total_count"));
        aof1.close();

        // Replay and assert 100,000!
        Store store2 = new Store(1000);
        PersistenceLog aof2 = new PersistenceLog(file.getPath(), PersistenceLog.FsyncPolicy.OFF);
        aof2.replay(store2);
        aof2.close();
        assertEquals("100000", store2.get("total_count"));
        file.delete();
    }
}
