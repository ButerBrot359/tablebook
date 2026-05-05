package com.tablebook.restaurant;

import com.tablebook.auth.user.User;
import com.tablebook.organization.OrganizationRole;
import com.tablebook.restaurant.dto.CreateTableRequest;
import com.tablebook.restaurant.dto.TableResponse;
import com.tablebook.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantTableService {

    private final RestaurantTableRepository tableRepository;
    private final RestaurantService restaurantService;

    @Transactional
    public RestaurantTable create(String orgSlug, String restSlug, CreateTableRequest request, User currentUser) {
        Restaurant restaurant = restaurantService.findBySlug(orgSlug, restSlug);
        restaurantService.requireRole(currentUser, restaurant.getOrganization(), Set.of(OrganizationRole.OWNER, OrganizationRole.MANAGER));

        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setLabel(request.label());
        table.setCapacity(request.capacity());

        return tableRepository.save(table);
    }

    public List<RestaurantTable> findAllByRestaurant(String orgSlug, String restSlug) {
        Restaurant restaurant = restaurantService.findBySlug(orgSlug, restSlug);
        return tableRepository.findAllByRestaurant(restaurant);
    }

    @Transactional
    public void delete(String orgSlug, String restSlug, Long tableId, User currentUser) {
        Restaurant restaurant = restaurantService.findBySlug(orgSlug, restSlug);
        restaurantService.requireRole(currentUser, restaurant.getOrganization(), Set.of(OrganizationRole.OWNER, OrganizationRole.MANAGER));

        RestaurantTable table = tableRepository.findByIdAndRestaurant(tableId, restaurant)
                .orElseThrow(() -> new ResourceNotFoundException("Table", tableId));

        tableRepository.delete(table);
    }

    public TableResponse toResponse(RestaurantTable table) {
        return new TableResponse(
                table.getId(),
                table.getLabel(),
                table.getCapacity()
        );
    }
}
