package fr.lesrestaurateurs.api.service;

import fr.lesrestaurateurs.api.domain.Restaurant;
import fr.lesrestaurateurs.api.domain.RestaurantStatus;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.domain.Vote;
import fr.lesrestaurateurs.api.repo.RestaurantRepository;
import fr.lesrestaurateurs.api.repo.VoteRepository;
import fr.lesrestaurateurs.api.web.ApiException;
import fr.lesrestaurateurs.api.web.dto.DrawResult;
import fr.lesrestaurateurs.api.web.dto.RestaurantResponse;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoteService {

    private static final Logger log = LoggerFactory.getLogger(VoteService.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VoteRepository votes;
    private final RestaurantRepository restaurants;
    private final GroupService groupService;

    public VoteService(VoteRepository votes, RestaurantRepository restaurants,
                       GroupService groupService) {
        this.votes = votes;
        this.restaurants = restaurants;
        this.groupService = groupService;
    }

    /**
     * Ajoute ou retire la voix du membre. Voter est une bascule : retaper sur
     * le bouton retire son soutien.
     *
     * @return l'identifiant du groupe concerné, pour la diffusion aux autres
     */
    @Transactional
    public UUID toggle(UUID restaurantId, User user) {
        Restaurant restaurant = restaurants.findByIdWithGroup(restaurantId)
                .orElseThrow(() -> ApiException.notFound("Ce restaurant n'existe pas."));

        if (!restaurant.getGroup().hasMember(user)) {
            throw ApiException.notFound("Ce restaurant n'existe pas.");
        }

        votes.findByRestaurantIdAndUserId(restaurantId, user.getId())
                .ifPresentOrElse(
                        votes::delete,
                        () -> votes.save(new Vote(restaurant, user)));

        return restaurant.getGroup().getId();
    }

    @Transactional
    public int clearVotes(UUID groupId, User user) {
        groupService.requireMembership(groupId, user);
        int removed = votes.deleteAllForGroup(groupId);
        log.info("{} a remis les votes à zéro sur le groupe {}", user.getEmail(), groupId);
        return removed;
    }

    /**
     * Désigne le restaurant du soir parmi ceux qui restent à tester.
     *
     * <p>Deux modes, conformément au brief §2 :
     * <ul>
     *   <li>{@code hasard} : tirage au sort parmi toutes les adresses à tester</li>
     *   <li>{@code votes} : celle qui a le plus de voix ; en cas d'égalité, le
     *       sort départage les ex æquo</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public DrawResult draw(UUID groupId, String mode, User user) {
        groupService.requireMembership(groupId, user);

        List<Restaurant> candidates = restaurants.findAllForGroup(groupId).stream()
                .filter(restaurant -> restaurant.getStatus() == RestaurantStatus.A_TESTER)
                .toList();

        if (candidates.isEmpty()) {
            throw ApiException.badRequest(
                    "Aucune adresse à tester. Ajoutes-en une, ou remets-en une au tableau.");
        }

        Map<UUID, Long> voteCounts = voteCounts(groupId);
        Set<UUID> myVotes = Set.copyOf(votes.findRestaurantIdsVotedBy(groupId, user.getId()));

        List<Restaurant> pool = candidates;
        boolean byVotes = "votes".equals(mode);
        boolean tie = false;

        if (byVotes) {
            long best = candidates.stream()
                    .mapToLong(restaurant -> voteCounts.getOrDefault(restaurant.getId(), 0L))
                    .max()
                    .orElse(0);

            // Personne n'a voté : on retombe sur le hasard plutôt que de
            // désigner arbitrairement le premier de la liste.
            if (best > 0) {
                pool = candidates.stream()
                        .filter(r -> voteCounts.getOrDefault(r.getId(), 0L) == best)
                        .toList();
                tie = pool.size() > 1;
            } else {
                byVotes = false;
            }
        }

        Restaurant winner = pool.get(RANDOM.nextInt(pool.size()));
        log.info("Décision ({}) sur le groupe {} : {}", mode, groupId, winner.getName());

        return new DrawResult(
                RestaurantResponse.from(
                        winner,
                        voteCounts.getOrDefault(winner.getId(), 0L),
                        myVotes.contains(winner.getId())),
                byVotes ? "votes" : "hasard",
                tie,
                user.getDisplayName());
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> voteCounts(UUID groupId) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : votes.countByRestaurantForGroup(groupId)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public Set<UUID> votedBy(UUID groupId, User user) {
        return Set.copyOf(votes.findRestaurantIdsVotedBy(groupId, user.getId()));
    }
}
