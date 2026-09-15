package com.example.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.orderservice.client.ProductClient;
import com.example.orderservice.client.ProductSnapshot;
import com.example.orderservice.exception.CartConflictException;
import com.example.orderservice.exception.NotFoundException;
import com.example.orderservice.model.Cart;
import com.example.orderservice.model.CartItem;
import com.example.orderservice.repository.CartRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CartServiceTest {
    private CartRepository repository;
    private ProductClient products;
    private CartService service;
    private final ProductSnapshot product = new ProductSnapshot("p1", "seller", "Keyboard", new BigDecimal("25.00"), 5);

    @BeforeEach void setUp() {
        repository = mock(CartRepository.class); products = mock(ProductClient.class);
        service = new CartService(repository, products);
        when(repository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(products.getProduct("p1")).thenReturn(product);
    }

    @Test void createsPersistentEmptyCartOnFirstRead() {
        when(repository.findByUserId("buyer")).thenReturn(Optional.empty());
        assertThat(service.get("buyer").items()).isEmpty();
        verify(repository).save(any(Cart.class));
    }

    @Test void mergesQuantitiesForTheSameProduct() {
        Cart cart = cartWith(2); when(repository.findByUserId("buyer")).thenReturn(Optional.of(cart));
        var response = service.add("buyer", "p1", 2);
        assertThat(response.items()).singleElement().extracting(CartItem::quantity).isEqualTo(4);
        assertThat(response.subtotal()).isEqualByComparingTo("100.00");
    }

    @Test void rejectsQuantityAboveCurrentStock() {
        when(repository.findByUserId("buyer")).thenReturn(Optional.of(new Cart("buyer")));
        assertThatThrownBy(() -> service.add("buyer", "p1", 6)).isInstanceOf(CartConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test void updateToZeroRemovesItemWithoutProductLookup() {
        when(repository.findByUserId("buyer")).thenReturn(Optional.of(cartWith(1)));
        assertThat(service.update("buyer", "p1", 0).items()).isEmpty();
        verifyNoInteractions(products);
    }

    @Test void removingUnknownItemReturnsNotFound() {
        when(repository.findByUserId("buyer")).thenReturn(Optional.of(new Cart("buyer")));
        assertThatThrownBy(() -> service.remove("buyer", "missing")).isInstanceOf(NotFoundException.class);
    }

    @Test void clearIsIdempotentWhenCartDoesNotExist() {
        when(repository.findByUserId("buyer")).thenReturn(Optional.empty());
        service.clear("buyer");
        verify(repository, never()).save(any());
    }

    private Cart cartWith(int quantity) {
        Cart cart = new Cart("buyer");
        cart.setItems(List.of(new CartItem("p1", "seller", "Keyboard", quantity,
                new BigDecimal("25.00"), Instant.parse("2026-01-01T00:00:00Z"))));
        return cart;
    }
}
