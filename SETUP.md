# IntelliJ IDEA Setup Guide

## Project Setup Complete ✓

The WebSocket Lab project is now configured for IntelliJ IDEA with run configurations.

## What's Been Set Up

### 1. Source Code
- ✅ **ws-server** - WebSocket server with Redis pub/sub
- ✅ **ws-client** - WebSocket client application
- ✅ All Java classes compiled successfully

### 2. IntelliJ Run Configurations
Two run configurations have been created:

#### WebSocketServerApplication
- Runs the WebSocket server on port 8080
- Environment variables:
  - `REDIS_HOST=localhost`
  - `REDIS_PORT=6379`
  - `SERVER_PORT=8080`

#### WebSocketClientApplication
- Runs a WebSocket client
- Environment variables:
  - `WS_SERVER_URL=ws://localhost:8080/ws`
  - `CLIENT_ID=local-client`

## Prerequisites

### 1. Install Docker Desktop (Required for full stack)
Download from: https://www.docker.com/products/docker-desktop

**Installation Steps:**
1. Download Docker Desktop for macOS
2. Open the .dmg file and drag Docker to Applications
3. Launch Docker Desktop from Applications
4. Wait for Docker to start (whale icon in menu bar)
5. Verify installation: Open Terminal and run `docker --version`

### 2. Install Redis (For local development)
```bash
brew install redis
```

## Running the Project

### Option A: Full Stack with Docker (Recommended)

Once Docker Desktop is installed:

```bash
cd ~/IdeaProjects/websocket-lab
docker compose up --build
```

This starts:
- 10 WebSocket servers (one per CPU core)
- 5 WebSocket clients
- Redis
- Nginx load balancer

Access via: `ws://localhost:8080/ws`

### Option B: Local Development in IntelliJ

1. **Start Redis:**
   ```bash
   redis-server
   ```

2. **In IntelliJ IDEA:**
   - Open the project: `~/IdeaProjects/websocket-lab`
   - Go to Run → Edit Configurations
   - You'll see two configurations ready:
     - WebSocketServerApplication
     - WebSocketClientApplication
   
3. **Run the server:**
   - Select "WebSocketServerApplication" from the run configurations dropdown
   - Click the green play button
   - Server will start on http://localhost:8080

4. **Run the client:**
   - Select "WebSocketClientApplication" from the run configurations dropdown
   - Click the green play button
   - Client will connect to the server and receive the hello message

### Option C: Run Multiple Instances

To test multiple servers locally:

**Terminal 1 - Server on port 8081:**
```bash
SERVER_PORT=8081 ./gradlew :ws-server:bootRun
```

**Terminal 2 - Server on port 8082:**
```bash
SERVER_PORT=8082 ./gradlew :ws-server:bootRun
```

**Terminal 3 - Client to server 1:**
```bash
WS_SERVER_URL=ws://localhost:8081/ws CLIENT_ID=client-1 ./gradlew :ws-client:bootRun
```

**Terminal 4 - Client to server 2:**
```bash
WS_SERVER_URL=ws://localhost:8082/ws CLIENT_ID=client-2 ./gradlew :ws-client:bootRun
```

## Testing the Setup

### 1. Test WebSocket Connection
Using `wscat` (install with `npm install -g wscat`):
```bash
wscat -c ws://localhost:8080/ws
```

You should receive: `Hello from WebSocket server! Your session ID: ...`

### 2. Test Redis Pub/Sub Broadcasting
```bash
curl -X POST "http://localhost:8080/api/publish?message=Hello%20Everyone"
```

All connected clients across all servers will receive the message.

### 3. Monitor Redis
```bash
# Connect to Redis CLI
redis-cli

# Subscribe to the channel
SUBSCRIBE websocket-channel

# In another terminal, publish a message
redis-cli PUBLISH websocket-channel "Test message"
```

## IntelliJ Tips

### Import the Project
1. Open IntelliJ IDEA
2. File → Open
3. Navigate to: `~/IdeaProjects/websocket-lab`
4. Click "Open"
5. IntelliJ will detect it's a Gradle project and import it automatically

### Reload Gradle Changes
If you modify `build.gradle.kts`:
- Click the Gradle refresh icon in the top right
- Or: Right-click on the project → Gradle → Reload Gradle Project

### View Logs
- Run window shows logs for the currently running configuration
- Each run configuration can run simultaneously in separate tabs

## Project Structure
```
websocket-lab/
├── ws-server/                    # WebSocket server module
│   └── src/main/java/com/websocketlab/server/
│       ├── WebSocketServerApplication.java
│       ├── WebSocketConfig.java
│       ├── WebSocketHandler.java
│       ├── RedisConfig.java
│       ├── RedisMessageListener.java
│       ├── RedisPublisher.java
│       └── MessageController.java
├── ws-client/                    # WebSocket client module
│   └── src/main/java/com/websocketlab/client/
│       ├── WebSocketClientApplication.java
│       ├── ClientWebSocketHandler.java
│       └── WebSocketClientRunner.java
├── docker-compose.yml           # 10 servers + 5 clients + Redis + nginx
├── nginx.conf                   # Load balancer config
└── README.md                    # Full documentation

```

## Next Steps

1. ✅ Install Docker Desktop
2. ✅ Open project in IntelliJ IDEA
3. ✅ Review the code in IntelliJ
4. ✅ Run locally with Redis, or
5. ✅ Run full stack with Docker

## Troubleshooting

### Redis Connection Refused
Make sure Redis is running:
```bash
redis-cli ping
# Should return: PONG
```

### Port Already in Use
Change the port in run configuration or stop the conflicting process:
```bash
lsof -i :8080
kill -9 <PID>
```

### Docker Not Starting
- Check Docker Desktop is running (whale icon in menu bar)
- Try restarting Docker Desktop
- Check disk space (Docker needs space for images)

## Documentation
See `README.md` for full API documentation and architecture details.
