package fr.lesrestaurateurs.api.service;

import fr.lesrestaurateurs.api.domain.Restaurant;
import fr.lesrestaurateurs.api.domain.RestaurantStatus;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.domain.UserGroup;
import fr.lesrestaurateurs.api.repo.RestaurantRepository;
import fr.lesrestaurateurs.api.repo.VoteRepository;
import fr.lesrestaurateurs.api.web.ApiException;
import fr.lesrestaurateurs.api.web.dto.RestaurantResponse;
import fr.lesrestaurateurs.api.web.dto.SaveRestaurantRequest;
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
public class RestaurantService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantService.class);

    private final RestaurantRepository restaurants;
    private final GroupService groupService;
    private final VoteRepository votes;

    public RestaurantService(RestaurantRepository restaurants, GroupService groupService,
                             VoteRepository votes) {
        this.restaurants = restaurants;
        this.groupService = groupService;
        this.votes = votes;
    }

    @Transactional(readOnly = true)
    public List<RestaurantResponse> findForGroup(UUID groupId, User user) {
        groupService.requireMembership(groupId, user);

        // Deux requêtes agrégées plutôt qu'un comptage par restaurant : la
        // liste tient en une seule page, inutile de la parcourir en boucle.
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : votes.countByRestaurantForGroup(groupId)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        Set<UUID> mine = Set.copyOf(votes.findRestaurantIdsVotedBy(groupId, user.getId()));

        return restaurants.findAllForGroup(groupId).stream()
                .map(restaurant -> RestaurantResponse.from(
                        restaurant,
                        counts.getOrDefault(restaurant.getId(), 0L),
                        mine.contains(restaurant.getId())))
                .toList();
    }

    @Transactional
    public RestaurantResponse add(UUID groupId, SaveRestaurantRequest body, User user) {
        UserGroup group = groupService.requireMembership(groupId, user);

        Restaurant restaurant = new Restaurant(group, body.name().trim(), user);
        restaurant.setAddress(blankToNull(body.address()));
        restaurant.setCity(blankToNull(body.city()));
        restaurant.setCoordinates(body.lat(), body.lng());
        restaurant.setMapsUrl(blankToNull(body.mapsUrl()));
        restaurant.setCuisine(blankToNull(body.cuisine()));
        restaurant.setBudget(blankToNull(body.budget()));

        restaurants.save(restaurant);
        log.info("« {} » ajouté au groupe « {} » par {}",
                restaurant.getName(), group.getName(), user.getEmail());

        return RestaurantResponse.from(restaurant);
    }

    /** Bascule entre « à tester » et « déjà fait ». */
    @Transactional
    public RestaurantResponse toggleStatus(UUID restaurantId, User user) {
        Restaurant restaurant = requireAccess(restaurantId, user);

        restaurant.setStatus(restaurant.getStatus() == RestaurantStatus.A_TESTER
                ? RestaurantStatus.DEJA_FAIT
                : RestaurantStatus.A_TESTER);

        return RestaurantResponse.from(restaurant);
    }

    @Transactional
    public UUID delete(UUID restaurantId, User user) {
        Restaurant restaurant = requireAccess(restaurantId, user);
        UUID groupId = restaurant.getGroup().getId();
        restaurants.delete(restaurant);
        log.info("« {} » retiré par {}", restaurant.getName(), user.getEmail());
        return groupId;
    }

    /**
     * Un restaurant n'est accessible qu'aux membres de son groupe. Tout membre
     * peut le modifier : le groupe est une poignée d'amis, pas une hiérarchie.
     */
    private Restaurant requireAccess(UUID restaurantId, User user) {
        Restaurant restaurant = restaurants.findByIdWithGroup(restaurantId)
                .orElseThrow(() -> ApiException.notFound("Ce restaurant n'existe pas."));

        if (!restaurant.getGroup().hasMember(user)) {
            throw ApiException.notFound("Ce restaurant n'existe pas.");
        }
        return restaurant;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
