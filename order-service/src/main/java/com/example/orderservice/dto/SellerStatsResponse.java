package com.example.orderservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record SellerStatsResponse(
        List<ProductStat> bestSellingProducts,
        BigDecimal totalRevenue,
        int orderCount
) {}
