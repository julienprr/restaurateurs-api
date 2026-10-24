# Les Restaurateurs — Brief produit & technique

## 1. Objectif

Application web mobile-first destinée à un groupe d'amis fermé ("Les Restaurateurs"), à l'origine un groupe WhatsApp créé pour lister des idées de restaurants avant une sortie, mais tombé en désuétude faute d'outil adapté.

Le but : redonner vie à ce groupe et pousser les amis à sortir manger ensemble plus souvent, en leur donnant un outil simple pour :
1. Alimenter une liste commune de restaurants à tester.
2. Décider rapidement, le jour J, où aller — par vote ou par tirage au sort.

Priorité produit : simplicité et rapidité d'usage avant tout. Pas de friction d'inscription, pas de fonctionnalités superflues en v1. On valide l'usage central avant d'enrichir.

## 2. Fonctionnalités — MVP (v1)

**Auth & groupe**
- Connexion par lien magique envoyé par email (pas de mot de passe, pas d'OAuth)
- Créer un groupe
- Rejoindre un groupe via un lien d'invitation partageable

**Restaurants**
- Ajouter un restaurant en collant un lien Google Maps (voir § 6.3 pour le détail du flux d'extraction)
- Repli sur saisie manuelle si l'extraction automatique échoue
- Voir la liste des restaurants du groupe (nom, ville/adresse, lien vers la fiche Google Maps)
- Marquer un restaurant "à tester" / "déjà fait"

**Décider ce soir**
- Tirage au sort aléatoire parmi les restaurants "à tester"
- Vote simple : chaque membre peut voter (+1) pour un ou plusieurs restaurants, compteur mis à jour en temps réel (SSE) pour tout le groupe
- Règle de décision : le restaurant avec le plus de votes gagne ; en cas d'égalité, tirage au sort parmi les ex-æquo

**Hors périmètre v1 (volontairement)** : filtres, commentaires/notes, historique des sorties, notifications, recherche de dispo commune, multi-groupes soigné.

## 3. Fonctionnalités futures

**V2 — rendre l'outil plus riche**
- Commentaires et notes par membre sur chaque restaurant
- Filtres (ville, type de cuisine, budget, distance)
- Vote plus riche (note sur 5, plusieurs tours en cas d'égalité persistante)
- Historique des sorties passées (qui a proposé quoi, note finale du groupe)
- Notifications (push web, ou retour de message dans le groupe WhatsApp d'origine)

**V3 — bonus / gamification**
- Statistiques du groupe (qui propose le plus, etc.)
- Recherche de disponibilité commune type Doodle
- Lien direct vers réservation (TheFork)
- Carte interactive des restaurants du groupe
- Animation "roulette" pour le tirage au sort
- Gestion propre de plusieurs groupes en parallèle

## 4. Architecture technique

Architecture 100% maison, hébergée sur un VPS personnel — aucun BaaS (pas de Supabase/Firebase), pour garder la main sur l'auth et le realtime et pour l'apprentissage.

```
Navigateur / PWA (Svelte 5 + Vite)
        │  HTTPS
        ▼
     Nginx  ── sert le build statique du front
        │  reverse proxy /api et /events
        ▼
  API Spring Boot (Java)  ──► Serveur SMTP (envoi du lien magique)
        │        └────────►  Nominatim / OpenStreetMap (géocodage, gratuit)
        ▼
   PostgreSQL
```

Tout tourne sur le même VPS : Nginx en frontal (HTTPS via Let's Encrypt/Certbot), l'API Spring Boot packagée en jar exécutable et lancée en process local via systemd, PostgreSQL en local. Le front est un simple dossier de fichiers statiques généré par `vite build`.

**Aucune API Google payante n'est utilisée** (voir § 6.3) : décision volontaire pour éviter tout risque de facturation et l'obligation de rattacher une carte bancaire à un compte Google Cloud.

## 5. Stack technique

| Couche | Choix | Rôle |
|---|---|---|
| Front | Svelte 5 (runes) + Vite | SPA mobile-first |
| PWA | `vite-plugin-pwa` | Installable sur l'écran d'accueil, cache basique |
| Style | Tailwind CSS | Utilitaires responsive |
| Data fetching | TanStack Query (Svelte Query) | Cache, refetch, états de chargement |
| Backend | Java + Spring Boot (Spring Web) | API REST + endpoint SSE |
| Build back | Maven | Dépendances, packaging en jar exécutable |
| Base de données | PostgreSQL | Utilisateurs, groupes, restaurants, votes |
| Accès DB | Spring Data JPA + Hibernate | Entités, repositories |
| Migrations | Flyway | Versionner le schéma de base |
| Auth | Fait maison — token signé + email, filtre custom (sans Spring Security) | Lien magique, session en cookie `httpOnly` |
| Realtime | `SseEmitter` (Spring Web) côté back, `EventSource` côté front | Push serveur → client (votes, ajouts) |
| Emails | Spring Mail (`JavaMailSender`) + relais SMTP (ex. Brevo/Mailgun free tier) | Envoi du lien magique |
| Géocodage | Nominatim (OpenStreetMap) | Adresse à partir de coordonnées, gratuit sans clé |
| Reverse proxy | Nginx | Sert le front, route `/api` et `/events`, HTTPS |
| Process manager | systemd | Garde l'API vivante, restart auto, logs |

## 6. Flux clés

### 6.1 Lien magique (authentification)
1. L'utilisateur entre son email dans le front.
2. `POST /api/auth/request-link` : le backend génère un token aléatoire (`SecureRandom` ou `UUID.randomUUID()`), le stocke en base avec expiration courte (15 min), lié à l'email.
3. Nodemailer envoie un email avec un lien `https://<domaine>/auth/verify?token=xxx`.
4. Le clic ramène sur `/auth/verify`, qui appelle `GET /api/auth/verify?token=xxx`.
5. Le backend vérifie le token (existant, non expiré, non consommé), le marque comme utilisé, crée une session et pose un cookie `httpOnly`, `secure`, `sameSite=lax` (via `ResponseCookie` de Spring).
6. Toutes les requêtes suivantes envoient ce cookie automatiquement.

Points d'attention à l'implémentation : invalider le token après usage, limiter le nombre de demandes par email (anti-spam basique).

### 6.2 Realtime (SSE)
1. Une fois connecté, le front ouvre `new EventSource('/api/groups/:id/events')`.
2. Le backend garde la connexion HTTP ouverte et pousse un événement à chaque changement pertinent (`vote_added`, `restaurant_added`, `draw_result`).
3. Le front met à jour l'état Svelte (`$state`) à la réception, sans polling.
4. `EventSource` gère la reconnexion automatique nativement en cas de coupure.

Implémentation côté Spring : un contrôleur expose une méthode retournant un `SseEmitter` ; un service applicatif garde en mémoire une `Map<groupId, List<SseEmitter>>`, et à chaque action, boucle sur les emitters du groupe concerné pour appeler `emitter.send(...)`. Penser à retirer l'emitter de la map sur `onCompletion`/`onTimeout`/`onError` pour éviter les fuites mémoire.

### 6.3 Ajout d'un restaurant via lien Google Maps
Objectif : éviter toute API Google payante tout en minimisant la saisie manuelle.

1. Le membre colle un lien Google Maps dans le formulaire.
2. Si c'est un lien court (`maps.app.goo.gl/...`), le backend suit la redirection HTTP (via `java.net.http.HttpClient`) pour obtenir l'URL longue.
3. Parsing de l'URL longue pour en extraire :
   - le nom du lieu (segment entre `/place/` et le `/@` suivant, `+` remplacés par des espaces)
   - les coordonnées GPS (segment `@LAT,LNG,ZOOM`)
4. Si des coordonnées sont trouvées, appel à Nominatim (reverse geocoding, gratuit, sans clé, en respectant la limite d'1 req/s et un header `User-Agent` identifiant l'appli) pour obtenir une adresse formatée.
5. Le formulaire se pré-remplit (nom, ville, adresse) et affiche un aperçu avec le lien Maps original cliquable.
6. L'utilisateur ajuste manuellement les champs restants (type de cuisine, budget — via choix rapides, pas de saisie libre) et valide.
7. Stockage en base : nom, lat/lng, adresse, lien Maps d'origine, champs choisis.

Cas de repli : si l'extraction échoue (lien sans coordonnées exploitables, format non reconnu), l'utilisateur bascule sur un ajout manuel classique avec le lien Maps conservé en pièce jointe.

## 7. Modèle de données (v1)

Tables principales : `users`, `magic_link_tokens`, `groups`, `group_members`, `restaurants`, `votes`.

Relations essentielles :
- Un `user` appartient à un ou plusieurs `groups` via `group_members`.
- Un `restaurant` appartient à un `group`, a un statut (`a_tester` / `deja_fait`), et référence les coordonnées + le lien Maps d'origine.
- Un `vote` relie un `user`, un `restaurant` et un `group`.

Ces tables seront modélisées en entités JPA (`@Entity`), avec le schéma versionné par des migrations Flyway plutôt que généré par l'ORM.

## 8. Routes API (v1)

- `POST /api/auth/request-link`
- `GET /api/auth/verify`
- `POST /api/groups`
- `POST /api/groups/:id/join`
- `GET /api/groups/:id/restaurants`
- `POST /api/groups/:id/restaurants` (ajout via lien Maps ou manuel)
- `PATCH /api/restaurants/:id` (changement de statut à tester/déjà fait)
- `POST /api/restaurants/:id/vote`
- `POST /api/groups/:id/draw` (tirage au sort)
- `GET /api/groups/:id/events` (flux SSE)

## 9. Déploiement

- Build front (`vite build`) → dossier `dist/` servi statiquement par Nginx.
- Build back (`mvn package`) → jar exécutable, lancé en local (`127.0.0.1:8080`) via un service systemd (`java -jar app.jar`) qui gère le restart auto et les logs.
- Nginx route `/` vers les fichiers statiques, `/api/*` et `/api/groups/*/events` vers `127.0.0.1:8080`, HTTPS via Certbot/Let's Encrypt.
- PostgreSQL en local sur le même VPS (ou conteneurisé via Docker si isolation souhaitée).

## 10. Thème UI retenu — "Ardoise du bistrot"

Direction choisie parmi trois propositions (chalkboard bistrot / ticket de tombola / reçu de cuisine). Ambiance chaleureuse et conviviale, inspirée du tableau à craie d'un bistrot.

**Palette**
| Rôle | Nom | Hex |
|---|---|---|
| Fond principal | board-green | `#1F2E22` |
| Texte principal | chalk-white | `#F5F1E6` |
| Texte secondaire / muted | muted-chalk | `#93A092` |
| Accent principal | chalk-yellow | `#E8B94A` |
| Accent secondaire | chalk-teal | `#6FA8A0` |
| Lignes / séparateurs | line-green | `#3A4D3B` |

**Typographie**
- Display (titres, sections) : **Caveat** (700), style manuscrit à la craie, utilisé avec retenue sur les titres et labels de statut
- Corps (listes, boutons, texte courant) : **Work Sans** (400/500/600), sans-serif propre et lisible en petite taille

**Principes de layout**
- Cadre en pointillés autour du contenu, évoquant le contour d'un tableau
- Liste de restaurants présentée comme des lignes de menu, avec un "leader" en pointillés entre le nom et le statut (comme un menu imprimé)
- Boutons en style "craie" : fond transparent, bordure en pointillés colorée
- Élément signature : le leader en pointillés menu-style + les statuts en écriture manuscrite (Caveat) contrastant avec le corps en sans-serif

Une maquette HTML de référence (écrans "liste" et "choisir ce soir") a été validée dans la conversation précédente et peut servir de base visuelle directe pour l'implémentation Svelte.
