package fr.lesrestaurateurs.api.service.notifications;

import fr.lesrestaurateurs.api.domain.Outing;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.repo.OutingRepository;
import fr.lesrestaurateurs.api.service.MailService;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Transforme les événements de sortie en emails, une fois la transaction
 * validée. Règles arrêtées avec Julien :
 * <ul>
 *   <li>ne jamais notifier l'auteur de l'action ;</li>
 *   <li>un seul email par événement, aucun rappel ;</li>
 *   <li>« On va là » repart à chaque décision, y compris quand on retranche ;</li>
 *   <li>changer la date ou annuler n'envoie rien ;</li>
 *   <li>un membre qui a coupé les emails n'en reçoit aucun.</li>
 * </ul>
 *
 * <p>Après validation, la transaction d'origine est terminée : il en faut une
 * nouvelle ({@code REQUIRES_NEW}) pour lire les membres et le restaurant, qui
 * sont chargés paresseusement. Les emails, eux, partent en asynchrone.
 */
@Component
public class OutingNotifier {

    private final OutingRepository outings;
    private final NotificationMails mails;
    private final MailService mailService;

    public OutingNotifier(OutingRepository outings, NotificationMails mails, MailService mailService) {
        this.outings = outings;
        this.mails = mails;
        this.mailService = mailService;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(OutingEvent event) {
        switch (event) {
            case OutingEvent.Planned planned -> notify(planned.outingId(), Author.CREATOR, mails::planned);
            case OutingEvent.Decided decided -> notify(decided.outingId(), Author.DECIDER,
                    (to, outing) -> mails.decided(to, outing, decided.changed()));
            case OutingEvent.Closed closed -> closed.outingIds()
                    .forEach(id -> notify(id, Author.NOBODY, mails::rateIt));
        }
    }

    /** Qui a déclenché l'événement, et ne doit donc pas recevoir l'email. */
    private enum Author { CREATOR, DECIDER, NOBODY }

    private void notify(UUID outingId, Author author, BiFunction<User, Outing, OutgoingMail> template) {
        Outing outing = outings.findByIdWithDetails(outingId).orElse(null);
        if (outing == null) {
            return; // supprimée entre-temps, avec son groupe
        }
        User excluded = switch (author) {
            case CREATOR -> outing.getCreatedBy();
            case DECIDER -> outing.getDecidedBy();
            case NOBODY -> null;
        };

        for (User member : outing.getGroup().getMembers()) {
            if (!member.isNotificationsEnabled()
                    || (excluded != null && member.getId().equals(excluded.getId()))) {
                continue;
            }
            mailService.send(template.apply(member, outing));
        }
    }
}
