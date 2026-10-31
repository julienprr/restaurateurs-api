package fr.lesrestaurateurs.api.web.dto;

import java.util.UUID;

/**
 * Verdict d'une décision.
 *
 * @param outingId la sortie dont le restaurant vient d'être fixé
 * @param mode     « hasard » ou « votes », selon le bouton utilisé
 * @param tie      vrai si plusieurs restaurants étaient à égalité et que le sort
 *                 a départagé
 * @param decidedBy prénom de celui qui a lancé la décision
 */
public record DrawResult(
        UUID outingId,
        RestaurantResponse winner,
        String mode,
        boolean tie,
        String decidedBy
) {
}
