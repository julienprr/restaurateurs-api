package fr.lesrestaurateurs.api.service;

import fr.lesrestaurateurs.api.domain.DecisionMode;
import fr.lesrestaurateurs.api.domain.Outing;
import fr.lesrestaurateurs.api.domain.OutingStatus;
import fr.lesrestaurateurs.api.domain.Rating;
import fr.lesrestaurateurs.api.domain.Restaurant;
import fr.lesrestaurateurs.api.domain.RestaurantStatus;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.domain.UserGroup;
import fr.lesrestaurateurs.api.repo.OutingRepository;
import fr.lesrestaurateurs.api.repo.RatingRepository;
import fr.lesrestaurateurs.api.repo.RestaurantRepository;
import fr.lesrestaurateurs.api.repo.VoteRepository;
import fr.lesrestaurateurs.api.web.ApiException;
import fr.lesrestaurateurs.api.web.dto.DrawResult;
import fr.lesrestaurateurs.api.web.dto.OutingResponse;
import fr.lesrestaurateurs.api.web.dto.PastOutingResponse;
import fr.lesrestaurateurs.api.web.dto.RatingResponse;
import fr.lesrestaurateurs.api.web.dto.RestaurantResponse;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
 *
 * <p>Les méthodes publiques renvoient des DTO : les relations d'une sortie sont
 * paresseuses et {@code open-in-view} est désactivé.
 */
@Service
public class OutingService {

    private static final Logger log = LoggerFactory.getLogger(OutingService.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Le fuseau des sorties. Celui du serveur ne convient pas : en UTC, la
     * journée française bascule une ou deux heures trop tard.
     */
    public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    /** « samedi 7 novembre », pour les messages d'erreur. */
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);

    private final OutingRepository outings;
    private final RestaurantRepository restaurants;
    private final VoteRepository votes;
    private final RatingRepository ratings;
    private final GroupService groupService;

    public OutingService(OutingRepository outings, RestaurantRepository restaurants,
                         VoteRepository votes, RatingRepository ratings,
                         GroupService groupService) {
        this.outings = outings;
        this.restaurants = restaurants;
        this.votes = votes;
        this.ratings = ratings;
        this.groupService = groupService;
    }

    /**
     * Ouvre une sortie, et avec elle le vote.
     *
     * <p>Prévoir et ouvrir le vote sont un seul geste : une sortie où l'on ne
     * pourrait pas encore voter n'aurait pas d'utilité.
     */
    @Transactional
    public OutingResponse plan(UUID groupId, LocalDate date, User user) {
        UserGroup group = groupService.requireMembership(groupId, user);
        requireNotPast(date);

        // Vérification préalable pour le cas courant, avec un message clair.
        // L'index en base reste le vrai garde-fou face aux clics simultanés.
        if (outings.findPlannedForGroup(groupId).isPresent()) {
            throw alreadyPlanned();
        }
        return toResponse(create(group, date, user));
    }

    /**
     * « Choisir ce soir » : une sortie pour aujourd'hui, tranchée dans le même
     * geste, sans passer par un vote.
     *
     * <p>Tout se fait dans une seule transaction : si le tirage échoue, faute
     * d'adresse à tester, aucune sortie vide ne reste à bloquer le groupe.
     *
     * <p>Si une sortie est déjà prévue ce soir, on tranche dessus. Si elle est
     * prévue un autre jour, refus en 409 : au front de proposer de l'avancer
     * à ce soir, puis de rappeler cette route.
     */
    @Transactional
    public DrawResult decideTonight(UUID groupId, DecisionMode mode, User user) {
        UserGroup group = groupService.requireMembership(groupId, user);
        LocalDate today = LocalDate.now(ZONE);

        Optional<Outing> planned = outings.findPlannedForGroup(groupId);
        if (planned.isPresent() && !planned.get().getPlannedFor().equals(today)) {
            throw ApiException.conflict("Une sortie est déjà prévue le "
                    + planned.get().getPlannedFor().format(DAY)
                    + ". Avance-la à ce soir pour la trancher maintenant.");
        }

        Outing outing = planned.orElseGet(() -> create(group, today, user));
        return decideOn(outing, mode, user);
    }

    private Outing create(UserGroup group, LocalDate date, User user) {
        try {
            // saveAndFlush et non save : sans flush, l'INSERT partirait au
            // commit, hors de ce try, et la violation d'index finirait en 500.
            Outing outing = outings.saveAndFlush(new Outing(group, date, user));
            log.info("Sortie du {} prévue dans « {} » par {}", date, group.getName(), user.getEmail());
            return outing;
        } catch (DataIntegrityViolationException e) {
            // L'index partiel outings_one_planned_per_group a parlé : deux
            // membres ont cliqué en même temps.
            throw alreadyPlanned();
        }
    }

    @Transactional(readOnly = true)
    public Optional<OutingResponse> findPlanned(UUID groupId, User user) {
        groupService.requireMembership(groupId, user);
        return outings.findPlannedForGroup(groupId).map(this::toResponse);
    }

    /** Les sorties passées avec leurs notes, de la plus récente à la plus ancienne. */
    @Transactional(readOnly = true)
    public List<PastOutingResponse> findPast(UUID groupId, User user) {
        groupService.requireMembership(groupId, user);
        List<Outing> past = outings.findByGroupAndStatus(groupId, OutingStatus.PASSEE);
        if (past.isEmpty()) {
            return List.of();
        }

        // Toutes les notes en une requête, puis réparties par sortie.
        Map<UUID, List<RatingResponse>> byOuting = new HashMap<>();
        for (Rating rating : ratings.findForOutings(past.stream().map(Outing::getId).toList())) {
            byOuting.computeIfAbsent(rating.getOuting().getId(), id -> new ArrayList<>())
                    .add(RatingResponse.from(rating, user));
        }

        return past.stream()
                .map(outing -> PastOutingResponse.of(toResponse(outing),
                        byOuting.getOrDefault(outing.getId(), List.of())))
                .toList();
    }

    @Transactional
    public OutingResponse reschedule(UUID outingId, LocalDate newDate, User user) {
        Outing outing = requirePlanned(outingId, user);
        requireNotPast(newDate);
        outing.reschedule(newDate);
        return toResponse(outing);
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
    public DrawResult decide(UUID outingId, DecisionMode mode, User user) {
        return decideOn(requirePlanned(outingId, user), mode, user);
    }

    private DrawResult decideOn(Outing outing, DecisionMode mode, User user) {
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
        return new DrawResult(
                outing.getId(),
                RestaurantResponse.from(winner, counts.getOrDefault(winner.getId(), 0L), false),
                applied.name().toLowerCase(),
                tie,
                user.getDisplayName());
    }

    /**
     * Clôt les sorties dont la date est derrière nous.
     *
     * <p>Sans restaurant choisi, il ne s'est rien passé : la sortie est annulée
     * et ne pourra pas être notée. Avec un restaurant, celui-ci rejoint les
     * adresses déjà faites.
     *
     * @return les sorties closes, passées ou annulées, pour que l'appelant
     *         prévienne leurs groupes
     */
    @Transactional
    public List<OutingResponse> closeOverdue(LocalDate today) {
        List<Outing> overdue = outings.findOverdue(today);
        if (overdue.isEmpty()) {
            return List.of();
        }

        List<OutingResponse> closed = new ArrayList<>();
        int rateable = 0;
        for (Outing outing : overdue) {
            outing.close();
            if (outing.getStatus() == OutingStatus.PASSEE) {
                outing.getRestaurant().setStatus(RestaurantStatus.DEJA_FAIT);
                rateable++;
            }
            closed.add(toResponse(outing));
        }

        log.info("{} sortie(s) close(s), dont {} à noter", closed.size(), rateable);
        return closed;
    }

    private Map<UUID, Long> voteCounts(UUID outingId) {
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

    private OutingResponse toResponse(Outing outing) {
        return OutingResponse.from(outing,
                outing.hasRestaurant() ? RestaurantResponse.from(outing.getRestaurant()) : null);
    }

    private static void requireNotPast(LocalDate date) {
        if (date.isBefore(LocalDate.now(ZONE))) {
            throw ApiException.badRequest("Cette date est déjà passée.");
        }
    }

    private static ApiException alreadyPlanned() {
        return ApiException.badRequest(
                "Une sortie est déjà prévue. Annule-la ou attends qu'elle soit passée avant d'en prévoir une autre.");
    }
}
