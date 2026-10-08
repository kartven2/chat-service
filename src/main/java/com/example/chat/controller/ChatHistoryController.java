package com.example.chat.controller;

import com.example.chat.domain.DirectMessage;
import com.example.chat.domain.GroupMessage;
import com.example.chat.security.ChatUserPrincipal;
import com.example.chat.service.ChatService;
import com.example.chat.service.PresenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * HTTP REST endpoints for chat history and presence checks.
 * Per README: "When an offline user comes online, she will see all her previous chat history."
 *
 * GET /api/chat/history/direct/{otherUserId}  → 1-1 conversation history
 * GET /api/chat/history/group/{groupId}       → group message history
 * GET /api/chat/presence/{userId}             → online status
 */
@RestController
@RequestMapping("/api/chat")
public class ChatHistoryController {

    private final ChatService chatService;
    private final PresenceService presenceService;

    public ChatHistoryController(ChatService chatService, PresenceService presenceService) {
        this.chatService = chatService;
        this.presenceService = presenceService;
    }

    @GetMapping("/history/direct/{otherUserId}")
    public ResponseEntity<List<DirectMessage>> directHistory(
            @PathVariable Long otherUserId,
            @AuthenticationPrincipal ChatUserPrincipal principal) {
        return ResponseEntity.ok(chatService.getDirectHistory(principal.getUserId(), otherUserId));
    }

    @GetMapping("/history/group/{groupId}")
    public ResponseEntity<List<GroupMessage>> groupHistory(@PathVariable Long groupId) {
        return ResponseEntity.ok(chatService.getGroupHistory(groupId));
    }

    @GetMapping("/presence/{userId}")
    public ResponseEntity<Map<String, Object>> presence(@PathVariable Long userId) {
        return ResponseEntity.ok(Map.of("userId", userId, "online", presenceService.isOnline(userId)));
    }
}
