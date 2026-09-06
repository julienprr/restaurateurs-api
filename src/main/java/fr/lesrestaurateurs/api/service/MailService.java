package fr.lesrestaurateurs.api.service;

import fr.lesrestaurateurs.api.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    public MailService(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    /**
     * Envoi asynchrone : la réponse HTTP ne doit pas attendre le serveur SMTP.
     * Un échec d'envoi est journalisé mais n'est jamais remonté à l'appelant,
     * pour ne pas révéler si l'adresse existe.
     */
    @Async
    public void sendMagicLink(String email, String link) {
        if (properties.logMagicLink()) {
            log.info("Lien magique pour {} : {}", email, link);
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart : l'email porte une version texte et une version HTML.
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.mailFrom());
            helper.setTo(email);
            helper.setSubject("Ta connexion aux Restaurateurs");
            helper.setText(textBody(link), htmlBody(link));
            mailSender.send(message);
            log.debug("Lien magique envoyé à {}", email);
        } catch (MailException | MessagingException e) {
            log.error("Échec de l'envoi du lien magique à {}", email, e);
        }
    }

    private String textBody(String link) {
        return """
                Les Restaurateurs

                Voici ton lien de connexion :
                %s

                Il est valable %d minutes et ne fonctionne qu'une fois.
                Si tu n'as rien demandé, ignore cet email.
                """.formatted(link, properties.magicLinkTtlMinutes());
    }

    private String htmlBody(String link) {
        return """
                <!doctype html>
                <html lang="fr">
                <body style="margin:0;padding:32px 16px;background:#1F2E22;font-family:'Work Sans',Helvetica,Arial,sans-serif;color:#F5F1E6;">
                  <div style="max-width:420px;margin:0 auto;border:2px dotted #3A4D3B;border-radius:8px;padding:28px 24px;text-align:center;">
                    <h1 style="margin:0 0 24px;font-size:26px;font-weight:600;color:#F5F1E6;">Les Restaurateurs</h1>
                    <p style="margin:0 0 24px;font-size:15px;line-height:1.6;color:#93A092;">
                      Clique sur le bouton pour te connecter. Pas de mot de passe à retenir.
                    </p>
                    <a href="%s" style="display:inline-block;padding:14px 28px;border:2px dashed #E8B94A;border-radius:6px;color:#E8B94A;font-size:15px;font-weight:600;text-decoration:none;">
                      Me connecter
                    </a>
                    <p style="margin:24px 0 0;font-size:12px;line-height:1.6;color:#93A092;">
                      Ce lien est valable %d minutes et ne fonctionne qu'une fois.<br>
                      Si tu n'as rien demandé, ignore cet email.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(link, properties.magicLinkTtlMinutes());
    }
}
