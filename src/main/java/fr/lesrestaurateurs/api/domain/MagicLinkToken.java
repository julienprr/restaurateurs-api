package fr.lesrestaurateurs.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Jeton de connexion à usage unique. Seul le hash du jeton est stocké : la
 * valeur en clair n'existe que dans l'email envoyé à l'utilisateur.
 */
@Entity
@Table(name = "magic_link_tokens")
public class MagicLinkToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private String email;

    /** Groupe à rejoindre automatiquement une fois connecté (invitation). */
    @Column(name = "invite_code")
    private String inviteCode;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MagicLinkToken() {
        // requis par JPA
    }

    public MagicLinkToken(String tokenHash, String email, String inviteCode, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.email = email;
        this.inviteCode = inviteCode;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public boolean isUsable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public void consume(Instant now) {
        this.consumedAt = now;
    }
}
