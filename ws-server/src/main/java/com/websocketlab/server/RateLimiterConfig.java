package com.websocketlab.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimiterConfig {

    @Value("${websocket.rate-limiter.enabled:false}")
    private boolean rateLimiterEnabled;

    @Value("${websocket.rate-limiter.max-connections:10000}")
    private int maxConnectionsPerWindow;

    @Value("${websocket.rate-limiter.window-size-ms:1000}")
    private long windowSizeMs;

    @Bean
    public ConnectionRateLimiter connectionRateLimiter() {
        return new ConnectionRateLimiter(maxConnectionsPerWindow, windowSizeMs, rateLimiterEnabled);
    }
}
