package fr.lesrestaurateurs.api.support;

import fr.lesrestaurateurs.api.domain.AuthSession;
import fr.lesrestaurateurs.api.domain.Restaurant;
import fr.lesrestaurateurs.api.domain.RestaurantStatus;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.domain.UserGroup;
import fr.lesrestaurateurs.api.repo.AuthSessionRepository;
import fr.lesrestaurateurs.api.repo.RestaurantRepository;
import fr.lesrestaurateurs.api.repo.UserGroupRepository;
import fr.lesrestaurateurs.api.repo.UserRepository;
import fr.lesrestaurateurs.api.service.InviteCodes;
import fr.lesrestaurateurs.api.service.Tokens;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;

/** Fabrique les données de départ des tests, directement en base. */
@TestComponent
public class Fixtures {

    private final UserRepository users;
    private final UserGroupRepository groups;
    private final RestaurantRepository restaurants;
    private final AuthSessionRepository sessions;

    public Fixtures(UserRepository users, UserGroupRepository groups,
                    RestaurantRepository restaurants, AuthSessionRepository sessions) {
        this.users = users;
        this.groups = groups;
        this.restaurants = restaurants;
        this.sessions = sessions;
    }

    /** L'email est unique à chaque appel : les tests ne se marchent pas dessus. */
    public User user(String name) {
        return users.save(new User(name.toLowerCase() + "-" + UUID.randomUUID() + "@test.local", name));
    }

    /** Un groupe dont le premier membre est le créateur. */
    public UserGroup group(User creator, User... others) {
        UserGroup group = new UserGroup("Groupe de " + creator.getDisplayName(),
                InviteCodes.generate(), creator);
        for (User other : others) {
            group.addMember(other);
        }
        return groups.save(group);
    }

    public Restaurant restaurant(UserGroup group, String name, User addedBy) {
        return restaurants.save(new Restaurant(group, name, addedBy));
    }

    public Restaurant alreadyDone(UserGroup group, String name, User addedBy) {
        Restaurant restaurant = new Restaurant(group, name, addedBy);
        restaurant.setStatus(RestaurantStatus.DEJA_FAIT);
        return restaurants.save(restaurant);
    }

    public RestaurantStatus statusOf(Restaurant restaurant) {
        return restaurants.findById(restaurant.getId()).orElseThrow().getStatus();
    }

    /** Ouvre une session et renvoie la valeur du cookie, comme après un lien magique. */
    public String sessionCookie(User user) {
        String token = Tokens.generate();
        sessions.save(new AuthSession(Tokens.hash(token), user,
                Instant.now().plus(Duration.ofDays(1))));
        return token;
    }
}
