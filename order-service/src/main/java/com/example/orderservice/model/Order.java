package com.example.orderservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("orders")
@CompoundIndex(name = "ix_orders_seller_status", def = "{'sellerId': 1, 'status': 1}")
public class Order {
    @Id
    private String id;
    @Indexed
    private String checkoutGroupId;
    @Indexed
    private String buyerId;
    @Indexed
    private String sellerId;
    private List<OrderItem> items = new ArrayList<>();
    private BigDecimal subtotal;
    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private List<StatusHistoryEntry> statusHistory = new ArrayList<>();
    private ShippingAddress shippingAddress;
    @Version
    private Long version;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public Order() {}

    public String getId() { return id; }
    public String getCheckoutGroupId() { return checkoutGroupId; }
    public String getBuyerId() { return buyerId; }
    public String getSellerId() { return sellerId; }
    public List<OrderItem> getItems() { return List.copyOf(items); }
    public BigDecimal getSubtotal() { return subtotal; }
    public OrderStatus getStatus() { return status; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public List<StatusHistoryEntry> getStatusHistory() { return List.copyOf(statusHistory); }
    public ShippingAddress getShippingAddress() { return shippingAddress; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setCheckoutGroupId(String checkoutGroupId) { this.checkoutGroupId = checkoutGroupId; }
    public void setBuyerId(String buyerId) { this.buyerId = buyerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public void setItems(List<OrderItem> items) { this.items = new ArrayList<>(items); }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public void setStatusHistory(List<StatusHistoryEntry> statusHistory) {
        this.statusHistory = new ArrayList<>(statusHistory);
    }
    public void setShippingAddress(ShippingAddress shippingAddress) { this.shippingAddress = shippingAddress; }
}
