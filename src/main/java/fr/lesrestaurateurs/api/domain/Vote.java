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
 * Un membre soutient un restaurant pour une sortie donnée.
 *
 * <p>La contrainte d'unicité {@code (outing_id, restaurant_id, user_id)} en
 * base garantit qu'un membre ne pèse qu'une fois par adresse et par sortie,
 * même en cas de double clic, tout en permettant de revoter pour la même
 * adresse lors d'une sortie suivante.
 */
@Entity
@Table(name = "votes")
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outing_id", nullable = false)
    private Outing outing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    /**
     * Redondant avec {@code outing.group}, mais permet de compter les votes
     * d'un groupe sans passer par la table des sorties.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private UserGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Vote() {
        // requis par JPA
    }

    public Vote(Outing outing, Restaurant restaurant, User user) {
        this.outing = outing;
        this.restaurant = restaurant;
        this.group = restaurant.getGroup();
        this.user = user;
    }

    public UUID getId() {
        return id;
    }

    public Outing getOuting() {
        return outing;
    }

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public User getUser() {
        return user;
    }
}
