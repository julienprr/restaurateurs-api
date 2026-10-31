-- Les notes : chaque membre note une sortie passée, de 1 à 5, avec un
-- commentaire facultatif.
--
-- La note porte sur la sortie et non sur le restaurant : retourner au même
-- endroit et le trouver moins bon doit pouvoir se dire.

create table ratings (
    id         uuid primary key     default gen_random_uuid(),
    outing_id  uuid        not null references outings (id) on delete cascade,
    user_id    uuid        not null references users (id) on delete cascade,
    score      smallint    not null,
    comment    text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint ratings_score_check check (score between 1 and 5),
    constraint ratings_comment_length check (char_length(comment) <= 500),

    -- Une note par membre et par sortie : la modifier remplace l'ancienne.
    constraint ratings_unique_per_user_and_outing unique (outing_id, user_id)
);
