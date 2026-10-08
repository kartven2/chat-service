package com.example.chat.config;

import com.example.chat.service.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Listens to WebSocket lifecycle events to keep presence state in sync.
 * On connect → mark online. On disconnect → mark offline.
 * Handles unexpected disconnects (no explicit /app/chat.disconnect frame needed).
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final PresenceService presenceService;

    public WebSocketEventListener(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = getUserId(accessor);
        if (userId != null) {
            presenceService.markOnline(userId);
            log.info("WebSocket connected: userId={}, sessionId={}", userId, accessor.getSessionId());
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = getUserId(accessor);
        if (userId != null) {
            presenceService.markOffline(userId);
            log.info("WebSocket disconnected: userId={}, sessionId={}", userId, accessor.getSessionId());
        }
    }

    private Long getUserId(StompHeaderAccessor accessor) {
        try {
            var attrs = accessor.getSessionAttributes();
            return attrs != null ? (Long) attrs.get("userId") : null;
        } catch (Exception e) {
            return null;
        }
    }
}
