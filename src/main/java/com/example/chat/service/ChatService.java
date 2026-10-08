package com.example.chat.service;

import com.example.chat.domain.DirectMessage;
import com.example.chat.domain.Group;
import com.example.chat.domain.GroupMessage;
import com.example.chat.dto.ChatMessageDTO;
import com.example.chat.repository.DirectMessageRepository;
import com.example.chat.repository.GroupMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core stateful chat service — the heart of the system.
 *
 * ─────────────────────────────────────────────────────────────────
 *  1-1 message flow (per README, steps 1-7):
 * ─────────────────────────────────────────────────────────────────
 *  1. User A sends STOMP frame to /app/chat.sendDirect
 *  2. sendDirectMessage() gets message ID from DB auto-increment
 *  3. Message published to RabbitMQ direct exchange (routing key = "user.{recipientId}")
 *  4. Message persisted to PostgreSQL (chat history stored forever)
 *  5. deliverDirectMessage() consumes from recipient's queue:
 *       - If online  → WebSocket push (/user/{userId}/queue/messages)
 *       - If offline → push notification stub (FCM/APN)
 *
 * ─────────────────────────────────────────────────────────────────
 *  Group message flow (per README — per-member inbox model):
 * ─────────────────────────────────────────────────────────────────
 *  1. User sends STOMP frame to /app/chat.sendGroup
 *  2. Message ID from per-group AtomicLong (local sequence generator per README)
 *  3. Fan-out: one RabbitMQ message per member (routing key = "group.{groupId}.{memberId}")
 *  4. Persisted to PostgreSQL
 *  5. Each member's listener: deliver via WebSocket or push notification
 *
 * ─────────────────────────────────────────────────────────────────
 *  Redis hot cache: last 500 messages per conversation (ring buffer)
 * ─────────────────────────────────────────────────────────────────
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String KEY_DIRECT_HISTORY = "msg:direct:";
    private static final int    MAX_REDIS_HISTORY  = 500;

    /** Local sequence number generators, one per groupId (per README). */
    private final ConcurrentHashMap<Long, AtomicLong> groupSeqMap = new ConcurrentHashMap<>();

    private final DirectMessageRepository directMessageRepository;
    private final GroupMessageRepository  groupMessageRepository;
    private final GroupService            groupService;
    private final PresenceService         presenceService;
    private final NotificationService     notificationService;
    private final SimpMessagingTemplate   messagingTemplate;
    private final RabbitTemplate          rabbitTemplate;
    private final RabbitAdmin             rabbitAdmin;
    private final RedisTemplate<String, Object> redisTemplate;
    private final String directExchange;
    private final String groupExchange;
    private final String instanceId;

    public ChatService(
            DirectMessageRepository directMessageRepository,
            GroupMessageRepository groupMessageRepository,
            GroupService groupService,
            PresenceService presenceService,
            NotificationService notificationService,
            SimpMessagingTemplate messagingTemplate,
            RabbitTemplate rabbitTemplate,
            RabbitAdmin rabbitAdmin,
            RedisTemplate<String, Object> redisTemplate,
            @Value("${chat.rabbitmq.direct-exchange}") String directExchange,
            @Value("${chat.rabbitmq.group-exchange}")  String groupExchange,
            @Value("${chat.server.instance-id}")       String instanceId) {
        this.directMessageRepository = directMessageRepository;
        this.groupMessageRepository  = groupMessageRepository;
        this.groupService            = groupService;
        this.presenceService         = presenceService;
        this.notificationService     = notificationService;
        this.messagingTemplate       = messagingTemplate;
        this.rabbitTemplate          = rabbitTemplate;
        this.rabbitAdmin             = rabbitAdmin;
        this.redisTemplate           = redisTemplate;
        this.directExchange          = directExchange;
        this.groupExchange           = groupExchange;
        this.instanceId              = instanceId;
    }

    // ═══════════════════════════════════════════════════════════════
    //  1-1 DIRECT MESSAGE
    // ═══════════════════════════════════════════════════════════════

    /** Steps 1-4: receive from client, persist, push to Redis cache, enqueue in RabbitMQ. */
    @Transactional
    public void sendDirectMessage(Long senderId, String senderUsername,
                                  Long recipientId, String content) {
        // Persist to DB (step 4 — chat history stored forever)
        DirectMessage saved = directMessageRepository.save(
                DirectMessage.builder()
                        .messageFrom(senderId)
                        .messageTo(recipientId)
                        .content(content)
                        .build());

        ChatMessageDTO dto = buildDTO(ChatMessageDTO.Type.DIRECT, saved.getMessageId(),
                senderId, senderUsername, recipientId, content);

        // Hot-cache in Redis (ring buffer of last 500 messages)
        cacheMessage(directConversationKey(senderId, recipientId), dto);

        // Step 3: enqueue in RabbitMQ direct exchange
        ensureDirectQueue(recipientId);
        rabbitTemplate.convertAndSend(directExchange, "user." + recipientId, dto);
        log.debug("Enqueued direct message {} → user {}", saved.getMessageId(), recipientId);
    }

    /**
     * Step 5-7: consume from recipient queue and deliver via WebSocket or push notification.
     * In multi-server setups each server listens on queues for its connected users.
     */
    @RabbitListener(queues = "#{@directQueueName}")
    public void deliverDirectMessage(ChatMessageDTO dto) {
        Long recipientId = dto.getTargetId();
        if (presenceService.isOnline(recipientId)) {
            // Online: push via WebSocket (step 5)
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(recipientId), "/queue/messages", dto);
            log.debug("Delivered direct message {} to online user {}", dto.getMessageId(), recipientId);
        } else {
            // Offline: push notification stub (step 6)
            notificationService.sendPushNotification(
                    recipientId, dto.getSenderUsername(), dto.getContent());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  GROUP MESSAGE
    // ═══════════════════════════════════════════════════════════════

    /** Group send: per-member fan-out to individual inbox queues. */
    @Transactional
    public void sendGroupMessage(Long senderId, String senderUsername,
                                 Long groupId, String content) {
        Group group = groupService.getGroup(groupId);
        if (!group.getMemberIds().contains(senderId))
            throw new SecurityException("User " + senderId + " is not a member of group " + groupId);

        // Local sequence number generator per group (per README)
        long msgId = nextGroupMessageId(groupId);

        // Persist to PostgreSQL
        groupMessageRepository.save(GroupMessage.builder()
                .channelId(groupId).messageId(msgId)
                .userId(senderId).content(content).build());

        // Fan-out: one message per member to their personal inbox queue
        ChatMessageDTO dto = buildDTO(ChatMessageDTO.Type.GROUP, msgId,
                senderId, senderUsername, groupId, content);
        for (Long memberId : group.getMemberIds()) {
            ensureGroupQueue(groupId, memberId);
            rabbitTemplate.convertAndSend(groupExchange, "group." + groupId + "." + memberId, dto);
        }
        log.debug("Fan-out group message {} to {} members of group {}",
                msgId, group.getMemberIds().size(), groupId);
    }

    /** Consume group inbox queue and deliver. */
    @RabbitListener(queues = "#{@groupQueueName}")
    public void deliverGroupMessage(ChatMessageDTO dto) {
        Long groupId = dto.getTargetId();
        // In single-server mode: broadcast to the group topic for all subscribers
        messagingTemplate.convertAndSend("/topic/group/" + groupId, dto);
    }

    // ═══════════════════════════════════════════════════════════════
    //  HISTORY (offline user reconnects)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Per README: "When an offline user comes online, she will see all her previous chat history."
     */
    public List<DirectMessage> getDirectHistory(Long userA, Long userB) {
        return directMessageRepository.findConversation(userA, userB);
    }

    public List<GroupMessage> getGroupHistory(Long groupId) {
        return groupMessageRepository.findByChannelIdOrderByCreatedAtAsc(groupId);
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    private long nextGroupMessageId(Long groupId) {
        return groupSeqMap
                .computeIfAbsent(groupId, id -> new AtomicLong(0))
                .incrementAndGet();
    }

    private void cacheMessage(String key, ChatMessageDTO dto) {
        redisTemplate.opsForList().rightPush(key, dto);
        redisTemplate.opsForList().trim(key, -MAX_REDIS_HISTORY, -1);
    }

    private String directConversationKey(Long a, Long b) {
        long lo = Math.min(a, b), hi = Math.max(a, b);
        return KEY_DIRECT_HISTORY + lo + ":" + hi;
    }

    private ChatMessageDTO buildDTO(ChatMessageDTO.Type type, Long msgId,
                                    Long senderId, String senderUsername,
                                    Long targetId, String content) {
        return ChatMessageDTO.builder()
                .type(type).messageId(msgId)
                .senderId(senderId).senderUsername(senderUsername)
                .targetId(targetId).content(content)
                .timestamp(Instant.now()).handledBy(instanceId)
                .build();
    }

    /** Idempotent: declare a per-user direct queue and bind it to the direct exchange. */
    private void ensureDirectQueue(Long userId) {
        String queueName = "direct.user." + userId;
        Queue queue = QueueBuilder.durable(queueName).build();
        Binding binding = BindingBuilder.bind(queue)
                .to(new DirectExchange(directExchange))
                .with("user." + userId);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(binding);
    }

    /** Idempotent: declare a per-member group inbox queue. */
    private void ensureGroupQueue(Long groupId, Long memberId) {
        String queueName = "group." + groupId + ".member." + memberId;
        Queue queue = QueueBuilder.durable(queueName).build();
        Binding binding = BindingBuilder.bind(queue)
                .to(new TopicExchange(groupExchange))
                .with("group." + groupId + "." + memberId);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(binding);
    }
}
