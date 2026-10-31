package fr.lesrestaurateurs.api.repo;

import fr.lesrestaurateurs.api.domain.Vote;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteRepository extends JpaRepository<Vote, UUID> {

    Optional<Vote> findByOutingIdAndRestaurantIdAndUserId(UUID outingId, UUID restaurantId,
                                                          UUID userId);

    /** Nombre de voix par restaurant, sous forme de paires (identifiant, total). */
    @Query("""
            select v.restaurant.id, count(v)
            from Vote v
            where v.outing.id = :outingId
            group by v.restaurant.id
            """)
    List<Object[]> countByRestaurantForOuting(@Param("outingId") UUID outingId);

    /** Restaurants pour lesquels cet utilisateur a voté lors de cette sortie. */
    @Query("select v.restaurant.id from Vote v where v.outing.id = :outingId and v.user.id = :userId")
    List<UUID> findRestaurantIdsVotedBy(@Param("outingId") UUID outingId,
                                        @Param("userId") UUID userId);
}
