# Reprise d'un salon resté sans administrateur (M14, S-146) : conception

Pôle Communauté, brouillon 0.1 du 8 novembre 2026, à caler avec le Directeur technique (back-office M15 et service de signature). Décision du chef d'équipe du 8 novembre 2026, par délégation de la propriétaire (« Pour les decisions a prendre, je suis tes recommandations ») : un salon dont le seul administrateur est déclaré compromis peut être repris par l'équipe, **par une action à deux du back-office**. Complète `salons-roles.md` (D-131, D-132) et `compte-compromis.md` (D-134).

## 1. Le cas

- Quand un compte est déclaré compromis (avis 1777 accepté), le serveur lui retire tous ses rôles de salon (`CompromisedAccounts`, lignes de `group_role` effacées). S'il était le seul administrateur, le salon n'en a plus : personne ne peut nommer, modifier la description ni retirer un rôle ; les modérateurs en place gardent le retrait de messages et l'exclusion.
- L'ancien titulaire ne peut pas reprendre la main en republiant la description : toute publication de la clé est refusée (`403 account_compromised`) avant la règle « le créateur devient le premier administrateur ».
- Le même état peut venir d'ailleurs plus tard (compte effacé, salon abandonné) : la file le montre quelle qu'en soit la cause, mais seule la cause « compte compromis » est couverte par la décision.

## 2. Ce que voit l'équipe : la file `groups.orphans`

« Salons sans administrateur », une file du socle (contrat du back-office, section 7), visible avec `queue.groups.orphans.decide`. Pour chaque salon, seulement ce que la décision demande :
- nom, territoire, date de création ;
- depuis quand il n'a plus d'administrateur et pourquoi (« Le compte administrateur a été déclaré compromis le … », avec l'identifiant de l'avis) ;
- les modérateurs en place (clés pseudonymes, date de leur nomination) ;
- le successeur nommé par l'avis, s'il y en a un (une affirmation de son ancien titulaire, signée par sa clé de secours, jamais une preuve d'identité) ;
- l'activité : nombre de messages des 30 derniers jours (aucune liste de membres : elle n'existe pas, D-028).

## 3. Les actions (toutes à deux, sensibles, motif obligatoire)

| Action | Ce qu'elle fait | Quand la proposer |
|---|---|---|
| `admin_moderator` | nomme administrateur un modérateur en place, choisi dans la liste | en premier : il tient déjà le salon |
| `admin_successor` | nomme administrateur le successeur nommé par l'avis | si l'avis en nomme un et que ce compte n'est pas lui-même compromis |
| `admin_key` | nomme administrateur une clé saisie par l'équipe (par exemple un membre qui l'a demandé par « Mes échanges », M30) | en dernier ; la clé doit avoir écrit dans le salon |
| `leave` | laisse le salon sans administrateur et ferme la demande | salon inactif ou aucun candidat |

- Le socle fait de chacune une demande validée par une deuxième personne qui a la même permission, journalisée (D-123, D-127) ; la personne qui demande ne valide pas.
- Le motif est montré publiquement dans le journal du salon (section 4) : il ne contient aucune donnée personnelle.
- Une nouvelle nomination n'est possible que si le salon n'a toujours aucun administrateur au moment de l'accord (sinon : « Ce salon a de nouveau un administrateur »).

## 4. La trace publique (transparence)

Un salon public est tenu en public : la reprise doit se voir dans son journal comme toute nomination (« Administrateur nommé par l'équipe de PPF le … : motif »). Deux options :

- **A (recommandée)** : un événement 9000 (put-user, rôle `admin`, motif dans le contenu) **signé par une clé de la plateforme**, par un nouvel usage du service de signature `groups.takeover` (kind 9000). Le serveur l'accepte seulement dans un salon sans administrateur (`409 room_has_admin` sinon), de la clé certifiée pour cet usage ; la clé de la plateforme ne reçoit jamais elle-même de rôle. L'application reconnaît la clé comme celle de l'équipe (liste des clés de la plateforme). Avantage : vérifiable par tous, identique aux autres nominations ; inconvénient : un usage de plus dans le service de signature.
- **B (repli)** : sans événement signé : la ligne `group_role` porte `granted_by = "ppf-team"` et `GET /api/v1/groups/{id}/roles` l'affiche ainsi, avec le motif ; le journal d'audit du back-office garde la trace complète. Avantage : rien à ajouter au service de signature ; inconvénient : invisible pour un client Nostr ordinaire, moins vérifiable.

## 5. Dans l'application

- « À propos du salon » : « Ce salon n'a plus d'administrateur depuis le … (compte déclaré compromis) » ; pour un modérateur et un membre qui a écrit dans le salon, « Demander à reprendre ce salon » ouvre « Mes échanges » (M30) avec l'objet prérempli, dès que M30 est dans `main`.
- Journal du salon : la nomination par l'équipe s'affiche avec son motif et la mention « par l'équipe de PPF ».
- Notification « Mon compte » (D-135) : rien de nouveau ; l'alerte de compromission suffit aux membres qui suivent l'ancien administrateur.

## 6. Hors de cette étape

- **Salon squatté** (administrateur actif qui abuse du nom) : même mécanisme, avec en plus le retrait du rôle de l'administrateur en place ; décision distincte, toujours ouverte (question « reprise d'un salon squatté »).
- Suppression d'un salon (9008) : toujours réservée à l'équipe, plus tard.

## 7. Données et menaces

- Aucune donnée nouvelle sur les personnes : la file lit les tables existantes (`group_role`, `compromised_account`, messages du salon) ; seules les décisions s'ajoutent au journal d'audit.
- Menace : une équipe qui s'empare de salons. Parades : seulement des salons sans administrateur, action à deux, motif public, journal d'audit inaltérable (D-124), et trace publique (option A).
- Menace : un attaquant qui détient l'ancienne clé se fait nommer par un tiers. Parade : la clé compromise ne peut rien publier ; `admin_key` exige une clé qui a écrit dans le salon et un motif ; le successeur n'est jamais nommé automatiquement.

## 8. Questions pour le Directeur technique

1. Option A ou B ? Si A : un usage `groups.takeover` (kind 9000) **automatique** pour le service de signature, puisque la décision humaine à deux a déjà eu lieu dans le back-office, ou un usage humain avec son approbation sur la page du service (soit trois personnes en tout) ? Ma recommandation : A avec un usage automatique, réservé au serveur principal, appelé seulement après l'accord à deux.
2. Le choix d'un modérateur ou la saisie d'une clé (`admin_key`) dans l'action : le contrat de file prévoit un motif, pas de paramètre. Faut-il un champ de plus dans `QueueAction` (liste de choix ou texte), ou une action par modérateur (`admin_moderator_<n>`) ?
3. Permission : `queue.groups.orphans.decide` pour le rôle `moderator` du back-office, comme `groups.creations` ?
