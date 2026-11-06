# Formats ouverts de la plateforme

Version 1 des formats, 5 novembre 2026. Ce document décrit les formats de données que la plateforme expose ou exporte, pour que n'importe qui puisse lire, vérifier et réutiliser ses contenus (cahier des charges, section 3.2 : identifiants stables et export JSON documenté). Toute évolution incompatible donne une nouvelle version numérotée ; les anciens fichiers restent lisibles.

## 1. Clés et identités

Une identité est une paire de clés secp256k1, la même famille de clés que Nostr.

| Forme | Exemple | Usage |
|---|---|---|
| Clé publique en hexadécimal (64 caractères minuscules) | `3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d` | Dans les événements signés (`pubkey`) |
| Clé publique lisible, bech32 (NIP-19) | `npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6` | Affichée aux utilisateurs, partagée |
| Clé secrète lisible, bech32 (NIP-19) | `nsec1…` | Sauvegarde ou export par l'utilisateur seul ; jamais transmise |

La clé publique est un pseudonyme : elle ne révèle rien de la personne mais relie entre eux les contenus d'un même compte.

### Phrase de récupération

Douze mots d'une liste officielle BIP-39 (française par défaut, anglaise acceptée). La clé est dérivée selon NIP-06 : graine BIP-39 sans mot de passe, puis chemin BIP-32 `m/44'/1237'/0'/0/0`. Exemple officiel (liste anglaise) : la phrase `leader monkey parrot ring guide accident before fence cannon height naive bean` donne la clé publique `npub1zutzeysacnf9rru6zqwmxd54mud0k44tst6l70ja5mhv8jjumytsd2x7nu`.

## 2. Événement signé (format d'export des contenus)

Chaque contenu publié est un événement au format Nostr NIP-01, en JSON :

```json
{
  "id": "dc90c95f09947507c1044e8f48bcf6350aa6bff1507dd4acfc755b9239b5c962",
  "pubkey": "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d",
  "created_at": 1644271588,
  "kind": 1,
  "tags": [],
  "content": "[EXEMPLE] Texte du contenu",
  "sig": "230e9d8f…"
}
```

| Champ | Contenu |
|---|---|
| `id` | SHA-256, en hexadécimal minuscule, de la sérialisation canonique ci-dessous. C'est l'identifiant de cette version exacte du contenu. |
| `pubkey` | Clé publique de l'auteur, hexadécimal minuscule. |
| `created_at` | Date de création, en secondes depuis le 1er janvier 1970 (UTC). |
| `kind` | Type de l'événement, entier de 0 à 65535 (section 3). |
| `tags` | Liste de listes de chaînes. Le premier élément d'une étiquette est son nom. |
| `content` | Le contenu lui-même (texte, ou JSON selon le type). |
| `sig` | Signature Schnorr BIP-340 de `id` par la clé de l'auteur, 128 caractères hexadécimaux. |

### Sérialisation canonique (pour calculer `id`)

Le tableau JSON `[0,"pubkey",created_at,kind,tags,"content"]` sans aucun espace ni retour à la ligne, en UTF-8. Dans les chaînes, seuls sept caractères sont échappés : `"` en `\"`, `\` en `\\`, retour à la ligne en `\n`, retour chariot en `\r`, tabulation en `\t`, retour arrière en `\b`, saut de page en `\f`. Tout autre caractère, accents compris, est écrit tel quel. Un événement est **authentique** si `id` correspond à cette sérialisation et si `sig` est une signature valide de `id` par `pubkey`.

### Pourquoi ce format

Il est déjà lu par des dizaines d'applications (Nostr), il est signé par construction et il permettra en phase 3 de relayer les contenus sur Nostr et sur des miroirs statiques sans les re-signer.

## 3. Types d'événements (`kind`)

Le numéro détermine le comportement :

| Plage | Comportement |
|---|---|
| 1, 2, 4 à 44, 1000 à 9999 | Réguliers : conservés tels quels. |
| 0, 3, 10000 à 19999 | Remplaçables : le plus récent du même auteur et du même type remplace le précédent. |
| 20000 à 29999 | Éphémères : non conservés. |
| 30000 à 39999 | Adressables : remplaçables par identifiant (`d`), voir section 4. |

Types standards utilisés : `0` (profil), `1` (note courte), `5` (demande de suppression), `30023` (article long). Les types propres aux contenus PPF (fiche pratique, événement de mobilisation, revendication, etc.) seront attribués dans la plage adressable et ajoutés ici au fur et à mesure.

## 4. Identifiant stable d'un contenu modifiable

Un contenu adressable porte une étiquette `["d", "identifiant"]`. Son adresse stable est `kind:pubkey:identifiant`, par exemple `30023:3bf0c63f…459d:fiche-droits-manifestation`. Elle ne change pas quand le contenu est corrigé, alors que `id` change à chaque version. L'identifiant peut contenir des deux-points : l'adresse se lit en coupant aux deux premiers.

## 4 bis. Contenus éditoriaux (fiches pratiques, dossiers, décryptages, articles)

Un contenu éditorial est un événement de type `30023` (NIP-23). Son `content` est le texte en Markdown (titres `##`, paragraphes, listes `-`, gras `**`, liens `[texte](adresse)`). Ses étiquettes :

| Étiquette | Contenu | Obligatoire |
|---|---|---|
| `d` | Identifiant stable du contenu (par exemple `garde-a-vue`), jamais modifié | Oui |
| `title` | Titre | Oui |
| `summary` | Résumé | Non |
| `published_at` | Première publication, secondes depuis 1970, en texte | Non |
| `t` | Un thème par étiquette (`droits`, `manifestation`, …) | Non |
| `ppf-type` | `fiche-pratique`, `dossier`, `decryptage`, `article` (défaut) ou `revendication` | Non |
| `ppf-status` | Revendications seulement : `nouvelle`, `en-debat`, `tres-soutenue`, `retenue` ou `campagne` | Non |
| `ppf-territory` | Revendications seulement : le territoire concerné (« France », un département, une métropole) | Non |
| `ppf-origin` | `human` (rédigé par un humain) ou `ai-assisted` (produit avec l'aide d'une IA) | Oui |

La date de dernière mise à jour est `created_at`. Une nouvelle version porte le même `d` et une date plus récente : elle remplace la précédente partout (section 4). L'application n'affiche que les contenus dont l'auteur figure dans sa liste d'auteurs de confiance.

Dans le Markdown, un bloc ouvert par une ligne `::: en-bref`, `::: legal`, `::: illegal`, `::: attention` ou `::: conseil` et fermé par une ligne `:::` est un **encart** (résumé, ce qui est légal, ce qui est illégal, avertissement, conseils). Les lecteurs qui ne connaissent pas cette convention voient simplement le texte du bloc.

### Publications courtes du fil

Une publication du fil est un événement de type `1` : `content` est le texte brut, les étiquettes `t` portent les thèmes. Pas de HTML, pas de Markdown. Un contenu qui commence par `[EXEMPLE]` est un contenu de démonstration : l'application affiche ce marqueur sous forme de badge. La publication la plus récente portant le thème `en-direct` alimente le bandeau « EN DIRECT » du fil.

## 4 ter. Messagerie : enveloppes NIP-59, messages NIP-17

Les conversations privées (deux personnes ou petit groupe) suivent les NIP-17, NIP-59 et NIP-44 de Nostr (décision D-027). Le serveur ne voit jamais ni l'auteur réel, ni les destinataires au-delà de l'étiquette de livraison, ni la date réelle, ni le contenu.

**Message (rumeur, kind 14)** : un événement NIP-01 **non signé**, avec son `id` mais sans `sig`. `content` est le texte brut du message (ni Markdown ni HTML, 16 Kio au plus). Étiquettes : une `["p", "<clé>"]` par destinataire (l'auteur n'y figure pas), `["e", "<id>"]` facultative pour une réponse, `["subject", "<sujet>"]` facultative. La conversation est l'ensemble des participants (auteur et destinataires) ; son identifiant local est leurs clés triées et jointes par des virgules. `created_at` est la vraie date du message. Une rumeur ne circule jamais seule.

**Sceau (kind 13)** : `content` = chiffrement NIP-44 (version 2) du JSON de la rumeur avec la clé de conversation de l'auteur et du destinataire ; signé par l'auteur ; aucune étiquette ; `created_at` tiré au hasard jusqu'à deux jours dans le passé. Le lecteur vérifie que la clé du sceau est celle de la rumeur, sinon n'importe qui pourrait écrire au nom de n'importe qui.

**Enveloppe (kind 1059)** : `content` = chiffrement NIP-44 du JSON du sceau avec une clé jetable, générée pour cette seule enveloppe puis détruite ; signée par la clé jetable ; une seule étiquette `["p", "<destinataire>"]` ; `created_at` aléatoire aussi. Il y a une enveloppe par participant, l'expéditeur compris (pour ses autres appareils). C'est le seul objet que le serveur reçoit et conserve.

**Chiffrement NIP-44 v2** : clé de conversation = HKDF-extract SHA-256 (sel `nip44-v2`) de la coordonnée x du point ECDH secp256k1 ; clés de message = HKDF-expand(clé de conversation, info = nonce de 32 octets, 76 octets) découpées en clé ChaCha20 (32), nonce ChaCha20 (12) et clé HMAC (32) ; texte rembourré à une taille qui ne révèle que l'ordre de grandeur ; charge utile = base64(0x02 ‖ nonce ‖ chiffré ‖ HMAC-SHA-256(nonce ‖ chiffré)). La plateforme accepte des textes de 1 à 65 535 octets. Le NIP-44 ne fournit ni confidentialité persistante ni déni : la compromission d'une clé expose les anciennes conversations ; c'est la limite connue acceptée par D-027 en attendant MLS.

**Relevé** : l'appareil demande ses enveloppes par `GET /api/v1/events?kind=1059&tag=p:<sa clé>&since=<dernier relevé moins deux jours>` (section 5), par pages, et ne garde que celles qu'il sait ouvrir. Les marques de lecture ne quittent jamais l'appareil.

**Réglage de la boîte** : chaque utilisateur choisit combien de temps le serveur garde les enveloppes qui lui sont adressées, par un événement NIP-78 signé de sa clé : type 30078, `["d","ppf:mailbox"]`, `["retention_days","<jours>"]` (1 à 9 chiffres). Le serveur borne la valeur entre `PPF_WRAP_RETENTION_DAYS` (90 jours, aussi la valeur par défaut) et `PPF_WRAP_RETENTION_MAX_DAYS` (365 jours) ; sans réglage lisible, la valeur par défaut s'applique. Événement adressable : une nouvelle version remplace la précédente. Il est public, comme tout événement du serveur.

## 4 quater. Revendications proposées par les citoyens, rapports des vérifications automatiques

Une revendication proposée par un citoyen est un contenu éditorial de type `30023` (section 4 bis) écrit par le formulaire guidé de l'application (`RevendicationDraft.toUnsignedEvent`) : `d` = premiers mots du titre repliés et quatre caractères hexadécimaux aléatoires (`medecin-garde-soir-3f9a`), `title` = la revendication en une ou deux phrases (280 caractères au plus), `summary` facultatif (300 au plus), `published_at` = `created_at`, `ppf-type` = `revendication`, `ppf-origin` = `human`, `ppf-status` = `nouvelle` (seule l'équipe fait avancer une revendication), `ppf-territory` (« France » par défaut), de un à trois `t` pris parmi les identifiants de thème ci-dessous. Le `content` enchaîne, dans cet ordre et seulement s'ils sont remplis, les intertitres `## Pourquoi`, `## Ce que la revendication demande` et `## Ce qui existe déjà` (4000 caractères au plus chacun).

**Thèmes** (identifiants stables, `RevendicationThemes`) : `societe`, `sante`, `education`, `travail`, `pouvoir-d-achat`, `logement`, `transports`, `environnement`, `services-publics`, `democratie`, `justice`, `fiscalite`, `agriculture`, `numerique`, `culture`. Des alias relient les anciens sujets à un thème (`ecole` et `cantine` à `education`, `medical` et `hopital` à `sante`, `transparence` et `institutions` à `democratie`...). Liste à valider par la propriétaire.

**Rapport des vérifications** (`ProposalReport`, JSON) : `{"issues":[…],"checks":[…],"checked_at":<secondes>}`. Un constat (`ProposalIssue`) : `kind` parmi `duplicate`, `similar`, `already_advanced`, `existing_law`, `pending_bill`, `contradiction`, `several_ideas`, `too_short`, `too_long`, `all_caps`, `missing_theme`, `too_many_themes`, `missing_territory`, `personal_data`, `targets_person`, `charter`, `out_of_scope`, `unclear` ; `severity` parmi `blocking` (règles du formulaire seulement), `warning`, `info` ; `source` parmi `rules`, `similarity`, `legal`, `ai` ; `field` (partie du formulaire en cause) ; `related` (revendications existantes : `address`, `title`, `score` de 0 à 1, `status`, `territory`) ; `references` (textes juridiques : `title`, `nature`, `status`, `identifier`, `url` limitée à Légifrance, Assemblée nationale, Sénat et EUR-Lex, `excerpt`, `verified` vrai seulement pour un texte venu du corpus officiel interrogé par le serveur) ; `detail` (explication du modèle d'IA, 400 caractères au plus, jamais remplie par les règles) ; `alert` (`red` ou `orange`, pour la charte, une personne visée ou des données personnelles relevées par l'IA). Une vérification (`CheckStatus`) : `kind` parmi `rules`, `similarity`, `legal`, `ai` ; `state` parmi `done`, `after_sending` (sur l'appareil : elle tournera sur le serveur), `unavailable` (pas encore en service), `failed` ; `compared` (nombre de revendications comparées) ; `model` (nom du modèle d'IA qui a répondu).

**Vues du mur, sur l'appareil seulement** : table locale `revendication_view(id, position, name, query)` ; une vue intégrée n'a ni nom ni requête (`tout`, `suivis`, `mes-revendications`, `nationales`, `theme:<id>`), une vue personnalisée (`custom:` et 16 caractères hexadécimaux) a un nom et une requête JSON `{"themes":[…],"territories":[…],"statuses":[…],"authors":"anyone"|"followed"|"mine","text":"…","sort":"newest"|"popular"|"updated"}` (valeurs par défaut omises, champs inconnus ignorés). Rien de tout cela n'est jamais envoyé au serveur.

## 4 quinquies. Espace « Voir » : briques sourcées (D-033)

Chaque brique est un contenu éditorial NIP-23 (type `30023`) : étiquettes communes de la section 4 bis (`d`, `title`, `summary`, `published_at`, `t`, `ppf-origin`), corps en Markdown lisible partout, `ppf-type` pour dire ce qu'elle est, puis des étiquettes structurées. Les étiquettes à plusieurs champs sont positionnelles ; un champ vide veut dire « inconnu ». Les dates sont au format `AAAA-MM-JJ`. Seules les briques signées par une clé de l'équipe éditoriale sont affichées ; une brique mal formée est ignorée.

Étiquettes communes à plusieurs briques :

| Étiquette | Champs |
|---|---|
| `ppf-source` | type de source, nom, adresse web, date |
| `ppf-archive` | empreinte SHA-256 de l'original, preuve d'horodatage (`opentimestamps`, `rfc3161`), adresse de la copie archivée, date |
| `ppf-correction` | date, motif (journal public des corrections) |
| `ppf-next` | libellé, cible : adresse d'un contenu `30023:<clé>:<identifiant>` ou `space:<espace>` (« Et maintenant ? ») |
| `a` | `30023:<clé>:<identifiant>`, champ vide, rôle : `personne`, `texte`, `indicateur` ou `cible` (contenu auquel répond un droit de réponse) |
| `ppf-axis` | `mouvement`, `pouvoir` ou `libertes` |

Types de source : `autorites`, `organisateurs`, `comptage-independant`, `medias`, `signalements`, `texte-officiel`, `justice`, `parlement`, `rapport`, `declaration`, `enquete-presse`. Niveaux de fiabilité : `elevee`, `moyenne`, `faible`.

| `ppf-type` | Étiquettes propres |
|---|---|
| `indicateur` | `ppf-unit` (obligatoire), `ppf-rank` (ordre parmi les chiffres clés), `ppf-declarative` (`true` pour un indicateur déclaratif), `ppf-breakdown` (libellé de la part, par exemple « dont mineurs »), `ppf-accepted-source` (une par type retenu), `ppf-measure` : identifiant, date, valeur, type de source, nom de la source, adresse, fiabilité, occasion (« Journée du 18 septembre »), lieu, part. Le corps décrit la méthode. |
| `fait` | `ppf-fact` (obligatoire) : `mobilisation`, `declaration-officielle`, `decision-officielle`, `reponse-gouvernement` (chronologie), `vote`, `promesse`, `discours-vote`, `affaire`, `declaration-contredite`, `argent-public`, `interets`, `influence`, `liberte` ; `ppf-date` (obligatoire), `ppf-place`, `ppf-fact-status` (obligatoire) : `etabli`, `allegue`, `enquete` ; `ppf-procedure` (obligatoire pour une affaire) : `enquete`, `mise-en-examen`, `proces`, `condamnation-premiere-instance`, `appel`, `condamnation-definitive`, `relaxe`, `non-lieu`, `classement-sans-suite` ; `ppf-sentence` et `ppf-sentence-served` (peine prononcée, peine exécutée) ; `ppf-promise` : `tenue`, `en-cours`, `abandonnee`, `contraire` ; `ppf-vote` : `pour`, `contre`, `abstention`, `non-votant`, `absent` ; `ppf-quote` (propos exacts) ; `ppf-liberty` : `interdiction-manifester`, `dissolution`, `loi-restrictive`, `surveillance`, `autre` ; `ppf-recourse` : `aucun`, `en-cours`, `suspendu`, `annule`, `rejete`. Tout fait qui porte `ppf-liberty` figure dans l'observatoire des libertés, quel que soit son type. |
| `personne` | `ppf-role` (une par fonction) : `depute`, `senateur`, `eurodepute`, `ministre`, `president`, `maire`, `elu-local`, `autre` ; `ppf-group` ; `ppf-territory` ; `ppf-mandate` : intitulé, territoire, début, fin (vide si en cours). |
| `texte` | `ppf-text-kind` (`projet` ou `proposition`), `ppf-stage` (obligatoire) : `depot`, `commission`, `assemblee`, `senat`, `cmp`, `lecture-definitive`, `adopte`, `conseil-constitutionnel`, `promulgue`, `rejete`, `retire` ; `ppf-stage-history` : étape, date ; `ppf-next-vote` : date, libellé ; `ppf-493` : date (une par recours à l'article 49.3) ; `ppf-impact` : catégorie de population, `gagne` / `perd` / `mitige`, effet, nom de la source, adresse. |
| `mouvement` | `ppf-country` (obligatoire), `ppf-period` : début, fin ; `ppf-claim` (une par revendication), `ppf-result` (une par résultat obtenu). |
| `dossier` | `ppf-axis` (un dossier sans axe n'est pas affiché dans « Voir »). |
| `reponse` | `ppf-respondent` (nom de la personne qui répond, obligatoire) et un lien `a` de rôle `cible`. Le corps est la réponse. |

## 5. API du serveur (version 1)

Toutes les réponses sont en JSON. Aucune information sur l'expéditeur n'est journalisée.

| Requête | Rôle | Réponses |
|---|---|---|
| `POST /api/v1/events`, corps : un événement signé (section 2), 64 Ko au plus, 1000 étiquettes au plus | Publier un contenu. La signature est vérifiée avant toute chose ; les contenus éditoriaux (type 30023) et les descriptions de salon (types 39000 à 39005, avec exactement un identifiant `d`) ne sont acceptés que des clés de l'équipe éditoriale ; les enveloppes chiffrées (type 1059) sont acceptées de toute clé, nomment exactement un destinataire (`p`) et sont stockées sans être ouvertes ; un message de salon (type 9) porte exactement une étiquette `h` et n'est accepté que si le salon existe et est ouvert ; les listes de membres (39002), les événements d'adhésion ou de modération (9000 à 9022) et les drapeaux `private` et `hidden` sont refusés. Limitation de débit par clé publique (60 par minute) et globale (600 par minute), jamais par adresse IP. | `201` `{"id":"…","status":"stored"}` ; `200` avec `"duplicate"` (déjà connu) ou `"superseded"` (une version plus récente existe) ; `400` `{"error":"invalid_signature"\|"malformed_event"\|"ephemeral_kind"\|"bad_recipient"\|"missing_group"\|"multiple_groups"\|"too_many_tags"\|"unsupported_kind"\|"unsupported_group_flag", "message":"…"}` ; `403` `not_allowed`, `group_closed` ou `members_list_refused` ; `404` `group_not_found` ; `409` `group_taken` ; `413` trop volumineux ; `429` `rate_limited` avec un en-tête `Retry-After` |
| `POST /api/v1/events`, revendication d'un compte citoyen | Une revendication (type 30023, `ppf-type` = `revendication`) signée par une clé hors de l'équipe éditoriale n'est pas publiée : elle attend la relecture d'une personne. Sa forme doit être exactement celle du formulaire (étiquettes `d`, `title`, `summary`, `published_at`, `ppf-type`, `ppf-origin`, `ppf-status`, `ppf-territory`, `t` ; chacune avec un nom et une valeur ; une seule de chaque sauf `t`, de un à trois thèmes connus et distincts ; `d` en lettres, chiffres et tirets ; aucun caractère de contrôle ; statut `nouvelle`), et elle doit respecter les règles du formulaire. Une nouvelle version (même clé, même `d`) remplace celle qui attend. | `202 {"id":"…","status":"pending_review"}` (aussi pour un renvoi du même événement en attente) ; `200 {"status":"duplicate"}` (déjà publié) ; `200 {"status":"superseded"}` (une version plus récente attend ou est publiée) ; `400 malformed_demand` (forme) ; `400 status_not_allowed` (statut autre que `nouvelle`) ; `400 draft_rules` (règles du formulaire ; le message nomme seulement les règles et les champs, par exemple `too_short (title)`, `personal_data (why)`, `personal_data (identifier)`, jamais ce qui a été trouvé) ; `409 rejected` (cette version a été refusée par l'équipe : en envoyer une corrigée) ; `429 too_many_pending` (10 en attente pour cette clé), `429 queue_full` (1000 en attente sur le serveur), tous deux avec `Retry-After: 3600` ; `429 daily_limit` (5 envois par 24 heures pour cette clé) avec `Retry-After` en secondes |
| `POST /api/v1/events` (partie salons) | Une description de salon (type 39000) est acceptée de toute clé (D-028) si elle porte exactement un `d` (1 à 64 caractères parmi `a-z`, `0-9`, `.`, `_`, `~`, `-`), un `name` contenant au moins une lettre ou un chiffre, un `ppf-territory` de même, `ppf-charter` = `1.0` (première valeur de chaque étiquette), aucun drapeau `private` ni `hidden`, au plus 8 Kio sérialisée, nom et territoire de 100 caractères au plus ; `about` et `picture` sont facultatifs. Un identifiant appartient à la première clé qui l'a décrit ; une nouvelle version par la même clé remplace la description. Un salon ne peut pas porter le même nom qu'un autre sur le même territoire (comparaison sans accents, majuscules ni ponctuation). Les compagnons (39001, 39003, 39004, 39005) ne sont acceptés que des clés éditoriales, pour un salon qu'elles gèrent. Les listes de membres (39002) et les événements d'adhésion ou de modération (9000 à 9022) sont refusés. Un événement envoyé à un salon porte exactement une étiquette `h` désignant un salon existant et ouvert ; un message de salon (type 9) doit en porter une. | `400` `bad_group_id` \| `unsupported_group_flag` \| `missing_name` \| `missing_territory` \| `missing_charter` \| `description_too_long` \| `missing_group` \| `multiple_groups` \| `unsupported_kind` ; `403` `not_allowed` (compagnon publié par une autre clé) \| `members_list_refused` \| `group_closed` ; `404` `group_not_found` ; `409` `{"error":"group_exists","message":"…","existing":"<identifiant du salon à rejoindre>"}` ; `409` `{"error":"group_taken","message":"…"}` (identifiant déjà décrit par une autre clé) |
| `GET /api/v1/revendications/proposals?id=<hex>&id=<hex>` | Où en sont les propositions de l'auteur : de 1 à 20 identifiants d'événements (64 caractères hexadécimaux minuscules, `id` répété). Réponse : liste au format `ProposalState` du cœur, dans l'ordre demandé, `[{"id":"…","status":"pending","received_at":1793966400,"decided_at":null,"reason":null,"report":{…}}]`. `status` : `pending`, `approved` ou `rejected` ; dates en secondes depuis 1970 ; `reason` : motif de l'équipe pour un refus ; `report` : le `ProposalReport` des vérifications automatiques (`null` tant qu'elles n'ont pas tourné). Le rapport ne nomme jamais une revendication non publiée et montre les revendications publiées telles qu'elles se lisent aujourd'hui. Une proposition approuvée ne sert plus de rapport (son identifiant est devenu public). Une revendication publiée sans proposition (supprimée 30 jours après l'approbation, ou publiée directement par l'équipe) répond `approved`, `received_at` étant sa date d'arrivée dans la table des événements, les autres champs à `null`. Un identifiant inconnu est simplement absent. Jamais le contenu de l'événement ni la clé de son auteur ; en-tête `Cache-Control: no-store`. Pas d'authentification : tant qu'une proposition attend, seul son auteur connaît l'identifiant de son événement. | `200` liste ; `400 bad_parameter` (aucun identifiant, plus de 20, ou mal formé) ; `503 database_unavailable` |
| `GET /api/v1/events?kind=1&kind=30023&author=<hex>&identifier=<d>&tag=<nom>:<valeur>&since=<s>&until=<s>&limit=50` | Lister les événements, du plus récent au plus ancien ; tous les paramètres sont facultatifs et répétables sauf `since`, `until`, `limit` (1 à 500). `tag` filtre par étiquette (20 filtres au plus, `identifier` compris, qui équivaut à `tag=d:…`) : le premier deux-points sépare le nom de la valeur, qui peut elle-même en contenir ; plusieurs `tag` du même nom : l'un ou l'autre ; de noms différents : tous ; nom de 1 à 64 caractères, valeur de 1024 octets au plus. Exemples : boîte de réception `kind=1059&tag=p:<hex>`, messages d'un salon `kind=9&tag=h:<id>` | `200` tableau d'événements ; `400` `bad_parameter` |
| `GET /api/v1/events/<id>` | Un événement par son identifiant | `200` l'événement ; `404` `not_found` |
| `GET /api/v1/groups?q=<texte>&territory=<texte>&limit=100` | Les salons connus du serveur (NIP-29), triés par nom (sans accents ni majuscules) puis identifiant : `[{"id":"…","name":"…","about":"…","picture":null,"territory":"Lyon","charter":"1.0","public":true,"open":true,"event":{…événement 39000 signé…}}]`. Tous les paramètres sont facultatifs, chacun au plus une fois. `q` : garde les salons dont le nom, le territoire ou la description contient le texte ; `territory` : garde les salons de ce territoire, comparé en entier ; les deux sont comparés une fois normalisés (accents, majuscules et ponctuation ignorés ; une valeur vide ou sans lettre ni chiffre ne filtre pas), 200 caractères au plus. `limit` : de 1 à 500, 100 par défaut. `territory` vaut l'étiquette `ppf-territory` telle qu'écrite par le créateur ; `charter` la version de la charte acceptée (`null` pour une description antérieure à D-028). `public` : lisible par tous (toujours vrai dans cette version) ; `open` : tout compte peut y écrire. Aucune liste de membres n'existe ni n'est servie. | `200` tableau ; `400` `bad_parameter` (`limit` hors de 1 à 500 ou non entier, paramètre répété, texte trop long) |
| `GET /api/v1/groups/<id>` | Un salon par son identifiant (1 à 64 caractères parmi `a-z`, `0-9`, `.`, `_`, `~`, `-`) | `200` le salon ; `400` `bad_parameter` ; `404` `group_not_found` |
| `GET /health` | État du serveur | `200` `{"status":"ok","service":"ppf-server","version":"0.1.0","environment":"…"}` |

Quand la base de données n'est pas configurée, toute adresse sous `/api/v1/` répond `503` `{"error":"database_unavailable"}`.

Les événements remplaçables et adressables (section 3) ne sont conservés qu'en une version : la plus récente, et à date égale celle dont l'identifiant est le plus petit. L'application et le serveur appliquent la même règle.

**Messagerie et salons (D-027, D-028).** Les messages privés transitent sous forme d'enveloppes NIP-59 (type 1059) adressées par une étiquette `p` ; le serveur ne les ouvre jamais, ne tient aucune liste de conversations ni de contacts, garde au plus `PPF_WRAP_MAX_PER_RECIPIENT` enveloppes par destinataire (5000 par défaut, les plus récentes) et les supprime après la durée choisie par le destinataire (réglage de la boîte, section 4 ter), entre 90 et 365 jours par défaut, comptée depuis leur réception, date qu'il connaît seul (`received_at`, car `created_at` est volontairement brouillé par NIP-59). Les salons suivent NIP-29 : description adressable de type 39000 (`d` = identifiant, étiquettes `name`, `about`, `picture`, `ppf-territory`, `ppf-charter`, drapeaux `closed` et `restricted`), messages de type 9 portant l'étiquette `h`. Tout compte peut créer un salon ; un nom ne sert qu'une fois par territoire.

**Revendications citoyennes (relecture avant publication).** Une revendication proposée par un citoyen attend dans une file la décision d'une personne de l'équipe ; elle n'est publiée (copiée dans la table des événements, telle que son auteur l'a signée) qu'après approbation. Le serveur vérifie de nouveau, à la réception, la forme de l'événement et les règles du formulaire, puis lance en arrière-plan les vérifications automatiques (doublons parmi les revendications publiées et en attente, modèle d'IA s'il est configuré), dont le rapport sert à l'équipe et à l'auteur. Ces vérifications ne bloquent ni ne publient jamais rien : seules les règles déterministes du formulaire peuvent refuser un envoi.

Variables d'environnement du serveur pour le relais :

| Variable | Rôle | Défaut |
|---|---|---|
| `PPF_WRAP_RETENTION_DAYS` | Durée minimale et par défaut de conservation des enveloppes chiffrées (type 1059), depuis leur réception ; un utilisateur peut l'allonger pour sa boîte, jamais la raccourcir | 90 |
| `PPF_WRAP_RETENTION_MAX_DAYS` | Durée la plus longue qu'un utilisateur peut demander pour sa boîte ; une valeur inférieure au minimum est relevée au minimum | 365 |
| `PPF_WRAP_CLEANUP_INTERVAL_MINUTES` | Fréquence de la suppression des enveloppes expirées (0 désactive la tâche, pour les tests) | 60 |
| `PPF_WRAP_MAX_PER_RECIPIENT` | Nombre d'enveloppes gardées par destinataire (les plus récentes) : protection contre l'inondation, non limite d'usage | 5000 |
| `PPF_POST_RATE_PER_MINUTE` | Publications autorisées par minute et par clé publique sur `POST /api/v1/events` | 60 |
| `PPF_POST_GLOBAL_RATE_PER_MINUTE` | Publications autorisées par minute pour tout le serveur, toutes clés confondues | 600 |

Variables d'environnement du serveur pour les revendications proposées et le connecteur d'IA :

| Variable | Rôle | Défaut |
|---|---|---|
| `PPF_PROPOSALS_MAX_PENDING_PER_KEY` | Propositions en attente au même moment pour une clé (une nouvelle version ne compte pas deux fois) | 10 |
| `PPF_PROPOSALS_PER_KEY_PER_DAY` | Envois de propositions (nouvelles versions comprises) par clé et par 24 heures | 5 |
| `PPF_PROPOSALS_MAX_PENDING` | Propositions en attente sur tout le serveur | 1000 |
| `PPF_PROPOSALS_RETENTION_DAYS` | Conservation d'une proposition décidée, après la décision | 30 |
| `PPF_PROPOSALS_CLEANUP_INTERVAL_MINUTES` | Fréquence de la suppression des propositions décidées expirées et de la relance des vérifications manquantes (0 désactive la tâche, pour les tests) | 60 |
| `PPF_AI_BASE_URL` | Adresse de l'API du modèle d'IA, la partie avant `/chat/completions` (par exemple `https://ia.example/v1`) ; https obligatoire, sauf `http://localhost` ou `http://127.0.0.1` pour un modèle sur la même machine ; ni nom d'utilisateur, ni mot de passe, ni paramètre dans l'adresse. Sans elle, pas d'IA | aucune (IA éteinte) |
| `PPF_AI_MODEL` | Nom du modèle chez le service (obligatoire avec `PPF_AI_BASE_URL`) | aucun |
| `PPF_AI_API_KEY_FILE` | Chemin d'un fichier qui contient seulement la clé d'API, si le service en demande une (forme des secrets Docker de `infra/`). La clé n'est jamais la valeur d'une variable : `PPF_AI_API_KEY` est refusée au démarrage | aucune |
| `PPF_AI_JSON_MODE` | `true` pour demander une réponse en JSON strict (`response_format`), que tous les serveurs ne comprennent pas | `false` |
| `PPF_AI_TIMEOUT_SECONDS` | Attente maximale d'une réponse du modèle (1 à 600) | 30 |
| `PPF_AI_MAX_REVIEWS_PER_DAY` | Relectures par l'IA par 24 heures pour tout le serveur (plafond de coût) | 200 |

Une configuration d'IA invalide (adresse http vers un autre hôte, modèle manquant, fichier de clé illisible, `PPF_AI_API_KEY` définie) arrête le serveur au démarrage avec un message qui ne cite jamais l'adresse ni la clé. Les autres valeurs invalides reprennent leur valeur par défaut.
