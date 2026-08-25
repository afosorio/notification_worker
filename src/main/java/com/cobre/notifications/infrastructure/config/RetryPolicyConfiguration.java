package com.cobre.notifications.infrastructure.config;

import com.cobre.notifications.domain.RetryPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RetryPolicyConfiguration {

    @Bean
    RetryPolicy retryPolicy(
            @Value("${notification.retry.max-attempts:3}") int maxAttempts,
            @Value("${notification.retry.initial-delay:PT1S}") Duration initialDelay,
            @Value("${notification.retry.max-delay:PT1M}") Duration maxDelay) {
        return new RetryPolicy(maxAttempts, initialDelay, maxDelay);
    }
}
