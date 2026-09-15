package com.example.orderservice.controller;

import com.example.orderservice.dto.AddCartItemRequest;
import com.example.orderservice.dto.CartResponse;
import com.example.orderservice.dto.UpdateCartItemRequest;
import com.example.orderservice.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
public class CartController {
    private final CartService service;
    public CartController(CartService service) { this.service = service; }
    @GetMapping public CartResponse get(@AuthenticationPrincipal Jwt jwt) { return service.get(jwt.getSubject()); }
    @PostMapping("/items") public CartResponse add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddCartItemRequest request) { return service.add(jwt.getSubject(), request.productId(), request.quantity()); }
    @PutMapping("/items/{productId}") public CartResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String productId, @Valid @RequestBody UpdateCartItemRequest request) { return service.update(jwt.getSubject(), productId, request.quantity()); }
    @DeleteMapping("/items/{productId}") public CartResponse remove(@AuthenticationPrincipal Jwt jwt, @PathVariable String productId) { return service.remove(jwt.getSubject(), productId); }
    @DeleteMapping public ResponseEntity<Void> clear(@AuthenticationPrincipal Jwt jwt) { service.clear(jwt.getSubject()); return ResponseEntity.noContent().build(); }
}
