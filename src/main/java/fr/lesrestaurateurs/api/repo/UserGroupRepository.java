package fr.lesrestaurateurs.api.repo;

import fr.lesrestaurateurs.api.domain.UserGroup;
import fr.lesrestaurateurs.api.web.dto.GroupSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserGroupRepository extends JpaRepository<UserGroup, UUID> {

    boolean existsByInviteCode(String inviteCode);

    Optional<UserGroup> findByInviteCode(String inviteCode);

    /**
     * Groupes de l'utilisateur, du plus récent au plus ancien.
     * Projection directe : évite de charger la collection des membres pour un
     * simple compteur.
     */
    @Query("""
            select new fr.lesrestaurateurs.api.web.dto.GroupSummary(
                g.id, g.name, g.inviteCode, size(g.members))
            from UserGroup g
            join g.members m
            where m.id = :userId
            order by g.createdAt desc
            """)
    List<GroupSummary> findSummariesForUser(@Param("userId") UUID userId);
}
