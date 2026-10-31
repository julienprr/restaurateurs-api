package fr.lesrestaurateurs.api.service;

import fr.lesrestaurateurs.api.web.dto.OutingResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Clôture quotidienne des sorties dont la date est passée.
 *
 * <p>Séparée d'{@link OutingService} pour diffuser les événements une fois la
 * transaction validée : un client qui recharge voit alors le nouvel état.
 */
@Component
public class OutingClosure {

    private final OutingService outingService;
    private final GroupEventService events;

    public OutingClosure(OutingService outingService, GroupEventService events) {
        this.outingService = outingService;
        this.events = events;
    }

    /**
     * Le matin plutôt que la nuit : l'email « Notez la sortie », prévu plus
     * tard, partira de là et arrivera à une heure où on le lit.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Paris")
    public void closeOverdue() {
        closeOverdue(LocalDate.now(OutingService.ZONE));
    }

    /** @param today date du jour, avançable à la main pour tester */
    public List<OutingResponse> closeOverdue(LocalDate today) {
        List<OutingResponse> closed = outingService.closeOverdue(today);
        for (OutingResponse outing : closed) {
            events.publish(outing.groupId(), "outing_changed",
                    Map.of("outingId", outing.id().toString()));
        }
        return closed;
    }
}
