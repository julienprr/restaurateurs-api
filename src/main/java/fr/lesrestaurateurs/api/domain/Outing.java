package fr.lesrestaurateurs.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Une sortie : un soir où le groupe va manger quelque part.
 *
 * <p>C'est la date qui pilote le cycle de vie, pas une action d'un membre.
 * Tant qu'elle est devant nous la sortie est {@code PLANIFIEE} et le groupe
 * vote ; une fois passée, plus rien ne bouge et chacun note.
 */
@Entity
@Table(name = "outings")
public class Outing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private UserGroup group;

    @Column(name = "planned_for", nullable = false)
    private LocalDate plannedFor;

    /** Nul tant que le groupe n'a pas tranché. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "decided_how")
    private DecisionMode decidedHow;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutingStatus status = OutingStatus.PLANIFIEE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Outing() {
        // requis par JPA
    }

    public Outing(UserGroup group, LocalDate plannedFor, User createdBy) {
        this.group = group;
        this.plannedFor = plannedFor;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public UserGroup getGroup() {
        return group;
    }

    public LocalDate getPlannedFor() {
        return plannedFor;
    }

    /** Repousser ou avancer une sortie tant qu'elle n'a pas eu lieu. */
    public void reschedule(LocalDate newDate) {
        this.plannedFor = newDate;
    }

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public User getDecidedBy() {
        return decidedBy;
    }

    public DecisionMode getDecidedHow() {
        return decidedHow;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    /**
     * Fixe le restaurant du soir. Rejouer une décision remplace la précédente
     * plutôt que d'ouvrir une seconde sortie : c'est un rattrapage, pas un
     * nouvel événement.
     */
    public void decide(Restaurant chosen, DecisionMode how, User by) {
        this.restaurant = chosen;
        this.decidedHow = how;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
    }

    public OutingStatus getStatus() {
        return status;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isPlanned() {
        return status == OutingStatus.PLANIFIEE;
    }

    public boolean hasRestaurant() {
        return restaurant != null;
    }

    /**
     * Clôt la sortie parce que sa date est derrière nous. Sans restaurant
     * choisi, il ne s'est rien passé : la sortie est annulée plutôt que passée,
     * et ne pourra pas être notée.
     */
    public void close() {
        this.status = hasRestaurant() ? OutingStatus.PASSEE : OutingStatus.ANNULEE;
    }

    /** Annulation explicite, avant la date. */
    public void cancel() {
        this.status = OutingStatus.ANNULEE;
    }
}
