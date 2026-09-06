package fr.lesrestaurateurs.api.service.maps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Géocodage inverse via Nominatim (OpenStreetMap) : gratuit, sans clé, sans
 * carte bancaire, conformément au choix du brief d'éviter les API Google
 * payantes.
 *
 * <p>La politique d'usage de Nominatim impose un {@code User-Agent} identifiant
 * l'application et une requête par seconde au maximum. Les deux sont respectés
 * ici : dépasser ces limites ferait bannir l'adresse IP du serveur.
 */
@Component
public class NominatimClient {

    private static final Logger log = LoggerFactory.getLogger(NominatimClient.class);

    private static final String ENDPOINT = "https://nominatim.openstreetmap.org/reverse";
    private static final String USER_AGENT = "LesRestaurateurs/0.1 (application privée entre amis)";
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final long MIN_INTERVAL_MS = 1100;

    /** Champs OpenStreetMap pouvant porter le nom de la commune, par ordre de préférence. */
    private static final List<String> CITY_FIELDS =
            List.of("city", "town", "village", "municipality", "suburb");

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    /** Horodatage du dernier appel, pour tenir la limite d'une requête par seconde. */
    private long lastCallAt = 0;

    /**
     * @return l'adresse trouvée, ou vide si Nominatim ne répond pas ou ne
     *         connaît pas ce point. L'échec n'est jamais bloquant : l'ajout d'un
     *         restaurant ne doit pas dépendre d'un service tiers.
     */
    public Optional<ReverseGeocode> reverse(double lat, double lng) {
        throttle();

        URI uri = URI.create("%s?format=jsonv2&lat=%s&lon=%s&zoom=18&addressdetails=1&accept-language=fr"
                .formatted(ENDPOINT, lat, lng));

        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", USER_AGENT)
                .timeout(TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Nominatim a répondu {}", response.statusCode());
                return Optional.empty();
            }
            return parse(response.body());
        } catch (IOException e) {
            log.warn("Nominatim injoignable : {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private Optional<ReverseGeocode> parse(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode address = root.path("address");
            if (address.isMissingNode()) {
                return Optional.empty();
            }

            String city = CITY_FIELDS.stream()
                    .map(field -> address.path(field).asText(null))
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(null);

            String street = joinNonBlank(
                    address.path("house_number").asText(null),
                    address.path("road").asText(null));

            String display = root.path("display_name").asText(null);

            return Optional.of(new ReverseGeocode(
                    street != null ? street : display,
                    city,
                    display));
        } catch (IOException e) {
            log.warn("Réponse Nominatim illisible", e);
            return Optional.empty();
        }
    }

    private static String joinNonBlank(String... parts) {
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (!joined.isEmpty()) {
                    joined.append(' ');
                }
                joined.append(part);
            }
        }
        return joined.isEmpty() ? null : joined.toString();
    }

    /** Espace les appels d'au moins une seconde, comme l'exige Nominatim. */
    private synchronized void throttle() {
        long now = System.currentTimeMillis();
        long waitFor = MIN_INTERVAL_MS - (now - lastCallAt);
        if (waitFor > 0) {
            try {
                Thread.sleep(waitFor);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallAt = System.currentTimeMillis();
    }

    public record ReverseGeocode(String address, String city, String displayName) {
    }
}
