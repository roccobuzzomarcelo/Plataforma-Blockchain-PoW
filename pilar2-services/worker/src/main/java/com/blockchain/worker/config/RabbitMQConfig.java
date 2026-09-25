package com.blockchain.worker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

    // Debe coincidir exactamente con el coordinator
    public static final String MINING_EXCHANGE = "mining.tasks.exchange";
    public static final String TASKS_QUEUE = "mining.tasks";
    public static final String RESULTS_EXCHANGE = "mining.results.exchange";
    public static final String RESULTS_QUEUE = "mining.results";

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public FanoutExchange miningExchange() {
        return new FanoutExchange(MINING_EXCHANGE, true, false);
    }

    // Cola COMPARTIDA entre todas las réplicas de worker (antes era una
    // cola exclusiva y autoDelete por worker, así que cada uno recibía
    // copia de TODA tarea -broadcast, no distribución de trabajo real).
    // Con una sola cola durable y varios consumidores, RabbitMQ reparte
    // cada mensaje a un único consumidor libre.
    @Bean
    public Queue tasksQueue() {
        return QueueBuilder.durable(TASKS_QUEUE).build();
    }

    @Bean
    public Binding miningBinding(Queue tasksQueue, FanoutExchange miningExchange) {
        return BindingBuilder.bind(tasksQueue).to(miningExchange);
    }

    @Bean
    public DirectExchange resultsExchange() {
        return new DirectExchange(RESULTS_EXCHANGE, true, false);
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
        // Clave para que la distribución de chunks sea pareja: sin esto,
        // RabbitMQ puede entregarle varios mensajes sin ACK al mismo
        // consumidor mientras está ocupado minando el primero, dejando a
        // otros workers ociosos con la cola vacía.
        factory.setPrefetchCount(1);
        return factory;
    }
}