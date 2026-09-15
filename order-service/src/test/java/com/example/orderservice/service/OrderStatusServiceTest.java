package com.example.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.orderservice.exception.InvalidTransitionException;
import com.example.orderservice.exception.NotFoundException;
import com.example.orderservice.kafka.OrderEventProducer;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.model.PaymentStatus;
import com.example.orderservice.repository.OrderRepository;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OrderStatusServiceTest {
    private OrderRepository repository;
    private OrderEventProducer eventProducer;
    private OrderStatusService service;

    @BeforeEach void setUp() {
        repository = mock(OrderRepository.class);
        eventProducer = mock(OrderEventProducer.class);
        service = new OrderStatusService(repository, eventProducer);
        when(repository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Stream<Arguments> legalTransitions() {
        return Stream.of(
                Arguments.of(OrderStatus.PENDING, OrderStatus.CONFIRMED),
                Arguments.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED),
                Arguments.of(OrderStatus.SHIPPED, OrderStatus.DELIVERED));
    }

    @ParameterizedTest
    @MethodSource("legalTransitions")
    void allowsLegalForwardTransitions(OrderStatus from, OrderStatus to) {
        Order order = orderWithStatus(from);
        when(repository.findByIdAndSellerId("o1", "seller")).thenReturn(Optional.of(order));

        Order updated = service.updateStatus("o1", "seller", to);

        assertThat(updated.getStatus()).isEqualTo(to);
        assertThat(updated.getStatusHistory()).last().extracting("status").isEqualTo(to);
        verify(eventProducer).publishStatusChanged(updated);
    }

    @Test void deliveringMarksPaymentAsPaid() {
        Order order = orderWithStatus(OrderStatus.SHIPPED);
        when(repository.findByIdAndSellerId("o1", "seller")).thenReturn(Optional.of(order));

        Order updated = service.updateStatus("o1", "seller", OrderStatus.DELIVERED);

        assertThat(updated.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    private static Stream<Arguments> illegalTransitions() {
        return Stream.of(
                Arguments.of(OrderStatus.PENDING, OrderStatus.SHIPPED),
                Arguments.of(OrderStatus.PENDING, OrderStatus.DELIVERED),
                Arguments.of(OrderStatus.CONFIRMED, OrderStatus.PENDING),
                Arguments.of(OrderStatus.CONFIRMED, OrderStatus.DELIVERED),
                Arguments.of(OrderStatus.DELIVERED, OrderStatus.CONFIRMED),
                Arguments.of(OrderStatus.CANCELLED, OrderStatus.CONFIRMED),
                Arguments.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED));
    }

    @ParameterizedTest
    @MethodSource("illegalTransitions")
    void rejectsIllegalTransitions(OrderStatus from, OrderStatus to) {
        Order order = orderWithStatus(from);
        when(repository.findByIdAndSellerId("o1", "seller")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateStatus("o1", "seller", to))
                .isInstanceOf(InvalidTransitionException.class);
        verifyNoInteractions(eventProducer);
    }

    @Test void nonOwningSellerGetsNotFound() {
        when(repository.findByIdAndSellerId("o1", "stranger")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateStatus("o1", "stranger", OrderStatus.CONFIRMED))
                .isInstanceOf(NotFoundException.class);
    }

    private Order orderWithStatus(OrderStatus status) {
        Order order = new Order();
        order.setSellerId("seller");
        order.setStatus(status);
        order.setStatusHistory(java.util.List.of());
        return order;
    }
}
