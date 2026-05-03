package com.tablebook.organization;

import com.tablebook.auth.user.User;
import com.tablebook.organization.dto.CreateOrganizationRequest;
import com.tablebook.organization.dto.OrganizationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {
    private final OrganizationService organizationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse create(
            @Valid @RequestBody CreateOrganizationRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        Organization created = organizationService.create(
                request.name(),
                request.slug(),
                currentUser
        );

        return organizationService.toResponse(created);

    }

    @GetMapping("/me")
    public List<OrganizationResponse> myOrganizations(@AuthenticationPrincipal User currentUser) {
        return organizationService.findMyOrganizations(currentUser).stream()
                .map(organizationService::toResponse)
                .toList();
    }

    @GetMapping("/{slug}")
    public OrganizationResponse getBySlug(@PathVariable String slug) {
        Organization org = organizationService.findBySlug(slug);
        return organizationService.toResponse(org);
    }

}
