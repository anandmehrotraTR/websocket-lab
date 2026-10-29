# WebSocket Lab

A scalable WebSocket server implementation with Redis pub/sub for broadcasting messages across multiple server instances.

## Architecture

- **10 WebSocket server instances** (one per CPU core) behind nginx load balancer
- **Redis pub/sub** for message broadcasting across all server instances
- **5 WebSocket clients** - 2 connected to server-1, 3 connected to server-2
- **Nginx** load balancer for WebSocket connections

## Features

- Each client receives a "Hello" message upon connection
- Redis pub/sub ensures messages broadcast to all clients across all server instances
- Load balanced WebSocket connections with sticky sessions support
- REST API endpoint for publishing messages: `POST /api/publish?message=<text>`

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
