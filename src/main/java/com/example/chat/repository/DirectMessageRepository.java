package com.example.chat.repository;

import com.example.chat.domain.DirectMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    /**
     * Fetch the full conversation between two users, newest-last.
     * Used to hydrate chat history when a user comes online.
     */
    @Query("""
            SELECT m FROM DirectMessage m
             WHERE (m.messageFrom = :a AND m.messageTo = :b)
                OR (m.messageFrom = :b AND m.messageTo = :a)
             ORDER BY m.createdAt ASC
            """)
    List<DirectMessage> findConversation(@Param("a") Long userA, @Param("b") Long userB);
}
