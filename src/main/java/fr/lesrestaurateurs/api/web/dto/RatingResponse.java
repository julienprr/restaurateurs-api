package fr.lesrestaurateurs.api.web.dto;

import fr.lesrestaurateurs.api.domain.Rating;
import fr.lesrestaurateurs.api.domain.User;

/**
 * Une note telle que l'application l'affiche.
 *
 * @param mine vrai si c'est la note du demandeur, pour lui proposer de la modifier
 */
public record RatingResponse(
        String author,
        int score,
        String comment,
        boolean mine
) {

    /** À construire dans une transaction : {@code user} est paresseux. */
    public static RatingResponse from(Rating rating, User viewer) {
        return new RatingResponse(
                rating.getUser().getDisplayName(),
                rating.getScore(),
                rating.getComment(),
                rating.getUser().getId().equals(viewer.getId())
        );
    }
}
