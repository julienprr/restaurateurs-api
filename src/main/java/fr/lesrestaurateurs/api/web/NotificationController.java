package fr.lesrestaurateurs.api.web;

import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.security.CurrentUser;
import fr.lesrestaurateurs.api.service.notifications.NotificationSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Les emails de sortie : les couper depuis un email, ou les régler depuis l'application. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationSettings settings;

    public NotificationController(NotificationSettings settings) {
        this.settings = settings;
    }

    /**
     * Désinscription sans connexion. Appelée par la page de désinscription du
     * front, et directement par les messageries qui gèrent l'en-tête
     * {@code List-Unsubscribe-Post} (toujours en POST, jamais en GET).
     */
    @PostMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@RequestParam(required = false) String token) {
        settings.unsubscribe(token);
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void set(@Valid @RequestBody Preference body, @CurrentUser User user) {
        settings.setEnabled(user, body.enabled());
    }

    public record Preference(@NotNull(message = "Précise si tu veux les emails.") Boolean enabled) {
    }
}
