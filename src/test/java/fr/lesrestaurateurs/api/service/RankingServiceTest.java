package fr.lesrestaurateurs.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import fr.lesrestaurateurs.api.domain.DecisionMode;
import fr.lesrestaurateurs.api.domain.Restaurant;
import fr.lesrestaurateurs.api.domain.RestaurantStatus;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.domain.UserGroup;
import fr.lesrestaurateurs.api.support.IntegrationTest;
import fr.lesrestaurateurs.api.web.ApiException;
import fr.lesrestaurateurs.api.web.dto.OutingResponse;
import fr.lesrestaurateurs.api.web.dto.RankingResponse;
import fr.lesrestaurateurs.api.web.dto.RankingResponse.MemberRank;
import fr.lesrestaurateurs.api.web.dto.RankingResponse.RestaurantRank;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * Les règles du classement validées avec Julien. Chaque scénario est construit
 * pour qu'une autre règle de calcul donne un autre résultat.
 */
class RankingServiceTest extends IntegrationTest {

    private static final LocalDate NEXT_WEEK = LocalDate.now(OutingService.ZONE).plusDays(7);

    @Autowired
    RankingService ranking;

    @Autowired
    OutingService outings;

    @Autowired
    RatingService ratings;

    @Autowired
    VoteService votes;

    @Autowired
    RestaurantService restaurants;

    User alice;
    User bob;
    User carol;
    UserGroup group;

    @BeforeEach
    void setUp() {
        alice = fixtures.user("Alice");
        bob = fixtures.user("Bob");
        carol = fixtures.user("Carol");
        group = fixtures.group(alice, bob, carol);
    }

    @Test
    @DisplayName("un restaurant : la moyenne de ses sorties, chacune pesant autant")
    void restaurantAveragesOutings() {
        Restaurant sushi = fixtures.restaurant(group, "Sushi", alice);
        // 5 par une seule personne, puis 3 par trois personnes. Moyenne des
        // sorties : 4. Moyenne de toutes les notes, écartée : 3,5.
        outingAt(sushi, Map.of(alice, 5));
        outingAt(sushi, Map.of(alice, 3, bob, 3, carol, 3));

        RestaurantRank row = ranking.rank(group.getId(), alice).restaurants().get(0);

        assertThat(row.name()).isEqualTo("Sushi");
        assertThat(row.average()).isEqualTo(4.0);
        assertThat(row.outings()).isEqualTo(2);
    }

    @Test
    @DisplayName("une sortie sans note ne compte pas, et ne tire pas la moyenne vers zéro")
    void unratedOutingIgnored() {
        Restaurant sushi = fixtures.restaurant(group, "Sushi", alice);
        Restaurant pizza = fixtures.restaurant(group, "Pizza", bob);
        outingAt(sushi, Map.of(alice, 4));
        outingAt(sushi, Map.of());
        outingAt(pizza, Map.of());

        RankingResponse result = ranking.rank(group.getId(), alice);

        assertThat(result.restaurants()).extracting(RestaurantRank::name, RestaurantRank::average,
                        RestaurantRank::outings)
                .containsExactly(tuple("Sushi", 4.0, 1));
    }

    @Test
    @DisplayName("un membre : la moyenne des restaurants qu'il a proposés ; sans restaurant noté, pas classé")
    void memberAveragesProposedRestaurants() {
        Restaurant sushi = fixtures.restaurant(group, "Sushi", alice);
        Restaurant ramen = fixtures.restaurant(group, "Ramen", alice);
        Restaurant pizza = fixtures.restaurant(group, "Pizza", bob);
        fixtures.restaurant(group, "Jamais testé", alice);
        outingAt(sushi, Map.of(bob, 5));
        outingAt(ramen, Map.of(bob, 2, carol, 4));   // 3
        outingAt(pizza, Map.of(alice, 3, carol, 5)); // 4

        RankingResponse result = ranking.rank(group.getId(), carol);

        assertThat(result.members())
                .extracting(MemberRank::name, MemberRank::rank, MemberRank::average,
                        MemberRank::restaurants, MemberRank::mine)
                .containsExactly(
                        tuple("Alice", 1, 4.0, 2, false),  // (5 + 3) / 2
                        tuple("Bob", 1, 4.0, 1, false),
                        tuple("Carol", null, null, 0, true));
    }

    @Test
    @DisplayName("les ex aequo partagent leur rang, le suivant le saute")
    void tiesShareRank() {
        Restaurant a = fixtures.restaurant(group, "A", alice);
        Restaurant b = fixtures.restaurant(group, "B", alice);
        Restaurant c = fixtures.restaurant(group, "C", alice);
        outingAt(c, Map.of(alice, 2));
        outingAt(b, Map.of(alice, 4));
        outingAt(a, Map.of(alice, 4));

        assertThat(ranking.rank(group.getId(), alice).restaurants())
                .extracting(RestaurantRank::name, RestaurantRank::rank)
                .containsExactly(tuple("A", 1), tuple("B", 1), tuple("C", 3));
    }

    @Test
    @DisplayName("sans sortie notée : aucun restaurant, tous les membres non classés")
    void empty() {
        fixtures.restaurant(group, "Sushi", alice);

        RankingResponse result = ranking.rank(group.getId(), alice);

        assertThat(result.restaurants()).isEmpty();
        assertThat(result.members()).extracting(MemberRank::name, MemberRank::rank)
                .containsExactly(tuple("Alice", null), tuple("Bob", null), tuple("Carol", null));
    }

    @Test
    @DisplayName("hors du groupe : 404, et les notes d'un autre groupe n'entrent pas")
    void isolation() {
        User eve = fixtures.user("Eve");
        UserGroup other = fixtures.group(eve);
        Restaurant elsewhere = fixtures.restaurant(other, "Ailleurs", eve);
        OutingResponse outing = outings.plan(other.getId(), NEXT_WEEK, eve);
        votes.toggle(elsewhere.getId(), eve);
        outings.decide(outing.id(), DecisionMode.VOTES, eve);
        outings.closeOverdue(NEXT_WEEK.plusDays(1));
        ratings.rate(outing.id(), 5, null, eve);

        assertThatThrownBy(() -> ranking.rank(group.getId(), eve))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(ranking.rank(group.getId(), alice).restaurants()).isEmpty();
    }

    /**
     * Une sortie au restaurant donné, close, puis notée. Le restaurant est
     * remis « à tester » d'abord, pour pouvoir y retourner.
     */
    private void outingAt(Restaurant restaurant, Map<User, Integer> scores) {
        if (fixtures.statusOf(restaurant) != RestaurantStatus.A_TESTER) {
            restaurants.toggleStatus(restaurant.getId(), alice);
        }
        OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);
        votes.toggle(restaurant.getId(), alice);
        outings.decide(outing.id(), DecisionMode.VOTES, alice);
        outings.closeOverdue(NEXT_WEEK.plusDays(1));
        scores.forEach((user, score) -> ratings.rate(outing.id(), score, null, user));
    }
}
