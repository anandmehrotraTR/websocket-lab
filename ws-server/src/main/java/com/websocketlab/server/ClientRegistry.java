package com.websocketlab.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ClientRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ClientRegistry.class);
    private static final String REGISTRY_KEY = "websocket:clients";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String serverId;

    public ClientRegistry(StringRedisTemplate redisTemplate,
                         @Value("${server.id:unknown}") String serverId) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.serverId = serverId;
    }

    /**
     * Clear any entries left behind by a previous incarnation of this server.
     * Sessions do not survive a restart, so anything still listed under our
     * own serverId is stale and would show up as a phantom client.
     */
    @jakarta.annotation.PostConstruct
    public void clearStaleEntries() {
        Map<Object, Object> allClients = redisTemplate.opsForHash().entries(REGISTRY_KEY);
        int removed = 0;
        for (Map.Entry<Object, Object> entry : allClients.entrySet()) {
            try {
                ClientInfo info = objectMapper.readValue((String) entry.getValue(), ClientInfo.class);
                if (serverId.equals(info.getServerId())) {
                    redisTemplate.opsForHash().delete(REGISTRY_KEY, entry.getKey());
                    removed++;
                }
            } catch (JsonProcessingException e) {
                // Unparseable entry is stale by definition - drop it
                redisTemplate.opsForHash().delete(REGISTRY_KEY, entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            logger.info("Cleared {} stale registry entries for server {}", removed, serverId);
        }
    }

    public void registerClient(String sessionId) {
        try {
            ClientInfo info = new ClientInfo(sessionId, serverId, System.currentTimeMillis());
            String json = objectMapper.writeValueAsString(info);
            redisTemplate.opsForHash().put(REGISTRY_KEY, sessionId, json);
            logger.info("Registered client {} on server {}", sessionId, serverId);
        } catch (JsonProcessingException e) {
            logger.error("Failed to register client {}", sessionId, e);
        }
    }

    public void unregisterClient(String sessionId) {
        redisTemplate.opsForHash().delete(REGISTRY_KEY, sessionId);
        logger.info("Unregistered client {} from server {}", sessionId, serverId);
    }

    public Map<String, List<ClientInfo>> getAllClientsByServer() {
        Map<String, List<ClientInfo>> byServer = new HashMap<>();

        Map<Object, Object> allClients = redisTemplate.opsForHash().entries(REGISTRY_KEY);

        for (Map.Entry<Object, Object> entry : allClients.entrySet()) {
            try {
                ClientInfo info = objectMapper.readValue((String) entry.getValue(), ClientInfo.class);
                byServer.computeIfAbsent(info.getServerId(), k -> new ArrayList<>()).add(info);
            } catch (JsonProcessingException e) {
                logger.error("Failed to parse client info: {}", entry.getValue(), e);
            }
        }

        return byServer;
    }
}
