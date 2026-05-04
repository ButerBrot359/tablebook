
package com.tablebook.organization.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
        @Size(min = 2, max = 100)
        String name,

        @Size(min = 3, max = 50)
        @Pattern(
                regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "must contain only lowercase letters, digits, and hyphens"
        )
        String slug
) {}
