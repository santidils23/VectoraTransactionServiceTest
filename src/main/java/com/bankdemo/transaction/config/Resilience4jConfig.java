package com.bankdemo.transaction.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.function.Consumer;

@Configuration
public class Resilience4jConfig {

    private static final Logger logger = LoggerFactory.getLogger(Resilience4jConfig.class);

    @Bean
    public CircuitBreakerConfig defaultCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .build();
    }

    @Bean
    public CircuitBreakerConfig accountServiceCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(5)
                .failureRateThreshold(40)
                .waitDurationInOpenState(Duration.ofSeconds(20))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();
    }

    @Bean
    public TimeLimiterConfig defaultTimeLimiterConfig() {
        return TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .build();
    }

    @Bean
    public TimeLimiterConfig accountServiceTimeLimiterConfig() {
        return TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(2))
                .build();
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(
            CircuitBreakerConfig defaultCircuitBreakerConfig,
            CircuitBreakerConfig accountServiceCircuitBreakerConfig,
            RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer) {

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultCircuitBreakerConfig, circuitBreakerEventConsumer);

        // Registrar configuración personalizada para accountService
        registry.addConfiguration("accountService", accountServiceCircuitBreakerConfig);

        return registry;
    }

    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer() {
        return new RegistryEventConsumer<CircuitBreaker>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
                entryAddedEvent.getAddedEntry().getEventPublisher()
                        .onSuccess(event -> logger.info("Call success via circuit breaker: {}", event.getCircuitBreakerName()))
                        .onError(event -> logger.error("Call failed via circuit breaker: {}", event.getCircuitBreakerName()))
                        .onStateTransition(event -> logger.info("Circuit breaker {} state changed from {} to {}",
                                event.getCircuitBreakerName(), event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemoveEvent) {
                // No action needed
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
                // No action needed
            }
        };
    }
}