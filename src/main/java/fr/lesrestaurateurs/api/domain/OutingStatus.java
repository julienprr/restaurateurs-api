package fr.lesrestaurateurs.api.domain;

/** Doit rester aligné sur la contrainte {@code outings_status_check} en base. */
public enum OutingStatus {
    /** La date est devant nous : on vote, on peut encore changer de restaurant. */
    PLANIFIEE,
    /** La date est derrière nous : on note, plus rien ne bouge. */
    PASSEE,
    /** La date est passée sans qu'aucun restaurant n'ait été choisi. */
    ANNULEE
}
