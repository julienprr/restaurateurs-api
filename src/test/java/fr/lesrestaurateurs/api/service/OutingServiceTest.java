package fr.lesrestaurateurs.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.lesrestaurateurs.api.domain.DecisionMode;
import fr.lesrestaurateurs.api.domain.Restaurant;
import fr.lesrestaurateurs.api.domain.RestaurantStatus;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.domain.UserGroup;
import fr.lesrestaurateurs.api.support.IntegrationTest;
import fr.lesrestaurateurs.api.web.ApiException;
import fr.lesrestaurateurs.api.web.dto.DrawResult;
import fr.lesrestaurateurs.api.web.dto.OutingResponse;
import fr.lesrestaurateurs.api.web.dto.RestaurantResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * Le cycle de vie d'une sortie, tel qu'arrêté dans docs/EN-COURS-sorties.md :
 * prévue, décidée, puis passée ou annulée.
 */
class OutingServiceTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(OutingService.ZONE);
    private static final LocalDate NEXT_WEEK = TODAY.plusDays(7);

    @Autowired
    OutingService outings;

    @Autowired
    VoteService votes;

    @Autowired
    RestaurantService restaurants;

    User alice;
    User bob;
    UserGroup group;
    Restaurant sushi;
    Restaurant pizza;

    @BeforeEach
    void setUp() {
        alice = fixtures.user("Alice");
        bob = fixtures.user("Bob");
        group = fixtures.group(alice, bob);
        sushi = fixtures.restaurant(group, "Sushi", alice);
        pizza = fixtures.restaurant(group, "Pizza", bob);
    }

    @Nested
    @DisplayName("Prévoir")
    class Planning {

        @Test
        @DisplayName("ouvre une sortie planifiée, sans restaurant")
        void plans() {
            OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);

            assertThat(outing.status()).isEqualTo("PLANIFIEE");
            assertThat(outing.plannedFor()).isEqualTo(NEXT_WEEK);
            assertThat(outing.restaurant()).isNull();
            assertThat(outing.createdBy()).isEqualTo("Alice");
            assertThat(outings.findPlanned(group.getId(), bob)).contains(outing);
        }

        @Test
        @DisplayName("aujourd'hui est accepté, hier est refusé")
        void refusesPastDates() {
            assertStatus(HttpStatus.BAD_REQUEST, () -> outings.plan(group.getId(), TODAY.minusDays(1), alice));
            assertThat(outings.plan(group.getId(), TODAY, alice).plannedFor()).isEqualTo(TODAY);
        }

        @Test
        @DisplayName("une seule sortie planifiée par groupe")
        void onlyOnePlanned() {
            outings.plan(group.getId(), NEXT_WEEK, alice);

            assertStatus(HttpStatus.BAD_REQUEST, () -> outings.plan(group.getId(), NEXT_WEEK.plusDays(1), bob));
        }

        @Test
        @DisplayName("un autre groupe peut prévoir la sienne en parallèle")
        void otherGroupsAreIndependent() {
            outings.plan(group.getId(), NEXT_WEEK, alice);
            UserGroup other = fixtures.group(alice);

            assertThat(outings.plan(other.getId(), NEXT_WEEK, alice).status()).isEqualTo("PLANIFIEE");
        }

        @Test
        @DisplayName("des clics simultanés donnent une sortie et des refus, jamais d'erreur serveur")
        void concurrentPlansNeverFail() throws Exception {
            int attempts = 6;
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(attempts);
            List<Future<OutingResponse>> results = new ArrayList<>();
            try {
                for (int i = 0; i < attempts; i++) {
                    Callable<OutingResponse> attempt = () -> {
                        start.await();
                        return outings.plan(group.getId(), NEXT_WEEK, alice);
                    };
                    results.add(pool.submit(attempt));
                }
                start.countDown();

                int planned = 0;
                for (Future<OutingResponse> result : results) {
                    try {
                        result.get();
                        planned++;
                    } catch (java.util.concurrent.ExecutionException e) {
                        assertThat(e.getCause()).isInstanceOfSatisfying(ApiException.class,
                                api -> assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
                    }
                }
                assertThat(planned).isEqualTo(1);
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("Voter")
    class Voting {

        @Test
        @DisplayName("impossible sans sortie prévue")
        void needsAnOuting() {
            assertStatus(HttpStatus.BAD_REQUEST, () -> votes.toggle(sushi.getId(), alice));
        }

        @Test
        @DisplayName("la liste affiche les voix de la sortie en préparation")
        void listShowsCurrentVotes() {
            outings.plan(group.getId(), NEXT_WEEK, alice);
            votes.toggle(sushi.getId(), alice);
            votes.toggle(sushi.getId(), bob);

            Map<String, RestaurantResponse> list = byName(restaurants.findForGroup(group.getId(), alice));
            assertThat(list.get("Sushi").voteCount()).isEqualTo(2);
            assertThat(list.get("Sushi").votedByMe()).isTrue();
            assertThat(list.get("Pizza").voteCount()).isZero();
        }

        @Test
        @DisplayName("voter deux fois retire sa voix")
        void toggles() {
            outings.plan(group.getId(), NEXT_WEEK, alice);
            votes.toggle(sushi.getId(), alice);
            votes.toggle(sushi.getId(), alice);

            assertThat(byName(restaurants.findForGroup(group.getId(), alice)).get("Sushi").voteCount()).isZero();
        }

        @Test
        @DisplayName("chaque sortie repart de zéro, et on peut revoter pour la même adresse")
        void votesDoNotCarryOver() {
            OutingResponse first = outings.plan(group.getId(), NEXT_WEEK, alice);
            votes.toggle(sushi.getId(), alice);
            outings.cancel(first.id(), alice);

            RestaurantResponse afterCancel = byName(restaurants.findForGroup(group.getId(), alice)).get("Sushi");
            assertThat(afterCancel.voteCount()).isZero();
            assertThat(afterCancel.votedByMe()).isFalse();

            outings.plan(group.getId(), NEXT_WEEK, alice);
            votes.toggle(sushi.getId(), alice);
            assertThat(byName(restaurants.findForGroup(group.getId(), alice)).get("Sushi").voteCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Décider")
    class Deciding {

        @Test
        @DisplayName("par les votes : le plus soutenu l'emporte")
        void mostVotedWins() {
            OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);
            votes.toggle(pizza.getId(), alice);
            votes.toggle(pizza.getId(), bob);
            votes.toggle(sushi.getId(), bob);

            DrawResult result = outings.decide(outing.id(), DecisionMode.VOTES, bob);

            assertThat(result.winner().name()).isEqualTo("Pizza");
            assertThat(result.winner().voteCount()).isEqualTo(2);
            assertThat(result.mode()).isEqualTo("votes");
            assertThat(result.tie()).isFalse();
            assertThat(result.decidedBy()).isEqualTo("Bob");

            OutingResponse decided = outings.findPlanned(group.getId(), alice).orElseThrow();
            assertThat(decided.restaurant().name()).isEqualTo("Pizza");
            assertThat(decided.decidedHow()).isEqualTo("VOTES");
            assertThat(decided.decidedBy()).isEqualTo("Bob");
        }

        @Test
        @DisplayName("par les votes, ex æquo : le sort tranche entre les premiers seulement")
        void tieIsBrokenAmongLeaders() {
            Restaurant thai = fixtures.restaurant(group, "Thaï", alice);
            OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);
            votes.toggle(sushi.getId(), alice);
            votes.toggle(pizza.getId(), bob);

            Set<String> winners = new HashSet<>();
            for (int i = 0; i < 30; i++) {
                DrawResult result = outings.decide(outing.id(), DecisionMode.VOTES, alice);
                assertThat(result.tie()).isTrue();
                winners.add(result.winner().name());
            }
            assertThat(winners).doesNotContain(thai.getName()).isNotEmpty();
        }

        @Test
        @DisplayName("par les votes, sans aucune voix : retombe sur le hasard")
        void noVotesFallsBackToChance() {
            OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);

            DrawResult result = outings.decide(outing.id(), DecisionMode.VOTES, alice);

            assertThat(result.mode()).isEqualTo("hasard");
            assertThat(result.winner().name()).isIn("Sushi", "Pizza");
        }

        @Test
        @DisplayName("seules les adresses à tester sont candidates")
        void onlyUntriedPlaces() {
            Restaurant done = fixtures.alreadyDone(group, "Déjà fait", alice);
            OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);

            for (int i = 0; i < 30; i++) {
                assertThat(outings.decide(outing.id(), DecisionMode.HASARD, alice).winner().id())
                        .isNotEqualTo(done.getId());
            }
        }

        @Test
        @DisplayName("sans adresse à tester, refus clair")
        void nothingToTry() {
            UserGroup empty = fixtures.group(alice);
            fixtures.alreadyDone(empty, "Déjà fait", alice);
            OutingResponse outing = outings.plan(empty.getId(), NEXT_WEEK, alice);

            assertStatus(HttpStatus.BAD_REQUEST, () -> outings.decide(outing.id(), DecisionMode.HASARD, alice));
        }

        @Test
        @DisplayName("re-tirer remplace le restaurant, sans ouvrir une nouvelle sortie")
        void redrawReplaces() {
            OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);
            votes.toggle(sushi.getId(), alice);
            outings.decide(outing.id(), DecisionMode.VOTES, alice);

            votes.toggle(sushi.getId(), alice);
            votes.toggle(pizza.getId(), alice);
            DrawResult redraw = outings.decide(outing.id(), DecisionMode.VOTES, bob);

            assertThat(redraw.outingId()).isEqualTo(outing.id());
            OutingResponse planned = outings.findPlanned(group.getId(), alice).orElseThrow();
            assertThat(planned.id()).isEqualTo(outing.id());
            assertThat(planned.restaurant().name()).isEqualTo("Pizza");
            assertThat(planned.decidedBy()).isEqualTo("Bob");
        }
    }

    @Nested
    @DisplayName("Choisir ce soir")
    class Tonight {

        @Test
        @DisplayName("crée la sortie du jour, déjà tranchée")
        void createsAndDecides() {
            DrawResult result = outings.decideTonight(group.getId(), DecisionMode.HASARD, alice);

            OutingResponse tonight = outings.findPlanned(group.getId(), bob).orElseThrow();
            assertThat(tonight.id()).isEqualTo(result.outingId());
            assertThat(tonight.plannedFor()).isEqualTo(TODAY);
            assertThat(tonight.restaurant().name()).isEqualTo(result.winner().name()).isIn("Sushi", "Pizza");
            assertThat(tonight.decidedHow()).isEqualTo("HASARD");
            assertThat(tonight.createdBy()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("sans adresse à tester, refus et aucune sortie vide laissée derrière")
        void failureLeavesNothing() {
            UserGroup empty = fixtures.group(alice);
            fixtures.alreadyDone(empty, "Déjà fait", alice);

            assertStatus(HttpStatus.BAD_REQUEST, () -> outings.decideTonight(empty.getId(), DecisionMode.HASARD, alice));
            assertThat(outings.findPlanned(empty.getId(), alice)).isEmpty();
        }

        @Test
        @DisplayName("une sortie déjà prévue ce soir est tranchée, pas dupliquée")
        void reusesTonightsOuting() {
            OutingResponse planned = outings.plan(group.getId(), TODAY, alice);
            votes.toggle(pizza.getId(), alice);

            DrawResult result = outings.decideTonight(group.getId(), DecisionMode.VOTES, bob);

            assertThat(result.outingId()).isEqualTo(planned.id());
            assertThat(result.winner().name()).isEqualTo("Pizza");
        }

        @Test
        @DisplayName("une sortie prévue un autre jour bloque, en 409, sans être touchée")
        void otherDayConflicts() {
            OutingResponse planned = outings.plan(group.getId(), NEXT_WEEK, alice);

            assertStatus(HttpStatus.CONFLICT, () -> outings.decideTonight(group.getId(), DecisionMode.HASARD, bob));

            OutingResponse unchanged = outings.findPlanned(group.getId(), alice).orElseThrow();
            assertThat(unchanged.id()).isEqualTo(planned.id());
            assertThat(unchanged.plannedFor()).isEqualTo(NEXT_WEEK);
            assertThat(unchanged.restaurant()).isNull();
        }

        @Test
        @DisplayName("l'avancer à ce soir débloque")
        void moveThenDecide() {
            OutingResponse planned = outings.plan(group.getId(), NEXT_WEEK, alice);
            outings.reschedule(planned.id(), TODAY, bob);

            assertThat(outings.decideTonight(group.getId(), DecisionMode.HASARD, bob).outingId())
                    .isEqualTo(planned.id());
        }

        @Test
        @DisplayName("réservé aux membres du groupe")
        void membersOnly() {
            User eve = fixtures.user("Eve");

            assertStatus(HttpStatus.NOT_FOUND, () -> outings.decideTonight(group.getId(), DecisionMode.HASARD, eve));
            assertThat(outings.findPlanned(group.getId(), alice)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Modifier")
    class Changing {

        @Test
        @DisplayName("reprogrammer change la date")
        void reschedules() {
            OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);

            assertThat(outings.reschedule(outing.id(), NEXT_WEEK.plusDays(2), bob).plannedFor())
                    .isEqualTo(NEXT_WEEK.plusDays(2));
            assertStatus(HttpStatus.BAD_REQUEST, () -> outings.reschedule(outing.id(), TODAY.minusDays(1), bob));
        }

        @Test
        @DisplayName("annuler libère la place pour une autre sortie")
        void cancelFreesTheSlot() {
            OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);

            assertThat(outings.cancel(outing.id(), bob)).isEqualTo(group.getId());
            assertThat(outings.findPlanned(group.getId(), alice)).isEmpty();
            assertThat(outings.plan(group.getId(), NEXT_WEEK, alice).id()).isNotEqualTo(outing.id());
        }

        @Test
        @DisplayName("une sortie annulée ne bouge plus")
        void cancelledIsFrozen() {
            OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);
            outings.cancel(outing.id(), alice);

            assertStatus(HttpStatus.BAD_REQUEST, () -> outings.reschedule(outing.id(), NEXT_WEEK, alice));
            assertStatus(HttpStatus.BAD_REQUEST, () -> outings.cancel(outing.id(), alice));
            assertStatus(HttpStatus.BAD_REQUEST, () -> outings.decide(outing.id(), DecisionMode.HASARD, alice));
        }
    }

    @Nested
    @DisplayName("Clore")
    class Closing {

        @Test
        @DisplayName("une sortie décidée devient passée, et son restaurant déjà fait")
        void decidedBecomesPast() {
            OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);
            votes.toggle(sushi.getId(), alice);
            outings.decide(outing.id(), DecisionMode.VOTES, alice);

            OutingResponse closed = closedIn(outings.closeOverdue(NEXT_WEEK.plusDays(1)), outing.id());

            assertThat(closed.status()).isEqualTo("PASSEE");
            assertThat(fixtures.statusOf(sushi)).isEqualTo(RestaurantStatus.DEJA_FAIT);
            assertThat(fixtures.statusOf(pizza)).isEqualTo(RestaurantStatus.A_TESTER);
            assertThat(outings.findPast(group.getId(), alice)).extracting(past -> past.outing().id())
                    .containsExactly(outing.id());
            assertThat(outings.findPlanned(group.getId(), alice)).isEmpty();
        }

        @Test
        @DisplayName("sans restaurant choisi, la sortie est annulée et absente de l'historique")
        void undecidedIsCancelled() {
            OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);

            OutingResponse closed = closedIn(outings.closeOverdue(NEXT_WEEK.plusDays(1)), outing.id());

            assertThat(closed.status()).isEqualTo("ANNULEE");
            assertThat(outings.findPast(group.getId(), alice)).isEmpty();
        }

        @Test
        @DisplayName("le jour même, la sortie reste ouverte")
        void sameDayStaysOpen() {
            OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);

            assertThat(outings.closeOverdue(NEXT_WEEK)).extracting(OutingResponse::id)
                    .doesNotContain(outing.id());
            assertThat(outings.findPlanned(group.getId(), alice)).isPresent();
        }

        @Test
        @DisplayName("une sortie passée ne peut plus être modifiée")
        void pastIsFrozen() {
            OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);
            outings.decide(outing.id(), DecisionMode.HASARD, alice);
            outings.closeOverdue(NEXT_WEEK.plusDays(1));

            assertStatus(HttpStatus.BAD_REQUEST, () -> outings.decide(outing.id(), DecisionMode.HASARD, alice));
            assertStatus(HttpStatus.BAD_REQUEST, () -> outings.cancel(outing.id(), alice));
        }

        private OutingResponse closedIn(List<OutingResponse> closed, UUID outingId) {
            return closed.stream().filter(o -> o.id().equals(outingId)).findFirst().orElseThrow();
        }
    }

    /**
     * Quelqu'un hors du groupe obtient la même réponse qu'une sortie ou un
     * groupe inexistant : on ne lui confirme même pas que ça existe.
     */
    @Nested
    @DisplayName("Cloisonnement des groupes")
    class Isolation {

        User eve;
        OutingResponse outing;

        @BeforeEach
        void setUp() {
            eve = fixtures.user("Eve");
            fixtures.group(eve);
            outing = outings.plan(group.getId(), NEXT_WEEK, alice);
        }

        @Test
        @DisplayName("ni prévoir, ni consulter")
        void cannotPlanOrRead() {
            assertStatus(HttpStatus.NOT_FOUND, () -> outings.plan(group.getId(), NEXT_WEEK, eve));
            assertStatus(HttpStatus.NOT_FOUND, () -> outings.findPlanned(group.getId(), eve));
            assertStatus(HttpStatus.NOT_FOUND, () -> outings.findPast(group.getId(), eve));
        }

        @Test
        @DisplayName("ni reprogrammer, ni annuler, ni décider")
        void cannotChange() {
            assertStatus(HttpStatus.NOT_FOUND, () -> outings.reschedule(outing.id(), NEXT_WEEK, eve));
            assertStatus(HttpStatus.NOT_FOUND, () -> outings.cancel(outing.id(), eve));
            assertStatus(HttpStatus.NOT_FOUND, () -> outings.decide(outing.id(), DecisionMode.HASARD, eve));
            assertThat(outings.findPlanned(group.getId(), alice).orElseThrow().restaurant()).isNull();
        }

        @Test
        @DisplayName("ni voter, ni voir la liste")
        void cannotVote() {
            assertStatus(HttpStatus.NOT_FOUND, () -> votes.toggle(sushi.getId(), eve));
            assertStatus(HttpStatus.NOT_FOUND, () -> restaurants.findForGroup(group.getId(), eve));
        }

        @Test
        @DisplayName("une sortie inconnue répond pareil")
        void unknownLooksTheSame() {
            assertStatus(HttpStatus.NOT_FOUND, () -> outings.cancel(UUID.randomUUID(), alice));
        }
    }

    private static Map<String, RestaurantResponse> byName(List<RestaurantResponse> list) {
        return list.stream().collect(Collectors.toMap(RestaurantResponse::name, Function.identity()));
    }

    private static void assertStatus(HttpStatus expected, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(expected));
    }
}
