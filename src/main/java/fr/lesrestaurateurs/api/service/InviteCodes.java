package fr.lesrestaurateurs.api.service;

import java.security.SecureRandom;

/**
 * Codes d'invitation courts, destinés à être lus et parfois recopiés à la main.
 *
 * <p>L'alphabet exclut les caractères qui se confondent à l'oral ou à l'écrit
 * (O et 0, I et 1 et L) pour éviter les erreurs de saisie.
 */
public final class InviteCodes {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodes() {
    }

    public static String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Tolère les minuscules et les espaces d'un code recopié à la main. */
    public static String normalize(String code) {
        return code == null ? null : code.trim().replace(" ", "").toUpperCase();
    }
}
