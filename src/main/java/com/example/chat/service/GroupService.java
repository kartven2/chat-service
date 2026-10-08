package com.example.chat.service;

import com.example.chat.domain.Group;
import com.example.chat.dto.GroupDTO;
import com.example.chat.repository.GroupRepository;
import com.example.chat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Group management service (stateless).
 * Rules: max 100 members, owner-only add/remove, stored in PostgreSQL.
 */
@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final int maxMembers;

    public GroupService(GroupRepository groupRepository,
                        UserRepository userRepository,
                        @Value("${chat.group.max-members}") int maxMembers) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.maxMembers = maxMembers;
    }

    @Transactional
    public Group createGroup(String name, Long ownerId) {
        userRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Owner not found: " + ownerId));
        Group group = Group.builder().name(name).ownerId(ownerId).build();
        group.getMemberIds().add(ownerId);
        return groupRepository.save(group);
    }

    @Transactional
    public Group addMember(Long groupId, Long requesterId, Long newMemberId) {
        Group group = getGroupOrThrow(groupId);
        assertOwner(group, requesterId);
        if (group.getMemberIds().size() >= maxMembers)
            throw new IllegalStateException("Group has reached the maximum member limit of " + maxMembers);
        if (!userRepository.existsById(newMemberId))
            throw new IllegalArgumentException("User not found: " + newMemberId);
        group.getMemberIds().add(newMemberId);
        return groupRepository.save(group);
    }

    @Transactional
    public void removeMember(Long groupId, Long requesterId, Long memberId) {
        Group group = getGroupOrThrow(groupId);
        assertOwner(group, requesterId);
        group.getMemberIds().remove(memberId);
        groupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public Group getGroup(Long groupId) { return getGroupOrThrow(groupId); }

    @Transactional(readOnly = true)
    public List<Group> getGroupsForUser(Long userId) {
        return groupRepository.findGroupsByMemberId(userId);
    }

    public GroupDTO.GroupResponse toResponse(Group group) {
        GroupDTO.GroupResponse resp = new GroupDTO.GroupResponse();
        resp.setId(group.getId());
        resp.setName(group.getName());
        resp.setOwnerId(group.getOwnerId());
        resp.setMemberCount(group.getMemberIds().size());
        return resp;
    }

    private Group getGroupOrThrow(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
    }

    private void assertOwner(Group group, Long userId) {
        if (!group.getOwnerId().equals(userId))
            throw new SecurityException("Only the group owner can perform this action");
    }
}
