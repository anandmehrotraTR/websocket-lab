package com.websocketlab.server;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for publishing messages to all connected WebSocket clients via Redis pub/sub
 */
@RestController
@RequestMapping("/api")
public class MessageController {

    private final RedisPublisher redisPublisher;

    public MessageController(RedisPublisher redisPublisher) {
        this.redisPublisher = redisPublisher;
    }

    @PostMapping("/publish")
    public void publishMessage(@RequestParam String message) {
        redisPublisher.publish(message);
    }
}
