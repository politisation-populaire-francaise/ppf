# Contrat du service de signature de la plateforme, version 1

Rédigé le 7 novembre 2026 par la session « Directeur Technique PPF », pour relecture par le chef d'équipe et les pôles qui en dépendent (« Comprendre » et « Voir » : circuit éditorial ; « Décider » : étiquettes de statut, tableau du Pacte, archives ; « Agir » : fiches d'organisation vérifiées). Version 1 du 7 novembre 2026 au soir : décisions de la propriétaire (clé racine, approbations) et du chef d'équipe intégrées (sections 3, 4 et 5). Décisions : D-079 (et la suite du bloc D-120 à D-129 si besoin).

## 1. Le besoin

- CLAUDE.md, section 7 : « chaque contenu publié est signé dans un format compatible avec les événements Nostr. La clé de la plateforme n'est jamais stockée en clair sur le serveur principal (service de signature séparé ou clé hors ligne). Rotation et révocation sont prévues dès le départ. »
- Aujourd'hui, les contenus éditoriaux d'exemple sont signés par une clé d'exemple gardée sur l'ordinateur de la propriétaire (`tools/content-signer`), et le serveur fait confiance aux clés de `PPF_EDITOR_KEYS`. Il n'existe encore aucune clé de la plateforme.
- Ce qui doit porter la signature de la plateforme : les contenus éditoriaux validés par deux éditeurs (Comprendre, Voir), les étiquettes de statut des revendications (NIP-32, type 1985), les fiches d'organisations vérifiées, le tableau public du Pacte, les archives horodatées, et plus tard les annonces de l'équipe.

## 2. Principe : une racine hors ligne, des clés de travail certifiées et révocables

1. **Clé racine de la plateforme** : jamais sur un serveur. Elle ne signe que des **certificats** (« cette clé de travail parle au nom de PPF, pour ces types de contenus, jusqu'à telle date ») et des **révocations**. Sa clé publique est inscrite dans les applications et sur le site (c'est la seule constante de confiance). Elle est utilisée quelques fois par an.
2. **Clés de travail** : une par usage (`editorial.comprendre`, `editorial.voir`, statuts des revendications, organisations, Pacte et archives : une clé éditoriale par espace, précisé le 7 novembre 2026 avec « Comprendre »), chacune dans le service de signature, valable 90 jours au plus, renouvelée avant l'échéance. Une clé compromise ne vaut que pour son usage et jusqu'à sa révocation ou son échéance.
3. **Les applications et le site** acceptent un contenu de la plateforme si sa clé est certifiée par la racine, pour ce type de contenu, à la date du contenu, et non révoquée. Cela remplace, pour les contenus de la plateforme, la liste fixe `EditorialTrust` ; les clés personnelles des éditeurs ne signent plus que leurs brouillons.

Formats (à décrire dans `docs/FORMATS.md`, section nouvelle) : certificat = événement adressable signé par la racine, d'un `kind` dédié à choisir dans la plage 30000 à 39999 (vérifier qu'aucune NIP ne l'utilise), `d` = clé publique de la clé de travail, étiquettes `ppf-key-use` (usages), `ppf-not-before`, `ppf-not-after` ; révocation = la même adresse republiée avec `ppf-key-status` = `revoquee` et la date d'effet (la version la plus récente l'emporte). Les applications gardent sur l'appareil la liste des certificats et des révocations reçus.

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

```kotlin
interface PlatformSigner {
    /** Asks for the platform signature of [unsigned] for [use]; [approval] carries the WebAuthn assertions. */
    suspend fun request(unsigned: UnsignedEvent, use: SigningUse, approval: ApprovalProof): SigningTicket
    suspend fun status(ticket: SigningTicket): SigningStatus   // Pending, Signed(SignedEvent), Refused(reason)
}
```

- Le pôle construit l'événement non signé, le back-office recueille les deux accords (validation à deux, D-123), le pôle appelle `request`, puis publie l'événement signé par le port de publication unique.
- **Preuve d'accord** (`ApprovalProof`) : une assertion WebAuthn par approbation exigée (une pour une publication ordinaire, deux de deux comptes différents pour un contenu de M17 qui met en cause une personne ; aucune pour un usage automatique listé), d'un compte qui a la permission de publier pour cet usage ; chacune porte sur le défi SHA-256(`"ppf-approval-v1\n" + usage + "\n" + identifiant hexadécimal de l'événement non signé`), séparation des domaines qui empêche de réutiliser une assertion pour un autre usage ou un autre événement ; chaque assertion contient l'identifiant du compte, l'identifiant de la clé, `authenticatorData`, `clientDataJSON` et la signature ; le service les vérifie lui-même avec la liste d'équipe signée par la racine. Pour la double validation, l'auteur du brouillon ne peut pas être l'un des deux (vérifié par la file éditoriale, clé Nostr de l'auteur contre celle des comptes).
" + usage + "
" + identifiant hexadécimal de l'événement non signé`), séparation des domaines qui empêche de réutiliser une assertion pour un autre usage ou un autre événement ; chaque assertion contient l'identifiant du compte, l'identifiant de la clé, `authenticatorData`, `clientDataJSON` et la signature ; le service les vérifie lui-même avec la liste d'équipe signée par la racine. L'auteur d'un brouillon ne peut pas être l'un des deux (vérifié par la file éditoriale, clé Nostr de l'auteur contre celle des comptes).
- En tests et en développement : un signataire factice en mémoire avec une clé générée à chaque lancement.

## 6. Étapes

1. Accord sur ce contrat : fait le 7 novembre 2026 (option A de la section 3 choisie par la propriétaire, règles d'accord fixées avec le chef d'équipe).
2. Formats (certificat, révocation, liste d'équipe) dans `shared/core`, avec la vérification de confiance commune aux applications et au serveur.
3. Conteneur `signer` (Kotlin, jar distinct dans la même image signée), règles par usage, vérification des assertions, journal.
4. Côté back-office : défi WebAuthn = empreinte de l'événement, écrans de certification et de révocation (code QR).
5. Côté application : espace « Administration de la plateforme » (option A), réservé à l'appareil qui détient la racine.
6. Bascule des contenus de la plateforme : les applications font confiance aux clés certifiées ; la clé d'exemple reste pour les exemples.
