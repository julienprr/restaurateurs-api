package fr.lesrestaurateurs.api.repo;

import fr.lesrestaurateurs.api.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUnsubscribeToken(String unsubscribeToken);

    /** Les destinataires d'une annonce : tous les inscrits qui n'ont pas coupé les emails. */
    List<User> findByNotificationsEnabledTrue();
}
