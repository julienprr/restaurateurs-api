package fr.lesrestaurateurs.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Groupe d'amis. La classe ne s'appelle pas {@code Group} : ce mot entre en
 * conflit avec {@code GROUP BY} dans les requêtes JPQL.
 */
@Entity
@Table(name = "groups")
public class UserGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Code court partagé dans le lien d'invitation. */
    @Column(name = "invite_code", nullable = false, unique = true)
    private String inviteCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    /**
     * La colonne {@code joined_at} de la table d'association est remplie par sa
     * valeur par défaut en base : le MVP n'a pas besoin de la lire.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "group_members",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> members = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UserGroup() {
        // requis par JPA
    }

    public UserGroup(String name, String inviteCode, User createdBy) {
        this.name = name;
        this.inviteCode = inviteCode;
        this.createdBy = createdBy;
        this.members.add(createdBy);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public Set<User> getMembers() {
        return members;
    }

    public boolean hasMember(User user) {
        return members.stream().anyMatch(member -> member.getId().equals(user.getId()));
    }

    /** @return true si l'utilisateur vient d'être ajouté, false s'il était déjà là */
    public boolean addMember(User user) {
        if (hasMember(user)) {
            return false;
        }
        members.add(user);
        return true;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
