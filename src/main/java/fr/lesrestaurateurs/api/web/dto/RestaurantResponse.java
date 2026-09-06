package fr.lesrestaurateurs.api.web.dto;

import fr.lesrestaurateurs.api.domain.Restaurant;
import java.util.UUID;

public record RestaurantResponse(
        UUID id,
        UUID groupId,
        String name,
        String address,
        String city,
        Double lat,
        Double lng,
        String mapsUrl,
        String cuisine,
        String budget,
        String status,
        String addedBy,
        long voteCount,
        boolean votedByMe
) {

    /** À construire dans une transaction : {@code addedBy} est paresseux. */
    public static RestaurantResponse from(Restaurant restaurant, long voteCount, boolean votedByMe) {
        return new RestaurantResponse(
                restaurant.getId(),
                restaurant.getGroup().getId(),
                restaurant.getName(),
                restaurant.getAddress(),
                restaurant.getCity(),
                restaurant.getLat(),
                restaurant.getLng(),
                restaurant.getMapsUrl(),
                restaurant.getCuisine(),
                restaurant.getBudget(),
                restaurant.getStatus().name(),
                restaurant.getAddedBy() == null ? null : restaurant.getAddedBy().getDisplayName(),
                voteCount,
                votedByMe
        );
    }

    public static RestaurantResponse from(Restaurant restaurant) {
        return from(restaurant, 0, false);
    }
}
