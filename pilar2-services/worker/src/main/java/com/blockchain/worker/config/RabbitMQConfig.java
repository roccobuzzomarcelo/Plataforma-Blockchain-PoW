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

@Configuration
public class RabbitMQConfig {

    // Debe coincidir exactamente con el coordinator
    public static final String MINING_EXCHANGE = "mining.tasks.exchange";
    public static final String RESULTS_EXCHANGE = "mining.results.exchange";
    public static final String RESULTS_QUEUE = "mining.results";

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    // Queue exclusiva por worker: se crea y destruye con la conexión
    @Bean
    public Queue miningQueue() {
        return QueueBuilder.nonDurable()
                .exclusive()
                .autoDelete()
                .build();
    }

    @Bean
    public FanoutExchange miningExchange() {
        return new FanoutExchange(MINING_EXCHANGE, true, false);
    }

    // Cada worker se vincula a su propia queue exclusiva en el fanout
    @Bean
    public Binding miningBinding(Queue miningQueue, FanoutExchange miningExchange) {
        return BindingBuilder.bind(miningQueue).to(miningExchange);
    }

    @Bean
    public DirectExchange resultsExchange() {
        return new DirectExchange(RESULTS_EXCHANGE, true, false);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
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