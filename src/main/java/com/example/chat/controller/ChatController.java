package com.example.chat.controller;

import com.example.chat.dto.ChatMessageDTO;
import com.example.chat.service.ChatService;
import com.example.chat.service.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * WebSocket / STOMP controller — stateful chat server (per README).
 *
 * Destinations:
 *   /app/chat.sendDirect  → 1-1 message
 *   /app/chat.sendGroup   → group message
 *   /app/chat.heartbeat   → presence ping
 *   /app/chat.disconnect  → explicit offline
 */
@Controller
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final PresenceService presenceService;

    public ChatController(ChatService chatService, PresenceService presenceService) {
        this.chatService = chatService;
        this.presenceService = presenceService;
    }

    @MessageMapping("/chat.sendDirect")
    public void sendDirect(@Payload ChatMessageDTO msg,
                           SimpMessageHeaderAccessor headerAccessor) {
        Long senderId = getUserId(headerAccessor);
        String senderUsername = getUsername(headerAccessor);
        log.debug("Direct message from user {} to user {}", senderId, msg.getTargetId());
        chatService.sendDirectMessage(senderId, senderUsername, msg.getTargetId(), msg.getContent());
    }

    @MessageMapping("/chat.sendGroup")
    public void sendGroup(@Payload ChatMessageDTO msg,
                          SimpMessageHeaderAccessor headerAccessor) {
        Long senderId = getUserId(headerAccessor);
        String senderUsername = getUsername(headerAccessor);
        log.debug("Group message from user {} to group {}", senderId, msg.getTargetId());
        chatService.sendGroupMessage(senderId, senderUsername, msg.getTargetId(), msg.getContent());
    }

    @MessageMapping("/chat.heartbeat")
    public void heartbeat(SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserId(headerAccessor);
        presenceService.recordHeartbeat(userId);
        log.trace("Heartbeat from user {}", userId);
    }

    @MessageMapping("/chat.disconnect")
    public void disconnect(SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserId(headerAccessor);
        presenceService.markOffline(userId);
        log.info("User {} disconnected (explicit)", userId);
    }

    private Long getUserId(SimpMessageHeaderAccessor h) {
        var attrs = h.getSessionAttributes();
        if (attrs == null) throw new IllegalStateException("No session attributes");
        return (Long) attrs.get("userId");
    }

    private String getUsername(SimpMessageHeaderAccessor h) {
        var attrs = h.getSessionAttributes();
        if (attrs == null) return "unknown";
        Object u = attrs.get("username");
        return u != null ? (String) u : "unknown";
    }
}
