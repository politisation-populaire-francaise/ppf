# Note d'intégration : droit de réponse et « Signaler une erreur » dans « Voir »

Rédigée le 7 novembre 2026 par l'agent chargé des demandes de « Voir » (worktree `voir-reponse`, branche `feature/voir-reponse`, partie de `d99c093`). Elle s'adresse à la session « Voir », qui fusionne et met à jour `CHANGELOG.md`, `ROADMAP.md`, `REGISTRE_DONNEES.md`, `FORMATS.md`, `THREAT_MODEL.md`, `GUIDE_PROPRIETAIRE.md` et, si besoin, `DECISIONS.md`. Aucun de ces documents n'a été modifié ici.

Mise en œuvre du module M17 du cahier fonctionnel (droit de réponse, journal des corrections) et des entrées VO-21 et VO-22 de l'inventaire 2.

## 1. Ce qui est fait

### Ce que voit l'utilisateur

- **Droit de réponse** : le bouton « Droit de réponse » (fait qui concerne une personne, fiche d'une personne) ouvre maintenant un **formulaire** en pleine page de « Voir » (le bouton retour le ferme, la mise en page suit celle des autres pages : une colonne bornée à 760 dp, défilante, sur téléphone, tablette et ordinateur). En haut : le contenu concerné et l'explication de la loi (LCEN, article 1-1, III ; vérification par le directeur de la publication, publication sous le contenu dans les trois jours, mention « Droit de réponse de [nom] »). Puis : pourquoi un compte est nécessaire ; le nom à faire figurer dans la mention (prérempli avec le nom de la fiche, ou de la seule personne que le fait concerne) ; la qualité (personne nommée ou désignée, ou représentant légal) ; la réponse, avec la longueur maximale calculée sur le contenu (max(mots du contenu, 1 000)) et un **compteur en direct** (« 5 mots sur 1000 au maximum », « 1001 mots : 1 de trop (maximum 1000) », annoncé aux lecteurs d'écran) ; la déclaration sur l'honneur à cocher ; le rappel que l'équipe demandera ensuite, dans les messages, de quoi vérifier l'identité ; le rappel que la demande est chiffrée. Les problèmes sont dits en clair au moment d'envoyer (nom manquant, réponse vide ou trop longue, déclaration non cochée, message trop gros).
- **Sans compte** : un encadré explique qu'il se crée en une minute dans « Mon espace », sans e-mail ni téléphone, avec un bouton « Créer un compte » ; le bouton d'envoi est désactivé ; **le brouillon est gardé** pendant la visite à « Mon espace ».
- **Signaler une erreur** : un lien discret « Signaler une erreur » termine **chaque page de contenu** de « Voir » (indicateur, fait, personne, texte de loi, mouvement, dossier). Il ouvre un formulaire : ce qui est inexact (obligatoire), la correction proposée et l'adresse web d'une source (facultatives ; une adresse doit commencer par `https://` ou `http://`). **Envoi anonyme par défaut** (protection par défaut) : une clé créée pour ce seul signalement, l'équipe ne saura pas qui l'a envoyé mais ne pourra pas répondre. Avec un compte, l'utilisateur peut choisir « Avec mon compte » : une mise en garde s'affiche d'abord (« Envoyer avec votre compte ? », boutons « Envoyer avec mon compte » et « Annuler »), et le choix reste réversible avant l'envoi (principe 10). Sans compte, seul l'envoi anonyme est proposé, avec la phrase qui dit que l'équipe ne pourra pas répondre.
- **Confirmation claire** après l'envoi : « Demande envoyée à l'équipe de rédaction », « Signalement envoyé », ou « En attente d'envoi » (sans serveur, ou serveur injoignable) avec ce qu'il advient de la demande ; boutons « Ouvrir mes messages » (envoi avec le compte : ouvre la conversation avec l'équipe) et « Revenir au contenu ».
- **Dans « Messages »** : la conversation avec l'équipe montre la demande **en clair** (« Demande de droit de réponse / Contenu : … / Nom : … (qualité) / texte »), pas son JSON, dans la bulle comme dans l'aperçu de la liste. L'horloge « En attente d'envoi » s'y affiche comme pour tout message. La réponse de l'équipe arrivera dans cette même conversation.

### Fichiers

| Fichier | Rôle |
|---|---|
| `shared/core/.../core/voir/VoirRequests.kt` (nouveau) | format des demandes (`VoirRequest`, `ReplyRequest`, `ErrorReport`, `ReplyCapacity`), règles (`VoirRequests.maxReplyWords`, `countWords`, `contentWords`, `isWebAddress`), problèmes (`RequestProblem`), clé de l'équipe (`VoirRequests.TEAM_INBOX`), envoi anonyme (`VoirRequestOutbox`) |
| `shared/core/src/jvmTest/.../core/voir/VoirRequestsTest.kt` (nouveau) | 8 tests du cœur |
| `shared/ui/.../ui/voir/VoirRequestSession.kt` (nouveau) | session des demandes pour toute la durée de l'application (brouillons, envoi par le compte ou anonyme, nouvel essai des signalements anonymes en attente), affichage en clair d'une demande dans les messages (`voirRequestText`) |
| `shared/ui/.../ui/voir/ReplyForm.kt` (nouveau) | formulaire du droit de réponse, étiquettes de test `VoirRequestTags`, petits composants partagés par les deux formulaires (case à cocher, encadrés de problèmes et de confirmation) |
| `shared/ui/.../ui/voir/ErrorReportForm.kt` (nouveau) | lien « Signaler une erreur » et formulaire du signalement, mise en garde avant l'envoi avec le compte |
| `shared/ui/src/commonMain/composeResources/values/strings_voir_demandes.xml` (nouveau) | tous les textes (préfixe `voir_req_`) |
| `shared/ui/src/jvmTest/.../ui/voir/VoirRequestsScreenTest.kt` (nouveau) | 6 tests d'interface |
| `VoirState.kt` | deux destinations : `VoirDestination.Reply(address)` et `VoirDestination.Report(address)` |
| `VoirScreen.kt` | paramètre `requests`, branchement des deux formulaires, lien « Signaler une erreur » sous chaque contenu, `ReplyDialog` remplacé par le formulaire |
| `VoirPages.kt` | `VoirActions.reply` prend l'adresse du contenu |
| `VoirComponents.kt`, `strings_voir.xml` | suppression de `ReplyDialog`, de son étiquette `REPLY_DIALOG` et de ses trois textes (dont un qui annonçait « le formulaire arrivera » et citait l'ancien article 6, IV) |
| `AdaptiveShell.kt` | `rememberVoirRequestSession(dependencies, messaging)` à côté de `voirState`, passé à `VoirScreen` |
| `messages/MessagingSession.kt` | nouvelle méthode `sendTo(recipientHex, text, subject)` : envoie un message privé sans ouvrir la conversation (stocké, livraisons en attente comme tout message) |
| `messages/ConversationScreen.kt`, `messages/PrivatePage.kt` | une ligne chacun : le texte affiché passe par `voirRequestText` |
| `VoirScreenTest.kt` | le test du droit de réponse ouvre maintenant le formulaire |

Aucune dépendance nouvelle, aucune migration de base de données, aucune modification du serveur.

## 2. Format des demandes (texte prêt pour `FORMATS.md`)

### Demandes à l'équipe de rédaction depuis « Voir » (`ppf-voir-request`, version 1)

Une demande est un objet JSON, texte d'un **message privé NIP-17** (rumeur de genre 14, scellée puis enveloppée NIP-59, chiffrement NIP-44) adressé à la **clé de la boîte de l'équipe de rédaction**. Le serveur ne voit qu'une enveloppe (genre 1059) : un auteur à usage unique, l'étiquette `p` de l'équipe, une date aléatoire, un contenu opaque. Le sujet de la rumeur (`subject`) vaut « Droit de réponse : [titre] » ou « Signalement d'erreur : [titre] ».

Champs communs :

| Champ | Type | Sens |
|---|---|---|
| `type` | texte | `right-of-reply` ou `error-report` |
| `format` | texte | toujours `ppf-voir-request` |
| `version` | entier | `1` ; un lecteur refuse une version qu'il ne connaît pas |
| `content` | texte | adresse stable du contenu, `genre:clé publique:identifiant` (par exemple `30023:2348…012f:affaire-dominique-marche-public`) |
| `content_title` | texte, facultatif | titre du contenu au moment de la demande |

`right-of-reply` (toujours envoyé depuis un compte : l'auteur de la rumeur est la clé du compte) :

| Champ | Type | Sens |
|---|---|---|
| `respondent` | texte, 200 caractères au plus | nom à faire figurer dans la mention « Droit de réponse de [nom] » |
| `capacity` | texte | `person-named` (personne nommée ou désignée) ou `legal-representative` (représentant légal) |
| `reply` | texte | la réponse, texte brut |
| `content_words` | entier | mots du contenu (titre, résumé, corps) comptés sur l'appareil |
| `max_words` | entier | longueur maximale : max(`content_words`, 1000) |
| `reply_words` | entier | mots de la réponse comptés sur l'appareil |
| `statement_of_truth` | booléen | déclaration sur l'honneur (qualité et exactitude) ; doit être vrai |

`error-report` :

| Champ | Type | Sens |
|---|---|---|
| `wrong` | texte, 4 000 caractères au plus | ce qui est inexact |
| `correction` | texte, facultatif, 4 000 caractères au plus | la correction proposée |
| `source` | texte, facultatif | adresse `https://` ou `http://` d'une source |
| `anonymous` | booléen | vrai quand la demande est signée par une clé à usage unique : personne ne peut y répondre |

Un mot est une suite de caractères entre deux espaces qui contient au moins une lettre ou un chiffre. Un champ facultatif absent est omis (jamais `null`). La demande entière tient dans un message privé (16 Kio de texte, `ChatMessage.MAX_TEXT_BYTES`), soit environ 2 000 mots de réponse en français. Exemple :

```json
{"type":"right-of-reply","content":"30023:23484985405d16929a93f9aea29135678ad0ced909bf5a264f7b923d619b012f:dominique-fictif","content_title":"[EXEMPLE] Dominique Fictif","respondent":"Dominique Fictif","capacity":"person-named","reply":"[EXEMPLE] Je conteste ces faits.","content_words":412,"max_words":1000,"reply_words":5,"statement_of_truth":true,"format":"ppf-voir-request","version":1}
```

**Envoi avec le compte** : message privé ordinaire (une enveloppe pour l'équipe, une pour l'utilisateur lui-même), stocké sur l'appareil ; la réponse de l'équipe arrive dans la même conversation. **Envoi anonyme** (signalement seulement) : la rumeur et le sceau sont signés par une clé générée pour cette seule demande, une seule enveloppe, pour l'équipe ; aucune copie pour l'expéditeur, rien n'est gardé sur l'appareil ; la clé est détruite dès que le serveur a accepté ou refusé l'enveloppe.

## 3. Notes pour `REGISTRE_DONNEES.md`

- **Demande de droit de réponse** (nom, qualité, texte, contenu visé) : donnée personnelle, souvent liée à une opinion ou à une accusation. Finalité : obligation légale (LCEN, article 1-1, III ; décret 2007-1527). Où : sur l'appareil de l'utilisateur (comme ses messages privés) et chez l'équipe de rédaction ; le serveur ne garde qu'une enveloppe chiffrée illisible, pendant la durée de conservation des messages (trois mois au minimum, D-028). Durée côté équipe : à fixer (voir questions).
- **Signalement d'erreur avec le compte** : la clé publique du compte et le texte, chez l'équipe et sur l'appareil (messages privés).
- **Signalement d'erreur anonyme** : aucune donnée sur l'appareil après l'envoi ; en attente d'un serveur, le texte et la clé à usage unique restent **en mémoire seulement**, effacés à la fermeture de l'application. Le serveur voit une enveloppe chiffrée d'un auteur aléatoire.
- **Brouillons** : en mémoire seulement, pour la durée de l'application ; jamais écrits sur le disque.
- Aucune adresse IP, aucun e-mail, aucun numéro de téléphone.

## 4. Choix techniques à consigner

1. **Transport** : messages privés NIP-17 vers une clé de l'équipe, par le relais existant, plutôt qu'une route serveur dédiée : chiffrement de bout en bout (contenus sensibles en attente chiffrés pour l'équipe, comme le veut `CLAUDE.md`), aucun changement du serveur, et tout l'échange (vérification de l'identité, réponse, suivi) a lieu dans l'application.
2. **Clé de l'équipe** : `VoirRequests.TEAM_INBOX`, pour l'instant la clé de l'équipe éditoriale d'exemple (`EditorialTrust.EXAMPLE_EDITOR`), isolée derrière cette constante (et le paramètre `teamInbox` de `VoirRequestOutbox` et `VoirRequestSession`). **En production, une clé dédiée à la boîte de l'équipe**, distincte de la clé qui signe les contenus, à remplacer avant le lancement public.
3. **Formulaires en pages de « Voir »** (destinations `Reply` et `Report` dans la pile), plutôt qu'en boîtes de dialogue : longs textes, défilement, retour système, mise en page adaptative déjà en place.
4. **Signalement anonyme par défaut**, envoi avec le compte après une mise en garde (principe 10). Le droit de réponse exige un compte : l'équipe doit répondre et vérifier l'identité, la personne doit pouvoir suivre.
5. **Signalements anonymes en attente en mémoire** : sans table nouvelle (pas de migration), ils sont perdus si l'application est fermée avant le retour du serveur ; l'écran de confirmation le dit. Une table chiffrée pourrait les garder plus tard.
6. **Affichage en clair** dans « Messages » d'un message qui contient une demande (`voirRequestText`) : l'équipe garde un JSON lisible par ses outils, l'utilisateur voit une phrase.

## 5. Questions pour la propriétaire

1. **Compte vérifié** (VO-21 : « un compte vérifié rédige directement sa réponse ») : il n'existe pas encore de vérification des comptes de responsables publics ; pour l'instant tout le monde passe par le formulaire et la vérification se fait dans la conversation avec l'équipe. À reprendre quand les comptes vérifiés existeront.
2. **Personne sans compte** : VO-21 prévoit un formulaire « y compris sans compte ». Ici, un compte est demandé (créé en une minute, sans e-mail) pour que l'équipe puisse répondre dans l'application. Faut-il aussi un envoi sans compte (par exemple avec une clé à usage unique dont la phrase secrète est montrée une fois pour suivre la réponse) ?
3. **Délai de trois mois** : la loi encadre le délai pour demander un droit de réponse ; faut-il l'afficher dans le formulaire (et refuser au-delà) ? À vérifier sur Légifrance avant de le coder.
4. **Clé de production** de la boîte de l'équipe : qui la détient (directeur de la publication, équipe de rédaction), sur quel appareil, et comment elle tourne.
5. **Durée de conservation** des demandes chez l'équipe, une fois traitées.

## 6. Tests

- Cœur (`gradlew :shared:core:jvmTest --tests "*VoirRequestsTest*"`) : 8 tests, tous au vert : aller-retour JSON des deux genres (champs, omission des facultatifs), refus de tout autre texte (version inconnue, genre inconnu, champs manquants), règle de longueur (max(mots, 1 000), comptage des mots), problèmes détectés avant l'envoi (dont adresses web invalides et message trop gros), signalement anonyme signé par une clé à usage unique différente de celle du compte et d'un signalement à l'autre, enveloppe adressée à la seule équipe et lisible par elle seule, attente puis départ sans serveur, droit de réponse depuis un compte lisible par l'équipe et pas par une autre clé, clé de l'équipe d'exemple.
- Interface (`gradlew :shared:ui:jvmTest --tests "*VoirRequestsScreenTest*" --tests "*VoirScreenTest*"`) : RESULTATS_UI
- Construction complète (`gradlew build`) : RESULTAT_BUILD
