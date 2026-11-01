package fr.lesrestaurateurs.api.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Le classement d'un groupe : les restaurants, puis les membres.
 *
 * <p>Deux ex aequo partagent le même rang, et le suivant le saute : 1, 1, 3.
 */
public record RankingResponse(
        List<RestaurantRank> restaurants,
        List<MemberRank> members
) {

    /**
     * @param outings nombre de sorties notées qui entrent dans la moyenne
     */
    public record RestaurantRank(
            UUID id,
            String name,
            int rank,
            double average,
            int outings
    ) {
    }

    /**
     * @param rank        nul tant qu'aucun restaurant proposé n'a été noté
     * @param average     moyenne des restaurants proposés, nulle dans le même cas
     * @param restaurants nombre de restaurants notés qui entrent dans la moyenne
     */
    public record MemberRank(
            String name,
            Integer rank,
            Double average,
            int restaurants,
            boolean mine
    ) {
    }
}
