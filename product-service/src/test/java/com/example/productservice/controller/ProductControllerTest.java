package com.example.productservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.productservice.config.AppProperties;
import com.example.productservice.dto.CreateProductRequest;
import com.example.productservice.dto.ProductResponse;
import com.example.productservice.exception.GlobalExceptionHandler;
import com.example.productservice.exception.MediaServiceUnavailableException;
import com.example.productservice.security.SecurityConfig;
import com.example.productservice.service.ProductService;

@WebMvcTest(ProductController.class)
@Import({ProductController.class, GlobalExceptionHandler.class, SecurityConfig.class,
        ProductControllerTest.TestProperties.class})
@ContextConfiguration(classes = ProductControllerTest.TestApplication.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void authenticateBearerToken() {
        when(jwtDecoder.decode("seller-token")).thenReturn(new Jwt(
                "seller-token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"), Map.of("sub", "seller-1", "role", "SELLER")));
    }

    @Test
    void returnsSafeRetryableResponseWhenMediaValidationIsUnavailable() throws Exception {
        when(productService.updateProduct(eq("seller-1"), eq("Bearer seller-token"), eq("product-1"), any()))
                .thenThrow(new MediaServiceUnavailableException("media service failed", new RuntimeException("down")));

        mockMvc.perform(put("/products/product-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer seller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Media validation is temporarily unavailable"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void acceptsPartialUpdateWithoutOptionalText() throws Exception {
        when(productService.updateProduct(eq("seller-1"), eq("Bearer seller-token"), eq("product-1"), any()))
                .thenReturn(new ProductResponse(
                        "product-1", "seller-1", "Name", "Description",
                        new BigDecimal("10.00"), 1, List.of(), null, null));

        mockMvc.perform(put("/products/product-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer seller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("product-1"));

        verify(productService).updateProduct(
                eq("seller-1"), eq("Bearer seller-token"), eq("product-1"), any());
    }

    @Test
    void acceptsLegacyImageUrlsDuringRollingApiUpgrades() throws Exception {
        when(productService.createProduct(eq("seller-1"), eq("Bearer seller-token"), any()))
                .thenReturn(new ProductResponse(
                        "product-1", "seller-1", "Name", "Description",
                        new BigDecimal("10.00"), 1, List.of("media-legacy"), null, null));

        mockMvc.perform(post("/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer seller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Name",
                                  "description": "Description",
                                  "price": 10.00,
                                  "stock": 1,
                                  "imageUrls": ["media-legacy"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageIds[0]").value("media-legacy"))
                .andExpect(jsonPath("$.imageUrls").doesNotExist());

        ArgumentCaptor<CreateProductRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateProductRequest.class);
        verify(productService).createProduct(
                eq("seller-1"), eq("Bearer seller-token"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().imageIds()).containsExactly("media-legacy");
    }

    @Test
    void deniesAClientFromCreatingAProduct() throws Exception {
        when(jwtDecoder.decode("client-token")).thenReturn(new Jwt(
                "client-token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"), Map.of("sub", "client-1", "role", "CLIENT")));

        mockMvc.perform(post("/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Name",
                                  "description": "Description",
                                  "price": 10.00,
                                  "stock": 1
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(productService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCreateProductRequests")
    void rejectsInvalidCreateRequestsAtTheHttpBoundary(String scenario, String requestBody) throws Exception {
        mockMvc.perform(post("/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer seller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidUpdateProductRequests")
    void rejectsInvalidUpdateRequestsAtTheHttpBoundary(String scenario, String requestBody) throws Exception {
        mockMvc.perform(put("/products/product-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer seller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    private static Stream<Arguments> invalidCreateProductRequests() {
        return Stream.of(
                Arguments.of("blank required name", """
                        {"name": "", "description": "Description", "price": 10.00, "stock": 1}
                        """),
                Arguments.of("oversized required description", """
                        {"name": "Name", "description": "%s", "price": 10.00, "stock": 1}
                        """.formatted("d".repeat(2001))),
                Arguments.of("missing required price", """
                        {"name": "Name", "description": "Description", "stock": 1}
                        """),
                Arguments.of("non-positive price", """
                        {"name": "Name", "description": "Description", "price": 0, "stock": 1}
                        """),
                Arguments.of("negative stock", """
                        {"name": "Name", "description": "Description", "price": 10.00, "stock": -1}
                        """));
    }

    private static Stream<Arguments> invalidUpdateProductRequests() {
        return Stream.of(
                Arguments.of("empty optional name", """
                        {"name": ""}
                        """),
                Arguments.of("whitespace-only optional name", """
                        {"name": "   "}
                        """),
                Arguments.of("unicode whitespace-only optional name", """
                        {"name": "\u2003"}
                        """),
                Arguments.of("whitespace-only optional description", """
                        {"description": "   "}
                        """),
                Arguments.of("unicode whitespace-only optional description", """
                        {"description": "\u2003"}
                        """),
                Arguments.of("oversized optional description", """
                        {"description": "%s"}
                        """.formatted("d".repeat(2001))),
                Arguments.of("non-positive price", """
                        {"price": 0}
                        """),
                Arguments.of("negative stock", """
                        {"stock": -1}
                        """));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestProperties {
        @Bean
        AppProperties appProperties() {
            return new AppProperties(
                    new AppProperties.JwtProperties(
                            "01234567890123456789012345678901", 60_000, "user-service", "buy-01-api"),
                    new AppProperties.CorsProperties(java.util.List.of("http://localhost:4200")),
                    new AppProperties.KafkaProperties(new AppProperties.KafkaProperties.Topics(
                            "product.created", "product.updated", "product.deleted", "image.deleted")),
                    new AppProperties.MediaProperties("media-service"));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {}
}
