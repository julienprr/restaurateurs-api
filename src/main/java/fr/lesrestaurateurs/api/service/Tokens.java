package fr.lesrestaurateurs.api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Génération et hachage des jetons (liens magiques, sessions).
 *
 * <p>Les jetons ne sont jamais stockés en clair : la base ne contient que leur
 * empreinte SHA-256. Une lecture de la base ne permet donc pas d'usurper une
 * session. Le jeton étant déjà 256 bits d'aléa, un simple SHA-256 suffit :
 * contrairement à un mot de passe, il n'est pas devinable par force brute.
 */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private Tokens() {
    }

    /** Jeton aléatoire de 256 bits, transportable dans une URL. */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
