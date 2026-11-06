package fr.lesrestaurateurs.api.service.notifications;

import java.util.List;
import java.util.UUID;

/**
 * Ce qui arrive à une sortie et mérite un email. Publiés par OutingService
 * pendant la transaction, traités par {@link OutingNotifier} après validation :
 * une sortie dont l'enregistrement échoue n'envoie rien.
 */
public sealed interface OutingEvent {

    /** Une sortie est prévue : « Allez voter ». Pas émis par « choisir ce soir ». */
    record Planned(UUID outingId) implements OutingEvent {
    }

    /**
     * Le restaurant est choisi : « On va là ».
     *
     * @param changed vrai si un restaurant avait déjà été choisi avant
     */
    record Decided(UUID outingId, boolean changed) implements OutingEvent {
    }

    /** Des sorties sont passées : « Notez la sortie ». Les annulées n'en font pas partie. */
    record Closed(List<UUID> outingIds) implements OutingEvent {
    }
}
