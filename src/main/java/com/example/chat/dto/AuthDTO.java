package com.example.chat.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDTO {

    public static class SignupRequest {
        @NotBlank @Size(min = 3, max = 64)
        private String username;
        @NotBlank @Email
        private String email;
        @NotBlank @Size(min = 6, max = 128)
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class LoginRequest {
        @NotBlank private String username;
        @NotBlank private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class AuthResponse {
        private final String token;
        private final Long userId;
        private final String username;

        public AuthResponse(String token, Long userId, String username) {
            this.token = token; this.userId = userId; this.username = username;
        }
        public String getToken() { return token; }
        public Long getUserId() { return userId; }
        public String getUsername() { return username; }
    }
}
