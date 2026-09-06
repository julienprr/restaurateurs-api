package fr.lesrestaurateurs.api.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestLinkRequest(
        @NotBlank(message = "L'adresse email est obligatoire.")
        @Email(message = "Cette adresse email ne semble pas valide.")
        String email,

        /** Code d'invitation éventuel, pour rejoindre un groupe dès la connexion. */
        String inviteCode
) {
}
