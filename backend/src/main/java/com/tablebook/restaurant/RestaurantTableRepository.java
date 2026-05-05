package com.tablebook.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    List<RestaurantTable> findAllByRestaurant(Restaurant restaurant);

    Optional<RestaurantTable> findByIdAndRestaurant(Long id, Restaurant restaurant);
}
