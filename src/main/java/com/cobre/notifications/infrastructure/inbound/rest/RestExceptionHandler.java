package com.cobre.notifications.infrastructure.inbound.rest;

import com.cobre.notifications.application.service.NotificationEventNotFoundException;
import com.cobre.notifications.application.service.NotificationEventNotReplayableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler({InvalidRequestException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", exception.getMessage() == null ? "Invalid request" : exception.getMessage()));
    }

    @ExceptionHandler(NotificationEventNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(NotificationEventNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(NotificationEventNotReplayableException.class)
    ResponseEntity<Map<String, String>> unprocessable(NotificationEventNotReplayableException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", exception.getMessage()));
    }
}
