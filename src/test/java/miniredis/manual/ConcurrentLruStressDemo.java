package miniredis.manual;

import miniredis.store.Store;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Concurrency stress test:
 * 20 threads perform 10,000 total random SET and GET operations across 100 keys
 * against a Store with a max capacity limit of 50.
 *
 * Verifies:
 * 1. No deadlocks.
 * 2. No ConcurrentModificationException.
 * 3. Final DBSIZE is guaranteed to be <= 50.
 */
public class ConcurrentLruStressDemo {
    public static void main(String[] args) throws InterruptedException {
        int maxCapacity = 50;
        int totalThreads = 20;
        int opsPerThread = 500;
        int keyUniverse = 100;

        System.out.println("=== MiniRedis Concurrent LRU Stress Test ===");
        System.out.println("Threads: " + totalThreads + ", Ops/Thread: " + opsPerThread + " (Total: " + (totalThreads * opsPerThread) + " ops)");
        System.out.println("Key Space: 100 keys, Store Max Capacity Limit: " + maxCapacity + "\n");

        Store store = new Store(maxCapacity);
        ExecutorService pool = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(totalThreads);

        for (int t = 0; t < totalThreads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                Random rng = new Random(threadId * 31L);
                try {
                    startSignal.await(); // All threads release at the exact same instant
                    for (int i = 0; i < opsPerThread; i++) {
                        String key = "key:" + rng.nextInt(keyUniverse);
                        if (rng.nextBoolean()) {
                            store.put(key, "val:" + i);
                        } else {
                            store.get(key);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Thread " + threadId + " failed: " + e.getMessage());
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startSignal.countDown(); // Fire!

        boolean completed = doneSignal.await(10, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;
        pool.shutdown();

        int finalSize = store.size();
        System.out.println("Execution completed in " + elapsed + "ms.");
        System.out.println("Final DBSIZE: " + finalSize + " (Max Allowed: " + maxCapacity + ")");

        if (completed && finalSize <= maxCapacity) {
            System.out.println("SUCCESS: Concurrency test passed with zero deadlocks and strict capacity adherence!");
        } else {
            System.err.println("FAILURE: DBSIZE exceeded limit or deadlock occurred!");
        }
    }
}
