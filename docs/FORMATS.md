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

La même phrase donne aussi la **clé de secours** (S-146, D-134, section 4 undecies) : chemin `m/44'/1237'/0'/1/0`, branche « 1 » de BIP-32 que NIP-06 n'utilise pas. Pour la phrase d'exemple ci-dessus, sa clé publique est `bfbb7b7fa954b8e5468bebf7b899696f5aab822412013053d891ed7602fbc24e`. Elle ne sert qu'à signer l'avis de compromission ; l'appareil ne la garde pas.

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

Types standards utilisés : `0` (profil), `1` (note courte), `5` (demande de suppression), `30023` (article long), `31923` (événement de calendrier daté, NIP-52 : mobilisation de l'agenda, section 4 sexies), `31925` (réponse à un événement de calendrier, NIP-52 : réponse publique d'une organisation à une mobilisation, section 4 sexies), `9` (message d'un salon, NIP-29), `9000`, `9001`, `9002` et `9005` (rôles et modération d'un salon, NIP-29, D-131 : section 5, partie rôles et modération), `39000` (description d'un salon, NIP-29), `1776` (engagement envers une clé de secours) et `1777` (avis de compromission), du brouillon NIP-41 : section 4 undecies, `1985` (étiquette NIP-32 : statut d'une revendication), `1040` (attestation OpenTimestamps, NIP-03 : preuve d'horodatage d'une archive). Types propres à PPF : `37240` (certificat d'une clé de travail de la plateforme) et `37241` (liste de l'équipe pour les accords de signature), section 4 decies. Les types propres aux contenus PPF (fiche pratique, événement de mobilisation, revendication, etc.) seront attribués dans la plage adressable et ajoutés ici au fur et à mesure.

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
| `ppf-type` | `fiche-pratique`, `dossier`, `decryptage`, `article` (défaut), `parcours`, `histoire`, `qui-decide` ou `revendication` (et les briques de « Voir », section 4 quinquies) ; `appel` et `organisation` pour l'espace « Agir » (section 4 sexies) ; `glossaire` pour une entrée du glossaire (ci-dessous, D-048) | Non |
| `ppf-status` | Revendications seulement : `nouvelle`, `en-debat`, `tres-soutenue`, `retenue` ou `campagne` | Non |
| `ppf-territory` | Revendications seulement : le territoire concerné (« France », un département, une métropole) | Non |
| `ppf-territory-code` | Code du référentiel des territoires (section 4 nonies) à côté du libellé ; pas encore accepté par le serveur pour les revendications | Non |
| `ppf-duration` | Parcours seulement : durée de lecture annoncée, en minutes (1 à 240) | Non |
| `ppf-origin` | `human` (rédigé par un humain) ou `ai-assisted` (produit avec l'aide d'une IA) | Oui |

La date de dernière mise à jour est `created_at`. Une nouvelle version porte le même `d` et une date plus récente : elle remplace la précédente partout (section 4). L'application n'affiche que les contenus dont l'auteur figure dans sa liste d'auteurs de confiance.

Dans le Markdown, un bloc ouvert par une ligne `::: en-bref`, `::: legal`, `::: illegal`, `::: attention` ou `::: conseil` et fermé par une ligne `:::` est un **encart** (résumé, ce qui est légal, ce qui est illégal, avertissement, conseils). Les lecteurs qui ne connaissent pas cette convention voient simplement le texte du bloc.

### Parcours « Comprendre en 5 minutes »

Un parcours (cahier fonctionnel, M13.1) est un contenu éditorial de `ppf-type` `parcours` (décision D-033). Son Markdown se lit ainsi (`core/comprendre/ParcoursFormat`) :

- le texte avant le premier intertitre `## ` est l'**introduction** (son encart « En bref ») ;
- chaque intertitre `## ` (hors d'un encart) ouvre une **étape** ; l'intertitre `## Sources` n'est pas une étape : il vient en dernier et porte les sources ;
- un bloc `::: quiz` ... `:::` clôt chaque étape : une ligne `? question`, de deux à cinq options `- [ ] texte` dont une seule `- [x] texte`, puis une ou plusieurs lignes `> explication` (la bonne réponse et sa source) ;
- un bloc `::: et-maintenant` ... `:::` porte les actions proposées à la fin (« Et maintenant ? ») : une puce faite d'un seul lien `[texte](ppf:<espace>)` vers un espace de l'application (`revendications`, `agir`, `decider`, `carte`, `comprendre`...) devient un bouton ; les autres puces restent du texte.

L'application lit un quiz mal formé comme une étape sans question ; l'outil de signature refuse un parcours dont une étape, un quiz, le bloc « Et maintenant ? » ou les sources manquent ou sont mal formés. Un lecteur qui ne connaît pas ce format voit un article ordinaire.

**Progression, sur l'appareil seulement** : tables locales `learning_progress(address, furthest_step, completed_at, updated_at)` et `learning_answer(address, step, choice, correct)`, indexées par l'adresse stable du parcours (`30023:<clé>:<identifiant>`), étapes et options numérotées à partir de 0. Elles ne sont jamais envoyées, ne sont pas liées au compte et s'effacent d'un geste.

### Décryptages, histoire des luttes, « Qui décide de ça ? » (D-038)

Blocs communs du Markdown de « Comprendre » (`core/comprendre/ContentBlocks`) :

- `::: objections` ... `:::` : des paires faites d'une ligne `? objection` puis d'une ou plusieurs lignes `> réponse` (la réponse cite sa source) ; affiché comme un encart « Objections et réponses » (pluralisme) ;
- `::: chronologie` ... `:::` : des lignes `- DATE : texte`, DATE au format `AAAA`, `AAAA-MM` ou `AAAA-MM-JJ`, dans l'ordre des dates ; affiché comme une frise.

| `ppf-type` | Étiquettes et corps |
|---|---|
| `decryptage` | `ppf-verdict` (obligatoire) : `exact`, `plutot-exact`, `trompeur`, `contredit-par-les-faits`, `inverifiable` ; `ppf-claim` (obligatoire) : l'affirmation mot pour mot ; `ppf-claim-context` : où et quand elle circule. Corps : l'explication, un bloc `objections` (obligatoire), « Et maintenant ? », `## Sources` en dernier. |
| `histoire` | `ppf-country` et `ppf-period` (début, fin facultative ; obligatoires), `ppf-claim` (une par revendication), `ppf-result` (une par résultat obtenu), comme les mouvements de « Voir ». Corps : contexte, déroulé avec un bloc `chronologie`, ce qui a été obtenu, ce qui a fonctionné ou échoué, un bloc `objections` (obligatoire), « Et maintenant ? », `## Sources`. |
| `qui-decide` | Corps commençant par un bloc `::: decideurs` : une ligne `- niveau | rôles | ce que ce niveau décide (référence)` par niveau, du plus proche au plus lointain ; niveaux `commune`, `intercommunalite`, `departement`, `region`, `etat`, `union-europeenne` ; rôles `maire`, `elu-local`, `depute`, `senateur`, `eurodepute`, `ministre`, `president`. Étiquettes `a` de rôle `personne` vers les personnes publiques de « Voir » à nommer dans l'encart des élus concernés. « Et maintenant ? » et `## Sources` obligatoires. |

La page qui définit la méthode et les verdicts est un article d'identifiant `notre-methode`.

### Glossaire (D-048)

**Fichier source** `content/glossaire/<slug>.md` (format complet dans `content/README.md`) :

```markdown
---
slug: motion-de-censure
title: [EXEMPLE] Motion de censure
summary: Vote par lequel les députés peuvent renverser le gouvernement.
variants: motion de censure, motions de censure
topics: parlement, gouvernement
see_also: 49-3, dissolution, gouvernement
related: parcours:le-49-3-et-la-motion-de-censure, qui-decide:ecole
origin: ai-assisted
published_at: 2026-11-07
updated_at: 2026-11-07
---
Explication détaillée (« En savoir plus »), encarts `::: en-bref` permis.

## Sources

- [Constitution, article 49](https://www.legifrance.gouv.fr/...)
```

| Champ de l'en-tête | Contenu | Obligatoire |
|---|---|---|
| `slug` | Identifiant stable de l'entrée (devient `d`) | Oui |
| `title` | Le terme affiché, précédé de `[EXEMPLE] ` pour l'instant | Oui |
| `summary` | La définition rapide de l'infobulle : une ou deux phrases, 200 caractères au plus | Oui |
| `variants` | Les formes repérées dans les textes, séparées par des virgules (singulier, pluriel, sigle, forme longue) ; `\,` écrit une virgule à l'intérieur d'une forme (`Conseil économique\, social et environnemental`). Au moins une ; une forme n'appartient qu'à une entrée (deux formes qui ne diffèrent que par les majuscules comptent comme la même) | Oui |
| `topics` | Thèmes (étiquettes `t`) | Non |
| `see_also` | Slugs d'autres entrées du glossaire | Non |
| `related` | Contenus liés : `parcours:<slug>`, `fiche:<slug>`, `decryptage:<slug>`, `qui-decide:<slug>`, `histoire:<slug>` | Non |
| `auto` | `non` : l'entrée n'est jamais repérée dans les textes (mot trop courant), seulement listée | Non |
| `origin`, `published_at`, `updated_at` | Comme les autres contenus | `origin` oui |

Le corps est l'explication détaillée ; il se termine par `## Sources` (sources primaires).

**Événement** : article NIP-23 (type `30023`), `ppf-type` = `glossaire`, `title` = le terme, `summary` = la définition rapide, et en plus des étiquettes communes :

| Étiquette | Contenu |
|---|---|
| `["ppf-variant", "<forme>"]` | Une par forme, sans l'échappement `\,` |
| `["ppf-auto", "non"]` | Seulement pour une entrée jamais repérée automatiquement |
| `["a", "30023:<clé>:<slug>", "", "voir-aussi"]` | Une par entrée de `see_also` (l'application la retrouve par son slug, quelle que soit la clé de l'équipe qui l'a signée) |
| `["a", "30023:<clé>:<slug>", "", "lie"]` | Une par contenu de `related` (l'application retrouve son type et son titre dans sa copie locale ; un contenu absent n'est pas affiché) |

Fichier intégré : `glossaire.json`, produit par `tools/content-signer`, qui refuse : définition absente ou de plus de 200 caractères, aucune forme, forme en double entre deux entrées, `## Sources` absent, `see_also` ou `related` vers un slug inconnu.

**Repérage** (`core/comprendre/Glossary.kt`, `GlossaryIndex`) : majuscules ignorées, sauf pour un sigle (seulement des capitales et des chiffres, au moins deux lettres : `UE`, `QPC`, `COP21`), comparé avec ses capitales ; apostrophes `'`, `’`, `‘`, `ʼ` équivalentes ; espaces et traits d'union insécables lus comme les ordinaires ; accents respectés ; limites de mot (ni lettre ni chiffre avant et après) ; la forme la plus longue gagne, sans chevauchement ; première occurrence seulement par contenu ; jamais dans les titres, les liens, la section des sources, les adresses écrites en clair (`http://…`, `https://…`, `www.…`, `ppf:…`), ni dans la page d'une entrée pour son propre terme. Un lecteur qui ne connaît pas ce format voit un article ordinaire.

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

## 4 quinquies. Espace « Voir » : briques sourcées (D-034)

> Ajouts du 7 novembre 2026 (D-054, D-056) : brique `parti` et liens de rôle `parti` et `responsable` (détail prêt à reprendre dans `docs/integration/voir-partis.md`, section « Format ») ; demandes à l'équipe de rédaction `ppf-voir-request` version 1, envoyées en message privé NIP-17 (détail dans `docs/integration/voir-demandes.md`, section 2) ; adresses des pages du site dans `docs/integration/voir-web.md`.

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

## 4 sexies. Espace « Agir » : mobilisations, appels, organisations (D-039 à D-044)

Le vocabulaire commun est dans `shared/core/.../core/agir/AgirFormats.kt` ; le contrat complet et son historique dans `docs/integration/agir-contrat.md` (version 1) et `docs/integration/agir-transparence.md` (version 2 : réponses publiques des organisations, type 31925). Qui publie quoi, et ce que fait le serveur, est décrit en section 5. Textes en texte brut : aucun caractère de contrôle, saut de ligne permis dans `content` seulement.

### Mobilisation de l'agenda (type 31923, NIP-52)

Événement de calendrier daté, adressable par `31923:<clé>:<d>`. Une correction, un report ou une annulation est une nouvelle version à la même adresse. `content` : la description, en texte brut, 8000 caractères au plus.

| Étiquette | Obligatoire | Contenu |
|---|---|---|
| `d` | Oui | Identifiant stable, 1 à 64 caractères `a-z0-9._~-` ; l'application prend le début du titre replié et 8 caractères hexadécimaux aléatoires (`marche-pour-l-hopital-3fa94c1e`) |
| `title` | Oui | 1 à 140 caractères |
| `start` | Oui | Début, secondes depuis 1970 |
| `end` | Non | Fin, strictement après `start` |
| `start_tzid` | Non | Fuseau IANA de forme `Zone/Ville` ; l'application écrit `Europe/Paris` |
| `location` | Oui | Lieu en clair (adresse, place, ville) |
| `g` | Non | Géohash de 9 caractères au plus, seulement si l'auteur a placé le point lui-même ; jamais de géolocalisation par un service tiers |
| `ppf-event-type` | Oui | `manifestation`, `blocage`, `rassemblement`, `greve`, `assemblee-generale`, `reunion` ou `autre` |
| `ppf-event-status` | Non | `prevu` (par défaut, non écrit), `annule` ou `reporte` |
| `ppf-territory` | Oui | Commune, département ou `France`, 100 caractères au plus |
| `ppf-charter` | Oui | `1.0` |
| `ppf-declaration` | Non | Pour une manifestation : `faite`, `en-cours` ou `non-faite` ; les trois sont proposées dans le formulaire et affichées (« Déclarée en préfecture », « Déclaration en préfecture en cours », « Non déclarée en préfecture »), avec une phrase qui prévient l'auteur que la mention est publique (D-044) |
| `p` | Non | `["p", "<clé hex>", "", "organizer"]` pour chaque organisation organisatrice nommée (AG-21) ; elle n'est montrée co-organisatrice que si elle l'a confirmé par sa propre réponse (type 31925, `ppf-role` = `organisateur`), sinon « annoncée, non confirmée » |
| `a` | Non | Adresse de l'appel dont l'événement est issu (AP-07) |
| `r` | Non | Lien `http` ou `https`, sans espace |
| `summary` | Non | Résumé court |

**Codes d'erreur**, communs à l'application et au serveur : `missing_field` (message `d`), `missing_title`, `missing_start`, `bad_end`, `missing_location`, `bad_event_type`, `missing_territory`, `missing_charter`, `missing_field` avec le nom de l'étiquette pour une valeur facultative illisible, `too_long` avec le nom de l'étiquette. L'application est plus stricte que le serveur sur certaines longueurs (lieu 200 caractères, résumé 280) ; le serveur borne toute étiquette sans limite propre à 1000 caractères.

**Annulation par l'auteur** : nouvelle version à la même adresse, `created_at` plus récent, où seule `ppf-event-status` passe à `annule` (remplacée à sa place, ou insérée juste après `ppf-event-type`) ; texte et autres étiquettes identiques, dans le même ordre. Le serveur la publie sans relecture (section 5).

**Export iCalendar** (RFC 5545), construit sur l'appareil (`ICalendar`) ou servi par `GET /api/v1/agenda.ics` : un `VEVENT` par mobilisation, `UID` = adresse stable `31923:<clé>:<d>` (l'agenda remplace l'événement importé au lieu de le doubler), `DTSTAMP` = date de la version, `DTSTART` et `DTEND` en UTC (`Z`, pas de `DTEND` sans fin), `SUMMARY`, `LOCATION`, `DESCRIPTION`, `URL` (premier lien `http(s)`), `STATUS` ; textes échappés (`\`, `;`, `,`, retours à la ligne en `\n`, autres caractères de contrôle retirés), lignes repliées à 75 octets UTF-8 sans couper un caractère, fins de ligne CRLF. Deux écarts entre les deux mises en œuvre, à unifier : l'appareil écrit `METHOD:PUBLISH`, `STATUS` `CONFIRMED`, `TENTATIVE` (reporté) ou `CANCELLED` et une `DESCRIPTION` faite du résumé puis du texte ; le serveur n'écrit pas `METHOD`, ajoute `X-WR-CALNAME`, n'écrit que `STATUS:CANCELLED` et prend le texte, ou le résumé à défaut.

**Sur l'appareil seulement** : tables locales `agenda_response(address, response, ends_at, responded_at)` (`response` = `going`, « J'y serai », ou `interested`, « Ça m'intéresse » ; effacée 24 heures après la fin de l'événement) et `agenda_proposal(event_id, state, reason, updated_at)` (`state` = `review`, `published` ou `rejected`, avec la raison de l'équipe). Jamais envoyées.

### Réponse publique à une mobilisation (type 31925, NIP-52, version 2 du contrat)

Réponse NIP-52 à un événement de calendrier (« Calendar Event RSVP »), adressable par `31925:<clé>:<d>`, signée par la clé qui répond. Elle permet à une **organisation vérifiée** ou à une **clé de l'équipe éditoriale** (PPF) d'afficher qu'elle participe à une mobilisation, ou de confirmer qu'elle la co-organise (décision de la propriétaire du 7 novembre 2026, D-044 : « la transparence passe avant tout »). Personne ne peut répondre au nom d'une autre clé.

| Étiquette | Obligatoire | Contenu |
|---|---|---|
| `d` | Oui | Identifiant stable, 1 à 200 caractères parmi lettres, chiffres et `._~:-` ; l'application écrit l'adresse de la mobilisation (`31923:<clé>:<d>`), pour qu'une nouvelle réponse de la même clé remplace la précédente ; un autre client peut écrire un UUID (NIP-52) |
| `a` | Oui | Adresse de la mobilisation, `31923:<clé>:<d>` (une seule ; une adresse de relais peut suivre, comme dans NIP-52) |
| `status` | Oui | `accepted`, `declined` ou `tentative` (NIP-52) |
| `ppf-role` | Non | Extension PPF : `participant` (par défaut) ou `organisateur` ; `organisateur` ne compte que pour une clé que la mobilisation nomme en `p` ; ailleurs, la réponse compte comme une participation |
| `p` | Non | Clé de l'auteur de la mobilisation (celle de `a`, NIP-52) |
| `e` | Non | Identifiant de la version de la mobilisation à laquelle on répond (NIP-52) ; l'application ne l'écrit pas |
| `fb` | Non | `free` ou `busy` (NIP-52) ; l'application ne l'écrit pas |

`content` : vide, ou une phrase courte (280 caractères au plus, sans coordonnée personnelle) ; **l'application ne l'affiche jamais**, d'où la publication directe, sans relecture. Retirer sa participation publie une version plus récente à la même adresse, `declined`.

**Lecture sur l'appareil** (`Participation`, `Participations`, `Hosting`) : pour chaque clé et chaque mobilisation, seule la réponse la plus récente compte, quel que soit son `d` (la plus récente gagne, à date égale l'identifiant le plus petit) ; un statut inconnu ou une adresse `a` illisible écarte la réponse ; un rôle inconnu se lit `participant`. **Seules les réponses des organisations vérifiées et des clés éditoriales sont montrées** ; celle de toute autre clé (un citoyen sur un autre client) est ignorée : la visibilité de la participation des citoyens attend une décision de la propriétaire, et leur nombre viendra de la méthode de comptage anonyme. Affichage : « Organisé par » = l'auteur, puis les organisations nommées qui ont confirmé (`accepted` et `organisateur`, badge « Vérifiée »), puis celles qui n'ont pas confirmé, « annoncée, non confirmée » ; « Organisations participantes (N) » = les clés qui comptent et dont la réponse est `accepted`, organisateurs compris ; les réponses `declined` et `tentative` ne sont jamais montrées.

**Synchronisation** : `GET /api/v1/events?kind=31925`, tout le type en bloc avec son propre curseur (`pull:31925`, parti de 0 la première fois), jamais une requête par mobilisation : le serveur ne sait pas quels événements l'appareil regarde ; copie dans la table `event`, versions remplacées par la règle habituelle.

**Codes d'erreur du serveur** : `missing_field` avec le nom de l'étiquette (`d`, `a`, `status`, `ppf-role`, `p`, `e`, `fb`), `too_long` (`content` au-delà de 280 caractères, toute autre étiquette au-delà de 1000), `personal_data` (`content`), `malformed_event` (caractère de contrôle). Écarts avec NIP-52 : le `d` de l'application n'est pas un identifiant aléatoire mais l'adresse de la mobilisation (une seule réponse par clé et par mobilisation) ; `ppf-role` n'existe pas dans NIP-52 (les autres clients l'ignorent).

### Appel (type 30023, `ppf-type` = `appel`)

Contenu adressable `30023:<clé>:<d>`, comme les revendications. `content` : le texte de l'appel en texte brut (pas de Markdown, donc aucun lien cliquable caché), 8000 caractères au plus ; pour un appel à témoins, la description factuelle, sans accuser nommément personne.

- **Étiquettes communes** : `d` (l'application prend les premiers mots du titre repliés et quatre caractères hexadécimaux, 64 caractères `a-z0-9-` au plus ; le serveur accepte jusqu'à 128), `title`, `ppf-type` = `appel`, `ppf-call-type`, `ppf-territory`, `ppf-charter` = `1.0`, `published_at`, `ppf-origin` = `human`.
- **Facultatives** : `summary`, `ppf-goal` (entier positif), `ppf-deadline` (secondes), `start`, `end`, `start_tzid` (`Europe/Paris`), `location`, `g`, `r` (liens), `ppf-call-status` (`ouvert`, écrit à la création, ou `clos` ; absente, elle se lit `ouvert`), `ppf-report` (bilan à la clôture, 2000 caractères au plus).
- **Valeurs libres** des champs propres aux types : texte (l'application limite à 300 caractères, 1000 pour `ppf-action`, `ppf-request`, `ppf-use`, `ppf-mission`, `ppf-alternatives` et `ppf-accessibility`, comme le serveur, qui borne toute étiquette sans limite propre à 1000 caractères, `AgirFormats.MAX_TAG_VALUE`) ; aucune coordonnée personnelle (adresse électronique, téléphone, IBAN, numéro de sécurité sociale) dans le texte ni dans les champs libres, vérifié par l'application puis par le serveur (`400 personal_data`) ; `ppf-people` et `ppf-goal` sont des entiers positifs ; `ppf-remote` vaut `oui` pour une mission à distance ; le parcours d'une manifestation va dans le texte.
- **Clôture par l'auteur** : nouvelle version à la même adresse où seuls `ppf-call-status` (`clos`) et `ppf-report` changent (pour un appel à témoins, seul le statut peut changer sans relecture).

Étiquettes obligatoires selon le type (AP-10, `AgirFormats.CALL_REQUIRED_TAGS` ; « `a|b` » : l'une ou l'autre). Le serveur répond `400 missing_field` avec le nom de la première qui manque.

| `ppf-call-type` | Étiquettes obligatoires en plus des communes | Réponse proposée dans l'application |
|---|---|---|
| `se-reunir`, `debattre`, `s-organiser` | `ppf-object`, `start`, `location\|r`, `ppf-organizer`, `ppf-accessibility` | J'y serai |
| `manifester` | `start`, `location`, `ppf-organizer`, `ppf-declaration` | J'y serai |
| `soutenir` | `ppf-cause`, `ppf-action`, `ppf-deadline` | Je soutiens |
| `petition` | `r`, `ppf-platform` (`assemblee-nationale`, `senat`, `initiative-citoyenne-europeenne` ou `autre`), `ppf-goal`, `ppf-deadline` | J'ai signé |
| `interpeller` | `ppf-targets`, `ppf-request`, `ppf-deadline` | Je soutiens |
| `boycotter` | `ppf-company`, `ppf-source`, `ppf-alternatives`, `ppf-boycott-duration` | Je soutiens |
| `collecte` | `ppf-beneficiary`, `ppf-goal`, `ppf-use`, `r`, `ppf-deadline` ; organisations vérifiées et équipe éditoriale seulement | J'aide |
| `benevoles` | `ppf-mission`, `ppf-skills`, `ppf-people`, `location\|ppf-remote`, `ppf-period` | J'aide |
| `information` | `ppf-source` | Je soutiens |
| `temoins` | `start`, `end`, `location`, `ppf-evidence`, `ppf-recipient` ; toujours relu | Je témoigne (désactivé tant que le canal sécurisé n'existe pas) |
| `autre` | aucune | Je soutiens |

**Sur l'appareil seulement** : table locale `call_response(address)`, l'adresse de chaque appel auquel l'utilisateur a répondu, sans date. Jamais envoyée. Une réponse est effacée 7 jours (`CallResponseStore.KEPT_AFTER_END`) après la fin de l'appel, au chargement suivant des appels : la fin est la clôture (date de la version close) ou l'échéance (`ppf-deadline`), la première des deux, et à défaut d'échéance la fin (`end`) ou le début (`start`) de ce qu'il annonce ; pour un appel à témoins, dont les dates sont celles des faits passés, seules l'échéance et la clôture comptent. Un appel ouvert sans aucune date garde la réponse jusqu'à son retrait.

### Fiche de l'annuaire des organisations (type 30023, `ppf-type` = `organisation`)

Publiée seulement par une clé de l'équipe éditoriale. `content` : la présentation, en Markdown simple, comme les fiches.

| Étiquette | Obligatoire | Contenu |
|---|---|---|
| `d` | Oui | Identifiant stable, 1 à 64 caractères `a-z0-9._~-` |
| `title` | Oui | Nom de l'organisation, 1 à 140 caractères |
| `summary` | Oui | Résumé |
| `published_at` | Oui | Secondes, en texte |
| `ppf-type` | Oui | `organisation` |
| `ppf-org-type` | Oui | `association`, `syndicat`, `collectif`, `legal-team`, `street-medics`, `avocats-benevoles` ou `autre` (une valeur inconnue se lit `autre`) |
| `ppf-territory` | Oui | « Commune (dd) », « Département (dd) » ou `France` ; le numéro de département sert à « près de chez moi » |
| `ppf-verified` | Oui | Date de la vérification, `AAAA-MM-JJ` |
| `ppf-origin` | Non | `human` ou `ai-assisted`, affichée sur la fiche |
| `p` | Non | Clé de l'organisation si elle a un compte : c'est elle qui devient « organisation vérifiée » |
| `ppf-org-status` | Non | `verifiee` (valeur par défaut, l'étiquette est alors omise) ou `revoquee` : la vérification est retirée (contrat d'Agir, version 2). Le serveur refuse toute autre valeur (`missing_field`, message `ppf-org-status`) ; l'appareil lit une valeur inconnue comme `revoquee`, pour qu'une version future n'étende jamais la confiance |
| `r` | Non | Site web `http(s)` à nom d'hôte ASCII, sans « utilisateur@ » ; plusieurs possibles, cinq lus au plus |
| `ppf-contact-phone`, `ppf-contact-email`, `ppf-contact-address` | Non | Coordonnées publiques de l'organisation, jamais celles d'une personne |
| `g` | Non | Géohash |

**Organisation vérifiée** : une clé nommée en `p` d'une fiche signée par une clé éditoriale de confiance (`PPF_EDITOR_KEYS` sur le serveur, `EditorialTrust` sur l'appareil). La fiche la plus récente décrit la clé (à date égale, le plus petit identifiant) ; une nouvelle version sans `p` retire la vérification, de même qu'une version `ppf-org-status` = `revoquee` qui garde sa clé `p` : celle-ci n'est plus vérifiée, ni sur l'appareil (`VerifiedOrganisations.revocationFor` rend la fiche révoquée) ni sur le serveur (le cache est oublié dès le stockage de la fiche). Transparence : la fiche révoquée n'est jamais effacée en silence ; elle perd son badge « Vérifiée », sort de la liste par défaut de l'annuaire (filtre « Inclure les vérifications retirées ») et sa page, ouverte par un lien, affiche « Vérification retirée par PPF le … » (date de la version révoquée, heure de Paris). Republier la fiche avec `verifiee` ou sans l'étiquette rétablit la vérification. Les écrans des fiches pratiques n'affichent jamais ces fiches.

## 4 septies. Espace « Décider » : pétitions, Pacte démocratique, réponses (D-059, D-060)

Même principe que « Voir » : des événements NIP-23 (type 30023) signés par l'équipe éditoriale, avec `ppf-type`, un corps Markdown et des étiquettes structurées (champs positionnels, champ vide = inconnu). Les étiquettes communes `ppf-source`, `ppf-archive`, `ppf-correction`, `ppf-next` et les liens `a` gardent leur sens. Contenus intégrés : `decider.json`. Signés par la plateforme, ces contenus relèvent de l'usage `decider.pacte` (section 4 decies), qui accepte exactement un `ppf-type` parmi `pacte`, `engagement`, `petition` et `proposition-finale` (texte final d'une délibération, format à décrire par « Décider ») ; ces quatre types ne sont jamais ceux d'un contenu éditorial.

### Pétition (`ppf-type` = `petition`)

| Étiquette | Contenu |
|---|---|
| `ppf-platform` | `assemblee`, `senat`, `cese`, `rip` ou `ice` (obligatoire) |
| `ppf-official-url` | Page de la pétition, en `https`, sur un site de la plateforme : `petitions.assemblee-nationale.fr`, `petitions.senat.fr`, `petitions.lecese.fr`, `www.referendum.interieur.gouv.fr`, `citizens-initiative.europa.eu` (obligatoire ; sinon la brique est ignorée) |
| `ppf-petition-status` | `ouverte` ou `close` (obligatoire) |
| `ppf-reference` | Numéro donné par la plateforme |
| `ppf-opened`, `ppf-closes` | Dates d'ouverture et dernier jour de signature (AAAA-MM-JJ) |
| `ppf-count` | Relevé du compteur : `nombre`, `date`, `source` ; répétée pour l'historique, le plus récent compte |
| `ppf-departments` | Départements d'origine des signataires, si la plateforme les publie : `nombre`, `date` |
| `ppf-required` | Soutiens nécessaires pour cette procédure (référendum d'initiative partagée) |
| `ppf-milestone` | Étape franchie : `date`, `libellé` |
| `a` rôle `texte` | Le texte de loi de « Voir » concerné |

### Pacte (`ppf-type` = `pacte`)

| Étiquette | Contenu |
|---|---|
| `ppf-election` | `identifiant`, `libellé`, `premier tour` (facultatif) |
| `ppf-pacte-status` | `proposee` (liste de départ, à voter), `envoye`, `clos` |
| `ppf-measure` | `identifiant`, `titre`, `engagement précis`, `texte à modifier` ; 1 à 10, dans l'ordre, identifiants distincts |
| `ppf-sent`, `ppf-deadline` | Jour d'envoi et date limite, les mêmes pour tous (obligatoires dès l'envoi) |
| `a` rôle `candidat` | Chaque candidat, vers sa fiche de « Voir » |

Empreinte du texte envoyé : SHA-256 (hexadécimal) du texte `ppf-pacte-v1\n`, puis `identifiant␟libellé\n` de l'élection, puis pour chaque mesure `identifiant␟titre␟engagement␟texte à modifier\n` (␟ = U+001F, espaces de début et de fin retirés).

### Réponse d'un candidat (`ppf-type` = `engagement`)

| Étiquette | Contenu |
|---|---|
| `a` rôle `pacte` | Le Pacte (un seul) |
| `a` rôle `candidat` | Le candidat (un seul) |
| `ppf-pacte-text` | Empreinte du texte répondu ; la réponse ne compte que si elle est égale à celle du Pacte |
| `ppf-received` | Jour de réception (obligatoire) |
| `ppf-answer` | `identifiant de mesure`, `signe` ou `refuse`, `commentaire` (facultatif) ; une seule par mesure |
| `ppf-archive` | Document reçu : `empreinte SHA-256`, `preuve d'horodatage`, `adresse de l'archive`, `date` |

Suivi après l'élection : un fait de « Voir » porte `a` rôle `pacte` et `ppf-pacte-measure` = identifiant de la mesure.

## 4 octies. Annuaire des élus (D-047)

Données publiques du Répertoire national des élus (RNE), servies par le serveur en JSON (ce ne sont pas des événements signés). Détail des sources et des règles de construction : `docs/integration/elus-serveur.md` ; lecture sur l'appareil : `docs/integration/elus-app.md`.

### Index (`GET /api/v1/elus`)

```json
{
  "format": 1,
  "example": true,
  "source": {
    "name": "Répertoire national des élus (ministère de l'Intérieur)",
    "url": "https://www.data.gouv.fr/fr/datasets/5c34c4d1634f4173183a64f1/",
    "licence": "Licence Ouverte 2.0",
    "updatedOn": "2026-08-11"
  },
  "importedOn": "2026-11-07",
  "packages": [
    { "code": "69", "name": "Rhône", "sha256": "<64 hex>", "bytes": 123456, "postalCodes": ["69001", "69002"] },
    { "code": "FR", "name": "France", "sha256": "<64 hex>", "bytes": 4567, "postalCodes": [] }
  ]
}
```

- `example` : vrai pour le jeu fictif (`PPF_RNE_SOURCE=example`, valeur par défaut) ; l'application affiche alors « Données fictives [EXEMPLE] ».
- `packages` : un par département (`01` à `95`, `2A`, `2B`, `971` à `976`), par collectivité d'outre-mer qui a des élus dans le RNE (`975`, `977`, `978`, `986`, `987`, `988`) et `FR` (membres du Parlement européen élus en France), triés par code. `sha256` : empreinte SHA-256, en hexadécimal minuscule, des **octets exacts du corps JSON non compressé** (UTF-8) ; `bytes` : leur nombre ; `postalCodes` : les codes postaux des communes du paquet, triés.

### Paquet (`GET /api/v1/elus/{code}`)

```json
{
  "format": 1,
  "example": true,
  "code": "69",
  "name": "Rhône",
  "region": { "code": "84", "name": "Auvergne-Rhône-Alpes" },
  "sourceUpdatedOn": "2026-08-11",
  "communes": [
    { "code": "69123", "name": "Lyon", "postalCodes": ["69001", "69002"], "epci": null, "cantons": [], "circonscriptions": ["6901", "6902"], "collectivite": "69M" }
  ],
  "epcis": [ { "code": "<SIREN>", "name": "…" } ],
  "cantons": [ { "code": "6901", "name": "…" } ],
  "circonscriptions": [ { "code": "6901", "name": "1re circonscription du Rhône" } ],
  "officials": [
    { "mandate": "municipal", "territory": "69123", "name": "[EXEMPLE] Prénom Nom", "function": "Maire", "since": "2026-03-22", "functionSince": "2026-03-28" },
    { "mandate": "communautaire", "territory": "<SIREN>", "commune": "<code Insee>", "name": "…", "function": null, "since": "…", "functionSince": null }
  ],
  "collectivites": [ { "code": "69M", "name": "Métropole de Lyon" } ]
}
```

| Champ | Contenu |
|---|---|
| `region` | Région du département ; `null` pour une collectivité d'outre-mer et pour `FR` |
| `communes[]` | Code Insee, nom, codes postaux (ceux des arrondissements pour Paris, Lyon et Marseille), `epci` (SIREN de l'intercommunalité ou `null`), `cantons` et `circonscriptions` (toutes celles de la commune, plusieurs pour une commune partagée ; aucun canton là où il n'y a pas de conseil départemental), `collectivite` (facultatif, voir ci-dessous) |
| `epcis`, `cantons`, `circonscriptions`, `collectivites` | Listes `{code, name}` des territoires cités |
| `officials[]` | `mandate`, `territory`, `name` (« Prénom Nom »), `function` (libellé du RNE ou `null`), `since` (début du mandat), `functionSince` (début de la fonction ou `null`) ; `commune` seulement pour `communautaire` (commune représentée, quand le RNE la donne) |

Valeurs de `mandate` et de `territory` : `municipal` (code Insee de la commune), `communautaire` (SIREN de l'intercommunalité), `departemental` (code du canton), `regional` (code de la région ; le paquet contient les conseillers de la section départementale et le président du conseil régional), `collectivite` (code de la collectivité à statut particulier : `69M`, `94` pour la Corse, `972`, `973`, `975`, `977`, `978`, `986`, `987`, `988`), `depute` (code de la circonscription), `senateur` (code du département : `69`, `2A`, `972`, `977`), `europeen` (`FR`, paquet national seulement).

**Codes des circonscriptions** : code du département ou de la collectivité suivi du numéro sur deux chiffres : `0101`, `6901`, `7518` en métropole ; `2A01`, `2B02` en Corse ; `97101` à `97104`, `97401` à `97407` outre-mer ; `97501`, `97701` (Saint-Barthélemy et Saint-Martin, circonscription commune, présente dans les paquets `977` et `978`), `98601`, `98701` à `98703`, `98801`, `98802`. Le numéro est toujours les deux derniers caractères. Les circonscriptions des Français établis hors de France (`ZZ01` à `ZZ11`) ne sont dans aucun paquet, pas plus que leurs sénateurs.

**`collectivite` d'une commune** (ajouté le 7 novembre 2026, facultatif) : code de la collectivité dont l'assemblée remplace le conseil départemental pour cette commune (`69M` Métropole de Lyon, `94` Corse, `972` Martinique, `973` Guyane, collectivités d'outre-mer), tiré du champ `CTCD` du Code officiel géographique ; le nom est dans `collectivites`. L'application montre alors cette assemblée sous le niveau « Département », à la place du conseil départemental. Un paquet dont aucune commune ne porte ce champ (écrit avant cet ajout) est lu comme avant : toutes les assemblées du paquet sont montrées.

**Règles communes** : dates `AAAA-MM-JJ` ; JSON compact, champs dans l'ordre ci-dessus ; listes triées (communes par nom sans accents ni majuscules, puis code ; élus par mandat, territoire, rang dans le conseil, nom) pour que deux imports des mêmes fichiers donnent les mêmes octets. **Jamais** de date de naissance, de sexe, de profession ni de nationalité.

**HTTP** : `ETag: "<sha256>"`, **entre guillemets** (RFC 9110), la valeur entre les guillemets étant le `sha256` de l'index ; `If-None-Match` (avec ou sans guillemets, `W/`, liste ou `*`) répond `304` sans corps ; `Cache-Control: public, no-cache` ; `Vary: Accept-Encoding` ; avec `Accept-Encoding: gzip`, le corps est envoyé compressé (`Content-Encoding: gzip`), l'empreinte restant celle des octets décompressés. L'appareil refuse un paquet dont l'empreinte ne correspond pas à l'index, ou qui ne se lit pas comme le paquet du code demandé.

**Lecture par l'appareil** (`core/elus/ElusFormats.kt`, `ElusReader`) : stricte pour le document (format 1, code valide, nom présent ; sinon le paquet est ignoré), tolérante pour ses entrées (une commune ou un élu illisible est écarté, les champs inconnus ignorés) ; 32 Mo au plus par paquet.

### Identifiant d'un mandat (`rne1:`)

Le RNE ne donne aucun identifiant de mandat. Le serveur en calcule un, stable d'un import à l'autre, pour le back-office (file `elus.certifications`, `ElusLookup`, `docs/integration/elus-serveur.md`, section 4 bis) :

```
rne1:<32 caractères hexadécimaux minuscules>
```

- Les 32 caractères sont les 16 premiers octets (128 bits) du SHA-256 des octets UTF-8 de `mandat LF territoire LF nom LF début` (LF = U+000A), où `mandat` est la valeur de `mandate` (`municipal`, `depute`…), `territoire` la valeur de `territory`, `début` la date de début du mandat `AAAA-MM-JJ` (`since`), et `nom` le nom affiché du paquet (`name`, « Prénom Nom ») mis sous forme canonique : décomposition Unicode NFD, marques combinantes retirées, minuscules (sans paramètre régional), suites d'espaces réduites à une, espaces de début et de fin retirées.
- La fonction et sa date n'entrent pas dans l'empreinte : un adjoint qui devient maire garde l'identifiant de son mandat. Une correction d'accent ou de casse dans le RNE ne le change pas ; une correction du nom, du territoire ou de la date de début, si.
- Exemple (jeu fictif) : `municipal`, `69123`, « [EXEMPLE] Claude Démonstration », `2026-03-15` donnent l'empreinte de `municipal⏎69123⏎[exemple] claude demonstration⏎2026-03-15` (⏎ = LF).
- Le préfixe `rne1` changera si la règle change ; un identifiant d'une autre forme n'est jamais trouvé.
- L'identifiant n'est écrit ni dans les paquets ni dans la base : il est recalculé sur les données servies. Il désigne un mandat en cours « selon la source mise à jour le … », jamais une personne.

### Sur l'appareil : schéma local en version 11

Migration `shared/core/src/commonMain/sqldelight/.../core/db/migrations/10.sqm` (schéma 10 vers 11), identique aux définitions de `Elus.sq` (un test compare une base neuve et une base migrée) :

| Table | Colonnes | Rôle |
|---|---|---|
| `elus_commune` | `id` (toujours 0 : une seule ligne), `commune_code` (code Insee), `department_code` (code du paquet) | La commune choisie ; jamais envoyée |
| `elus_package` | `code` (clé : un département, `FR`, ou `index` pour l'index du serveur), `sha256`, `json` (le JSON exact vérifié), `source_updated_on` (`AAAA-MM-JJ`), `downloaded_at` (secondes depuis 1970) | Les listes téléchargées, lues hors ligne ; seuls restent le paquet de la commune, `FR` et l'index |

« Oublier ma commune » vide les deux tables.

### Sur le serveur : migration Flyway `V6__elus.sql`

| Table | Rôle |
|---|---|
| `elus_import` | Un essai d'import par ligne : source (`example` ou `official`), statut (`building`, `ready`, `failed`), dates, date de la source, nombres d'élus par mandat (`counts`, pour la garde des 20 %), index exact et son empreinte, motif d'échec (code court, jamais une valeur lue dans un fichier). Un seul import servi et un seul en construction à la fois |
| `elus_package` | Les paquets d'un import : code, nom, `sha256` et `bytes` du JSON non compressé, codes postaux, corps compressé en gzip |
| `elus_staging` | Table de travail (`UNLOGGED`) des élus lus pendant un import, vidée dès que les paquets sont écrits |

Rien sur les lecteurs : ni adresse, ni requête, ni qui a téléchargé quel paquet.

Variables d'environnement du serveur pour l'annuaire :

| Variable | Rôle | Défaut |
|---|---|---|
| `PPF_RNE_SOURCE` | `example` (jeu fictif embarqué, ne contacte personne) ou `official` (vrai RNE, suivi avec `PPF_ENVIRONMENT=production` ou `preproduction`, décision de la propriétaire du 7 novembre 2026 : « on reste dans la vraie info même en preprod » ; ailleurs le jeu fictif est servi) | `example` |
| `PPF_RNE_REFRESH_DAYS` | Jours entre deux imports ; nouvel essai six heures après un échec ; `0` coupe l'import automatique (tests) | 7 |

## 4 nonies. Référentiel des territoires (D-090)

Statistiques publiques de l'Insee (populations, électeurs inscrits) et codes officiels des territoires, servis en JSON (ce ne sont pas des événements signés). Aucune donnée personnelle. Conception, sources et limites : `docs/integration/territoires.md` ; lecture sur l'appareil : `shared/core/.../core/territoires/`.

### Codes et étiquette `ppf-territory-code`

| Type | Code | Exemples |
|---|---|---|
| France | `fr` | `fr` |
| Région (COG) | `reg:<REG>` | `reg:84`, `reg:94` |
| Département (COG) | `dep:<DEP>` | `dep:01`, `dep:2A`, `dep:971` |
| Collectivité à compétences départementales (COG `CTCD`) | `ctcd:<CTCD>` | `ctcd:01D`, `ctcd:69D` (Rhône hors Métropole), `ctcd:69M`, `ctcd:20R`, `ctcd:6AE`, `ctcd:75C`, `ctcd:972R`, `ctcd:973R`, `ctcd:976R` |
| Collectivité d'outre-mer et Nouvelle-Calédonie (COG `COMER`) | `comer:<COMER>` | `comer:975`, `comer:987`, `comer:988` |
| EPCI à fiscalité propre | `epci:<SIREN>` | `epci:200040590` |
| Commune (COG) | `com:<COM>` | `com:69123`, `com:2A004`, `com:97502` |
| Arrondissement municipal | `arm:<ARM>` | `arm:75101`, `arm:69381` |

Forme exacte : `^(fr|reg:[0-9]{2}|dep:(0[1-9]|1[0-9]|2[1-9AB]|[3-8][0-9]|9[0-5]|97[1-6])|ctcd:([0-9]{2}D|97[14]D|20R|69M|6AE|75C|972R|973R|976R)|comer:9[78][0-9]|epci:[0-9]{9}|com:([0-9]{5}|2[AB][0-9]{3})|arm:[0-9]{5})$` (préfixe en minuscules, code en majuscules ; `TerritoryCode.parse` lit aussi les majuscules et les espaces autour). La Métropole de Lyon est toujours `ctcd:69M`, jamais un EPCI.

**`ppf-territory`** (salons, mobilisations, appels, organisations, revendications) **ne change pas** : un libellé lisible, obligatoire là où il l'est. Forme recommandée quand il vient du référentiel : « Nom (dd) » pour une commune ou un département, le nom seul pour une région, une collectivité d'outre-mer ou « France ».

**`ppf-territory-code`** (nouvelle étiquette, **facultative**, au plus une) : le code du **plus petit territoire du référentiel qui contient** ce que dit le libellé (« Quartier de la Guillotière » : `arm:69387`). L'application l'écrit seulement quand l'utilisateur a choisi un territoire proposé par le référentiel, jamais deviné à partir d'un texte. À la lecture : un code connu sert aux comparaisons (filtres, « près de chez moi », doublons) ; l'ancien code d'une commune fusionnée est suivi (`formerCodes`) ; un code absent, invalide ou inconnu laisse le comportement d'aujourd'hui sur le libellé. Le serveur ne vérifie que la forme, jamais l'existence. Une étiquette séparée est indexée comme les autres : `GET /api/v1/events?tag=ppf-territory-code:dep:69`. État le 7 novembre 2026 : acceptée par le serveur pour l'espace « Agir » et les salons (aucune liste fermée d'étiquettes) ; **refusée pour les revendications** (`DemandDrafts.ALLOWED_TAGS`) tant que le serveur ne l'accepte pas (pôle « Décider », à l'entrée dans `main`) : l'application ne l'écrit pas sur une revendication avant.

### Niveau national (`GET /api/v1/territoires`)

```json
{
  "format": 1, "example": true, "partial": true, "geography": "2026-01-01", "builtOn": "2026-02-11",
  "sources": [ { "id": "popref-2023", "kind": "population", "name": "Populations de référence 2023 (Insee)", "url": "https://www.insee.fr/fr/statistiques/8681011", "licence": "Licence Ouverte 2.0", "vintage": 2023, "date": "2026-01-01" } ],
  "france": { "name": "France", "population": 68094280, "populationTotale": 69294105, "populationPerimeter": "…", "inscrits": 48154748, "inscritsComplementaires": 373681, "inscritsPerimeter": "…" },
  "regions": [ { "code": "94", "name": "Corse", "of": "de la Corse", "population": 355486, "populationTotale": 360270, "inscrits": 253064, "inscritsComplementaires": 7210, "heldBy": "20R" } ],
  "departements": [ { "code": "69", "name": "Rhône", "of": "du Rhône", "region": "84", "collectivites": ["69D", "69M"], "population": 1914667, "populationTotale": 1940015, "inscrits": 1227213, "inscritsComplementaires": 7992, "spread": "counts" } ],
  "collectivites": [ { "code": "69M", "name": "Métropole de Lyon", "of": "de la Métropole de Lyon", "special": true, "region": "84", "departements": ["69"], "population": 1, "inscrits": 1, "inscritsComplementaires": 1, "computed": ["population", "inscrits", "inscritsComplementaires"] } ],
  "outreMer": [ { "code": "975", "name": "Saint-Pierre-et-Miquelon", "of": "de Saint-Pierre-et-Miquelon", "population": 5790, "inscrits": 5059, "inscritsComplementaires": 4, "spread": "counts", "sources": { "population": "insee-com-2023" } } ],
  "packages": [ { "code": "69", "name": "Rhône", "sha256": "<64 hex>", "bytes": 123456, "postalCodes": ["69001"] }, { "code": "communes", "name": "Toutes les communes", "sha256": "<64 hex>", "bytes": 1234, "postalCodes": [] } ]
}
```

(Chiffres du Rhône, de la Corse, de Saint-Pierre-et-Miquelon et de la France relevés le 7 novembre 2026 ; les `1` des exemples ne montrent que la place d'un chiffre.)

- `sources[].kind` : `population`, `electorate` ou `geography`. La source d'un chiffre est la première de son genre, sauf `sources` sur l'entrée (`{"population": "insee-com-2023"}`).
- `population` (population municipale), `populationTotale`, `inscrits` (liste principale), `inscritsComplementaires` (listes complémentaires municipales) : entiers ; **absent = inconnu** (`0` est une valeur). `computed` : les chiffres additionnés par le serveur (la population totale n'est jamais additionnée, elle compte deux fois certaines personnes).
- `spread` (départements et outre-mer) : `counts`, `to-confirm` ou `excluded`, règle de l'article 148, alinéa 6, du Règlement de l'Assemblée nationale.
- `heldBy` : collectivité qui exerce les compétences de la région (`20R`, `972R`, `973R`, `976R`).
- `example` : jeu d'exemple (vrais chiffres publics, quelques départements) ; `partial` : tous les départements n'ont pas de paquet. `builtOn` : la date la plus récente des sources (stable d'un import des mêmes sources à l'autre).
- `france` : les deux chiffres publiés n'ont pas le même périmètre, dit par `populationPerimeter` et `inscritsPerimeter` ; jamais un dénominateur d'objectif.

### Paquet d'un département ou d'une collectivité d'outre-mer (`GET /api/v1/territoires/{code}`)

```json
{
  "format": 1, "example": true, "code": "69", "name": "Rhône", "outreMer": false, "region": "84", "geography": "2026-01-01", "sources": [ "…" ],
  "communes": [ { "code": "69264", "name": "Villefranche-sur-Saône", "of": "de Villefranche-sur-Saône", "postalCodes": ["69400"], "epci": "200040590", "collectivite": "69D", "population": 1, "populationTotale": 1, "inscrits": 1, "inscritsComplementaires": 1, "computed": [], "formerCodes": [] } ],
  "arrondissements": [ { "code": "69381", "name": "Lyon 1er Arrondissement", "of": "…", "commune": "69123", "postalCodes": ["69001"], "population": 1, "inscrits": 1 } ],
  "epcis": [ { "code": "200040590", "name": "…", "of": "…", "nature": "CA", "departements": ["01", "69"], "communes": ["01194", "…"], "population": 1, "inscrits": 1, "computed": ["population", "inscrits", "inscritsComplementaires"] } ]
}
```

- `collectivite` d'une commune : son code CTCD (ou, outre-mer, celui de la collectivité) ; département et région se déduisent du paquet. `formerCodes` : codes remplacés par fusion depuis 2024. Une commune fusionnée depuis la géographie des chiffres de l'Insee (2025) reçoit la somme de ses anciennes communes (`computed`) ; une commune scindée n'a pas de chiffre.
- `epcis` : ceux qui ont une commune dans le paquet, avec **toutes** leurs communes (aussi d'un autre département) et des totaux calculés sur toutes. `nature` : `CC`, `CA`, `CU`, `METRO`.
- **Index des noms** (`GET /api/v1/territoires/communes`) : `{"format":1,"example":…,"code":"communes","geography":"2026-01-01","communes":[["69123","Lyon","69"],…]}`, un tableau `[code, nom, paquet]` par commune, trié par code.

**Règles communes** : JSON compact, UTF-8, champs dans l'ordre ci-dessus, listes triées : deux imports des mêmes sources donnent les mêmes octets. HTTP comme l'annuaire des élus (section 4 octies) : `ETag: "<sha256>"`, `If-None-Match`, `304`, gzip, `Cache-Control: public, no-cache` ; `404 unknown_package` ; `503 territoires_unavailable` avec `Retry-After: 60` tant qu'aucun import n'a réussi. L'appareil refuse un paquet dont l'empreinte ne correspond pas au niveau national ou qui ne se lit pas comme le paquet demandé ; lecture stricte du document, tolérante des entrées ; 2 Mo au plus pour le niveau national, 8 Mo pour un paquet.

### Sur l'appareil : schéma local en version 12

Migration `migrations/12.sqm` (schéma 12 vers 13), identique à `Territoires.sq` : table `territoire_package(code, sha256, json, built_on, downloaded_at)` : le niveau national quand une copie plus récente que celle de l'application a été vérifiée (`national`), les paquets téléchargés et l'index des noms (`communes`). Jamais envoyée. La copie embarquée est `shared/ui/src/commonMain/composeResources/files/territoires/national.json`, identique à celle que le serveur construit à partir du jeu d'exemple (vérifié par `TerritoiresBundleTest`).

### Sur le serveur : migration Flyway `V7__territoires.sql`

| Table | Rôle |
|---|---|
| `territoires_import` | Un essai d'import par ligne : source (`example`, `official`), statut, dates, nombres pour la garde (`counts` : communes et population de la France ; import refusé au-delà de 2 % et 3 % d'écart), niveau national exact et son empreinte, motif d'échec (code court) |
| `territoires_package` | Les documents d'un import : code, nom, `sha256` et `bytes` du JSON non compressé, corps compressé |

| Variable | Rôle | Défaut |
|---|---|---|
| `PPF_TERRITOIRES_SOURCE` | `example` (jeu embarqué, ne contacte personne) ou `official` (Insee, data.gouv.fr, La Poste), suivi en production, ou en préproduction avec la variable suivante ; ailleurs le jeu d'exemple est servi | `example` |
| `PPF_TERRITOIRES_REFRESH_DAYS` | Jours entre deux imports ; nouvel essai six heures après un échec ; `0` coupe l'import automatique (tests) | 30 |
## 4 octies. Données ouvertes du Parlement (D-064, D-065)

Index : événement NIP-01, kind 30078, `d` = `ppf:parlement-index`, signé par une clé d'import (`OpenDataKeys`) ; `content` = l'index JSON (`example`, `importedOn`, `sources` avec nom, adresse, licence et date, `packages` avec nom, sha256 et taille). Paquets : `membres` (membres et groupes), `scrutins-an-AAAA-MM`, `votes-pe-AAAA-MM` (scrutins avec décomptes, décompte par groupe, positions `pour`, `contre`, `abstention`, `nonVotant`, `delegated`, `corrections` de type `mise-au-point` ou `dysfonctionnement`, `notVotingCauses`). Identifiants préfixés : `an:PA…`, `senat:…`, `pe:…`, scrutins `an:17:<numéro>`, dossiers `an-dossier:DLR5L17N…`. Code de circonscription : département puis numéro sur deux chiffres (`0101`, `2A01`, `97101`), commun avec l'annuaire des élus. Une brique de « Voir » peut porter `ppf-official-id` (personne ou texte). Modèles Kotlin : `core/voir/parlement/ParliamentFormat.kt` ; détail dans `docs/integration/voir-donnees-ouvertes.md`, section 3.

### Historique des votes, pour le comparateur de « Décider » (D-066)

Paquets `historique-an-<législature>` (14 à 17), `historique-senat-<année de session>` (octobre à septembre, depuis 2006), `historique-pe-<législature européenne>` (9 et 10), cités dans le même index signé, avec trois sources de plus (`an-history`, `senat-history`, `pe-history`). Même JSON que les autres paquets (champs inconnus ignorés, valeurs par défaut non écrites). Modèles Kotlin : `core/voir/parlement/ParliamentHistory.kt` (`HistoryPackage`, `HistoryBallot`, `PartyDistribution`, `PartyRef`, `PartyBasis`, `HistoryCoverage`) ; lecture vérifiée : `ParliamentHistory.read(name, json)`, et dans l'application `ParliamentRepository.history(name)` et `downloadHistory()`.

| Champ | Contenu |
|---|---|
| `format`, `example`, `chamber`, `period` | version (1), données fictives, `an`, `senat` ou `pe`, législature ou année de session ; doivent correspondre au nom du paquet |
| `coverage` | `first` et `last` (AAAA-MM-JJ) : premier et dernier jour des scrutins du paquet ; absent pour un paquet vide |
| `parties[]` | `id` (`an:PO…`, `senat-grp:<code>`, `pe-org:<identifiant>`, lignes spéciales `senat-grp:aucun` et `pe-org:aucun`), `label` tel que la source l'écrit, `abbreviation`, `basis` (`parti` ou `groupe`), `basisLabel` pour un groupe (« groupe, rattachement au parti non publié ») ; trié par `id` |
| `ballots[]` | `id` (`an:<lég.>:<numéro>`, `senat:<session>:<numéro>`, `pe:<date>:<numéro du vote>`), `date`, `title` officiel, `kind` (`SPO`, `SPS`, `MOC`, `appel-nominal`), `text`, `dossier` (`an-dossier:…`), `committee` (prévu, vide pour l'instant), `adopted` (absent sans résultat), `counts` (totaux de toute l'assemblée), `parties[]`, `url` ; trié par date puis identifiant |
| `ballots[].parties[]` | `party`, `basis` (toujours écrit), `pour`, `contre`, `abstention`, `nonVotant`, `members` (membres du parti à la date du scrutin) ; trié par `party` |

Parti à la date du vote : Assemblée, parti de rattachement (`PARPOL`, publié depuis décembre 2012) sinon le groupe ; Sénat, toujours le groupe ; Parlement européen, parti national des élus en France. **Aucun nom de personne, aucun vote nominatif, aucune position majoritaire calculée.** Détail : `docs/integration/voir-historique-import.md`.

### Paquet `semaine` : « Cette semaine au Parlement » (D-100)

Cité par le même index signé, réécrit à chaque import (deux imports identiques donnent les mêmes octets). JSON : `format` (1), `example`, `from` et `to` (AAAA-MM-JJ : le jour de l'import, heure de Paris, et le quatorzième jour suivant), `items` triés par heure, assemblée puis identifiant. Chaque point : `id` stable (`an:<uid de la réunion>:<rang du point>` ou `an:<uid>`, `senat:<UID>:<AAAAMMJJHHMM>`, `pe:<séance>:<ordre>`), `chamber` (`an`, `senat`, `pe`), `start` et `end` (heure de Paris, `AAAA-MM-JJTHH:MM`), `kind` (`seance-publique`, `commission`, `autre`), `body` (commission), `title`, `pointKind`, `detail` (2 000 caractères au plus), `dossier` (`an-dossier:DLR5L17N…`) et `dossierUrl`, `status` (`confirme`, `previsionnel`, `annule`, absent quand la source ne dit rien), `url` (page officielle de l'ordre du jour). Jamais de participant, d'organisateur, de contact ni de lieu. Lecture stricte sur le format, tolérante sur les champs inconnus ; un paquet illisible est ignoré. Modèles Kotlin : `core/voir/parlement/ParliamentWeek.kt` ; détail dans `docs/integration/voir-semaine.md`.

## 4 decies. Signature de la plateforme : certificats, révocations, liste de l'équipe (D-129)

Contrat : `docs/integration/service-signature.md` (version 1.2 du 8 novembre 2026). Modèles Kotlin communs aux applications, au serveur et au service de signature : `core/platform/` (`KeyCertificate`, `TeamList`, `PasskeyFingerprint`, `PlatformTrust`, `SigningUse`, `UrnClosures`, `ApprovalChallenge`, `SigningProtocol`).

**Clé racine.** Clé secp256k1 de la plateforme, sur le téléphone de la propriétaire (option A du contrat). Elle ne signe que les deux types ci-dessous : un contenu signé par la racine n'est jamais un contenu de la plateforme. Sa clé publique est la seule constante de confiance (`PlatformRoot.PUBLIC_KEY`) ; elle reste vide tant que la racine n'existe pas, et rien n'est alors reconnu comme contenu de la plateforme (jamais « tout »). La préproduction a sa propre racine, qui ne certifie jamais une clé de la production, et inversement.

**Numéros.** Vérifiés le 7 novembre 2026 dans le tableau « Event Kinds » du README du dépôt officiel des NIP (github.com/nostr-protocol/nips) : aucune NIP n'utilise 37240 ni 37241, ni aucun type de 37000 à 37515 (les plus proches : 35128, NIP-5A ; 37516 et 37517, NIP-CC).

### Certificat d'une clé de travail (type 37240, adressable)

Signé par la racine, `content` vide.

| Étiquette | Contenu | Nombre |
|---|---|---|
| `d` | Clé publique de la clé de travail, hexadécimal minuscule (jamais la racine) | Une |
| `ppf-key-use` | Un usage : `editorial.comprendre`, `editorial.voir`, `revendications.status`, `agir.organisations`, `decider.pacte`, `archives.timestamps`, `opendata.index`, `decider.urn_closure` | Une par usage ; un usage inconnu est ignoré par les applications ; le service de signature n'accepte un certificat `active` que s'il nomme exactement l'usage de la clé, une seule fois |
| `ppf-not-before` | Début de la validité, secondes depuis 1970, en chiffres | Une |
| `ppf-not-after` | Fin de la validité (exclue), 90 jours au plus après le début | Une |
| `ppf-key-status` | `["ppf-key-status","active"]`, ou `["ppf-key-status","revoquee","<date d'effet en secondes>"]` | Une |
| `alt` | Texte montré par les clients Nostr qui ne connaissent pas ce type (NIP-31) | Facultative |

- **Révocation** : le même certificat republié (même `d`) avec le statut `revoquee` ; les contenus datés (`created_at`) de la date d'effet ou après ne sont plus reconnus ; sans date d'effet, plus aucun contenu de cette clé ne l'est.
- **Pour l'usage et la validité, la version la plus récente l'emporte** (`created_at` le plus grand, à égalité le plus petit `id`), comme pour tout événement adressable.
- **Les révocations sont définitives** (8 novembre 2026) : une fois reçue, une révocation signée par la racine est gardée quel que soit l'ordre d'arrivée des événements, même si elle est plus ancienne qu'un certificat `active` déjà reçu ; si plusieurs révocations existent, la date d'effet la plus ancienne l'emporte (sans date : depuis le début ; à égalité, le plus petit `id`) ; un certificat `active` plus récent ne rend jamais confiance à la clé. Les applications gardent donc, pour chaque clé, le dernier certificat **et** la révocation retenue (`PlatformTrust.events`) ; le service les sert ensemble (`GET /v1/certificates`).
- **Lecture qui échoue fermée** : statut inconnu, absent ou en double, dates illisibles ou en double, fin avant le début, validité de plus de 90 jours : la clé est tenue pour révoquée depuis le début, et cette révocation est elle aussi définitive.
- **Révocation de clôture** : au retrait d'une clé de travail, la racine signe une révocation à la date `last_signed_at + 1` (donnée par `GET /v1/keys` : la plus grande des dates de signature et des `created_at` de ce que la clé a signé) ; tout ce que la clé a signé reste valide, rien de ce qu'une copie volée signerait ensuite ne l'est.

Un événement est un **contenu valide de la plateforme pour un usage** (`PlatformTrust.check`) si sa signature est authentique, si sa clé n'est pas la racine, si son type est l'un de ceux de l'usage et s'il a la **forme de l'usage** (`SigningUse.matches`, colonne « Forme commune » ci-dessous), si le dernier certificat de sa clé signé par la racine nomme cet usage, si son `created_at` est dans la validité (début compris, fin exclue) et avant la date d'effet de toute révocation reçue. La forme commune distingue les usages d'un même type : une clé certifiée pour un usage ne valide jamais le contenu d'un autre usage, même si elle est volée ; seuls les deux espaces éditoriaux partagent une forme, et leurs clés les distinguent.

| Usage | Types | Forme commune (applications et service) | Accords | Forme exigée par le service de signature (version 1) |
|---|---|---|---|---|
| `editorial.comprendre`, `editorial.voir` | 30023 | Aucun `ppf-type` d'un autre usage (`organisation`, `pacte`, `engagement`, `petition`, `proposition-finale`) | Un ; deux pour la double validation, que le service exige lui-même pour les contenus de M17 de `editorial.voir` (règle de forme, `docs/integration/service-signature.md`, section 5) | `d`, `title` et `ppf-origin` (`human` ou `ai-assisted`), une fois chacune ; `ppf-type` au plus une fois, jamais `organisation`, `pacte`, `engagement`, `petition` ni `proposition-finale` (types d'autres usages) |
| `agir.organisations` | 30023 | Exactement un `ppf-type` = `organisation` | Un ; deux sur demande | `d` (1 à 64 caractères `a-z0-9._~-`), `title`, `ppf-type` = `organisation`, au plus un `p` hexadécimal, `ppf-org-status` = `verifiee` ou `revoquee` s'il est présent |
| `decider.pacte` | 30023 | Exactement un `ppf-type` : `pacte`, `engagement`, `petition` ou `proposition-finale` | Un ; deux sur demande | `d`, `title`, `ppf-origin`, `ppf-type` = `pacte`, `engagement`, `petition` (pétition officielle relayée, avec le lien vers le site officiel) ou `proposition-finale` (texte final d'une délibération) |
| `revendications.status` | 1985 | Exactement un `L` = `ppf-status` | Un ; deux sur demande | Exactement `["L","ppf-status"]`, `["l","<statut>","ppf-status"]` (statuts de la section 4 bis) et un `a` vers une revendication `30023:<clé>:<d>`, aucune autre étiquette |
| `archives.timestamps` | 1040 | Le type suffit | Aucun | Exactement un `e` (64 caractères hexadécimaux : l'empreinte horodatée) et au plus un `k` (type, en chiffres) ; `content` = la preuve `.ots` en base64 canonique, 8 Kio au plus une fois décodée, qui commence par l'en-tête OpenTimestamps (`\x00OpenTimestamps\x00\x00Proof\x00` puis `bf89e2e884e89294`), la version majeure 1, l'opération SHA-256 du fichier (`0x08`) et les 32 octets de l'empreinte, égaux à la valeur de `e`. **Aucun autre format de preuve n'est accepté pour l'instant** (RFC 3161 compris : un jeton RFC 3161 n'entre pas dans un événement 1040 de NIP-03 ; s'il en faut un, ce sera un autre type et une modification du contrat) |
| `opendata.index` | 30078 | Exactement `d` = `ppf:parlement-index` | Aucun | Exactement `["d","ppf:parlement-index"]` (section 4 octies) ; `content` = un objet JSON |
| `decider.urn_closure` | 30078 | Exactement un `d` qui commence par `ppf:urne-cloture:` | Aucun | Exactement `d` = `ppf:urne-cloture:<n>`, `ppf-urne` = `<n>` et `ppf-closing-sha256` = l'empreinte SHA-256 du document de clôture (`GET /api/v1/urnes/{n}/closing`) ; `content` = un objet JSON ; **jamais une seconde clôture pour le même `d`** |

Les formes des trois usages automatiques sont celles de la version 1 du contrat : « Décider » les confirme avant de brancher l'archivage horodaté et la clôture des urnes, et toute modification est une modification du contrat (section 4). Pour tous les usages : 64 Kio au plus (forme canonique), 1000 étiquettes au plus, `created_at` au plus 5 minutes dans le futur et au plus 8 jours dans le passé (1 jour pour un usage automatique). **La signature prouve que la plateforme a dit le contenu, pas qu'il est vrai** : l'index des données ouvertes et les clôtures d'urnes portent un JSON choisi par le serveur sans relecture humaine, et les applications vérifient tout ce qui peut l'être (empreintes des paquets, empreinte du document de clôture, recomptage quand la méthode le permet).

### Clôture d'une urne : la première l'emporte (pour « Décider »)

Exception à la règle des événements adressables : pour un même `d` = `ppf:urne-cloture:<n>`, l'application garde la **première** clôture signée par la plateforme (le plus petit `created_at`, à égalité le plus petit `id`), jamais la plus récente, et montre toute autre clôture signée par la plateforme pour le même `d` comme un **conflit** (« Deux clôtures signées existent pour cette urne : la première est retenue »). Une clôture plus récente ne remplace jamais la première, qu'elle vienne de la même clé ou de la clé suivante (autre adresse). Code commun : `UrnClosures.choose(événements, confiance)` rend, pour chaque `d`, la clôture retenue et la liste des conflits ; seuls comptent les événements que `PlatformTrust` accepte pour `decider.urn_closure`. Le service de signature, de son côté, refuse définitivement une seconde clôture pour le même `d` (`urn_already_closed`). Le stockage local et le serveur gardent donc **toutes** les clôtures reçues pour un `d` (pas de remplacement par `EventReplacement` pour ce type et ce préfixe), à brancher par « Décider ».

### Liste de l'équipe (type 37241, adressable)

Signée par la racine, `d` = `ppf-equipe`, `content` vide. La plus récente l'emporte ; un membre absent de la dernière liste ne peut plus rien approuver, et ses approbations déjà données ne comptent plus.

| Étiquette | Champs (positionnels) |
|---|---|
| `ppf-not-after` | Fin de validité de la liste (exclue), secondes depuis 1970, au plus 90 jours après son `created_at` (comme un certificat, pour que la propriétaire signe tout en une seule fois) ; une seule |
| `ppf-member` | Identifiant du compte du back-office (1 à 64 caractères `A-Za-z0-9._-`) ; date à partir de laquelle ses accords comptent (secondes) ; clé publique Nostr reliée au compte (hexadécimal) ou champ vide (ou absent) |
| `ppf-member-use` | Identifiant du compte ; un usage qu'il peut approuver (une étiquette par usage) |
| `ppf-credential` | Identifiant du compte ; identifiant de sa clé d'accès WebAuthn ; clé publique COSE de cette clé ; les deux en base64url canonique sans remplissage (une étiquette par clé d'accès) |
| `alt` | Texte montré par les clients Nostr (NIP-31) |

Lecture qui échoue fermée (8 novembre 2026) : une liste sans `ppf-not-after`, avec une fin illisible, en double, avant sa date ou à plus de 90 jours, ou échue, ne permet **aucune** approbation (`team_list_expired`) ; un compte décrit deux fois, dont une ligne `ppf-member` est illisible ou a moins de trois champs, est écarté ; un identifiant de clé d'accès ou une clé COSE qui n'est pas en base64url **canonique** (décoder puis encoder de nouveau doit redonner exactement le même texte : ni `=`, ni bits de fin non nuls) est ignoré ; un identifiant de clé d'accès listé deux fois (comparé sur ses octets) est écarté de tous les comptes ; une **clé COSE listée deux fois**, par le même compte ou par deux comptes, écarte **tous les comptes qui la listent**. Les clés de sécurité physiques des super-administrateurs n'y figurent pas : elles ne servent pas à publier.

**Empreinte d'une clé d'accès** (`PasskeyFingerprint.of`, `TeamCredential.fingerprint`) : les 8 premiers octets du SHA-256 de la clé COSE, en hexadécimal minuscule par groupes de 4 caractères (exemple : la clé de trois octets `abc` donne `ba78 16bf 8f01 cfea`). Montrée par l'écran d'inscription du back-office et par le téléphone de la propriétaire avant de signer la liste.

### Défi d'un accord

Le défi WebAuthn qu'un membre signe pour approuver la signature d'un événement est SHA-256 du texte `ppf-approval-v1`, saut de ligne, usage, saut de ligne, identifiant hexadécimal de l'événement non signé (`ApprovalChallenge`). Exemple : l'usage `editorial.voir` et l'identifiant fait de 64 fois `a` donnent `6191c603b63bb16d1acb87d97834ad0872576eac06bb6027bd13929c38674f9a`. Depuis la version 1.2 du contrat, ce défi est donné au navigateur par le **service de signature lui-même**, sur sa propre page d'approbation (`<PPF_SIGNER_APPROVAL_ORIGIN>/approbation/<usage>/<id>`), jamais par le serveur principal. Le service vérifie l'assertion avec la clé COSE de la liste de l'équipe (jamais avec une clé envoyée dans la requête), son **origine d'approbation seulement** (une assertion faite sur le back-office est refusée), l'identifiant de partie de confiance des clés du back-office (`PPF_SIGNER_RP_ID`) et la vérification de la personne.

### API interne et pages d'approbation du service de signature

Hors API publique. API interne : port 8090 de son conteneur, joignable seulement par le serveur, qui présente `Authorization: Bearer <jeton>` (fichier secret partagé) ; brouillons (`POST /v1/drafts`, `GET /v1/drafts/{usage}/{id}`), signature (`POST /v1/sign`, qui refuse tout corps portant encore une approbation), certificats, liste, journal. Pages d'approbation : port 8091, sur leur propre adresse, servies et vérifiées par le service ; elles reçoivent aussi directement de l'application de la propriétaire les certificats, révocations et listes signés (`POST /racine/certificats`, `POST /racine/liste-equipe`). Requêtes et réponses en JSON (`core/platform/SigningProtocol.kt`) ; détail dans le contrat, section 5.

## 4 undecies. Clé de secours et compte compromis (S-146, D-134)

Conception complète : `docs/integration/compte-compromis.md`. Code commun : `core/identity/AccountRecovery.kt` (constructeurs, lecture et vérifications partagées par les appareils et le serveur) ; serveur : `server/accounts/CompromisedAccounts.kt`.

**Engagement** (type `1776`, forme du brouillon NIP-41, demande de fusion n° 829 non adoptée), signé par la clé du compte à sa création, contenu vide :

| Étiquette | Valeur |
|---|---|
| `p` | Clé publique de secours (hexadécimal), exactement une, différente de la clé du compte |
| `alt` | `pubkey whitelisting event` |
| `ppf-recovery` | `1` (version du mécanisme), exactement une |

Seul le **premier** engagement reçu par le serveur pour une clé compte ; un autre est refusé (`409 recovery_already_set`). Quand l'horodatage OpenTimestamps des engagements sera branché (service d'archives de Décider), le plus ancien selon sa preuve fera foi.

**Avis de compromission** (type `1777`), signé par **la clé de secours** nommée par le premier engagement :

| Étiquette | Valeur |
|---|---|
| `p` | Clé compromise, exactement une |
| `e` | Identifiant de l'engagement `1776` de ce compte, exactement un |
| `proof` | Facultative, au plus une : identifiant de l'événement `1040` (NIP-03) qui porte la preuve d'horodatage de l'engagement |
| `alt` | `pubkey migration event` |
| `ppf-compromised-since` | Date en secondes depuis 1970, exactement une : au plus tôt 30 jours avant `created_at`, au plus tard `created_at` (D-134, décision 2) |
| `ppf-successor` | Facultative, au plus une : clé publique du nouveau compte, différente de la clé compromise et de la clé de secours. C'est une affirmation, jamais une preuve d'identité : un appareil ne suit jamais ce compte sans vérification en personne. |

L'avis est irréversible : un seul par compte (`409 already_compromised`), une clé ne succède qu'à un seul compte (`409 successor_taken`). Le serveur ne garde que les avis qu'il a acceptés : un avis de type `1777` servi par `GET /api/v1/events` est donc valide. Un appareil peut le revérifier lui-même : signature, lien avec l'engagement (`e`), signataire égal à la clé `p` de l'engagement, borne de `ppf-compromised-since`. Les événements de la clé compromise publiés avant l'avis restent servis tels quels ; ceux datés d'après `ppf-compromised-since` sont à marquer « suspects » par l'appareil, qui lit la date dans l'avis ou par `GET /api/v1/accounts/<clé>/status` (section 5). Ni l'engagement ni l'avis ne sont envoyés à un salon (pas d'étiquette `h`).

## 5. API du serveur (version 1)

Toutes les réponses sont en JSON. Aucune information sur l'expéditeur n'est journalisée.

| Requête | Rôle | Réponses |
|---|---|---|
| `POST /api/v1/events`, corps : un événement signé (section 2), 64 Ko au plus, 1000 étiquettes au plus | Publier un contenu. La signature est vérifiée avant toute chose ; sauf les cas décrits dans les lignes suivantes (revendications, salons, espace Agir), les contenus éditoriaux (type 30023) et les descriptions de salon (types 39000 à 39005, avec exactement un identifiant `d`) ne sont acceptés que des clés de l'équipe éditoriale ; les enveloppes chiffrées (type 1059) sont acceptées de toute clé, nomment exactement un destinataire (`p`) et sont stockées sans être ouvertes ; un message de salon (type 9) porte exactement une étiquette `h` et n'est accepté que si le salon existe et est ouvert ; les listes de membres (39002), les drapeaux `private` et `hidden` et, parmi les événements d'adhésion ou de modération (9000 à 9022), tous sauf 9000, 9001, 9002 et 9005 (ligne « partie rôles et modération ») sont refusés. Limitation de débit par clé publique (60 par minute) et globale (600 par minute), jamais par adresse IP. | `201` `{"id":"…","status":"stored"}` ; `200` avec `"duplicate"` (déjà connu) ou `"superseded"` (une version plus récente existe) ; `400` `{"error":"invalid_signature"\|"malformed_event"\|"ephemeral_kind"\|"bad_recipient"\|"missing_group"\|"multiple_groups"\|"too_many_tags"\|"unsupported_kind"\|"unsupported_group_flag", "message":"…"}` ; `403` `not_allowed`, `group_closed` ou `members_list_refused` ; `404` `group_not_found` ; `409` `group_taken` ; `413` trop volumineux ; `429` `rate_limited` avec un en-tête `Retry-After` |
| `POST /api/v1/events`, revendication d'un compte citoyen | Une revendication (type 30023, `ppf-type` = `revendication`) signée par une clé hors de l'équipe éditoriale n'est pas publiée : elle attend la relecture d'une personne. Sa forme doit être exactement celle du formulaire (étiquettes `d`, `title`, `summary`, `published_at`, `ppf-type`, `ppf-origin`, `ppf-status`, `ppf-territory`, `t` ; chacune avec un nom et une valeur ; une seule de chaque sauf `t`, de un à trois thèmes connus et distincts ; `d` en lettres, chiffres et tirets ; aucun caractère de contrôle ; statut `nouvelle`), et elle doit respecter les règles du formulaire. Une nouvelle version (même clé, même `d`) remplace celle qui attend. | `202 {"id":"…","status":"pending_review"}` (aussi pour un renvoi du même événement en attente) ; `200 {"status":"duplicate"}` (déjà publié) ; `200 {"status":"superseded"}` (une version plus récente attend ou est publiée) ; `400 malformed_demand` (forme) ; `400 status_not_allowed` (statut autre que `nouvelle`) ; `400 draft_rules` (règles du formulaire ; le message nomme seulement les règles et les champs, par exemple `too_short (title)`, `personal_data (why)`, `personal_data (identifier)`, jamais ce qui a été trouvé) ; `409 rejected` (cette version a été refusée par l'équipe : en envoyer une corrigée) ; `429 too_many_pending` (10 en attente pour cette clé), `429 queue_full` (1000 en attente sur le serveur), tous deux avec `Retry-After: 3600` ; `429 daily_limit` (5 envois par 24 heures pour cette clé) avec `Retry-After` en secondes |
| `POST /api/v1/events` (partie salons) | Une description de salon (type 39000) est acceptée de toute clé (D-028) si elle porte exactement un `d` (1 à 64 caractères parmi `a-z`, `0-9`, `.`, `_`, `~`, `-`), un `name` contenant au moins une lettre ou un chiffre, un `ppf-territory` de même, `ppf-charter` = `1.0` (première valeur de chaque étiquette), aucun drapeau `private` ni `hidden`, au plus 8 Kio sérialisée, nom et territoire de 100 caractères au plus ; `about` et `picture` sont facultatifs. Un identifiant appartient à la première clé qui l'a décrit ; une nouvelle version par la même clé remplace la description. Un salon ne peut pas porter le même nom qu'un autre sur le même territoire (comparaison sans accents, majuscules ni ponctuation). Les compagnons (39001, 39003, 39004, 39005) ne sont acceptés que des clés éditoriales, pour un salon qu'elles gèrent. Les listes de membres (39002) sont refusées. Un événement envoyé à un salon porte exactement une étiquette `h` désignant un salon existant et ouvert ; un message de salon (type 9) doit en porter une, et il est refusé d'un compte exclu du salon. Une nouvelle version de la description n'est acceptée que de son créateur tant qu'il est administrateur, et jamais avec un autre territoire (D-131). **Garde-fous à la création d'un nouveau salon (D-132)** : au plus 3 salons par clé en 24 heures (créations en attente comprises) ; un nom proche d'un salon du même territoire ou d'un territoire parent (aujourd'hui : « France ») est refusé avec la liste des salons proches, sauf si la description porte `["ppf-similar","confirmed"]` et `["ppf-difference","<en quoi il est différent, 1 à 500 caractères, public>"]` ; un nom proche de celui d'une organisation vérifiée (fiches d'« Agir ») ou d'un salon d'un autre territoire qui compte au moins 200 messages est mis en attente de relecture par l'équipe (file `groups.creations`), sauf pour les clés éditoriales et les clés de l'organisation elle-même ; une clé connue du serveur depuis moins de 7 jours (premier événement reçu) ne peut pas créer un salon au nom d'une organisation vérifiée. Noms « proches » : comparés sans accents ni majuscules ni ponctuation, sans les mots du territoire ni les mots vides (« collectif », « de », « du », « salon », « [EXEMPLE] »…), mêmes mots dans un autre ordre, ou une lettre de différence (5 à 11 lettres), deux au-delà ; pour une organisation, contenir tous ses mots suffit. | `201 stored` ; `202 {"id":"…","status":"held"}` (mis en attente de l'équipe) ; `400` `bad_group_id` \| `unsupported_group_flag` \| `missing_name` \| `missing_territory` \| `missing_charter` \| `description_too_long` \| `missing_group` \| `multiple_groups` \| `unsupported_kind` \| `bad_difference` (confirmation sans différence, ou différence trop longue) \| `territory_fixed` ; `403` `not_allowed` (compagnon publié par une autre clé, nouvelle version d'un créateur qui n'est plus administrateur) \| `members_list_refused` \| `group_closed` \| `banned` (message d'un compte exclu) \| `account_too_recent` \| `organisation_name` (renommage vers le nom d'une organisation vérifiée) ; `404` `group_not_found` ; `409` `{"error":"group_exists","message":"…","existing":"<identifiant du salon à rejoindre>"}` ; `409` `{"error":"group_similar","message":"…","similar":[{"id":"…","name":"…","territory":"…"}]}` (10 au plus) ; `409` `{"error":"group_taken","message":"…"}` (identifiant déjà décrit par une autre clé) ; `429` `too_many_groups` (sans `Retry-After` : réessayer le lendemain) |
| `POST /api/v1/events` (partie rôles et modération, D-131) | Événements NIP-29 acceptés, chacun avec exactement une étiquette `h` désignant un salon existant : **9000** `put-user` (`["p","<clé>"]` ou `["p","<clé>","admin"\|"moderator"]`) nomme un administrateur ou un modérateur, ou, sans rôle, retire le rôle et réadmet un compte exclu ; **9001** `remove-user` (`["p","<clé>"]`, contenu = motif obligatoire de 1 à 1000 caractères) exclut un compte, qui perd son rôle ; **9002** `edit-metadata` (`name` et/ou `about`, aucune autre étiquette) change le nom et la présentation ; **9005** `delete-event` (`["e","<identifiant>"]`, contenu = motif obligatoire) retire un message (type 9) du salon. Qui peut : le créateur (auteur de la description 39000) est le premier administrateur ; un administrateur fait tout ; un modérateur exclut et réadmet un compte sans rôle et retire les messages ; l'auteur retire son propre message ; le dernier administrateur ne peut ni retirer son rôle ni s'exclure. Le serveur applique ces événements un par un et par salon, dans l'ordre où il les reçoit. Les événements acceptés sont publics : `GET /api/v1/events?kind=9000&kind=9001&kind=9002&kind=9005&tag=h:<id>` est le journal de modération du salon. Un message retiré n'est plus servi ; le 9005 reste, avec son motif. Toujours refusés : 9003, 9004, 9006 à 9020 (dont 9007, 9008 suppression du salon et 9009 invitations), 9021, 9022, 39002. | `201 stored` ; `400` `missing_group` \| `multiple_groups` \| `bad_target` \| `bad_role` \| `reason_required` \| `edit_not_allowed` \| `missing_name` \| `description_too_long` \| `not_a_message` ; `403` `not_allowed` \| `organisation_name` ; `404` `group_not_found` \| `event_not_found` ; `409` `last_admin` \| `group_exists` (renommage vers le nom d'un autre salon du territoire) |
| `POST /api/v1/events` (partie Agir) | Mobilisation (type 31923, NIP-52), appel (type 30023, `ppf-type` = `appel`) et fiche d'organisation (type 30023, `ppf-type` = `organisation`) doivent avoir la forme de la section 4 sexies, quelle que soit la clé (équipe comprise). Une clé éditoriale ou une **organisation vérifiée** (clé nommée en `p` d'une fiche d'organisation signée par une clé éditoriale) publie mobilisations et appels directement, dès sa vérification ; une autre clé les voit mis en attente de relecture. Un appel à témoins (`temoins`) est toujours mis en attente ; un appel à collecter des fonds (`collecte`) n'est accepté que d'une organisation vérifiée ou d'une clé éditoriale ; une fiche d'organisation, que d'une clé éditoriale (refusée avant toute lecture de sa forme). Une nouvelle version (même clé, même `d`) remplace celle qui attend. Un contenu approuvé est publié tel qu'il a été signé. Quel que soit le compte, l'auteur peut annuler sa mobilisation (seule différence avec la version publiée : `ppf-event-status` = `annule`) ou clore son appel (seules différences : `ppf-call-status` = `clos` et `ppf-report`, sauf pour un appel à témoins où seul le statut peut changer) sans relecture ; une version de l'auteur encore en attente est alors retirée de la file (motif « Annulé par son auteur avant la relecture »). Une mobilisation en attente dont la date est passée est refusée automatiquement (motif « La date de l'événement est passée avant la relecture. »). Aucune limite sur le nombre de contenus en attente : seule la limitation de débit par minute s'applique. Un contenu mis en attente qui vise un salon (`h`) doit viser un salon existant et ouvert. Une réponse publique à une mobilisation (type 31925, section 4 sexies) est publiée directement, quelle que soit la clé, si sa forme est bonne : elle ne porte aucun texte que l'application affiche. | `201 stored` (publié, y compris une annulation ou une clôture) ; `202 {"id":"…","status":"pending_review"}` (mis en attente, aussi pour un renvoi du même événement en attente) ; `200 {"id":"…","status":"withdrawn"}` (l'annulation ou la clôture ne portait que sur une version en attente, jamais publiée : celle-ci est retirée de la file et rien n'est publié) ; `200 duplicate` (déjà publié) ; `200 superseded` (une version plus récente attend ou est publiée) ; `400` `missing_title` \| `missing_start` \| `bad_end` \| `missing_location` \| `bad_event_type` \| `bad_call_type` \| `missing_territory` \| `missing_charter` \| `missing_field` \| `too_long` \| `personal_data` (appel portant une coordonnée personnelle ; le message nomme le champ, jamais ce qui a été trouvé) \| `malformed_event` (caractère de contrôle) ; pour `missing_field` et `too_long`, le message est exactement le nom de l'étiquette en cause (`ppf-object`, `location\|r`, `content`…), qu'elle soit absente ou illisible ; `403 not_allowed` (collecte hors organisation vérifiée et équipe éditoriale, fiche d'organisation hors équipe éditoriale) ; `404 group_not_found` ; `409 rejected` (cette version a été refusée : en envoyer une corrigée) ; `429 rate_limited` (limitation de débit générale, avec `Retry-After`) |
| `GET /api/v1/held?id=<hex>&id=<hex>` | Où en sont les contenus de l'espace Agir mis en attente par leur auteur : de 1 à 20 identifiants d'événements (64 caractères hexadécimaux minuscules). Réponse dans l'ordre demandé, doublons retirés : `[{"id":"…","status":"pending","received_at":1793966400,"decided_at":null,"reason":null}]` (format `HeldState` du cœur). `status` : `pending`, `approved` ou `rejected` ; dates en secondes ; `reason` : motif d'un refus (par l'équipe, ou l'un des deux motifs fixes : date passée, retrait par l'auteur). Une mobilisation en attente dont la date est passée est refusée au moment de cette lecture. Un identifiant inconnu (jamais mis en attente, ou effacé 30 jours après la décision) est absent. Jamais la clé ni le contenu ; `Cache-Control: no-store` ; pas d'authentification (tant qu'un contenu attend, seul son auteur connaît son identifiant). | `200` liste ; `400 bad_parameter` |
| `GET /api/v1/agenda?from=<s>&to=<s>&territory=<texte>&type=<type>&organizer=<hex>&limit=100` | Les mobilisations publiées qui **chevauchent** la période (début au plus tard `to` ; fin, ou début s'il n'y a pas de fin, au plus tôt `from`), triées par début puis identifiant : tableau d'événements signés. Par défaut, de maintenant moins 12 heures à maintenant plus 90 jours. `from` ≤ `to`, entiers positifs ; `territory` comparé en entier une fois normalisé comme pour les salons (accents, majuscules et ponctuation ignorés ; une valeur sans lettre ni chiffre ne filtre pas ; 200 caractères au plus) ; `type` (parmi les types de mobilisation) et `organizer` (clé hexadécimale : l'auteur, ou une clé nommée organisatrice par `["p","<clé>","","organizer"]`) répétables comme alternatives, 20 valeurs au plus ; `limit` de 1 à 500. Les autres paramètres au plus une fois. Les mobilisations annulées restent listées, avec leur statut. Sert au site et aux abonnements de calendrier : l'application, elle, synchronise par `GET /api/v1/events?kind=31923` et filtre sur l'appareil. | `200` tableau ; `400 bad_parameter` |
| `GET /api/v1/agenda.ics` (mêmes paramètres) | La même sélection en iCalendar (section 4 sexies), `text/calendar; charset=utf-8`, `Content-Disposition: inline; filename="agenda-ppf.ics"`, `X-WR-CALNAME:PPF : agenda des mobilisations` ; `STATUS:CANCELLED` pour une mobilisation annulée. Un logiciel d'agenda peut s'abonner à cette adresse. | `200` ; `400 bad_parameter` |
| `GET /api/v1/revendications/proposals?id=<hex>&id=<hex>` | Où en sont les propositions de l'auteur : de 1 à 20 identifiants d'événements (64 caractères hexadécimaux minuscules, `id` répété). Réponse : liste au format `ProposalState` du cœur, dans l'ordre demandé, `[{"id":"…","status":"pending","received_at":1793966400,"decided_at":null,"reason":null,"report":{…}}]`. `status` : `pending`, `approved` ou `rejected` ; dates en secondes depuis 1970 ; `reason` : motif de l'équipe pour un refus ; `report` : le `ProposalReport` des vérifications automatiques (`null` tant qu'elles n'ont pas tourné). Le rapport ne nomme jamais une revendication non publiée et montre les revendications publiées telles qu'elles se lisent aujourd'hui. Une proposition approuvée ne sert plus de rapport (son identifiant est devenu public). Une revendication publiée sans proposition (supprimée 30 jours après l'approbation, ou publiée directement par l'équipe) répond `approved`, `received_at` étant sa date d'arrivée dans la table des événements, les autres champs à `null`. Un identifiant inconnu est simplement absent. Jamais le contenu de l'événement ni la clé de son auteur ; en-tête `Cache-Control: no-store`. Pas d'authentification : tant qu'une proposition attend, seul son auteur connaît l'identifiant de son événement. | `200` liste ; `400 bad_parameter` (aucun identifiant, plus de 20, ou mal formé) ; `503 database_unavailable` |
| `GET /api/v1/events?kind=1&kind=30023&author=<hex>&identifier=<d>&tag=<nom>:<valeur>&since=<s>&until=<s>&limit=50` | Lister les événements, du plus récent au plus ancien ; tous les paramètres sont facultatifs et répétables sauf `since`, `until`, `limit` (1 à 500). `tag` filtre par étiquette (20 filtres au plus, `identifier` compris, qui équivaut à `tag=d:…`) : le premier deux-points sépare le nom de la valeur, qui peut elle-même en contenir ; plusieurs `tag` du même nom : l'un ou l'autre ; de noms différents : tous ; nom de 1 à 64 caractères, valeur de 1024 octets au plus. Exemples : boîte de réception `kind=1059&tag=p:<hex>`, messages d'un salon `kind=9&tag=h:<id>` | `200` tableau d'événements ; `400` `bad_parameter` |
| `GET /api/v1/events/<id>` | Un événement par son identifiant | `200` l'événement ; `404` `not_found` |
| `POST /api/v1/events` (partie clé de secours, S-146, D-134) | Un engagement (type `1776`) ou un avis de compromission (type `1777`) de la section 4 undecies. Le serveur vérifie la forme et la signature, puis, pour un avis, qu'il est signé par la clé de secours du premier engagement du compte, qu'il nomme cet engagement, que `ppf-compromised-since` est dans ses bornes et que l'avis n'est pas daté de plus de 10 minutes dans le futur. Après un avis accepté : **tout** nouvel événement de la clé compromise est refusé, quel que soit son type ; ses inscriptions aux réveils de sa boîte (M31) sont effacées et refusées ensuite ; ses rôles dans les salons sont retirés (le salon et son journal restent). | `201 stored` ; `200 duplicate` ; `400` `invalid_commitment` \| `invalid_notice` \| `unsupported_tag` (étiquette `h`) ; `403 account_compromised` (tout événement d'une clé déclarée compromise, et `POST /api/v1/push/subscriptions` de la portée `mailbox`) ; `409` `recovery_already_set` \| `already_compromised` \| `successor_taken` |
| `GET /api/v1/accounts/<clé>/status` | Où en est la clé de secours d'un compte (clé hexadécimale minuscule) : `{"compromised":true,"since":1793966400,"successor":"…"\|null,"notice":{…avis 1777 signé…}\|null,"commitment":"<identifiant du premier engagement>"\|null}` ; pour un compte non compromis, `compromised` vaut `false` et `since`, `successor`, `notice` `null` ; `commitment` `null` signale un compte sans clé de secours (« Votre compte n'a pas encore de clé de secours »). `Cache-Control: no-store`. Pour les clés de ses contacts, l'appareil lit plutôt les avis par petits lots mélangés (`GET /api/v1/events?kind=1777&tag=p:<clé>&tag=p:<clé>…`), pour que le serveur n'apprenne pas sa liste. | `200` ; `400 bad_parameter` |
| `GET /api/v1/groups?q=<texte>&territory=<texte>&limit=100` | Les salons connus du serveur (NIP-29), triés par nom (sans accents ni majuscules) puis identifiant : `[{"id":"…","name":"…","about":"…","picture":null,"territory":"Lyon","charter":"1.0","public":true,"open":true,"event":{…événement 39000 signé…}}]`. Tous les paramètres sont facultatifs, chacun au plus une fois. `q` : garde les salons dont le nom, le territoire ou la description contient le texte ; `territory` : garde les salons de ce territoire, comparé en entier ; les deux sont comparés une fois normalisés (accents, majuscules et ponctuation ignorés ; une valeur vide ou sans lettre ni chiffre ne filtre pas), 200 caractères au plus. `limit` : de 1 à 500, 100 par défaut. `territory` vaut l'étiquette `ppf-territory` telle qu'écrite par le créateur ; `charter` la version de la charte acceptée (`null` pour une description antérieure à D-028). `public` : lisible par tous (toujours vrai dans cette version) ; `open` : tout compte peut y écrire. Aucune liste de membres n'existe ni n'est servie. | `200` tableau ; `400` `bad_parameter` (`limit` hors de 1 à 500 ou non entier, paramètre répété, texte trop long) |
| `GET /api/v1/groups/<id>` | Un salon par son identifiant (1 à 64 caractères parmi `a-z`, `0-9`, `.`, `_`, `~`, `-`) ; `name` et `about` sont ceux en vigueur après les modifications de ses administrateurs, dont la dernière est servie dans `edit` (événement 9002 signé, `null` sinon ; aussi dans la liste) | `200` le salon ; `400` `bad_parameter` ; `404` `group_not_found` |
| `GET /api/v1/groups/<id>/roles` | Les rôles en vigueur d'un salon (D-131) : `{"group":"…","roles":[{"pubkey":"…","role":"admin"\|"moderator","granted_by":"…","granted_at":<s>}],"banned":<nombre de comptes exclus>}`. Aucune liste de membres. | `200` ; `400` `bad_parameter` ; `404` `group_not_found` |
| `GET /api/v1/group-creations?id=<identifiant>&id=…` | Où en sont les créations de salon mises en attente (D-132), pour leur auteur : `[{"id":"…","status":"pending"\|"approved"\|"rejected","received_at":<s>,"decided_at":<s>\|null,"reason":"…"\|null}]` (forme de `GET /api/v1/held`) ; un identifiant inconnu est absent (effacé 30 jours après la décision). 20 identifiants au plus. | `200` ; `400` `bad_parameter` |
| `GET /api/v1/elus` | L'index de l'annuaire des élus (section 4 octies) : source, date d'import, un paquet par département avec son empreinte, sa taille et ses codes postaux. Sans compte ; `ETag` entre guillemets, `If-None-Match`, gzip | `200` ; `304` ; `503 elus_unavailable` avec `Retry-After: 60` tant qu'aucun import n'a réussi |
| `GET /api/v1/elus/{code}` | Le paquet d'un département, d'une collectivité d'outre-mer ou `FR` (section 4 octies). Aucune commune ni aucun code postal n'est jamais envoyé par l'appareil | `200` ; `304` ; `404 unknown_package` ; `503 elus_unavailable` |
| `GET /api/v1/territoires` | Le niveau national du référentiel des territoires (section 4 nonies) : régions, départements, collectivités, outre-mer avec population et inscrits, et l'index des paquets. Sans compte ; `ETag`, `If-None-Match`, gzip | `200` ; `304` ; `503 territoires_unavailable` avec `Retry-After: 60` tant qu'aucun import n'a réussi |
| `GET /api/v1/territoires/{code}` | Le paquet d'un département ou d'une collectivité d'outre-mer, ou `communes` (index des noms). Aucune commune, aucun code postal, aucune recherche n'est envoyé par l'appareil | `200` ; `304` ; `404 unknown_package` ; `503 territoires_unavailable` |
| `GET /api/v1/transparence/requisitions` | Transparence du registre légal (D-077) : le nombre de réquisitions judiciaires auxquelles PPF a répondu, par mois (temps universel, `YYYY-MM`, les mois sans réquisition sont absents), jamais une référence, une clé, un contenu ni une date plus fine. Corps : `{"format":1,"months":[{"month":"2026-11","answered":1}],"total":1}` | `200` ; `503 database_unavailable` sans base |
| `GET /health` | État du serveur | `200` `{"status":"ok","service":"ppf-server","version":"0.1.0","environment":"…"}` |

Quand la base de données n'est pas configurée, toute adresse sous `/api/v1/` répond `503` `{"error":"database_unavailable"}`.

Les événements remplaçables et adressables (section 3) ne sont conservés qu'en une version : la plus récente, et à date égale celle dont l'identifiant est le plus petit. L'application et le serveur appliquent la même règle.

**Messagerie et salons (D-027, D-028).** Les messages privés transitent sous forme d'enveloppes NIP-59 (type 1059) adressées par une étiquette `p` ; le serveur ne les ouvre jamais, ne tient aucune liste de conversations ni de contacts, garde au plus `PPF_WRAP_MAX_PER_RECIPIENT` enveloppes par destinataire (5000 par défaut, les plus récentes) et les supprime après la durée choisie par le destinataire (réglage de la boîte, section 4 ter), entre 90 et 365 jours par défaut, comptée depuis leur réception, date qu'il connaît seul (`received_at`, car `created_at` est volontairement brouillé par NIP-59). Les salons suivent NIP-29 : description adressable de type 39000 (`d` = identifiant, étiquettes `name`, `about`, `picture`, `ppf-territory`, `ppf-charter`, drapeaux `closed` et `restricted`), messages de type 9 portant l'étiquette `h`. Tout compte peut créer un salon ; un nom ne sert qu'une fois par territoire.

**Revendications citoyennes (relecture avant publication).** Une revendication proposée par un citoyen attend dans une file la décision d'une personne de l'équipe ; elle n'est publiée (copiée dans la table des événements, telle que son auteur l'a signée) qu'après approbation. Le serveur vérifie de nouveau, à la réception, la forme de l'événement et les règles du formulaire, puis lance en arrière-plan les vérifications automatiques (doublons parmi les revendications publiées et en attente, modèle d'IA s'il est configuré), dont le rapport sert à l'équipe et à l'auteur. Ces vérifications ne bloquent ni ne publient jamais rien : seules les règles déterministes du formulaire peuvent refuser un envoi.

**Espace Agir (relecture avant publication, D-043).** Les mobilisations et appels des comptes qui ne sont ni de l'équipe éditoriale ni des organisations vérifiées attendent dans une file (`held_content`) la décision d'une personne de l'équipe, comme les revendications ; les appels à témoins y passent toujours. Un contenu approuvé est copié dans la table des événements tel que son auteur l'a signé, puis servi par `GET /api/v1/events` comme tout contenu. L'auteur peut toujours annuler sa mobilisation ou clore son appel sans relecture. Une mobilisation dont la date passe avant la relecture est refusée automatiquement. La file n'a pas de plafond (seule la limitation de débit par minute de l'API s'applique) et ne garde rien de la requête ; un contenu décidé en est effacé 30 jours après la décision. L'appareil synchronise l'agenda par `GET /api/v1/events?kind=31923`, les appels par `kind=30023&tag=ppf-type:appel` et l'annuaire par `kind=30023&tag=ppf-type:organisation`, et calcule lui-même les filtres, dont « près de chez moi » ; `GET /api/v1/agenda` sert au site et aux abonnements de calendrier.

**Page `/agenda` du site** (hors API) : `GET /agenda?territory=<texte>&type=<type>` rend en HTML, sans script, les mobilisations de la période par défaut (200 au plus), avec les dates en français dans le fuseau de l'événement (Paris par défaut), le statut « Annulé » ou « Reporté » en tête du titre, les liens extérieurs `http(s)` seulement (marqués `rel="noopener noreferrer nofollow external"`) et un lien d'abonnement au flux iCal de la même sélection. Le filtre passe par un formulaire envoyé en GET, seul moyen de filtrer sans script : le territoire choisi apparaît donc dans l'adresse de la page (le serveur ne journalise aucune requête). Un type inconnu est ignoré ; la page répond toujours.

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

Variables d'environnement du serveur pour l'espace Agir :

| Variable | Rôle | Défaut |
|---|---|---|
| `PPF_HELD_RETENTION_DAYS` | Conservation d'un contenu décidé dans la file de relecture, après la décision | 30 |
| `PPF_HELD_CLEANUP_INTERVAL_MINUTES` | Fréquence du ménage de la file : refus des mobilisations dont la date est passée, effacement des contenus décidés expirés (0 désactive la tâche, pour les tests) | 60 |

Les valeurs invalides reprennent leur valeur par défaut. `PPF_EDITOR_KEYS` sert aussi à reconnaître les fiches d'organisation qui vérifient une clé. Aucune variable ne limite le nombre de contenus en attente (décision de la propriétaire du 7 novembre 2026) ; la limitation de débit de `POST /api/v1/events` reste réglée par `PPF_POST_RATE_PER_MINUTE` et `PPF_POST_GLOBAL_RATE_PER_MINUTE`.
