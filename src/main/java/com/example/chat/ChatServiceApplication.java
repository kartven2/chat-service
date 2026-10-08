package com.example.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the scalable chat service.
 *
 * Architecture:
 *  - Stateless services: Auth, User profile, Group management, Service discovery (Zookeeper)
 *  - Stateful service:   Chat server (WebSocket / STOMP)
 *  - 3rd-party stub:     Push notification (FCM / APN simulated)
 */
@SpringBootApplication
@EnableScheduling
public class ChatServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatServiceApplication.class, args);
    }
}
