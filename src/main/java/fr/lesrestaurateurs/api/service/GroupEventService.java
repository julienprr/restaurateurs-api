package fr.lesrestaurateurs.api.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Diffuse les changements d'un groupe à tous ses membres connectés (SSE).
 *
 * <p>Les connexions sont gardées en mémoire. Conséquence à connaître : cela
 * suppose une seule instance de l'API. Avec plusieurs répliques, un membre
 * connecté à l'instance A ne recevrait pas les événements produits sur
 * l'instance B. Le MVP tourne en une seule réplique ; passer à l'échelle
 * demanderait un relais partagé (Redis, ou LISTEN/NOTIFY de PostgreSQL).
 */
@Service
public class GroupEventService {

    private static final Logger log = LoggerFactory.getLogger(GroupEventService.class);

    /** Au-delà, le navigateur rouvre la connexion de lui-même. */
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, Set<SseEmitter>> emittersByGroup = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID groupId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        emittersByGroup
                .computeIfAbsent(groupId, key -> ConcurrentHashMap.newKeySet())
                .add(emitter);

        // Sans retrait sur fermeture, la liste grossirait indéfiniment : c'est
        // la fuite de mémoire classique de ce mécanisme.
        emitter.onCompletion(() -> remove(groupId, emitter));
        emitter.onTimeout(() -> remove(groupId, emitter));
        emitter.onError(error -> remove(groupId, emitter));

        try {
            // Premier message immédiat : confirme au client que le flux est ouvert.
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove(groupId, emitter);
        }

        log.debug("Abonnement au groupe {} ({} connexions)", groupId, countFor(groupId));
        return emitter;
    }

    public void publish(UUID groupId, String eventName, Object payload) {
        Set<SseEmitter> emitters = emittersByGroup.get(groupId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException e) {
                // Client parti sans prévenir : on nettoie sans bruit.
                remove(groupId, emitter);
            }
        }
        log.debug("Événement « {} » diffusé au groupe {}", eventName, groupId);
    }

    /**
     * Maintient les connexions ouvertes. Les proxies coupent en général une
     * connexion inactive au bout d'une minute ; ce battement l'évite.
     */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emittersByGroup.forEach((groupId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    remove(groupId, emitter);
                }
            }
        });
    }

    private void remove(UUID groupId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByGroup.get(groupId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByGroup.remove(groupId);
            }
        }
    }

    int countFor(UUID groupId) {
        Set<SseEmitter> emitters = emittersByGroup.get(groupId);
        return emitters == null ? 0 : emitters.size();
    }
}
