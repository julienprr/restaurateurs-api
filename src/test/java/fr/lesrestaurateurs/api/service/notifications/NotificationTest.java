package fr.lesrestaurateurs.api.service.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.lesrestaurateurs.api.domain.DecisionMode;
import fr.lesrestaurateurs.api.domain.Restaurant;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.domain.UserGroup;
import fr.lesrestaurateurs.api.service.OutingService;
import fr.lesrestaurateurs.api.service.VoteService;
import fr.lesrestaurateurs.api.support.IntegrationTest;
import fr.lesrestaurateurs.api.support.Mailbox.Mail;
import fr.lesrestaurateurs.api.web.ApiException;
import fr.lesrestaurateurs.api.web.dto.OutingResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Les emails de sortie, tels qu'arrêtés dans docs/EN-COURS-sorties.md : qui les
 * reçoit, quand, et ce qu'ils contiennent.
 */
class NotificationTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(OutingService.ZONE);
    private static final LocalDate NEXT_WEEK = TODAY.plusDays(7);

    @Autowired
    OutingService outings;

    @Autowired
    VoteService votes;

    @Autowired
    NotificationSettings settings;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    MockMvc mvc;

    User alice;
    User bob;
    User carol;
    UserGroup group;
    Restaurant sushi;

    @BeforeEach
    void setUp() {
        alice = fixtures.user("Alice");
        bob = fixtures.user("Bob");
        carol = fixtures.user("Carol");
        group = fixtures.group(alice, bob, carol);
        sushi = fixtures.restaurant(group, "Sushi", alice);
    }

    @Test
    @DisplayName("prévoir : « Allez voter » à tous, sauf à qui a prévu")
    void plannedNotifiesOthers() {
        outings.plan(group.getId(), NEXT_WEEK, alice);

        Mail toBob = single(bob);
        assertThat(toBob.subject()).startsWith(group.getName() + " : sortie prévue");
        assertThat(toBob.text())
                .contains("Alice propose une sortie")
                .contains("http://localhost:5173/groupes/" + group.getId() + "?onglet=sortie");
        assertThat(mailbox.awaitFor(carol.getEmail(), 1)).hasSize(1);
        assertThat(mailbox.settledFor(alice.getEmail())).isEmpty();
    }

    @Test
    @DisplayName("chaque email porte le lien de désinscription de son destinataire")
    void personalUnsubscribeLink() {
        outings.plan(group.getId(), NEXT_WEEK, alice);

        String token = fixtures.reload(bob).getUnsubscribeToken();
        Mail toBob = single(bob);
        assertThat(token).hasSizeGreaterThan(40);
        assertThat(toBob.text()).contains("http://localhost:5173/desinscription?token=" + token);
        assertThat(toBob.listUnsubscribe())
                .isEqualTo("<http://localhost:5173/api/notifications/unsubscribe?token=" + token + ">");
        assertThat(single(carol).text()).doesNotContain(token);
    }

    @Test
    @DisplayName("trancher : « On va là » à tous sauf au décideur ; retrancher l'annonce comme un changement")
    void decidedNotifiesOthers() {
        OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);
        votes.toggle(sushi.getId(), bob);
        outings.decide(outing.id(), DecisionMode.VOTES, bob);

        List<Mail> toCarol = mailbox.awaitFor(carol.getEmail(), 2);
        assertThat(toCarol).extracting(Mail::subject)
                .containsExactly(group.getName() + " : sortie prévue le " + day(NEXT_WEEK),
                        group.getName() + " : on va chez Sushi le " + day(NEXT_WEEK));
        assertThat(toCarol.get(1).text()).contains("Le vote a parlé").contains("Décision lancée par Bob");

        outings.decide(outing.id(), DecisionMode.HASARD, carol);

        // Bob a tranché la première fois : il n'a reçu que l'annonce de la
        // sortie, puis le changement lancé par Carol.
        assertThat(mailbox.awaitFor(bob.getEmail(), 2).get(1).subject())
                .isEqualTo(group.getName() + " : changement, on va chez Sushi le " + day(NEXT_WEEK));
        assertThat(mailbox.settledFor(bob.getEmail())).hasSize(2);
    }

    @Test
    @DisplayName("choisir ce soir : pas de « Allez voter », seulement « On va là »")
    void tonightSkipsVoting() {
        outings.decideTonight(group.getId(), DecisionMode.HASARD, alice);

        Mail toBob = single(bob);
        assertThat(toBob.subject()).isEqualTo(group.getName() + " : on va chez Sushi ce soir");
        assertThat(mailbox.settledFor(alice.getEmail())).isEmpty();
    }

    @Test
    @DisplayName("changer la date ou annuler n'envoie rien")
    void rescheduleAndCancelAreSilent() {
        OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);
        single(bob);

        outings.reschedule(outing.id(), NEXT_WEEK.plusDays(1), alice);
        outings.cancel(outing.id(), alice);

        assertThat(mailbox.settledFor(bob.getEmail())).hasSize(1);
    }

    @Test
    @DisplayName("clôture : « Notez la sortie » à tous, auteur compris ; rien pour une sortie annulée")
    void closureAsksForRatings() {
        OutingResponse outing = outings.plan(group.getId(), NEXT_WEEK, alice);
        outings.decide(outing.id(), DecisionMode.HASARD, alice);
        outings.closeOverdue(NEXT_WEEK.plusDays(1));

        Mail toAlice = single(alice);
        assertThat(toAlice.subject()).isEqualTo(group.getName() + " : comment c'était chez Sushi ?");
        assertThat(toAlice.text()).contains("?onglet=sorties");
        assertThat(mailbox.awaitFor(bob.getEmail(), 3)).hasSize(3);

        UserGroup quiet = fixtures.group(carol);
        fixtures.restaurant(quiet, "Pizza", carol);
        outings.plan(quiet.getId(), NEXT_WEEK, carol);
        outings.closeOverdue(NEXT_WEEK.plusDays(1));

        // Carol : « Allez voter » et « On va là » du premier groupe, « Notez » ; rien du second.
        assertThat(mailbox.settledFor(carol.getEmail())).extracting(Mail::subject)
                .noneMatch(subject -> subject.startsWith(quiet.getName()));
    }

    @Test
    @DisplayName("qui a coupé les emails n'en reçoit plus, et peut les rétablir")
    void optOut() {
        settings.setEnabled(carol, false);
        outings.plan(group.getId(), NEXT_WEEK, alice);
        single(bob);
        assertThat(mailbox.settledFor(carol.getEmail())).isEmpty();

        settings.setEnabled(carol, true);
        outings.cancel(outings.findPlanned(group.getId(), alice).orElseThrow().id(), alice);
        outings.plan(group.getId(), NEXT_WEEK, alice);
        assertThat(mailbox.awaitFor(carol.getEmail(), 1)).hasSize(1);
    }

    @Test
    @DisplayName("le lien de désinscription marche sans connexion, en POST ; un jeton inconnu : 404")
    void unsubscribeLink() throws Exception {
        String token = fixtures.reload(bob).getUnsubscribeToken();

        mvc.perform(post("/api/notifications/unsubscribe").param("token", token))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/notifications/unsubscribe").param("token", "inconnu"))
                .andExpect(status().isNotFound());

        assertThat(fixtures.reload(bob).isNotificationsEnabled()).isFalse();
        outings.plan(group.getId(), NEXT_WEEK, alice);
        single(carol);
        assertThat(mailbox.settledFor(bob.getEmail())).isEmpty();
    }

    @Test
    @DisplayName("une sortie dont l'enregistrement échoue n'envoie rien")
    void rollbackSendsNothing() {
        transactions.executeWithoutResult(tx -> {
            outings.plan(group.getId(), NEXT_WEEK, alice);
            tx.setRollbackOnly();
        });

        assertThat(mailbox.settledFor(bob.getEmail())).isEmpty();
        assertThat(outings.findPlanned(group.getId(), alice)).isEmpty();
    }

    @Test
    @DisplayName("ce que saisissent les membres est échappé dans la version HTML")
    void htmlIsEscaped() {
        // Un groupe dont la seule adresse a un nom piégé : le tirage la désigne forcément.
        UserGroup trapped = fixtures.group(alice, bob);
        fixtures.restaurant(trapped, "<a href=\"https://piege.example\">Clic</a>", alice);
        outings.decideTonight(trapped.getId(), DecisionMode.HASARD, alice);

        Mail decided = single(bob);
        assertThat(decided.html())
                .doesNotContain("<a href=\"https://piege.example\">")
                .contains("&lt;a href=&quot;https://piege.example&quot;&gt;Clic&lt;/a&gt;");
    }

    @Test
    @DisplayName("annonce de version : aperçu à une adresse, puis une seule fois à tous")
    void releaseAnnouncement(@Autowired ReleaseAnnouncement announcement) {
        announcement.preview(alice.getEmail());
        Mail preview = single(alice);
        assertThat(preview.subject()).isEqualTo("Les Restaurateurs : place aux sorties");
        assertThat(mailbox.settledFor(bob.getEmail())).isEmpty();

        settings.setEnabled(carol, false);
        assertThat(announcement.sendToAll()).isPositive();

        assertThat(mailbox.awaitFor(bob.getEmail(), 1)).hasSize(1);
        assertThat(mailbox.awaitFor(alice.getEmail(), 2)).hasSize(2);
        assertThat(mailbox.settledFor(carol.getEmail())).isEmpty();
        assertThatThrownBy(announcement::sendToAll).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("annonce de version : le bouton ouvre « Nos sorties » du groupe, ou l'accueil si on en a plusieurs")
    void releaseLinksToTheOnlyGroup(@Autowired ReleaseAnnouncement announcement) {
        announcement.preview(alice.getEmail());
        assertThat(single(alice).text())
                .contains("Pense à noter tes sorties passées")
                .contains("Noter mes sorties : http://localhost:5173/groupes/" + group.getId() + "?onglet=sorties");

        User dave = fixtures.user("Dave");
        fixtures.group(dave);
        fixtures.group(dave);
        announcement.preview(dave.getEmail());
        assertThat(single(dave).text()).contains("Noter mes sorties : http://localhost:5173/\n");
    }

    /** Attend l'unique email reçu par ce membre. */
    private Mail single(User user) {
        List<Mail> mails = mailbox.awaitFor(user.getEmail(), 1);
        assertThat(mails).hasSize(1);
        return mails.get(0);
    }

    private static String day(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH));
    }
}
