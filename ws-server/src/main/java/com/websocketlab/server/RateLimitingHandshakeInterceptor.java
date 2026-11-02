package com.websocketlab.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Interceptor to enforce rate limiting on WebSocket handshake requests
 */
public class RateLimitingHandshakeInterceptor implements HandshakeInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(RateLimitingHandshakeInterceptor.class);

    private final ConnectionRateLimiter rateLimiter;

    public RateLimitingHandshakeInterceptor(ConnectionRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                  WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        boolean allowed = rateLimiter.allowConnection();

        if (!allowed) {
            logger.warn("Connection rejected due to rate limiting from {}", request.getRemoteAddress());
            response.setStatusCode(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
            response.getHeaders().add("Retry-After", "1"); // Suggest retry after 1 second
            return false;
        }

        logger.debug("Connection allowed from {}", request.getRemoteAddress());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {
        // Nothing to do after handshake
    }
}
