package fr.lesrestaurateurs.api.web.dto;

import java.util.List;

/**
 * Une sortie passée, avec ses notes.
 *
 * @param average moyenne des notes, nulle tant que personne n'a noté
 */
public record PastOutingResponse(
        OutingResponse outing,
        Double average,
        List<RatingResponse> ratings
) {

    public static PastOutingResponse of(OutingResponse outing, List<RatingResponse> ratings) {
        Double average = ratings.isEmpty()
                ? null
                : ratings.stream().mapToInt(RatingResponse::score).average().orElseThrow();
        return new PastOutingResponse(outing, average, ratings);
    }
}
