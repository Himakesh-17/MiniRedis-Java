package miniredis.manual;

import miniredis.command.CommandParser;
import miniredis.store.Store;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Demonstrates 100 threads concurrently executing INCR on the same key "counter".
 * Verifies that the final value is exactly 100, proving atomicity.
 */
public class ConcurrentIncrDemo {
    public static void main(String[] args) throws InterruptedException {
        Store store = new Store();
        CommandParser parser = new CommandParser(store);

        int totalThreads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(totalThreads);

        System.out.println("Starting 100 threads ready to INCR 'counter'...");

        for (int i = 0; i < totalThreads; i++) {
            pool.submit(() -> {
                try {
                    startSignal.await(); // Wait for simultaneous release
                    parser.parseAndExecute("INCR counter");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        long startTime = System.currentTimeMillis();
        // Release all 100 threads simultaneously!
        startSignal.countDown();

        doneSignal.await(5, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;
        pool.shutdown();

        String finalVal = parser.parseAndExecute("GET counter");
        System.out.println("All 100 threads completed in " + elapsed + "ms.");
        System.out.println("Final value of 'counter': " + finalVal);

        if ("100".equals(finalVal)) {
            System.out.println("SUCCESS: Exactly 100! Race condition was completely prevented!");
        } else {
            System.err.println("FAILURE: Value is " + finalVal + " (Lost updates occurred!)");
        }
    }
}
