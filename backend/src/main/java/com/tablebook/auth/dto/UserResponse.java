package com.tablebook.auth.dto;

import com.tablebook.auth.PlatformRole;

import java.time.OffsetDateTime;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        PlatformRole platformRole,
        boolean emailVerified,
        OffsetDateTime createdAt
) {
}
