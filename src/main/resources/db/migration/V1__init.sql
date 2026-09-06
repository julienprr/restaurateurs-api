-- Schéma initial des Restaurateurs (v1)

create table users (
    id           uuid primary key     default gen_random_uuid(),
    email        text        not null unique,
    display_name text,
    created_at   timestamptz not null default now()
);

-- Jetons de connexion par lien magique. On ne stocke que le hash du jeton :
-- une fuite de la base ne permet donc pas de se connecter.
create table magic_link_tokens (
    id          uuid primary key     default gen_random_uuid(),
    token_hash  text        not null unique,
    email       text        not null,
    invite_code text, -- groupe à rejoindre automatiquement après connexion
    expires_at  timestamptz not null,
    consumed_at timestamptz,
    created_at  timestamptz not null default now()
);
create index idx_magic_link_tokens_email on magic_link_tokens (email);

create table sessions (
    id         uuid primary key     default gen_random_uuid(),
    token_hash text        not null unique,
    user_id    uuid        not null references users (id) on delete cascade,
    expires_at timestamptz not null,
    created_at timestamptz not null default now()
);
create index idx_sessions_user on sessions (user_id);

create table groups (
    id          uuid primary key     default gen_random_uuid(),
    name        text        not null,
    invite_code text        not null unique,
    created_by  uuid references users (id) on delete set null,
    created_at  timestamptz not null default now()
);

create table group_members (
    group_id  uuid        not null references groups (id) on delete cascade,
    user_id   uuid        not null references users (id) on delete cascade,
    joined_at timestamptz not null default now(),
    primary key (group_id, user_id)
);

create table restaurants (
    id         uuid primary key     default gen_random_uuid(),
    group_id   uuid        not null references groups (id) on delete cascade,
    name       text        not null,
    address    text,
    city       text,
    lat        double precision,
    lng        double precision,
    maps_url   text,
    cuisine    text,
    budget     text,
    status     text        not null default 'A_TESTER', -- A_TESTER | DEJA_FAIT
    added_by   uuid references users (id) on delete set null,
    created_at timestamptz not null default now(),
    constraint restaurants_status_check check (status in ('A_TESTER', 'DEJA_FAIT'))
);
create index idx_restaurants_group on restaurants (group_id);

-- Un membre ne peut voter qu'une fois par restaurant (le vote est un bascule).
create table votes (
    id            uuid primary key     default gen_random_uuid(),
    restaurant_id uuid        not null references restaurants (id) on delete cascade,
    group_id      uuid        not null references groups (id) on delete cascade,
    user_id       uuid        not null references users (id) on delete cascade,
    created_at    timestamptz not null default now(),
    constraint votes_unique_per_user unique (restaurant_id, user_id)
);
create index idx_votes_group on votes (group_id);
