package com.websocketlab.loadtest.runner;

import com.websocketlab.loadtest.client.LoadTestWebSocketClient;
import com.websocketlab.loadtest.metrics.MetricsCollector;
import com.websocketlab.loadtest.metrics.TestResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Message spike test - sends messages simultaneously from all connected clients
 * Simulates external events causing high write volume
 */
public class MessageSpikeTest {
    private static final Logger logger = LoggerFactory.getLogger(MessageSpikeTest.class);

    private final List<LoadTestWebSocketClient> clients;
    private final int messagesPerClient;

    public MessageSpikeTest(List<LoadTestWebSocketClient> clients, int messagesPerClient) {
        this.clients = clients;
        this.messagesPerClient = messagesPerClient;
    }

    /**
     * Send messages from all clients simultaneously
     */
    public MessageSpikeResult runMessageSpike() {
        int clientCount = clients.size();
        int totalExpectedMessages = clientCount * messagesPerClient;

        logger.info("Starting message spike: {} clients sending {} messages each (total: {})",
            clientCount, messagesPerClient, totalExpectedMessages);

        MetricsCollector metrics = new MetricsCollector();
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(clientCount, 1000));
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(clientCount);

        long testStartTime = System.currentTimeMillis();

        // Create tasks for each client
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (LoadTestWebSocketClient client : clients) {
            tasks.add(() -> {
                try {
                    // Wait for start signal to send all messages at once
                    startLatch.await();

                    int sent = 0;
                    for (int i = 0; i < messagesPerClient; i++) {
                        String payload = String.format(
                            "{\"type\":\"spike\",\"clientId\":\"%s\",\"seq\":%d,\"timestamp\":%d}",
                            client.getClientId(), i, System.currentTimeMillis()
                        );
                        client.sendMessage(payload);
                        metrics.recordMessageSent();
                        sent++;
                    }
                    return sent;
                } catch (Exception e) {
                    logger.warn("Client {} failed to send messages: {}", client.getClientId(), e.getMessage());
                    return 0;
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        try {
            // Submit all tasks
            executor.invokeAll(tasks);

            // Release the message spike!
            long spikeStartTime = System.currentTimeMillis();
            startLatch.countDown();

            // Wait for all messages to be sent
            boolean completed = completionLatch.await(300, TimeUnit.SECONDS);
            long sendDuration = System.currentTimeMillis() - spikeStartTime;

            if (!completed) {
                logger.warn("Message spike send did not complete within timeout");
            }

            logger.info("All messages sent in {} ms ({:.2f} msg/sec)",
                sendDuration, (double) totalExpectedMessages / (sendDuration / 1000.0));

            // Wait for message delivery/echo with timeout
            long waitStartTime = System.currentTimeMillis();
            long maxWaitMs = 60_000; // Wait up to 60 seconds for delivery

            logger.info("Waiting for message deliveries...");
            Thread.sleep(5000); // Give time for messages to be processed

            long totalDuration = System.currentTimeMillis() - testStartTime;
            TestResults results = metrics.getResults();

            return new MessageSpikeResult(
                clientCount, messagesPerClient, totalExpectedMessages,
                results, totalDuration, sendDuration
            );

        } catch (Exception e) {
            logger.error("Message spike test failed: {}", e.getMessage(), e);
            return null;
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Result from message spike test
     */
    public static class MessageSpikeResult {
        private final int clientCount;
        private final int messagesPerClient;
        private final int totalExpectedMessages;
        private final TestResults testResults;
        private final long totalDurationMs;
        private final long sendDurationMs;

        public MessageSpikeResult(int clientCount, int messagesPerClient, int totalExpectedMessages,
                                 TestResults testResults, long totalDurationMs, long sendDurationMs) {
            this.clientCount = clientCount;
            this.messagesPerClient = messagesPerClient;
            this.totalExpectedMessages = totalExpectedMessages;
            this.testResults = testResults;
            this.totalDurationMs = totalDurationMs;
            this.sendDurationMs = sendDurationMs;
        }

        public int getClientCount() {
            return clientCount;
        }

        public int getMessagesPerClient() {
            return messagesPerClient;
        }

        public int getTotalExpectedMessages() {
            return totalExpectedMessages;
        }

        public TestResults getTestResults() {
            return testResults;
        }

        public long getTotalDurationMs() {
            return totalDurationMs;
        }

        public long getSendDurationMs() {
            return sendDurationMs;
        }

        public double getSendThroughputMsgPerSec() {
            return sendDurationMs == 0 ? 0.0 : (double) totalExpectedMessages / (sendDurationMs / 1000.0);
        }

        public String toCsv() {
            return String.format("%d,%d,%d,%d,%d,%.2f,%s",
                clientCount, messagesPerClient, totalExpectedMessages,
                totalDurationMs, sendDurationMs, getSendThroughputMsgPerSec(),
                testResults.toCsv());
        }

        public static String csvHeader() {
            return "client_count,messages_per_client,total_expected_messages," +
                   "total_duration_ms,send_duration_ms,send_throughput_msg_per_sec," +
                   TestResults.csvHeader();
        }
    }
}
