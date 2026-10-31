package fr.lesrestaurateurs.api.service;

import fr.lesrestaurateurs.api.domain.Outing;
import fr.lesrestaurateurs.api.domain.Restaurant;
import fr.lesrestaurateurs.api.domain.RestaurantStatus;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.domain.Vote;
import fr.lesrestaurateurs.api.repo.OutingRepository;
import fr.lesrestaurateurs.api.repo.RestaurantRepository;
import fr.lesrestaurateurs.api.repo.VoteRepository;
import fr.lesrestaurateurs.api.web.ApiException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les voix des membres pour la sortie en préparation.
 *
 * <p>On ne vote jamais dans le vide : il faut qu'une sortie soit prévue. C'est
 * ce qui permet de garder l'historique de chaque décision.
 */
@Service
public class VoteService {

    private final VoteRepository votes;
    private final RestaurantRepository restaurants;
    private final OutingRepository outings;

    public VoteService(VoteRepository votes, RestaurantRepository restaurants,
                       OutingRepository outings) {
        this.votes = votes;
        this.restaurants = restaurants;
        this.outings = outings;
    }

    /**
     * Ajoute ou retire la voix du membre sur la sortie en préparation. Voter
     * est une bascule : retaper sur le bouton retire son soutien.
     *
     * @return l'identifiant du groupe, pour la diffusion aux autres membres
     */
    @Transactional
    public UUID toggle(UUID restaurantId, User user) {
        Restaurant restaurant = restaurants.findByIdWithGroup(restaurantId)
                .orElseThrow(() -> ApiException.notFound("Ce restaurant n'existe pas."));

        if (!restaurant.getGroup().hasMember(user)) {
            throw ApiException.notFound("Ce restaurant n'existe pas.");
        }
        if (restaurant.getStatus() != RestaurantStatus.A_TESTER) {
            throw ApiException.badRequest(
                    "Vous y êtes déjà allés. Remets-le au tableau pour pouvoir voter.");
        }

        UUID groupId = restaurant.getGroup().getId();
        Outing outing = outings.findPlannedForGroup(groupId)
                .orElseThrow(() -> ApiException.badRequest(
                        "Aucune sortie n'est prévue. Prévois-en une pour ouvrir le vote."));

        votes.findByOutingIdAndRestaurantIdAndUserId(outing.getId(), restaurantId, user.getId())
                .ifPresentOrElse(
                        votes::delete,
                        () -> votes.save(new Vote(outing, restaurant, user)));

        return groupId;
    }

    @Transactional(readOnly = true)
    public Set<UUID> votedBy(UUID outingId, User user) {
        return Set.copyOf(votes.findRestaurantIdsVotedBy(outingId, user.getId()));
    }
}
