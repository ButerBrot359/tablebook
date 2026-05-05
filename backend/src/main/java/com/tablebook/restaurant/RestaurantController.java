package com.tablebook.restaurant;

import com.tablebook.auth.user.User;
import com.tablebook.restaurant.dto.CreateRestaurantRequest;
import com.tablebook.restaurant.dto.RestaurantResponse;
import com.tablebook.restaurant.dto.UpdateRestaurantRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations/{orgSlug}/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RestaurantResponse create(
            @PathVariable String orgSlug,
            @Valid @RequestBody CreateRestaurantRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        Restaurant created = restaurantService.create(orgSlug, request, currentUser);
        return restaurantService.toResponse(created);
    }

    @GetMapping
    public List<RestaurantResponse> list(@PathVariable String orgSlug) {
        return restaurantService.findAllByOrganization(orgSlug).stream()
                .map(restaurantService::toResponse)
                .toList();
    }

    @GetMapping("/{restSlug}")
    public RestaurantResponse getBySlug(
            @PathVariable String orgSlug,
            @PathVariable String restSlug
    ) {
        Restaurant restaurant = restaurantService.findBySlug(orgSlug, restSlug);
        return restaurantService.toResponse(restaurant);
    }

    @PatchMapping("/{restSlug}")
    public RestaurantResponse update(
            @PathVariable String orgSlug,
            @PathVariable String restSlug,
            @Valid @RequestBody UpdateRestaurantRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        Restaurant updated = restaurantService.update(orgSlug, restSlug, request, currentUser);
        return restaurantService.toResponse(updated);
    }

    @DeleteMapping("/{restSlug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable String orgSlug,
            @PathVariable String restSlug,
            @AuthenticationPrincipal User currentUser
    ) {
        restaurantService.delete(orgSlug, restSlug, currentUser);
    }
}
