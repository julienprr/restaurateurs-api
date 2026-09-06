package fr.lesrestaurateurs.api.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injecte l'utilisateur connecté dans une méthode de contrôleur.
 *
 * <p>Par défaut la route devient protégée : sans session valide, la requête est
 * rejetée en 401 avant d'atteindre le contrôleur. Utiliser
 * {@code @CurrentUser(required = false)} pour une route accessible aux deux.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {

    boolean required() default true;
}
