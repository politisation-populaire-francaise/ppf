# Contrat du service de signature de la plateforme, version 1.1

Rédigé le 7 novembre 2026 par la session « Directeur Technique PPF », pour relecture par le chef d'équipe et les pôles qui en dépendent (« Comprendre » et « Voir » : circuit éditorial ; « Décider » : étiquettes de statut, tableau du Pacte, archives ; « Agir » : fiches d'organisation vérifiées). Version 1 du 7 novembre 2026 au soir : décisions de la propriétaire (clé racine, approbations) et du chef d'équipe intégrées (sections 3, 4 et 5). Version 1.1 (7 novembre 2026, nuit) : formats, service et interface des étapes 2 et 3 (sections 2 et 5). Décisions : D-079, D-129.

## 1. Le besoin

- CLAUDE.md, section 7 : « chaque contenu publié est signé dans un format compatible avec les événements Nostr. La clé de la plateforme n'est jamais stockée en clair sur le serveur principal (service de signature séparé ou clé hors ligne). Rotation et révocation sont prévues dès le départ. »
- Aujourd'hui, les contenus éditoriaux d'exemple sont signés par une clé d'exemple gardée sur l'ordinateur de la propriétaire (`tools/content-signer`), et le serveur fait confiance aux clés de `PPF_EDITOR_KEYS`. Il n'existe encore aucune clé de la plateforme.
- Ce qui doit porter la signature de la plateforme : les contenus éditoriaux validés par deux éditeurs (Comprendre, Voir), les étiquettes de statut des revendications (NIP-32, type 1985), les fiches d'organisations vérifiées, le tableau public du Pacte, les archives horodatées, et plus tard les annonces de l'équipe.

## 2. Principe : une racine hors ligne, des clés de travail certifiées et révocables

1. **Clé racine de la plateforme** : jamais sur un serveur. Elle ne signe que des **certificats** (« cette clé de travail parle au nom de PPF, pour ces types de contenus, jusqu'à telle date ») et des **révocations**. Sa clé publique est inscrite dans les applications et sur le site (c'est la seule constante de confiance). Elle est utilisée quelques fois par an.
2. **Clés de travail** : une par usage (`editorial.comprendre`, `editorial.voir`, statuts des revendications, organisations, Pacte et archives : une clé éditoriale par espace, précisé le 7 novembre 2026 avec « Comprendre »), chacune dans le service de signature, valable 90 jours au plus, renouvelée avant l'échéance. Une clé compromise ne vaut que pour son usage et jusqu'à sa révocation ou son échéance.
3. **Les applications et le site** acceptent un contenu de la plateforme si sa clé est certifiée par la racine, pour ce type de contenu, à la date du contenu, et non révoquée. Cela remplace, pour les contenus de la plateforme, la liste fixe `EditorialTrust` ; les clés personnelles des éditeurs ne signent plus que leurs brouillons.

Formats (décrits dans `docs/FORMATS.md`, section 4 decies, D-129) : **certificat** = événement adressable de type `37240` signé par la racine (numéro libre dans la liste des NIP, vérifié le 7 novembre 2026), `d` = clé publique de la clé de travail, étiquettes `ppf-key-use` (une par usage), `ppf-not-before`, `ppf-not-after` (90 jours au plus) et `ppf-key-status` = `active` ; **révocation** = la même adresse republiée avec `["ppf-key-status","revoquee","<date d'effet>"]` (la version la plus récente l'emporte ; un statut inconnu, absent ou illisible vaut révocation depuis le début) ; **liste de l'équipe** = événement adressable de type `37241` signé par la racine, `d` = `ppf-equipe`, une étiquette `ppf-member` par membre (compte, date à partir de laquelle ses accords comptent, clé Nostr), `ppf-member-use` (usages qu'il peut approuver) et `ppf-credential` (identifiant et clé publique COSE de chaque clé d'accès). Les applications gardent sur l'appareil la liste des certificats et des révocations reçus ; la vérification commune est `PlatformTrust` (`shared/core`, paquet `core.platform`), qui ne reconnaît rien tant que la clé publique de la racine (`PlatformRoot.PUBLIC_KEY`) est vide. La préproduction a sa propre racine.

## 3. Où vit la clé racine : décidé (option A)

**Décision de la propriétaire (7 novembre 2026) : « Option A ».** La clé racine vit sur son téléphone, dans l'application PPF : un espace « Administration de la plateforme », réservé à l'appareil qui détient la racine, la garde chiffrée par le coffre du téléphone (comme son compte). Pour certifier ou révoquer une clé de travail, ou mettre à jour la liste de l'équipe, le back-office affiche un code QR ; elle le scanne, lit en clair ce qui est demandé et confirme. Secours : les 12 mots de la racine, recopiés sur papier et gardés en lieu sûr (jamais en photo, jamais dans un message).

Options écartées : la ressaisie des 12 mots à chaque usage (fastidieuse, risque d'œil indiscret) ; un appareil de signature dédié (un achat de plus).

## 4. Le service de signature des clés de travail

- **Conteneur à part** dans chaque environnement (`signer`), son propre utilisateur, système de fichiers en lecture seule, **aucune sortie vers Internet**, joignable seulement par le serveur sur un réseau interne dédié ; ses clés de travail chiffrées au repos, générées dans le conteneur (la clé secrète ne le quitte jamais) ; seule sa clé publique est montrée pour être certifiée.
- **Il ne signe que ce que sa règle permet** : pour chaque usage, les types et étiquettes acceptés, un plafond par heure, et surtout la **preuve d'accord**, vérifiée par le service lui-même avec les clés publiques des membres tirées d'une **liste d'équipe signée par la racine** (mise à jour par la propriétaire, comme un certificat). Un serveur principal compromis ne peut donc pas lui faire signer ce qu'aucun membre n'a approuvé. Règles d'accord (décision de la propriétaire et du chef d'équipe du 7 novembre 2026) :
  - **publication ordinaire** (contenu éditorial, étiquette de statut, fiche d'organisation, Pacte) : **une seule approbation** d'un membre du groupe concerné (rédacteurs de l'espace, modérateurs, vérificateurs des organisations), avec sa **connexion habituelle** : sa clé d'accès WebAuthn sur son téléphone ou son ordinateur, sans clé physique ;
  - **contenus de M17 qui mettent en cause une personne** (méthode validée « Mettre en lumière ») : **double validation**, deux approbations de deux rédacteurs différents, l'auteur du brouillon n'étant jamais l'un des deux ;
  - **signature automatique, sans approbation humaine**, seulement pour des usages bornés et listés, chacun avec sa propre clé de travail et sa règle stricte : preuves d'horodatage des archives (D-111), index des données ouvertes, clôture des urnes ; tout autre usage automatique demande une décision et une modification de ce contrat ;
  - les **clés de sécurité physiques** sont réservées aux super-administrateurs (sécurité maximale de l'administration, D-122) ; elles ne servent pas à publier.
- **Journal** : chaque signature et chaque refus, avec l'empreinte, l'usage et les membres qui ont approuvé, dans un journal chaîné du service (comme D-124), recopié dans le journal d'audit du back-office.
- **Rotation** : le service crée la clé suivante 15 jours avant l'échéance et la présente au back-office pour certification par la racine ; il bascule à la date de début du nouveau certificat.
- **Révocation** : en un geste dans le back-office (préparée par un administrateur, confirmée par la racine sur le téléphone) ; le service cesse aussitôt d'utiliser la clé.

## 5. Interface pour les pôles (côté serveur)

Version du 7 novembre 2026 au soir (étape 3, D-129) : `currentKey` ajoutée, preuve d'accord précisée, API du service décrite.

```kotlin
// server/.../server/signing/PlatformSigner.kt
interface PlatformSigner {
    /** The public key (hex) to put in the events of [use] now, null when the use has no certified key. */
    suspend fun currentKey(use: SigningUse): String?
    /** Asks for the platform signature of [unsigned] for [use]; [approval] carries the WebAuthn assertions. */
    suspend fun request(unsigned: UnsignedEvent, use: SigningUse, approval: ApprovalProof): SigningTicket
    suspend fun status(ticket: SigningTicket): SigningStatus   // Pending, Signed(SignedEvent), Refused(code, detail)
}
```

- Le pôle construit l'événement non signé avec la clé que donne `currentKey` (`pubkey`), le back-office recueille les accords, le pôle appelle `request`, puis publie l'événement signé, sans le modifier, par le port de publication unique. Mises en œuvre : `HttpPlatformSigner` (client du service, qui vérifie que l'événement rendu est bien celui demandé et qu'il est correctement signé) et, pour les tests et le développement, `FakePlatformSigner` (sources de test du serveur : une racine et une clé par usage générées à chaque lancement, en mémoire, avec leur `PlatformTrust`). Aucune file n'est encore branchée.
- **Preuve d'accord** (`ApprovalProof`, `shared/core`, `core.platform`) : `double_validation` (vrai pour un contenu de M17 qui met en cause une personne, dit par la file), `author` (le compte ou la clé Nostr de l'auteur du brouillon, obligatoire pour la double validation) et `assertions`. Une assertion WebAuthn par approbation exigée : aucune pour un usage automatique listé (toute assertion est alors refusée), une pour une publication ordinaire (l'auteur peut l'être), deux de deux comptes différents pour la double validation, l'auteur n'étant aucun des deux (comparé au compte et à la clé Nostr de la liste de l'équipe). Chaque assertion porte `account`, `credential_id`, `authenticator_data`, `client_data`, `signature` et, facultatif, `user_handle` (base64url sans remplissage) ; elle signe le défi SHA-256(`"ppf-approval-v1\n" + usage + "\n" + identifiant hexadécimal de l'événement non signé`), séparation des domaines qui empêche de réutiliser une assertion pour un autre usage ou un autre événement. Le service la vérifie lui-même : clé d'accès cherchée dans la liste d'équipe signée par la racine, compte identique, usage permis au membre depuis sa date de début, origine du back-office (`PPF_SIGNER_APPROVAL_ORIGIN`), présence et vérification de la personne exigées.
- **Rejeu** : un même événement n'est signé qu'une fois par usage (le service se souvient des identifiants signés, d'après son journal, au-delà de l'âge maximal d'un événement) ; une assertion rejouée est donc refusée (`already_signed`), et une assertion faite pour un autre événement ou un autre usage ne correspond pas au défi (`assertion_refused`). Le compteur de signatures des clés d'accès n'est pas suivi : les accords peuvent arriver dans n'importe quel ordre.
- **Limite connue** : la file dit si la double validation est exigée et qui est l'auteur ; un serveur principal compromis pourrait donc demander une seule approbation pour un contenu de M17, mais jamais obtenir une signature sans l'approbation réelle d'un membre autorisé pour cet usage. Une règle de forme propre à M17, vérifiable par le service, est à fixer avec « Voir » (D-129).

### API du service (réseau interne seulement)

Conteneur `signer`, port 8090 par défaut, aucune sortie vers Internet ; chaque requête porte `Authorization: Bearer <jeton>`, comparé en temps constant au fichier secret `PPF_SIGNER_CLIENT_TOKEN_FILE` (le même fichier côté serveur, `PPF_SIGNER_CLIENT_TOKEN_FILE`, avec `PPF_SIGNER_URL`). Réponses en JSON, `Cache-Control: no-store`, sans journal des requêtes.

| Requête | Réponse |
|---|---|
| `GET /health` (sans jeton) | `200 {"status":"ok","service":"ppf-signer"}` |
| `GET /v1/keys` | `200 {"root_configured":true,"team_list":"<id>","keys":[{"use":"editorial.voir","pubkey":"…","state":"active","created_at":…,"current":true,"not_before":…,"not_after":…}]}` ; `state` : `awaiting_certification` (à certifier par la racine), `scheduled` (certifiée, validité future), `active`, `expired`, `revoked` |
| `POST /v1/sign`, corps `{"use":"…","event":{"pubkey":"…","created_at":…,"kind":…,"tags":[…],"content":"…"},"approval":{"double_validation":false,"author":{"account":"…","pubkey":"…"},"assertions":[…]}}` (160 Kio au plus) | `200 {"status":"signed","event":{…}}` ; refus `{"status":"refused","code":"…","detail":"…"}` : `422` (règle), `409 already_signed`, `429 rate_limited` (avec `Retry-After`), `400 malformed_request`, `413 too_large` |
| `POST /v1/certificates`, corps : un événement ou un tableau (100 au plus) de certificats et révocations signés par la racine | `200 [{"id":"…","status":"stored"\|"duplicate"\|"superseded"\|"unknown_key"\|"not_a_certificate"\|"no_root"}]` |
| `POST /v1/team-list`, corps : la liste de l'équipe signée par la racine | `200 {"status":"stored"\|"duplicate"\|"superseded"}` ; `422 not_a_team_list` ou `no_root` |
| `GET /v1/certificates`, `GET /v1/team-list` | Ce que le service garde (public), pour que le serveur le serve aux applications ; `404 no_team_list` |
| `GET /v1/journal?after=<n>&limit=<1-1000>`, `GET /v1/journal/head` | Les lignes du journal après `n`, et la dernière ligne (`seq`, `hash`), pour les recopier dans le journal d'audit du back-office et ancrer la fin |

Codes de refus : `unknown_use`, `use_disabled`, `malformed_event`, `too_large`, `kind_not_allowed`, `missing_tag`, `bad_tag`, `tag_not_allowed`, `bad_content` (le détail nomme l'étiquette, jamais une valeur), `created_at_in_future`, `created_at_too_old`, `no_root`, `no_active_key`, `wrong_key`, `key_not_certified`, `key_revoked`, `key_expired`, `outside_certificate`, `no_team_list`, `unexpected_approval`, `approvals_missing`, `unknown_credential`, `account_mismatch`, `not_allowed_for_use`, `assertion_refused`, `same_account_twice`, `author_required`, `author_cannot_approve`, `already_signed`, `rate_limited`. Le client du serveur ajoute `signer_unavailable`, `signer_unauthorized`, `bad_signature_from_signer` et `unknown_ticket`.

Plafonds par heure (réglables par `PPF_SIGNER_MAX_PER_HOUR`) : 60 pour chaque espace éditorial et pour le Pacte, 120 pour les statuts, 30 pour les organisations, 600 pour les preuves d'horodatage, 6 pour l'index des données ouvertes, 30 pour les clôtures d'urnes.

**Journal du service** : un fichier `journal.jsonl` dans son dossier de données, une ligne par signature, refus, clé créée ou détruite, certificat et liste reçus : numéro, date, action, usage, identifiant de l'événement et sa date, clé, code de refus, comptes qui ont approuvé, empreinte de la ligne précédente et de la ligne (SHA-256 d'un tableau JSON canonique, comme D-124) ; jamais le contenu d'un événement ni l'adresse de l'appelant. Toute la chaîne est vérifiée au démarrage, qui échoue si une ligne a été retirée, changée ou déplacée. Au-delà de 1000 refus par heure, les suivants sont comptés en une ligne.

**Clés de travail** : générées dans le service, une par usage, gardées chiffrées au repos (AES-256-GCM, clé dérivée par HKDF-SHA-256 du fichier secret `PPF_SIGNER_KEK_FILE`, données associées : usage, clé publique, date) ; aucune requête ne rend une clé secrète. La clé suivante d'un usage est créée 15 jours avant la fin de la clé en cours (ou aussitôt si elle est révoquée) et montrée comme « à certifier » ; le service signe les nouveaux événements avec la clé active dont le certificat a commencé le plus tard, donc bascule à la date de début du nouveau certificat ; la clé secrète d'une clé révoquée ou échue est détruite.

- En tests et en développement : `FakePlatformSigner` (ci-dessus) côté serveur ; le service lui-même se lance en local par `gradlew :signer:run` avec ses variables (D-129).

## 6. Étapes

1. Accord sur ce contrat : fait le 7 novembre 2026 (option A de la section 3 choisie par la propriétaire, règles d'accord fixées avec le chef d'équipe).
2. Formats (certificat, révocation, liste d'équipe) dans `shared/core`, avec la vérification de confiance commune aux applications et au serveur : fait le 7 novembre 2026 (`core.platform`, D-129).
3. Conteneur `signer` (Kotlin, jar distinct dans la même image signée), règles par usage, vérification des assertions, journal : service fait le 7 novembre 2026 (module `signer`, jar `ppf-signer.jar`, interface `PlatformSigner` du serveur, D-129) ; reste à poser le conteneur et son réseau dans `infra/` (Directeur technique).
4. Côté back-office : défi WebAuthn = empreinte de l'événement, écrans de certification et de révocation (code QR).
5. Côté application : espace « Administration de la plateforme » (option A), réservé à l'appareil qui détient la racine.
6. Bascule des contenus de la plateforme : les applications font confiance aux clés certifiées ; la clé d'exemple reste pour les exemples.
