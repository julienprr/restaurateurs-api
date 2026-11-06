-- Les emails de sortie : « Allez voter », « On va là », « Notez la sortie », et
-- l'annonce ponctuelle d'une nouvelle version.

-- Chacun peut couper ces emails. Ceux de connexion, eux, partent toujours :
-- sans eux, on ne peut plus se connecter.
alter table users add column notifications_enabled boolean not null default true;

-- Le jeton du lien « ne plus recevoir ces emails », qui doit marcher sans être
-- connecté. Contrairement aux jetons de session, il est stocké en clair : il
-- faut pouvoir le remettre dans chaque email, et il ne permet rien d'autre
-- que couper ces emails.
--
-- Deux UUID aléatoires mis bout à bout : 244 bits de hasard, sans dépendre de
-- l'extension pgcrypto. Le défaut sert aussi aux comptes créés plus tard.
alter table users add column unsubscribe_token text not null
    default replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '');
alter table users add constraint users_unsubscribe_token_key unique (unsubscribe_token);

-- Les annonces déjà envoyées. Une annonce part une fois : rejouer la commande
-- par erreur ne doit pas spammer tous les inscrits.
create table announcements (
    key        text primary key,
    sent_at    timestamptz not null default now(),
    recipients integer     not null
);
