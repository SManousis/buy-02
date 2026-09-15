package com.example.orderservice.exception;

public class CartConflictException extends RuntimeException {
    public CartConflictException(String message) { super(message); }
}
