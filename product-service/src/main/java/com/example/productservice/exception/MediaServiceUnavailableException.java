package com.example.productservice.exception;

public class MediaServiceUnavailableException extends RuntimeException {
    public MediaServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
