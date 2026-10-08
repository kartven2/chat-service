package com.example.chat.repository;

import com.example.chat.domain.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {

    /** All groups a given user belongs to. */
    @Query("SELECT g FROM Group g WHERE :userId MEMBER OF g.memberIds")
    List<Group> findGroupsByMemberId(@Param("userId") Long userId);
}
