package com.tablebook.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateRestaurantRequest(
        @NotBlank
        @Size(min = 2, max = 255)
        String name,

        @NotBlank
        @Size(min = 3, max = 100)
        @Pattern(
                regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "must contain only lowercase letters, digits, and hyphens"
        )
        String slug,

        String description,

        @NotBlank
        @Size(max = 500)
        String address,

        @NotBlank
        @Size(max = 100)
        String city,

        @NotBlank
        @Size(min = 2, max = 2)
        String country,

        BigDecimal latitude,

        BigDecimal longitude
) {}
