package fr.lesrestaurateurs.api.repo;

import fr.lesrestaurateurs.api.domain.Rating;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    Optional<Rating> findByOutingIdAndUserId(UUID outingId, UUID userId);

    /**
     * Crée la note du membre, ou remplace celle qu'il avait déjà donnée. En une
     * seule requête : deux envois simultanés ne peuvent pas heurter la
     * contrainte d'unicité, le second écrase simplement le premier.
     *
     * <p>Le {@code cast} évite qu'un commentaire nul soit transmis à
     * PostgreSQL sans type, ce qu'il refuse.
     */
    @Modifying
    @Query(value = """
            insert into ratings (outing_id, user_id, score, comment)
            values (:outingId, :userId, :score, cast(:comment as text))
            on conflict (outing_id, user_id)
            do update set score = excluded.score,
                          comment = excluded.comment,
                          updated_at = now()
            """, nativeQuery = true)
    void upsert(@Param("outingId") UUID outingId, @Param("userId") UUID userId,
                @Param("score") int score, @Param("comment") String comment);

    /**
     * Les notes de plusieurs sorties en une requête, auteur compris : l'écran
     * d'historique les affiche toutes d'un coup.
     */
    @Query("""
            select r from Rating r
            join fetch r.user
            where r.outing.id in :outingIds
            order by r.updatedAt
            """)
    List<Rating> findForOutings(@Param("outingIds") Collection<UUID> outingIds);
}
