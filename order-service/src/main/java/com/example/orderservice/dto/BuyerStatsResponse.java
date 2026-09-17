package com.example.orderservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record BuyerStatsResponse(
        List<ProductStat> topProducts,
        List<ProductStat> mostBoughtProducts,
        BigDecimal totalSpent,
        int orderCount
) {}
