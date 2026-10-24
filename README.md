# Les Restaurateurs — API

API REST de l'application. Java 21, Spring Boot 3.5, PostgreSQL, Flyway.

Le front vit dans [restaurateurs-web](https://github.com/julienprr/restaurateurs-web),
les manifests de déploiement dans
[vps-cluster-k3s](https://github.com/julienprr/vps-cluster-k3s).

En production : https://restaurateurs.julienprr.com

## Démarrer en local

```bash
docker compose up -d          # Postgres + boîte mail de test
mvn spring-boot:run           # http://localhost:8081
```

Pour tout arrêter : `docker compose down`. Les données survivent, elles vivent
dans un volume. `docker compose down -v` les efface aussi.

| Service | URL |
|---|---|
| API | http://localhost:8081/api/health |
| Boîte mail de test | http://localhost:8025 |

**En développement, aucun email ne part sur Internet** : tout arrive dans
Mailpit. Le lien de connexion est aussi écrit dans les logs.

> L'API écoute sur **8081** et non 8080, ce dernier étant souvent déjà pris.

## Tester

```bash
mvn test
```

## Carte du code

```
config/     AppProperties (réglages app.*), WebConfig (@CurrentUser, async, cron)
domain/     Entités JPA : User, UserGroup, Restaurant, Vote, AuthSession,
            MagicLinkToken, RestaurantStatus
repo/       Repositories Spring Data
security/   AuthFilter (résout le cookie), @CurrentUser + son resolver (403/401)
service/    Règles métier
  AuthService         liens magiques, sessions, anti-spam
  GroupService        création, invitations, appartenance
  RestaurantService   ajout, statut, suppression
  VoteService         vote bascule, tirage au sort, règles de décision
  GroupEventService   connexions SSE en mémoire, diffusion par groupe
  MailService         envoi asynchrone, versions texte et HTML
  Tokens, InviteCodes génération et hachage
  ExpiredTokenCleanup purge quotidienne
service/maps/   Import depuis un lien Google Maps
  MapsLinkResolver    déplie les liens courts, filtre les domaines
  MapsUrlParser       extrait nom et coordonnées (pur, testé)
  NominatimClient     géocodage inverse, 1 req/s
  GoogleHosts         liste blanche anti-SSRF (testé)
web/        Contrôleurs REST + DTO + gestion d'erreurs
db/migration/   Schéma, versionné par Flyway
```

## Routes

| Méthode | Route | Rôle |
|---|---|---|
| POST | `/api/auth/request-link` | demande un lien de connexion |
| GET | `/api/auth/verify` | consomme le lien, pose le cookie |
| GET | `/api/auth/me` | utilisateur courant, ou `null` |
| POST | `/api/auth/logout` | supprime la session |
| GET | `/api/groups` | mes groupes |
| POST | `/api/groups` | créer un groupe |
| GET | `/api/groups/{id}` | détail, avec les membres |
| GET | `/api/invites/{code}` | aperçu d'une invitation (accessible sans compte) |
| POST | `/api/invites/{code}/join` | rejoindre |
| GET | `/api/groups/{id}/restaurants` | la liste, avec les votes |
| POST | `/api/groups/{id}/restaurants` | ajouter |
| PATCH | `/api/restaurants/{id}/status` | bascule à tester / déjà fait |
| DELETE | `/api/restaurants/{id}` | retirer |
| POST | `/api/maps/preview` | lit un lien Google Maps |
| POST | `/api/restaurants/{id}/vote` | voter, bascule |
| DELETE | `/api/groups/{id}/votes` | remettre les votes à zéro |
| POST | `/api/groups/{id}/draw?mode=votes\|hasard` | trancher |
| GET | `/api/groups/{id}/events` | flux temps réel (SSE) |
| GET | `/api/health` | sonde de vie |

## Configuration

Tout passe par des variables d'environnement.

| Variable | Défaut | Rôle |
|---|---|---|
| `PORT` | `8081` | port d'écoute |
| `DB_URL` | `jdbc:postgresql://localhost:5432/restaurateurs` | base |
| `DB_USER` / `DB_PASSWORD` | `resto` / `resto` | identifiants |
| `SMTP_HOST` / `SMTP_PORT` | `localhost` / `1025` | relais mail |
| `SMTP_USER` / `SMTP_PASSWORD` | vide | authentification SMTP |
| `SMTP_AUTH` / `SMTP_STARTTLS` | `false` | `true` en production |
| `APP_BASE_URL` | `http://localhost:5173` | **sert à construire les liens de connexion** |
| `MAIL_FROM` | `les-restaurateurs@localhost` | expéditeur |
| `COOKIE_SECURE` | `false` | `true` en production |
| `LOG_MAGIC_LINK` | `true` | `false` en production |

> `APP_BASE_URL` est la variable la plus dangereuse du projet. Une erreur ici
> n'a **aucun symptôme visible** : l'application tourne, les emails partent,
> mais les liens mènent ailleurs et personne ne peut se connecter. C'est
> arrivé en production le 9 octobre 2026.

## Déploiement

Automatique : un `git push` sur `main` lance les tests, construit l'image,
met à jour le Deployment et vérifie que le pod redémarre.

Le détail est dans le `RUNBOOK.md` du dépôt d'infrastructure.
