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

- Requête Web Push standard (RFC 8030) vers l'adresse de l'inscription : contenu chiffré `aes128gcm` (RFC 8291) avec les clés de l'inscription, en-tête `Authorization: vapid t=<JWT ES256>, k=<clé publique>` (RFC 8292), `TTL: 3600`, `Urgency: normal` (`high` pour la messagerie).
- Chiffrement fait avec la bibliothèque standard de Java (ECDH P-256, HKDF, AES-GCM), sans nouvelle dépendance côté serveur.
- Clé privée VAPID : fichier hors dépôt désigné par `PPF_VAPID_PRIVATE_KEY_FILE` (format PKCS#8). Sans ce fichier, les notifications sont éteintes et `GET /api/v1/push/vapid` répond `503 push_disabled`.
- Réponses `404` et `410` : inscription effacée. `429` : on respecte `Retry-After`.

## 5. Côté application

- **Android** : `org.unifiedpush.android:connector` 3.3.5 (Apache 2.0, dépendance d'exécution : `com.google.crypto.tink:tink` 1.23.0, bibliothèque de chiffrement sans aucun service Google ; à justifier dans la pull request). Un `PushService` dans `android/` reçoit le réveil et déclenche la synchronisation, via l'interface commune `PushWakeReceiver` de `shared/core`. Il faut une application « distributeur » sur le téléphone (l'application ntfy, réglée sur notre serveur ntfy) : Mon espace l'explique pas à pas et propose de rester sans notifications.
- **Sans distributeur, sur ordinateur et en attendant** : synchronisation tant que l'application est ouverte, notifications locales du système (sur ordinateur, l'icône de la zone de notification).
- **Notifications locales** : un canal par espace (Messages, Mobilisations et appels, Votes et pétitions, Contenus suivis, Équipe PPF) ; écran verrouillé : aucun contenu (« PPF : nouvelle activité ») ; par défaut, même écran déverrouillé, ni expéditeur ni extrait de message (assouplissement possible avec mise en garde) ; option « silence total » (S-25).
- **Abonnements par thème, campagne, territoire et type (S-23)** : gardés sur l'appareil seulement ; jamais envoyés au serveur.
- **Centre de notifications (S-24)** : liste locale depuis la barre du haut, effaçable.

## 6. Ce que le Directeur technique installe (infrastructure)

- ntfy (version stable actuelle : 2.37.2) en conteneur, dans chaque projet Docker Compose (production et préproduction séparées), derrière Caddy, sous un nom du domaine principal.
- Réglages : `auth-default-access: deny-all` sauf le chemin UnifiedPush (`up*`, écriture ouverte, comme le prévoit ntfy pour UnifiedPush) ; `cache-duration` court (12 h au plus : un téléphone éteint récupère son réveil en se rallumant) ; journaux sans adresse IP (niveau `warn`, vérifier ce qu'écrit ntfy) ; `upstream-base-url` vide tant qu'il n'y a pas d'iPhone (sinon ntfy.sh serait contacté).
- Secrets : `PPF_VAPID_PRIVATE_KEY_FILE` (un fichier par environnement), `PPF_PUSH_ALLOWED_HOSTS`.
- Hôtes sortants du serveur : notre ntfy seulement (à ajouter à `docs/EGRESS.md`, THREAT_MODEL et REGISTRE_DONNEES).

## 7. Données (registre RGPD)

- Inscription publique : adresse de poussée et clés, sans lien avec un compte ; 30 jours après le dernier renouvellement.
- Inscription messagerie : la même chose et la clé publique du compte ; 30 jours après le dernier renouvellement ; but : réveiller le téléphone quand un message arrive.
- Aucune donnée sur les centres d'intérêt, aucune notification gardée sur le serveur.
