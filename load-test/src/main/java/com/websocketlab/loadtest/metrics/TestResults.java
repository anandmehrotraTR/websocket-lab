package com.websocketlab.loadtest.metrics;

import org.HdrHistogram.Histogram;
import java.text.DecimalFormat;

/**
 * Immutable snapshot of test results
 */
public class TestResults {
    private final long successfulConnections;
    private final long failedConnections;
    private final long messagesSent;
    private final long messagesReceived;
    private final Histogram connectLatencyHistogram;
    private final Histogram messageLatencyHistogram;
    private final long peakMemoryBytes;
    private final long durationMs;

    public TestResults(long successfulConnections, long failedConnections,
                      long messagesSent, long messagesReceived,
                      Histogram connectLatencyHistogram, Histogram messageLatencyHistogram,
                      long peakMemoryBytes, long durationMs) {
        this.successfulConnections = successfulConnections;
        this.failedConnections = failedConnections;
        this.messagesSent = messagesSent;
        this.messagesReceived = messagesReceived;
        // Create defensive copies of histograms
        this.connectLatencyHistogram = connectLatencyHistogram.copy();
        this.messageLatencyHistogram = messageLatencyHistogram.copy();
        this.peakMemoryBytes = peakMemoryBytes;
        this.durationMs = durationMs;
    }

    public long getSuccessfulConnections() {
        return successfulConnections;
    }

    public long getFailedConnections() {
        return failedConnections;
    }

    public long getMessagesSent() {
        return messagesSent;
    }

    public long getMessagesReceived() {
        return messagesReceived;
    }

    public long getPeakMemoryBytes() {
        return peakMemoryBytes;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public double getConnectionSuccessRate() {
        long total = successfulConnections + failedConnections;
        return total == 0 ? 0.0 : (double) successfulConnections / total * 100;
    }

    public double getMessageDeliveryRate() {
        return messagesSent == 0 ? 0.0 : (double) messagesReceived / messagesSent * 100;
    }

    public String formatReport() {
        DecimalFormat df = new DecimalFormat("#,###");
        DecimalFormat pctFormat = new DecimalFormat("#0.00");

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("                      TEST RESULTS SUMMARY                      \n");
        sb.append("═══════════════════════════════════════════════════════════════\n\n");

        sb.append("Duration: ").append(df.format(durationMs)).append(" ms (")
          .append(pctFormat.format(durationMs / 1000.0)).append(" seconds)\n\n");

        sb.append("CONNECTION METRICS:\n");
        sb.append("  Successful:     ").append(df.format(successfulConnections)).append("\n");
        sb.append("  Failed:         ").append(df.format(failedConnections)).append("\n");
        sb.append("  Success Rate:   ").append(pctFormat.format(getConnectionSuccessRate())).append("%\n\n");

        if (connectLatencyHistogram.getTotalCount() > 0) {
            sb.append("CONNECTION LATENCY (ms):\n");
            sb.append("  Min:            ").append(df.format(connectLatencyHistogram.getMinValue())).append("\n");
            sb.append("  P50:            ").append(df.format(connectLatencyHistogram.getValueAtPercentile(50))).append("\n");
            sb.append("  P95:            ").append(df.format(connectLatencyHistogram.getValueAtPercentile(95))).append("\n");
            sb.append("  P99:            ").append(df.format(connectLatencyHistogram.getValueAtPercentile(99))).append("\n");
            sb.append("  P99.9:          ").append(df.format(connectLatencyHistogram.getValueAtPercentile(99.9))).append("\n");
            sb.append("  Max:            ").append(df.format(connectLatencyHistogram.getMaxValue())).append("\n");
            sb.append("  Mean:           ").append(df.format((long)connectLatencyHistogram.getMean())).append("\n\n");
        }

        sb.append("MESSAGE METRICS:\n");
        sb.append("  Sent:           ").append(df.format(messagesSent)).append("\n");
        sb.append("  Received:       ").append(df.format(messagesReceived)).append("\n");
        sb.append("  Delivery Rate:  ").append(pctFormat.format(getMessageDeliveryRate())).append("%\n\n");

        if (messageLatencyHistogram.getTotalCount() > 0) {
            sb.append("MESSAGE LATENCY (ms):\n");
            sb.append("  Min:            ").append(df.format(messageLatencyHistogram.getMinValue())).append("\n");
            sb.append("  P50:            ").append(df.format(messageLatencyHistogram.getValueAtPercentile(50))).append("\n");
            sb.append("  P95:            ").append(df.format(messageLatencyHistogram.getValueAtPercentile(95))).append("\n");
            sb.append("  P99:            ").append(df.format(messageLatencyHistogram.getValueAtPercentile(99))).append("\n");
            sb.append("  P99.9:          ").append(df.format(messageLatencyHistogram.getValueAtPercentile(99.9))).append("\n");
            sb.append("  Max:            ").append(df.format(messageLatencyHistogram.getMaxValue())).append("\n");
            sb.append("  Mean:           ").append(df.format((long)messageLatencyHistogram.getMean())).append("\n\n");
        }

        sb.append("RESOURCE USAGE:\n");
        sb.append("  Peak Memory:    ").append(formatBytes(peakMemoryBytes)).append("\n");

        sb.append("\n═══════════════════════════════════════════════════════════════\n");

        return sb.toString();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "i";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public String toCsv() {
        return String.format("%d,%d,%d,%d,%d,%.2f,%.2f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d",
            durationMs,
            successfulConnections,
            failedConnections,
            getConnectionSuccessRate(),
            messagesSent,
            messagesReceived,
            getMessageDeliveryRate(),
            connectLatencyHistogram.getMinValue(),
            connectLatencyHistogram.getValueAtPercentile(50),
            connectLatencyHistogram.getValueAtPercentile(95),
            connectLatencyHistogram.getValueAtPercentile(99),
            connectLatencyHistogram.getMaxValue(),
            messageLatencyHistogram.getMinValue(),
            messageLatencyHistogram.getValueAtPercentile(50),
            messageLatencyHistogram.getValueAtPercentile(95),
            messageLatencyHistogram.getValueAtPercentile(99),
            messageLatencyHistogram.getMaxValue(),
            peakMemoryBytes
        );
    }

    public static String csvHeader() {
        return "duration_ms,successful_connections,failed_connections,connection_success_rate," +
               "messages_sent,messages_received,message_delivery_rate," +
               "connect_latency_min,connect_latency_p50,connect_latency_p95,connect_latency_p99,connect_latency_max," +
               "message_latency_min,message_latency_p50,message_latency_p95,message_latency_p99,message_latency_max," +
               "peak_memory_bytes";
    }
}
