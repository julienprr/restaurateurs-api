package fr.lesrestaurateurs.api.service.maps;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Liste blanche des domaines vers lesquels le serveur accepte d'émettre une
 * requête en suivant un lien fourni par un utilisateur.
 *
 * <p>Sans ce filtre, coller {@code http://localhost:8081/...} ou une adresse
 * interne du cluster ferait interroger cette adresse par le serveur, avec ses
 * propres droits réseau. C'est la faille classique dite « SSRF ».
 */
final class GoogleHosts {

    /** google.com, google.fr, www.google.de, maps.google.co.uk... */
    private static final Pattern GOOGLE =
            Pattern.compile("^([a-z0-9-]+\\.)*google(\\.[a-z]{2,3})(\\.[a-z]{2})?$");

    /** goo.gl, maps.app.goo.gl */
    private static final Pattern GOO_GL =
            Pattern.compile("^([a-z0-9-]+\\.)*goo\\.gl$");

    private GoogleHosts() {
    }

    static boolean isAllowed(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return false;
        }
        // Uniquement HTTPS : évite de suivre un lien vers un service en clair.
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            return false;
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return GOOGLE.matcher(host).matches() || GOO_GL.matcher(host).matches();
    }
}
