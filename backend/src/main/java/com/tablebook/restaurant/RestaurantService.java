package com.tablebook.restaurant;

import com.tablebook.auth.user.User;
import com.tablebook.organization.Membership;
import com.tablebook.organization.MembershipRepository;
import com.tablebook.organization.Organization;
import com.tablebook.organization.OrganizationRole;
import com.tablebook.organization.OrganizationService;
import com.tablebook.organization.SlugAlreadyTakenException;
import com.tablebook.restaurant.dto.CreateRestaurantRequest;
import com.tablebook.restaurant.dto.RestaurantResponse;
import com.tablebook.restaurant.dto.UpdateRestaurantRequest;
import com.tablebook.shared.exception.ForbiddenException;
import com.tablebook.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final OrganizationService organizationService;
    private final MembershipRepository membershipRepository;

    @Transactional
    public Restaurant create(String orgSlug, CreateRestaurantRequest request, User currentUser) {
        Organization org = organizationService.findBySlug(orgSlug);
        requireRole(currentUser, org, Set.of(OrganizationRole.OWNER, OrganizationRole.MANAGER));

        if (restaurantRepository.existsByOrganizationAndSlug(org, request.slug())) {
            throw new SlugAlreadyTakenException(request.slug());
        }

        Restaurant restaurant = new Restaurant();
        restaurant.setOrganization(org);
        restaurant.setName(request.name());
        restaurant.setSlug(request.slug());
        restaurant.setDescription(request.description());
        restaurant.setAddress(request.address());
        restaurant.setCity(request.city());
        restaurant.setCountry(request.country());
        restaurant.setLatitude(request.latitude());
        restaurant.setLongitude(request.longitude());

        return restaurantRepository.save(restaurant);
    }

    public List<Restaurant> findAllByOrganization(String orgSlug) {
        Organization org = organizationService.findBySlug(orgSlug);
        return restaurantRepository.findAllByOrganization(org);
    }

    public Restaurant findBySlug(String orgSlug, String restSlug) {
        Organization org = organizationService.findBySlug(orgSlug);
        return restaurantRepository.findByOrganizationAndSlug(org, restSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restSlug));
    }

    @Transactional
    public Restaurant update(String orgSlug, String restSlug, UpdateRestaurantRequest request, User currentUser) {
        Organization org = organizationService.findBySlug(orgSlug);
        requireRole(currentUser, org, Set.of(OrganizationRole.OWNER, OrganizationRole.MANAGER));

        Restaurant restaurant = restaurantRepository.findByOrganizationAndSlug(org, restSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restSlug));

        if (request.name() != null) {
            restaurant.setName(request.name());
        }
        if (request.slug() != null && !request.slug().equals(restaurant.getSlug())) {
            if (restaurantRepository.existsByOrganizationAndSlug(org, request.slug())) {
                throw new SlugAlreadyTakenException(request.slug());
            }
            restaurant.setSlug(request.slug());
        }
        if (request.description() != null) {
            restaurant.setDescription(request.description());
        }
        if (request.address() != null) {
            restaurant.setAddress(request.address());
        }
        if (request.city() != null) {
            restaurant.setCity(request.city());
        }
        if (request.country() != null) {
            restaurant.setCountry(request.country());
        }
        if (request.latitude() != null) {
            restaurant.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            restaurant.setLongitude(request.longitude());
        }

        return restaurant;
    }

    @Transactional
    public void delete(String orgSlug, String restSlug, User currentUser) {
        Organization org = organizationService.findBySlug(orgSlug);
        requireRole(currentUser, org, Set.of(OrganizationRole.OWNER));

        Restaurant restaurant = restaurantRepository.findByOrganizationAndSlug(org, restSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restSlug));

        restaurantRepository.delete(restaurant);
    }

    public RestaurantResponse toResponse(Restaurant restaurant) {
        return new RestaurantResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getSlug(),
                restaurant.getDescription(),
                restaurant.getAddress(),
                restaurant.getCity(),
                restaurant.getCountry(),
                restaurant.getLatitude(),
                restaurant.getLongitude(),
                restaurant.getOrganization().getSlug(),
                restaurant.getCreatedAt()
        );
    }

    void requireRole(User user, Organization org, Set<OrganizationRole> allowedRoles) {
        Membership membership = membershipRepository.findMembershipByUserAndOrganization(user, org)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this organization"));

        if (!allowedRoles.contains(membership.getRole())) {
            throw new ForbiddenException("You don't have permission for this action");
        }
    }
}
