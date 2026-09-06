package fr.lesrestaurateurs.api.repo;

import fr.lesrestaurateurs.api.domain.MagicLinkToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, UUID> {

    Optional<MagicLinkToken> findByTokenHash(String tokenHash);

    /** Sert au garde-fou anti-spam : combien de liens demandés récemment. */
    long countByEmailAndCreatedAtAfter(String email, Instant since);

    @Modifying
    @Query("delete from MagicLinkToken t where t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
