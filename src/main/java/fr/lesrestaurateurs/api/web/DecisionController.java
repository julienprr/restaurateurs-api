package fr.lesrestaurateurs.api.web;

import fr.lesrestaurateurs.api.domain.DecisionMode;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.security.CurrentUser;
import fr.lesrestaurateurs.api.service.GroupEventService;
import fr.lesrestaurateurs.api.service.GroupService;
import fr.lesrestaurateurs.api.service.OutingService;
import fr.lesrestaurateurs.api.service.VoteService;
import fr.lesrestaurateurs.api.web.dto.DrawResult;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Voter, trancher, et suivre le tout en direct. */
@RestController
@RequestMapping("/api")
public class DecisionController {

    private final VoteService voteService;
    private final OutingService outingService;
    private final GroupService groupService;
    private final GroupEventService events;

    public DecisionController(VoteService voteService, OutingService outingService,
                              GroupService groupService, GroupEventService events) {
        this.voteService = voteService;
        this.outingService = outingService;
        this.groupService = groupService;
        this.events = events;
    }

    @PostMapping("/restaurants/{id}/vote")
    public Map<String, String> vote(@PathVariable UUID id, @CurrentUser User user) {
        UUID groupId = voteService.toggle(id, user);
        // Diffusé après le retour du service, donc après validation en base :
        // les clients qui rechargent verront bien le nouveau total.
        events.publish(groupId, "votes_changed", Map.of("restaurantId", id.toString()));
        return Map.of("status", "ok");
    }

    /**
     * Fixe le restaurant de la sortie. Rejouer remplace le choix précédent.
     *
     * <p>Il n'y a plus de route pour effacer les votes : chaque sortie repart
     * de zéro, les voix de la précédente restent attachées à son historique.
     *
     * @param mode {@code votes} pour appliquer le résultat du vote,
     *             {@code hasard} pour un tirage au sort pur
     */
    @PostMapping("/outings/{outingId}/decide")
    public DrawResult decide(@PathVariable UUID outingId,
                             @RequestParam(defaultValue = "hasard") String mode,
                             @CurrentUser User user) {
        DrawResult result = outingService.decide(outingId, parseMode(mode), user);
        // Tout le groupe voit le verdict apparaître, pas seulement celui qui a
        // appuyé sur le bouton.
        events.publish(result.winner().groupId(), "draw_result", result);
        return result;
    }

    /**
     * « Choisir ce soir » : crée la sortie du jour et la tranche d'un coup.
     * Répond 409 si une sortie est déjà prévue un autre jour.
     */
    @PostMapping("/groups/{groupId}/outings/tonight")
    public DrawResult decideTonight(@PathVariable UUID groupId,
                                    @RequestParam(defaultValue = "hasard") String mode,
                                    @CurrentUser User user) {
        DrawResult result = outingService.decideTonight(groupId, parseMode(mode), user);
        events.publish(groupId, "outing_changed", Map.of("outingId", result.outingId().toString()));
        events.publish(groupId, "draw_result", result);
        return result;
    }

    /**
     * Flux d'événements du groupe. Le navigateur (EventSource) rouvre la
     * connexion tout seul en cas de coupure.
     */
    @GetMapping(value = "/groups/{groupId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID groupId, @CurrentUser User user) {
        groupService.assertMembership(groupId, user);
        return events.subscribe(groupId);
    }

    private static DecisionMode parseMode(String mode) {
        return switch (mode.toLowerCase()) {
            case "votes" -> DecisionMode.VOTES;
            case "hasard" -> DecisionMode.HASARD;
            default -> throw ApiException.badRequest("Mode de décision inconnu : « votes » ou « hasard ».");
        };
    }
}
