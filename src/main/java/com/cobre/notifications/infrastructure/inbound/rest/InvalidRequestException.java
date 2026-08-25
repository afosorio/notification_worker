package com.cobre.notifications.infrastructure.inbound.rest;

public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
