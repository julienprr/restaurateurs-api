package fr.lesrestaurateurs.api.service;

import fr.lesrestaurateurs.api.domain.DecisionMode;
import fr.lesrestaurateurs.api.domain.Outing;
import fr.lesrestaurateurs.api.domain.OutingStatus;
import fr.lesrestaurateurs.api.domain.Restaurant;
import fr.lesrestaurateurs.api.domain.RestaurantStatus;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.domain.UserGroup;
import fr.lesrestaurateurs.api.repo.OutingRepository;
import fr.lesrestaurateurs.api.repo.RestaurantRepository;
import fr.lesrestaurateurs.api.repo.VoteRepository;
import fr.lesrestaurateurs.api.web.ApiException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le cycle de vie d'une sortie.
 *
 * <p>C'est la date qui mène la danse : on prévoit, on vote, et une fois la date
 * passée la sortie se clôt d'elle-même. Aucune action de membre ne la fait
 * basculer en « passée ».
 */
@Service
public class OutingService {

    private static final Logger log = LoggerFactory.getLogger(OutingService.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OutingRepository outings;
    private final RestaurantRepository restaurants;
    private final VoteRepository votes;
    private final GroupService groupService;

    public OutingService(OutingRepository outings, RestaurantRepository restaurants,
                         VoteRepository votes, GroupService groupService) {
        this.outings = outings;
        this.restaurants = restaurants;
        this.votes = votes;
        this.groupService = groupService;
    }

    /**
     * Ouvre une sortie, et avec elle le vote.
     *
     * <p>Prévoir et ouvrir le vote sont un seul geste : une sortie où l'on ne
     * pourrait pas encore voter n'aurait pas d'utilité.
     */
    @Transactional
    public Outing plan(UUID groupId, LocalDate date, User user) {
        UserGroup group = groupService.requireMembership(groupId, user);

        if (date.isBefore(LocalDate.now())) {
            throw ApiException.badRequest("Cette date est déjà passée.");
        }

        try {
            Outing outing = outings.save(new Outing(group, date, user));
            log.info("Sortie du {} prévue dans « {} » par {}", date, group.getName(), user.getEmail());
            return outing;
        } catch (DataIntegrityViolationException e) {
            // L'index partiel outings_one_planned_per_group a parlé : deux
            // membres ont cliqué en même temps, ou une sortie était déjà ouverte.
            throw ApiException.badRequest(
                    "Une sortie est déjà prévue. Termine-la ou annule-la avant d'en prévoir une autre.");
        }
    }

    @Transactional(readOnly = true)
    public Optional<Outing> findPlanned(UUID groupId, User user) {
        groupService.requireMembership(groupId, user);
        return outings.findPlannedForGroup(groupId);
    }

    @Transactional(readOnly = true)
    public List<Outing> findPast(UUID groupId, User user) {
        groupService.requireMembership(groupId, user);
        return outings.findByGroupAndStatus(groupId, OutingStatus.PASSEE);
    }

    @Transactional
    public Outing reschedule(UUID outingId, LocalDate newDate, User user) {
        Outing outing = requirePlanned(outingId, user);
        if (newDate.isBefore(LocalDate.now())) {
            throw ApiException.badRequest("Cette date est déjà passée.");
        }
        outing.reschedule(newDate);
        return outing;
    }

    @Transactional
    public UUID cancel(UUID outingId, User user) {
        Outing outing = requirePlanned(outingId, user);
        outing.cancel();
        log.info("Sortie du {} annulée par {}", outing.getPlannedFor(), user.getEmail());
        return outing.getGroup().getId();
    }

    /**
     * Désigne le restaurant du soir parmi ceux qui restent à tester.
     *
     * <p>Rejouer une décision remplace la précédente : c'est un rattrapage, pas
     * une nouvelle sortie.
     *
     * @param mode {@code VOTES} applique le résultat du vote, le sort
     *             départageant les ex æquo ; {@code HASARD} tire parmi toutes
     *             les adresses à tester. Sans aucune voix, {@code VOTES}
     *             retombe sur le hasard plutôt que de désigner arbitrairement
     *             le premier de la liste.
     */
    @Transactional
    public Decision decide(UUID outingId, DecisionMode mode, User user) {
        Outing outing = requirePlanned(outingId, user);
        UUID groupId = outing.getGroup().getId();

        List<Restaurant> candidates = restaurants.findAllForGroup(groupId).stream()
                .filter(r -> r.getStatus() == RestaurantStatus.A_TESTER)
                .toList();

        if (candidates.isEmpty()) {
            throw ApiException.badRequest(
                    "Aucune adresse à tester. Ajoutes-en une, ou remets-en une au tableau.");
        }

        Map<UUID, Long> counts = voteCounts(outing.getId());
        List<Restaurant> pool = candidates;
        DecisionMode applied = mode;
        boolean tie = false;

        if (mode == DecisionMode.VOTES) {
            long best = candidates.stream()
                    .mapToLong(r -> counts.getOrDefault(r.getId(), 0L))
                    .max()
                    .orElse(0);
            if (best > 0) {
                pool = candidates.stream()
                        .filter(r -> counts.getOrDefault(r.getId(), 0L) == best)
                        .toList();
                tie = pool.size() > 1;
            } else {
                applied = DecisionMode.HASARD;
            }
        }

        Restaurant winner = pool.get(RANDOM.nextInt(pool.size()));
        outing.decide(winner, applied, user);

        log.info("Sortie du {} : {} retenu ({})", outing.getPlannedFor(), winner.getName(), applied);
        return new Decision(outing, winner, applied, tie,
                counts.getOrDefault(winner.getId(), 0L));
    }

    /**
     * Clôt les sorties dont la date est derrière nous.
     *
     * <p>Sans restaurant choisi, il ne s'est rien passé : la sortie est annulée
     * et ne pourra pas être notée. Avec un restaurant, celui-ci rejoint les
     * adresses déjà faites.
     *
     * @return les sorties devenues notables, pour que l'appelant prévienne
     *         leurs membres
     */
    @Transactional
    public List<Outing> closeOverdue(LocalDate today) {
        List<Outing> overdue = outings.findOverdue(today);
        if (overdue.isEmpty()) {
            return List.of();
        }

        List<Outing> closed = overdue.stream().peek(outing -> {
            outing.close();
            if (outing.getStatus() == OutingStatus.PASSEE) {
                outing.getRestaurant().setStatus(RestaurantStatus.DEJA_FAIT);
            }
        }).filter(o -> o.getStatus() == OutingStatus.PASSEE).toList();

        log.info("{} sortie(s) close(s), dont {} à noter", overdue.size(), closed.size());
        return closed;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> voteCounts(UUID outingId) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : votes.countByRestaurantForOuting(outingId)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** Charge une sortie en préparation, en vérifiant l'accès du demandeur. */
    private Outing requirePlanned(UUID outingId, User user) {
        Outing outing = outings.findByIdWithDetails(outingId)
                .orElseThrow(() -> ApiException.notFound("Cette sortie n'existe pas."));

        if (!outing.getGroup().hasMember(user)) {
            // Même message qu'une sortie absente : ne pas confirmer son
            // existence à quelqu'un qui n'y a pas accès.
            throw ApiException.notFound("Cette sortie n'existe pas.");
        }
        if (!outing.isPlanned()) {
            throw ApiException.badRequest("Cette sortie est terminée, elle ne peut plus changer.");
        }
        return outing;
    }

    /** Le verdict d'une décision, tel qu'il est annoncé au groupe. */
    public record Decision(Outing outing, Restaurant winner, DecisionMode mode,
                           boolean tie, long voteCount) {
    }
}
