package com.example.chat.config;

import com.example.chat.security.JwtHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * WebSocket / STOMP configuration.
 *
 * Connection flow (per README):
 *  1. Client connects to /ws — starts as HTTP, gets upgraded to WebSocket.
 *  2. Client subscribes to /user/queue/messages    (1-1 inbox)
 *                      or /topic/group/{groupId}   (group channel)
 *  3. Client sends to  /app/chat.sendDirect        (1-1 send)
 *                  or  /app/chat.sendGroup         (group send)
 *
 * The in-memory broker handles subscriptions. In production this
 * would be replaced with a full RabbitMQ STOMP broker relay.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS();   // SockJS fallback for environments blocking WebSocket
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Destinations starting with /app go to @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");

        // /user/queue/** → per-user inbox (1-1 messages)
        // /topic/**      → broadcast topics (group messages)
        registry.enableSimpleBroker("/topic", "/user");

        // Used by SimpMessagingTemplate.convertAndSendToUser()
        registry.setUserDestinationPrefix("/user");
    }
}
