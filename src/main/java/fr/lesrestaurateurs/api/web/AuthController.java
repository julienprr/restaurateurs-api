package fr.lesrestaurateurs.api.web;

import fr.lesrestaurateurs.api.config.AppProperties;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.security.AuthFilter;
import fr.lesrestaurateurs.api.security.CurrentUser;
import fr.lesrestaurateurs.api.service.AuthService;
import fr.lesrestaurateurs.api.web.dto.RequestLinkRequest;
import fr.lesrestaurateurs.api.web.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AppProperties properties;

    public AuthController(AuthService authService, AppProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/request-link")
    public ResponseEntity<Map<String, String>> requestLink(@Valid @RequestBody RequestLinkRequest body) {
        authService.requestLink(body.email(), body.inviteCode());
        return ResponseEntity.accepted()
                .body(Map.of("message", "Si cette adresse est valide, le lien arrive."));
    }

    @GetMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestParam String token) {
        AuthService.VerifiedSession verified = authService.verify(token);

        ResponseCookie cookie = sessionCookie(verified.sessionToken(),
                Duration.ofDays(properties.sessionTtlDays()));

        // HashMap et non Map.of : inviteCode est nul la plupart du temps.
        Map<String, Object> body = new HashMap<>();
        body.put("user", UserResponse.from(verified.user()));
        body.put("inviteCode", verified.inviteCode());

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
    }

    /**
     * Utilisateur connecté, ou {@code null}. Renvoie volontairement 200 même
     * sans session : le front interroge cette route au démarrage et une 401
     * systématique polluerait la console du navigateur.
     */
    @GetMapping("/me")
    public Map<String, Object> me(@CurrentUser(required = false) User user) {
        Map<String, Object> body = new HashMap<>();
        body.put("user", user == null ? null : UserResponse.from(user));
        return body;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (AuthFilter.COOKIE_NAME.equals(cookie.getName())) {
                    authService.logout(cookie.getValue());
                }
            }
        }
        // Cookie vide et immédiatement expiré : le navigateur le supprime.
        ResponseCookie cleared = sessionCookie("", Duration.ZERO);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from(AuthFilter.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
