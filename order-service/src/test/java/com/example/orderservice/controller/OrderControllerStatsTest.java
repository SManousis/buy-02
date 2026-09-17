package com.example.orderservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.orderservice.dto.BuyerStatsResponse;
import com.example.orderservice.dto.SellerStatsResponse;
import com.example.orderservice.service.CheckoutService;
import com.example.orderservice.service.OrderCancellationService;
import com.example.orderservice.service.OrderQueryService;
import com.example.orderservice.service.OrderStatsService;
import com.example.orderservice.service.OrderStatusService;
import com.example.orderservice.service.ReorderService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

class OrderControllerStatsTest {
    private OrderStatsService statsService;
    private OrderController controller;

    @BeforeEach void setUp() {
        statsService = mock(OrderStatsService.class);
        controller = new OrderController(mock(CheckoutService.class), mock(OrderQueryService.class),
                mock(OrderStatusService.class), mock(OrderCancellationService.class),
                mock(ReorderService.class), statsService);
    }

    private static Jwt jwt(String subject, String role) {
        return Jwt.withTokenValue("token").header("alg", "HS256").subject(subject).claim("role", role).build();
    }

    @Test void myStatsAreScopedToTheTokenSubject() {
        BuyerStatsResponse expected = new BuyerStatsResponse(List.of(), List.of(), BigDecimal.ZERO, 0);
        when(statsService.buyerStats("buyer-1")).thenReturn(expected);

        assertThat(controller.myStats(jwt("buyer-1", "CLIENT"))).isEqualTo(expected);
    }

    @Test void sellingStatsAreScopedToTheSellerSubject() {
        SellerStatsResponse expected = new SellerStatsResponse(List.of(), BigDecimal.TEN, 1);
        when(statsService.sellerStats("seller-1")).thenReturn(expected);

        assertThat(controller.sellingStats(jwt("seller-1", "SELLER"))).isEqualTo(expected);
    }

    @Test void sellingStatsRejectNonSellers() {
        Jwt client = jwt("buyer-1", "CLIENT");

        assertThatThrownBy(() -> controller.sellingStats(client)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(statsService);
    }
}
