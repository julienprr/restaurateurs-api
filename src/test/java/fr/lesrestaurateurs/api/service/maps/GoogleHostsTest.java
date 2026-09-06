package fr.lesrestaurateurs.api.service.maps;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Le serveur émet une requête vers l'URL collée par l'utilisateur. Sans ce
 * filtre, n'importe qui pourrait lui faire interroger une adresse interne.
 */
class GoogleHostsTest {

    @Test
    @DisplayName("Domaines Google acceptés")
    void domainesAutorises() {
        assertThat(GoogleHosts.isAllowed(URI.create("https://maps.app.goo.gl/abc"))).isTrue();
        assertThat(GoogleHosts.isAllowed(URI.create("https://goo.gl/maps/abc"))).isTrue();
        assertThat(GoogleHosts.isAllowed(URI.create("https://www.google.com/maps/place/X"))).isTrue();
        assertThat(GoogleHosts.isAllowed(URI.create("https://maps.google.fr/place/X"))).isTrue();
        assertThat(GoogleHosts.isAllowed(URI.create("https://www.google.co.uk/maps"))).isTrue();
    }

    @Test
    @DisplayName("Adresses internes refusées")
    void adressesInternes() {
        assertThat(GoogleHosts.isAllowed(URI.create("https://localhost:8081/api/health"))).isFalse();
        assertThat(GoogleHosts.isAllowed(URI.create("https://127.0.0.1/"))).isFalse();
        assertThat(GoogleHosts.isAllowed(URI.create("https://10.0.0.1/secret"))).isFalse();
        assertThat(GoogleHosts.isAllowed(URI.create("https://kubernetes.default.svc/"))).isFalse();
        // Métadonnées cloud : cible classique de ce type d'attaque.
        assertThat(GoogleHosts.isAllowed(URI.create("https://169.254.169.254/latest/meta-data/"))).isFalse();
    }

    @Test
    @DisplayName("Domaines imitant Google refusés")
    void domainesTrompeurs() {
        assertThat(GoogleHosts.isAllowed(URI.create("https://google.com.evil.fr/maps"))).isFalse();
        assertThat(GoogleHosts.isAllowed(URI.create("https://notgoogle.com/maps"))).isFalse();
        assertThat(GoogleHosts.isAllowed(URI.create("https://goo.gl.evil.fr/x"))).isFalse();
        assertThat(GoogleHosts.isAllowed(URI.create("https://evilgoogle.fr/maps"))).isFalse();
    }

    @Test
    @DisplayName("Requêtes non chiffrées refusées")
    void schemaNonHttps() {
        assertThat(GoogleHosts.isAllowed(URI.create("http://www.google.com/maps"))).isFalse();
        assertThat(GoogleHosts.isAllowed(URI.create("file:///etc/passwd"))).isFalse();
    }
}
