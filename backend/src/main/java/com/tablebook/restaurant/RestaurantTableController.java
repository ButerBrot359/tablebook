package com.tablebook.restaurant;

import com.tablebook.auth.user.User;
import com.tablebook.restaurant.dto.CreateTableRequest;
import com.tablebook.restaurant.dto.TableResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations/{orgSlug}/restaurants/{restSlug}/tables")
@RequiredArgsConstructor
public class RestaurantTableController {

    private final RestaurantTableService tableService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TableResponse create(
            @PathVariable String orgSlug,
            @PathVariable String restSlug,
            @Valid @RequestBody CreateTableRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        RestaurantTable created = tableService.create(orgSlug, restSlug, request, currentUser);
        return tableService.toResponse(created);
    }

    @GetMapping
    public List<TableResponse> list(
            @PathVariable String orgSlug,
            @PathVariable String restSlug
    ) {
        return tableService.findAllByRestaurant(orgSlug, restSlug).stream()
                .map(tableService::toResponse)
                .toList();
    }

    @DeleteMapping("/{tableId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable String orgSlug,
            @PathVariable String restSlug,
            @PathVariable Long tableId,
            @AuthenticationPrincipal User currentUser
    ) {
        tableService.delete(orgSlug, restSlug, tableId, currentUser);
    }
}
