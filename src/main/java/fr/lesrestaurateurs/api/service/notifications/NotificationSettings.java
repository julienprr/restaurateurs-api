package fr.lesrestaurateurs.api.service.notifications;

import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.repo.UserRepository;
import fr.lesrestaurateurs.api.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Couper ou rétablir les emails de sortie. Ceux de connexion ne sont jamais concernés. */
@Service
public class NotificationSettings {

    private static final Logger log = LoggerFactory.getLogger(NotificationSettings.class);

    private final UserRepository users;

    public NotificationSettings(UserRepository users) {
        this.users = users;
    }

    /** Depuis le lien d'un email, sans être connecté. */
    @Transactional
    public void unsubscribe(String token) {
        User user = (token == null || token.isBlank() ? null
                : users.findByUnsubscribeToken(token).orElse(null));
        if (user == null) {
            throw ApiException.notFound("Ce lien de désinscription n'est pas valable.");
        }
        user.setNotificationsEnabled(false);
        log.info("{} a coupé les emails depuis un lien", user.getEmail());
    }

    /** Depuis l'application, où l'on peut aussi les rétablir. */
    @Transactional
    public void setEnabled(User current, boolean enabled) {
        // L'utilisateur fourni par le filtre d'authentification est détaché :
        // on recharge celui de la transaction pour que la modification parte.
        User user = users.findById(current.getId()).orElseThrow();
        user.setNotificationsEnabled(enabled);
    }
}
