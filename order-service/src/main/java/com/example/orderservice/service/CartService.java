package com.example.orderservice.service;

import com.example.orderservice.client.ProductClient;
import com.example.orderservice.client.ProductSnapshot;
import com.example.orderservice.dto.CartResponse;
import com.example.orderservice.exception.CartConflictException;
import com.example.orderservice.exception.NotFoundException;
import com.example.orderservice.model.Cart;
import com.example.orderservice.model.CartItem;
import com.example.orderservice.repository.CartRepository;
import java.time.Instant;
import java.util.ArrayList;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class CartService {
    private final CartRepository repository;
    private final ProductClient productClient;

    public CartService(CartRepository repository, ProductClient productClient) {
        this.repository = repository;
        this.productClient = productClient;
    }

    public CartResponse get(String userId) { return CartResponse.from(findOrCreate(userId)); }

    public CartResponse add(String userId, String productId, int quantity) {
        ProductSnapshot product = productClient.getProduct(productId);
        Cart cart = findOrCreate(userId);
        ArrayList<CartItem> items = new ArrayList<>(cart.getItems());
        int existingIndex = indexOf(items, productId);
        int total = quantity + (existingIndex >= 0 ? items.get(existingIndex).quantity() : 0);
        ensureStock(product, total);
        Instant addedAt = existingIndex >= 0 ? items.get(existingIndex).addedAt() : Instant.now();
        CartItem item = new CartItem(product.id(), product.sellerId(), product.name(), total, product.price(), addedAt);
        if (existingIndex >= 0) items.set(existingIndex, item); else items.add(item);
        return save(cart, items);
    }

    public CartResponse update(String userId, String productId, int quantity) {
        Cart cart = findExisting(userId);
        ArrayList<CartItem> items = new ArrayList<>(cart.getItems());
        int index = indexOf(items, productId);
        if (index < 0) throw new NotFoundException("Product is not in the cart: " + productId);
        if (quantity == 0) items.remove(index);
        else {
            ProductSnapshot product = productClient.getProduct(productId);
            ensureStock(product, quantity);
            CartItem old = items.get(index);
            items.set(index, new CartItem(product.id(), product.sellerId(), product.name(), quantity,
                    product.price(), old.addedAt()));
        }
        return save(cart, items);
    }

    public CartResponse remove(String userId, String productId) {
        Cart cart = findExisting(userId);
        ArrayList<CartItem> items = new ArrayList<>(cart.getItems());
        if (!items.removeIf(item -> item.productId().equals(productId)))
            throw new NotFoundException("Product is not in the cart: " + productId);
        return save(cart, items);
    }

    public void clear(String userId) {
        repository.findByUserId(userId).ifPresent(cart -> { cart.setItems(java.util.List.of()); cart.setUpdatedAt(Instant.now()); repository.save(cart); });
    }

    private Cart findOrCreate(String userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            Cart cart = new Cart(userId); cart.setUpdatedAt(Instant.now());
            try { return repository.save(cart); }
            catch (DuplicateKeyException race) { return repository.findByUserId(userId).orElseThrow(() -> race); }
        });
    }
    private Cart findExisting(String userId) { return repository.findByUserId(userId).orElseThrow(() -> new NotFoundException("Cart not found")); }
    private CartResponse save(Cart cart, java.util.List<CartItem> items) { cart.setItems(items); cart.setUpdatedAt(Instant.now()); return CartResponse.from(repository.save(cart)); }
    private int indexOf(java.util.List<CartItem> items, String id) { for (int i=0;i<items.size();i++) if (items.get(i).productId().equals(id)) return i; return -1; }
    private void ensureStock(ProductSnapshot product, int quantity) {
        if (product.stock() == null || product.stock() < quantity) throw new CartConflictException("Insufficient stock for product: " + product.name());
    }
}
