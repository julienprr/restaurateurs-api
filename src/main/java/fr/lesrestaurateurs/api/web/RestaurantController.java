package fr.lesrestaurateurs.api.web;

import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.security.CurrentUser;
import fr.lesrestaurateurs.api.service.GroupEventService;
import fr.lesrestaurateurs.api.service.RestaurantService;
import fr.lesrestaurateurs.api.service.maps.MapsImportService;
import fr.lesrestaurateurs.api.web.dto.MapsPreview;
import fr.lesrestaurateurs.api.web.dto.RestaurantResponse;
import fr.lesrestaurateurs.api.web.dto.SaveRestaurantRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final MapsImportService mapsImportService;
    private final GroupEventService events;

    public RestaurantController(RestaurantService restaurantService,
                                MapsImportService mapsImportService,
                                GroupEventService events) {
        this.restaurantService = restaurantService;
        this.mapsImportService = mapsImportService;
        this.events = events;
    }

    @GetMapping("/groups/{groupId}/restaurants")
    public List<RestaurantResponse> list(@PathVariable UUID groupId, @CurrentUser User user) {
        return restaurantService.findForGroup(groupId, user);
    }

    @PostMapping("/groups/{groupId}/restaurants")
    @ResponseStatus(HttpStatus.CREATED)
    public RestaurantResponse add(@PathVariable UUID groupId,
                                  @Valid @RequestBody SaveRestaurantRequest body,
                                  @CurrentUser User user) {
        RestaurantResponse created = restaurantService.add(groupId, body, user);
        events.publish(groupId, "restaurants_changed", Map.of("added", created.name()));
        return created;
    }

    @PatchMapping("/restaurants/{id}/status")
    public RestaurantResponse toggleStatus(@PathVariable UUID id, @CurrentUser User user) {
        RestaurantResponse updated = restaurantService.toggleStatus(id, user);
        events.publish(updated.groupId(), "restaurants_changed", Map.of("updated", updated.name()));
        return updated;
    }

    @DeleteMapping("/restaurants/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @CurrentUser User user) {
        UUID groupId = restaurantService.delete(id, user);
        events.publish(groupId, "restaurants_changed", Map.of("removed", true));
    }

    /**
     * Lit un lien Google Maps et propose une fiche pré-remplie.
     * Réservé aux membres connectés : c'est le serveur qui émet la requête
     * sortante, on ne l'ouvre pas aux anonymes.
     */
    @PostMapping("/maps/preview")
    public MapsPreview previewMapsLink(@Valid @RequestBody MapsPreviewRequest body,
                                       @CurrentUser User user) {
        return mapsImportService.preview(body.url());
    }

    public record MapsPreviewRequest(
            @NotBlank(message = "Colle un lien Google Maps.") String url) {
    }
}
