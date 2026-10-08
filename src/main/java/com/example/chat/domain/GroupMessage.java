package com.example.chat.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity for group chat messages.
 * Schema: channel_id, message_id, user_id, content, created_at
 * Composite PK: (channel_id, message_id)  — per README
 */
@Entity
@Table(name = "group_messages")
@IdClass(GroupMessageId.class)
public class GroupMessage {

    @Id
    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Id
    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public GroupMessage() {}

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }

    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getCreatedAt() { return createdAt; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private Long channelId; private Long messageId; private Long userId; private String content;
        public Builder channelId(Long c) { this.channelId = c; return this; }
        public Builder messageId(Long m) { this.messageId = m; return this; }
        public Builder userId(Long u) { this.userId = u; return this; }
        public Builder content(String c) { this.content = c; return this; }
        public GroupMessage build() {
            GroupMessage gm = new GroupMessage();
            gm.channelId = channelId; gm.messageId = messageId;
            gm.userId = userId; gm.content = content;
            return gm;
        }
    }
}
