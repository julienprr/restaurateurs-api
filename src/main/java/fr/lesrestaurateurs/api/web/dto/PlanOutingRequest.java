package fr.lesrestaurateurs.api.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PlanOutingRequest(
        @NotNull(message = "Choisis une date pour la sortie.")
        LocalDate plannedFor
) {
}
