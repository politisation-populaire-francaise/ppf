# Contrat : contact, réclamations et « Mes échanges » (M30), version 1

Rédigé le 8 novembre 2026 par la session « Directeur Technique PPF ». Décision : **option B** du brainstorming (cahier des charges, entrée « [2026-11-08] Contact, réclamations et « Mes échanges » (M30) »), validée par le chef d'équipe par délégation de la propriétaire (« Pour les decisions a prendre, je suis tes recommandations »), **sans e-mail en version 1** ; consignée dans D-140. Concerne le cahier fonctionnel, M30 (S-102 à S-111), le cahier des charges, sections 5, 9.4 et 9.6, le point d'attention n° 20 (durées de conservation) et D-056 (demandes de « Voir »).

## 1. Principe

- Tout échange avec l'équipe est un **dossier**. Le texte part **chiffré de bout en bout** depuis l'appareil vers la **boîte de l'équipe** (une clé dédiée, distincte des clés qui signent les contenus) ; les réponses de l'équipe partent chiffrées vers la **clé du dossier**, tirée d'un code secret que seule la personne connaît.
- Le serveur ne garde que des **enveloppes illisibles** (NIP-59) et les **métadonnées du suivi** : type, motif ou catégorie, statut, dates, échéance, priorité, membre assigné. Il ne sait pas qui a écrit, sauf si la personne relie elle-même son compte, et même alors seule l'équipe le voit, dans le texte chiffré.
- L'équipe trie dans le back-office web (métadonnées seulement) et lit et répond dans le **client de l'équipe** (étape 3), le même que celui des droits de réponse de « Voir ».
- Rien n'est envoyé par e-mail. La personne retrouve son dossier dans « Mes échanges » (application) ou avec son numéro et son code (site) ; une notification sans contenu (M31) viendra ensuite.

## 2. Ce que la loi impose, et ce que PPF s'impose

| Règle | Source | Application dans PPF |
|---|---|---|
| Droits sur les données : réponse dans les meilleurs délais et **au plus tard un mois** après la réception ; prolongation de deux mois possible selon la complexité et le nombre de demandes, annoncée et motivée dans le premier mois | RGPD, article 12, paragraphe 3 | Échéance calculée à la réception ; prolongation enregistrée avec son motif, refusée après le premier mois ; alerte 7 jours et 2 jours avant |
| Droit de réponse en ligne : insertion **dans les trois jours** de la réception | LCEN, article 1-1, III (rédaction de la loi n° 2024-449 du 21 mai 2024), renvoyant à l'article 13 de la loi du 29 juillet 1881 | Échéance de 3 jours ; alerte dès la réception et 1 jour avant |
| Réclamation contre une décision de modération : ouverte **au moins six mois** après que la personne a été informée de la décision ; traitement « en temps opportun, de manière non discriminatoire, diligente et non arbitraire » ; décision « sous le contrôle de collaborateurs dûment qualifiés, et pas uniquement par des moyens automatisés » | Règlement (UE) 2022/2065 (DSA), article 20, paragraphes 1, 2, 4 et 6 | Appliqué **par choix** : l'article 19 en exempte les micro et petites entreprises, ce qu'est l'association (à confirmer par l'avocat). Réclamation acceptée jusqu'à six mois après la décision ; examinée par **une autre personne que l'auteur de la décision** (règle de PPF, plus stricte) ; objectif interne de 15 jours |
| Exposé des motifs, avec l'usage éventuel de moyens automatisés | DSA, article 17, paragraphe 3, point c) | La réponse à une contestation reprend les motifs et dit si l'IA de modération est intervenue |

Objectifs internes, non légaux, réglables par l'équipe (configuration privée) : contact 15 jours, usage des fonds et comportement d'un membre de l'équipe 30 jours, « autre » 30 jours ; aucun délai pour un bug ou une suggestion. Ils sont affichés comme des objectifs, jamais comme des délais garantis.

Sources : [RGPD sur EUR-Lex](https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:32016R0679), [DSA sur EUR-Lex](https://eur-lex.europa.eu/legal-content/FR/TXT/HTML/?uri=CELEX:32022R2065), [LCEN sur Légifrance](https://www.legifrance.gouv.fr/loda/id/JORFTEXT000000801164) (article 1-1, III, à relire sur Légifrance avant la mise en production ; la numérotation date de 2024).

## 3. Vocabulaire

- **Type** : `ticket` (S-102), `contact` (S-103), `claim` (réclamation, S-104).
- **Sous-type d'un ticket** : `bug`, `suggestion`, `report` (signalement d'un contenu ou d'un compte : va aussi dans la file de modération). Une faille de sécurité n'ouvre pas de ticket : l'application renvoie vers `/securite` (S-92).
- **Catégorie d'un contact** (déclarative) : `citizen`, `association`, `union`, `journalist`, `elected`, `institution`, `company`, `other`.
- **Motif d'une réclamation** : `moderation_decision`, `data_rights`, `right_of_reply`, `funds`, `team_member`, `other`.
- **Statut** : `received`, `in_progress`, `answered` (réponse envoyée), `awaiting_you` (en attente de votre réponse), `closed`. Un dossier `awaiting_you` sans nouvelle de la personne pendant 60 jours est clos automatiquement (la personne en est prévenue dans la dernière réponse).

## 4. Numéro, code et clés du dossier

- **Numéro** : 10 symboles aléatoires en base 32 de Crockford (50 bits), affichés `PPF-XXXXX-XXXXX`, jamais un compteur. Tiré par l'appareil ; s'il existe déjà, le serveur répond `409` et l'appareil en tire un autre.
- **Code secret** : 20 symboles aléatoires en base 32 de Crockford (100 bits), affichés en cinq groupes de quatre ; saisie sans tenir compte des majuscules, des espaces ni des tirets, avec les équivalences de Crockford (`O` vaut `0`, `I` et `L` valent `1`). Montré **une seule fois** à la création ; gardé sur l'appareil dans « Mes échanges ».
- **Dérivations** (HKDF-SHA-256, sel `ppf-case-v1`, matière : le code normalisé en ASCII) :
  - `info = "access " + numéro` → 32 octets : le **jeton d'accès** ; le serveur n'en garde que le SHA-256 ;
  - `info = "key " + numéro + " " + compteur` (compteur 0, puis 1… si les 32 octets ne sont pas une clé secp256k1 valide) → la **clé secrète du dossier** ; le serveur garde sa clé publique (x seule, hexadécimal).
- La clé du dossier est l'identité de la personne pour ce seul dossier : elle signe le sceau (NIP-59) de ses messages et reçoit les réponses. Elle n'est reliée à aucun compte.
- **Relier son compte** (seulement quand le motif l'exige : contester une décision sur son propre contenu, exercer ses droits sur ses données, droit de réponse) : la personne l'accepte après une mise en garde (principe 10) ; le sceau de ses messages est alors signé par la clé de son compte au lieu de la clé du dossier. Seule l'équipe le voit, après déchiffrement ; le serveur ne le sait pas.

## 5. Messages

- Chaque message est une rumeur NIP-17 (type 14), scellée (type 13, signée par la clé du dossier ou du compte), enveloppée (type 1059, auteur à usage unique, date aléatoire dans les deux jours passés, chiffrement NIP-44), adressée par une seule étiquette `p` à la boîte de l'équipe (message de la personne) ou à la clé du dossier (réponse de l'équipe).
- Contenu de la rumeur : un objet JSON `{"format":"ppf-case","version":1,"case":"PPF-XXXXX-XXXXX","kind":"…","text":"…"}` avec, selon `kind` :
  - `opening` : `type`, `subtype` ou `category` ou `motif`, `subject` (200 caractères au plus), `text` (20 000 au plus), et facultatifs : `technical` (rapport technique joint avec l'accord de la personne, 16 000 caractères au plus, S-101), `content` (adresse stable du contenu concerné), `decision` (identifiant de la décision de modération contestée et sa date), `declared_identity` (pour un droit de réponse ou des droits sur les données, ce que la personne veut bien dire) ;
  - `message` : un message de suivi de la personne ;
  - `reply` : une réponse de l'équipe, signée « l'équipe PPF » ; `motives` et `automated` (booléen) pour une contestation (DSA, article 17) ;
  - `status` : un changement de statut expliqué à la personne (prolongation motivée, clôture).
- Un lecteur refuse une version qu'il ne connaît pas. Les réponses sont non modifiables : une correction est une nouvelle réponse.

## 6. Serveur (étape 1)

### Données (migration serveur : le prochain numéro libre au moment de l'entrée dans main, V12 aujourd'hui)

- `contact_case` : `number` (clé), `type`, `subtype`, `category`, `motif`, `status`, `priority` (`normal`, `high`), `assigned_to` (compte de l'équipe, facultatif), `received_at`, `deadline_at`, `deadline_kind` (`legal` ou `goal`), `extended_at`, `extension_reason`, `decision_at` (date de la décision contestée), `decision_author` (compte de l'équipe auteur de la décision contestée, rempli par l'équipe), `concerns` (compte de l'équipe mis en cause par un dossier `team_member`, rempli par l'équipe), `closed_at`, `legal_hold_reason`, `legal_hold_review_at`, `anonymized_at`, `access_hash`, `case_pubkey`, `last_activity_at`. Jamais d'adresse IP, d'e-mail, de téléphone, d'identifiant d'appareil ni de compte de la personne.
- `contact_envelope` : `id` (identifiant de l'événement), `case_number`, `direction` (`in` ou `out`), `event` (JSON de l'enveloppe), `received_at`, `team_author` (compte de l'équipe pour `out`, S-108). Une enveloppe n'est jamais modifiée.
- Les actions de l'équipe passent par le journal d'audit du back-office (assignation, priorité, statut, prolongation, archive juridique, ouverture d'un dossier clos), avec le numéro du dossier, jamais le texte.

### API publique (port public)

| Requête | Effet |
|---|---|
| `POST /api/v1/cases` : `{"number","type","subtype"/"category"/"motif","decision_at"?,"access_hash","case_pubkey","envelope"}` | Crée le dossier et garde l'enveloppe ; `201 {"number","status","deadline_at","deadline_kind"}` ; `409 number_taken` ; `422` pour une règle (type, motif, réclamation de modération plus de six mois après la décision, enveloppe) ; `429` |
| `GET /api/v1/cases/{number}` avec `Authorization: Bearer <jeton d'accès en base64url>` | Statut, échéance et enveloppes `out` depuis `?after=<id>` ; `404` pour un numéro inconnu **ou** un jeton faux (même réponse, même durée) |
| `POST /api/v1/cases/{number}/messages` avec le jeton | Ajoute une enveloppe `in` (dossier non clos) ; repasse le statut à `in_progress` |

Contrôles d'une enveloppe : type 1059, identifiant et signature valides, une seule étiquette `p` égale à la boîte de l'équipe de l'environnement (`PPF_CONTACT_INBOX_KEY`), `created_at` entre 3 jours dans le passé et 10 minutes dans le futur, 96 Kio au plus, preuve de travail NIP-13 d'au moins `PPF_CASE_POW_BITS` bits (16 par défaut, réglage privé) pour la création. Plafonds globaux (comme les témoignages d'« Agir ») : 30 créations et 120 messages par minute, sans aucune clé liée à la personne. Le jeton d'accès est comparé en temps constant ; 5 essais faux pour un même numéro bloquent ce numéro 15 minutes.

### Back-office (section `contact`, port de l'équipe)

- Boîte de réception (S-107) : dossiers triés par échéance, puis par réception ; filtres par type, motif ou catégorie, statut, assignation, priorité ; marques « échéance dans 2 jours », « dépassée ». Aucune lecture de texte ici.
- Actions : s'assigner ou assigner, priorité, statut, prolongation d'un dossier `data_rights` (motif obligatoire, possible seulement dans le premier mois), auteur de la décision contestée (pour la règle de l'autre personne), archive juridique (motif et date de réexamen, au plus un an ; validation à deux), clôture.
- Permissions nouvelles : `contact.triage` (modérateurs, administrateurs), `contact.data_rights` (administrateurs), `contact.legal_hold` (administrateurs, à deux), `contact.restricted` (administrateurs ; ajoutée à l'étape 1, section 11) ; `appeals.decide` existe déjà pour les contestations, `rightofreply.decide` pour les droits de réponse ; `funds` et `team_member` sont réservés aux administrateurs, et un membre ne voit jamais un dossier `team_member` qui lui est assigné comme objet (champ `concerns`, rempli par l'équipe).

### Durées (point d'attention n° 20, S-109), appliquées chaque nuit

- Ticket et contact : **effacés** (dossier et enveloppes) **3 ans après la clôture**.
- Réclamation : **anonymisée 5 ans après la clôture** : enveloppes, `access_hash` et `case_pubkey` effacés ; restent le type, le motif, les dates et les durées (statistiques S-111, bilan annuel S-110) ; jamais supprimée.
- Archive juridique : rien n'est effacé tant qu'elle est active ; à la date de réexamen, le dossier revient dans la boîte avec une alerte ; sans renouvellement motivé, les durées ordinaires reprennent.
- Ouvrir les enveloppes d'un dossier clos (client de l'équipe) inscrit une ligne au journal d'audit.

## 7. Application (étape 2)

- Trois formulaires dans « Mon espace », « Écrire à l'équipe » : ticket, contact, réclamation (délai applicable affiché avant l'envoi : « réponse au plus tard le … », ou « objectif : … »). Mise en garde avant de relier son compte.
- « Mes échanges » (S-105) : liste des dossiers gardés sur l'appareil (numéro, code, type, objet, dernier statut, dernières réponses déchiffrées), dans la base locale (prochain numéro de migration libre) ; « Ajouter un dossier » avec numéro et code ; vérification à l'ouverture et à chaque synchronisation.
- Messages en attente d'envoi si le réseau manque (offline-first), preuve de travail calculée hors du fil de l'interface.

## 8. Client de l'équipe (étape 3) et site sans JavaScript (étape 4)

- Client de l'équipe : mode de l'application de bureau ouvert avec la clé de la boîte ; lit les enveloppes par une API du port de l'équipe (NIP-98 signé par la clé Nostr reliée au compte du membre), répond (réponse attribuée au membre dans `contact_envelope.team_author`), notes internes chiffrées pour la boîte, réponses types ; partagé avec les droits de réponse de « Voir ».
- Site : formulaire sans JavaScript ; le serveur chiffre le texte dès la réception pour la boîte de l'équipe, avec la clé du dossier tirée du code qu'il vient de créer, puis oublie le texte et le code ; pour lire les réponses, la personne tape son numéro et son code, le serveur déchiffre le temps d'afficher la page et oublie. Le serveur voit donc le texte au passage : c'est dit sur la page, avec le conseil d'utiliser l'application pour un sujet sensible.

## 9. Menaces et limites

- Un serveur compromis ne lit aucun texte envoyé par l'application ; il pourrait cacher ou retarder des enveloppes (visible pour la personne, qui voit son numéro sans réponse) ou fausser les métadonnées. Le texte envoyé par le site passe en clair dans la mémoire du serveur.
- La clé de la boîte de l'équipe est un secret partagé par les membres qui traitent les dossiers ; sa perte rend illisibles les dossiers ouverts. Elle est **créée avant la préproduction publique**, gardée hors du serveur, avec une copie de secours sur papier chez la propriétaire ; une nouvelle clé s'annonce dans l'application, les anciens dossiers restant lisibles avec l'ancienne.
- La clé publique de la boîte est intégrée à l'application pour chaque environnement (comme `OpenDataKeys`), jamais reçue du serveur : un serveur compromis ne peut pas la remplacer. Plus tard, certificat par la racine de la plateforme.
- Le numéro et le code donnent accès au dossier : perdre le code, c'est perdre l'accès (l'équipe ne peut pas le retrouver). Le dire clairement à la création.
- Les métadonnées disent qu'un dossier d'un certain type existe, avec ses dates : c'est le minimum du suivi et des délais légaux.

## 10. Étapes

1. Protocole dans le code commun (numéro, code, dérivations, format `ppf-case`, contrôles d'enveloppe), tables, API publique, section du back-office, durées, tests.
2. Formulaires de l'application et « Mes échanges ».
3. Client de l'équipe, mis en commun avec « Voir ».
4. Formulaire du site sans JavaScript, puis notifications sans contenu (M31).

## 11. Étape 1 : ce qui est codé et les écarts (8 novembre 2026)

Codé dans la branche `feature/contact` : code commun `shared/core/.../core/contact/` (numéro, code, dérivations, format `ppf-case`, échéances, preuve de travail, enveloppes, JSON de l'API, boîte d'exemple), serveur `server/.../server/contact/`, migration `V12__contact_cases.sql`, permissions, section « Échanges », durées. Formats détaillés : `docs/FORMATS.md`, section 4 undecies. Précisions et écarts, chacun avec sa raison :

1. **Permission `contact.restricted`** (administrateurs) : c'est elle qui ouvre les réclamations `funds` et `team_member`, « réservées aux administrateurs » ; le socle donne les droits par permission, pas par rôle.
2. **Archive juridique à deux, tenue par la section** (table `contact_legal_hold_request`) au lieu des demandes à deux du socle (`PendingApprovals`) : celles-ci ne servent qu'aux files, dont l'affichage ne connaît pas le membre qui regarde (impossible d'y cacher un dossier `team_member` au membre mis en cause), et une action de file ne porte qu'un motif, pas de date de réexamen. Les règles du socle sont reprises : deuxième personne différente du demandeur, avec la même permission, clé d'accès vérifiée de nouveau pour les deux, demande périmée après 7 jours, une ligne du journal à chaque étape ; la levée de l'archive suit le même chemin. À reprendre le jour où le socle offrira la validation à deux aux sections.
3. **Réexamen** : à la date de réexamen, une alerte du tableau de bord (« N archives juridiques … à réexaminer », sans numéro) et le dossier revient dans la boîte ; l'archive protège encore **30 jours** pour laisser le temps de la renouveler, puis elle est levée et les durées ordinaires reprennent. Colonne ajoutée : `legal_hold_alerted_at` (une seule alerte par archive).
4. **Ordre des enveloppes** : colonne `seq` de `contact_envelope` (ordre de réception), pour que `?after=<id>` ne saute jamais une réponse reçue dans la même seconde ; 20 enveloppes au plus par réponse, avec `more`.
5. **Même création envoyée de nouveau** (même numéro, même empreinte, même enveloppe, par exemple après une coupure du réseau) : `200` avec le dossier, et non `409`, pour que l'application hors ligne ne crée pas de doublon.
6. **Codes de l'API** : `422` pour toute règle (dont les contrôles de l'enveloppe, avec leur code), `400 malformed` pour un corps illisible, `413` au-delà de 128 Kio ; `429 locked` (avec `Retry-After`) après 5 essais faux pour un numéro **qu'il existe ou non**, pour que le blocage ne révèle pas l'existence d'un dossier.
7. **Message de suivi** : un dossier `answered` ou `awaiting_you` repasse `in_progress` ; un dossier `received` reste `received` (personne ne l'a encore pris en charge).
8. **Échéances** : calculées en heure de Paris, à la même heure que la réception (la lecture la plus stricte), le jour borné à la fin d'un mois plus court ; la prolongation donne trois mois après la réception. Le signalement (`report`) n'a pas d'objectif par défaut (le contrat n'en donne pas), réglable comme les autres.
9. **Droits sur les données** : le statut, la clôture et la prolongation d'un dossier `data_rights` demandent `contact.data_rights` ; un modérateur le voit dans la boîte et peut l'assigner à qui détient cette permission.
10. **Anonymisation d'une réclamation** : effacés aussi les membres nommés (assigné, auteur de la décision, membre mis en cause) et le motif de prolongation (un texte de l'équipe) ; restent la nature, le statut et les dates.
11. **Journal** : chaque action de la section inscrit le numéro, le type, la nature et des codes ; le membre mis en cause n'y est jamais nommé (`concerns` : `set` ou `cleared`), car les administrateurs lisent le journal.
12. **Pas encore** : le signalement (`report`) n'entre pas encore dans la file de modération (socle, étape 2) ; `appeals.decide` et `rightofreply.decide` serviront quand le client de l'équipe répondra (étape 3), tout comme la ligne du journal à l'ouverture des enveloppes d'un dossier clos ; aucune alerte du tableau de bord pour les délais légaux (les marques de la boîte : « échéance dans 2 jours », « échéance dans 7 jours » pour les droits sur les données, « Droit de réponse : 3 jours » dès la réception, « dépassée »).
13. **Développement** : sans `PPF_CONTACT_INBOX_KEY`, le serveur de développement prend la boîte d'exemple (`ContactInbox.EXAMPLE`, tirée d'une phrase publique marquée [EXEMPLE]) et l'annonce en une ligne au démarrage ; en préproduction et en production, sans clé, les routes répondent `503 contact_not_open`, et la boîte d'exemple est refusée en production.
