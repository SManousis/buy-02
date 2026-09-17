package com.example.orderservice.exception;

public class EmptyCartException extends RuntimeException {
    public EmptyCartException() {
        super("Cannot check out an empty cart");
    }
}
