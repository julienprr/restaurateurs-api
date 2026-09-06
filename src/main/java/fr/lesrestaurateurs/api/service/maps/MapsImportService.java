package fr.lesrestaurateurs.api.service.maps;

import fr.lesrestaurateurs.api.web.dto.MapsPreview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Transforme un lien Google Maps collé par un membre en fiche pré-remplie.
 *
 * <p>Le processus est « au mieux » : chaque étape peut échouer sans faire
 * échouer l'ensemble. L'utilisateur complète toujours ce qui manque à la main.
 */
@Service
public class MapsImportService {

    private static final Logger log = LoggerFactory.getLogger(MapsImportService.class);

    private final MapsLinkResolver resolver;
    private final NominatimClient nominatim;

    public MapsImportService(MapsLinkResolver resolver, NominatimClient nominatim) {
        this.resolver = resolver;
        this.nominatim = nominatim;
    }

    public MapsPreview preview(String rawUrl) {
        String longUrl = resolver.resolve(rawUrl);
        MapsLink link = MapsUrlParser.parse(longUrl);

        String address = null;
        String city = null;

        if (link.hasCoordinates()) {
            var geocode = nominatim.reverse(link.lat(), link.lng());
            if (geocode.isPresent()) {
                address = geocode.get().address();
                city = geocode.get().city();
            }
        }

        log.debug("Import Maps : nom={}, coordonnées={}, ville={}",
                link.name(), link.hasCoordinates(), city);

        return new MapsPreview(
                link.name(),
                address,
                city,
                link.lat(),
                link.lng(),
                // On garde le lien collé par l'utilisateur, plus court et
                // partageable, plutôt que l'URL dépliée.
                rawUrl.trim(),
                !link.isEmpty()
        );
    }
}
