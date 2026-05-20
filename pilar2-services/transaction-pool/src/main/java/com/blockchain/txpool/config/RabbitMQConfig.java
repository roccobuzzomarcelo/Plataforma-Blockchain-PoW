package com.blockchain.txpool.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Exchange tipo topic para distribuir tareas a workers
    public static final String MINING_EXCHANGE = "mining.exchange";

    // Queue para recibir resultados del coordinator
    public static final String TASK_ROUTING_KEY = "mining.task";

    @Bean
    public TopicExchange miningExchange() {
        return new TopicExchange(MINING_EXCHANGE, true, false);
    }
}