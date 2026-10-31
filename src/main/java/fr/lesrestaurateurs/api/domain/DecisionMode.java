package fr.lesrestaurateurs.api.domain;

/** Doit rester aligné sur la contrainte {@code outings_how_check} en base. */
public enum DecisionMode {
    /** Le restaurant le plus soutenu l'emporte, le sort départage les ex æquo. */
    VOTES,
    /** Tirage au sort parmi toutes les adresses à tester. */
    HASARD
}
