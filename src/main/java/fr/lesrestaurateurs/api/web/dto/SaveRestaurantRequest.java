package fr.lesrestaurateurs.api.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveRestaurantRequest(
        @NotBlank(message = "Il faut au moins un nom.")
        @Size(max = 120, message = "Ce nom est trop long (120 caractères maximum).")
        String name,

        @Size(max = 250, message = "Cette adresse est trop longue.")
        String address,

        @Size(max = 100, message = "Ce nom de ville est trop long.")
        String city,

        Double lat,
        Double lng,

        @Size(max = 500, message = "Ce lien est trop long.")
        String mapsUrl,

        @Size(max = 40) String cuisine,
        @Size(max = 10) String budget
) {
}
