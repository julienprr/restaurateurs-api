-- Les sorties : un soir où le groupe va quelque part.
--
-- Jusqu'ici, voter et trancher ne laissaient aucune trace : le résultat d'une
-- décision était calculé, diffusé, puis oublié. Cette migration introduit
-- l'objet qui manquait, et rattache l'existant.

create table outings (
    id            uuid primary key     default gen_random_uuid(),
    group_id      uuid        not null references groups (id) on delete cascade,

    -- La date pilote tout le cycle de vie : une sortie devient passée quand
    -- elle est derrière nous, pas sur action d'un membre.
    planned_for   date        not null,

    -- Nul tant que le groupe n'a pas tranché.
    restaurant_id uuid references restaurants (id) on delete set null,
    decided_by    uuid references users (id) on delete set null,
    decided_how   text,
    decided_at    timestamptz,

    status        text        not null default 'PLANIFIEE',
    created_by    uuid references users (id) on delete set null,
    created_at    timestamptz not null default now(),

    constraint outings_status_check check (status in ('PLANIFIEE', 'PASSEE', 'ANNULEE')),
    constraint outings_how_check check (decided_how is null or decided_how in ('VOTES', 'HASARD'))
);

create index idx_outings_group on outings (group_id, planned_for desc);

-- Un groupe n'a qu'une sortie en préparation à la fois. La règle est tenue par
-- la base plutôt que par le code : elle ne peut pas être contournée par un
-- double clic ou deux requêtes simultanées.
create unique index outings_one_planned_per_group
    on outings (group_id) where status = 'PLANIFIEE';

-- --------------------------------------------------------------------------
-- Les votes appartiennent désormais à une sortie, non plus au groupe.
-- --------------------------------------------------------------------------

alter table votes add column outing_id uuid references outings (id) on delete cascade;

-- Chaque restaurant déjà fait devient une sortie passée. La date retenue est
-- celle du dernier vote quand il y en a un, sinon celle de l'ajout du
-- restaurant : approximatif, mais corrigeable depuis l'application.
insert into outings (group_id, restaurant_id, planned_for, status, decided_how, decided_at, created_at)
select r.group_id,
       r.id,
       coalesce(max(v.created_at)::date, r.created_at::date),
       'PASSEE',
       case when count(v.id) > 0 then 'VOTES' end,
       coalesce(max(v.created_at), r.created_at),
       now()
from restaurants r
         left join votes v on v.restaurant_id = r.id
where r.status = 'DEJA_FAIT'
group by r.id, r.group_id, r.created_at;

-- Les votes qui portaient sur ces restaurants rejoignent leur sortie.
update votes v
set outing_id = o.id
from outings o
where o.restaurant_id = v.restaurant_id
  and o.status = 'PASSEE'
  and v.outing_id is null;

-- Les votes restants portaient sur des restaurants encore à tester : ils ne
-- correspondent à aucune sortie et n'en auront jamais. Au 31 octobre 2026, la
-- production n'en comptait aucun.
delete from votes where outing_id is null;

alter table votes alter column outing_id set not null;

-- Un membre vote une fois par restaurant ET par sortie : revoter pour la même
-- adresse lors d'une sortie suivante doit rester possible.
alter table votes drop constraint votes_unique_per_user;
alter table votes add constraint votes_unique_per_user_and_outing
    unique (outing_id, restaurant_id, user_id);

create index idx_votes_outing on votes (outing_id);
