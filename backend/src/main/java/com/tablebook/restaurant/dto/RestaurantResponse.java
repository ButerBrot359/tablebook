package com.tablebook.restaurant.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RestaurantResponse(
        Long id,
        String name,
        String slug,
        String description,
        String address,
        String city,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        String organizationSlug,
        OffsetDateTime createdAt
) {}
