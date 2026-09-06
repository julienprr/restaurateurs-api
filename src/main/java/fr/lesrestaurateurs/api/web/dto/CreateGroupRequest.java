package fr.lesrestaurateurs.api.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank(message = "Donne un nom à ton groupe.")
        @Size(max = 60, message = "Le nom du groupe est trop long (60 caractères maximum).")
        String name
) {
}
