# WebSocket Lab

A scalable WebSocket server implementation with Redis pub/sub for broadcasting messages across multiple server instances, plus comprehensive load testing framework for testing at scale (1M+ concurrent connections).

## Architecture

- **10 WebSocket server instances** (one per CPU core) behind nginx load balancer
- **Redis pub/sub** for message broadcasting across all server instances
- **5 WebSocket clients** - 2 connected to server-1, 3 connected to server-2
- **Nginx** load balancer for WebSocket connections
- **Load testing framework** for testing extreme scale scenarios

## Modules

### ws-server
WebSocket server with Redis pub/sub integration, rate limiting, and metrics collection.

**Features:**
- WebSocket connection handling
- Redis pub/sub for cross-instance messaging
- Configurable connection rate limiting (sliding window)
- Direct messaging between clients
- REST API for message publishing

### ws-client
Simple WebSocket client that connects to the server and logs messages.

### load-test
Comprehensive load testing framework for stress testing WebSocket servers.

**Features:**
- Progressive load testing (10 → 1M clients)
- Thundering herd simulation (simultaneous connections)
- Reconnection testing
- Message spike testing (10M messages)
- Exponential backoff with jitter
- Detailed metrics (P50, P95, P99, P99.9 latencies)
- CSV export for analysis

See [load-test/README.md](load-test/README.md) for detailed documentation.

## Features

- Each client receives a "Hello" message upon connection
- Redis pub/sub ensures messages broadcast to all clients across all server instances
- Load balanced WebSocket connections with sticky sessions support
- REST API endpoint for publishing messages: `POST /api/publish?message=<text>`
- Connection rate limiting (configurable)
- Load testing framework for extreme scale testing

## Running the Project

### Start all services (10 servers, 5 clients, Redis, nginx)

```bash
docker-compose up --build
```

### View logs for specific services

```bash
# All server logs
docker-compose logs -f ws-server-1 ws-server-2

# Client logs
docker-compose logs -f ws-client-1 ws-client-2 ws-client-3 ws-client-4 ws-client-5

# Redis logs
docker-compose logs -f redis

# Nginx logs
docker-compose logs -f nginx
```

### Stop all services

```bash
docker-compose down
```

## Testing the Setup

### 1. Connect via WebSocket client

```bash
# Connect through nginx load balancer
wscat -c ws://localhost:8080/ws
```

### 2. Publish message via REST API

This will broadcast to ALL connected clients across ALL server instances via Redis pub/sub:

```bash
curl -X POST "http://localhost:8080/api/publish?message=Hello%20from%20Redis"
```

### 3. Monitor Redis pub/sub

```bash
docker-compose exec redis redis-cli
> SUBSCRIBE websocket-channel
```

## Architecture Details

### Server Distribution
- **ws-server-1 through ws-server-10**: 10 identical server instances
- **nginx**: Load balances on port 8080 using least-connections algorithm
- **Redis**: Pub/sub hub on port 6379

### Client Distribution
- **ws-client-1, ws-client-2**: Connected to ws-server-1
- **ws-client-3, ws-client-4, ws-client-5**: Connected to ws-server-2

## Configuration

### Environment Variables

**Server:**
- `SERVER_PORT`: Server port (default: 8080)
- `REDIS_HOST`: Redis hostname (default: redis)
- `REDIS_PORT`: Redis port (default: 6379)

**Client:**
- `WS_SERVER_URL`: WebSocket server URL (e.g., ws://ws-server-1:8080/ws)
- `CLIENT_ID`: Unique client identifier

## Development

### Build locally

```bash
./gradlew build
```

### Run server locally

```bash
./gradlew :ws-server:bootRun
```

### Run client locally

```bash
WS_SERVER_URL=ws://localhost:8080/ws CLIENT_ID=local-client ./gradlew :ws-client:bootRun
```

## Tech Stack

- Java 21
- Spring Boot 3.3.13
- Spring WebSocket
- Spring Data Redis
- Lettuce (Redis client)
- Nginx (Load balancer)
- Docker & Docker Compose

## Load Testing

The project includes a comprehensive load testing framework capable of testing up to 1 million concurrent connections.

### Quick Start - Load Testing

**Run progressive load test:**
```bash
./gradlew :load-test:bootRun --args="--test-type progressive --server-url ws://localhost:8080/ws"
```

**Run thundering herd test (100K clients connecting simultaneously):**
```bash
./gradlew :load-test:bootRun --args="--test-type thundering-herd --clients 100000 --enable-backoff"
```

**Run all tests:**
```bash
./gradlew :load-test:bootRun --args="--test-type all --output-dir ./results"
```

See [load-test/README.md](load-test/README.md) for comprehensive documentation.

### Load Testing Features

- **Progressive scaling:** Test from 10 to 1M+ clients
- **Thundering herd:** Simulate connection spikes
- **Message bursts:** Test 10M+ simultaneous messages
- **Rate limiting:** Server-side connection throttling
- **Exponential backoff:** Client-side retry with jitter
- **Detailed metrics:** P50, P95, P99, P99.9 latencies with HdrHistogram
- **CSV export:** Results ready for analysis and graphing

### Server Rate Limiting

Enable rate limiting to protect the server from connection floods:

```bash
WS_RATE_LIMITER_ENABLED=true WS_RATE_LIMITER_MAX=10000 WS_RATE_LIMITER_WINDOW_MS=1000 docker-compose up ws-server-1
```

Configuration:
- `WS_RATE_LIMITER_ENABLED`: Enable/disable rate limiting
- `WS_RATE_LIMITER_MAX`: Max connections per window (default: 10,000)
- `WS_RATE_LIMITER_WINDOW_MS`: Time window in milliseconds (default: 1,000)

## Documentation

- [Load Test Documentation](load-test/README.md) - Comprehensive load testing guide
- [LinkedIn Post Guide](LINKEDIN_POST.md) - Test results and prompt analysis for sharing

