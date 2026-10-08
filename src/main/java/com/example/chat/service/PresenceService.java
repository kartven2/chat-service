package com.example.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Presence service — manages online/offline indicator using Redis TTL keys.
 *
 * Design (per README heartbeat mechanism):
 *  - Client sends HEARTBEAT every ~5 s over WebSocket.
 *  - Server refreshes Redis key "presence:{userId}" with TTL=10 s.
 *  - Expired key = offline. isOnline() is O(1).
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private static final String PRESENCE_PREFIX = "presence:";

    private final StringRedisTemplate redis;
    private final String instanceId;
    private final long heartbeatTtlSeconds;

    public PresenceService(
            StringRedisTemplate redis,
            @Value("${chat.server.instance-id}") String instanceId,
            @Value("${chat.presence.heartbeat-ttl-seconds}") long heartbeatTtlSeconds) {
        this.redis = redis;
        this.instanceId = instanceId;
        this.heartbeatTtlSeconds = heartbeatTtlSeconds;
    }

    public void recordHeartbeat(Long userId) {
        redis.opsForValue().set(PRESENCE_PREFIX + userId, instanceId,
                Duration.ofSeconds(heartbeatTtlSeconds));
        log.debug("Heartbeat recorded for user {} on server {}", userId, instanceId);
    }

    public void markOnline(Long userId) {
        recordHeartbeat(userId);
        log.info("User {} is now online", userId);
    }

    public void markOffline(Long userId) {
        redis.delete(PRESENCE_PREFIX + userId);
        log.info("User {} is now offline", userId);
    }

    public boolean isOnline(Long userId) {
        return Boolean.TRUE.equals(redis.hasKey(PRESENCE_PREFIX + userId));
    }

    public String getServerForUser(Long userId) {
        return redis.opsForValue().get(PRESENCE_PREFIX + userId);
    }
}
