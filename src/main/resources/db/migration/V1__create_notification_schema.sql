CREATE TABLE notification_event (
    event_id VARCHAR(16) NOT NULL,
    client_id VARCHAR(16) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    content VARCHAR(512) NOT NULL,
    event_created_at DATETIME(6) NOT NULL,
    delivery_date DATETIME(6) NULL,
    delivery_status ENUM(
        'PENDING', 'DELIVERING', 'COMPLETED', 'RETRY_SCHEDULED',
        'FAILED', 'NOT_SUBSCRIBED', 'SUBSCRIPTION_INACTIVE'
    ) NOT NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id),
    INDEX idx_event_client_status_created (client_id, delivery_status, event_created_at, event_id),
    INDEX idx_event_status_retry (delivery_status, next_retry_at),
    INDEX idx_event_status_updated (delivery_status, updated_at)
);

CREATE TABLE subscription (
    subscription_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    client_id VARCHAR(16) NOT NULL,
    status ENUM('ACTIVE', 'INACTIVE') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (subscription_id),
    UNIQUE KEY uk_subscription_client (client_id)
);
