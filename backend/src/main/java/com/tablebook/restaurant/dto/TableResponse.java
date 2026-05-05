package com.tablebook.restaurant.dto;

public record TableResponse(
        Long id,
        String label,
        Integer capacity
) {}

