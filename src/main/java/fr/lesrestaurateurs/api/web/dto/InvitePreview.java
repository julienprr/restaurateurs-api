package fr.lesrestaurateurs.api.web.dto;

/**
 * Aperçu d'un groupe avant d'accepter l'invitation : juste de quoi savoir dans
 * quoi on entre, sans dévoiler la liste des membres à un inconnu.
 */
public record InvitePreview(String name, long memberCount, boolean alreadyMember) {
}
