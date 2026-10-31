# En cours : les sorties, les notes et le classement

Branche `feature/sorties`. **Le projet ne compile pas en l'état**, c'est normal
et documenté plus bas.

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

## Ce qui ne compile pas, et pourquoi

Trois erreurs, toutes dues au passage de « votes du groupe » à « votes de la
sortie » :

| Fichier | Appel devenu caduc | À faire |
|---|---|---|
| `RestaurantService:45` | `countByRestaurantForGroup` | compter sur la sortie en préparation, ou zéro s'il n'y en a pas |
| `DecisionController:48` | `voteService.clearVotes` | supprimer la route : chaque sortie repart de zéro |
| `DecisionController:61` | `voteService.draw` | déléguer à `outingService.decide` |

## Ce qui reste, dans l'ordre

**1. Finir le backend des sorties**
- `OutingController` : prévoir, consulter, reprogrammer, annuler
- `DecisionController` : décider sur la sortie, retirer « effacer les votes »
- `RestaurantService` : votes de la sortie en préparation
- Job quotidien de clôture, à côté de `ExpiredTokenCleanup`
- Une route d'administration pour déclencher la clôture à la main, sinon
  tester demande d'attendre le lendemain
- **Tests** : cycle de vie, cloisonnement des groupes, règles de décision

**2. Le front**
- Écran de vote conditionnel : pas de sortie, sortie sans restaurant, sortie
  décidée
- Sélecteur de date
- `events.js` : nouveaux événements SSE

**3. Notes et commentaires**
- Table `ratings`, note de 1 à 5, commentaire facultatif, modifiable
- Une note par membre et par sortie

**4. Historique et classement**
- Écran « Nos sorties »
- Note d'un restaurant : moyenne de ses sorties
- Note d'un membre : moyenne des restaurants qu'il a proposés (`added_by`)

**5. Les emails**

| Email | Déclencheur | Destinataires |
|---|---|---|
| Allez voter | création d'une sortie | les membres, **sauf le créateur** |
| On va là | décision du restaurant | les membres, **sauf le décideur** |
| Notez la sortie | job de clôture | tous les membres |
| Nouvelle version | lancé à la main, une fois | tous les inscrits |

Règles validées : ne jamais notifier l'auteur de l'action, un seul email par
événement, aucun rappel.

## Pièges à garder en tête

- **`APP_BASE_URL`** construit les liens des emails. Les nouveaux emails
  doivent pointer vers le bon écran, et cette variable a déjà cassé la
  production une fois.
- **Les services renvoient des DTO, jamais des entités** : `open-in-view` est
  désactivé.
- **Une seule réplique de l'API** : les connexions SSE vivent en mémoire.
- **Ne pas modifier `V2__outings.sql` une fois déployée.** Toute correction
  passe par un `V3`.
