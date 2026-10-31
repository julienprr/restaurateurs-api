package fr.lesrestaurateurs.api.web;

import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.security.CurrentUser;
import fr.lesrestaurateurs.api.service.GroupEventService;
import fr.lesrestaurateurs.api.service.OutingService;
import fr.lesrestaurateurs.api.web.dto.OutingResponse;
import fr.lesrestaurateurs.api.web.dto.PlanOutingRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Prévoir une sortie, la consulter, la déplacer ou l'annuler. */
@RestController
@RequestMapping("/api")
public class OutingController {

    private final OutingService outingService;
    private final GroupEventService events;

    public OutingController(OutingService outingService, GroupEventService events) {
        this.outingService = outingService;
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

    /** Les sorties passées, de la plus récente à la plus ancienne. */
    @GetMapping("/groups/{groupId}/outings")
    public List<OutingResponse> past(@PathVariable UUID groupId, @CurrentUser User user) {
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
}
