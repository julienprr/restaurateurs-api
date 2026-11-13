package fr.lesrestaurateurs.api.service.notifications;

import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.repo.UserGroupRepository;
import fr.lesrestaurateurs.api.repo.UserRepository;
import fr.lesrestaurateurs.api.service.MailService;
import fr.lesrestaurateurs.api.web.ApiException;
import fr.lesrestaurateurs.api.web.dto.GroupSummary;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * L'email « Nouvelle version », lancé à la main une fois, à tous les inscrits
 * qui n'ont pas coupé les emails.
 *
 * <p>La table {@code announcements} garantit qu'il ne part qu'une fois : relancer
 * la commande par erreur répond 409 au lieu de renvoyer à tout le monde.
 */
@Service
public class ReleaseAnnouncement {

    private static final Logger log = LoggerFactory.getLogger(ReleaseAnnouncement.class);

    private final UserRepository users;
    private final UserGroupRepository groups;
    private final NotificationMails mails;
    private final MailService mailService;
    private final JdbcClient jdbc;

    public ReleaseAnnouncement(UserRepository users, UserGroupRepository groups, NotificationMails mails,
                               MailService mailService, JdbcClient jdbc) {
        this.users = users;
        this.groups = groups;
        this.mails = mails;
        this.mailService = mailService;
        this.jdbc = jdbc;
    }

    /**
     * Aperçu : envoie l'annonce à un seul compte, sans la marquer comme partie.
     * Pour la relire dans une vraie boîte avant l'envoi général.
     */
    @Transactional(readOnly = true)
    public void preview(String email) {
        User user = users.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> ApiException.notFound("Aucun compte avec cette adresse."));
        mailService.send(mail(user));
    }

    /** @return le nombre de destinataires */
    @Transactional
    public int sendToAll() {
        List<User> recipients = users.findByNotificationsEnabledTrue();

        // Insérer d'abord : la clé primaire refuse un second envoi, même lancé
        // deux fois en même temps.
        int inserted = jdbc.sql("""
                        insert into announcements (key, recipients) values (:key, :recipients)
                        on conflict (key) do nothing
                        """)
                .param("key", NotificationMails.RELEASE_KEY)
                .param("recipients", recipients.size())
                .update();
        if (inserted == 0) {
            throw ApiException.conflict("Cette annonce est déjà partie.");
        }

        recipients.forEach(user -> mailService.send(mail(user)));
        log.info("Annonce {} envoyée à {} inscrits", NotificationMails.RELEASE_KEY, recipients.size());
        return recipients.size();
    }

    /** Le bouton mène au groupe du destinataire s'il n'en a qu'un, sinon à l'accueil. */
    private OutgoingMail mail(User user) {
        List<GroupSummary> memberships = groups.findSummariesForUser(user.getId());
        UUID onlyGroup = memberships.size() == 1 ? memberships.get(0).id() : null;
        return mails.release(user, onlyGroup);
    }
}
