package com.websocketlab.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketHandler webSocketHandler;
    private final ConnectionRateLimiter connectionRateLimiter;

    @Value("${websocket.rate-limiter.enabled:false}")
    private boolean rateLimiterEnabled;

    @Value("${websocket.rate-limiter.max-connections:10000}")
    private int maxConnectionsPerWindow;

    @Value("${websocket.rate-limiter.window-size-ms:1000}")
    private long windowSizeMs;

    public WebSocketConfig(WebSocketHandler webSocketHandler,
                          ConnectionRateLimiter connectionRateLimiter) {
        this.webSocketHandler = webSocketHandler;
        this.connectionRateLimiter = connectionRateLimiter;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/ws")
                .addInterceptors(new RateLimitingHandshakeInterceptor(connectionRateLimiter))
                .setAllowedOrigins("*");
    }
}
