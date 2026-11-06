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

## 5. API du serveur (version 1)

Toutes les réponses sont en JSON. Aucune information sur l'expéditeur n'est journalisée.

| Requête | Rôle | Réponses |
|---|---|---|
| `POST /api/v1/events`, corps : un événement signé (section 2), 64 Ko au plus, 1000 étiquettes au plus | Publier un contenu. La signature est vérifiée avant toute chose ; les contenus éditoriaux (type 30023) et les descriptions de salon (types 39000 à 39005, avec exactement un identifiant `d`) ne sont acceptés que des clés de l'équipe éditoriale ; les enveloppes chiffrées (type 1059) sont acceptées de toute clé, nomment exactement un destinataire (`p`) et sont stockées sans être ouvertes ; un message de salon (type 9) porte exactement une étiquette `h` et n'est accepté que si le salon existe et est ouvert ; les listes de membres (39002), les événements d'adhésion ou de modération (9000 à 9022) et les drapeaux `private` et `hidden` sont refusés. Limitation de débit par clé publique (60 par minute) et globale (600 par minute), jamais par adresse IP. | `201` `{"id":"…","status":"stored"}` ; `200` avec `"duplicate"` (déjà connu) ou `"superseded"` (une version plus récente existe) ; `400` `{"error":"invalid_signature"\|"malformed_event"\|"ephemeral_kind"\|"bad_recipient"\|"missing_group"\|"multiple_groups"\|"too_many_tags"\|"unsupported_kind"\|"unsupported_group_flag", "message":"…"}` ; `403` `not_allowed`, `group_closed` ou `members_list_refused` ; `404` `group_not_found` ; `409` `group_taken` ; `413` trop volumineux ; `429` `rate_limited` avec un en-tête `Retry-After` |
| `GET /api/v1/events?kind=1&kind=30023&author=<hex>&identifier=<d>&tag=<nom>:<valeur>&since=<s>&until=<s>&limit=50` | Lister les événements, du plus récent au plus ancien ; tous les paramètres sont facultatifs et répétables sauf `since`, `until`, `limit` (1 à 500). `tag` filtre par étiquette (20 filtres au plus, `identifier` compris, qui équivaut à `tag=d:…`) : le premier deux-points sépare le nom de la valeur, qui peut elle-même en contenir ; plusieurs `tag` du même nom : l'un ou l'autre ; de noms différents : tous ; nom de 1 à 64 caractères, valeur de 1024 octets au plus. Exemples : boîte de réception `kind=1059&tag=p:<hex>`, messages d'un salon `kind=9&tag=h:<id>` | `200` tableau d'événements ; `400` `bad_parameter` |
| `GET /api/v1/events/<id>` | Un événement par son identifiant | `200` l'événement ; `404` `not_found` |
| `GET /api/v1/groups` | Les salons connus du serveur (NIP-29), triés par nom : `[{"id":"…","name":"…","about":"…","picture":null,"public":true,"open":true,"event":{…événement 39000 signé…}}]`. `public` : lisible par tous (toujours vrai dans cette version) ; `open` : tout compte peut y écrire. Aucune liste de membres n'existe ni n'est servie. 500 salons au plus dans cette version. | `200` tableau |
| `GET /api/v1/groups/<id>` | Un salon par son identifiant (1 à 64 caractères parmi `a-z`, `0-9`, `.`, `_`, `~`, `-`) | `200` le salon ; `400` `bad_parameter` ; `404` `group_not_found` |
| `GET /health` | État du serveur | `200` `{"status":"ok","service":"ppf-server","version":"0.1.0","environment":"…"}` |

Quand la base de données n'est pas configurée, toute adresse sous `/api/v1/` répond `503` `{"error":"database_unavailable"}`.

Les événements remplaçables et adressables (section 3) ne sont conservés qu'en une version : la plus récente, et à date égale celle dont l'identifiant est le plus petit. L'application et le serveur appliquent la même règle.

**Messagerie et salons (D-027).** Les messages privés transitent sous forme d'enveloppes NIP-59 (type 1059) adressées par une étiquette `p` ; le serveur ne les ouvre jamais, ne tient aucune liste de conversations ni de contacts, garde au plus `PPF_WRAP_MAX_PER_RECIPIENT` enveloppes par destinataire (1000 par défaut, les plus récentes) et les supprime `PPF_WRAP_RETENTION_DAYS` jours (30 par défaut) après leur réception, date qu'il connaît seul (`received_at`, car `created_at` est volontairement brouillé par NIP-59). Les salons suivent NIP-29 : description adressable de type 39000 (`d` = identifiant du salon, étiquettes `name`, `about`, `picture`, drapeaux `closed` et `restricted`), messages de type 9 portant une étiquette `h`. Première version : salons publics et ouverts, décrits par les clés éditoriales ; adhésion, rôles et modération viendront ensuite.

Variables d'environnement du serveur pour le relais :

| Variable | Rôle | Défaut |
|---|---|---|
| `PPF_WRAP_RETENTION_DAYS` | Durée de conservation des enveloppes chiffrées (type 1059), depuis leur réception | 30 |
| `PPF_WRAP_CLEANUP_INTERVAL_MINUTES` | Fréquence de la suppression des enveloppes expirées (0 désactive la tâche, pour les tests) | 60 |
| `PPF_WRAP_MAX_PER_RECIPIENT` | Nombre d'enveloppes gardées par destinataire (les plus récentes) | 1000 |
| `PPF_POST_RATE_PER_MINUTE` | Publications autorisées par minute et par clé publique sur `POST /api/v1/events` | 60 |
| `PPF_POST_GLOBAL_RATE_PER_MINUTE` | Publications autorisées par minute pour tout le serveur, toutes clés confondues | 600 |
