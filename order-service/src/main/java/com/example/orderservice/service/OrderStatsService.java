package com.example.orderservice.service;

import com.example.orderservice.dto.BuyerStatsResponse;
import com.example.orderservice.dto.ProductStat;
import com.example.orderservice.dto.SellerStatsResponse;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Profile statistics computed from the caller's own non-cancelled orders.
 *
 * Grouping happens here in Java over the indexed per-user query rather than in
 * a Mongo aggregation pipeline: a single user's order history is small, the
 * BigDecimal totals stay exact, and the logic is unit-testable without a
 * database, matching the rest of this service's tests.
 */
@Service
public class OrderStatsService {
    static final int TOP_LIMIT = 5;

    private static final Comparator<ProductStat> BY_NAME =
            Comparator.comparing(ProductStat::name, Comparator.nullsLast(Comparator.naturalOrder()));
    private static final Comparator<ProductStat> BY_AMOUNT =
            Comparator.comparing(ProductStat::amount, Comparator.reverseOrder()).thenComparing(BY_NAME);
    private static final Comparator<ProductStat> BY_QUANTITY =
            Comparator.comparingInt(ProductStat::quantity).reversed()
                    .thenComparing(ProductStat::amount, Comparator.reverseOrder())
                    .thenComparing(BY_NAME);

    private final OrderRepository repository;

    public OrderStatsService(OrderRepository repository) {
        this.repository = repository;
    }

    public BuyerStatsResponse buyerStats(String buyerId) {
        List<Order> orders = repository.findByBuyerIdAndStatusNotOrderByCreatedAtDesc(buyerId, OrderStatus.CANCELLED);
        List<ProductStat> products = productTotals(orders);
        return new BuyerStatsResponse(
                top(products, BY_AMOUNT), top(products, BY_QUANTITY), totalOf(orders), orders.size());
    }

    public SellerStatsResponse sellerStats(String sellerId) {
        List<Order> orders = repository.findBySellerIdAndStatusNotOrderByCreatedAtDesc(sellerId, OrderStatus.CANCELLED);
        return new SellerStatsResponse(top(productTotals(orders), BY_QUANTITY), totalOf(orders), orders.size());
    }

    /** Orders arrive newest first, so the first snapshot seen for a product is its latest name and image. */
    private static List<ProductStat> productTotals(List<Order> orders) {
        Map<String, ProductTotal> totals = new LinkedHashMap<>();
        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                totals.computeIfAbsent(item.productId(), id -> new ProductTotal(item)).add(item);
            }
        }
        return totals.values().stream().map(ProductTotal::toStat).toList();
    }

    private static List<ProductStat> top(List<ProductStat> products, Comparator<ProductStat> order) {
        return products.stream().sorted(order).limit(TOP_LIMIT).toList();
    }

    private static BigDecimal totalOf(List<Order> orders) {
        return orders.stream()
                .map(Order::getSubtotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static final class ProductTotal {
        private final String productId;
        private final String name;
        private final String imageId;
        private int quantity;
        private BigDecimal amount = BigDecimal.ZERO;

        ProductTotal(OrderItem latest) {
            this.productId = latest.productId();
            this.name = latest.name();
            this.imageId = latest.imageId();
        }

        ProductTotal add(OrderItem item) {
            quantity += item.quantity();
            amount = amount.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
            return this;
        }

        ProductStat toStat() {
            return new ProductStat(productId, name, imageId, quantity, amount);
        }
    }
}
