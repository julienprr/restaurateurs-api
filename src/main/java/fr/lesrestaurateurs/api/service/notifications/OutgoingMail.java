package fr.lesrestaurateurs.api.service.notifications;

/**
 * Un email prêt à partir, pour un seul destinataire. Rien que des chaînes :
 * l'envoi est asynchrone et ne doit dépendre d'aucune entité JPA.
 *
 * @param unsubscribeUrl adresse de désinscription en un clic, placée dans
 *                       l'en-tête {@code List-Unsubscribe} ; nulle pour un
 *                       email qu'on ne peut pas couper
 */
public record OutgoingMail(
        String to,
        String subject,
        String text,
        String html,
        String unsubscribeUrl
) {
}
