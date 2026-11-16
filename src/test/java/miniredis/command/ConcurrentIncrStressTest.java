package miniredis.command;

import miniredis.store.Store;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConcurrentIncrStressTest {

    @Test
    void test100ConcurrentIncrements() throws InterruptedException {
        Store store = new Store();
        CommandParser parser = new CommandParser(store);

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // All 100 threads wait here until released simultaneously
                    parser.parseAndExecute("INCR hits");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Fire all 100 threads at the exact same instant!
        startLatch.countDown();

        // Wait for all 100 threads to finish
        boolean finished = finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(true, finished, "All 100 threads should complete within 5 seconds");
        // Final value MUST be exactly 100 with zero race condition / lost updates!
        assertEquals("100", parser.parseAndExecute("GET hits"));
    }
}
