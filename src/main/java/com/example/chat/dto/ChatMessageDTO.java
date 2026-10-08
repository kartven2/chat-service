package com.example.chat.dto;

import java.time.Instant;

/**
 * Wire format for chat messages — WebSocket (STOMP) and RabbitMQ.
 */
public class ChatMessageDTO {

    public enum Type { DIRECT, GROUP, HEARTBEAT, ACK }

    private Type type;
    private Long messageId;
    private Long senderId;
    private String senderUsername;
    private Long targetId;
    private String content;
    private Instant timestamp;
    private String handledBy;

    public ChatMessageDTO() {}

    private ChatMessageDTO(Builder b) {
        this.type = b.type; this.messageId = b.messageId;
        this.senderId = b.senderId; this.senderUsername = b.senderUsername;
        this.targetId = b.targetId; this.content = b.content;
        this.timestamp = b.timestamp; this.handledBy = b.handledBy;
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getHandledBy() { return handledBy; }
    public void setHandledBy(String handledBy) { this.handledBy = handledBy; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Type type; private Long messageId; private Long senderId;
        private String senderUsername; private Long targetId; private String content;
        private Instant timestamp; private String handledBy;

        public Builder type(Type t) { this.type = t; return this; }
        public Builder messageId(Long id) { this.messageId = id; return this; }
        public Builder senderId(Long id) { this.senderId = id; return this; }
        public Builder senderUsername(String u) { this.senderUsername = u; return this; }
        public Builder targetId(Long id) { this.targetId = id; return this; }
        public Builder content(String c) { this.content = c; return this; }
        public Builder timestamp(Instant t) { this.timestamp = t; return this; }
        public Builder handledBy(String h) { this.handledBy = h; return this; }
        public ChatMessageDTO build() { return new ChatMessageDTO(this); }
    }
}
