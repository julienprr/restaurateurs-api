package fr.lesrestaurateurs.api.web.dto;

import fr.lesrestaurateurs.api.domain.Rating;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RateOutingRequest(
        @NotNull(message = "Choisis une note.")
        @Min(value = 1, message = "La note va de 1 à 5.")
        @Max(value = 5, message = "La note va de 1 à 5.")
        Integer score,

        @Size(max = Rating.MAX_COMMENT_LENGTH, message = "Le commentaire est limité à 500 caractères.")
        String comment
) {
}
