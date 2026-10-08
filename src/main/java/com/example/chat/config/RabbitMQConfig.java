package com.example.chat.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology:
 *
 *  chat.direct  (DirectExchange)
 *      └── Routing key = "user.{userId}"  → per-user queue
 *          Used for 1-1 messages: sender → queue of target user.
 *
 *  chat.group   (TopicExchange)
 *      └── Routing key = "group.{groupId}.{memberId}"
 *          Fan-out: each group member has an individual inbox queue.
 *
 *  chat.dlx     (DirectExchange)  — dead-letter exchange for undeliverable messages
 */
@Configuration
public class RabbitMQConfig {

    @Value("${chat.rabbitmq.direct-exchange}")
    private String directExchange;

    @Value("${chat.rabbitmq.group-exchange}")
    private String groupExchange;

    @Value("${chat.rabbitmq.dlx-exchange}")
    private String dlxExchange;

    // ---- Exchanges ----

    @Bean
    public DirectExchange chatDirectExchange() {
        return ExchangeBuilder.directExchange(directExchange).durable(true).build();
    }

    @Bean
    public TopicExchange chatGroupExchange() {
        return ExchangeBuilder.topicExchange(groupExchange).durable(true).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(dlxExchange).durable(true).build();
    }

    // ---- JSON converter ----

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
