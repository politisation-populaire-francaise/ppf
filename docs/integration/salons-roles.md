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

## 4. Hors de cette étape

Salons réservés et chiffrés (MLS), invitations, demandes d'adhésion, suppression d'un salon, reprise d'un salon squatté, rapprochement par le sens, signalements (socle, étape 2).
