package fr.lesrestaurateurs.api.web;

import fr.lesrestaurateurs.api.config.AppProperties;
import fr.lesrestaurateurs.api.service.OutingClosure;
import fr.lesrestaurateurs.api.service.OutingService;
import fr.lesrestaurateurs.api.web.dto.OutingResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Routes d'exploitation, protégées par un jeton partagé plutôt que par un
 * compte : l'application n'a pas de notion d'administrateur.
 *
 * <p>Sans {@code app.admin-token}, tout répond 404.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AppProperties props;
    private final OutingClosure closure;

    public AdminController(AppProperties props, OutingClosure closure) {
        this.props = props;
        this.closure = closure;
    }

    /**
     * Déclenche la clôture sans attendre le job du matin.
     *
     * @param today date à considérer comme aujourd'hui. Une date future clôt
     *              les sorties prévues avant elle : utile pour tester, à
     *              manier avec soin en production.
     */
    @PostMapping("/outings/close")
    public List<OutingResponse> closeOutings(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        requireAdmin(token);
        return closure.closeOverdue(today != null ? today : LocalDate.now(OutingService.ZONE));
    }

    private void requireAdmin(String token) {
        String expected = props.adminToken();
        if (expected == null || expected.isBlank()) {
            throw ApiException.notFound("Route inconnue.");
        }
        // Comparaison à temps constant : ne pas laisser deviner le jeton
        // caractère par caractère d'après le temps de réponse.
        if (token == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.forbidden("Jeton d'administration invalide.");
        }
    }
}
