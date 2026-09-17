package com.example.orderservice.service;

import com.example.orderservice.exception.NotFoundException;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderQueryService {
    private final OrderRepository repository;

    public OrderQueryService(OrderRepository repository) {
        this.repository = repository;
    }

    public List<Order> listMine(String buyerId, OrderStatus status) {
        return status == null
                ? repository.findByBuyerIdOrderByCreatedAtDesc(buyerId)
                : repository.findByBuyerIdAndStatusOrderByCreatedAtDesc(buyerId, status);
    }

    public List<Order> listSelling(String sellerId, OrderStatus status) {
        return status == null
                ? repository.findBySellerIdOrderByCreatedAtDesc(sellerId)
                : repository.findBySellerIdAndStatusOrderByCreatedAtDesc(sellerId, status);
    }

    /**
     * Returns the order only if the given user is its buyer or its seller.
     * Any other caller receives a 404 (existence masking), never a 403.
     */
    public Order getForUser(String id, String userId) {
        return repository.findByIdAndBuyerId(id, userId)
                .or(() -> repository.findByIdAndSellerId(id, userId))
                .orElseThrow(() -> new NotFoundException("Order not found: " + id));
    }
}
