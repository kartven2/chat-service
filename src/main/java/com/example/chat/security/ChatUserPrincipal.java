package com.example.chat.security;

import java.security.Principal;

/**
 * Lightweight principal stored in the SecurityContext.
 * userId and username are decoded directly from JWT claims — no DB lookup.
 */
public class ChatUserPrincipal implements Principal {

    private final Long userId;
    private final String username;

    public ChatUserPrincipal(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }

    @Override
    public String getName() { return username; }
}
