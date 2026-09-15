package com.example.orderservice.controller;

import com.example.orderservice.dto.CheckoutRequest;
import com.example.orderservice.dto.CheckoutResponse;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.dto.StatusUpdateRequest;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.service.CheckoutService;
import com.example.orderservice.service.OrderQueryService;
import com.example.orderservice.service.OrderStatusService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final CheckoutService checkoutService;
    private final OrderQueryService queryService;
    private final OrderStatusService statusService;

    public OrderController(CheckoutService checkoutService, OrderQueryService queryService,
                            OrderStatusService statusService) {
        this.checkoutService = checkoutService;
        this.queryService = queryService;
        this.statusService = statusService;
    }

    @PostMapping("/checkout")
    public CheckoutResponse checkout(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @Valid @RequestBody CheckoutRequest request) {
        return checkoutService.checkout(jwt.getSubject(), bearerToken, request);
    }

    @GetMapping("/mine")
    public List<OrderResponse> mine(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(required = false) OrderStatus status) {
        return queryService.listMine(jwt.getSubject(), status).stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/selling")
    public List<OrderResponse> selling(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam(required = false) OrderStatus status) {
        ensureSeller(jwt);
        return queryService.listSelling(jwt.getSubject(), status).stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return OrderResponse.from(queryService.getForUser(id, jwt.getSubject()));
    }

    @PatchMapping("/{id}/status")
    public OrderResponse updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                       @Valid @RequestBody StatusUpdateRequest request) {
        ensureSeller(jwt);
        return OrderResponse.from(statusService.updateStatus(id, jwt.getSubject(), request.status()));
    }

    private void ensureSeller(Jwt jwt) {
        Object role = jwt.getClaims().get("role");
        if (role == null || !"SELLER".equals(role.toString())) {
            throw new AccessDeniedException("Seller role required");
        }
    }
}
