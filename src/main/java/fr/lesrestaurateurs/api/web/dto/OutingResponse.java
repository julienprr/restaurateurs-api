package fr.lesrestaurateurs.api.web.dto;

import fr.lesrestaurateurs.api.domain.Outing;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Une sortie telle que l'application l'affiche.
 *
 * @param restaurant nul tant que le groupe n'a pas tranché
 * @param decidedBy  prénom de qui a lancé la décision, nul si personne encore
 */
public record OutingResponse(
        UUID id,
        UUID groupId,
        LocalDate plannedFor,
        String status,
        RestaurantResponse restaurant,
        String decidedHow,
        String decidedBy,
        String createdBy
) {

    /** À construire dans une transaction : les relations sont paresseuses. */
    public static OutingResponse from(Outing outing, RestaurantResponse restaurant) {
        return new OutingResponse(
                outing.getId(),
                outing.getGroup().getId(),
                outing.getPlannedFor(),
                outing.getStatus().name(),
                restaurant,
                outing.getDecidedHow() == null ? null : outing.getDecidedHow().name(),
                outing.getDecidedBy() == null ? null : outing.getDecidedBy().getDisplayName(),
                outing.getCreatedBy() == null ? null : outing.getCreatedBy().getDisplayName()
        );
    }
}
