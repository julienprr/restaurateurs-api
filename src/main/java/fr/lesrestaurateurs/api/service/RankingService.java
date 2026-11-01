package fr.lesrestaurateurs.api.service;

import fr.lesrestaurateurs.api.domain.Outing;
import fr.lesrestaurateurs.api.domain.OutingStatus;
import fr.lesrestaurateurs.api.domain.Rating;
import fr.lesrestaurateurs.api.domain.Restaurant;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.domain.UserGroup;
import fr.lesrestaurateurs.api.repo.OutingRepository;
import fr.lesrestaurateurs.api.repo.RatingRepository;
import fr.lesrestaurateurs.api.web.dto.RankingResponse;
import fr.lesrestaurateurs.api.web.dto.RankingResponse.MemberRank;
import fr.lesrestaurateurs.api.web.dto.RankingResponse.RestaurantRank;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le classement, assumé comme un jeu (voir docs/EN-COURS-sorties.md).
 *
 * <p>Règles validées avec Julien le 31 octobre 2026 :
 * <ul>
 *   <li>la note d'une sortie est la moyenne de ses notes ;</li>
 *   <li>la note d'un restaurant est la moyenne de ses sorties notées : chaque
 *       sortie pèse autant, quel que soit le nombre de membres qui l'ont notée ;</li>
 *   <li>la note d'un membre est la moyenne des restaurants qu'il a proposés,
 *       ceux qui n'ont jamais été notés étant ignorés.</li>
 * </ul>
 *
 * <p>Tout est calculé en mémoire : un groupe d'amis compte quelques dizaines
 * de sorties, pas de quoi justifier une requête d'agrégation.
 */
@Service
public class RankingService {

    private final OutingRepository outings;
    private final RatingRepository ratings;
    private final GroupService groupService;

    public RankingService(OutingRepository outings, RatingRepository ratings,
                          GroupService groupService) {
        this.outings = outings;
        this.ratings = ratings;
        this.groupService = groupService;
    }

    @Transactional(readOnly = true)
    public RankingResponse rank(UUID groupId, User viewer) {
        UserGroup group = groupService.requireMembership(groupId, viewer);

        // Une sortie dont le restaurant a été supprimé ne peut rien classer.
        List<Outing> past = outings.findByGroupAndStatus(groupId, OutingStatus.PASSEE).stream()
                .filter(Outing::hasRestaurant)
                .toList();

        Map<UUID, List<Integer>> scoresByOuting = new HashMap<>();
        if (!past.isEmpty()) {
            for (Rating rating : ratings.findForOutings(past.stream().map(Outing::getId).toList())) {
                scoresByOuting.computeIfAbsent(rating.getOuting().getId(), id -> new ArrayList<>())
                        .add(rating.getScore());
            }
        }

        // Restaurant → moyennes de ses sorties notées.
        Map<UUID, Restaurant> byId = new HashMap<>();
        Map<UUID, List<Double>> outingAverages = new LinkedHashMap<>();
        for (Outing outing : past) {
            List<Integer> scores = scoresByOuting.get(outing.getId());
            if (scores != null) {
                Restaurant restaurant = outing.getRestaurant();
                byId.put(restaurant.getId(), restaurant);
                outingAverages.computeIfAbsent(restaurant.getId(), id -> new ArrayList<>())
                        .add(average(scores));
            }
        }

        List<Scored<Restaurant>> restaurants = new ArrayList<>();
        outingAverages.forEach((id, averages) ->
                restaurants.add(new Scored<>(byId.get(id), average(averages), averages.size())));
        sort(restaurants, r -> r.item().getName());

        // Membre → moyennes des restaurants qu'il a proposés.
        Map<UUID, List<Double>> proposed = new HashMap<>();
        for (Scored<Restaurant> r : restaurants) {
            User addedBy = r.item().getAddedBy();
            if (addedBy != null) {
                proposed.computeIfAbsent(addedBy.getId(), id -> new ArrayList<>()).add(r.average());
            }
        }

        List<Scored<User>> ranked = new ArrayList<>();
        List<User> unranked = new ArrayList<>();
        for (User member : group.getMembers()) {
            List<Double> averages = proposed.get(member.getId());
            if (averages == null) {
                unranked.add(member);
            } else {
                ranked.add(new Scored<>(member, average(averages), averages.size()));
            }
        }
        sort(ranked, m -> m.item().getDisplayName());
        unranked.sort(Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER));

        List<Integer> restaurantRanks = ranks(restaurants);
        List<RestaurantRank> restaurantRows = new ArrayList<>();
        for (int i = 0; i < restaurants.size(); i++) {
            Scored<Restaurant> r = restaurants.get(i);
            restaurantRows.add(new RestaurantRank(r.item().getId(), r.item().getName(),
                    restaurantRanks.get(i), r.average(), r.count()));
        }

        List<Integer> memberRanks = ranks(ranked);
        List<MemberRank> memberRows = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            Scored<User> m = ranked.get(i);
            memberRows.add(new MemberRank(m.item().getDisplayName(), memberRanks.get(i),
                    m.average(), m.count(), m.item().getId().equals(viewer.getId())));
        }
        for (User member : unranked) {
            memberRows.add(new MemberRank(member.getDisplayName(), null, null, 0,
                    member.getId().equals(viewer.getId())));
        }

        return new RankingResponse(restaurantRows, memberRows);
    }

    /** Un élément, sa moyenne, et le nombre de valeurs qui la composent. */
    private record Scored<T>(T item, double average, int count) {
    }

    /** Du meilleur au moins bon, puis par ordre alphabétique pour départager l'affichage. */
    private static <T> void sort(List<Scored<T>> list, Function<Scored<T>, String> name) {
        list.sort(Comparator.comparingDouble((ToDoubleFunction<Scored<T>>) Scored::average).reversed()
                .thenComparing(name, String.CASE_INSENSITIVE_ORDER));
    }

    /** Rangs « sportifs » d'une liste déjà triée : 1, 1, 3. */
    private static <T> List<Integer> ranks(List<Scored<T>> sorted) {
        List<Integer> ranks = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            // Tolérance : 10/3 calculé de deux façons peut différer au dernier bit.
            boolean tie = i > 0
                    && Math.abs(sorted.get(i).average() - sorted.get(i - 1).average()) < 1e-9;
            ranks.add(tie ? ranks.get(i - 1) : i + 1);
        }
        return ranks;
    }

    private static double average(List<? extends Number> values) {
        return values.stream().mapToDouble(Number::doubleValue).average().orElseThrow();
    }
}
