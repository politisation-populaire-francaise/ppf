# « Et après ? » : les suites sourcées d'une mobilisation (AG-62, étape 1)

Rédigé le 9 novembre 2026 par le pôle « Agir ». **Rien n'est encore compilé ni signé** : les compilations de branche sont interdites en ce moment sur la machine de la propriétaire. Le code et les tests sont écrits pour compiler du premier coup (chaque API appelée a été relue dans le code), mais ils n'ont pas tourné. `suites.json` n'existe pas encore : la session qui fusionne lance l'outil de signature (section 8).

- Source : cahier fonctionnel, ligne 367 : « Les suites sourcées d'une mobilisation passée (réponses des élus, annonces, reculs, textes déposés), reliées aux revendications, aux appels et à la chronologie (VO-06, M4). » Dépendances : « Surtout éditorial ; pôle Voir pour la chronologie ».
- Méthode suivie (cahier des charges, section 9.1, et M17) : sources primaires, distinction visible entre établi, allégué et en cours d'enquête, droit de réponse des personnes nommées.
- Aucune migration, aucune nouvelle dépendance, aucun appel au serveur.

## 1. Ce que voit la personne

Sur le détail d'une mobilisation **passée** de l'agenda (onglet « Passés », ou la carte), sous la description, une section « Et après ? » :

- une phrase qui dit de quoi il s'agit (« Ce qui a suivi cette mobilisation, sources à l'appui : réponses des élus, annonces, reculs, textes déposés. ») ;
- les suites, **de la plus ancienne à la plus récente** (ce qui s'est passé d'abord vient d'abord), chacune dans une carte : le type en mots (« Réponse d'un élu », « Annonce », « Recul », « Texte déposé », « Décision de justice », « Autre suite ») avec une icône décorative, le badge [EXEMPLE] s'il y a lieu, **le statut en mots** avec les mêmes mots et les mêmes couleurs que « Voir » (« Établi », « Allégué », « En cours d'enquête » ; jamais une couleur seule), la date (« Le 6 novembre 2026 »), le titre, le résumé, le texte, l'origine (« Rédigé par un humain » ou « Produit avec l'aide d'une IA ») ;
- les liens **connus sur l'appareil** : « Personne concernée : … », « Revendication : … », « Appel : … », « Chronologie de « Voir » : … », avec le titre du contenu tel qu'il est publié ; un lien dont le contenu n'est pas sur l'appareil n'est pas montré ;
- le droit de réponse : une réponse publiée (brique `reponse` de « Voir » dont la cible est la suite) s'affiche sous la suite (« Droit de réponse de … ») ; tant qu'il n'y en a pas, une suite qui nomme une personne rappelle qu'elle peut demander un droit de réponse à l'équipe de rédaction depuis sa fiche dans « Voir » ;
- les sources, chacune avec son type, son nom, sa date et le nom du site ; un geste ouvre d'abord l'avertissement « Vous quittez PPF » de l'agenda, le choix prudent en premier (« Rester sur PPF »).

Rien pour une mobilisation à venir. Une phrase quand aucune suite n'est connue : « Aucune suite n'est encore connue pour cette mobilisation. L'équipe de rédaction les ajoute quand elles sont vérifiées. » La même phrase si `suites.json` est absent ou illisible : rien ne plante.

Lecture seule à l'étape 1 : la personne ne propose pas de suite (voir les questions, section 10).

Adaptatif : la section suit la colonne du détail (720 dp au plus), en une colonne sur téléphone et tablette, dans le volet de droite sur ordinateur ; clair et sombre par les jetons de `PpfTheme` ; titres marqués pour les lecteurs d'écran, liens lus comme une phrase, boutons des sources de 44 dp au moins.

## 2. Le format : un contenu éditorial `ppf-type` = `suite`

Une suite est un contenu éditorial NIP-23 (type `30023`), signé par une clé éditoriale comme les autres contenus intégrés (fiches, « Voir », solidarité). Étiquettes communes de la section 4 bis de `docs/FORMATS.md` (`d`, `title`, `summary` obligatoire, `published_at`, `t`, `ppf-type` = `suite`, `ppf-origin`), puis, dans l'ordre de l'en-tête du fichier :

| Clé de l'en-tête | Étiquette | Nombre | Contenu |
|---|---|---|---|
| `mobilization` | `["a", "31923:<clé>:<d>", "", "mobilisation"]` | 1 à 20 | Adresse complète de la mobilisation suivie, quel que soit son auteur |
| `suite-kind` | `ppf-suite-kind` | 1 | `reponse-elu`, `annonce`, `recul`, `texte-depose`, `decision-justice` ou `autre` |
| `date` | `ppf-date` | 1 | Date de la suite, `AAAA-MM-JJ` (même étiquette que les faits de « Voir ») |
| `fact-status` | `ppf-fact-status` | 1 | `etabli`, `allegue` ou `enquete` : le vocabulaire des faits de « Voir » (`FactStatus`) |
| `source` | `ppf-source` | au moins 1 | « type \| nom \| adresse \| date » : la convention de « Voir » ; types de source de « Voir » ; **adresse `https://` obligatoire** ; date facultative |
| `person` | `["a", "30023:<clé>:<d>", "", "personne"]` | 0 à 20 | Fiche d'une personne publique de « Voir » ; **obligatoire pour une réponse d'élu** |
| `demand` | `["a", …, "", "revendication"]` | 0 à 20 | Une revendication du mur |
| `call` | `["a", …, "", "appel"]` | 0 à 20 | Un appel d'« Agir » |
| `chronology` | `["a", …, "", "chronologie"]` | 0 à 20 | Un fait de la chronologie de « Voir » (VO-06) |
| `correction` | `ppf-correction` | 0 ou plus | « date \| résumé public \| nature » (`correction` ou `mise-a-jour`), le journal public des corrections |

Le corps est un texte simple (pas de Markdown), 4 000 caractères au plus ; l'application l'affiche tel quel, sous le résumé.

**Pourquoi des adresses complètes** dans les liens, et non un identifiant seul comme les liens de « Voir » (`30023:<clé de l'espace>:<identifiant>`) : aucune cible n'est un contenu de cet espace sous sa clé. Une mobilisation (type 31923) et une revendication ou un appel proposés par un citoyen sont signés par leur auteur ; une brique de « Voir » l'est par la clé de « Voir » (usage `editorial.voir` du service de signature, D-095). Le moteur éditorial apprend donc ces liens (section 3). Le rôle en quatrième position suit la convention de « Voir » (`["a", adresse, "", rôle]`) ; un client Nostr y voit des étiquettes `a` ordinaires (NIP-01), filtrables par `#a`.

**Lecture** (`core/agir/followups/FollowUp.kt`, `FollowUp.from`) : une suite sans type connu, sans date valide, sans statut connu, sans aucune mobilisation de type 31923 ou sans aucune source qui ait son adresse n'est pas montrée ; une source sans adresse est écartée ; seules les suites d'une clé éditoriale de confiance sont montrées (`EditorialTrust`), et seulement authentiques quand elles arrivent (`verify`). Pour une même adresse, la version la plus récente l'emporte (à date égale, le plus petit identifiant), comme partout.

## 3. Le moteur éditorial (cœur commun de « Comprendre » et « Voir »)

Comme « Voir » à l'étape 2 bis (`docs/integration/voir-schemas.md`), le type est décrit par un schéma déclaratif, ajouté à `EditorialSchemas.all` :

- `core/agir/followups/FollowUpSchema.kt` : le schéma `SUITE` (champs, règles, vérifications) ; `core/agir/AgirSchemas.kt` les rassemble pour l'espace.
- **Fichiers communs modifiés** (à relire par « Comprendre », qui tient le moteur, et par « Voir ») :
  - `ArticleType.SUITE("suite")` dans `core/editorial/Article.kt` ;
  - `EditorialSpace.AGIR("agir")` dans `ContentSchema.kt` : schémas seulement ; aucune section ni file du back-office n'est enregistrée pour cet espace, et la colonne `editorial_draft.space` du serveur n'accepte que `comprendre` et `voir` (migration `V11`) ;
  - `FieldSpec.addressRole` dans `ContentSchema.kt` (paramètre facultatif, à la fin, valeur par défaut `null` : aucun schéma existant ne change) : avec l'étiquette `a` et une valeur simple, le moteur écrit `["a", <valeur>, "", <rôle>]`, et `draftOf` relit l'étiquette par son rôle ; deux lignes dans `EditorialEngine.kt` (`tagsOf`, `fieldOfTag`) ;
  - sept textes français `agir.rule.suite_*` dans `EditorialMessages.kt` ;
  - `ArticleType.SUITE` dans le `when` complet de `typeLabel` de `ui/content/ComprendreScreen.kt` (une ligne, texte « Suite d'une mobilisation »), sans quoi l'interface ne compilerait plus.
- **Ce que le moteur refuse** : un champ inconnu ; une suite sans source ; une source sans adresse, ou en `http://`, ou d'un type inconnu ; un type de suite ou un statut inconnus ; une suite sans mobilisation ; une mobilisation qui n'est pas une adresse de l'agenda (type 31923, identifiant `a-z0-9._~-` de 64 caractères au plus) ; un lien vers un contenu qui n'est pas une adresse `30023:<clé>:<identifiant>`, ou qui, connu de la vérification, n'a pas le type attendu (fiche d'une personne, revendication, appel, fait) ; une réponse d'élu sans sa personne ; deux fois le même lien ; toute ligne que l'application ignorerait (comptée champ par champ).
- **Approbations** : le schéma déclare deux approbations (`EditorialApprovals.M17`), par prudence, parce qu'une suite nomme souvent un responsable public (voir la décision et la question 2).

## 4. Contenus intégrés et outil de signature

- `content/suites/` : trois suites **[EXEMPLE]** de la projection-débat de Rennes du 3 novembre 2026 (`content/agenda/2026-11-03-projection-debat-rennes.md`), la seule mobilisation d'exemple déjà passée : une annonce **alléguée** (un seul article de presse fictif, le 4 novembre, liée au 49.3 de la chronologie d'exemple de « Voir »), la réponse **établie** d'une députée fictive (6 novembre, liée à sa fiche `camille-exemple` et à la revendication `transparence-agenda-elus`), une proposition de loi fictive déposée (9 novembre). Personnes, organisations et documents fictifs ; adresses des sources en `example.org` ; `origin: ai-assisted`. Ce sont des exemples parce que l'agenda reste fait d'exemples (décision de la propriétaire du 9 novembre 2026 : de vrais contenus là où c'est possible, mais l'agenda, les appels, les organisations et la solidarité restent des exemples) ; les suites d'un exemple sont donc des exemples, et l'outil le vérifie.
- `tools/content-signer/.../FollowUpContent.kt` : chaque fichier de `content/suites` est vérifié par le moteur puis signé dans `suites.json`. Dans ces fichiers, un lien s'écrit avec un préfixe et l'identifiant d'un fichier de `content/` : `agenda:<slug>`, `revendication:<slug>`, `appel:<slug>`, `voir:<slug>` (un fait de la chronologie pour `chronology`, une personne pour `person`) ; l'outil écrit l'adresse complète sous sa propre clé, qui signe aussi l'agenda et les autres exemples. Il refuse en plus : un identifiant qui n'existe pas ou du mauvais genre (un fait qui n'est pas de la chronologie), une adresse écrite à la main, une suite datée d'avant la mobilisation suivie, un titre sans [EXEMPLE], une source hors des domaines réservés aux exemples (`example.org`, `example.com`, `example.net`, `.invalid`). Comme l'agenda et la solidarité, ce bloc reste hors de `prepareBundles` : le test de non-régression n'est pas touché tant que `suites.json` n'est pas signé.
- `content/README.md` documente le format.

## 5. Dans l'application

- `ui/agir/followups/FollowUpLibrary.kt` : à la première ouverture d'une mobilisation passée, importe une fois `suites.json` (s'il existe) dans la copie locale des contenus, clés de confiance et signatures vérifiées seulement ; cherche ensuite sur l'appareil les suites qui portent l'adresse de la mobilisation (`a`), puis le titre des contenus liés (par leur `d` et leur auteur) et les droits de réponse qui visent chaque suite. Sans base locale, les suites du paquet restent en mémoire, sans liens.
- `ui/agir/followups/FollowUpsSection.kt` : la section (`MobilizationFollowUps`, `FollowUpsSection`), ses étiquettes de test (`FollowUpTags`), ses textes (`composeResources/values/strings_suites.xml`). Elle reprend les libellés et les couleurs du statut et des types de source de « Voir » (`FactStatus.label`, `FactStatus.badgeStyle`, `SourceKind.label`, lus sans être modifiés) et l'avertissement « Vous quittez PPF » de l'agenda (`SafeChoiceDialog`, textes `agenda_leave_*`).
- **Deux lignes dans les fichiers de l'agenda** : `AgendaLibrary.followUps` (la bibliothèque des suites, faite avec la base et la confiance de l'agenda) et, dans `MobilizationDetail.kt`, l'appel `MobilizationFollowUps(mobilization, agenda, openLink)` sous la description, loin des actions du détail (où l'agent d'AG-61 ajoute « Faire une affiche »).
- **Vie privée** : rien n'est envoyé. Le serveur ne sait pas quelle mobilisation est regardée : les suites sont cherchées sur l'appareil. Quand elles viendront du serveur (étape 2), elles seront tirées par type, toutes (`kind=30023`, `ppf-type=suite`), jamais par mobilisation, comme les caisses et les missions. Aucune donnée personnelle n'est stockée : seulement des contenus publics dans la copie locale déjà existante ; rien à ajouter à l'effacement rapide ni au registre.

## 6. Lien avec la chronologie de « Voir » et le droit de réponse

- **Chronologie (VO-06)** : un lien `chronologie` vise un fait de « Voir » d'un type de la chronologie (`mobilisation`, `declaration-officielle`, `decision-officielle`, `reponse-gouvernement`, `FactKind.inChronology`). Une suite ne remplace pas un fait : elle dit ce qui a suivi **une** mobilisation de l'agenda ; le fait dit ce qui s'est passé dans le mouvement, sur la chronologie commune. Quand une suite est aussi un événement du mouvement (un recul du gouvernement), l'équipe publie le fait dans « Voir » et la suite le relie. Rien n'est encore montré du côté de « Voir » (question 4).
- **Droit de réponse (M17, VO-21, `docs/integration/voir-demandes.md`)** : une suite qui nomme un responsable public relie sa fiche de « Voir » (`person`, obligatoire pour une réponse d'élu). La personne demande un droit de réponse par le formulaire chiffré de « Voir », depuis sa fiche. Une réponse publiée est une brique `reponse` dont la cible (`target`) est la suite ; l'application la montre déjà sous la suite si elle existe. **Mais le schéma `reponse` de « Voir » n'accepte aujourd'hui qu'une cible de « Voir »** (`TargetType.VOIR_BLOCK`) : publier un droit de réponse à une suite demande d'y ajouter `suite` (question 3). Rien n'est changé dans les schémas de « Voir ».

## 7. Serveur et site

Rien à l'étape 1. Le circuit éditorial n'a pas besoin du type pour l'outil de signature. Ce qu'il faudra pour publier des suites depuis le back-office (étape 2) : une migration qui ajoute `agir` à la contrainte de `editorial_draft.space` (numéro à prendre dans le registre de l'équipe), une section `EditorialDraftsSection(EditorialSpace.AGIR)` et sa file, un usage de signature (aujourd'hui l'espace « Agir » prendrait celui de « Comprendre » par défaut, `Application.kt` et `EditorialPublisher.use`), un aperçu, les libellés `agir.field.suite_*` et `agir.help.suite_*` dans `EditorialTexts`, et la règle de publication du serveur pour ce type. Le site (`/agenda`) pourra ensuite montrer la même section sans JavaScript.

## 8. À faire au moment de compiler (session qui fusionne)

1. Compiler et lancer les tests : `gradlew :shared:core:jvmTest --tests "*FollowUpSchemaTest*"`, `gradlew :tools:content-signer:test --tests "*FollowUpContentTest*"` (et `NoRegressionTest`, qui ne doit pas bouger), `gradlew :shared:ui:jvmTest --tests "*FollowUpsTest*"` puis les tests de l'agenda (`AgendaScreenTest`, `AgendaLibraryTest`) et de « Comprendre » (`ComprendreSchemasTest`, `VoirSchemasTest`, `EditorialEngineTest`, `EditorialApprovalsTest`), puis `gradlew build`.
2. Lancer l'outil de signature : `gradlew :tools:content-signer:run`. Il réécrit tous les paquets avec de nouvelles signatures : **ne garder que `suites.json`** (nouveau), remettre les autres fichiers tels qu'ils étaient (`git checkout -- shared/ui/src/commonMain/composeResources/files/contents/` sauf `suites.json`).
3. Ajouter `suites.json` à `tools/check-apk.sh`, à côté de `solidarite.json` : `require '^assets/composeResources/[^/]+/files/contents/suites\.json$' "bundled follow-ups of the mobilizations of Agir (suites.json)"`.
4. Vérifier sur l'ordinateur (`gradlew :desktop:run`) : Carte et agenda, onglet « Passés », la projection-débat de Rennes, section « Et après ? » avec ses trois suites, puis un APK (`gradlew :android:assemblePreview`).
5. Prendre un numéro de décision pour la décision ci-dessous et la reporter dans `docs/DECISIONS.md`, et un numéro de section définitif dans `docs/FORMATS.md` si l'agent d'AG-61 a pris le même (section 9).

## 9. Décision à numéroter

**Contexte** : AG-62 demande les suites sourcées d'une mobilisation passée, reliées aux revendications, aux appels et à la chronologie. Le bloc de numéros de décisions d'« Agir » est épuisé ; cette décision attend son numéro.

**Options** : (A) un nouveau type de contenu éditorial `suite`, signé par une clé éditoriale, décrit par le moteur commun ; (B) un nouveau type de fait de « Voir » (`fact: suite`) relié à la mobilisation ; (C) un champ de bilan dans la mobilisation elle-même (comme le bilan d'un appel clos).

**Choix : A.** Une suite concerne une mobilisation de l'agenda, souvent proposée par un citoyen et signée par lui : elle ne peut pas vivre dans son événement (C), que seul son auteur peut modifier, et elle ne doit pas dépendre de lui. Un fait de « Voir » (B) obligerait à changer le schéma et les écrans de « Voir » et mêlerait à la chronologie nationale des suites très locales. Le type `suite` reprend tout ce que « Voir » a déjà de solide (statut, sources, corrections, droit de réponse) sans le modifier.

**Règles retenues** : étiquettes et vocabulaire des faits de « Voir » (`ppf-date`, `ppf-fact-status`, `ppf-source` avec adresse obligatoire, `ppf-correction`) ; types de suite en liste fermée ; liens par adresses complètes avec rôle (`mobilisation`, `personne`, `revendication`, `appel`, `chronologie`), d'où l'extension `FieldSpec.addressRole` du moteur ; une réponse d'élu nomme sa fiche ; une suite suit au moins une mobilisation et en suit jusqu'à vingt ; deux approbations par prudence ; espace `agir` des schémas, sans circuit du back-office à l'étape 1 ; lecture seule ; rien n'est envoyé au serveur ; les suites des exemples de l'agenda sont des exemples.

## 10. Questions pour le chef d'équipe

1. **Suggestions des citoyens** : faut-il permettre à une personne de proposer une suite (« Signaler une suite ») ? Recommandation : oui, à l'étape 2 ou 3, comme une demande chiffrée vers l'équipe de rédaction (le même canal que « Signaler une erreur » de « Voir », anonyme par défaut), jamais publiée telle quelle : l'équipe vérifie la source primaire et publie la suite par le circuit éditorial.
2. **Nombre d'approbations** : deux pour toute suite (choix prudent de l'étape 1), ou une, et deux seulement quand la suite relie une personne ? Recommandation : une par défaut, deux dès qu'un lien `personne` est présent (une ligne dans `EditorialApprovals.IMPLICATING_ROLES`, ou une règle propre au type) ; à décider avec « Comprendre », qui tient `EditorialApprovals`.
3. **Droit de réponse à une suite** : ajouter `suite` aux cibles acceptées par le schéma `reponse` de « Voir » (une ligne dans `TargetType.VOIR_BLOCK`, ou une cible propre) ? Recommandation : oui, avec l'accord de « Voir » ; l'application montre déjà la réponse sous la suite.
4. **Côté « Voir »** : montrer, sur un fait de la chronologie, les suites qui le relient, et sur la fiche d'une personne, les suites qui la nomment ? Recommandation : oui, plus tard, par le pôle « Voir », avec la même lecture (`FollowUp.from`).
5. **Ouvrir les liens** : à l'étape 1, les liens (revendication, appel, chronologie, personne) sont des lignes de texte. Recommandation : en faire des liens qui ouvrent le contenu dans son espace, quand le shell offrira une navigation vers un contenu par son adresse (à voir avec le Directeur technique).
6. **Serveur** : publier des suites depuis le back-office (section 7) en étape 2 ? Recommandation : oui, après la décision sur le nombre d'approbations ; c'est la seule façon d'avoir de vraies suites pour de vraies mobilisations.
7. **Numéro de section de `docs/FORMATS.md`** : la suite prend « 4 sexdecies » ; l'agent d'AG-61 (affiche avec code QR) ajoute peut-être une section au même moment. Recommandation : la session qui fusionne garde l'ordre d'entrée dans `main` et renumérote la seconde.

## 11. Fichiers

Créés : `shared/core/.../core/agir/AgirSchemas.kt`, `core/agir/followups/FollowUp.kt`, `core/agir/followups/FollowUpSchema.kt`, `commonTest/.../core/agir/followups/FollowUpSchemaTest.kt` ; `tools/content-signer/.../FollowUpContent.kt`, `FollowUpContentTest.kt` ; `content/suites/*.md` (trois) ; `shared/ui/.../ui/agir/followups/FollowUpLibrary.kt`, `FollowUpsSection.kt`, `composeResources/values/strings_suites.xml`, `jvmTest/.../ui/agir/followups/FollowUpsTest.kt` ; cette note.

Modifiés : moteur commun (`Article.kt`, `ContentSchema.kt`, `EditorialEngine.kt`, `EditorialMessages.kt`, `EditorialSchemas.kt`) ; `ui/content/ComprendreScreen.kt` (une ligne, fichier de « Comprendre ») ; `ui/agir/agenda/AgendaLibrary.kt` et `MobilizationDetail.kt` (une ligne chacun et leur import) ; `tools/content-signer/.../ContentSigner.kt` (une ligne et la documentation) ; `content/README.md`, `docs/FORMATS.md`, `docs/CHANGELOG.md`, `docs/ROADMAP.md`.

## 12. Tests écrits (pas lancés)

- `FollowUpSchemaTest` (cœur, code commun) : le type a son schéma dans l'espace `agir` ; une suite valide passe (tous les types, tous les statuts, plusieurs mobilisations et sources, une correction) ; étiquettes et liens écrits avec leurs rôles ; relecture `draftOf` identique ; lecture par `FollowUp.parse` ; refus d'un champ inconnu, d'une suite sans source, d'une source sans adresse (ou en `http://`, ou d'un type inconnu), d'un type inconnu, d'un statut inconnu, d'une suite sans mobilisation, d'une mobilisation qui n'est pas de l'agenda, d'un lien qui n'est pas une adresse ou du mauvais type, d'une réponse d'élu sans personne, d'un lien en double ; le lecteur refuse ce qu'il ne peut pas montrer ; seules les clés de confiance et les signatures valides comptent ; ordre par date et version la plus récente ; chaque règle a son texte ; `addressRole` exige l'étiquette `a` et une valeur simple.
- `FollowUpContentTest` (outil) : les trois exemples se construisent et se relisent comme l'application les lit ; refus d'une mobilisation absente de l'agenda, d'une adresse écrite à la main, d'un fait hors de la chronologie, d'une suite datée d'avant sa mobilisation, d'un titre sans [EXEMPLE], d'une source hors des domaines d'exemple, d'un champ inconnu, d'une suite sans source.
- `FollowUpsTest` (interface, JVM, sur le modèle des tests de l'agenda, le vendredi 13 novembre 2026 à 10 h) : une mobilisation passée montre ses suites par date avec le statut en mots, le type et la date, sans celles d'une autre mobilisation ni d'une clé qui n'est pas de confiance (ordinateur) ; une source ne s'ouvre qu'après l'avertissement (téléphone) ; une mobilisation à venir ne montre rien ; une mobilisation passée sans suite le dit (téléphone) ; un paquet absent ou illisible donne la phrase (mode sombre) ; le paquet n'apporte que les suites authentiques des clés de confiance, avec les seuls liens connus de l'appareil et le droit de réponse publié.
