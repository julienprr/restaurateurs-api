package fr.lesrestaurateurs.api.repo;

import fr.lesrestaurateurs.api.domain.Vote;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteRepository extends JpaRepository<Vote, UUID> {

    Optional<Vote> findByRestaurantIdAndUserId(UUID restaurantId, UUID userId);

    /** Nombre de voix par restaurant, sous forme de paires (identifiant, total). */
    @Query("""
            select v.restaurant.id, count(v)
            from Vote v
            where v.group.id = :groupId
            group by v.restaurant.id
            """)
    List<Object[]> countByRestaurantForGroup(@Param("groupId") UUID groupId);

    /** Restaurants pour lesquels cet utilisateur a déjà voté. */
    @Query("select v.restaurant.id from Vote v where v.group.id = :groupId and v.user.id = :userId")
    List<UUID> findRestaurantIdsVotedBy(@Param("groupId") UUID groupId,
                                        @Param("userId") UUID userId);

    @Modifying
    @Query("delete from Vote v where v.group.id = :groupId")
    int deleteAllForGroup(@Param("groupId") UUID groupId);
}
