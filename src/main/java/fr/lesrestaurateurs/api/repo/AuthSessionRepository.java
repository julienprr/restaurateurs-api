package fr.lesrestaurateurs.api.repo;

import fr.lesrestaurateurs.api.domain.AuthSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    /**
     * L'utilisateur est chargé dans la même requête : il est consulté hors
     * transaction par le filtre d'authentification, et {@code open-in-view}
     * est désactivé.
     */
    @Query("select s from AuthSession s join fetch s.user where s.tokenHash = :tokenHash")
    Optional<AuthSession> findByTokenHash(@Param("tokenHash") String tokenHash);

    void deleteByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
