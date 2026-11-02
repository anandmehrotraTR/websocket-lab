package com.websocketlab.loadtest;

import com.websocketlab.loadtest.runner.MessageSpikeTest;
import com.websocketlab.loadtest.runner.ProgressiveLoadTest;
import com.websocketlab.loadtest.runner.ThunderingHerdTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI for running WebSocket load tests
 */
@Command(name = "load-test", mixinStandardHelpOptions = true, version = "1.0",
    description = "WebSocket Load Testing Tool")
public class LoadTestCLI implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(LoadTestCLI.class);

    @Option(names = {"-s", "--server-url"}, description = "WebSocket server URL",
            defaultValue = "ws://localhost:8080/ws")
    private String serverUrl;

    @Option(names = {"-t", "--test-type"}, description = "Test type: progressive, thundering-herd, reconnect, message-spike, all",
            defaultValue = "all")
    private String testType;

    @Option(names = {"-c", "--clients"}, description = "Number of clients for thundering herd tests",
            defaultValue = "10000")
    private int clientCount;

    @Option(names = {"-m", "--messages"}, description = "Messages per client",
            defaultValue = "10")
    private int messagesPerClient;

    @Option(names = {"--enable-backoff"}, description = "Enable client-side exponential backoff",
            defaultValue = "false")
    private boolean enableBackoff;

    @Option(names = {"--max-retries"}, description = "Max connection retry attempts with backoff",
            defaultValue = "5")
    private int maxRetries;

    @Option(names = {"--base-backoff-ms"}, description = "Base backoff time in milliseconds",
            defaultValue = "100")
    private long baseBackoffMs;

    @Option(names = {"-o", "--output-dir"}, description = "Output directory for results",
            defaultValue = "./load-test-results")
    private String outputDir;

    @Override
    public Integer call() throws Exception {
        logger.info("Starting WebSocket Load Tests");
        logger.info("Server URL: {}", serverUrl);
        logger.info("Test Type: {}", testType);
        logger.info("Backoff Enabled: {}", enableBackoff);

        // Create output directory
        Files.createDirectories(Paths.get(outputDir));

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        try {
            switch (testType.toLowerCase()) {
                case "progressive":
                    runProgressiveTest(timestamp);
                    break;
                case "thundering-herd":
                    runThunderingHerdTest(timestamp);
                    break;
                case "reconnect":
                    runReconnectTest(timestamp);
                    break;
                case "message-spike":
                    runMessageSpikeTest(timestamp);
                    break;
                case "all":
                    runAllTests(timestamp);
                    break;
                default:
                    logger.error("Unknown test type: {}", testType);
                    return 1;
            }

            logger.info("Load tests completed successfully");
            logger.info("Results saved to: {}", outputDir);
            return 0;

        } catch (Exception e) {
            logger.error("Load test failed: {}", e.getMessage(), e);
            return 1;
        }
    }

    private void runProgressiveTest(String timestamp) throws Exception {
        logger.info("=== PROGRESSIVE LOAD TEST ===");
        logger.info("Testing connection limits by progressively increasing client count");

        ProgressiveLoadTest test = new ProgressiveLoadTest(
            serverUrl, maxRetries, baseBackoffMs, enableBackoff
        );

        int[] clientCounts = {10, 100, 1_000, 10_000, 100_000, 1_000_000};
        List<ProgressiveLoadTest.ProgressiveTestResult> results = test.runProgressiveTest(clientCounts, messagesPerClient);

        // Save results
        String filename = String.format("%s/progressive_test_%s.csv", outputDir, timestamp);
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println(ProgressiveLoadTest.ProgressiveTestResult.csvHeader());
            for (ProgressiveLoadTest.ProgressiveTestResult result : results) {
                writer.println(result.toCsv());
            }
        }

        logger.info("Progressive test results saved to: {}", filename);
    }

    private void runThunderingHerdTest(String timestamp) throws Exception {
        logger.info("=== THUNDERING HERD TEST (CONNECT) ===");
        logger.info("Connecting {} clients simultaneously", clientCount);

        ThunderingHerdTest test = new ThunderingHerdTest(
            serverUrl, maxRetries, baseBackoffMs, enableBackoff
        );

        ThunderingHerdTest.ThunderingHerdResult result = test.runThunderingHerdConnect(clientCount);

        if (result != null) {
            System.out.println(result.getTestResults().formatReport());

            // Save results
            String filename = String.format("%s/thundering_herd_connect_%s.csv", outputDir, timestamp);
            try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                writer.println(ThunderingHerdTest.ThunderingHerdResult.csvHeader());
                writer.println(result.toCsv());
            }

            logger.info("Thundering herd test results saved to: {}", filename);

            // Cleanup
            logger.info("Cleaning up {} clients", result.getClients().size());
            for (var client : result.getClients()) {
                try {
                    client.close();
                } catch (Exception e) {
                    logger.warn("Failed to close client: {}", e.getMessage());
                }
            }
        }
    }

    private void runReconnectTest(String timestamp) throws Exception {
        logger.info("=== THUNDERING HERD TEST (RECONNECT) ===");
        logger.info("First connecting {} clients, then reconnecting all simultaneously", clientCount);

        ThunderingHerdTest test = new ThunderingHerdTest(
            serverUrl, maxRetries, baseBackoffMs, enableBackoff
        );

        // First connect
        logger.info("Phase 1: Initial connection");
        ThunderingHerdTest.ThunderingHerdResult connectResult = test.runThunderingHerdConnect(clientCount);

        if (connectResult != null && !connectResult.getClients().isEmpty()) {
            System.out.println(connectResult.getTestResults().formatReport());

            // Then reconnect
            logger.info("Phase 2: Reconnection after disconnect");
            ThunderingHerdTest.ThunderingHerdResult reconnectResult =
                test.runThunderingHerdReconnect(connectResult.getClients());

            if (reconnectResult != null) {
                System.out.println(reconnectResult.getTestResults().formatReport());

                // Save results
                String filename = String.format("%s/thundering_herd_reconnect_%s.csv", outputDir, timestamp);
                try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                    writer.println(ThunderingHerdTest.ThunderingHerdResult.csvHeader());
                    writer.println(connectResult.toCsv());
                    writer.println(reconnectResult.toCsv());
                }

                logger.info("Reconnect test results saved to: {}", filename);

                // Cleanup
                for (var client : reconnectResult.getClients()) {
                    try {
                        client.close();
                    } catch (Exception e) {
                        logger.warn("Failed to close client: {}", e.getMessage());
                    }
                }
            }
        }
    }

    private void runMessageSpikeTest(String timestamp) throws Exception {
        logger.info("=== MESSAGE SPIKE TEST ===");
        logger.info("Connecting {} clients and sending {} messages per client simultaneously",
                   clientCount, messagesPerClient);

        // First connect clients
        ThunderingHerdTest connectTest = new ThunderingHerdTest(
            serverUrl, maxRetries, baseBackoffMs, enableBackoff
        );

        ThunderingHerdTest.ThunderingHerdResult connectResult = connectTest.runThunderingHerdConnect(clientCount);

        if (connectResult != null && !connectResult.getClients().isEmpty()) {
            logger.info("Connected {} clients, now sending messages", connectResult.getClients().size());

            MessageSpikeTest spikeTest = new MessageSpikeTest(
                connectResult.getClients(), messagesPerClient
            );

            MessageSpikeTest.MessageSpikeResult spikeResult = spikeTest.runMessageSpike();

            if (spikeResult != null) {
                System.out.println(spikeResult.getTestResults().formatReport());

                // Save results
                String filename = String.format("%s/message_spike_%s.csv", outputDir, timestamp);
                try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                    writer.println(MessageSpikeTest.MessageSpikeResult.csvHeader());
                    writer.println(spikeResult.toCsv());
                }

                logger.info("Message spike test results saved to: {}", filename);
            }

            // Cleanup
            for (var client : connectResult.getClients()) {
                try {
                    client.close();
                } catch (Exception e) {
                    logger.warn("Failed to close client: {}", e.getMessage());
                }
            }
        }
    }

    private void runAllTests(String timestamp) throws Exception {
        logger.info("=== RUNNING ALL LOAD TESTS ===");

        // Test 1: Progressive load test
        try {
            runProgressiveTest(timestamp);
        } catch (Exception e) {
            logger.error("Progressive test failed: {}", e.getMessage(), e);
        }

        Thread.sleep(10_000); // Pause between tests

        // Test 2: Thundering herd on connect
        try {
            runThunderingHerdTest(timestamp);
        } catch (Exception e) {
            logger.error("Thundering herd test failed: {}", e.getMessage(), e);
        }

        Thread.sleep(10_000);

        // Test 3: Thundering herd on reconnect
        try {
            runReconnectTest(timestamp);
        } catch (Exception e) {
            logger.error("Reconnect test failed: {}", e.getMessage(), e);
        }

        Thread.sleep(10_000);

        // Test 4: Message spike
        try {
            runMessageSpikeTest(timestamp);
        } catch (Exception e) {
            logger.error("Message spike test failed: {}", e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new LoadTestCLI()).execute(args);
        System.exit(exitCode);
    }
}
