package com.plassey.facilityservice.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("!test")
@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange.facility:facility.events}")
    private String facilityExchange;

    /* ------------------------------------------------------------------ */
    /* Exchange                                                             */
    /* ------------------------------------------------------------------ */

    @Bean
    public TopicExchange facilityExchange() {
        return ExchangeBuilder.topicExchange(facilityExchange)
                .durable(true)
                .build();
    }

    /* ------------------------------------------------------------------ */
    /* Queues – durable, bound to the exchange with routing-key patterns   */
    /* ------------------------------------------------------------------ */

    @Bean
    public Queue facilityCreatedQueue() {
        return QueueBuilder.durable("facility.created.queue").build();
    }

    @Bean
    public Queue facilityUpdatedQueue() {
        return QueueBuilder.durable("facility.updated.queue").build();
    }

    @Bean
    public Queue facilityStatusChangedQueue() {
        return QueueBuilder.durable("facility.status.changed.queue").build();
    }

    @Bean
    public Queue maintenanceScheduledQueue() {
        return QueueBuilder.durable("facility.maintenance.scheduled.queue").build();
    }

    /* ------------------------------------------------------------------ */
    /* Bindings                                                             */
    /* ------------------------------------------------------------------ */

    @Bean
    public Binding createdBinding(Queue facilityCreatedQueue, TopicExchange facilityExchange) {
        return BindingBuilder.bind(facilityCreatedQueue).to(facilityExchange).with("facility.created");
    }

    @Bean
    public Binding updatedBinding(Queue facilityUpdatedQueue, TopicExchange facilityExchange) {
        return BindingBuilder.bind(facilityUpdatedQueue).to(facilityExchange).with("facility.updated");
    }

    @Bean
    public Binding statusChangedBinding(Queue facilityStatusChangedQueue, TopicExchange facilityExchange) {
        return BindingBuilder.bind(facilityStatusChangedQueue).to(facilityExchange).with("facility.status.changed");
    }

    @Bean
    public Binding maintenanceBinding(Queue maintenanceScheduledQueue, TopicExchange facilityExchange) {
        return BindingBuilder.bind(maintenanceScheduledQueue).to(facilityExchange).with("facility.maintenance.scheduled");
    }

    /* ------------------------------------------------------------------ */
    /* Serialisation                                                        */
    /* ------------------------------------------------------------------ */

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jacksonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter);
        template.setMandatory(true);
        return template;
    }
}
