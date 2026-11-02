package com.websocketlab.loadtest.metrics;

import org.HdrHistogram.Histogram;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects metrics for load testing including latencies, throughput, and error rates
 */
public class MetricsCollector {
    private final Histogram connectLatencyHistogram;
    private final Histogram messageLatencyHistogram;
    private final LongAdder successfulConnections;
    private final LongAdder failedConnections;
    private final LongAdder messagesReceived;
    private final LongAdder messagesSent;
    private final AtomicLong peakMemoryUsage;
    private final long testStartTime;

    public MetricsCollector() {
        // Histogram tracking latencies from 1ms to 60 seconds with 3 significant digits
        this.connectLatencyHistogram = new Histogram(60_000, 3);
        this.messageLatencyHistogram = new Histogram(60_000, 3);
        this.successfulConnections = new LongAdder();
        this.failedConnections = new LongAdder();
        this.messagesReceived = new LongAdder();
        this.messagesSent = new LongAdder();
        this.peakMemoryUsage = new AtomicLong(0);
        this.testStartTime = System.currentTimeMillis();
    }

    public void recordConnectionSuccess(long latencyMs) {
        successfulConnections.increment();
        connectLatencyHistogram.recordValue(latencyMs);
    }

    public void recordConnectionFailure() {
        failedConnections.increment();
    }

    public void recordMessageSent() {
        messagesSent.increment();
    }

    public void recordMessageReceived(long latencyMs) {
        messagesReceived.increment();
        messageLatencyHistogram.recordValue(latencyMs);
    }

    public void updateMemoryUsage(long memoryBytes) {
        peakMemoryUsage.updateAndGet(current -> Math.max(current, memoryBytes));
    }

    public TestResults getResults() {
        long duration = System.currentTimeMillis() - testStartTime;
        return new TestResults(
            successfulConnections.sum(),
            failedConnections.sum(),
            messagesSent.sum(),
            messagesReceived.sum(),
            connectLatencyHistogram,
            messageLatencyHistogram,
            peakMemoryUsage.get(),
            duration
        );
    }

    public void reset() {
        connectLatencyHistogram.reset();
        messageLatencyHistogram.reset();
        successfulConnections.reset();
        failedConnections.reset();
        messagesReceived.reset();
        messagesSent.reset();
        peakMemoryUsage.set(0);
    }
}
