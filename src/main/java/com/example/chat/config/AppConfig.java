package com.example.chat.config;

import com.example.chat.service.ChatUserDetailsService;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Application-level bean definitions.
 */
@Configuration
public class AppConfig {

    /**
     * RabbitAdmin — used by ChatService to dynamically declare queues and bindings.
     */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    /**
     * Named beans for the @RabbitListener SpEL references (#{@directQueueName}, #{@groupQueueName}).
     */
    @Bean
    public String directQueueName(@Value("${chat.server.instance-id}") String instanceId) {
        return "direct.server." + instanceId;
    }

    @Bean
    public String groupQueueName(@Value("${chat.server.instance-id}") String instanceId) {
        return "group.server." + instanceId;
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            ChatUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }
}
