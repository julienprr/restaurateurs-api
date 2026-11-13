package fr.lesrestaurateurs.api.service.notifications;

import fr.lesrestaurateurs.api.config.AppProperties;
import fr.lesrestaurateurs.api.domain.DecisionMode;
import fr.lesrestaurateurs.api.domain.Outing;
import fr.lesrestaurateurs.api.domain.Restaurant;
import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.service.OutingService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Le texte des emails de sortie, en version texte et HTML.
 *
 * <p>Tout ce que saisissent les membres (noms de groupe, de restaurant,
 * prénoms) est échappé dans la version HTML : un restaurant nommé
 * {@code <a href=...>} ne doit pas devenir un lien.
 *
 * <p>Les liens partent tous de {@code app.base-url} : une erreur sur cette
 * variable ne casse rien de visible, mais envoie les membres ailleurs.
 */
@Component
public class NotificationMails {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);

    /** Annonce de la version « sorties ». Changer la clé pour une future annonce. */
    public static final String RELEASE_KEY = "sorties-2026-11";

    private final AppProperties properties;

    public NotificationMails(AppProperties properties) {
        this.properties = properties;
    }

    /** « Allez voter » : une sortie vient d'être prévue. À lire dans une transaction. */
    public OutgoingMail planned(User to, Outing outing) {
        String group = outing.getGroup().getName();
        String when = when(outing.getPlannedFor());
        String who = outing.getCreatedBy() == null
                ? "Une sortie est prévue " + when + "."
                : outing.getCreatedBy().getDisplayName() + " propose une sortie " + when + ".";
        return compose(to, group,
                group + " : sortie prévue " + when,
                List.of(who, "Vote pour les adresses qui te tentent, puis le groupe tranchera."),
                "Voter", groupLink(outing, "sortie"));
    }

    /** « On va là » : le restaurant est choisi, ou remplacé. À lire dans une transaction. */
    public OutgoingMail decided(User to, Outing outing, boolean changed) {
        String group = outing.getGroup().getName();
        String when = when(outing.getPlannedFor());
        Restaurant restaurant = outing.getRestaurant();
        String how = outing.getDecidedHow() == DecisionMode.VOTES ? "Le vote a parlé" : "Le sort a parlé";

        List<String> paragraphs = new ArrayList<>();
        paragraphs.add((changed ? "Changement de programme. " : "")
                + how + " : " + when + ", on va chez " + restaurant.getName() + ".");
        String place = placeOf(restaurant);
        if (place != null) {
            paragraphs.add(place);
        }
        if (outing.getDecidedBy() != null) {
            paragraphs.add("Décision lancée par " + outing.getDecidedBy().getDisplayName() + ".");
        }

        String subject = group + " : " + (changed ? "changement, on va chez " : "on va chez ")
                + restaurant.getName() + " " + when;
        return compose(to, group, subject, paragraphs, "Voir la sortie", groupLink(outing, "sortie"));
    }

    /** « Notez la sortie » : le lendemain, à la clôture. À lire dans une transaction. */
    public OutgoingMail rateIt(User to, Outing outing) {
        String group = outing.getGroup().getName();
        String restaurant = outing.getRestaurant().getName();
        return compose(to, group,
                group + " : comment c'était chez " + restaurant + " ?",
                List.of("Vous étiez chez " + restaurant + " " + when(outing.getPlannedFor()) + ".",
                        "Note la sortie de 1 à 5, avec un mot si tu veux : chaque note compte pour le classement."),
                "Noter la sortie", groupLink(outing, "sorties"));
    }

    /**
     * L'annonce de la version « sorties », à tous les inscrits.
     *
     * @param onlyGroup le groupe du destinataire s'il n'en a qu'un : le bouton
     *                  ouvre alors « Nos sorties », où l'on note. Nul sinon :
     *                  le bouton mène à l'accueil, d'où l'on choisit le groupe.
     */
    public OutgoingMail release(User to, UUID onlyGroup) {
        String link = onlyGroup == null
                ? properties.baseUrl() + "/"
                : properties.baseUrl() + "/groupes/" + onlyGroup + "?onglet=sorties";
        return compose(to, null,
                "Les Restaurateurs : place aux sorties",
                List.of("Nouveauté dans Les Restaurateurs : les sorties.",
                        "Prévois une sortie à une date, et le vote s'ouvre pour tout le groupe. "
                                + "Pas envie d'attendre ? « Choisir ce soir » tire une adresse au sort, tout de suite.",
                        "Le lendemain, chacun note la sortie de 1 à 5. Les notes font un classement : "
                                + "celui des restaurants, et celui des dénicheurs, selon les adresses qu'ils ont proposées.",
                        "Vous êtes déjà sortis ensemble ? Pense à noter tes sorties passées : "
                                + "c'est ce qui lance le classement.",
                        "Tu recevras désormais un email quand une sortie est prévue, quand le restaurant est choisi, "
                                + "et le lendemain pour la noter. Un lien en bas de chaque email permet de les couper."),
                "Noter mes sorties", link);
    }

    /**
     * « ce soir », « demain soir », « hier soir », ou « le samedi 7 novembre »,
     * jugé à l'heure de Paris au moment de l'envoi.
     */
    static String when(LocalDate date) {
        LocalDate today = LocalDate.now(OutingService.ZONE);
        if (date.equals(today)) {
            return "ce soir";
        }
        if (date.equals(today.plusDays(1))) {
            return "demain soir";
        }
        if (date.equals(today.minusDays(1))) {
            return "hier soir";
        }
        return "le " + date.format(DAY);
    }

    /** L'adresse si on l'a, sinon la ville. L'adresse importée contient souvent déjà la ville. */
    private static String placeOf(Restaurant restaurant) {
        if (restaurant.getAddress() != null && !restaurant.getAddress().isBlank()) {
            return restaurant.getAddress();
        }
        return restaurant.getCity() != null && !restaurant.getCity().isBlank() ? restaurant.getCity() : null;
    }

    private String groupLink(Outing outing, String tab) {
        return properties.baseUrl() + "/groupes/" + outing.getGroup().getId() + "?onglet=" + tab;
    }

    /**
     * Assemble l'email. Le lien de désinscription du corps mène à une page
     * avec un bouton, pas directement à la désinscription : les antivirus de
     * messagerie ouvrent les liens pour les vérifier, et désinscriraient les
     * gens à leur insu. L'en-tête, lui, pointe sur la route en un clic, que
     * les messageries appellent en POST.
     *
     * @param group nom du groupe concerné, nul pour une annonce générale
     */
    private OutgoingMail compose(User to, String group, String subject, List<String> paragraphs,
                                 String button, String link) {
        String token = to.getUnsubscribeToken();
        String pageUrl = properties.baseUrl() + "/desinscription?token=" + token;
        String oneClickUrl = properties.baseUrl() + "/api/notifications/unsubscribe?token=" + token;
        String why = group == null
                ? "Tu reçois cet email parce que tu as un compte sur Les Restaurateurs."
                : "Tu reçois cet email parce que tu fais partie de « " + group + " ».";

        String text = "Les Restaurateurs\n\n"
                + String.join("\n\n", paragraphs)
                + "\n\n" + button + " : " + link
                + "\n\n--\n" + why + "\nNe plus recevoir ces emails : " + pageUrl + "\n";

        String html = """
                <!doctype html>
                <html lang="fr">
                <body style="margin:0;padding:32px 16px;background:#1F2E22;font-family:'Work Sans',Helvetica,Arial,sans-serif;color:#F5F1E6;">
                  <div style="max-width:420px;margin:0 auto;border:2px dotted #3A4D3B;border-radius:8px;padding:28px 24px;text-align:center;">
                    <h1 style="margin:0 0 24px;font-size:26px;font-weight:600;color:#F5F1E6;">Les Restaurateurs</h1>
                    %s
                    <a href="%s" style="display:inline-block;margin-top:8px;padding:14px 28px;border:2px dashed #E8B94A;border-radius:6px;color:#E8B94A;font-size:15px;font-weight:600;text-decoration:none;">
                      %s
                    </a>
                    <p style="margin:28px 0 0;font-size:12px;line-height:1.6;color:#93A092;">
                      %s<br>
                      <a href="%s" style="color:#93A092;">Ne plus recevoir ces emails</a>
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(
                paragraphs.stream()
                        .map(p -> "<p style=\"margin:0 0 16px;font-size:15px;line-height:1.6;color:#F5F1E6;\">"
                                + HtmlUtils.htmlEscape(p) + "</p>")
                        .collect(Collectors.joining("\n")),
                HtmlUtils.htmlEscape(link),
                HtmlUtils.htmlEscape(button),
                HtmlUtils.htmlEscape(why),
                HtmlUtils.htmlEscape(pageUrl));

        return new OutgoingMail(to.getEmail(), subject, text, html, oneClickUrl);
    }
}
