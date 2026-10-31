package fr.lesrestaurateurs.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import fr.lesrestaurateurs.api.domain.DecisionMode;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.domain.UserGroup;
import fr.lesrestaurateurs.api.repo.RatingRepository;
import fr.lesrestaurateurs.api.support.IntegrationTest;
import fr.lesrestaurateurs.api.web.ApiException;
import fr.lesrestaurateurs.api.web.dto.OutingResponse;
import fr.lesrestaurateurs.api.web.dto.PastOutingResponse;
import fr.lesrestaurateurs.api.web.dto.RatingResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * Les notes, telles qu'arrêtées avec Julien : tout le monde note, une note par
 * membre et par sortie, modifiable sans limite, visible de tous.
 */
class RatingServiceTest extends IntegrationTest {

    private static final LocalDate NEXT_WEEK = LocalDate.now(OutingService.ZONE).plusDays(7);

    @Autowired
    RatingService ratingService;

    @Autowired
    OutingService outings;

    @Autowired
    RatingRepository ratings;

    User alice;
    User bob;
    UserGroup group;

    @BeforeEach
    void setUp() {
        alice = fixtures.user("Alice");
        bob = fixtures.user("Bob");
        group = fixtures.group(alice, bob);
        fixtures.restaurant(group, "Sushi", alice);
    }

    @Test
    @DisplayName("une sortie passée se note, et la note apparaît dans l'historique")
    void ratesPastOuting() {
        UUID outingId = pastOuting();

        UUID groupId = ratingService.rate(outingId, 4, "  Très bon accueil  ", alice);

        assertThat(groupId).isEqualTo(group.getId());
        PastOutingResponse past = onlyPast(alice);
        assertThat(past.average()).isEqualTo(4.0);
        assertThat(past.ratings()).containsExactly(
                new RatingResponse("Alice", 4, "Très bon accueil", true));
    }

    @Test
    @DisplayName("noter à nouveau remplace la note, sans en créer une deuxième")
    void ratingAgainReplaces() {
        UUID outingId = pastOuting();
        ratingService.rate(outingId, 2, "Bof", alice);

        ratingService.rate(outingId, 5, "   ", alice);

        assertThat(onlyPast(alice).ratings()).containsExactly(
                new RatingResponse("Alice", 5, null, true));
    }

    @Test
    @DisplayName("la moyenne réunit les notes de tous, chacun reconnaît la sienne")
    void averageAndOwnership() {
        UUID outingId = pastOuting();
        ratingService.rate(outingId, 4, null, alice);
        ratingService.rate(outingId, 3, "Un peu cher", bob);

        PastOutingResponse seenByBob = onlyPast(bob);

        assertThat(seenByBob.average()).isEqualTo(3.5);
        assertThat(seenByBob.ratings()).extracting(RatingResponse::author, RatingResponse::mine)
                .containsExactlyInAnyOrder(
                        tuple("Alice", false),
                        tuple("Bob", true));
    }

    @Test
    @DisplayName("sans note, la moyenne est absente plutôt que zéro")
    void noRatingNoAverage() {
        pastOuting();

        PastOutingResponse past = onlyPast(alice);

        assertThat(past.average()).isNull();
        assertThat(past.ratings()).isEmpty();
    }

    @Test
    @DisplayName("une sortie prévue ou annulée ne se note pas")
    void onlyPastOutings() {
        OutingResponse planned = outings.plan(group.getId(), NEXT_WEEK, alice);
        assertStatus(HttpStatus.BAD_REQUEST, () -> ratingService.rate(planned.id(), 4, null, alice));

        outings.cancel(planned.id(), alice);
        assertStatus(HttpStatus.BAD_REQUEST, () -> ratingService.rate(planned.id(), 4, null, alice));
    }

    @Test
    @DisplayName("hors du groupe : 404, comme une sortie inconnue")
    void isolation() {
        UUID outingId = pastOuting();
        User eve = fixtures.user("Eve");

        assertStatus(HttpStatus.NOT_FOUND, () -> ratingService.rate(outingId, 1, "Sabotage", eve));
        assertStatus(HttpStatus.NOT_FOUND, () -> ratingService.rate(UUID.randomUUID(), 3, null, alice));
        assertThat(onlyPast(alice).ratings()).isEmpty();
    }

    @Test
    @DisplayName("deux envois simultanés d'une première note n'échouent pas")
    void concurrentFirstRating() throws Exception {
        UUID outingId = pastOuting();
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<UUID>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int score = 1 + i % 5;
                Callable<UUID> call = () -> {
                    start.await();
                    return ratingService.rate(outingId, score, null, alice);
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            for (Future<UUID> result : results) {
                result.get(); // relance l'exception si un envoi a échoué
            }
        } finally {
            pool.shutdown();
        }

        assertThat(onlyPast(alice).ratings()).hasSize(1);
        assertThat(ratings.findByOutingIdAndUserId(outingId, alice.getId())).isPresent();
    }

    /** Une sortie prévue, tranchée, puis close par le job du lendemain. */
    private UUID pastOuting() {
        OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);
        outings.decide(outing.id(), DecisionMode.HASARD, alice);
        outings.closeOverdue(NEXT_WEEK.plusDays(1));
        return outing.id();
    }

    private PastOutingResponse onlyPast(User viewer) {
        List<PastOutingResponse> past = outings.findPast(group.getId(), viewer);
        assertThat(past).hasSize(1);
        return past.get(0);
    }

    private static void assertStatus(HttpStatus expected, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(expected));
    }
}
