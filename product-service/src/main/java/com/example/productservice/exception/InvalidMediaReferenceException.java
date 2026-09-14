package com.example.productservice.exception;

public class InvalidMediaReferenceException extends RuntimeException {
    public InvalidMediaReferenceException(String message) {
        super(message);
    }

    public InvalidMediaReferenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
