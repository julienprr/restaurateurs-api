package fr.lesrestaurateurs.api.service;

import fr.lesrestaurateurs.api.domain.Outing;
import fr.lesrestaurateurs.api.domain.OutingStatus;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.repo.OutingRepository;
import fr.lesrestaurateurs.api.repo.RatingRepository;
import fr.lesrestaurateurs.api.web.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les notes des membres sur les sorties passées.
 *
 * <p>Tout le monde peut noter, sans vérifier qui était présent, et sans limite
 * de temps. Les notes sont visibles de tous, y compris avant d'avoir noté.
 */
@Service
public class RatingService {

    private final RatingRepository ratings;
    private final OutingRepository outings;

    public RatingService(RatingRepository ratings, OutingRepository outings) {
        this.ratings = ratings;
        this.outings = outings;
    }

    /**
     * Donne ou modifie la note du membre sur une sortie passée.
     *
     * @return l'identifiant du groupe, pour la diffusion aux autres membres
     */
    @Transactional
    public UUID rate(UUID outingId, int score, String comment, User user) {
        Outing outing = outings.findByIdWithDetails(outingId)
                .orElseThrow(() -> ApiException.notFound("Cette sortie n'existe pas."));

        if (!outing.getGroup().hasMember(user)) {
            // Même message qu'une sortie absente, comme dans OutingService.
            throw ApiException.notFound("Cette sortie n'existe pas.");
        }
        if (outing.getStatus() != OutingStatus.PASSEE) {
            throw ApiException.badRequest(outing.isPlanned()
                    ? "On ne note une sortie qu'une fois qu'elle est passée."
                    : "Cette sortie a été annulée, il n'y a rien à noter.");
        }

        ratings.upsert(outingId, user.getId(), score, blankToNull(comment));
        return outing.getGroup().getId();
    }

    private static String blankToNull(String comment) {
        return comment == null || comment.isBlank() ? null : comment.strip();
    }
}
