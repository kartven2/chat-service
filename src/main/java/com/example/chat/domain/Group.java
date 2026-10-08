package com.example.chat.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Group chat channel entity (max 100 members per README).
 */
@Entity
@Table(name = "groups")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "group_members",
            joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "user_id")
    private Set<Long> memberIds = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Group() {}

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Set<Long> getMemberIds() { return memberIds; }
    public Instant getCreatedAt() { return createdAt; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private String name; private Long ownerId;
        public Builder name(String n) { this.name = n; return this; }
        public Builder ownerId(Long o) { this.ownerId = o; return this; }
        public Group build() {
            Group g = new Group();
            g.name = name; g.ownerId = ownerId;
            return g;
        }
    }
}
