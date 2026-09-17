package com.example.orderservice.service;

import com.example.orderservice.client.ProductClient;
import com.example.orderservice.client.ProductSnapshot;
import com.example.orderservice.dto.CheckoutRequest;
import com.example.orderservice.dto.CheckoutResponse;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.dto.ShippingAddressRequest;
import com.example.orderservice.exception.EmptyCartException;
import com.example.orderservice.exception.OrderConflictException;
import com.example.orderservice.kafka.OrderEventProducer;
import com.example.orderservice.model.Cart;
import com.example.orderservice.model.CartItem;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.model.PaymentStatus;
import com.example.orderservice.model.ShippingAddress;
import com.example.orderservice.model.StatusHistoryEntry;
import com.example.orderservice.repository.CartRepository;
import com.example.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CheckoutService {
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final OrderEventProducer eventProducer;
    private final CartService cartService;

    public CheckoutService(CartRepository cartRepository, OrderRepository orderRepository,
                           ProductClient productClient, OrderEventProducer eventProducer,
                           CartService cartService) {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.eventProducer = eventProducer;
        this.cartService = cartService;
    }

    public CheckoutResponse checkout(String buyerId, String bearerToken, CheckoutRequest request) {
        Cart cart = cartRepository.findByUserId(buyerId).orElseThrow(EmptyCartException::new);
        if (cart.getItems().isEmpty()) throw new EmptyCartException();

        Map<String, ProductSnapshot> products = cart.getItems().stream()
                .map(CartItem::productId)
                .distinct()
                .map(id -> productClient.getProduct(id, bearerToken))
                .collect(Collectors.toMap(ProductSnapshot::id, Function.identity()));

        List<Reservation> reservations = new ArrayList<>();
        try {
            for (CartItem item : cart.getItems()) {
                ProductSnapshot product = products.get(item.productId());
                if (!product.sellerId().equals(item.sellerId())) {
                    throw new OrderConflictException("Product seller changed: " + product.name());
                }
                if (product.stock() == null || product.stock() < item.quantity()) {
                    throw new OrderConflictException("Insufficient stock for product: " + product.name());
                }
                reservations.add(new Reservation(item.productId(), item.quantity()));
                productClient.adjustStock(item.productId(), -item.quantity(), bearerToken);
            }

            String groupId = UUID.randomUUID().toString();
            ShippingAddress address = toAddress(request.shippingAddress());
            List<Order> orders = cart.getItems().stream()
                    .collect(Collectors.groupingBy(CartItem::sellerId, java.util.LinkedHashMap::new, Collectors.toList()))
                    .entrySet().stream()
                    .map(entry -> createOrder(buyerId, groupId, entry.getKey(), entry.getValue(), products,
                            address, request))
                    .map(orderRepository::save)
                    .toList();

            cartService.clear(buyerId);
            orders.forEach(eventProducer::publishCreated);
            return new CheckoutResponse(groupId, orders.stream().map(OrderResponse::from).toList());
        } catch (RuntimeException failure) {
            restoreReservations(reservations, bearerToken);
            throw failure;
        }
    }

    private Order createOrder(String buyerId, String groupId, String sellerId, List<CartItem> items,
                              Map<String, ProductSnapshot> products, ShippingAddress address,
                              CheckoutRequest request) {
        List<OrderItem> snapshots = items.stream().map(item -> {
            ProductSnapshot product = products.get(item.productId());
            String imageId = product.imageIds() == null || product.imageIds().isEmpty()
                    ? null : product.imageIds().get(0);
            return new OrderItem(product.id(), product.name(), product.price(), item.quantity(), imageId);
        }).toList();
        BigDecimal subtotal = snapshots.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant now = Instant.now();
        Order order = new Order();
        order.setBuyerId(buyerId);
        order.setSellerId(sellerId);
        order.setCheckoutGroupId(groupId);
        order.setItems(snapshots);
        order.setSubtotal(subtotal);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod(request.paymentMethod());
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setShippingAddress(address);
        order.setStatusHistory(List.of(new StatusHistoryEntry(OrderStatus.PENDING, now, buyerId)));
        return order;
    }

    private void restoreReservations(List<Reservation> reservations, String bearerToken) {
        for (int i = reservations.size() - 1; i >= 0; i--) {
            Reservation reservation = reservations.get(i);
            productClient.adjustStock(reservation.productId(), reservation.quantity(), bearerToken);
        }
    }

    private ShippingAddress toAddress(ShippingAddressRequest request) {
        return new ShippingAddress(request.line1().trim(), request.city().trim(),
                request.postalCode().trim(), request.country().trim());
    }

    private record Reservation(String productId, int quantity) {}
}
