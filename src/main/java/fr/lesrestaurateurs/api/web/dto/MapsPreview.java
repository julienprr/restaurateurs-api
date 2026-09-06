package fr.lesrestaurateurs.api.web.dto;

/**
 * Fiche pré-remplie proposée à l'utilisateur après collage d'un lien.
 *
 * @param found false quand le lien n'a rien donné : le front bascule alors sur
 *              la saisie manuelle, en conservant le lien
 */
public record MapsPreview(
        String name,
        String address,
        String city,
        Double lat,
        Double lng,
        String mapsUrl,
        boolean found
) {
}
