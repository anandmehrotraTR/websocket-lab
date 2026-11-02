package com.websocketlab.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter for WebSocket connections using sliding window algorithm
 * Tracks connection attempts and enforces limits per time window
 */
@Component
public class ConnectionRateLimiter {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionRateLimiter.class);

    private final ConcurrentHashMap<Long, AtomicInteger> connectionWindows;
    private final int maxConnectionsPerWindow;
    private final long windowSizeMs;
    private final boolean enabled;

    /**
     * Create a rate limiter with configurable settings
     *
     * @param maxConnectionsPerWindow Maximum connections allowed in the time window
     * @param windowSizeMs Time window size in milliseconds
     * @param enabled Whether rate limiting is enabled
     */
    public ConnectionRateLimiter(int maxConnectionsPerWindow, long windowSizeMs, boolean enabled) {
        this.connectionWindows = new ConcurrentHashMap<>();
        this.maxConnectionsPerWindow = maxConnectionsPerWindow;
        this.windowSizeMs = windowSizeMs;
        this.enabled = enabled;

        if (enabled) {
            logger.info("Connection rate limiter enabled: max {} connections per {} ms",
                maxConnectionsPerWindow, windowSizeMs);

            // Start cleanup thread to remove old windows
            startCleanupThread();
        } else {
            logger.info("Connection rate limiter disabled");
        }
    }

    /**
     * Default constructor with sensible defaults: 10,000 connections per second
     */
    public ConnectionRateLimiter() {
        this(10_000, 1_000, false);
    }

    /**
     * Check if a new connection should be allowed
     *
     * @return true if connection is allowed, false if rate limit exceeded
     */
    public boolean allowConnection() {
        if (!enabled) {
            return true;
        }

        long currentWindow = getCurrentWindow();
        AtomicInteger counter = connectionWindows.computeIfAbsent(currentWindow, k -> new AtomicInteger(0));

        int currentCount = counter.incrementAndGet();

        if (currentCount > maxConnectionsPerWindow) {
            logger.warn("Rate limit exceeded: {} connections in current window (max: {})",
                currentCount, maxConnectionsPerWindow);
            // Decrement since we're rejecting this connection
            counter.decrementAndGet();
            return false;
        }

        return true;
    }

    /**
     * Get current time window bucket
     */
    private long getCurrentWindow() {
        return System.currentTimeMillis() / windowSizeMs;
    }

    /**
     * Get current connection count in the active window
     */
    public int getCurrentConnectionCount() {
        if (!enabled) {
            return 0;
        }

        long currentWindow = getCurrentWindow();
        AtomicInteger counter = connectionWindows.get(currentWindow);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Get rate limiter statistics
     */
    public RateLimiterStats getStats() {
        int activeWindows = connectionWindows.size();
        int currentWindowCount = getCurrentConnectionCount();

        return new RateLimiterStats(
            enabled,
            maxConnectionsPerWindow,
            windowSizeMs,
            activeWindows,
            currentWindowCount
        );
    }

    /**
     * Start background thread to clean up old windows
     */
    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(windowSizeMs * 2); // Clean up every 2 windows
                    cleanupOldWindows();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.setName("rate-limiter-cleanup");
        cleanupThread.start();
    }

    /**
     * Remove windows older than 2 window periods
     */
    private void cleanupOldWindows() {
        long currentWindow = getCurrentWindow();
        long cutoffWindow = currentWindow - 2; // Keep last 2 windows

        connectionWindows.entrySet().removeIf(entry -> entry.getKey() < cutoffWindow);
    }

    /**
     * Rate limiter statistics
     */
    public static class RateLimiterStats {
        private final boolean enabled;
        private final int maxConnectionsPerWindow;
        private final long windowSizeMs;
        private final int activeWindows;
        private final int currentWindowCount;

        public RateLimiterStats(boolean enabled, int maxConnectionsPerWindow, long windowSizeMs,
                               int activeWindows, int currentWindowCount) {
            this.enabled = enabled;
            this.maxConnectionsPerWindow = maxConnectionsPerWindow;
            this.windowSizeMs = windowSizeMs;
            this.activeWindows = activeWindows;
            this.currentWindowCount = currentWindowCount;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getMaxConnectionsPerWindow() {
            return maxConnectionsPerWindow;
        }

        public long getWindowSizeMs() {
            return windowSizeMs;
        }

        public int getActiveWindows() {
            return activeWindows;
        }

        public int getCurrentWindowCount() {
            return currentWindowCount;
        }

        @Override
        public String toString() {
            return String.format("RateLimiter{enabled=%s, max=%d/%dms, activeWindows=%d, currentCount=%d}",
                enabled, maxConnectionsPerWindow, windowSizeMs, activeWindows, currentWindowCount);
        }
    }
}
