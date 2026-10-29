package com.websocketlab.server;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publisher for broadcasting messages to all WebSocket servers via Redis pub/sub
 */
@Component
public class RedisPublisher {

    private final StringRedisTemplate redisTemplate;
    private static final String CHANNEL = "websocket-channel";

    public RedisPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void publish(String message) {
        redisTemplate.convertAndSend(CHANNEL, message);
    }
}
