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
        return listMine(buyerId, status, null);
    }

    public List<Order> listMine(String buyerId, OrderStatus status, String keyword) {
        return filter(status == null
                ? repository.findByBuyerIdOrderByCreatedAtDesc(buyerId)
                : repository.findByBuyerIdAndStatusOrderByCreatedAtDesc(buyerId, status), keyword);
    }

    public List<Order> listSelling(String sellerId, OrderStatus status) {
        return listSelling(sellerId, status, null);
    }

    public List<Order> listSelling(String sellerId, OrderStatus status, String keyword) {
        return filter(status == null
                ? repository.findBySellerIdOrderByCreatedAtDesc(sellerId)
                : repository.findBySellerIdAndStatusOrderByCreatedAtDesc(sellerId, status), keyword);
    }

    private List<Order> filter(List<Order> orders, String keyword) {
        if (keyword == null || keyword.isBlank()) return orders;
        String term = keyword.trim().toLowerCase(java.util.Locale.ROOT);
        return orders.stream().filter(order ->
                contains(order.getId(), term)
                || contains(order.getBuyerId(), term)
                || order.getItems().stream().anyMatch(item -> contains(item.name(), term)))
                .toList();
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(term);
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
