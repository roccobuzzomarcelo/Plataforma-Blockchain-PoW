package com.blockchain.coordinator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

@Configuration
public class RabbitMQConfig {

    // Exchange fanout: broadcast de tareas a TODOS los workers
    public static final String MINING_EXCHANGE = "mining.tasks.exchange";
    // Queue exclusiva por la que el coordinator recibe resultados
    public static final String RESULTS_QUEUE = "mining.results";
    // Exchange direct para resultados
    public static final String RESULTS_EXCHANGE = "mining.results.exchange";

    @Bean
    public FanoutExchange miningExchange() {
        return new FanoutExchange(MINING_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange resultsExchange() {
        return new DirectExchange(RESULTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue resultsQueue() {
        return QueueBuilder.durable(RESULTS_QUEUE).build();
    }

    @Bean
    public Binding resultsBinding(Queue resultsQueue, DirectExchange resultsExchange) {
        return BindingBuilder.bind(resultsQueue).to(resultsExchange).with(RESULTS_QUEUE);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter(@NonNull ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(@NonNull ConnectionFactory connectionFactory,
            @NonNull Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        return factory;
    }
}