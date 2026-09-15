package com.example.orderservice.exception;

public class ProductServiceUnavailableException extends RuntimeException {
    public ProductServiceUnavailableException() { super("Product validation is temporarily unavailable"); }
}
