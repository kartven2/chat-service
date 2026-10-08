package com.example.chat.controller;

import com.example.chat.dto.GroupDTO;
import com.example.chat.security.ChatUserPrincipal;
import com.example.chat.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Group management REST API (stateless per README).
 */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public ResponseEntity<GroupDTO.GroupResponse> createGroup(
            @Valid @RequestBody GroupDTO.CreateGroupRequest req,
            @AuthenticationPrincipal ChatUserPrincipal principal) {
        var group = groupService.createGroup(req.getName(), principal.getUserId());
        return ResponseEntity.ok(groupService.toResponse(group));
    }

    @GetMapping
    public ResponseEntity<List<GroupDTO.GroupResponse>> listMyGroups(
            @AuthenticationPrincipal ChatUserPrincipal principal) {
        var groups = groupService.getGroupsForUser(principal.getUserId())
                .stream().map(groupService::toResponse).toList();
        return ResponseEntity.ok(groups);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupDTO.GroupResponse> getGroup(@PathVariable Long id) {
        return ResponseEntity.ok(groupService.toResponse(groupService.getGroup(id)));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<GroupDTO.GroupResponse> addMember(
            @PathVariable Long id,
            @RequestBody GroupDTO.AddMemberRequest req,
            @AuthenticationPrincipal ChatUserPrincipal principal) {
        var group = groupService.addMember(id, principal.getUserId(), req.getUserId());
        return ResponseEntity.ok(groupService.toResponse(group));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal ChatUserPrincipal principal) {
        groupService.removeMember(id, principal.getUserId(), userId);
        return ResponseEntity.noContent().build();
    }
}
