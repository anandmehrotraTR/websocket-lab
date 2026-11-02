package com.websocketlab.loadtest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.websocketlab.loadtest.metrics.MetricsCollector;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Single WebSocket client for load testing with backoff support
 */
public class LoadTestWebSocketClient extends WebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(LoadTestWebSocketClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String clientId;
    private final MetricsCollector metrics;
    private final AtomicLong connectAttempts;
    private volatile long connectionStartTime;
    private volatile boolean connected;
    private CountDownLatch connectedLatch;
    private final int maxRetries;
    private final long baseBackoffMs;

    public LoadTestWebSocketClient(URI serverUri, String clientId, MetricsCollector metrics,
                                   int maxRetries, long baseBackoffMs) {
        super(serverUri);
        this.clientId = clientId;
        this.metrics = metrics;
        this.connectAttempts = new AtomicLong(0);
        this.connected = false;
        this.maxRetries = maxRetries;
        this.baseBackoffMs = baseBackoffMs;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        long latency = System.currentTimeMillis() - connectionStartTime;
        metrics.recordConnectionSuccess(latency);
        connected = true;
        if (connectedLatch != null) {
            connectedLatch.countDown();
        }
        logger.debug("Client {} connected in {}ms", clientId, latency);
    }

    @Override
    public void onMessage(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, Map.class);
            metrics.recordMessageReceived(0);
        } catch (Exception e) {
            logger.warn("Failed to parse message: {}", e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        connected = false;
        logger.debug("Client {} disconnected: {} - {}", clientId, code, reason);
    }

    @Override
    public void onError(Exception ex) {
        connected = false;
        metrics.recordConnectionFailure();
        logger.debug("Client {} error: {}", clientId, ex.getMessage());
    }

    @Override
    public void onMessage(ByteBuffer message) {
        // Not used in this implementation
    }

    public boolean connectWithBackoff() throws InterruptedException {
        long attempt = 0;
        while (attempt < maxRetries) {
            try {
                connectedLatch = new CountDownLatch(1);
                connectionStartTime = System.currentTimeMillis();
                connectAttempts.incrementAndGet();

                this.connect();

                // Wait for connection with timeout
                boolean connected = connectedLatch.await(30, java.util.concurrent.TimeUnit.SECONDS);
                if (connected && this.connected) {
                    return true;
                }

                // Connection failed, apply backoff
                if (attempt < maxRetries - 1) {
                    long backoffMs = calculateBackoff(attempt);
                    logger.debug("Client {} backoff for {}ms (attempt {}/{})", clientId, backoffMs, attempt + 1, maxRetries);
                    Thread.sleep(backoffMs);
                }

                attempt++;
            } catch (Exception e) {
                metrics.recordConnectionFailure();
                logger.warn("Client {} connection attempt {} failed: {}", clientId, attempt + 1, e.getMessage());

                if (attempt < maxRetries - 1) {
                    long backoffMs = calculateBackoff(attempt);
                    Thread.sleep(backoffMs);
                }
                attempt++;
            }
        }

        metrics.recordConnectionFailure();
        return false;
    }

    /**
     * Calculate exponential backoff with jitter
     * Formula: min(baseBackoff * 2^attempt, maxBackoff) + random jitter
     */
    private long calculateBackoff(long attempt) {
        long exponentialBackoff = baseBackoffMs * (1L << attempt); // 2^attempt
        long maxBackoff = 60_000; // 60 seconds max
        long backoff = Math.min(exponentialBackoff, maxBackoff);

        // Add jitter: ±25% of calculated backoff
        long jitter = (long) (backoff * 0.25 * (Math.random() * 2 - 1));
        return Math.max(100, backoff + jitter); // Minimum 100ms
    }

    public void sendMessage(String message) {
        if (connected && this.isOpen()) {
            try {
                this.send(message);
                metrics.recordMessageSent();
            } catch (Exception e) {
                logger.warn("Failed to send message from client {}: {}", clientId, e.getMessage());
            }
        }
    }

    public String getClientId() {
        return clientId;
    }

    public boolean isConnected() {
        return connected && this.isOpen();
    }

    public long getConnectionAttempts() {
        return connectAttempts.get();
    }
}
