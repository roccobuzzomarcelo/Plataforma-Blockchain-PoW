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

    // Exchange fanout: el coordinator publica una vez, la cola
    // compartida de tareas es la única suscripta -ver más abajo-.
    public static final String MINING_EXCHANGE = "mining.tasks.exchange";
    // Cola COMPARTIDA de tareas: todos los workers son consumidores en
    // competencia sobre esta única cola (antes cada worker tenía su
    // propia cola exclusiva vinculada al fanout, y recibía copia de
    // TODA tarea -broadcast-, no una porción. Con una sola cola
    // durable, RabbitMQ reparte cada mensaje a un solo consumidor
    // libre, logrando la fragmentación real que pide P5).
    public static final String TASKS_QUEUE = "mining.tasks";
    // Queue exclusiva por la que el coordinator recibe resultados
    public static final String RESULTS_QUEUE = "mining.results";
    // Exchange direct para resultados
    public static final String RESULTS_EXCHANGE = "mining.results.exchange";

    @Bean
    public FanoutExchange miningExchange() {
        return new FanoutExchange(MINING_EXCHANGE, true, false);
    }

    @Bean
    public Queue tasksQueue() {
        return QueueBuilder.durable(TASKS_QUEUE).build();
    }

    @Bean
    public Binding tasksBinding(Queue tasksQueue, FanoutExchange miningExchange) {
        return BindingBuilder.bind(tasksQueue).to(miningExchange);
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