package fr.lesrestaurateurs.api.repo;

import fr.lesrestaurateurs.api.domain.Restaurant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    /**
     * Le groupe et l'auteur sont chargés dans la même requête : ils sont lus
     * lors de la construction des réponses, et {@code open-in-view} est désactivé.
     */
    @Query("""
            select r from Restaurant r
            left join fetch r.addedBy
            where r.group.id = :groupId
            order by r.createdAt desc
            """)
    List<Restaurant> findAllForGroup(@Param("groupId") UUID groupId);

    @Query("""
            select r from Restaurant r
            join fetch r.group
            left join fetch r.addedBy
            where r.id = :id
            """)
    Optional<Restaurant> findByIdWithGroup(@Param("id") UUID id);
}
