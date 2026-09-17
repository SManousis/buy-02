package com.example.userservice.dto;

import com.example.userservice.model.User;

public record SellerSummaryResponse(String id, String username) {
    public static SellerSummaryResponse from(User user) {
        return new SellerSummaryResponse(user.getId(), user.getUsername());
    }
}
