package fr.lesrestaurateurs.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * La note d'un membre sur une sortie passée, de 1 à 5.
 *
 * <p>La contrainte d'unicité {@code (outing_id, user_id)} en base garantit une
 * note par membre et par sortie : noter à nouveau modifie la note existante.
 * L'écriture passe par {@link fr.lesrestaurateurs.api.repo.RatingRepository#upsert},
 * qui tient même face à un double clic.
 */
@Entity
@Table(name = "ratings")
public class Rating {

    public static final int MAX_COMMENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outing_id", nullable = false)
    private Outing outing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private short score;

    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Rating() {
        // requis par JPA
    }

    public UUID getId() {
        return id;
    }

    public Outing getOuting() {
        return outing;
    }

    public User getUser() {
        return user;
    }

    public int getScore() {
        return score;
    }

    public String getComment() {
        return comment;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
