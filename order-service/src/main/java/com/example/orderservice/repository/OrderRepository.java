package com.example.orderservice.repository;

import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findByBuyerIdOrderByCreatedAtDesc(String buyerId);
    List<Order> findBySellerIdOrderByCreatedAtDesc(String sellerId);
    List<Order> findByBuyerIdAndStatusOrderByCreatedAtDesc(String buyerId, OrderStatus status);
    List<Order> findBySellerIdAndStatusOrderByCreatedAtDesc(String sellerId, OrderStatus status);
    List<Order> findByBuyerIdAndStatusNotOrderByCreatedAtDesc(String buyerId, OrderStatus status);
    List<Order> findBySellerIdAndStatusNotOrderByCreatedAtDesc(String sellerId, OrderStatus status);
    Optional<Order> findByIdAndBuyerId(String id, String buyerId);
    Optional<Order> findByIdAndSellerId(String id, String sellerId);
}
