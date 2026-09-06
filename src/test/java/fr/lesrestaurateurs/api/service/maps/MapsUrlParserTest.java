package fr.lesrestaurateurs.api.service.maps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Le format des URL Google Maps n'est pas documenté et peut changer. Ces cas
 * servent de témoin : s'ils cassent un jour, c'est que Google a modifié son
 * format et que l'extraction doit être revue.
 */
class MapsUrlParserTest {

    @Test
    @DisplayName("Lien de fiche classique : nom et position du marqueur")
    void ficheClassique() {
        String url = "https://www.google.com/maps/place/Chez+Aline/@48.8566,2.3522,17z/"
                + "data=!3m1!4b1!4m6!3m5!1s0x47e66e1f0!8m2!3d48.8571!4d2.3699";

        MapsLink link = MapsUrlParser.parse(url);

        assertThat(link.name()).isEqualTo("Chez Aline");
        // Le marqueur (!3d/!4d) prime sur le centre de la vue (@).
        assertThat(link.lat()).isCloseTo(48.8571, within(0.0001));
        assertThat(link.lng()).isCloseTo(2.3699, within(0.0001));
    }

    @Test
    @DisplayName("Sans segment data : repli sur le centre de la vue")
    void sansMarqueur() {
        String url = "https://www.google.com/maps/place/Le+Petit+Cambodge/@48.8698,2.3689,17z";

        MapsLink link = MapsUrlParser.parse(url);

        assertThat(link.name()).isEqualTo("Le Petit Cambodge");
        assertThat(link.lat()).isCloseTo(48.8698, within(0.0001));
    }

    @Test
    @DisplayName("Les accents et espaces encodés sont restitués")
    void nomEncode() {
        String url = "https://www.google.com/maps/place/Caf%C3%A9+de+la+Paix/@48.87,2.33,17z";

        assertThat(MapsUrlParser.parse(url).name()).isEqualTo("Café de la Paix");
    }

    @Test
    @DisplayName("Lien de recherche avec coordonnées en paramètre")
    void rechercheParCoordonnees() {
        String url = "https://www.google.com/maps/search/?api=1&query=48.8566,2.3522";

        MapsLink link = MapsUrlParser.parse(url);

        assertThat(link.lat()).isCloseTo(48.8566, within(0.0001));
        // Des coordonnées ne sont pas un nom de restaurant.
        assertThat(link.name()).isNull();
    }

    @Test
    @DisplayName("Lien de recherche par nom")
    void rechercheParNom() {
        String url = "https://www.google.com/maps/search/?api=1&query=Dong%20Huong%20Paris";

        assertThat(MapsUrlParser.parse(url).name()).isEqualTo("Dong Huong Paris");
    }

    @Test
    @DisplayName("Coordonnées négatives (hémisphère sud, ouest)")
    void coordonneesNegatives() {
        String url = "https://www.google.com/maps/place/Bar/@-33.8688,-151.2093,17z";

        MapsLink link = MapsUrlParser.parse(url);

        assertThat(link.lat()).isCloseTo(-33.8688, within(0.0001));
        assertThat(link.lng()).isCloseTo(-151.2093, within(0.0001));
    }

    @Test
    @DisplayName("URL réelle produite par le bouton Partager, une fois dépliée")
    void lienCourtDeplie() {
        // Obtenue en dépliant https://maps.app.goo.gl/4DbznkxhgtmsgSFfA
        // le 21 août 2026. Sert de témoin : si Google change son format, ce
        // test tombe avant que Julien ne découvre des fiches vides.
        String url = "https://www.google.fr/maps/place/La+Maison+du+Passeur/"
                + "@48.9911976,2.1416183,15z/data=!4m6!3m5!"
                + "1s0x47e660f95ef8e6eb:0xfd0a630a1c6c01fe!8m2!3d48.9869678!4d2.157871!"
                + "16s%2Fg%2F11bzzw_rnc?entry=tts&g_ep=EgoyMDI2MDgxOS4wIPu8ASoASAFQAw%3D%3D";

        MapsLink link = MapsUrlParser.parse(url);

        assertThat(link.name()).isEqualTo("La Maison du Passeur");
        // Le marqueur, pas le centre de la vue qui est à 48.9911976.
        assertThat(link.lat()).isCloseTo(48.9869678, within(0.0000001));
        assertThat(link.lng()).isCloseTo(2.157871, within(0.0000001));
    }

    @Test
    @DisplayName("Un lien court n'a rien à offrir avant d'être déplié")
    void lienCourtNonDeplie() {
        MapsLink link = MapsUrlParser.parse("https://maps.app.goo.gl/aBcDeF123");

        assertThat(link.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Coordonnées hors limites terrestres : ignorées")
    void coordonneesAberrantes() {
        MapsLink link = MapsUrlParser.parse("https://www.google.com/maps/place/X/@200.0,500.0,17z");

        assertThat(link.hasCoordinates()).isFalse();
    }

    @Test
    @DisplayName("Chaîne vide ou nulle : aucun plantage")
    void entreeVide() {
        assertThat(MapsUrlParser.parse(null).isEmpty()).isTrue();
        assertThat(MapsUrlParser.parse("   ").isEmpty()).isTrue();
        assertThat(MapsUrlParser.parse("pas une url").isEmpty()).isTrue();
    }
}
