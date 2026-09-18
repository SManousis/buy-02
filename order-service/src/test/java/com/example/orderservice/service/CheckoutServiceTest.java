package com.example.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.orderservice.client.ProductClient;
import com.example.orderservice.client.ProductSnapshot;
import com.example.orderservice.dto.CheckoutRequest;
import com.example.orderservice.dto.ShippingAddressRequest;
import com.example.orderservice.exception.EmptyCartException;
import com.example.orderservice.exception.OrderConflictException;
import com.example.orderservice.kafka.OrderEventProducer;
import com.example.orderservice.model.Cart;
import com.example.orderservice.model.CartItem;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.PaymentMethod;
import com.example.orderservice.repository.CartRepository;
import com.example.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {
    @Mock CartRepository cartRepository;
    @Mock OrderRepository orderRepository;
    @Mock ProductClient productClient;
    @Mock OrderEventProducer eventProducer;
    @Mock CartService cartService;
    private CheckoutService service;

    @BeforeEach
    void setUp() {
        service = new CheckoutService(cartRepository, orderRepository, productClient, eventProducer, cartService);
    }

    @Test
    void checksOutAndSplitsOrdersBySeller() {
        Cart cart = cart(
                item("p1", "seller-a", 2),
                item("p2", "seller-b", 1));
        when(cartRepository.findByUserId("buyer")).thenReturn(Optional.of(cart));
        when(productClient.getProduct("p1", "Bearer token")).thenReturn(
                new ProductSnapshot("p1", "seller-a", "Olive oil", new BigDecimal("10.00"), 5,
                        List.of("image-1")));
        when(productClient.getProduct("p2", "Bearer token")).thenReturn(
                new ProductSnapshot("p2", "seller-b", "Honey", new BigDecimal("5.00"), 3, List.of()));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.checkout("buyer", "Bearer token", request());

        assertThat(response.checkoutGroupId()).isNotBlank();
        assertThat(response.orders()).hasSize(2);
        assertThat(response.orders()).extracting("subtotal")
                .containsExactly(new BigDecimal("20.00"), new BigDecimal("5.00"));
        verify(productClient).adjustStock("p1", -2, "Bearer token");
        verify(productClient).adjustStock("p2", -1, "Bearer token");
        verify(cartService).clear("buyer");
        verify(eventProducer, org.mockito.Mockito.times(2)).publishCreated(any(Order.class));
    }

    @Test
    void rejectsAnEmptyCart() {
        when(cartRepository.findByUserId("buyer")).thenReturn(Optional.of(new Cart("buyer")));

        assertThatThrownBy(() -> service.checkout("buyer", "Bearer token", request()))
                .isInstanceOf(EmptyCartException.class);

        verify(productClient, never()).getProduct(any(), any());
    }

    @Test
    void restoresReservedStockWhenALaterProductConflicts() {
        Cart cart = cart(item("p1", "seller-a", 2), item("p2", "seller-b", 4));
        when(cartRepository.findByUserId("buyer")).thenReturn(Optional.of(cart));
        when(productClient.getProduct("p1", "Bearer token")).thenReturn(
                new ProductSnapshot("p1", "seller-a", "Oil", BigDecimal.TEN, 5));
        when(productClient.getProduct("p2", "Bearer token")).thenReturn(
                new ProductSnapshot("p2", "seller-b", "Honey", BigDecimal.ONE, 1));

        assertThatThrownBy(() -> service.checkout("buyer", "Bearer token", request()))
                .isInstanceOf(OrderConflictException.class)
                .hasMessageContaining("Insufficient stock");

        var ordered = inOrder(productClient);
        ordered.verify(productClient).adjustStock("p1", -2, "Bearer token");
        ordered.verify(productClient).adjustStock("p1", 2, "Bearer token");
        verify(orderRepository, never()).save(any());
    }

    private Cart cart(CartItem... items) {
        Cart cart = new Cart("buyer");
        cart.setItems(List.of(items));
        return cart;
    }

    private CartItem item(String productId, String sellerId, int quantity) {
        return new CartItem(productId, sellerId, productId, quantity, BigDecimal.ONE, Instant.now());
    }

    private CheckoutRequest request() {
        return new CheckoutRequest(new ShippingAddressRequest(" 1 Main St ", " Athens ", " 10000 ", " GR "),
                PaymentMethod.CASH_ON_DELIVERY);
    }
}
