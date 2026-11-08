# Salons : rôles et modération par le collectif (M14, S-82)

Pôle Communauté, version 1.0 du 7 novembre 2026 (décision D-131, à consigner). Complète `messagerie-salons.md` (D-028, D-032) et suit NIP-29 (événements 9000 à 9022).

## 1. Ce qui change

Aujourd'hui, un salon public est créé par un compte (description 39000 signée par lui, charte, territoire) ; personne ne peut y modérer quoi que ce soit, et le serveur refuse les événements 9000 à 9022. Cette étape donne au collectif les outils d'un salon ordinaire, **en public et de façon traçable** (principe 11 « La transparence d'abord », à valider) :

- le **créateur** du salon (auteur de la description 39000) est son premier administrateur ;
- un administrateur peut **nommer** un autre administrateur ou un **modérateur**, et retirer ces rôles ;
- un administrateur ou un modérateur peut **retirer un message** du salon et **exclure** un compte du salon (il ne peut plus y écrire), puis le réadmettre ;
- un administrateur peut **modifier la description** (nom, présentation, image plus tard) ; le territoire et la charte ne changent pas (sinon le refus des doublons serait contourné) ;
- chaque action est un événement signé par son auteur, **public**, visible dans le journal du salon (« Retiré par … : motif ») ; le motif est obligatoire pour un retrait et une exclusion.

L'équipe de PPF garde la main au-dessus du collectif : signalement d'un message ou d'un salon (NIP-56, file `reports` du socle, étape 2 du back-office), reprise d'un salon squatté (décision en attente de la propriétaire), masquage d'un contenu illicite. Rien de cela n'est codé dans cette étape.

## 2. Événements acceptés par le serveur (NIP-29)

| Kind | Nom | Étiquettes | Qui peut l'envoyer |
|---|---|---|---|
| 9000 | put-user | `h` = salon, `p` = clé, rôle facultatif (`admin` ou `moderator`) | administrateur |
| 9001 | remove-user | `h`, `p`, contenu = motif (obligatoire) | administrateur ; modérateur pour un simple membre |
| 9002 | edit-metadata | `h`, `name`, `about` ; jamais `ppf-territory` ni `ppf-charter` | administrateur |
| 9005 | delete-event | `h`, `e` = message retiré, contenu = motif (obligatoire) | administrateur, modérateur ; l'auteur pour son propre message |

Refusés comme aujourd'hui : 9007, 9008 (suppression du salon : par l'équipe seulement, plus tard), 9009 (invitations : avec les salons réservés), 9021 et 9022 (adhésion : les salons publics sont ouverts, rejoindre reste une affaire de l'appareil), 39002 (liste des membres, jamais publiée, D-027).

- Le serveur calcule l'état de chaque salon à partir de ces événements, dans l'ordre de réception : rôles en vigueur, comptes exclus, messages retirés. Un événement d'un compte qui n'a pas le rôle requis au moment de sa réception est refusé (`403 not_allowed`).
- Un dernier administrateur ne peut pas se retirer son propre rôle (`409 last_admin`).
- Un compte exclu voit ses messages 9 refusés dans ce salon (`403 banned`) ; ses messages déjà publiés restent, sauf retrait explicite.
- Un message retiré n'est plus servi dans le fil du salon ; l'événement 9005 reste public, avec son motif, sans le texte retiré.
- `GET /api/v1/groups/{id}/roles` : rôles en vigueur (clé, rôle, date, clé de celui qui a nommé) et nombre de comptes exclus ; aucune liste de membres.
- Les événements 9000 à 9005 sont servis par `GET /api/v1/events?tag=h:<salon>&kinds=…`, comme les messages : le journal du salon est lisible par tous.
- Tables Flyway (numéro au moment de l'entrée dans main) : `group_role(group_id, pubkey, role, granted_by, granted_at)`, `group_ban(group_id, pubkey, banned_by, reason, banned_at)`, `group_removed_event(group_id, event_id, removed_by, reason, removed_at)` ; clés publiques seulement, déjà publiques dans les événements signés.

## 3. Dans l'application

- Écran du salon : menu d'un message (« Retirer ce message » pour les administrateurs et modérateurs, et pour son auteur), menu d'un auteur (« Nommer administrateur », « Nommer modérateur », « Exclure du salon » avec motif obligatoire), page « À propos du salon » (description, territoire, charte, rôles, journal de modération).
- Chaque action passe par une confirmation qui rappelle qu'elle est **publique et signée** de son pseudonyme.
- Message retiré : « Message retiré par un modérateur : motif ».
- Accessibilité et adaptation téléphone, tablette, ordinateur, comme le reste de la messagerie.

## 3 bis. Garde-fous à la création (décision de la propriétaire, D-132, question 26)

« L'idéal est que l'algo de la fonctionnalité mette plein de garde fou pour éviter ce genre de situation ainsi que les doublons. » En plus du refus actuel des doublons exacts (même nom normalisé, même territoire) :

- **noms proches** : à la création, le serveur compare le nom normalisé aux salons du même territoire et des territoires parents (distance d'édition faible, mêmes mots dans un autre ordre, ajout d'un mot vide comme « collectif », « de », « du ») ; s'il en trouve, il répond `409 group_similar` avec la liste, et l'application propose de les rejoindre ; créer quand même demande une confirmation explicite et un champ « En quoi votre salon est différent » (envoyé avec la description) ;
- **organisations connues** : un nom proche de celui d'une organisation vérifiée (fiches d'organisations d'Agir) ou d'un salon déjà existant ailleurs avec beaucoup d'activité est **mis en attente** (`202 held`) pour une relecture de l'équipe, dans une file du back-office `groups.creations` (approuver, refuser avec motif) ; le créateur voit « En attente de vérification » ;
- **rythme** : au plus 3 salons créés par compte et par jour, et un compte de moins de 7 jours ne crée pas de salon dont le nom ressemble à une organisation connue ;
- le **signalement** d'un salon reste possible (file `reports` du socle, étape 2).

## 4. Hors de cette étape

Salons réservés et chiffrés (MLS) et bascule transparente d'une conversation de groupe vers un salon chiffré au-delà d'un seuil (décision 26 : aucune limite de taille visible), invitations, demandes d'adhésion, suppression d'un salon, reprise d'un salon squatté, rapprochement par le sens, signalements (socle, étape 2).

## 5. État (7 novembre 2026, branche `communaute-salons-roles`)

Fait (tests de l'étape 2, du code commun et de l'application encore à passer, 7 novembre, 23 h) :

- **Serveur, rôles et modération** (section 2) : 9000, 9001, 9002 et 9005 acceptés, appliqués un par un et par salon dans l'ordre de réception (verrou par salon, même transaction que l'enregistrement) ; le créateur est le premier administrateur (y compris pour les salons créés avant, par la migration) ; `403 not_allowed`, `409 last_admin`, `403 banned` ; messages retirés plus servis (ni dans le fil, ni par leur identifiant), le 9005 reste public avec son motif ; `GET /api/v1/groups/{id}/roles` ; nom et présentation modifiés servis par `GET /api/v1/groups` (champ `edit` en plus) ; 9007, 9008, 9009, 9021, 9022 et 39002 toujours refusés. Tables de `V12__group_roles.sql` (numéro provisoire, à reprendre à l'entrée dans `main`) : `group_role`, `group_ban`, `group_removed_event`, plus `group_edit` (nom et présentation en vigueur) et `group_held_creation` (créations en attente). Aucune instruction de structure au fonctionnement : le rôle restreint de production suffit.
- **Serveur, garde-fous** (section 3 bis) : `409 group_similar` avec la liste (même territoire et « France ») ; création confirmée par `["ppf-similar","confirmed"]` et `["ppf-difference","…"]` ; `202 held` et file `groups.creations` du back-office (approuver, refuser avec motif ; permission donnée au rôle modérateur) pour un nom proche d'une organisation vérifiée ou d'un salon de plus de 200 messages ailleurs ; `GET /api/v1/group-creations?id=…` pour le créateur ; `429 too_many_groups` au-delà de 3 créations en 24 heures ; `403 account_too_recent` (clé connue depuis moins de 7 jours, réglable par `PPF_GROUP_YOUNG_ACCOUNT_DAYS`) ; renommage vers le nom d'un autre salon ou d'une organisation refusé.
- **Code commun** : `RoomModeration` (rôles, exclusions, retraits, modifications et journal calculés sur l'appareil, mêmes règles que le serveur, constructeurs des quatre événements) ; `Group.build(..., difference)` ; nouveaux motifs de refus lus des réponses du serveur.
- **Application** : menu « … » d'un message (« Retirer ce message » pour les administrateurs, les modérateurs et l'auteur ; « Nommer administrateur », « Nommer modérateur », « Retirer son rôle », « Exclure du salon », « Réadmettre dans le salon ») ; confirmation qui rappelle que l'action est publique et signée du pseudonyme, « Annuler » d'abord, motif obligatoire pour un retrait et une exclusion ; « Message retiré par un modérateur : motif » ; page « À propos du salon » (présentation, territoire, charte, rôles, nombre d'exclus, journal de modération) ; message « Vous avez été exclu de ce salon » à la place du champ d'écriture ; formulaire de création : salons proches à rejoindre ou « Créer quand même » avec « En quoi votre salon est différent », « En attente de vérification » pour une création retenue.

Écarts et choix d'interprétation :

- Un **modérateur peut réadmettre** (le tableau de la section 2 réserve 9000 aux administrateurs, la section 1 dit que les modérateurs réadmettent) : il envoie un 9000 sans rôle, accepté seulement pour une clé sans rôle.
- Retirer un rôle, c'est un 9000 **sans rôle** (NIP-29) ; un 9001 sur un administrateur par un autre administrateur est accepté (sauf le dernier).
- Le **créateur** peut toujours republier sa description tant qu'il est administrateur, jamais avec un autre territoire (`400 territory_fixed`) ; la nouvelle version remplace les modifications 9002.
- **Territoires parents** : les salons nomment leur territoire en texte libre ; le seul parent reconnu est « France », en attendant le code du référentiel (D-090).
- « Salon très actif » : au moins 200 messages, seuil choisi par le pôle (réglable dans le code).

Comment le tester sur l'ordinateur :

1. Ouvrez une fenêtre PowerShell dans `C:\Users\clair\Documents\PPF` et tapez `$env:PPF_GROUP_YOUNG_ACCOUNT_DAYS = "0"` puis Entrée (sur le serveur de développement seulement : un compte neuf peut alors voir une création mise en attente), puis `.\gradlew.bat :server:runDev` et Entrée. Attendez la ligne qui se termine par `http://localhost:8080` et laissez la fenêtre ouverte.
2. Lancez deux comptes d'essai comme dans le guide de la propriétaire (« Échanger un message entre deux comptes sur l'ordinateur », étapes 3 à 6, avec `PPF_SERVER_URL=http://localhost:8080`).
3. Compte 1 : Messages, onglet « Collectifs », « Créer un salon ». Nom « [EXEMPLE] Atelier logements », territoire « Lyon », cochez la charte, « Créer le salon ». Le serveur répond qu'un salon proche existe (« Atelier logement ») : vous pouvez le rejoindre, ou écrire en quoi le vôtre est différent puis « Créer quand même ». Le salon s'ouvre : vous en êtes l'administrateur.
4. Compte 2 : ouvrez ce salon, « Rejoindre pour écrire », écrivez un message commençant par [EXEMPLE].
5. Compte 1 : actualisez (flèche en cercle), cliquez sur « … » à côté du message du compte 2. Essayez « Nommer modérateur » : la fenêtre dit que l'action est publique et signée de votre pseudonyme ; « Annuler » est en premier. Puis « Exclure du salon » : sans motif, rien ne part ; avec un motif, le compte 2 est exclu.
6. Compte 2 : actualisez ; à la place du champ d'écriture, « Vous avez été exclu de ce salon : » suivi du motif.
7. Compte 1 : « … », « Réadmettre dans le salon », puis « … », « Retirer ce message » avec un motif : le message est remplacé par « Message retiré par un modérateur : » et le motif.
8. Dans le salon, le bouton « i » (« À propos du salon ») montre la présentation, le territoire, la charte, les administrateurs et modérateurs, le nombre d'exclus et le journal de toutes ces actions.
9. Création retenue : créez un salon nommé « [EXEMPLE] Association fictive Les Ateliers de la Lanterne », territoire « Lille » : le formulaire affiche « En attente de vérification ». La décision se prend dans l'espace de l'équipe (`PPF_BACKOFFICE_ORIGIN=http://localhost:8081`), file « Salons en attente de vérification ».

Reste : signalements d'un salon ou d'un message (socle, étape 2), reprise d'un salon squatté (décision en attente), affichage pour le créateur de la décision sur une création retenue (l'API existe, l'application ne l'interroge pas encore), modification du nom et de la présentation depuis l'application (le serveur et le code commun sont prêts).
