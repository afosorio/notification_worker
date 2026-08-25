package com.cobre.notifications.application.port.out;

import com.cobre.notifications.domain.Subscription;

import java.util.Optional;

public interface SubscriptionRepository {

    Optional<Subscription> findByClientId(String clientId);
}
