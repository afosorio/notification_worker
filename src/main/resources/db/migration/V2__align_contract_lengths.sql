ALTER TABLE notification_event
    MODIFY event_id VARCHAR(64) NOT NULL,
    MODIFY client_id VARCHAR(64) NOT NULL,
    MODIFY content VARCHAR(1024) NOT NULL;

ALTER TABLE subscription
    MODIFY client_id VARCHAR(64) NOT NULL;
