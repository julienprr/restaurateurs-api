package fr.lesrestaurateurs.api.web;

import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.security.CurrentUser;
import fr.lesrestaurateurs.api.service.GroupEventService;
import fr.lesrestaurateurs.api.service.OutingService;
import fr.lesrestaurateurs.api.service.RatingService;
import fr.lesrestaurateurs.api.web.dto.OutingResponse;
import fr.lesrestaurateurs.api.web.dto.PastOutingResponse;
import fr.lesrestaurateurs.api.web.dto.PlanOutingRequest;
import fr.lesrestaurateurs.api.web.dto.RateOutingRequest;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Prévoir une sortie, la consulter, la déplacer, l'annuler, puis la noter. */
@RestController
@RequestMapping("/api")
public class OutingController {

    private final OutingService outingService;
    private final RatingService ratingService;
    private final GroupEventService events;

    public OutingController(OutingService outingService, RatingService ratingService,
                            GroupEventService events) {
        this.outingService = outingService;
        this.ratingService = ratingService;
        this.events = events;
    }

    /** Prévoit une sortie et ouvre le vote, en un seul geste. */
    @PostMapping("/groups/{groupId}/outings")
    @ResponseStatus(HttpStatus.CREATED)
    public OutingResponse plan(@PathVariable UUID groupId,
                               @Valid @RequestBody PlanOutingRequest body,
                               @CurrentUser User user) {
        OutingResponse created = outingService.plan(groupId, body.plannedFor(), user);
        events.publish(groupId, "outing_changed", Map.of("outingId", created.id().toString()));
        return created;
    }

    /**
     * La sortie en préparation, enveloppée pour pouvoir répondre
     * {@code {"outing": null}} quand il n'y en a pas, comme {@code /auth/me}.
     */
    @GetMapping("/groups/{groupId}/outings/planned")
    public Map<String, Object> planned(@PathVariable UUID groupId, @CurrentUser User user) {
        Map<String, Object> body = new HashMap<>();
        body.put("outing", outingService.findPlanned(groupId, user).orElse(null));
        return body;
    }

    /** Les sorties passées et leurs notes, de la plus récente à la plus ancienne. */
    @GetMapping("/groups/{groupId}/outings")
    public List<PastOutingResponse> past(@PathVariable UUID groupId, @CurrentUser User user) {
        return outingService.findPast(groupId, user);
    }

    @PatchMapping("/outings/{id}")
    public OutingResponse reschedule(@PathVariable UUID id,
                                     @Valid @RequestBody PlanOutingRequest body,
                                     @CurrentUser User user) {
        OutingResponse updated = outingService.reschedule(id, body.plannedFor(), user);
        events.publish(updated.groupId(), "outing_changed", Map.of("outingId", id.toString()));
        return updated;
    }

    /**
     * Annule la sortie. Un POST plutôt qu'un DELETE : la sortie n'est pas
     * effacée, elle passe en {@code ANNULEE}.
     */
    @PostMapping("/outings/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id, @CurrentUser User user) {
        UUID groupId = outingService.cancel(id, user);
        events.publish(groupId, "outing_changed", Map.of("outingId", id.toString()));
    }

    /**
     * Note une sortie passée, ou modifie sa note. Un PUT : il n'y a qu'une
     * note par membre et par sortie, la renvoyer la remplace.
     */
    @PutMapping("/outings/{id}/rating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rate(@PathVariable UUID id,
                     @Valid @RequestBody RateOutingRequest body,
                     @CurrentUser User user) {
        UUID groupId = ratingService.rate(id, body.score(), body.comment(), user);
        events.publish(groupId, "ratings_changed", Map.of("outingId", id.toString()));
    }
}
