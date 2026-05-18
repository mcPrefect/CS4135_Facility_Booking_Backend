package com.facilitybooking.bookingservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Booking Service to consume facility status change events
 * from Eryk Marcinkowski's Facility Service (facility.events exchange).
 */
@Configuration
public class FacilityEventConfig {

    @Bean
    public Queue facilityStatusQueue() {
        return QueueBuilder.durable("booking.facility.status.queue").build();
    }

    @Bean
    public TopicExchange facilityExchange() {
        return new TopicExchange("facility.events", true, false);
    }

    @Bean
    public Binding facilityStatusBinding(Queue facilityStatusQueue,
                                          TopicExchange facilityExchange) {
        return BindingBuilder
                .bind(facilityStatusQueue)
                .to(facilityExchange)
                .with("facility.status.changed");
    }
}
