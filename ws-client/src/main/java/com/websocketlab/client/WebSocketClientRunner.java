package com.websocketlab.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;

@Component
public class WebSocketClientRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketClientRunner.class);

    @Value("${websocket.server.url:ws://localhost:8080/ws}")
    private String serverUrl;

    @Value("${client.id:default-client}")
    private String clientId;

    private final ClientWebSocketHandler handler;

    public WebSocketClientRunner(ClientWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Client '{}' starting, connecting to: {}", clientId, serverUrl);

        StandardWebSocketClient client = new StandardWebSocketClient();

        try {
            client.execute(handler, serverUrl).get();
            logger.info("Client '{}' connected successfully", clientId);

            // Keep the application running
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Client '{}' failed to connect: {}", clientId, e.getMessage(), e);
            throw e;
        }
    }
}
