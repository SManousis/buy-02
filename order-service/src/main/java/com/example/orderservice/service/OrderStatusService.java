package com.example.orderservice.service;

import com.example.orderservice.exception.InvalidTransitionException;
import com.example.orderservice.exception.NotFoundException;
import com.example.orderservice.kafka.OrderEventProducer;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.model.PaymentStatus;
import com.example.orderservice.model.StatusHistoryEntry;
import com.example.orderservice.repository.OrderRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OrderStatusService {
    /** Only forward, one-step-at-a-time transitions are legal; no skipping and no leaving a terminal state. */
    private static final Map<OrderStatus, OrderStatus> NEXT_STATUS = Map.of(
            OrderStatus.PENDING, OrderStatus.CONFIRMED,
            OrderStatus.CONFIRMED, OrderStatus.SHIPPED,
            OrderStatus.SHIPPED, OrderStatus.DELIVERED
    );

    private final OrderRepository repository;
    private final OrderEventProducer eventProducer;

    public OrderStatusService(OrderRepository repository, OrderEventProducer eventProducer) {
        this.repository = repository;
        this.eventProducer = eventProducer;
    }

    public Order updateStatus(String orderId, String sellerId, OrderStatus requestedStatus) {
        Order order = repository.findByIdAndSellerId(orderId, sellerId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));

        OrderStatus current = order.getStatus();
        OrderStatus expectedNext = NEXT_STATUS.get(current);
        if (expectedNext == null || expectedNext != requestedStatus) {
            throw new InvalidTransitionException(
                    "Cannot transition order from " + current + " to " + requestedStatus);
        }

        Instant now = Instant.now();
        order.setStatus(requestedStatus);
        if (requestedStatus == OrderStatus.DELIVERED) {
            order.setPaymentStatus(PaymentStatus.PAID);
        }
        List<StatusHistoryEntry> history = new ArrayList<>(order.getStatusHistory());
        history.add(new StatusHistoryEntry(requestedStatus, now, sellerId));
        order.setStatusHistory(history);

        Order saved = repository.save(order);
        eventProducer.publishStatusChanged(saved);
        return saved;
    }
}
