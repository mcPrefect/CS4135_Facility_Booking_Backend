package com.facilitybooking.userservice.messaging;

import com.facilitybooking.userservice.domain.entity.User;
import com.facilitybooking.userservice.mapper.UserEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UserEventPublisher {

    private static  final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private UserEventMapper userEventMapper;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing.user-registered}")
    private String routingKey;

    public void publishUserRegistered(User user) {
        UserRegisteredEvent userRegisteredEvent = userEventMapper.toEvent(user);

        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, userRegisteredEvent);
        } catch (Exception e) {
            log.warn(
                    "Failed to publish UserRegistered event for userId={}. Continuing without messaging.",
                    user.getId(), e
            );
        }
    }
}

