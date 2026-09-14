package com.example.productservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
public record UpdateProductRequest(
        @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "Name must not be blank")
        @Size(min = 1, max = 120, message = "Name must be 1..120 characters")
        String name,

        @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "Description must not be blank")
        @Size(min = 1, max = 2000, message = "Description must be 1..2000 characters")
        String description,

        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        BigDecimal price,

        @PositiveOrZero(message = "Stock must be 0 or greater")
        Integer stock,

        @JsonAlias("imageUrls")
        List<String> imageIds
) {}
