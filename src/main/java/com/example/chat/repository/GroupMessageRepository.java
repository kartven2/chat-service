package com.example.chat.repository;

import com.example.chat.domain.GroupMessage;
import com.example.chat.domain.GroupMessageId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupMessageRepository extends JpaRepository<GroupMessage, GroupMessageId> {

    /**
     * Fetch all messages for a channel ordered chronologically.
     * Used when an offline member reconnects and needs to sync history.
     */
    List<GroupMessage> findByChannelIdOrderByCreatedAtAsc(Long channelId);
}
