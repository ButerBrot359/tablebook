package com.tablebook.organization;

import com.tablebook.auth.user.User;
import com.tablebook.organization.dto.OrganizationResponse;
import com.tablebook.organization.dto.UpdateOrganizationRequest;
import com.tablebook.shared.exception.ForbiddenException;
import com.tablebook.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationService {
    public final MembershipRepository membershipRepository;
    public final OrganizationRepository organizationRepository;

    @Transactional
    public Organization create(String name, String slug, User owner) {
        if (organizationRepository.existsBySlug(slug)) {
            throw new SlugAlreadyTakenException(slug);
        }

        Organization org = new Organization();
        org.setName(name);
        org.setSlug(slug);
        org.setOwner(owner);
        Organization saved = organizationRepository.save(org);

        Membership ownerMembership = new Membership();
        ownerMembership.setOrganization(saved);
        ownerMembership.setUser(owner);
        ownerMembership.setRole(OrganizationRole.OWNER);
        ownerMembership.setJoinedAt(OffsetDateTime.now());

        membershipRepository.save(ownerMembership);
        return saved;
    }

    @Transactional
    public Organization update(Long id, UpdateOrganizationRequest request, User currentUser) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));

        Membership membership = membershipRepository
                .findMembershipByUserAndOrganization(currentUser, org)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this organization"));

        if (membership.getRole() != OrganizationRole.OWNER) {
            throw new ForbiddenException("Only OWNER can update organization");
        }

        if (request.slug() != null && !request.slug().equals(org.getSlug())) {
            if (organizationRepository.existsBySlugAndIdNot(request.slug(), id)) {
                throw new SlugAlreadyTakenException(request.slug());
            }

            org.setSlug(request.slug());
        }

        if (request.name() != null) {
            org.setName(request.name());
        }

        return org;
    }

    public Organization findBySlug(String slug) {
        return organizationRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", slug));
    }

    public List<Organization> findMyOrganizations(User user) {
        return membershipRepository.findAllByUser(user).stream()
                .map(Membership::getOrganization)
                .toList();
    }

    public OrganizationResponse toResponse(Organization org) {
        return new OrganizationResponse(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getOwner().getId(),
                org.getCreatedAt()
        );
    }
}
