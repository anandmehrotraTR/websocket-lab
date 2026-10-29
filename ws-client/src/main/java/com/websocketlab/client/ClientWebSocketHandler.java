package com.websocketlab.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class ClientWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ClientWebSocketHandler.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String timestamp = LocalDateTime.now().format(formatter);
        logger.info("[{}] Connected to server: {}", timestamp, session.getRemoteAddress());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String timestamp = LocalDateTime.now().format(formatter);
        logger.info("[{}] Received message: {}", timestamp, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String timestamp = LocalDateTime.now().format(formatter);
        logger.info("[{}] Disconnected from server. Status: {}", timestamp, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String timestamp = LocalDateTime.now().format(formatter);
        logger.error("[{}] Transport error: {}", timestamp, exception.getMessage(), exception);
    }
}
