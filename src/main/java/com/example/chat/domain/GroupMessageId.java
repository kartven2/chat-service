package com.example.chat.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for GroupMessage: (channelId, messageId).
 */
public class GroupMessageId implements Serializable {
    private Long channelId;
    private Long messageId;

    public GroupMessageId() {}
    public GroupMessageId(Long channelId, Long messageId) {
        this.channelId = channelId; this.messageId = messageId;
    }

    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupMessageId that)) return false;
        return Objects.equals(channelId, that.channelId) && Objects.equals(messageId, that.messageId);
    }
    @Override public int hashCode() { return Objects.hash(channelId, messageId); }
}
