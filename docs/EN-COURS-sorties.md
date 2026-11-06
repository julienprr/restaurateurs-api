# En cours : les sorties, les notes et le classement

Branche `feature/sorties`, dans l'API et dans le front. Étapes 1 à 5
faites (sorties, notes, classement, emails) ; reste le déploiement.

À lire en entier avant de reprendre.

## L'objectif

Ajouter la notion de **sortie** : un soir où le groupe va manger quelque part.
Tout le reste en découle, notes, commentaires, historique et classement.

Aujourd'hui, voter et trancher ne laissent aucune trace : le résultat d'une
décision est calculé, diffusé par SSE, puis oublié.

## Le processus métier, validé avec Julien

```
  AU REPOS
  on ajoute des restaurants, rien n'est prévu
      │
      │  « Prévoir une sortie » + une date   (un seul geste, le vote s'ouvre)
      ▼
  PLANIFIÉE
  les membres votent
  on tranche → le restaurant est fixé
  on peut re-tirer → il est remplacé, ce n'est pas une nouvelle sortie
      │
      │  la date passe   (job quotidien)
      ▼
  PASSÉE
  le restaurant devient « déjà fait »
  chacun note et commente
```

Décisions actées :

- **Une seule sortie planifiée par groupe**, garanti par un index partiel en
  base, pas par le code.
- **La note porte sur la sortie**, pas sur le restaurant. On peut donc
  retourner au même endroit et noter différemment.
- **Modifier la date ou annuler** une sortie planifiée est possible.
- **Sans restaurant choisi à la clôture**, la sortie devient `ANNULEE` et n'est
  pas notable.
- **Le classement est assumé comme un jeu.** Julien a écarté mes réserves sur
  la pression sociale et la fragilité statistique : ne pas y revenir.
- **Tout le monde peut noter**, sans gérer qui était présent.
- **« Choisir ce soir » reste**, ajouté le 31 octobre 2026 : un clic crée la
  sortie du jour et tire le restaurant au sort, sans vote. Si une sortie est
  déjà prévue ce soir, on tranche dessus. Si elle est prévue un autre jour,
  l'API répond 409 et **le front propose de l'avancer à ce soir**
  (`PATCH /api/outings/{id}`), puis rappelle la route. Autoriser deux sorties
  planifiées a été écarté.

## Ce qui est fait

| Fichier | État |
|---|---|
| `db/migration/V2__outings.sql` | **écrit et testé sur une copie des données de production** |
| `domain/Outing.java` | écrit |
| `domain/OutingStatus.java`, `DecisionMode.java` | écrits |
| `domain/Vote.java` | rattaché à une sortie |
| `repo/OutingRepository.java` | écrit |
| `repo/VoteRepository.java` | requêtes passées de « par groupe » à « par sortie » |
| `service/OutingService.java` | cycle de vie complet, décision comprise |
| `service/VoteService.java` | vote rattaché à la sortie en préparation |
| `web/dto/OutingResponse.java`, `PlanOutingRequest.java` | écrits |

### La migration, vérifiée

Rejouée sur une copie exacte de la production du 31 octobre 2026 :

- les 2 restaurants `DEJA_FAIT` sont devenus des sorties passées du 9 octobre
- les 2 votes de « Rivières d'himalaya » y sont rattachés
- les 2 restaurants `A_TESTER` n'ont pas bougé

Et les quatre garde-fous ont été testés directement en base : deux sorties
planifiées dans un même groupe sont refusées, une par groupe reste possible,
revoter lors d'une autre sortie est accepté, voter deux fois dans la même
sortie est refusé.

> **Julien a pris un dump de la production avant toute chose.** La migration
> est irréversible : elle supprime une contrainte d'unicité, rend `outing_id`
> obligatoire et efface les votes orphelins.

## Étape 1, le backend : faite

- Routes : voir le tableau du README. Décider se fait désormais sur une
  sortie (`POST /api/outings/{id}/decide`), la route « effacer les votes » a
  disparu : chaque sortie repart de zéro.
- `RestaurantService` affiche les voix de la sortie en préparation, zéro sans
  sortie.
- `OutingClosure` clôt chaque jour à 9 h (heure de Paris) les sorties dont la
  date est passée. Le matin plutôt que la nuit, pour l'email « Notez la
  sortie » de l'étape 5.
- `POST /api/admin/outings/close?today=AAAA-MM-JJ`, en-tête `X-Admin-Token`,
  déclenche la clôture à la main. `today` simule une autre date. Sans
  `ADMIN_TOKEN`, la route répond 404.
- `POST /api/groups/{id}/outings/tonight` : « choisir ce soir », création et
  tirage dans une seule transaction. Un tirage impossible ne laisse donc pas
  de sortie vide bloquer le groupe.
- Toutes les dates sont jugées dans le fuseau `Europe/Paris`
  (`OutingService.ZONE`), pas celui du serveur.
- Tests : `OutingServiceTest` (cycle de vie, décision, cloisonnement, clics
  simultanés) et `OutingApiTest`, contre un vrai PostgreSQL via Testcontainers.

Corrigé au passage dans le code de départ : `OutingService` renvoyait des
entités (contraire à la règle des DTO), et `plan()` attrapait la violation
d'index autour d'un `save` sans flush, ce qui laissait l'erreur sortir en 500
au commit. Un test le vérifie.

Événements SSE à câbler dans le front : `outing_changed` (`outingId`) à
chaque création, changement de date, annulation ou clôture ; `draw_result`
garde son nom et gagne un champ `outingId`.

## Étape 2, le front : faite

Dépôt `restaurateurs-web`, branche `feature/sorties`, commit `44f0d8c`.

- L'onglet « La sortie » (anciennement « Choisir ce soir ») affiche l'état :
  pas de sortie, sortie sans restaurant, sortie décidée.
- Sélecteur de date natif (`OutingDateSheet`), borné à aujourd'hui, heure de
  Paris (`src/lib/outings.js`).
- « Choisir ce soir » : sur 409, un panneau propose d'avancer la sortie
  prévue à ce soir (PATCH de la date, puis tirage).
- Les boutons de décision appellent `/outings/{id}/decide`. « Effacer tous
  les votes » a disparu.
- SSE : `outing_changed` et `draw_result` rafraîchissent la sortie.
- Vérifié à la main dans le navigateur, par Claude puis par Julien. Le front
  n'a pas de tests automatisés : pas de framework de test configuré.

L'API et le front doivent partir ensemble : l'ancien front appelle
`/groups/{id}/draw`, supprimée. Procédure complète en fin de document.

## Étape 3, les notes : faite

Décisions prises avec Julien le 31 octobre 2026 :

- on note depuis un onglet **« Nos sorties »**, qui accueillera le
  classement à l'étape 4 ;
- les notes des autres sont **visibles avant d'avoir noté** ;
- on peut noter ou modifier sa note **sans limite de temps**.

Côté API :

- migration `V3__ratings.sql`, séparée de V2 : V2 a été vérifiée sur une
  copie de la production, la retoucher annulerait cette vérification ;
- `PUT /api/outings/{id}/rating`, réservé aux sorties `PASSEE` ;
- l'écriture est un `insert ... on conflict do update` : deux envois
  simultanés ne peuvent pas heurter la contrainte d'unicité (testé) ;
- `GET /api/groups/{id}/outings` renvoie désormais
  `[{outing, average, ratings}]` ; `average` est nul sans note ;
- événement SSE `ratings_changed` (`outingId`).

Côté front : `PastOutings.svelte` et `RatingSheet.svelte`.

## Étape 4, le classement : faite

Règles validées avec Julien le 31 octobre 2026 :

- **un restaurant** : la moyenne de ses sorties notées, chaque sortie pesant
  autant quel que soit son nombre de notes (et non la moyenne de toutes les
  notes) ;
- **un membre** : la moyenne des restaurants qu'il a proposés (`added_by`) ;
  un restaurant jamais noté est ignoré, et un membre sans restaurant noté
  apparaît « pas encore classé » ;
- les ex aequo partagent leur rang : 1, 1, 3 ;
- affiché en haut de l'onglet « Nos sorties ».

`GET /api/groups/{id}/ranking`, calculé en mémoire par `RankingService` à
chaque appel : rien n'est stocké, donc rien ne peut se désynchroniser. Le
front recharge le classement sur `ratings_changed` et `outing_changed`.

`PRODUIT.md` a été mis à jour : notes et historique ne sont plus dans la liste
de ce qui est volontairement absent.

## Étape 5, les emails : faite

| Email | Déclencheur | Destinataires |
|---|---|---|
| Allez voter | création d'une sortie, **sauf par « choisir ce soir »** | les membres, **sauf le créateur** |
| On va là | chaque décision, y compris quand on retranche (« changement ») | les membres, **sauf le décideur** |
| Notez la sortie | job de clôture, sorties `PASSEE` seulement | tous les membres |
| Nouvelle version | lancé à la main, une fois | tous les inscrits |

Décisions prises avec Julien le 1er novembre 2026 : « On va là » repart à
chaque décision ; changer la date ou annuler n'envoie rien ; un lien de
désinscription en bas de chaque email ; le texte de l'annonce est proposé par
Claude et relu par Julien.

Fonctionnement :

- `OutingService` publie un `OutingEvent` ; `OutingNotifier` l'écoute
  **après validation** de la transaction. Une sortie dont l'enregistrement
  échoue n'envoie rien, et l'écouteur ne verrait de toute façon pas une sortie
  non validée (vérifié : avec un écouteur immédiat, 9 tests sur 11 échouent).
- Un email par destinataire : personne ne voit les adresses des autres.
- Désinscription : `users.notifications_enabled` et `users.unsubscribe_token`
  (V4). Le jeton est **en clair**, il ne permet que de couper les emails. Le
  lien du corps mène à une page avec un bouton (les antivirus ouvrent les
  liens) ; l'en-tête `List-Unsubscribe` pointe sur la route POST en un clic.
- Annonce : la table `announcements` empêche un second envoi (409).
- Tests : `NotificationTest`, avec `support/Mailbox` qui remplace l'envoi SMTP.

## Ce qui reste : le déploiement

L'API et le front doivent partir ensemble. Dans l'ordre :

1. **Dump de la production**, juste avant : V2, V3 et V4 s'appliquent au
   démarrage de l'API, et V2 est irréversible.
2. Vérifier en production `APP_BASE_URL` (tous les liens des emails en
   dépendent), `MAIL_FROM`, et définir `ADMIN_TOKEN` (clôture à la main,
   annonce). Un ConfigMap modifié n'atteint le pod qu'après redémarrage.
3. Fusionner `feature/sorties` dans `main` sur les deux dépôts, puis
   déployer.
4. Vérifier : migrations appliquées, une sortie prévue, un email reçu dans
   une vraie boîte avec un lien qui mène au bon écran.
5. Annonce : d'abord `?preview=` vers la boîte de Julien, relecture, puis
   l'envoi général.

## Pièges à garder en tête

- **`APP_BASE_URL`** construit les liens des emails. Les nouveaux emails
  doivent pointer vers le bon écran, et cette variable a déjà cassé la
  production une fois.
- **Les services renvoient des DTO, jamais des entités** : `open-in-view` est
  désactivé.
- **Une seule réplique de l'API** : les connexions SSE vivent en mémoire.
- **Ne pas modifier `V2__outings.sql` une fois déployée.** Toute correction
  passe par un `V3`.
