package com.example.orderservice.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("carts")
public class Cart {
    @Id private String id;
    @Indexed(unique = true) private String userId;
    private List<CartItem> items = new ArrayList<>();
    private Instant updatedAt;

    public Cart() {}
    public Cart(String userId) { this.userId = userId; }
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public List<CartItem> getItems() { return items; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setItems(List<CartItem> items) { this.items = new ArrayList<>(items); }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
