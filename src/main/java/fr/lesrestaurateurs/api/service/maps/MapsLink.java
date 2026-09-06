package fr.lesrestaurateurs.api.service.maps;

/**
 * Ce qu'on a réussi à tirer d'un lien Google Maps.
 * Tous les champs sont optionnels : l'extraction est un service au mieux, pas
 * une garantie.
 */
public record MapsLink(String name, Double lat, Double lng, String mapsUrl) {

    public boolean hasCoordinates() {
        return lat != null && lng != null;
    }

    public boolean isEmpty() {
        return name == null && !hasCoordinates();
    }
}
