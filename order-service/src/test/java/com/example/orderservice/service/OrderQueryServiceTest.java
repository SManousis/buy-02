package com.example.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.orderservice.exception.NotFoundException;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.model.OrderItem;
import java.math.BigDecimal;
import com.example.orderservice.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderQueryServiceTest {
    private OrderRepository repository;
    private OrderQueryService service;

    @BeforeEach void setUp() {
        repository = mock(OrderRepository.class);
        service = new OrderQueryService(repository);
    }

    @Test void listMineWithoutStatusReturnsAllBuyerOrders() {
        Order order = new Order();
        when(repository.findByBuyerIdOrderByCreatedAtDesc("buyer")).thenReturn(List.of(order));
        assertThat(service.listMine("buyer", null)).containsExactly(order);
    }

    @Test void listMineWithStatusFiltersByStatus() {
        Order order = new Order();
        when(repository.findByBuyerIdAndStatusOrderByCreatedAtDesc("buyer", OrderStatus.PENDING))
                .thenReturn(List.of(order));
        assertThat(service.listMine("buyer", OrderStatus.PENDING)).containsExactly(order);
    }

    @Test void listSellingWithoutStatusReturnsAllSellerOrders() {
        Order order = new Order();
        when(repository.findBySellerIdOrderByCreatedAtDesc("seller")).thenReturn(List.of(order));
        assertThat(service.listSelling("seller", null)).containsExactly(order);
    }

    @Test void listSellingWithStatusFiltersByStatus() {
        Order order = new Order();
        when(repository.findBySellerIdAndStatusOrderByCreatedAtDesc("seller", OrderStatus.SHIPPED))
                .thenReturn(List.of(order));
        assertThat(service.listSelling("seller", OrderStatus.SHIPPED)).containsExactly(order);
    }

    @Test void keywordSearchMatchesSnapshottedProductNames() {
        Order matching = new Order();
        matching.setBuyerId("buyer-one");
        matching.setItems(List.of(new OrderItem("p1", "Organic honey", BigDecimal.TEN, 1, null)));
        Order other = new Order();
        other.setBuyerId("buyer-two");
        other.setItems(List.of(new OrderItem("p2", "Olive oil", BigDecimal.TEN, 1, null)));
        when(repository.findBySellerIdOrderByCreatedAtDesc("seller")).thenReturn(List.of(matching, other));

        assertThat(service.listSelling("seller", null, "HONEY")).containsExactly(matching);
    }

    @Test void getForUserReturnsOrderWhenCallerIsBuyer() {
        Order order = new Order();
        when(repository.findByIdAndBuyerId("o1", "buyer")).thenReturn(Optional.of(order));
        assertThat(service.getForUser("o1", "buyer")).isEqualTo(order);
    }

    @Test void getForUserReturnsOrderWhenCallerIsSeller() {
        Order order = new Order();
        when(repository.findByIdAndBuyerId("o1", "seller")).thenReturn(Optional.empty());
        when(repository.findByIdAndSellerId("o1", "seller")).thenReturn(Optional.of(order));
        assertThat(service.getForUser("o1", "seller")).isEqualTo(order);
    }

    @Test void getForUserMasksExistenceForNonOwners() {
        when(repository.findByIdAndBuyerId("o1", "stranger")).thenReturn(Optional.empty());
        when(repository.findByIdAndSellerId("o1", "stranger")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getForUser("o1", "stranger"))
                .isInstanceOf(NotFoundException.class);
    }
}
