package fr.lesrestaurateurs.api.service.maps;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extraction du nom et des coordonnées depuis une URL Google Maps.
 *
 * <p>Classe volontairement pure : aucun appel réseau, tout est déductible de la
 * chaîne d'URL. Google ne documente pas ce format et peut le changer sans
 * préavis, d'où le repli systématique sur la saisie manuelle en cas d'échec.
 */
public final class MapsUrlParser {

    /**
     * Position du marqueur, encodée dans le segment {@code data=} sous la forme
     * {@code !3d<latitude>!4d<longitude>}. C'est la source la plus fiable : elle
     * désigne le lieu lui-même.
     */
    private static final Pattern MARKER_COORDS =
            Pattern.compile("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)");

    /**
     * Centre de la vue, sous la forme {@code @<latitude>,<longitude>,<zoom>}.
     * Moins précis que le marqueur : c'est le cadrage de la carte, qui peut être
     * légèrement décalé par rapport au lieu.
     */
    private static final Pattern VIEWPORT_COORDS =
            Pattern.compile("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)");

    /** Nom du lieu, dans le segment {@code /place/<nom>/}. */
    private static final Pattern PLACE_NAME =
            Pattern.compile("/place/([^/@?]+)");

    /** Coordonnées passées en paramètre, par exemple {@code ?q=48.85,2.35}. */
    private static final Pattern QUERY_COORDS =
            Pattern.compile("^(-?\\d+\\.\\d+),\\s*(-?\\d+\\.\\d+)$");

    private MapsUrlParser() {
    }

    public static MapsLink parse(String url) {
        if (url == null || url.isBlank()) {
            return new MapsLink(null, null, null, url);
        }

        String name = extractName(url);
        double[] coords = extractCoordinates(url);

        return new MapsLink(
                name,
                coords == null ? null : coords[0],
                coords == null ? null : coords[1],
                url
        );
    }

    private static double[] extractCoordinates(String url) {
        Matcher marker = MARKER_COORDS.matcher(url);
        if (marker.find()) {
            return parseCoords(marker.group(1), marker.group(2));
        }

        Matcher viewport = VIEWPORT_COORDS.matcher(url);
        if (viewport.find()) {
            return parseCoords(viewport.group(1), viewport.group(2));
        }

        String query = queryParam(url, "q");
        if (query == null) {
            query = queryParam(url, "query");
        }
        if (query != null) {
            Matcher coords = QUERY_COORDS.matcher(query.trim());
            if (coords.matches()) {
                return parseCoords(coords.group(1), coords.group(2));
            }
        }
        return null;
    }

    private static double[] parseCoords(String lat, String lng) {
        try {
            double latitude = Double.parseDouble(lat);
            double longitude = Double.parseDouble(lng);
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                return null;
            }
            return new double[] { latitude, longitude };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String extractName(String url) {
        Matcher place = PLACE_NAME.matcher(url);
        if (place.find()) {
            String candidate = clean(place.group(1));
            // Google met parfois les coordonnées à la place du nom.
            if (candidate != null && !QUERY_COORDS.matcher(candidate).matches()) {
                return candidate;
            }
        }

        // Lien de recherche : ?q=Chez+Aline ou ?query=Chez%20Aline
        String query = queryParam(url, "q");
        if (query == null) {
            query = queryParam(url, "query");
        }
        if (query != null && !QUERY_COORDS.matcher(query.trim()).matches()) {
            return clean(query);
        }
        return null;
    }

    /** Décode le nom et enlève les artefacts d'URL ({@code +}, {@code %20}). */
    private static String clean(String raw) {
        try {
            String decoded = URLDecoder.decode(raw.replace('+', ' '), StandardCharsets.UTF_8);
            String trimmed = decoded.replaceAll("\\s+", " ").trim();
            return trimmed.isEmpty() ? null : trimmed;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String queryParam(String url, String name) {
        try {
            String query = URI.create(url).getRawQuery();
            if (query == null) {
                return null;
            }
            for (String pair : query.split("&")) {
                int equals = pair.indexOf('=');
                if (equals > 0 && pair.substring(0, equals).toLowerCase(Locale.ROOT).equals(name)) {
                    return URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
                }
            }
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
