package fr.lesrestaurateurs.api.service;

import fr.lesrestaurateurs.api.config.AppProperties;
import fr.lesrestaurateurs.api.domain.AuthSession;
import fr.lesrestaurateurs.api.domain.MagicLinkToken;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.repo.AuthSessionRepository;
import fr.lesrestaurateurs.api.repo.MagicLinkTokenRepository;
import fr.lesrestaurateurs.api.repo.UserRepository;
import fr.lesrestaurateurs.api.web.ApiException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Garde-fou anti-spam : nombre de liens demandés par adresse et par heure. */
    private static final int MAX_LINKS_PER_HOUR = 5;

    private final UserRepository users;
    private final MagicLinkTokenRepository tokens;
    private final AuthSessionRepository sessions;
    private final MailService mailService;
    private final AppProperties properties;

    public AuthService(UserRepository users, MagicLinkTokenRepository tokens,
                       AuthSessionRepository sessions, MailService mailService,
                       AppProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.sessions = sessions;
        this.mailService = mailService;
        this.properties = properties;
    }

    /**
     * Génère un lien magique et l'envoie par email.
     *
     * <p>Ne révèle jamais si l'adresse est déjà connue : le compte est créé à la
     * première connexion réussie, pas ici.
     */
    @Transactional
    public void requestLink(String rawEmail, String inviteCode) {
        String email = normalizeEmail(rawEmail);
        Instant now = Instant.now();

        long recent = tokens.countByEmailAndCreatedAtAfter(email, now.minus(Duration.ofHours(1)));
        if (recent >= MAX_LINKS_PER_HOUR) {
            log.warn("Trop de demandes de lien pour {}", email);
            throw ApiException.tooManyRequests(
                    "Trop de demandes pour cette adresse. Réessaie dans une heure.");
        }

        String token = Tokens.generate();
        Instant expiresAt = now.plus(Duration.ofMinutes(properties.magicLinkTtlMinutes()));
        tokens.save(new MagicLinkToken(Tokens.hash(token), email, blankToNull(inviteCode), expiresAt));

        String link = properties.baseUrl() + "/auth/verify?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        mailService.sendMagicLink(email, link);
    }

    /**
     * Consomme un lien magique et ouvre une session.
     *
     * @return la session créée et le jeton en clair à poser en cookie
     */
    @Transactional
    public VerifiedSession verify(String token) {
        Instant now = Instant.now();

        MagicLinkToken magicLink = tokens.findByTokenHash(Tokens.hash(token))
                .orElseThrow(() -> ApiException.badRequest(
                        "Ce lien n'est pas valide. Demande-en un nouveau."));

        if (!magicLink.isUsable(now)) {
            throw ApiException.badRequest(
                    "Ce lien a expiré ou a déjà été utilisé. Demande-en un nouveau.");
        }
        magicLink.consume(now);

        User user = users.findByEmail(magicLink.getEmail())
                .orElseGet(() -> users.save(
                        new User(magicLink.getEmail(), defaultDisplayName(magicLink.getEmail()))));

        String sessionToken = Tokens.generate();
        Instant expiresAt = now.plus(Duration.ofDays(properties.sessionTtlDays()));
        sessions.save(new AuthSession(Tokens.hash(sessionToken), user, expiresAt));

        log.info("Connexion réussie pour {}", user.getEmail());
        return new VerifiedSession(user, sessionToken, magicLink.getInviteCode());
    }

    /** Retrouve l'utilisateur derrière un jeton de session, s'il est encore valide. */
    @Transactional(readOnly = true)
    public Optional<User> resolveSession(String sessionToken) {
        return sessions.findByTokenHash(Tokens.hash(sessionToken))
                .filter(session -> session.isValid(Instant.now()))
                .map(AuthSession::getUser);
    }

    @Transactional
    public void logout(String sessionToken) {
        sessions.deleteByTokenHash(Tokens.hash(sessionToken));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    /** Nom affiché par défaut : la partie de l'email avant l'arobase. */
    private static String defaultDisplayName(String email) {
        String local = email.substring(0, email.indexOf('@'));
        return local.substring(0, 1).toUpperCase() + local.substring(1);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public record VerifiedSession(User user, String sessionToken, String inviteCode) {
    }
}
