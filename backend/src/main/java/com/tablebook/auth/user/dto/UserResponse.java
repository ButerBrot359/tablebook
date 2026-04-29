package com.tablebook.auth.user.dto;

import com.tablebook.auth.user.PlatformRole;

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
