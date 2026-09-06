package fr.lesrestaurateurs.api.service.maps;

import fr.lesrestaurateurs.api.web.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Déplie les liens courts {@code maps.app.goo.gl} pour retrouver l'URL longue,
 * seule porteuse du nom et des coordonnées.
 *
 * <p>Les redirections sont suivies à la main plutôt que par {@link HttpClient} :
 * cela permet de vérifier que chaque saut reste sur un domaine Google.
 */
@Component
public class MapsLinkResolver {

    private static final Logger log = LoggerFactory.getLogger(MapsLinkResolver.class);

    private static final int MAX_REDIRECTS = 5;
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    /**
     * Ne surtout pas se faire passer pour un navigateur.
     *
     * <p>Face à un User-Agent de navigateur de bureau, {@code maps.app.goo.gl}
     * répond 200 avec une page intermédiaire dont la redirection est faite en
     * JavaScript : il n'y a alors aucun en-tête {@code Location} à suivre, et
     * l'extraction échoue. Avec n'importe quel autre client, Google renvoie une
     * redirection 302 tout à fait normale.
     *
     * <p>Vérifié le 21 août 2026 : identique en HEAD et en GET.
     */
    private static final String USER_AGENT = "LesRestaurateurs/0.1 (application privée entre amis)";

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(TIMEOUT)
            .build();

    /**
     * @return l'URL finale après redirections, ou l'URL de départ si elle n'en
     *         comporte pas
     */
    public String resolve(String rawUrl) {
        URI uri = parse(rawUrl);

        if (!GoogleHosts.isAllowed(uri)) {
            throw ApiException.badRequest(
                    "Ce lien ne vient pas de Google Maps. Colle le lien de partage de l'application.");
        }

        URI current = uri;
        for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
            Optional<URI> next = followOnce(current);
            if (next.isEmpty()) {
                return current.toString();
            }
            if (!GoogleHosts.isAllowed(next.get())) {
                log.warn("Redirection hors Google ignorée : {}", next.get().getHost());
                return current.toString();
            }
            current = next.get();
        }

        log.warn("Trop de redirections pour {}", rawUrl);
        return current.toString();
    }

    /** @return la cible de la redirection, ou vide si la réponse n'en est pas une */
    private Optional<URI> followOnce(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "fr-FR,fr;q=0.9")
                .timeout(TIMEOUT)
                .build();

        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 3) {
                return Optional.empty();
            }
            return response.headers().firstValue("location").map(uri::resolve);
        } catch (IOException e) {
            log.warn("Lien Google Maps injoignable : {}", e.getMessage());
            throw ApiException.badRequest(
                    "Impossible de lire ce lien pour le moment. Saisis les informations à la main.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.badRequest("Lecture du lien interrompue, réessaie.");
        }
    }

    private URI parse(String rawUrl) {
        try {
            return URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Ce lien n'est pas une adresse valide.");
        }
    }
}
