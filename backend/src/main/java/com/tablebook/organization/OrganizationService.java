package com.tablebook.organization;

import com.tablebook.auth.user.User;
import com.tablebook.organization.dto.OrganizationResponse;
import com.tablebook.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
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
