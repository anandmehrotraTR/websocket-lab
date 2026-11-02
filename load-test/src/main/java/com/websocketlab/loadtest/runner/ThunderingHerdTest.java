package com.websocketlab.loadtest.runner;

import com.websocketlab.loadtest.client.LoadTestWebSocketClient;
import com.websocketlab.loadtest.metrics.MetricsCollector;
import com.websocketlab.loadtest.metrics.TestResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Thundering herd test - connects many clients simultaneously
 */
public class ThunderingHerdTest {
    private static final Logger logger = LoggerFactory.getLogger(ThunderingHerdTest.class);

    private final String serverUrl;
    private final int maxRetries;
    private final long baseBackoffMs;
    private final boolean enableBackoff;

    public ThunderingHerdTest(String serverUrl, int maxRetries, long baseBackoffMs, boolean enableBackoff) {
        this.serverUrl = serverUrl;
        this.maxRetries = maxRetries;
        this.baseBackoffMs = baseBackoffMs;
        this.enableBackoff = enableBackoff;
    }

    /**
     * Connect all clients at the same time (thundering herd)
     */
    public ThunderingHerdResult runThunderingHerdConnect(int clientCount) {
        MetricsCollector metrics = new MetricsCollector();
        List<LoadTestWebSocketClient> clients = new CopyOnWriteArrayList<>();

        // Use a large thread pool to truly connect simultaneously
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(clientCount, 2000));
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(clientCount);

        long memoryBefore = getMemoryUsage();
        logger.info("Memory before connections: {} MB", memoryBefore / (1024 * 1024));

        long testStartTime = System.currentTimeMillis();

        // Create all clients and prepare them
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < clientCount; i++) {
            final int clientIndex = i;
            tasks.add(() -> {
                try {
                    URI serverUri = new URI(serverUrl);
                    String clientId = "herd-client-" + clientIndex;
                    LoadTestWebSocketClient client = new LoadTestWebSocketClient(
                        serverUri, clientId, metrics,
                        enableBackoff ? maxRetries : 1,
                        enableBackoff ? baseBackoffMs : 0
                    );

                    // Wait for the start signal
                    startLatch.await();

                    // All threads start connecting at the same time
                    boolean connected = client.connectWithBackoff();
                    if (connected) {
                        clients.add(client);
                    }
                    return connected;
                } catch (Exception e) {
                    logger.warn("Client {} failed: {}", clientIndex, e.getMessage());
                    metrics.recordConnectionFailure();
                    return false;
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        logger.info("Starting thundering herd with {} clients connecting simultaneously", clientCount);

        try {
            // Submit all tasks
            List<Future<Boolean>> futures = executor.invokeAll(tasks);

            // Release the herd!
            long herdStartTime = System.currentTimeMillis();
            startLatch.countDown();

            // Wait for all to complete (with timeout)
            boolean completed = completionLatch.await(300, TimeUnit.SECONDS);
            long herdDuration = System.currentTimeMillis() - herdStartTime;

            if (!completed) {
                logger.warn("Thundering herd did not complete within timeout");
            }

            logger.info("Thundering herd completed in {} ms", herdDuration);
            logger.info("Successfully connected: {}/{}", clients.size(), clientCount);

            // Measure memory spike
            Runtime.getRuntime().gc();
            Thread.sleep(1000);
            long memoryAfter = getMemoryUsage();
            long memorySpike = memoryAfter - memoryBefore;
            metrics.updateMemoryUsage(memoryAfter);

            logger.info("Memory after connections: {} MB", memoryAfter / (1024 * 1024));
            logger.info("Memory spike: {} MB", memorySpike / (1024 * 1024));

            long totalDuration = System.currentTimeMillis() - testStartTime;
            TestResults results = metrics.getResults();

            return new ThunderingHerdResult(
                "connect", clientCount, clients.size(),
                results, totalDuration, memoryBefore, memoryAfter, memorySpike, clients
            );

        } catch (Exception e) {
            logger.error("Thundering herd test failed: {}", e.getMessage(), e);
            return null;
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Reconnect test - drop all connections and reconnect simultaneously
     */
    public ThunderingHerdResult runThunderingHerdReconnect(List<LoadTestWebSocketClient> existingClients) {
        MetricsCollector metrics = new MetricsCollector();
        List<LoadTestWebSocketClient> reconnectedClients = new CopyOnWriteArrayList<>();

        logger.info("Dropping {} connections", existingClients.size());

        // Drop all connections
        for (LoadTestWebSocketClient client : existingClients) {
            try {
                client.close();
            } catch (Exception e) {
                logger.warn("Failed to close client: {}", e.getMessage());
            }
        }

        // Wait a moment for disconnections to complete
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long memoryBefore = getMemoryUsage();
        logger.info("Starting reconnection thundering herd with {} clients", existingClients.size());

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(existingClients.size(), 2000));
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(existingClients.size());

        long testStartTime = System.currentTimeMillis();

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < existingClients.size(); i++) {
            final int clientIndex = i;
            tasks.add(() -> {
                try {
                    URI serverUri = new URI(serverUrl);
                    String clientId = "reconnect-client-" + clientIndex;
                    LoadTestWebSocketClient client = new LoadTestWebSocketClient(
                        serverUri, clientId, metrics,
                        enableBackoff ? maxRetries : 1,
                        enableBackoff ? baseBackoffMs : 0
                    );

                    // Wait for start signal
                    startLatch.await();

                    // Reconnect
                    boolean connected = client.connectWithBackoff();
                    if (connected) {
                        reconnectedClients.add(client);
                    }
                    return connected;
                } catch (Exception e) {
                    logger.warn("Reconnection failed for client {}: {}", clientIndex, e.getMessage());
                    metrics.recordConnectionFailure();
                    return false;
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        try {
            // Submit all tasks
            executor.invokeAll(tasks);

            // Release the reconnection herd!
            long herdStartTime = System.currentTimeMillis();
            startLatch.countDown();

            // Wait for completion
            boolean completed = completionLatch.await(300, TimeUnit.SECONDS);
            long herdDuration = System.currentTimeMillis() - herdStartTime;

            if (!completed) {
                logger.warn("Reconnection herd did not complete within timeout");
            }

            logger.info("Reconnection herd completed in {} ms", herdDuration);
            logger.info("Successfully reconnected: {}/{}", reconnectedClients.size(), existingClients.size());

            // Measure memory
            Runtime.getRuntime().gc();
            Thread.sleep(1000);
            long memoryAfter = getMemoryUsage();
            long memorySpike = memoryAfter - memoryBefore;
            metrics.updateMemoryUsage(memoryAfter);

            long totalDuration = System.currentTimeMillis() - testStartTime;
            TestResults results = metrics.getResults();

            return new ThunderingHerdResult(
                "reconnect", existingClients.size(), reconnectedClients.size(),
                results, totalDuration, memoryBefore, memoryAfter, memorySpike, reconnectedClients
            );

        } catch (Exception e) {
            logger.error("Reconnection test failed: {}", e.getMessage(), e);
            return null;
        } finally {
            executor.shutdown();
        }
    }

    private long getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * Result from thundering herd test
     */
    public static class ThunderingHerdResult {
        private final String testType;
        private final int targetClients;
        private final int successfulClients;
        private final TestResults testResults;
        private final long totalDurationMs;
        private final long memoryBefore;
        private final long memoryAfter;
        private final long memorySpike;
        private final List<LoadTestWebSocketClient> clients;

        public ThunderingHerdResult(String testType, int targetClients, int successfulClients,
                                   TestResults testResults, long totalDurationMs,
                                   long memoryBefore, long memoryAfter, long memorySpike,
                                   List<LoadTestWebSocketClient> clients) {
            this.testType = testType;
            this.targetClients = targetClients;
            this.successfulClients = successfulClients;
            this.testResults = testResults;
            this.totalDurationMs = totalDurationMs;
            this.memoryBefore = memoryBefore;
            this.memoryAfter = memoryAfter;
            this.memorySpike = memorySpike;
            this.clients = clients;
        }

        public String getTestType() {
            return testType;
        }

        public int getTargetClients() {
            return targetClients;
        }

        public int getSuccessfulClients() {
            return successfulClients;
        }

        public TestResults getTestResults() {
            return testResults;
        }

        public long getTotalDurationMs() {
            return totalDurationMs;
        }

        public long getMemoryBefore() {
            return memoryBefore;
        }

        public long getMemoryAfter() {
            return memoryAfter;
        }

        public long getMemorySpike() {
            return memorySpike;
        }

        public List<LoadTestWebSocketClient> getClients() {
            return clients;
        }

        public String toCsv() {
            return String.format("%s,%d,%d,%d,%d,%d,%d,%s",
                testType, targetClients, successfulClients, totalDurationMs,
                memoryBefore, memoryAfter, memorySpike, testResults.toCsv());
        }

        public static String csvHeader() {
            return "test_type,target_clients,successful_clients,total_duration_ms," +
                   "memory_before_bytes,memory_after_bytes,memory_spike_bytes," +
                   TestResults.csvHeader();
        }
    }
}
