package com.example.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.orderservice.dto.CartResponse;
import com.example.orderservice.dto.ReorderResponse;
import com.example.orderservice.exception.CartConflictException;
import com.example.orderservice.exception.NotFoundException;
import com.example.orderservice.exception.ProductServiceUnavailableException;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReorderServiceTest {
    private OrderRepository orderRepository;
    private CartService cartService;
    private ReorderService service;

    @BeforeEach void setUp() {
        orderRepository = mock(OrderRepository.class);
        cartService = mock(CartService.class);
        service = new ReorderService(orderRepository, cartService);
    }

    @Test void nonOwningBuyerGetsNotFound() {
        when(orderRepository.findByIdAndBuyerId("o1", "stranger")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reorder("o1", "stranger")).isInstanceOf(NotFoundException.class);
        verify(cartService, never()).add(anyString(), anyString(), anyInt());
    }

    @Test void addsAllItemsToCartUsingCurrentQuantities() {
        Order order = orderWithItems(
                new OrderItem("p1", "Keyboard", new BigDecimal("10.00"), 2, null),
                new OrderItem("p2", "Mouse", new BigDecimal("5.00"), 1, null));
        when(orderRepository.findByIdAndBuyerId("o1", "buyer")).thenReturn(Optional.of(order));
        CartResponse afterP1 = new CartResponse("cart", List.of(), BigDecimal.ZERO, Instant.now());
        CartResponse afterP2 = new CartResponse("cart", List.of(), BigDecimal.TEN, Instant.now());
        when(cartService.add("buyer", "p1", 2)).thenReturn(afterP1);
        when(cartService.add("buyer", "p2", 1)).thenReturn(afterP2);

        ReorderResponse response = service.reorder("o1", "buyer");

        assertThat(response.unavailableItems()).isEmpty();
        assertThat(response.cart()).isEqualTo(afterP2);
        verify(cartService).add("buyer", "p1", 2);
        verify(cartService).add("buyer", "p2", 1);
    }

    @Test void reportsRemovedProductAsUnavailableAndKeepsGoing() {
        Order order = orderWithItems(
                new OrderItem("p1", "Discontinued", new BigDecimal("10.00"), 1, null),
                new OrderItem("p2", "Mouse", new BigDecimal("5.00"), 1, null));
        when(orderRepository.findByIdAndBuyerId("o1", "buyer")).thenReturn(Optional.of(order));
        when(cartService.add("buyer", "p1", 1)).thenThrow(new NotFoundException("Product not found: p1"));
        CartResponse afterP2 = new CartResponse("cart", List.of(), BigDecimal.TEN, Instant.now());
        when(cartService.add("buyer", "p2", 1)).thenReturn(afterP2);

        ReorderResponse response = service.reorder("o1", "buyer");

        assertThat(response.unavailableItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.productId()).isEqualTo("p1");
                    assertThat(item.reason()).isEqualTo("Product no longer available");
                });
        assertThat(response.cart()).isEqualTo(afterP2);
    }

    @Test void reportsInsufficientStockAsUnavailable() {
        Order order = orderWithItems(new OrderItem("p1", "Keyboard", new BigDecimal("10.00"), 5, null));
        when(orderRepository.findByIdAndBuyerId("o1", "buyer")).thenReturn(Optional.of(order));
        when(cartService.add("buyer", "p1", 5)).thenThrow(new CartConflictException("Insufficient stock for product: Keyboard"));
        CartResponse currentCart = new CartResponse("cart", List.of(), BigDecimal.ZERO, Instant.now());
        when(cartService.get("buyer")).thenReturn(currentCart);

        ReorderResponse response = service.reorder("o1", "buyer");

        assertThat(response.unavailableItems()).singleElement()
                .satisfies(item -> assertThat(item.reason()).isEqualTo("Insufficient stock"));
        assertThat(response.cart()).isEqualTo(currentCart);
    }

    @Test void reportsProductServiceUnavailability() {
        Order order = orderWithItems(new OrderItem("p1", "Keyboard", new BigDecimal("10.00"), 1, null));
        when(orderRepository.findByIdAndBuyerId("o1", "buyer")).thenReturn(Optional.of(order));
        when(cartService.add("buyer", "p1", 1)).thenThrow(new ProductServiceUnavailableException());
        CartResponse currentCart = new CartResponse("cart", List.of(), BigDecimal.ZERO, Instant.now());
        when(cartService.get("buyer")).thenReturn(currentCart);

        ReorderResponse response = service.reorder("o1", "buyer");

        assertThat(response.unavailableItems()).singleElement()
                .satisfies(item -> assertThat(item.reason()).isEqualTo("Product service unavailable"));
    }

    private Order orderWithItems(OrderItem... items) {
        Order order = new Order();
        order.setBuyerId("buyer");
        order.setItems(List.of(items));
        return order;
    }
}
