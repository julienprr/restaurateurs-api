# CLAUDE.md — API des Restaurateurs

Ce fichier guide Claude Code dans ce dépôt.

---

## Comment me parler

- **En français**, systématiquement.
- **Direct et efficace**, pas de phrases d'introduction creuses.
- **Poser des questions** quand le contexte n'est pas clair, plutôt que deviner.
- **Honnête**, même quand ça ne fait pas plaisir. Pas de flagornerie.
- **Pour les décisions importantes**, donner les pour et les contre plutôt que
  de trancher à ma place.
- **Pas de tirets longs** dans les réponses.
- Julien débute en développement assisté : expliquer ce qui a été fait et
  pourquoi, plutôt que détailler l'implémentation.

## Le projet

API REST de l'application « Les Restaurateurs ». Le produit est décrit dans
[PRODUIT.md](PRODUIT.md), le démarrage et la carte du code dans
[README.md](README.md).

Trois dépôts : cette API, `restaurateurs-web` (front Svelte),
`vps-cluster-k3s` (manifests k3s).

## Décisions techniques, à ne pas re-litiger

- **Spring Boot 3.5**, pas la 4.x : ligne la mieux documentée.
- **Schéma géré par Flyway uniquement.** Hibernate est en `ddl-auto: validate`.
  Toute évolution du modèle passe par une migration `V<n>__*.sql`. Ne jamais
  modifier une migration déjà appliquée en production.
- **Les services renvoient des DTO, jamais des entités.** `open-in-view` est
  désactivé : une collection paresseuse doit être lue dans la transaction, donc
  dans le service. Un contrôleur qui sérialise une entité échouera.
- **L'entité du groupe s'appelle `UserGroup`** : `Group` entre en conflit avec
  `GROUP BY` en JPQL.
- **Authentification maison, sans Spring Security** : un filtre résout le
  cookie, l'annotation `@CurrentUser` protège les routes.
- **Les jetons sont stockés hachés** (SHA-256). Une lecture de la base ne
  permet ni de se connecter ni d'usurper une session.
- **Aucune API Google payante** : extraction depuis l'URL Maps, puis Nominatim
  pour le géocodage inverse.
- **Liste blanche de domaines avant toute requête sortante**
  (`GoogleHosts`). Sans elle, coller une adresse interne ferait interroger
  cette adresse par le serveur.
- **Une seule réplique.** Les connexions SSE vivent en mémoire dans
  `GroupEventService`. Augmenter `replicas` casserait le temps réel **sans
  erreur visible**. Il faudrait d'abord un relais partagé.
- **`POST /api/invites/{code}/join`** et non `/api/groups/:id/join` comme le
  prévoyait le brief : un lien d'invitation porte un code, pas un identifiant.

## Pièges éprouvés

- **`APP_BASE_URL`** construit les liens de connexion envoyés par email. Une
  erreur n'a aucun symptôme : l'app tourne, les mails partent, mais les liens
  mènent ailleurs. Arrivé en production le 9 octobre 2026.
- **Ne pas envoyer de `User-Agent` de navigateur** vers `maps.app.goo.gl` :
  Google répond alors une page JavaScript sans redirection HTTP, et
  l'extraction échoue.
- **Un email multipart exige `new MimeMessageHelper(message, true, "UTF-8")`.**
  Sans le `true`, l'envoi échoue, et comme il est asynchrone l'API répond 202
  quand même.

## Méthode de travail

Développement par tranches, chacune se terminant par quelque chose que Julien
peut tester lui-même. Un commit par tranche validée.

**Ne jamais annoncer qu'une chose fonctionne sans l'avoir observée.** Une
réponse HTTP 2xx ne prouve pas qu'un traitement asynchrone a réussi.

**Chaque nouvelle feature arrive avec ses tests.** La couverture actuelle est
faible (extraction d'URL et filtrage de domaines uniquement) : l'authentification,
le cloisonnement des groupes et la logique de vote n'ont aucun filet. Couvrir
l'existant au fur et à mesure qu'on y touche.

## Périmètre

[PRODUIT.md](PRODUIT.md) liste ce qui est volontairement absent. Ne pas
l'implémenter sans demande explicite.
