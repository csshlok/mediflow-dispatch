package org.example.matching.service;

// Thrown after a failed attempt has been compensated, so Kafka retries the emergency later
public class DispatchFailedException extends RuntimeException {
    public DispatchFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
