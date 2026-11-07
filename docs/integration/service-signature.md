# Contrat du service de signature de la plateforme, version 0 (proposition)

Rédigé le 7 novembre 2026 par la session « Directeur Technique PPF », pour relecture par le chef d'équipe et les pôles qui en dépendent (« Comprendre » et « Voir » : circuit éditorial ; « Décider » : étiquettes de statut, tableau du Pacte, archives ; « Agir » : fiches d'organisation vérifiées). Rien n'est codé avant l'accord sur ce contrat. Décisions : D-079 (et la suite du bloc D-120 à D-129 si besoin).

## 1. Le besoin

- CLAUDE.md, section 7 : « chaque contenu publié est signé dans un format compatible avec les événements Nostr. La clé de la plateforme n'est jamais stockée en clair sur le serveur principal (service de signature séparé ou clé hors ligne). Rotation et révocation sont prévues dès le départ. »
- Aujourd'hui, les contenus éditoriaux d'exemple sont signés par une clé d'exemple gardée sur l'ordinateur de la propriétaire (`tools/content-signer`), et le serveur fait confiance aux clés de `PPF_EDITOR_KEYS`. Il n'existe encore aucune clé de la plateforme.
- Ce qui doit porter la signature de la plateforme : les contenus éditoriaux validés par deux éditeurs (Comprendre, Voir), les étiquettes de statut des revendications (NIP-32, type 1985), les fiches d'organisations vérifiées, le tableau public du Pacte, les archives horodatées, et plus tard les annonces de l'équipe.

## 2. Principe : une racine hors ligne, des clés de travail certifiées et révocables

1. **Clé racine de la plateforme** : jamais sur un serveur. Elle ne signe que des **certificats** (« cette clé de travail parle au nom de PPF, pour ces types de contenus, jusqu'à telle date ») et des **révocations**. Sa clé publique est inscrite dans les applications et sur le site (c'est la seule constante de confiance). Elle est utilisée quelques fois par an.
2. **Clés de travail** : une par usage (éditorial, statuts, organisations, Pacte et archives), chacune dans le service de signature, valable 90 jours au plus, renouvelée avant l'échéance. Une clé compromise ne vaut que pour son usage et jusqu'à sa révocation ou son échéance.
3. **Les applications et le site** acceptent un contenu de la plateforme si sa clé est certifiée par la racine, pour ce type de contenu, à la date du contenu, et non révoquée. Cela remplace, pour les contenus de la plateforme, la liste fixe `EditorialTrust` ; les clés personnelles des éditeurs ne signent plus que leurs brouillons.

Formats (à décrire dans `docs/FORMATS.md`, section nouvelle) : certificat = événement adressable signé par la racine, d'un `kind` dédié à choisir dans la plage 30000 à 39999 (vérifier qu'aucune NIP ne l'utilise), `d` = clé publique de la clé de travail, étiquettes `ppf-key-use` (usages), `ppf-not-before`, `ppf-not-after` ; révocation = la même adresse republiée avec `ppf-key-status` = `revoquee` et la date d'effet (la version la plus récente l'emporte). Les applications gardent sur l'appareil la liste des certificats et des révocations reçus.

## 3. Où vit la clé racine : options

| Option | Comment | Pour | Contre |
|---|---|---|---|
| **A. Sur le téléphone de la propriétaire, dans l'application PPF** (recommandée) | Un espace « Administration de la plateforme » de l'APK garde la clé racine, chiffrée par le coffre du téléphone (comme son compte) ; pour certifier ou révoquer, le back-office affiche un code QR, elle le scanne, vérifie ce qui est demandé en clair et confirme ; secours : les 12 mots de la racine sur papier, dans un lieu sûr | Aucun outil technique pour elle, quelques gestes par trimestre, jamais en ligne côté serveur | Le téléphone devient précieux (secours papier indispensable) |
| B. Sur papier seulement, ressaisie à chaque usage | Les 12 mots tapés dans l'application au moment de certifier, puis effacés | Rien ne reste sur un appareil | Saisie fastidieuse, risque d'erreur et d'œil indiscret |
| C. Un service de signature distant (NIP-46) sur un appareil dédié | Un petit appareil allumé en permanence chez quelqu'un de confiance | Automatique | Un appareil de plus à acheter et sécuriser |

## 4. Le service de signature des clés de travail

- **Conteneur à part** dans chaque environnement (`signer`), son propre utilisateur, système de fichiers en lecture seule, **aucune sortie vers Internet**, joignable seulement par le serveur sur un réseau interne dédié ; ses clés de travail chiffrées au repos, générées dans le conteneur (la clé secrète ne le quitte jamais) ; seule sa clé publique est montrée pour être certifiée.
- **Il ne signe que ce que sa règle permet** : pour chaque usage, les types et étiquettes acceptés, un plafond par heure, et surtout la **preuve d'accord** : pour un contenu éditorial ou une décision du back-office, deux **assertions WebAuthn** de deux membres différents de l'équipe, portant sur l'empreinte exacte de l'événement à signer (le back-office les recueille déjà pour les actions sensibles : le défi devient l'empreinte de l'événement). Le service vérifie lui-même ces assertions avec les clés publiques des membres, tirées d'une **liste d'équipe signée par la racine** (mise à jour par la propriétaire, comme un certificat). Un serveur principal compromis ne peut donc pas lui faire signer ce qu'aucun membre n'a approuvé.
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
- En tests et en développement : un signataire factice en mémoire avec une clé générée à chaque lancement.

## 6. Étapes

1. Accord sur ce contrat (chef d'équipe, pôles) et choix de l'option de la section 3 par la propriétaire.
2. Formats (certificat, révocation, liste d'équipe) dans `shared/core`, avec la vérification de confiance commune aux applications et au serveur.
3. Conteneur `signer` (Kotlin, jar distinct dans la même image signée), règles par usage, vérification des assertions, journal.
4. Côté back-office : défi WebAuthn = empreinte de l'événement, écrans de certification et de révocation (code QR).
5. Côté application : espace « Administration de la plateforme » (option A), réservé à l'appareil qui détient la racine.
6. Bascule des contenus de la plateforme : les applications font confiance aux clés certifiées ; la clé d'exemple reste pour les exemples.
