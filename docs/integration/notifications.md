# Notifications (M31) : contrat entre l'application, le serveur et ntfy

Pôle Communauté, version 1.0 du 7 novembre 2026 (décision D-130, à consigner). Contrat à caler avec le Directeur technique, qui installe ntfy côté infrastructure.

## 1. Principe : un réveil, jamais un contenu

- Une poussée ne transporte **aucune information** : ni texte, ni expéditeur, ni sujet, ni catégorie. Son contenu est une constante (`{"v":1}`), chiffrée selon RFC 8291 comme l'exige UnifiedPush. Elle dit seulement à l'appareil : « synchronise-toi ».
- À chaque réveil, l'appareil fait sa synchronisation habituelle, compare ce qu'il reçoit à **ses propres abonnements, gardés sur l'appareil** (thèmes, territoire, contenus suivis, appels auxquels il a répondu, événements, conversations), puis affiche lui-même des notifications locales (règle D-044 du cahier fonctionnel, M31).
- Le serveur ne sait donc jamais ce qui intéresse une personne : il ne connaît ni ses thèmes, ni son territoire, ni ses suivis.
- Sur iPhone (plus tard), APNs reçoit le même réveil vide, sans aucun contenu.

## 2. Deux types d'inscription, désactivés par défaut

| Inscription | Ce que le serveur garde | Quand il réveille | Lien avec une identité |
|---|---|---|---|
| **Réveil public** (`scope = public`) | adresse de poussée et ses clés RFC 8291, date d'inscription, date du dernier renouvellement | Quand un contenu public nouveau est publié (fil, Voir, Comprendre, Agir, Décider), regroupé : au plus un réveil toutes les 30 minutes par inscription | Aucun : inscription anonyme, sans clé de compte |
| **Réveil messagerie** (`scope = mailbox`) | la même chose, plus la clé publique du compte | Quand un message privé (kind 1059) arrive pour cette clé, regroupé : au plus un réveil par minute | La clé publique du compte, que le serveur connaît déjà comme destinataire des messages (étiquette `p`) ; l'inscription est signée par cette clé |

- Les deux sont **éteintes par défaut** (principe 10). L'utilisateur les allume dans Mon espace, après une explication simple de ce que le serveur apprend (pour la messagerie : « le serveur saura qu'un téléphone attend les messages de ce compte, jamais ce qu'ils contiennent ni qui les envoie »).
- Un délai aléatoire de 0 à 60 secondes est ajouté à chaque réveil de messagerie, pour qu'on ne puisse pas relier l'arrivée d'un message à l'heure de son envoi.
- Une inscription non renouvelée depuis 30 jours est effacée. L'appareil la renouvelle à chaque démarrage. Désinscription immédiate possible (bouton dans Mon espace, ou réponse 404 ou 410 de la poussée : l'inscription est effacée).
- Les deux inscriptions utilisent des adresses de poussée différentes (deux « instances » UnifiedPush), pour que le serveur ne puisse pas relier l'inscription publique anonyme au compte.

## 3. API du serveur (`/api/v1/push`)

- `GET /api/v1/push/vapid` : clé publique VAPID du serveur (P-256, base64url), que l'application passe à `UnifiedPush.register`.
- `POST /api/v1/push/subscriptions` : corps JSON `{ "scope": "public" | "mailbox", "endpoint": "https://…", "p256dh": "…", "auth": "…" }`. Pour `mailbox`, en plus, un événement signé (NIP-98, kind 27235, étiquettes `u` et `method`, et `payload` = SHA-256 du corps) dans l'en-tête `Authorization: Nostr <base64>`, daté de moins de 60 secondes. Réponse `201 { "id": "<identifiant aléatoire>" }` ; la même adresse renvoie le même identifiant (renouvellement).
- `DELETE /api/v1/push/subscriptions/{id}` : désinscription (l'identifiant aléatoire suffit).
- **Hôtes de poussée autorisés** : le serveur refuse (`422 push_host_not_allowed`) toute adresse dont l'hôte n'est pas dans `PPF_PUSH_ALLOWED_HOSTS` (par défaut : notre ntfy seulement). Cela empêche le serveur d'être utilisé pour contacter n'importe quelle adresse (falsification de requêtes côté serveur) et garde les réveils sur notre infrastructure.
- Limites : 5 inscriptions par minute par clé de compte, preuve de travail auto-hébergée quand elle existera ; 10 000 inscriptions `public` au plus par jour au total, au-delà `429`.
- Aucune adresse IP n'est journalisée (règle commune).

## 4. Envoi des réveils par le serveur

- Requête Web Push standard (RFC 8030), envoyée à `PPF_PUSH_INTERNAL_URL` (`http://ntfy:8080`, réseau interne des conteneurs) en gardant le chemin de l'adresse, quand l'hôte de l'adresse est celui de notre ntfy ; jamais par le nom public. Requête vers l'adresse de l'inscription : contenu chiffré `aes128gcm` (RFC 8291) avec les clés de l'inscription, en-tête `Authorization: vapid t=<JWT ES256>, k=<clé publique>` (RFC 8292), `TTL: 3600`, `Urgency: normal` (`high` pour la messagerie).
- Chiffrement fait avec la bibliothèque standard de Java (ECDH P-256, HKDF, AES-GCM), sans nouvelle dépendance côté serveur.
- Clé privée VAPID : fichier hors dépôt désigné par `PPF_VAPID_PRIVATE_KEY_FILE` (clé EC P-256, PKCS#8 PEM, `/run/secrets/vapid_private_key`, une par environnement, créée sur le serveur). Revendication `sub` du jeton VAPID : l'adresse https du site, jamais une adresse e-mail. Sans ce fichier, les notifications sont éteintes et `GET /api/v1/push/vapid` répond `503 push_disabled`.
- Réponses `404` et `410` : inscription effacée. `429` : on respecte `Retry-After`.

## 5. Côté application

- **Android** : `org.unifiedpush.android:connector` 3.3.5 (Apache 2.0, dépendance d'exécution : `com.google.crypto.tink:tink` 1.23.0, bibliothèque de chiffrement sans aucun service Google ; à justifier dans la pull request). Un `PushService` dans `android/` reçoit le réveil et déclenche la synchronisation, via l'interface commune `PushWakeReceiver` de `shared/core`. Il faut une application « distributeur » sur le téléphone (l'application ntfy, réglée sur notre serveur ntfy) : Mon espace l'explique pas à pas et propose de rester sans notifications.
- **Sans distributeur, sur ordinateur et en attendant** : synchronisation tant que l'application est ouverte, notifications locales du système (sur ordinateur, l'icône de la zone de notification).
- **Notifications locales** : un canal par espace (Messages, Mobilisations et appels, Votes et pétitions, Contenus suivis, Équipe PPF) ; écran verrouillé : aucun contenu (« PPF : nouvelle activité ») ; par défaut, même écran déverrouillé, ni expéditeur ni extrait de message (assouplissement possible avec mise en garde) ; option « silence total » (S-25).
- **Abonnements par thème, campagne, territoire et type (S-23)** : gardés sur l'appareil seulement ; jamais envoyés au serveur.
- **Centre de notifications (S-24)** : liste locale depuis la barre du haut, effaçable.

## 6. Ce que le Directeur technique installe (infrastructure, réponse du 7 novembre 2026)

- Noms : `push.politisation-populaire-francaise.org` en production (public) ; `push.preprod.politisation-populaire-francaise.org` en préproduction (tunnel WireGuard seulement). Un enregistrement DNS de plus pour la propriétaire, ajouté au guide du serveur.
- ntfy v2.37.2 (image épinglée par empreinte), un conteneur par projet Compose, utilisateur propre, système de fichiers en lecture seule plus un volume de cache, 64 Mo de mémoire au plus.
- Configuration (`server.yml` dans infra) : `base-url` par environnement, pièces jointes, application web, inscription et connexion désactivées, `upstream-base-url` vide (jamais ntfy.sh), `cache-duration: 12h`, `auth-default-access: deny-all` avec seulement les sujets `up*` ouverts (noms aléatoires, contenus = réveils constants chiffrés), `log-level: warn`.
- Caddy retire l'adresse du client : les limites par visiteur de ntfy deviennent un plafond global ; la protection contre les abus reste dans l'API du serveur (limites par minute).
- Secrets et réglages : `PPF_VAPID_PRIVATE_KEY_FILE=/run/secrets/vapid_private_key`, `PPF_PUSH_ALLOWED_HOSTS=<nom de notre ntfy>`, `PPF_PUSH_INTERNAL_URL=http://ntfy:8080`. Seuls les sujets commençant par `up` existent (adresses `https://push.politisation-populaire-francaise.org/up<aléatoire>`) ; ntfy est dans main depuis 4acd7d8 (D-078).
- Hôtes sortants : aucun pour ntfy (pas de relais amont) ; ntfy ajouté au THREAT_MODEL et au REGISTRE_DONNEES avec l'infrastructure. Les services de poussée des navigateurs (Chrome, Firefox) sont des tiers : pas de notifications poussées pour la PWA.

## 7. Données (registre RGPD)

- Inscription publique : adresse de poussée et clés, sans lien avec un compte ; 30 jours après le dernier renouvellement.
- Inscription messagerie : la même chose et la clé publique du compte ; 30 jours après le dernier renouvellement ; but : réveiller le téléphone quand un message arrive.
- Aucune donnée sur les centres d'intérêt, aucune notification gardée sur le serveur.

## 8. État du serveur

Côté serveur (Kotlin, paquet `server/.../push`, 7 novembre 2026), fait :

- Table `push_subscription` (migration Flyway `V10__push.sql`, numéro donné par le chef d'équipe après l'entrée de `V8__back_office.sql` dans main) : identifiant aléatoire de 128 bits, portée, adresse de poussée, clés RFC 8291, clé de compte pour la messagerie seulement, dates d'inscription, de renouvellement et de dernier réveil. Ni adresse IP, ni appareil.
- Réglages `PushConfig` : `PPF_VAPID_PRIVATE_KEY_FILE` (`/run/secrets/vapid_private_key` ; sans lui, `503 push_disabled`), `PPF_PUSH_ALLOWED_HOSTS` (`push.politisation-populaire-francaise.org`, en préproduction `push.preprod.politisation-populaire-francaise.org` ; par défaut `push.` suivi de l'hôte de `PPF_PUBLIC_URL`), `PPF_PUSH_INTERNAL_URL` (`http://ntfy:8080`), et `PPF_PUBLIC_URL` (nouvelle variable, par défaut `https://politisation-populaire-francaise.org`), qui sert de revendication `sub` du jeton VAPID et de base de l'adresse que l'étiquette `u` de NIP-98 doit nommer (`<PPF_PUBLIC_URL>/api/v1/push/subscriptions`). La clé publique VAPID est déduite de la clé privée.
- API de la section 3 : `GET /api/v1/push/vapid`, `POST /api/v1/push/subscriptions` (https seulement, http en développement ; hôte autorisé et chemin de sujet UnifiedPush `/up<aléatoire>`, seuls sujets de notre ntfy, sinon `422 push_host_not_allowed` ; `p256dh` = point P-256 de 65 octets vérifié sur la courbe, `auth` = 16 octets ; NIP-98 pour la messagerie, sinon `401 unauthorized` ; même adresse = même identifiant), `DELETE /api/v1/push/subscriptions/{id}` (`204`, `404`). Limites : 5 inscriptions par minute et par clé, 10 000 nouvelles inscriptions publiques par jour (un renouvellement ne compte pas), puis `429` avec `Retry-After`.
- Envoi Web Push avec la seule bibliothèque standard de Java : chiffrement `aes128gcm` (RFC 8291, vérifié avec l'exemple de l'annexe A), jeton VAPID ES256 (RFC 8292), `TTL: 3600`, `Urgency: high` pour la messagerie et `normal` pour le public, passage par `PPF_PUSH_INTERNAL_URL` en gardant chemin et requête. `404` et `410` : inscription effacée ; `429` : l'hôte est mis en attente jusqu'à `Retry-After`.
- Réveils : branchés sur `POST /api/v1/events` (après l'enregistrement, sans bloquer la réponse). Une enveloppe (type 1059) réveille les inscriptions de messagerie de sa clé `p`, au plus une fois par minute, après 0 à 60 secondes au hasard ; un autre contenu public (ni éphémère, ni enveloppe, ni réglage de type 30078) réveille les inscriptions publiques, au plus une fois toutes les 30 minutes. Effacement des inscriptions non renouvelées depuis 30 jours, chaque heure.
- Base de données : le serveur ne crée ni ne modifie aucune table en marchant (rôle restreint aux lectures et écritures en préproduction et en production, migrations lancées à part) ; les droits sur `push_subscription` sont donnés par `afterMigrate.sql`.

Reste à faire ou à savoir :

- Les contenus publiés plus tard par un autre chemin (revendication acceptée après relecture, contenu d'« Agir » sorti de la file de relecture, contenus d'exemple chargés au démarrage) ne déclenchent pas de réveil public à eux seuls ; le prochain contenu public le fera.
- L'heure du dernier contenu public est gardée en mémoire : après un redémarrage, le premier réveil public attend un nouveau contenu.
- Preuve de travail auto-hébergée sur l'inscription : quand elle existera (cahier des charges, section 5.4).
