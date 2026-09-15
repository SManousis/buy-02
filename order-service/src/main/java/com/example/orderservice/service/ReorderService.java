package com.example.orderservice.service;

import com.example.orderservice.dto.CartResponse;
import com.example.orderservice.dto.ReorderResponse;
import com.example.orderservice.dto.UnavailableItem;
import com.example.orderservice.exception.CartConflictException;
import com.example.orderservice.exception.NotFoundException;
import com.example.orderservice.exception.ProductServiceUnavailableException;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.repository.OrderRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReorderService {
    private final OrderRepository orderRepository;
    private final CartService cartService;

    public ReorderService(OrderRepository orderRepository, CartService cartService) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
    }

    /**
     * Re-adds a past order's items to the caller's current cart using today's product data
     * (current price and stock, never the historical order snapshot). Only the buyer who placed
     * the order may reorder it. Items that are no longer available or out of stock are skipped
     * and reported back instead of failing the whole request.
     */
    public ReorderResponse reorder(String orderId, String buyerId) {
        Order order = orderRepository.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));

        List<UnavailableItem> unavailable = new ArrayList<>();
        CartResponse cart = null;
        for (OrderItem item : order.getItems()) {
            try {
                cart = cartService.add(buyerId, item.productId(), item.quantity());
            } catch (NotFoundException ex) {
                unavailable.add(new UnavailableItem(item.productId(), item.name(), "Product no longer available"));
            } catch (CartConflictException ex) {
                unavailable.add(new UnavailableItem(item.productId(), item.name(), "Insufficient stock"));
            } catch (ProductServiceUnavailableException ex) {
                unavailable.add(new UnavailableItem(item.productId(), item.name(), "Product service unavailable"));
            }
        }
        if (cart == null) cart = cartService.get(buyerId);
        return new ReorderResponse(cart, unavailable);
    }
}
