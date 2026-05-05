package com.tablebook.restaurant;

import com.tablebook.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    Optional<Restaurant> findByOrganizationAndSlug(Organization organization, String slug);

    boolean existsByOrganizationAndSlug(Organization organization, String slug);

    List<Restaurant> findAllByOrganization(Organization organization);
}
