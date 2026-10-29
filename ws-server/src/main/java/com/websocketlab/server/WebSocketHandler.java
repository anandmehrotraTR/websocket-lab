package com.websocketlab.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final RedisMessageListener redisMessageListener;
    private final ClientRegistry clientRegistry;
    private final ObjectMapper objectMapper;

    public WebSocketHandler(RedisMessageListener redisMessageListener,
                           ClientRegistry clientRegistry) {
        this.redisMessageListener = redisMessageListener;
        this.clientRegistry = clientRegistry;
        this.objectMapper = new ObjectMapper();
        this.redisMessageListener.setWebSocketHandler(this);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        clientRegistry.registerClient(session.getId());
        logger.info("Client connected: {} - Total connections: {}", session.getId(), sessions.size());

        // Send hello message to the newly connected client
        String helloMessage = String.format("Hello from WebSocket server! Your session ID: %s", session.getId());
        session.sendMessage(new TextMessage(helloMessage));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        logger.info("Message received from {}: {}", session.getId(), message.getPayload());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");

            if ("directMessage".equals(type)) {
                String targetSessionId = (String) payload.get("targetSessionId");
                String content = (String) payload.get("message");
                sendDirectMessage(targetSessionId, content, session.getId());
            } else {
                // Echo the message back
                session.sendMessage(new TextMessage("Echo: " + message.getPayload()));
            }
        } catch (Exception e) {
            // Not JSON, echo back as before
            session.sendMessage(new TextMessage("Echo: " + message.getPayload()));
        }
    }

    private void sendDirectMessage(String targetSessionId, String content, String fromSessionId) {
        WebSocketSession targetSession = sessions.get(targetSessionId);
        if (targetSession != null && targetSession.isOpen()) {
            try {
                Map<String, String> response = new HashMap<>();
                response.put("type", "directMessage");
                response.put("from", fromSessionId);
                response.put("message", content);
                targetSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                logger.info("Sent direct message from {} to {}", fromSessionId, targetSessionId);
            } catch (IOException e) {
                logger.error("Failed to send direct message to {}: {}", targetSessionId, e.getMessage());
            }
        } else {
            logger.warn("Target session {} not found on this server", targetSessionId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        clientRegistry.unregisterClient(session.getId());
        logger.info("Client disconnected: {} - Total connections: {}", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("Transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    /**
     * Broadcast message to all connected clients
     */
    public void broadcastMessage(String message) {
        logger.info("Broadcasting message to {} clients: {}", sessions.size(), message);
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                logger.error("Error sending message to session {}: {}", session.getId(), e.getMessage());
            }
        });
    }

    /**
     * Send targeted message to specific clients
     */
    public void sendTargetedMessage(java.util.List<String> targetIds, String message) {
        logger.info("Sending targeted message to {} clients: {}", targetIds.size(), message);
        int sent = 0;
        for (String sessionId : targetIds) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                    sent++;
                } catch (IOException e) {
                    logger.error("Error sending targeted message to session {}: {}", sessionId, e.getMessage());
                }
            }
        }
        logger.info("Targeted message delivered to {}/{} clients on this server", sent, targetIds.size());
    }
}
