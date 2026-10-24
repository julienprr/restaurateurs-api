# Les Restaurateurs — le produit

## Pourquoi cette application existe

Un groupe WhatsApp servait à lister des idées de restaurants avant une sortie.
Il est tombé en désuétude : une conversation n'est pas une liste, les adresses
se perdent dans le fil, et le jour venu personne ne tranche.

L'application remplace ce groupe sur deux usages, et deux seulement :

1. **Alimenter** une liste commune d'adresses à tester.
2. **Décider** le jour J, par vote ou par tirage au sort.

Priorité absolue : **la simplicité**. Chaque écran ajouté éloigne du but.

Le brief d'origine est conservé dans [docs/brief.md](docs/brief.md).

## Ce que l'application fait aujourd'hui

**Se connecter**
- Par lien envoyé par email, sans mot de passe ni inscription
- Le lien vaut 15 minutes et ne fonctionne qu'une fois
- La session dure 30 jours
- Maximum 5 demandes par adresse et par heure

**Les groupes**
- Créer un groupe
- Partager un lien d'invitation, ou dicter un code de 8 caractères
- Rejoindre depuis le lien, même sans compte : la connexion se fait d'abord,
  l'adhésion suit automatiquement
- Un même utilisateur peut appartenir à plusieurs groupes

**Les restaurants**
- Ajouter en collant un lien Google Maps : le nom, la ville et l'adresse se
  remplissent seuls, sans aucune API payante
- Repli manuel si l'extraction échoue, le lien étant conservé
- Type de cuisine et budget en choix rapides, jamais en saisie libre
- Marquer « déjà fait », remettre « à tester », retirer

**Décider ce soir**
- Voter pour autant d'adresses qu'on veut, le vote est une bascule
- Les votes des autres apparaissent **en direct**, sans rafraîchir
- Trancher selon les votes : le plus de voix gagne, le sort départage les ex æquo
- Ou tirer au sort parmi toutes les adresses à tester
- Le verdict s'affiche chez tous les membres connectés en même temps
- Remettre les votes à zéro pour la prochaine fois

## Ce qui est volontairement absent

Ces choix sont délibérés, les réintroduire doit être une décision, pas un
réflexe.

| Absent | Pourquoi |
|---|---|
| Mot de passe, inscription | la friction tuerait l'usage avant qu'il ne démarre |
| Filtres, recherche | avec vingt adresses, faire défiler suffit |
| Commentaires, notes | le groupe WhatsApp existe toujours pour ça |
| Historique des sorties | personne n'a demandé à relire le passé |
| Notifications | une application entre amis n'a pas à réclamer l'attention |
| Recherche de disponibilité | un sondage WhatsApp fait le travail |
| Rôles, administration | un groupe d'amis n'est pas une hiérarchie : tout
  membre peut tout modifier |

## Limites connues

- **Le résultat d'un tirage n'est pas enregistré.** Qui recharge la page le
  perd. C'est cohérent avec l'absence d'historique, mais surprend parfois.
- **Une seule réplique de l'API**, car les connexions temps réel vivent en
  mémoire. Suffisant pour un groupe d'amis, bloquant pour passer à l'échelle.
- **Pas de suppression de compte ni de groupe.** Personne n'en a eu besoin.
- **L'extraction Google Maps dépend d'un format non documenté.** Elle peut
  cesser de fonctionner du jour au lendemain ; des tests servent de témoin.

## Idées pour la suite

Par ordre de valeur perçue, à discuter avant d'implémenter.

**Probablement utiles**
- Enregistrer les sorties passées : qui a proposé, où on est allés, quand
- Une note après la sortie, pour alimenter les décisions suivantes
- Un filtre par ville, le jour où la liste dépassera trente adresses

**À voir à l'usage**
- Animation de roulette pour le tirage au sort
- Carte des adresses du groupe
- Lien direct vers la réservation

**Sceptique**
- Notifications push : risque d'être désinstallée pour ça
- Gestion fine de plusieurs groupes : un seul suffit à presque tout le monde
