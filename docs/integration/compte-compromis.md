# S-146 « Déclarer mon compte compromis » : conception

Pôle Communauté, version 1.0 du 8 novembre 2026, approuvée par le chef d'équipe (D-134). Calage avec Décider pour M16 (comptes, niveaux de votant) : section 6.

## 1. Le problème

Un compte PPF est une paire de clés gardée sur le téléphone. Si le téléphone est **saisi** (garde à vue), **volé** ou **piraté**, celui qui le détient peut écrire au nom de la personne : messages à ses contacts, publications, actions dans ses salons, et même ses votes. Il faut que la personne puisse dire, depuis un autre appareil, « ce compte n'est plus à moi depuis telle date », d'une façon que tout le monde peut vérifier et que le voleur ne peut pas contrefaire ni devancer.

## 2. La clé de secours

- À la création du compte, l'application dérive de la phrase de récupération (12 mots, D-017) **deux** clés :
  - la clé du compte, comme aujourd'hui : chemin NIP-06 `m/44'/1237'/0'/0/0` ;
  - la **clé de secours** : chemin `m/44'/1237'/0'/1/0` (branche « 1 » de BIP-32, que NIP-06 n'utilise pas : aucun autre client Nostr ne la prendra pour un compte).
- Le téléphone **publie aussitôt un engagement** signé par la clé du compte, qui nomme la clé publique de secours (section 3), puis **oublie la clé de secours**. Elle ne réapparaît qu'en retapant la phrase.
- **Changement nécessaire** : aujourd'hui le téléphone garde aussi la phrase (`StoredIdentity.recoveryWords`, pour « Afficher ma phrase »). Un téléphone saisi donnerait alors la clé de secours au voleur. La phrase doit donc être **effacée du téléphone** dès que la personne a confirmé l'avoir notée (étape de vérification de la création). « Afficher ma phrase » devient « Vérifier ma phrase » : la personne la retape, l'application dit si elle correspond. Question 1 (section 9).
- Comptes existants (comptes d'essai seulement, aucun utilisateur réel) : à la prochaine ouverture, l'application propose de retaper la phrase pour publier l'engagement ; tant que ce n'est pas fait, Mon espace l'indique (« Votre compte n'a pas encore de clé de secours »). C'est pour cela que la clé doit exister **avant** le lancement public : après, il faudrait faire repasser tout le monde.
- Comptes de collectif (D-133) : même mécanisme ; leur phrase est gardée par les responsables du collectif.

## 3. L'engagement (au moment de la création)

- Événement de type **1776** (format de la proposition Nostr NIP-41, « migration de clé ») signé par la clé du compte : étiquettes `["p", <clé publique de secours>]`, `["alt", "pubkey whitelisting event"]`, `["ppf-recovery", "1"]` ; contenu vide.
- Il est **horodaté par le serveur** (OpenTimestamps, `ArchiveService.stamp`, section 6 bis) pour prouver qu'il existait avant toute compromission.
- **Seul le premier engagement compte** : le plus ancien selon sa preuve d'horodatage, ou à défaut selon l'ordre de réception du serveur. Un voleur qui publierait plus tard un engagement vers sa propre clé est ignoré. Un deuxième engagement est refusé par le serveur (`409 recovery_already_set`).

## 4. L'avis de compromission

- Depuis n'importe quel appareil, la personne tape sa phrase (« J'ai perdu le contrôle de mon compte » dans Mon espace, ou sur le site). L'application dérive la clé de secours et publie un événement de type **1777** signé par **la clé de secours** : `["p", <clé compromise>]`, `["e", <id de l'engagement 1776>]`, `["proof", <id de l'événement 1040 de l'engagement>]`, `["alt", "pubkey migration event"]`, `["ppf-compromised-since", <date en secondes>]` (par défaut : maintenant ; la personne peut donner une date antérieure, par exemple l'heure de son interpellation), et facultativement `["ppf-successor", <nouvelle clé publique>]` si elle crée un nouveau compte.
- Il est **irréversible** et public. Le serveur vérifie la signature, le lien avec le premier engagement, puis l'enregistre et le sert à tous.
- Un même avis peut être publié par l'équipe, à la demande d'une personne qui a aussi perdu sa phrase, seulement après une vérification en personne (procédure de M16) ; il est alors signé par la clé de l'équipe et marqué comme tel.

## 5. Ce que fait chacun

**Le serveur**
- Refuse tout nouvel événement de la clé compromise (`403 account_compromised`), sans exception.
- Ajoute aux événements de cette clé datés d'après `ppf-compromised-since` un marqueur « suspect » dans ses réponses (sans les supprimer : la trace reste).
- Efface ses inscriptions aux réveils (M31) et retire ses rôles dans les salons (événements 9000 sans rôle générés par le serveur, visibles dans le journal du salon avec le motif « compte compromis »).
- Transmet l'avis à Décider (section 6).

**Les appareils des contacts**
- À chaque synchronisation, l'appareil demande les avis 1777 pour les clés qu'il connaît (comptes suivis, conversations, membres de ses cercles, salons rejoints), par petits lots mélangés, comme pour les comptes suivis : le serveur n'apprend pas la liste.
- S'il en trouve : bandeau dans la conversation et sur le profil (« Ce compte a été déclaré compromis le … »), messages reçus après la date marqués « reçu après la compromission, prudence », vérification en personne du contact retirée, écriture vers cette clé bloquée avec explication, et, si un successeur est nommé, proposition de suivre le nouveau compte **seulement après l'avoir vérifié en personne** (le successeur est une affirmation, pas une preuve d'identité).
- Notification locale (catégorie Messages ou Équipe).

**Les salons**
- Rôles retirés (voir serveur) ; messages postérieurs marqués suspects ; le salon reste.

## 6. Avec Décider (M16, « une personne, une voix »)

- Jetons de vote : une clé compromise ne reçoit plus de jetons ; les bulletins déjà déposés restent anonymes (le serveur ne peut pas les relier à la clé, par construction) : rien à annuler.
- Niveau « vérifié » : il ne passe **pas** automatiquement au successeur ; la personne refait la validation en personne avec son nouveau compte (sinon un voleur qui aurait la phrase deviendrait « vérifié »).
- Point d'appel proposé : `CompromisedAccounts.isCompromised(pubkey)` côté serveur, à appeler dans `PersonAccounts.refusal` ; à confirmer avec Décider.

## 6 bis. Calage avec Décider (8 novembre 2026)

- `PersonAccounts.refusal` appelle `CompromisedAccounts.isCompromised(pubkey)` et répond **403 account_compromised** (code distinct, pour que l'application dise la vraie raison), à l'inscription et à chaque demande de jetons.
- **Pas de double voix** : le successeur nommé par l'avis **hérite du curseur de délivrance** (`issued_up_to`) de l'ancienne clé ; il ne reçoit de jetons que pour les urnes ouvertes après le dernier service de l'ancienne. Côté serveur, Communauté fournit `CompromisedAccounts.successorOf(old)` et `predecessorOf(new)` ; une clé ne peut succéder qu'à une seule autre (`409 successor_taken`).
- Limite assumée : se déclarer compromis sans successeur puis créer un compte neuf redonne un curseur vierge dans la colonne « non vérifiés », comme deux comptes non vérifiés ; la colonne « vérifiés » n'est pas touchée.
- Niveau « vérifié » et certification « élu » : jamais transmis ; nouvelle validation en personne, nouvelle certification.
- **Horodatage côté serveur** (jamais par l'appareil) : à l'enregistrement de l'engagement 1776, le serveur appelle `ArchiveService.stamp(sha256)` (server/archive, Décider, dans main depuis le 8 novembre 2026) ; tout le monde lit `GET /api/v1/archives/<id>` et `/api/v1/archives/<id>.ots` ; l'appareil vérifie avec `OtsFile.read` et `OtsBitcoinVerifier.check` (core/archive).
- Il n'existe aucun registre des émissions par personne ni par urne (voulu, D-110) : seulement un curseur par compte. Les jetons déjà délivrés à l'ancienne clé restent sur le téléphone de la victime, utilisables tant qu'elle l'a ; sinon ces votes sont perdus. Jamais deux.
- **Avis par l'équipe** (D-134, point 3) : il attend l'étape 5 de Décider (validation en personne et lien chiffré de l'audit `vote_validation(session, week, sealed)`, scellé NIP-44 vers PPF_AUDIT_PUBLIC_KEY, clé « audit » hors ligne chez la propriétaire) ; rien à coder de ce côté avant.
- La phrase effacée du téléphone ne gêne aucun parcours de Décider.

## 7. Compatibilité Nostr (NIP-41), vérifiée le 8 novembre 2026

- **NIP-41 n'est pas adopté** : absent de la liste officielle des NIP ; le texte existe seulement dans la demande de fusion ouverte n° 829 (« NIP-41: simple account recovery », pablof7z, 2023, approuvée par trois relecteurs, jamais fusionnée ; brouillon sur la branche `pf7z-nip41`). Propositions concurrentes ouvertes : n° 2137 « Key migration » (staab, novembre 2025, renumérotée), n° 2139, n° 2114, n° 1452, n° 1056. Sources : github.com/nostr-protocol/nips (liste, demandes 829, 2137, 2139, brouillon 41.md).
- Le brouillon utilise exactement les types retenus ici : **1776** (désigne une clé, une seule étiquette `p`, étiquette `alt`), **1777** (bascule vers la clé désignée : `p` = ancienne clé, `e` = l'engagement 1776, `proof` = l'événement **1040** qui porte la preuve OpenTimestamps de l'engagement, `relays`, `alt`). Les deux doivent être horodatés ; entre deux 1777 concurrents, gagne celui dont l'engagement a la preuve d'horodatage **la plus ancienne**.
- Les clients du brouillon peuvent **afficher un avertissement tout de suite** et ne changent automatiquement la liste des comptes suivis qu'**après 60 jours** à compter de la réception de l'avis. Notre conception reste compatible : l'alerte est immédiate, et nous ne suivons **jamais** automatiquement le successeur (vérification en personne exigée).
- Nos avis portent donc en plus les étiquettes `proof` et `alt` du brouillon, et nos étiquettes `ppf-…` (ignorées par les autres clients). L'horodatage se fait avec `core/archive` (Décider) ; la preuve est aussi publiée en événement 1040 pour les clients Nostr. Attention : NIP-03 (1040) est marqué « déconseillé » dans la liste officielle (vulnérable à une attaque précise) : nous vérifions nous-mêmes la preuve OpenTimestamps (D-111) au lieu de lui faire confiance.
- À revoir avant le lancement public : si une des propositions concurrentes est adoptée, ajouter sa forme en plus de la nôtre (l'interface des avis le permet sans toucher aux écrans).

## 8. Modèle de menaces

| Menace | Réponse |
|---|---|
| Téléphone saisi ou volé, phrase non stockée dessus | Le voleur a la clé du compte, pas la clé de secours : l'avis 1777 l'arrête. |
| Le voleur publie le premier un avis pour discréditer la personne | Impossible sans la clé de secours. |
| Le voleur publie un nouvel engagement vers sa clé | Ignoré : seul le premier engagement horodaté compte. |
| La phrase écrite sur papier est saisie avec le téléphone | Le voleur peut déclarer le compte compromis et nommer un faux successeur : le successeur n'est jamais cru sans vérification en personne ; l'équipe peut intervenir après vérification. |
| Faux avis signé par l'équipe | Procédure à deux personnes du back-office (validation à deux, journal d'audit). |
| Le serveur cache un avis | Les avis sont des événements signés : ils pourront être relayés ailleurs (Nostr, miroirs, phase 3). |
| Un avis révèle que la personne a été interpellée | La date et le motif sont facultatifs ; l'avis dit seulement « compromis depuis ». |

## 9. Décisions (D-134, chef d'équipe par délégation de la propriétaire)

1. Phrase effacée du téléphone par défaut après la vérification ; « Vérifier ma phrase » ; possibilité de la garder après une mise en garde claire, réversible (principe 10) ; rappel une semaine plus tard ; l'inscription insiste sur la feuille de papier.
2. « Compromis depuis » jusqu'à 30 jours avant l'avis.
3. Avis par l'équipe seulement pour un compte déjà vérifié en personne (séance retrouvée par le lien chiffré de l'audit, D-112, vérification en personne, action à deux du back-office, journalisée) ; sans vérification ni phrase, aucune reprise possible, dit clairement à l'inscription.
4. Avec Décider : refus des jetons d'une clé compromise et délivrance au successeur sans double voix (à caler).

## 10. État

**Fait le 8 novembre 2026 (cœur et serveur, pôle Communauté)**
- Cœur, `core/identity` : `RecoveryPhrase.deriveRecoveryKeys()` (chemin `m/44'/1237'/0'/1/0`, à côté de `deriveKeys`, qui reste NIP-06 compte 0) ; `AccountRecovery` (constructeurs `commitment`/`signCommitment` et `notice`/`signNotice`, lecture `parseCommitment` et `parseNotice`, vérification `checkNotice` commune à l'appareil et au serveur : signature, lien avec l'engagement, signataire = clé de secours de l'engagement, « compromis depuis » entre 30 jours avant l'avis et l'avis, avis daté au plus 10 minutes dans le futur quand l'heure est donnée). Vecteur fixe : la phrase d'exemple de NIP-06 donne la clé de secours publique `bfbb7b7f…c24e` (calculée aussi hors du projet).
- Serveur, `server/accounts/CompromisedAccounts.kt`, migration provisoire `V19__account_recovery.sql` (tables `recovery_commitment` et `compromised_account`, clés publiques et identifiants seulement) : engagement et avis vérifiés puis stockés dans une même transaction, sous un verrou par compte ; refus `400 invalid_commitment`, `400 invalid_notice`, `409 recovery_already_set`, `409 already_compromised`, `409 successor_taken` ; après un avis, `403 account_compromised` pour tout événement de la clé (contrôle placé avant tout le reste du parcours de publication, revendications comprises) et pour une nouvelle inscription aux réveils de messagerie ; inscriptions de messagerie de la clé effacées, rôles de salon retirés (rien dans le registre légal : les rôles ne sont pas des contenus).
- Points d'appel pour Décider : `isCompromised(pubkey)`, `successorOf(old)`, `predecessorOf(new)`, `noticeFor(pubkey)`, `commitmentOf(pubkey)`.
- `GET /api/v1/accounts/<clé>/status` : `{compromised, since, successor, notice, commitment}` (format dans `docs/FORMATS.md`, section 5). Les événements de la clé datés d'après `since` restent servis tels quels ; l'appareil lit `since` (statut ou avis) pour les marquer.

**Fait le 8 novembre 2026 (appareil, Mon espace et conversations, pôle Communauté)**
- Cœur : `AccountRecoveryClient` (`ensureCommitment` : lit le statut, publie l'engagement seulement si `commitment` est nul, dit si l'engagement enregistré nomme bien la clé de secours de la phrase ; `declareCompromised` : dérive les deux clés de la phrase, relit l'engagement, signe l'avis avec la clé de secours, la détruit aussitôt, ramène « compromis depuis » dans les 30 jours ; `noticesFor` : avis des contacts demandés par lots mélangés de 10 clés au plus, revérifiés avec `checkNotice`) ; `RemoteEvents.fetchAccountStatus` (`GET /api/v1/accounts/<clé>/status`) ; raisons de refus nommées (`ACCOUNT_COMPROMISED`, `RECOVERY_ALREADY_SET`, `ALREADY_COMPROMISED`, `SUCCESSOR_TAKEN`, `INVALID_COMMITMENT`, `INVALID_NOTICE`) ; `CompromisedRefusals` : l'adaptateur du serveur retient les clés refusées en `403 account_compromised`, pour que Mon espace l'apprenne quel que soit l'écran qui publiait ; `InMemoryPublicationPort` applique les mêmes règles que le serveur (tests).
- `StoredIdentity` : `phraseVerified`, `phraseCheckDueAt` (rappel), `pendingRecoveryKey` (clé publique de secours dont l'engagement attend le réseau : la phrase peut être effacée avant que l'engagement parte), `recoveryCommitment`. Les enregistrements antérieurs se lisent avec ces valeurs par défaut (phrase non vérifiée : le rappel s'affiche, et l'engagement part tout seul puisque la phrase est sur l'appareil).
- Création : vérification de trois mots, puis phrase **effacée** ; « Garder la phrase sur ce téléphone » après une mise en garde (Confirmer, Annuler), réversible (« Effacer la phrase de ce téléphone ») ; « Vérifier plus tard » garde la phrase et affiche un rappel dans Mon espace une semaine plus tard ; texte sur la feuille de papier et sur l'absence de reprise sans vérification en personne. Restauration par la phrase : comptée comme vérifiée, phrase non gardée.
- « Vérifier ma phrase » : trois mots quand la phrase est sur l'appareil, les douze sinon (la phrase doit donner la clé du compte) ; pour un compte sans clé de secours (importé par sa clé « nsec », ou ancien), c'est aussi ce qui publie l'engagement. Mon espace dit « Clé de secours : Enregistrée, En attente du serveur, Absente, Ne correspond pas ».
- « J'ai perdu le contrôle de mon compte » (Mon espace, avec ou sans compte sur l'appareil) : phrase, « compromis depuis » réglable à l'heure près (maintenant par défaut, 30 jours en arrière au plus), nouveau compte (par défaut celui de l'appareil s'il en a un, sinon un nouveau compte dont la phrase est notée et vérifiée avant que l'avis le nomme ; ou aucun, ou une autre clé), confirmation de l'irréversibilité, publication, refus du serveur en français.
- Compte de l'appareil compromis (statut ou refus 403) : bandeau dans Mon espace, carte du réglage de la boîte retirée, écriture bloquée dans les conversations privées avec l'explication.
- Contacts (`ui/messages/ContactAlerts`) : après chaque synchronisation de la messagerie (au plus toutes les 10 minutes), avis des participants des conversations demandés au serveur, vérifiés, gardés dans la table des événements publics de l'appareil (aucune nouvelle table) ; bandeau « Ce compte a été déclaré compromis le … » dans la conversation, messages datés d'après « compromis depuis » marqués « Reçu après la compromission, prudence », écriture vers la clé bloquée, vérification en personne retirée, nouveau compte seulement proposé (« Écrire au nouveau compte »).

**Écarts avec la conception (appareil)**
- Le rappel d'une semaine est une carte de Mon espace et, depuis D-135, une entrée de la catégorie de notification « Mon compte » : toujours dans le centre de notifications, notification du système seulement si la catégorie est allumée (proposée à la fin de « Vérifier ma phrase »). Les avis des contacts et celui du compte de l'appareil y entrent aussi.
- Les avis des contacts ne sont demandés que pour les participants des conversations privées ; comptes suivis, membres des cercles et salons rejoints : à brancher par leurs écrans (la classe `ContactAlerts` sert telle quelle). Le profil d'un compte et la notification locale (section 5) ne sont pas faits.
- Le marqueur « suspect » se fonde sur la date écrite par l'auteur du message (NIP-17) : quelqu'un qui détient la clé peut antidater un message ; l'écriture vers la clé est de toute façon bloquée.
- « Voir » (8 novembre 2026) : le droit de réponse est grisé avec l'explication (`PublishBlockedNotice`) ; le signalement d'erreur reste possible en anonyme, avec une clé à usage unique, mais plus avec le compte.
- « Comprendre » et circuit éditorial (9 novembre 2026) : **rien à brancher dans les files `editorial.comprendre` et `editorial.voir`**, après examen. Leurs brouillons ne sont pas signés par la clé d'un citoyen : ils sont écrits par des comptes d'équipe, qui se connectent avec leurs clés d'accès (passkeys) et jamais avec une clé Nostr, puis signés par la clé de la plateforme (service de signature). La clé Nostr facultative d'un compte d'équipe (`team_account.nostr_pubkey`) ne sert qu'à reconnaître qu'un relecteur et un auteur sont la même personne (`EditorialReviews.nostrKeyOf`) ; qu'elle soit déclarée compromise ne donne aucun accès au back-office. `CompromisedAccounts.heldVerdict` ne s'y applique donc pas. Si un membre de l'équipe déclare sa clé compromise, l'administrateur relie la nouvelle clé à son compte d'équipe pour que ce contrôle reste juste (back-office, Directeur technique). L'espace « Comprendre » n'a aucun bouton de publication citoyen aujourd'hui ; les demandes de vérification (CO-18) passent par le contact (M30), sans compte relié.
- Les autres espaces (Agir, Décider, salons) ne désactivent pas encore leurs boutons de publication pour un compte compromis : le serveur les refuse (`403`), ils affichent leur message de refus générique, et Mon espace dit la vraie raison.
- « Décider » (8 novembre 2026, main 5db6689d) : l'envoi d'une revendication et le code de validation sont grisés avec l'explication. Pour, Contre et Blanc **restent actifs** quand l'appareil a déjà reçu le jeton de l'urne (le jeton est aveuglé, jamais relié au compte : il reste celui de la personne) ; sous les boutons d'une urne ouverte, quand `LocalCanPublish` est faux, la ligne `urne_compromised_tokens` dit : « Ce compte a été déclaré compromis : il ne reçoit plus de jeton. Ceux que ce téléphone a déjà reçus restent utilisables ; le nouveau compte annoncé dans la déclaration, s'il y en a un, recevra ceux des urnes suivantes. » (approuvé par le chef d'équipe).
- Salons (8 novembre 2026, Communauté) : l'écriture dans un salon et les deux boutons de création de salon sont grisés, avec l'explication commune (`PublishBlockedNotice`).
- « Agir » : grisage de ses boutons de publication avec son entrée de S-146 dans main (en cours le 9 novembre 2026). D'ici là, le serveur refuse (`403`), l'écran affiche son message de refus générique, et Mon espace dit la vraie raison.

**Écarts avec la conception**
- Section 5 : le serveur ne génère pas d'événements 9000 « sans rôle » pour retirer les rôles (il faudrait une clé de signature du serveur, jamais en clair sur le serveur principal) : il retire directement les lignes de `group_role`, et `GET /api/v1/groups/<id>/roles` ne montre plus la clé. Le motif « compte compromis » ne figure donc pas dans le journal du salon ; l'avis public en tient lieu. À revoir avec le service de signature.
- Section 5 : le marqueur « suspect » n'est pas ajouté aux réponses (le format des événements ne change pas) ; l'appareil le calcule avec `since`.
- `proof` reste facultative tant que l'horodatage n'existe pas.
- Ajout : `commitment` dans le statut (identifiant du premier engagement, `null` s'il n'y en a pas), pour que Mon espace puisse dire « Votre compte n'a pas encore de clé de secours ».

**Fait par Décider (8 novembre 2026, main 5db6689d)**
- Horodatage OpenTimestamps de l'engagement : `CommitmentStamper` branché sur `ArchiveService.commitmentStamper()` dans `Application.kt` ; chaque premier engagement de récupération part dans le lot d'horodatage suivant.
- `PersonAccounts.refusal` : une clé compromise ne s'inscrit plus, ne reçoit plus de jeton, n'est plus validée ni certifiée et ne vote plus en public (`403 account_compromised`) ; le successeur nommé par l'avis reprend le curseur de jetons de l'ancienne clé (`predecessorOf`), sans jamais recevoir deux fois le jeton d'une même urne.
- Files « Revendications proposées » et « Propositions et amendements » (`server/decider/CompromisedAuthors`) : ce qui a été signé à partir de « compromis depuis » ne peut qu'être refusé ; ce qui a été signé avant porte une mention et se relit comme le reste (`heldVerdict`, comme la file des créations de salons).

**En attente**
- Publier la preuve de l'engagement en événement 1040 et la nommer dans `proof` : il faut la signature de la plateforme (usage des preuves d'horodatage, service de signature du Directeur technique) ; `proof` reste facultative d'ici là.
- Avis par l'équipe (D-134, point 3) : après l'étape 5 de Décider.
- Site web : « J'ai perdu le contrôle de mon compte » sans l'application (section 4).

**Questions ouvertes**
- Tranché par le chef d'équipe le 8 novembre 2026 : un contenu **mis en attente** puis dont la clé est déclarée compromise ne peut qu'être refusé s'il a été signé à partir de « compromis depuis », et porte une mention sinon (`heldVerdict` ; files des créations de salons, des revendications et des délibérations ; files d'Agir avec son entrée).
- Un salon dont le seul administrateur était la clé compromise n'a plus d'administrateur : file « Salons sans administrateur » (`groups.orphans`, dans main depuis le 8 novembre 2026, action « Laisser sans administrateur » à deux) ; la reprise par l'équipe (`docs/integration/salons-reprise.md`) attend le contrat 1.3 du service de signature.
