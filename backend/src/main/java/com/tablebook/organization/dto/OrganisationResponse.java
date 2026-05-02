package com.tablebook.organization.dto;

import java.time.OffsetDateTime;

public record OrganisationResponse(
        Long id,
        String name,
        String slug,
        Long ownerId,
        OffsetDateTime createdAt
) {
}
