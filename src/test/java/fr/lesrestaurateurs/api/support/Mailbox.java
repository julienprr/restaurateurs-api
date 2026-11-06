package fr.lesrestaurateurs.api.support;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Remplace l'envoi SMTP dans les tests : les messages sont gardés en mémoire
 * au lieu de partir. Les envois étant asynchrones, il faut les attendre.
 *
 * <p>La base est partagée entre les tests : on filtre toujours par
 * destinataire, chaque test ayant ses propres utilisateurs.
 */
@TestComponent
@Primary
public class Mailbox extends JavaMailSenderImpl {

    private final List<MimeMessage> received = Collections.synchronizedList(new ArrayList<>());

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
        for (MimeMessage message : mimeMessages) {
            try {
                // Comme le vrai envoi : fixe les en-têtes MIME, sans quoi le
                // message se présente comme du texte brut.
                message.saveChanges();
            } catch (MessagingException e) {
                throw new IllegalStateException(e);
            }
            received.add(message);
        }
    }

    /** Les messages reçus par cette adresse, en attendant qu'il y en ait au moins {@code count}. */
    public List<Mail> awaitFor(String email, int count) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        List<Mail> mails = to(email);
        while (mails.size() < count && Instant.now().isBefore(deadline)) {
            pause(50);
            mails = to(email);
        }
        return mails;
    }

    /**
     * Ce qu'a reçu cette adresse après un court délai. Pour vérifier qu'un
     * email n'est PAS parti, faute de pouvoir attendre un événement absent.
     */
    public List<Mail> settledFor(String email) {
        pause(400);
        return to(email);
    }

    private List<Mail> to(String email) {
        synchronized (received) {
            return received.stream()
                    .map(Mail::of)
                    .filter(mail -> mail.to().equals(email))
                    .toList();
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Un message reçu, réduit à ce que les tests regardent. */
    public record Mail(String to, String subject, String text, String html, String listUnsubscribe) {

        static Mail of(MimeMessage message) {
            try {
                String to = ((InternetAddress) message.getAllRecipients()[0]).getAddress();
                return new Mail(to, message.getSubject(), part(message, "text/plain"),
                        part(message, "text/html"), message.getHeader("List-Unsubscribe", null));
            } catch (MessagingException | IOException e) {
                throw new IllegalStateException(e);
            }
        }

        /** Cherche la partie du type demandé dans l'arbre multipart. */
        private static String part(Part part, String type) throws MessagingException, IOException {
            if (part.isMimeType(type)) {
                return (String) part.getContent();
            }
            if (part.getContent() instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart child = multipart.getBodyPart(i);
                    String found = part(child, type);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }
    }
}
