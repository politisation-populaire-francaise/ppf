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
- **Horodatage côté serveur** (jamais par l'appareil) : à l'enregistrement de l'engagement 1776, le serveur appelle `ArchiveService.stamp(sha256)` (server/archive, Décider, dans main à l'entrée de sa branche) ; tout le monde lit `GET /api/v1/archives/<id>` et `/api/v1/archives/<id>.ots` ; l'appareil vérifie avec `OtsFile.read` et `OtsBitcoinVerifier.check` (core/archive).
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
