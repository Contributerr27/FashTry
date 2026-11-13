package com.fashtry.backend.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String TASK_EXCHANGE = "ai.tryon.exchange";
    public static final String TASK_QUEUE = "ai.tryon.poll.queue";
    public static final String TASK_ROUTING_KEY = "ai.tryon.poll";

    @Bean
    public DirectExchange tryOnExchange() {
        return new DirectExchange(TASK_EXCHANGE, true, false);
    }

    @Bean
    public Queue tryOnQueue() {
        return new Queue(TASK_QUEUE, true);
    }

    @Bean
    public Binding tryOnBinding(Queue tryOnQueue, DirectExchange tryOnExchange) {
        return BindingBuilder.bind(tryOnQueue).to(tryOnExchange).with(TASK_ROUTING_KEY);
    }
}
