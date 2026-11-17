package miniredis.manual;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-threaded benchmark client for MiniRedis.
 * Measures throughput (requests/sec) and latency percentiles (p50, p95, p99).
 */
public class BenchmarkClient {

    public static class BenchmarkResult {
        public final int concurrency;
        public final int totalRequests;
        public final double totalSeconds;
        public final double reqPerSec;
        public final double avgLatencyMs;
        public final double p50LatencyMs;
        public final double p95LatencyMs;
        public final double p99LatencyMs;
        public final int errors;

        public BenchmarkResult(int concurrency, int totalRequests, double totalSeconds,
                               double reqPerSec, double avgLatencyMs, double p50LatencyMs,
                               double p95LatencyMs, double p99LatencyMs, int errors) {
            this.concurrency = concurrency;
            this.totalRequests = totalRequests;
            this.totalSeconds = totalSeconds;
            this.reqPerSec = reqPerSec;
            this.avgLatencyMs = avgLatencyMs;
            this.p50LatencyMs = p50LatencyMs;
            this.p95LatencyMs = p95LatencyMs;
            this.p99LatencyMs = p99LatencyMs;
            this.errors = errors;
        }
    }

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 6380;

        System.out.println("=============================================================");
        System.out.println("            MiniRedis Performance Benchmark Suite             ");
        System.out.println("=============================================================");
        System.out.println("Target: " + host + ":" + port);
        System.out.println("Workload: 50% SET / 50% GET\n");

        // Warm-up phase: 5 clients x 200 requests to prime JIT compiler and OS sockets
        System.out.print("Warming up JIT compiler & TCP stack... ");
        runBenchmark(host, port, 5, 200, false);
        System.out.println("Done.\n");

        int[] clientScenarios = {1, 10, 50};
        int requestsPerClient = 2000;
        List<BenchmarkResult> results = new ArrayList<>();

        for (int clients : clientScenarios) {
            System.out.println(">>> Running Scenario: " + clients + " concurrent clients (" + (clients * requestsPerClient) + " requests total)...");
            BenchmarkResult res = runBenchmark(host, port, clients, requestsPerClient, true);
            results.add(res);
            Thread.sleep(500); // Brief pause between runs
        }

        printSummaryTable(results);
    }

    public static BenchmarkResult runBenchmark(String host, int port, int numClients, int reqsPerClient, boolean collectStats) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(numClients);
        CountDownLatch readyLatch = new CountDownLatch(numClients);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numClients);

        AtomicInteger errorCount = new AtomicInteger(0);
        long[][] threadLatenciesNano = new long[numClients][reqsPerClient];

        for (int c = 0; c < numClients; c++) {
            final int clientId = c;
            pool.submit(() -> {
                try (Socket socket = new Socket(host, port);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                    readyLatch.countDown();
                    startLatch.await(); // All client threads synchronized to start together

                    for (int i = 0; i < reqsPerClient; i++) {
                        String key = "bench:key:" + (i % 250);
                        String cmd = (i % 2 == 0)
                                ? "SET " + key + " val:" + i
                                : "GET " + key;

                        long t0 = System.nanoTime();
                        out.println(cmd);
                        String reply = in.readLine();
                        long t1 = System.nanoTime();

                        if (reply == null || reply.startsWith("(error)")) {
                            errorCount.incrementAndGet();
                        }

                        if (collectStats) {
                            threadLatenciesNano[clientId][i] = (t1 - t0);
                        }
                    }
                    out.println("QUIT");
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Wait until all client sockets are connected
        readyLatch.await();

        long globalStart = System.nanoTime();
        startLatch.countDown(); // Release!

        finishLatch.await(60, TimeUnit.SECONDS);
        long globalEnd = System.nanoTime();
        pool.shutdown();

        int totalRequests = numClients * reqsPerClient;
        double totalSeconds = (globalEnd - globalStart) / 1_000_000_000.0;
        double reqPerSec = totalRequests / totalSeconds;

        if (!collectStats) {
            return null;
        }

        // Flatten and sort latencies for percentile calculations
        long[] allLatenciesNano = new long[totalRequests];
        int idx = 0;
        long sumNano = 0;
        for (int c = 0; c < numClients; c++) {
            for (int i = 0; i < reqsPerClient; i++) {
                long lat = threadLatenciesNano[c][i];
                allLatenciesNano[idx++] = lat;
                sumNano += lat;
            }
        }
        Arrays.sort(allLatenciesNano);

        double avgLatencyMs = (sumNano / (double) totalRequests) / 1_000_000.0;
        double p50 = allLatenciesNano[(int) (totalRequests * 0.50)] / 1_000_000.0;
        double p95 = allLatenciesNano[(int) (totalRequests * 0.95)] / 1_000_000.0;
        double p99 = allLatenciesNano[(int) (totalRequests * 0.99)] / 1_000_000.0;

        return new BenchmarkResult(numClients, totalRequests, totalSeconds, reqPerSec, avgLatencyMs, p50, p95, p99, errorCount.get());
    }

    private static void printSummaryTable(List<BenchmarkResult> results) {
        System.out.println("\n==========================================================================================");
        System.out.println("                               BENCHMARK RESULTS TABLE                                    ");
        System.out.println("==========================================================================================");
        System.out.printf("%-10s | %-12s | %-14s | %-10s | %-10s | %-10s | %-10s | %-6s%n",
                "Clients", "Total Reqs", "Throughput", "Avg Latency", "p50", "p95", "p99", "Errors");
        System.out.println("------------------------------------------------------------------------------------------");
        for (BenchmarkResult r : results) {
            System.out.printf("%-10d | %-12d | %-10.0f req/s | %-7.2f ms | %-7.2f ms | %-7.2f ms | %-7.2f ms | %-6d%n",
                    r.concurrency, r.totalRequests, r.reqPerSec, r.avgLatencyMs, r.p50LatencyMs, r.p95LatencyMs, r.p99LatencyMs, r.errors);
        }
        System.out.println("==========================================================================================\n");
    }
}
