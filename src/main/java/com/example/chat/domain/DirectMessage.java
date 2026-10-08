package com.example.chat.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity for 1-1 direct messages.
 * Schema: message_id, message_from, message_to, content, created_at (per README)
 */
@Entity
@Table(name = "direct_messages", indexes = {
        @Index(name = "idx_dm_from",    columnList = "message_from"),
        @Index(name = "idx_dm_to",      columnList = "message_to"),
        @Index(name = "idx_dm_created", columnList = "created_at")
})
public class DirectMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;

    @Column(name = "message_from", nullable = false)
    private Long messageFrom;

    @Column(name = "message_to", nullable = false)
    private Long messageTo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DirectMessage() {}

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }

    public Long getMessageId() { return messageId; }
    public Long getMessageFrom() { return messageFrom; }
    public void setMessageFrom(Long messageFrom) { this.messageFrom = messageFrom; }
    public Long getMessageTo() { return messageTo; }
    public void setMessageTo(Long messageTo) { this.messageTo = messageTo; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getCreatedAt() { return createdAt; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private Long messageFrom; private Long messageTo; private String content;
        public Builder messageFrom(Long f) { this.messageFrom = f; return this; }
        public Builder messageTo(Long t) { this.messageTo = t; return this; }
        public Builder content(String c) { this.content = c; return this; }
        public DirectMessage build() {
            DirectMessage m = new DirectMessage();
            m.messageFrom = messageFrom; m.messageTo = messageTo; m.content = content;
            return m;
        }
    }
}
