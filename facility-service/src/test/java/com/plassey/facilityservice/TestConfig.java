package com.plassey.facilityservice;

import com.plassey.facilityservice.infrastructure.messaging.FacilityEventPublisher;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the real event publisher with a mock so integration tests
 * don't require a live RabbitMQ instance.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public FacilityEventPublisher mockEventPublisher() {
        return Mockito.mock(FacilityEventPublisher.class);
    }
}
