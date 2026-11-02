# WebSocket Lab - Knowledge Graph

This project has been indexed with a knowledge graph for efficient codebase navigation and analysis.

## Quick Start for Claude Code

**Before reading files or searching the codebase, query the graph first to save tokens:**

```bash
# Check if graph exists
[ -f graphify-out/graph.json ] && echo "Graph available" || echo "Run /graphify first"

# Query the graph (fast, token-efficient)
graphify query "How does rate limiting work?"
graphify query "What components handle WebSocket connections?"
graphify query "Trace the message flow from client to server"
```

**Why query the graph first?**
- **Save 90%+ tokens**: Graph queries traverse pre-extracted relationships instead of reading multiple files
- **Faster insights**: Get answers in seconds vs. minutes of file reading
- **Cross-file connections**: See relationships between code, docs, and architecture that text search misses

## Interactive Visualization

Open `graphify-out/graph.html` in your browser to explore the codebase visually:
- **394 nodes** representing classes, functions, concepts, and components
- **738 edges** showing relationships (imports, calls, implements, uses)
- **35 communities** auto-detected by topic

## Graph Statistics

- **Nodes**: 394 (classes, functions, concepts, metrics, algorithms)
- **Edges**: 738 (imports, calls, implements, depends-on, uses)
- **Communities**: 35 clusters of related functionality
- **Coverage**: 32 Java files + 7 documentation files

### Top Communities by Size
1. **WebSocket Client Core** (54 nodes) - Client implementation and backoff logic
2. **Concurrency Primitives** (52 nodes) - Thread-safe data structures
3. **Metrics Collection** (43 nodes) - Latency tracking and results
4. **Load Testing Concepts** (35 nodes) - Test scenarios and validation
5. **Rate Limiting** (27 nodes) - Connection throttling and interceptors

## God Nodes (Most Connected)

These are the core abstractions with the most connections:

1. **TestResults** (28 edges) - Immutable test metrics snapshot
2. **LoadTestWebSocketClient** (27 edges) - WebSocket client with backoff
3. **MetricsCollector** (21 edges) - Thread-safe metrics aggregation
4. **WebSocketHandler** (19 edges) - Server-side connection handler
5. **ConnectionRateLimiter** (16 edges) - Sliding window rate limiter

## Key Cross-Community Bridges

Nodes that connect multiple communities:

- **ConnectionRateLimiter** (betweenness: 0.074)
  Bridges: Rate Limiting ↔ Concurrency Primitives ↔ Redis Configuration

- **ClientRegistry** (betweenness: 0.070)
  Bridges: Concurrency Primitives ↔ Message Publishing ↔ REST Controllers

- **LoadTestWebSocketClient** (betweenness: 0.066)
  Bridges: WebSocket Client Core ↔ Concurrency Primitives ↔ CLI Utilities

## Surprising Connections

Relationships extracted from documentation that span multiple files:

- `WebSocket Server` → `WebSocket Client Dashboard` (README.md → index.html)
- `Connection Rate Limiter` → `Sliding Window Algorithm` (PR → load-test docs)
- `Exponential Backoff` → `Thundering Herd Problem` (PR → load-test docs)
- `Reconnection Test` → `Exponential Backoff` (docs → PR description)

## Common Queries

### Architecture Questions
```bash
# What does the WebSocket handler do?
graphify query "WebSocketHandler responsibilities"

# How are messages broadcast?
graphify query "message broadcasting flow"

# What's the Redis integration pattern?
graphify query "Redis pub/sub implementation"
```

### Load Testing Questions
```bash
# What metrics are collected?
graphify query "load testing metrics"

# How does exponential backoff work?
graphify query "exponential backoff algorithm"

# What test scenarios exist?
graphify query "test scenario types"
```

### Code Navigation
```bash
# Find all rate limiting code
graphify query "rate limiting components"

# Trace connection flow
graphify query "WebSocket connection lifecycle"

# Find metrics-related code
graphify query "latency measurement"
```

## Graph Maintenance

### Update Graph (Incremental)
After code changes, update the graph incrementally:
```bash
graphify --update
```

### Full Rebuild
```bash
/graphify .
```

### Export Formats
```bash
# SVG for embedding in docs
graphify export svg

# GraphML for Gephi/yEd
graphify export graphml

# Neo4j Cypher for graph databases
graphify --neo4j
```

## Query Tips

1. **Use specific terms**: "exponential backoff" > "backoff"
2. **Trace relationships**: "What calls MetricsCollector?" vs "MetricsCollector methods"
3. **Cross-reference**: "How does ConnectionRateLimiter use sliding window?"
4. **Community insights**: "What's in the Rate Limiting community?"

## Performance Benefits

**Without graph**: Read 5-10 files → ~20,000 tokens
**With graph**: Single query → ~500-1000 tokens

**Token savings**: 90-95% reduction for architecture/navigation questions

## Graph Schema

### Node Types
- `class` - Java classes and Spring components
- `function` - Methods and functions
- `module` - Gradle modules (ws-server, load-test, etc.)
- `concept` - Architectural concepts (rate limiting, exponential backoff)
- `metric` - Performance measurements (latency, throughput)
- `algorithm` - Implemented algorithms (sliding window, token bucket)

### Edge Types
- `imports` - Java import statements
- `calls` - Method invocations
- `implements` - Interface implementations
- `depends-on` - Component dependencies
- `uses` - Usage relationships
- `contains` - Hierarchical containment
- `validates` - Test validates concept
- `enables` - Component enables feature

## Integration with Development Workflow

### Before Code Changes
```bash
# Understand impact
graphify query "What depends on WebSocketHandler?"
graphify query "What uses MetricsCollector?"
```

### After Code Changes
```bash
# Update graph incrementally
graphify --update

# Verify no broken references
graphify query "dangling references to [changed-component]"
```

### Code Review
```bash
# Understand component relationships
graphify query "How does [component] connect to the rest of the system?"
```

## Files

- `graphify-out/graph.html` - Interactive visualization
- `graphify-out/graph.json` - Raw graph data (for scripts)
- `graphify-out/GRAPH_REPORT.md` - Detailed analysis report
- `graphify-out/.graphify_python` - Python interpreter path
- `graphify-out/.graphify_root` - Scan root directory

---

**Pro tip**: Before diving into file searches or reading multiple files, query the graph first. It's faster, cheaper, and often reveals connections you wouldn't think to look for.
