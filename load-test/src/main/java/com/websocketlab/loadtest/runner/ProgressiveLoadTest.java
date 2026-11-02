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
 * Progressive load test - gradually increases client count to find max connections
 */
public class ProgressiveLoadTest {
    private static final Logger logger = LoggerFactory.getLogger(ProgressiveLoadTest.class);

    private final String serverUrl;
    private final int maxRetries;
    private final long baseBackoffMs;
    private final boolean enableBackoff;

    public ProgressiveLoadTest(String serverUrl, int maxRetries, long baseBackoffMs, boolean enableBackoff) {
        this.serverUrl = serverUrl;
        this.maxRetries = maxRetries;
        this.baseBackoffMs = baseBackoffMs;
        this.enableBackoff = enableBackoff;
    }

    /**
     * Run progressive load test with increasing client counts
     */
    public List<ProgressiveTestResult> runProgressiveTest(int[] clientCounts, int messagesPerClient) {
        List<ProgressiveTestResult> results = new ArrayList<>();

        for (int clientCount : clientCounts) {
            logger.info("Starting test with {} clients", clientCount);
            ProgressiveTestResult result = runSingleLevel(clientCount, messagesPerClient);
            results.add(result);

            logger.info("Completed test with {} clients - Success rate: {:.2f}%",
                clientCount, result.getTestResults().getConnectionSuccessRate());

            // Print intermediate results
            System.out.println(result.getTestResults().formatReport());

            // If success rate drops below 95%, consider this the failure point
            if (result.getTestResults().getConnectionSuccessRate() < 95.0) {
                logger.warn("Connection success rate dropped below 95% at {} clients. Stopping progressive test.",
                    clientCount);
                break;
            }

            // Cleanup between levels
            try {
                Thread.sleep(5000); // 5 second pause between levels
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return results;
    }

    private ProgressiveTestResult runSingleLevel(int clientCount, int messagesPerClient) {
        MetricsCollector metrics = new MetricsCollector();
        List<LoadTestWebSocketClient> clients = new ArrayList<>(clientCount);
        ExecutorService connectionPool = Executors.newFixedThreadPool(Math.min(clientCount, 500));

        long testStartTime = System.currentTimeMillis();

        try {
            // Phase 1: Connect all clients
            logger.info("Phase 1: Connecting {} clients", clientCount);
            List<Future<Boolean>> connectionFutures = new ArrayList<>();

            for (int i = 0; i < clientCount; i++) {
                final int clientIndex = i;
                Future<Boolean> future = connectionPool.submit(() -> {
                    try {
                        URI serverUri = new URI(serverUrl);
                        String clientId = "load-test-client-" + clientIndex;
                        LoadTestWebSocketClient client = new LoadTestWebSocketClient(
                            serverUri, clientId, metrics,
                            enableBackoff ? maxRetries : 1,
                            enableBackoff ? baseBackoffMs : 0
                        );

                        boolean connected = client.connectWithBackoff();
                        if (connected) {
                            synchronized (clients) {
                                clients.add(client);
                            }
                            return true;
                        }
                        return false;
                    } catch (Exception e) {
                        logger.error("Failed to create client {}: {}", clientIndex, e.getMessage());
                        metrics.recordConnectionFailure();
                        return false;
                    }
                });
                connectionFutures.add(future);
            }

            // Wait for all connections to complete
            int successCount = 0;
            for (Future<Boolean> future : connectionFutures) {
                try {
                    if (future.get(60, TimeUnit.SECONDS)) {
                        successCount++;
                    }
                } catch (Exception e) {
                    logger.warn("Connection future failed: {}", e.getMessage());
                }
            }

            logger.info("Connected {}/{} clients successfully", successCount, clientCount);

            // Measure memory after connections
            Runtime runtime = Runtime.getRuntime();
            runtime.gc();
            long memoryUsed = runtime.totalMemory() - runtime.freeMemory();
            metrics.updateMemoryUsage(memoryUsed);

            // Phase 2: Send messages from all clients
            if (messagesPerClient > 0 && !clients.isEmpty()) {
                logger.info("Phase 2: Sending {} messages per client from {} clients",
                    messagesPerClient, clients.size());

                CountDownLatch messageLatch = new CountDownLatch(clients.size() * messagesPerClient);
                ExecutorService messagePool = Executors.newFixedThreadPool(Math.min(clients.size(), 500));

                for (LoadTestWebSocketClient client : clients) {
                    messagePool.submit(() -> {
                        for (int i = 0; i < messagesPerClient; i++) {
                            try {
                                String message = String.format(
                                    "{\"type\":\"test\",\"clientId\":\"%s\",\"seq\":%d,\"timestamp\":%d}",
                                    client.getClientId(), i, System.currentTimeMillis()
                                );
                                client.sendMessage(message);
                                messageLatch.countDown();
                            } catch (Exception e) {
                                logger.warn("Failed to send message: {}", e.getMessage());
                                messageLatch.countDown();
                            }
                        }
                    });
                }

                // Wait for all messages to be sent
                try {
                    messageLatch.await(120, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                messagePool.shutdown();
                messagePool.awaitTermination(30, TimeUnit.SECONDS);
            }

            // Wait a bit for message delivery
            Thread.sleep(2000);

        } catch (Exception e) {
            logger.error("Test failed: {}", e.getMessage(), e);
        } finally {
            // Cleanup
            logger.info("Cleaning up {} clients", clients.size());
            for (LoadTestWebSocketClient client : clients) {
                try {
                    client.close();
                } catch (Exception e) {
                    logger.warn("Failed to close client: {}", e.getMessage());
                }
            }

            connectionPool.shutdown();
            try {
                connectionPool.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        long testDuration = System.currentTimeMillis() - testStartTime;
        TestResults results = metrics.getResults();

        return new ProgressiveTestResult(clientCount, messagesPerClient, results, testDuration);
    }

    /**
     * Result for a single level of progressive testing
     */
    public static class ProgressiveTestResult {
        private final int targetClients;
        private final int messagesPerClient;
        private final TestResults testResults;
        private final long totalDurationMs;

        public ProgressiveTestResult(int targetClients, int messagesPerClient,
                                    TestResults testResults, long totalDurationMs) {
            this.targetClients = targetClients;
            this.messagesPerClient = messagesPerClient;
            this.testResults = testResults;
            this.totalDurationMs = totalDurationMs;
        }

        public int getTargetClients() {
            return targetClients;
        }

        public int getMessagesPerClient() {
            return messagesPerClient;
        }

        public TestResults getTestResults() {
            return testResults;
        }

        public long getTotalDurationMs() {
            return totalDurationMs;
        }

        public String toCsv() {
            return String.format("%d,%d,%d,%s",
                targetClients, messagesPerClient, totalDurationMs, testResults.toCsv());
        }

        public static String csvHeader() {
            return "target_clients,messages_per_client,total_duration_ms," + TestResults.csvHeader();
        }
    }
}
