package com.example.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.orderservice.dto.BuyerStatsResponse;
import com.example.orderservice.dto.ProductStat;
import com.example.orderservice.dto.SellerStatsResponse;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderStatsServiceTest {
    private OrderRepository repository;
    private OrderStatsService service;

    @BeforeEach void setUp() {
        repository = mock(OrderRepository.class);
        service = new OrderStatsService(repository);
    }

    private static OrderItem item(String productId, String name, String unitPrice, int quantity) {
        return new OrderItem(productId, name, new BigDecimal(unitPrice), quantity, "img-" + productId);
    }

    private static Order order(OrderItem... items) {
        Order order = new Order();
        order.setItems(List.of(items));
        order.setSubtotal(List.of(items).stream()
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return order;
    }

    private void buyerOrders(List<Order> orders) {
        when(repository.findByBuyerIdAndStatusNotOrderByCreatedAtDesc("buyer", OrderStatus.CANCELLED))
                .thenReturn(orders);
    }

    private void sellerOrders(List<Order> orders) {
        when(repository.findBySellerIdAndStatusNotOrderByCreatedAtDesc("seller", OrderStatus.CANCELLED))
                .thenReturn(orders);
    }

    private static List<String> ids(List<ProductStat> stats) {
        return stats.stream().map(ProductStat::productId).toList();
    }

    @Test void buyerWithNoOrdersGetsZeroTotalsAndEmptyLists() {
        buyerOrders(List.of());

        BuyerStatsResponse stats = service.buyerStats("buyer");

        assertThat(stats.totalSpent()).isEqualByComparingTo("0");
        assertThat(stats.orderCount()).isZero();
        assertThat(stats.topProducts()).isEmpty();
        assertThat(stats.mostBoughtProducts()).isEmpty();
    }

    @Test void sellerWithNoOrdersGetsZeroTotalsAndEmptyList() {
        sellerOrders(List.of());

        SellerStatsResponse stats = service.sellerStats("seller");

        assertThat(stats.totalRevenue()).isEqualByComparingTo("0");
        assertThat(stats.orderCount()).isZero();
        assertThat(stats.bestSellingProducts()).isEmpty();
    }

    @Test void buyerTotalsMergeProductsAcrossOrders() {
        // Only non-cancelled orders are returned by the repository query the service calls.
        buyerOrders(List.of(
                order(item("p2", "Honey", "25.00", 1)),
                order(item("p1", "Olive oil", "10.00", 3), item("p3", "Salt", "5.00", 1))));

        BuyerStatsResponse stats = service.buyerStats("buyer");

        assertThat(stats.totalSpent()).isEqualByComparingTo("60.00");
        assertThat(stats.orderCount()).isEqualTo(2);
        assertThat(stats.topProducts()).containsExactly(
                new ProductStat("p1", "Olive oil", "img-p1", 3, new BigDecimal("30.00")),
                new ProductStat("p2", "Honey", "img-p2", 1, new BigDecimal("25.00")),
                new ProductStat("p3", "Salt", "img-p3", 1, new BigDecimal("5.00")));
    }

    @Test void topProductsRankBySpendWhileMostBoughtRanksByQuantity() {
        buyerOrders(List.of(order(item("cheap", "Pens", "1.00", 10), item("pricey", "Lamp", "50.00", 1))));

        BuyerStatsResponse stats = service.buyerStats("buyer");

        assertThat(ids(stats.topProducts())).containsExactly("pricey", "cheap");
        assertThat(ids(stats.mostBoughtProducts())).containsExactly("cheap", "pricey");
    }

    @Test void equalQuantitiesAreOrderedByAmountThenName() {
        buyerOrders(List.of(order(
                item("b", "Bread", "2.00", 1),
                item("a", "Apple", "2.00", 1),
                item("c", "Cheese", "9.00", 1))));

        BuyerStatsResponse stats = service.buyerStats("buyer");

        assertThat(ids(stats.mostBoughtProducts())).containsExactly("c", "a", "b");
        assertThat(ids(stats.topProducts())).containsExactly("c", "a", "b");
    }

    @Test void listsAreLimitedToTheTopFiveProducts() {
        List<OrderItem> items = new ArrayList<>();
        IntStream.rangeClosed(1, 7).forEach(i -> items.add(item("p" + i, "Product " + i, "1.00", i)));
        buyerOrders(List.of(order(items.toArray(OrderItem[]::new))));

        BuyerStatsResponse stats = service.buyerStats("buyer");

        assertThat(ids(stats.mostBoughtProducts())).containsExactly("p7", "p6", "p5", "p4", "p3");
        assertThat(stats.topProducts()).hasSize(OrderStatsService.TOP_LIMIT);
    }

    @Test void theMostRecentSnapshotNameIsShownAfterARename() {
        // Repository returns newest first.
        buyerOrders(List.of(
                order(item("p1", "Extra virgin olive oil", "12.00", 1)),
                order(item("p1", "Olive oil", "10.00", 2))));

        ProductStat stat = service.buyerStats("buyer").topProducts().get(0);

        assertThat(stat.name()).isEqualTo("Extra virgin olive oil");
        assertThat(stat.quantity()).isEqualTo(3);
        assertThat(stat.amount()).isEqualByComparingTo("32.00");
    }

    @Test void amountsStayExactForDecimalPrices() {
        buyerOrders(List.of(order(item("p1", "Wine", "19.99", 3)), order(item("p1", "Wine", "0.10", 1))));

        BuyerStatsResponse stats = service.buyerStats("buyer");

        assertThat(stats.topProducts().get(0).amount()).isEqualByComparingTo("60.07");
        assertThat(stats.totalSpent()).isEqualByComparingTo("60.07");
    }

    @Test void orderWithoutSubtotalDoesNotBreakTheTotal() {
        Order legacy = new Order();
        legacy.setItems(List.of(item("p1", "Olive oil", "10.00", 1)));
        buyerOrders(List.of(legacy, order(item("p2", "Honey", "25.00", 1))));

        assertThat(service.buyerStats("buyer").totalSpent()).isEqualByComparingTo("25.00");
    }

    @Test void sellerStatsRankBestSellersByQuantityWithRevenue() {
        sellerOrders(List.of(
                order(item("p1", "Olive oil", "10.00", 1)),
                order(item("p2", "Honey", "25.00", 1)),
                order(item("p1", "Olive oil", "10.00", 3))));

        SellerStatsResponse stats = service.sellerStats("seller");

        assertThat(stats.totalRevenue()).isEqualByComparingTo("65.00");
        assertThat(stats.orderCount()).isEqualTo(3);
        assertThat(stats.bestSellingProducts()).containsExactly(
                new ProductStat("p1", "Olive oil", "img-p1", 4, new BigDecimal("40.00")),
                new ProductStat("p2", "Honey", "img-p2", 1, new BigDecimal("25.00")));
    }
}
