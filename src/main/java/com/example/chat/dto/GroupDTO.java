package com.example.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GroupDTO {

    public static class CreateGroupRequest {
        @NotBlank @Size(min = 2, max = 128)
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class AddMemberRequest {
        private Long userId;
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
    }

    public static class GroupResponse {
        private Long id;
        private String name;
        private Long ownerId;
        private int memberCount;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getOwnerId() { return ownerId; }
        public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
        public int getMemberCount() { return memberCount; }
        public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
    }
}
