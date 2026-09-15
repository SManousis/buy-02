package com.example.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.orderservice.client.ProductClient;
import com.example.orderservice.exception.NotFoundException;
import com.example.orderservice.exception.OrderConflictException;
import com.example.orderservice.exception.ProductServiceUnavailableException;
import com.example.orderservice.kafka.OrderEventProducer;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderCancellationServiceTest {
    private OrderRepository repository;
    private ProductClient productClient;
    private OrderEventProducer eventProducer;
    private OrderCancellationService service;

    @BeforeEach void setUp() {
        repository = mock(OrderRepository.class);
        productClient = mock(ProductClient.class);
        eventProducer = mock(OrderEventProducer.class);
        service = new OrderCancellationService(repository, productClient, eventProducer);
        when(repository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void buyerCanCancelAndStockIsRestoredForEveryItem() {
        Order order = orderWithItems(OrderStatus.CONFIRMED,
                new OrderItem("p1", "Keyboard", new BigDecimal("10.00"), 2, null),
                new OrderItem("p2", "Mouse", new BigDecimal("5.00"), 3, null));
        when(repository.findByIdAndBuyerId("o1", "buyer")).thenReturn(Optional.of(order));

        Order cancelled = service.cancel("o1", "buyer", "Bearer token");

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getStatusHistory()).last().extracting("status").isEqualTo(OrderStatus.CANCELLED);
        verify(productClient).adjustStock("p1", 2, "Bearer token");
        verify(productClient).adjustStock("p2", 3, "Bearer token");
        verify(eventProducer).publishCancelled(cancelled);
    }

    @Test void owningSellerCanCancelWhenNotFoundAsBuyer() {
        Order order = orderWithItems(OrderStatus.PENDING,
                new OrderItem("p1", "Keyboard", new BigDecimal("10.00"), 1, null));
        when(repository.findByIdAndBuyerId("o1", "seller")).thenReturn(Optional.empty());
        when(repository.findByIdAndSellerId("o1", "seller")).thenReturn(Optional.of(order));

        Order cancelled = service.cancel("o1", "seller", "Bearer token");

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test void nonOwnerGetsNotFound() {
        when(repository.findByIdAndBuyerId("o1", "stranger")).thenReturn(Optional.empty());
        when(repository.findByIdAndSellerId("o1", "stranger")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel("o1", "stranger", "Bearer token"))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(productClient, eventProducer);
    }

    @Test void cannotCancelADeliveredOrder() {
        Order order = orderWithItems(OrderStatus.DELIVERED,
                new OrderItem("p1", "Keyboard", new BigDecimal("10.00"), 1, null));
        when(repository.findByIdAndBuyerId("o1", "buyer")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel("o1", "buyer", "Bearer token"))
                .isInstanceOf(OrderConflictException.class);
        verifyNoInteractions(productClient, eventProducer);
    }

    @Test void cancellingAlreadyCancelledOrderIsRejectedIdempotently() {
        Order order = orderWithItems(OrderStatus.CANCELLED,
                new OrderItem("p1", "Keyboard", new BigDecimal("10.00"), 1, null));
        when(repository.findByIdAndBuyerId("o1", "buyer")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel("o1", "buyer", "Bearer token"))
                .isInstanceOf(OrderConflictException.class);
        verifyNoInteractions(productClient, eventProducer);
    }

    @Test void partialStockRestorationFailurePreventsCancellationAndEvent() {
        Order order = orderWithItems(OrderStatus.CONFIRMED,
                new OrderItem("p1", "Keyboard", new BigDecimal("10.00"), 2, null),
                new OrderItem("p2", "Mouse", new BigDecimal("5.00"), 3, null));
        when(repository.findByIdAndBuyerId("o1", "buyer")).thenReturn(Optional.of(order));
        when(productClient.adjustStock(eq("p2"), anyInt(), anyString()))
                .thenThrow(new ProductServiceUnavailableException());

        assertThatThrownBy(() -> service.cancel("o1", "buyer", "Bearer token"))
                .isInstanceOf(ProductServiceUnavailableException.class);

        verify(productClient).adjustStock("p1", 2, "Bearer token");
        verify(productClient, times(1)).adjustStock(eq("p2"), anyInt(), anyString());
        verify(repository, never()).save(any());
        verifyNoInteractions(eventProducer);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    private Order orderWithItems(OrderStatus status, OrderItem... items) {
        Order order = new Order();
        order.setBuyerId("buyer");
        order.setSellerId("seller");
        order.setStatus(status);
        order.setItems(List.of(items));
        order.setStatusHistory(List.of());
        return order;
    }
}
