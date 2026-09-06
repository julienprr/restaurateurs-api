package fr.lesrestaurateurs.api.security;

import fr.lesrestaurateurs.api.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Résout la session à partir du cookie et dépose l'utilisateur dans la requête.
 *
 * <p>Ce filtre n'interdit rien : il se contente d'identifier. C'est
 * {@link CurrentUserArgumentResolver} qui refuse les requêtes anonymes sur les
 * routes qui exigent un utilisateur.
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "session";
    static final String REQUEST_ATTRIBUTE = "fr.lesrestaurateurs.currentUser";

    private final AuthService authService;

    public AuthFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        readSessionCookie(request)
                .flatMap(authService::resolveSession)
                .ifPresent(user -> request.setAttribute(REQUEST_ATTRIBUTE, user));

        chain.doFilter(request, response);
    }

    private Optional<String> readSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
