package com.websocketlab.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RedisMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(RedisMessageListener.class);
    private WebSocketHandler webSocketHandler;

    public void setWebSocketHandler(WebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    public void onMessage(String message) {
        logger.info("Received message from Redis: {}", message);
        if (webSocketHandler != null) {
            try {
                // Try to parse as JSON to see if it's a targeted message
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = new com.fasterxml.jackson.databind.ObjectMapper().readValue(message, Map.class);

                if ("targeted".equals(payload.get("type"))) {
                    @SuppressWarnings("unchecked")
                    List<String> targets = (List<String>) payload.get("targets");
                    String content = (String) payload.get("message");
                    webSocketHandler.sendTargetedMessage(targets, content);
                } else {
                    webSocketHandler.broadcastMessage(message);
                }
            } catch (Exception e) {
                // Not JSON or parse error, broadcast as-is
                webSocketHandler.broadcastMessage(message);
            }
        }
    }
}
