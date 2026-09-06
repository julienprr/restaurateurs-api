package fr.lesrestaurateurs.api.web.dto;

import java.util.UUID;

/** Groupe tel qu'il apparaît dans la liste « mes groupes ». */
public record GroupSummary(UUID id, String name, String inviteCode, long memberCount) {
}
