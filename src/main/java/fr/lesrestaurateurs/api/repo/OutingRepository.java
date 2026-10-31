package fr.lesrestaurateurs.api.repo;

import fr.lesrestaurateurs.api.domain.Outing;
import fr.lesrestaurateurs.api.domain.OutingStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutingRepository extends JpaRepository<Outing, UUID> {

    /**
     * La sortie en préparation d'un groupe, s'il y en a une. L'index partiel
     * {@code outings_one_planned_per_group} garantit qu'il n'y en a jamais deux.
     */
    @Query("""
            select o from Outing o
            left join fetch o.restaurant
            left join fetch o.decidedBy
            where o.group.id = :groupId and o.status = 'PLANIFIEE'
            """)
    Optional<Outing> findPlannedForGroup(@Param("groupId") UUID groupId);

    /** L'historique d'un groupe, de la plus récente à la plus ancienne. */
    @Query("""
            select o from Outing o
            left join fetch o.restaurant
            left join fetch o.decidedBy
            where o.group.id = :groupId and o.status = :status
            order by o.plannedFor desc
            """)
    List<Outing> findByGroupAndStatus(@Param("groupId") UUID groupId,
                                      @Param("status") OutingStatus status);

    /**
     * Les sorties dont la date est derrière nous mais qui n'ont pas encore été
     * closes. On les cherche toutes, et pas seulement celles de la veille :
     * l'API peut avoir été arrêtée plusieurs jours.
     */
    @Query("""
            select o from Outing o
            left join fetch o.restaurant
            where o.status = 'PLANIFIEE' and o.plannedFor < :today
            """)
    List<Outing> findOverdue(@Param("today") LocalDate today);

    @Query("""
            select o from Outing o
            join fetch o.group
            left join fetch o.restaurant
            left join fetch o.decidedBy
            where o.id = :id
            """)
    Optional<Outing> findByIdWithDetails(@Param("id") UUID id);
}
