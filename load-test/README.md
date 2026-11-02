# WebSocket Load Testing Documentation

## Overview

This comprehensive load testing framework tests a WebSocket server's ability to handle:
- **1 million concurrent connections** on a single server
- **10 million messages** (10 messages per client) sent simultaneously
- **Thundering herd scenarios** on connect and reconnect
- **Rate limiting** with exponential backoff

## Test Scenarios

### 1. Progressive Load Test (Max Connections)

**Goal:** Find the maximum number of concurrent connections a single server can handle.

**Method:**
- Progressively add clients: 10 → 100 → 1K → 10K → 100K → 1M
- At each level, measure:
  - Connection success rate
  - Connection latency (P50, P95, P99, P99.9, max)
  - Server memory usage
  - Message delivery latency

**Stop Condition:** When connection success rate drops below 95%

**Command:**
```bash
./gradlew :load-test:bootRun --args="--test-type progressive --server-url ws://localhost:8080/ws --messages 10"
```

---

### 2. Thundering Herd on Connect

**Goal:** Test server behavior when many clients connect simultaneously (simulating a spike event).

**Method:**
- Create 1 million client threads
- All threads connect at exactly the same time
- Measure:
  - Connection latency distribution
  - Memory spike on server
  - Connection success rate
  - Server CPU usage

**Without Rate Limiting:**
```bash
./gradlew :load-test:bootRun --args="--test-type thundering-herd --clients 1000000"
```

**With Rate Limiting (server drops >10K connections/second with 429):**
```bash
# Start server with rate limiting enabled
WS_RATE_LIMITER_ENABLED=true WS_RATE_LIMITER_MAX=10000 docker-compose up ws-server-1

# Run test without backoff
./gradlew :load-test:bootRun --args="--test-type thundering-herd --clients 1000000"

# Run test with client backoff
./gradlew :load-test:bootRun --args="--test-type thundering-herd --clients 1000000 --enable-backoff --max-retries 5 --base-backoff-ms 100"
```

---

### 3. Thundering Herd on Reconnect

**Goal:** Test server recovery when all clients disconnect and reconnect simultaneously.

**Method:**
1. Connect 1 million clients
2. Drop all connections simultaneously
3. All clients reconnect at the same time
4. Measure reconnection behavior with and without backoff

**Command:**
```bash
# Without backoff
./gradlew :load-test:bootRun --args="--test-type reconnect --clients 1000000"

# With backoff
./gradlew :load-test:bootRun --args="--test-type reconnect --clients 1000000 --enable-backoff"
```

---

### 4. Message Spike Test

**Goal:** Measure message delivery latency when all clients send messages simultaneously.

**Method:**
1. Connect 1 million clients
2. Each client sends 10 messages at the same time (10M total messages)
3. Measure:
   - Send throughput (messages/second)
   - Message delivery latency
   - Message delivery rate (% of messages received)

**Command:**
```bash
./gradlew :load-test:bootRun --args="--test-type message-spike --clients 1000000 --messages 10"
```

---

### 5. Complete Test Suite

**Goal:** Run all tests sequentially and generate comprehensive report.

**Command:**
```bash
./gradlew :load-test:bootRun --args="--test-type all --server-url ws://localhost:8080/ws --output-dir ./results"
```

---

## Architecture

### Load Test Module Structure

```
load-test/
├── src/main/java/com/websocketlab/loadtest/
│   ├── LoadTestApplication.java           # Main entry point
│   ├── LoadTestCLI.java                   # CLI orchestrator
│   ├── client/
│   │   └── LoadTestWebSocketClient.java   # WebSocket client with backoff
│   ├── metrics/
│   │   ├── MetricsCollector.java          # Collects latency/throughput metrics
│   │   └── TestResults.java               # Immutable results snapshot
│   └── runner/
│       ├── ProgressiveLoadTest.java       # Progressive load test runner
│       ├── ThunderingHerdTest.java        # Thundering herd test runner
│       └── MessageSpikeTest.java          # Message spike test runner
└── build.gradle.kts
```

### Server Enhancements

#### Rate Limiter
- **Class:** `ConnectionRateLimiter.java`
- **Algorithm:** Sliding window
- **Configuration:**
  - `WS_RATE_LIMITER_ENABLED`: Enable/disable rate limiting
  - `WS_RATE_LIMITER_MAX`: Max connections per window (default: 10,000)
  - `WS_RATE_LIMITER_WINDOW_MS`: Time window in ms (default: 1,000)

#### Client Backoff
- **Algorithm:** Exponential backoff with jitter
- **Formula:** `min(baseBackoff * 2^attempt, 60s) + random jitter (±25%)`
- **Configuration:**
  - `--enable-backoff`: Enable backoff
  - `--max-retries`: Max retry attempts (default: 5)
  - `--base-backoff-ms`: Base backoff time (default: 100ms)

---

## Metrics Collected

### Connection Metrics
- Successful connections
- Failed connections
- Connection success rate (%)
- Connection latency (min, P50, P95, P99, P99.9, max, mean)

### Message Metrics
- Messages sent
- Messages received
- Message delivery rate (%)
- Message latency (min, P50, P95, P99, P99.9, max, mean)

### Resource Metrics
- Peak memory usage
- Memory spike during thundering herd
- Test duration

### All metrics are exported as:
1. **Console output** (formatted report)
2. **CSV files** (for analysis and graphing)

---

## Results Format

### CSV Output Structure

Each test generates a CSV file with the following structure:

**Progressive Test:**
```csv
target_clients,messages_per_client,total_duration_ms,duration_ms,successful_connections,failed_connections,connection_success_rate,...
```

**Thundering Herd Test:**
```csv
test_type,target_clients,successful_clients,total_duration_ms,memory_before_bytes,memory_after_bytes,memory_spike_bytes,...
```

**Message Spike Test:**
```csv
client_count,messages_per_client,total_expected_messages,total_duration_ms,send_duration_ms,send_throughput_msg_per_sec,...
```

---

## Performance Expectations

### Target Goals
- **Connections:** 1,000,000 concurrent connections
- **Messages:** 10,000,000 messages (10 per client)
- **Connection Latency P99:** < 5 seconds
- **Message Latency P99:** < 10 seconds
- **Success Rate:** > 99%

### Server Requirements
For 1M connections, estimated requirements:
- **Memory:** ~16-32 GB (each connection ~16-32 KB)
- **CPU:** 8-16 cores
- **Network:** High bandwidth (10 Gbps+)
- **File Descriptors:** Increase ulimit to 1,100,000+

### System Configuration

**Increase file descriptor limits:**
```bash
ulimit -n 1100000
```

**JVM Options for server:**
```bash
-Xmx32g -Xms32g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

**JVM Options for load test client:**
```bash
-Xmx8g -Xms8g -XX:+UseG1GC
```

---

## Running Tests

### Prerequisites

1. **Start the WebSocket server:**
```bash
docker-compose up ws-server-1 redis
```

2. **Enable rate limiting (optional):**
```bash
WS_RATE_LIMITER_ENABLED=true docker-compose up ws-server-1 redis
```

### Build the Load Test Module

```bash
./gradlew :load-test:build
```

### Run Individual Tests

**Progressive test:**
```bash
./gradlew :load-test:bootRun --args="--test-type progressive"
```

**Thundering herd test:**
```bash
./gradlew :load-test:bootRun --args="--test-type thundering-herd --clients 100000"
```

**With backoff enabled:**
```bash
./gradlew :load-test:bootRun --args="--test-type thundering-herd --clients 100000 --enable-backoff"
```

### View Results

Results are saved to `./load-test-results/` by default:
```bash
ls -la load-test-results/
```

Each file contains:
- Timestamp in filename
- CSV data for analysis
- Can be imported into Excel, Tableau, or Python for visualization

---

## Troubleshooting

### Issue: Connection timeouts
**Solution:** Increase timeout in `LoadTestWebSocketClient.java` or reduce client count

### Issue: Out of memory (client side)
**Solution:** Increase JVM heap size: `-Xmx16g`

### Issue: Too many open files
**Solution:** Increase ulimit: `ulimit -n 1100000`

### Issue: Server crashes under load
**Solution:**
1. Enable rate limiting
2. Enable client backoff
3. Scale horizontally with multiple server instances

---

## Future Enhancements

1. **Distributed load testing** - Run clients from multiple machines
2. **Real-time dashboards** - Grafana integration for live metrics
3. **Auto-scaling tests** - Test Kubernetes horizontal pod autoscaling
4. **Chaos engineering** - Random network failures, latency injection
5. **Regional distribution** - Clients from multiple geographic locations

---

## References

- [WebSocket RFC 6455](https://tools.ietf.org/html/rfc6455)
- [HdrHistogram](https://github.com/HdrHistogram/HdrHistogram) - Latency measurement
- [Spring WebSocket Documentation](https://docs.spring.io/spring-framework/reference/web/websocket.html)
