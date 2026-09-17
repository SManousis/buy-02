package com.example.orderservice.service;

import com.example.orderservice.client.ProductClient;
import com.example.orderservice.exception.NotFoundException;
import com.example.orderservice.exception.OrderConflictException;
import com.example.orderservice.kafka.OrderEventProducer;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.model.StatusHistoryEntry;
import com.example.orderservice.repository.OrderRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderCancellationService {
    private final OrderRepository repository;
    private final ProductClient productClient;
    private final OrderEventProducer eventProducer;

    public OrderCancellationService(OrderRepository repository, ProductClient productClient,
                                     OrderEventProducer eventProducer) {
        this.repository = repository;
        this.productClient = productClient;
        this.eventProducer = eventProducer;
    }

    /**
     * Cancels an order owned by the caller (buyer or seller), restoring stock for every line item
     * before the order is marked CANCELLED.
     *
     * <p>Idempotency: an order already CANCELLED is rejected up front, so a client retrying after a
     * fully successful cancellation cannot trigger a second stock restoration. The one unguarded edge
     * case is a crash between restoring an item's stock and persisting the CANCELLED status: a retry
     * in that window would restore already-restored items a second time. Avoiding that would require a
     * distributed transaction or a saga/idempotency-key mechanism, which is out of scope for this
     * project; this is the same trade-off already accepted by {@link CheckoutService}'s compensating
     * rollback, which is likewise not crash-safe.
     */
    public Order cancel(String orderId, String userId, String bearerToken) {
        Order order = repository.findByIdAndBuyerId(orderId, userId)
                .or(() -> repository.findByIdAndSellerId(orderId, userId))
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderConflictException("Order is already cancelled: " + orderId);
        }
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new OrderConflictException("Cannot cancel a delivered order: " + orderId);
        }

        for (OrderItem item : order.getItems()) {
            productClient.adjustStock(item.productId(), item.quantity(), bearerToken);
        }

        Instant now = Instant.now();
        List<StatusHistoryEntry> history = new ArrayList<>(order.getStatusHistory());
        history.add(new StatusHistoryEntry(OrderStatus.CANCELLED, now, userId));
        order.setStatus(OrderStatus.CANCELLED);
        order.setStatusHistory(history);

        Order saved = repository.save(order);
        eventProducer.publishCancelled(saved);
        return saved;
    }
}
