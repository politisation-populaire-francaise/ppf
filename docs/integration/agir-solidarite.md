# Note d'intégration : solidarité, soutien et bénévolat (M35 : AG-10, AG-12)

- **État (8 novembre 2026)** : conception écrite, puis code du cœur, de l'interface, des exemples et des tests **écrit sans être compilé** (consigne de la session : une seule compilation à la fois sur la machine, réservée à d'autres sessions). Rien n'a été lancé : ni Gradle, ni test, ni l'outil de signature.
- **À faire au moment de compiler** : section 9.
- Rédigée par l'agent « solidarité » (branche `feature/agir-solidarite`, partie de `feature/agir` au commit `c824c76`). Elle s'adresse à la session « Agir », qui fusionne, numérote les décisions (D-085 à D-089) et reporte les formats dans `docs/FORMATS.md` et `content/README.md`.

## 1. Ce que fait la rubrique

M35 rassemble ce qui permet de se soutenir les uns les autres pendant le mouvement. La règle qui domine tout le module : **PPF ne détient ni ne fait jamais transiter d'argent** (point d'attention n° 12) ; elle renvoie vers la page de dons de celui qui gère la caisse, après la mise en garde « Vous quittez PPF ».

| Partie | Fonctionnalité | Fait ici | Annoncé « bientôt » |
|---|---|---|---|
| Caisses de solidarité | AG-10 : annuaire des caisses de grève et de solidarité juridique, liens vers leurs propres pages de dons | Annuaire signé par l'équipe éditoriale, recherche, filtres (type, territoire, « près de chez moi » calculé sur l'appareil), page d'une caisse, lien de dons ouvert seulement après « Vous quittez PPF » avec la mise en garde contre les fausses cagnottes, lisible hors ligne | Rien |
| Entraide | AG-10 : covoiturage vers les manifestations, hébergement | Rien (questions 1 et 2) | Onglet « Entraide » qui explique ce qui se prépare et pourquoi |
| Je veux aider | AG-12 : missions de bénévolat publiées par l'équipe, par compétences | Missions signées par l'équipe, filtre « Mes compétences » gardé en mémoire sur l'appareil, filtre « À distance », page d'une mission, renvoi vers les appels « Chercher des bénévoles » | « Proposer mon aide » (question 3) |
| Hors périmètre | AG-60 (simulateur du gréviste), S-55 (don à l'association), PP-08 (adhésion) | Rien | Rien (non demandés) |

L'accueil d'Agir ouvre la rubrique par deux cartes, « Solidarité et caisses de grève » (onglet des caisses) et « Je veux aider » (onglet des missions), qui remplacent les deux annonces « En préparation » correspondantes.

## 2. Formats des contenus

Deux nouveaux types de contenus éditoriaux, tous deux de type 30023 (contenu adressable, comme les fiches d'organisation), **publiés seulement par une clé de l'équipe éditoriale**. Le serveur le garantit déjà sans changement : `PublicationPolicy` refuse tout événement de type 30023 d'une clé non éditoriale qui n'est pas un appel (`403 not_allowed`). L'appareil, lui, n'importe, ne stocke et n'affiche que les contenus signés par une clé de `EditorialTrust`, signature vérifiée à l'arrivée, auteur revérifié à chaque lecture (même règle que l'annuaire des organisations).

### 2.1 Caisse de solidarité (`ppf-type` = `caisse`)

| Étiquette | Obligatoire | Contenu |
|---|---|---|
| `d` | oui | identifiant stable, 1 à 64 caractères `a-z0-9._~-` |
| `title` | oui | nom de la caisse, 1 à 140 caractères ([EXEMPLE] pour un exemple) |
| `summary` | oui | résumé |
| `published_at` | oui | première publication, secondes depuis 1970 |
| `ppf-type` | oui | `caisse` |
| `ppf-fund-type` | oui | `greve` (caisse de grève), `juridique` (solidarité juridique : frais d'avocat, amendes, soutien aux personnes poursuivies), `autre` ; une valeur inconnue est lue `autre` |
| `ppf-territory` | oui | commune, département ou `France`, écrit « Commune (dd) » comme l'annuaire |
| `ppf-beneficiary` | oui | à qui elle sert, en clair (« Grévistes des transports urbains de Lille ») ; même nom d'étiquette que la collecte des appels (AP-10) |
| `ppf-verified` | oui | date `AAAA-MM-JJ` à laquelle l'équipe a vérifié la caisse (la page de dons appartient bien à celui qui la gère) |
| `ppf-no-party` | oui | `oui` : l'équipe a vérifié que la caisse ne sert ni un candidat ni un parti (point d'attention n° 12, D-039) ; sans cette valeur, la fiche est écartée |
| `ppf-manager` | l'un des deux | nom de celui qui gère la caisse, en clair |
| `a` | l'un des deux | adresse (`30023:<clé>:<d>`) de la fiche de l'annuaire de l'organisation qui gère la caisse |
| `r` | pour une caisse ouverte | la page de dons de la caisse : seulement `https://`, nom d'hôte ASCII, sans partie « utilisateur@ » ; la première adresse valable est la seule retenue |
| `ppf-sector` | non | secteur (« Transports », « Éducation ») |
| `ppf-strike` | non | la grève ou le mouvement soutenu |
| `ppf-fund-status` | non | `ouverte` (défaut, absente), `close` (la caisse ne reçoit plus de dons), `retiree` (l'équipe a retiré la fiche) ; une valeur inconnue est lue `retiree` : elle n'étend jamais la confiance |
| `ppf-origin` | non | `human` ou `ai-assisted`, affichée |
| `g` | non | géohash placé par l'équipe elle-même |

`content` : présentation en Markdown simple (8 000 caractères au plus), sans lien cliquable (le rendu Markdown commun n'ouvre aucun lien).

**Pourquoi un type à part plutôt qu'une fiche d'organisation de type « caisse »** : une caisse n'est pas une organisation, elle est gérée par une organisation ; elle a ses propres champs (bénéficiaires, grève, page de dons, engagement « ni candidat ni parti »). Surtout, une fiche d'organisation qui porte une clé `p` **vérifie** cette clé (`VerifiedOrganisations`) : réutiliser ce format pour les caisses mélangerait deux règles de confiance, et une caisse risquerait d'apparaître dans l'annuaire ou de rendre « vérifiée » la clé de son gestionnaire. Avec un type à part, rien ne change pour l'annuaire, l'agenda ni les appels.

**Qui la gère** : le gestionnaire est désigné par l'adresse de sa fiche dans l'annuaire (`a`), retrouvée sur l'appareil parmi les fiches de confiance (à défaut de l'adresse exacte, par son identifiant `d`, par exemple après une rotation de la clé éditoriale). L'écran dit en mots l'un des trois états : « organisation vérifiée par PPF », « vérification retirée par PPF », ou « n'est pas dans l'annuaire de PPF » (nom seul, `ppf-manager`).

### 2.2 Mission de bénévolat (`ppf-type` = `mission`)

| Étiquette | Obligatoire | Contenu |
|---|---|---|
| `d`, `title`, `summary`, `published_at`, `ppf-type` | oui | comme ci-dessus, `ppf-type` = `mission` |
| `ppf-skill` | oui, une ou plusieurs | `juriste`, `traduction`, `developpement`, `graphisme`, `verification`, `moderation`, `accueil`, `autre` ; une valeur inconnue est lue `autre` |
| `ppf-territory` | oui | territoire, `France` pour une mission à distance |
| `ppf-remote` | l'un des deux | `oui` : la mission se fait à distance (même valeur que les appels « Chercher des bénévoles ») |
| `location` | l'un des deux | lieu en clair, jamais l'adresse d'une personne |
| `ppf-commitment` | non | temps demandé, en clair (« Deux heures par semaine ») |
| `ppf-people` | non | nombre de personnes recherchées, entier positif |
| `ppf-deadline` | non | date limite, secondes depuis 1970 |
| `ppf-mission-status` | non | `ouverte` (défaut, absente), `pourvue`, `close` ; une valeur inconnue est lue `close` |
| `ppf-origin` | non | comme ci-dessus |

`content` : description de la mission en Markdown simple.

### 2.3 Fichiers sources (`content/solidarite/`, à reporter dans `content/README.md`)

- `content/solidarite/caisses/*.md` : `slug`, `title`, `summary`, `fund_type`, `territory`, `beneficiary`, `verified`, `no_party` (`oui`), `manager` (nom) et/ou `manager_entry` (identifiant d'une fiche de `content/organisations`, transformé en adresse `30023:<clé de l'outil>:<slug>` ; l'outil vérifie que la fiche existe), `donation_link`, `sector`, `strike`, `status`, `origin`, `geohash`, `published_at`, `updated_at` (`AAAA-MM-JJ`, midi UTC comme les autres contenus).
- `content/solidarite/missions/*.md` : `slug`, `title`, `summary`, `skills` (séparées par des virgules), `territory`, `remote` (`oui`), `location`, `commitment`, `people`, `deadline` (`AAAA-MM-JJ`, fin de journée à l'heure de Paris), `status`, `origin`, `published_at`, `updated_at`.
- Aucun champ `type` dans ces en-têtes : l'outil de signature parcourt tout `content/` pour le contexte de « Comprendre » et ignore un fichier sans `type` hors de ses dossiers.
- L'outil écrit `solidarite.json` à côté de `organisations.json` (`tools/content-signer/.../SolidarityContent.kt`, une ligne dans `main`), hors de `prepareBundles` : le test de non-régression (`NoRegressionTest`, qui attend 9 paquets) n'est pas touché tant que le fichier n'est pas signé.

## 3. Ce qui reste sur l'appareil, ce qui part au serveur

- **Rien de l'utilisateur ne part** : la recherche, les filtres, le territoire « près de chez moi », les compétences choisies et la caisse ou la mission ouverte restent en mémoire, sur l'appareil. Le serveur ne reçoit que deux requêtes générales, identiques pour tout le monde : `kind=30023&tag=ppf-type:caisse` et `kind=30023&tag=ppf-type:mission`, chacune avec son curseur (`pull:30023:tag:ppf-type=caisse`, `pull:30023:tag:ppf-type=mission`).
- **Compétences** : elles servent seulement de filtre « Mes compétences ». Elles sont gardées en mémoire tant que l'espace « Agir » est affiché, jamais enregistrées ni envoyées (le réglage le plus protecteur, principe 10) ; l'effacement rapide (S-44) les oublie aussi (partie `agir-solidarity` inscrite dans `AppDependencies.erasables`). Les garder d'une fois sur l'autre est la question 4.
- **Contenus** : les caisses et les missions sont des contenus publics signés, gardés dans la table d'événements existante (`LocalEventStore`), lisibles hors ligne. **Aucune migration** de base locale ni serveur.
- **Lien de dons** : ouvert par le système (navigateur), seulement après « Vous quittez PPF ». L'application n'interroge jamais la page de dons (aucun montant collecté affiché, aucune requête vers un tiers).

## 4. Menaces

| Menace | Protection en place | Protection prévue |
|---|---|---|
| Fausse caisse, cagnotte frauduleuse, hameçonnage | Seule une clé éditoriale publie une caisse (serveur et appareil) ; date de vérification affichée ; lien `https` à hôte ASCII seulement (pas d'imitation d'un nom par des caractères proches, pas de « utilisateur@ ») ; « Vous quittez PPF » nomme l'hôte, met en garde contre les fausses cagnottes, le choix sûr (« Rester sur PPF ») en premier ; caisse close ou retirée : plus de lien | Procédure écrite de vérification et revérification (question 6) |
| Caisse détournée au profit d'un candidat ou d'un parti (financement politique interdit à une personne morale) | Étiquette `ppf-no-party` = `oui` obligatoire, refus de l'outil de signature et de l'appareil sans elle ; l'annuaire des organisations exclut partis et candidats | Liste des cas limites pour l'avocat (question 7) |
| Clé éditoriale compromise | Comme l'annuaire : `EditorialTrust` figée dans l'application en phase 1, rotation et révocation prévues pour la signature des contenus | |
| Profilage par la recherche (qui cherche une caisse de grève de quel secteur, qui a des compétences de juriste) | Requêtes générales seulement ; recherche, territoire et compétences jamais envoyés ni enregistrés | |
| Page de dons qui piste le visiteur | La mise en garde le dit ; rien n'est ouvert sans geste | |
| Exposition des personnes dans l'entraide (adresse, trajet, présence à une manifestation) | Non codée : annoncée « bientôt » | Questions 1 et 2 |
| Révélation d'un engagement en proposant son aide | Non codée : annoncée « bientôt » | Question 3 |

## 5. Questions, avec recommandations

1. **Entraide, covoiturage et hébergement : comment la faire sans rien imposer et sans que PPF publie la position de quelqu'un ?** Une offre de covoiturage « pour la manifestation du 21 depuis Lille » dit où l'on habite à peu près, quand on part, et qu'on va manifester (une opinion politique, RGPD article 9) ; une offre d'hébergement dit plus encore.
   - Option A : offres publiques, comme des appels d'un type nouveau, pseudonymes, ville seulement (jamais d'adresse), contact par message privé chiffré seulement, effacement automatique après l'événement. Simple, mais la clé pseudonyme est liée publiquement à la participation.
   - Option B : offres dans le salon de la mobilisation ou du collectif (M14, M24), avec des modèles de message (« Je propose trois places depuis Lille, départ 9 h ») qui demandent la ville et jamais l'adresse ; contact en message privé ; PPF ne publie rien en dehors du salon. Dépend des salons d'événements et des collectifs privés chiffrés (S-133, S-134).
   - Option C : ne rien faire dans PPF, renvoyer vers l'entraide des organisations de l'annuaire.
   - **Recommandation : B pour la première version** (rien de public par défaut, principe 10), puis A en option, sur choix de la personne après une mise en garde, réversible.
2. **Hébergement solidaire** : en plus de la question 1, accueillir un inconnu pose des questions de sécurité (personnes vulnérables, mineurs). Recommandation : ne le proposer qu'entre membres d'un collectif ou par une organisation vérifiée qui l'organise déjà, jamais entre inconnus dans une liste publique ; avis de l'avocat.
3. **« Proposer mon aide » (AG-12) : comment un bénévole propose son aide sans que ses compétences quittent l'appareil sans son accord ?** Un chemin chiffré existe dans le code (message privé NIP-17 vers une boîte de l'équipe, D-056), mais la seule boîte prévue est celle de la rédaction de « Voir » : y envoyer des offres de bénévolat mélangerait les finalités (RGPD) et personne n'est désigné pour les traiter. Proposer son aide à PPF peut aussi révéler une sympathie politique.
   - Option A : message privé chiffré (NIP-17) depuis le compte vers une **boîte dédiée « bénévolat »** de l'équipe, distincte de la clé qui signe les contenus et de celle de la rédaction ; écran de confirmation qui montre exactement ce qui part (pseudonyme, adresse de la mission, compétences cochées pour cette mission, message facultatif) ; réponse de l'équipe dans « Messages » ; l'équipe efface l'offre quand la mission est pourvue.
   - Option B : passer par « Écrire à l'équipe » avec un numéro de dossier (M30), quand il sera dans `main`.
   - Option C : la mission donne une adresse électronique de l'équipe (sort de PPF, révèle plus).
   - **Recommandation : A**, après désignation des personnes qui lisent la boîte (rôle « coordination des bénévoles », double authentification) et une durée de conservation écrite. Rien n'est envoyé d'ici là : le bouton est grisé avec l'explication.
4. **Garder les compétences d'une fois sur l'autre** : aujourd'hui en mémoire seulement. Option : les garder dans les préférences privées de l'appareil, sur choix de la personne après une mise en garde, effacées par l'effacement rapide. Recommandation : mémoire seule pour la première version ; proposer l'enregistrement avec le tableau de bord de « Mon espace ».
5. **Quelles caisses peuvent figurer dans l'annuaire ?** L'appel à collecter des fonds (AP-08) est réservé aux organisations vérifiées et à PPF. Ici c'est l'équipe de PPF qui publie, ce qui reste dans la règle. Recommandation : n'accepter que des caisses dont l'équipe a identifié le gestionnaire (de préférence une organisation de l'annuaire ; sinon une intersyndicale ou une legal team nommée et vérifiée), et toujours afficher la différence (« organisation vérifiée » ou « n'est pas dans l'annuaire de PPF »).
6. **Procédure de vérification d'une caisse** : quelles pièces (lien depuis le site officiel de l'organisation, statuts, numéro RNA ou SIREN, usage de l'argent publié), qui vérifie, à quelle fréquence on revérifie (par exemple à chaque nouvelle grève, et au moins tous les trois mois), comment on retire une fiche (`retiree`, avec la date, jamais effacée en silence). Recommandation : la même procédure que la vérification des organisations (question 1 de `agir-organisations.md`), plus la vérification du lien de dons.
7. **Cas limites du financement politique**, pour l'avocat : caisse juridique pour un militant d'un parti poursuivi après une manifestation ; caisse de grève d'un syndicat proche d'un parti ; caisse d'un collectif qui soutient ensuite une liste aux municipales. Recommandation : refuser tant que l'avocat n'a pas répondu.
8. **Plateformes de cagnotte tierces** (HelloAsso, Cotizup, Leetchi, Lydia) : beaucoup de caisses y sont. Recommandation : les accepter, l'hôte étant montré en clair avant de quitter PPF, en préférant la page de l'organisation elle-même ou HelloAsso (sans commission sur les dons).
9. **Montant collecté** : afficher un montant demanderait d'interroger un site tiers ou de recopier un chiffre. Recommandation : rien dans la première version ; plus tard, un montant déclaré par la caisse, publié par l'équipe avec sa date et sa source.

## 6. Décisions proposées (à numéroter D-085 à D-089 par la session Agir)

1. **D-085, format des caisses** : contenu éditorial de type 30023, `ppf-type` = `caisse`, publié seulement par une clé éditoriale, lu seulement s'il est signé par une clé de confiance ; étiquettes de la section 2.1 ; engagement `ppf-no-party` = `oui` obligatoire ; statuts `ouverte`, `close`, `retiree` (une valeur inconnue vaut `retiree`). Type à part plutôt qu'une fiche d'organisation (section 2.1).
2. **D-086, lien de dons** : une seule page de dons par caisse, `https` à hôte ASCII seulement, ouverte uniquement après « Vous quittez PPF » (choix sûr en premier, hôte nommé, mise en garde contre les fausses cagnottes), seulement pour une caisse ouverte ; PPF ne détient ni ne fait transiter d'argent, n'affiche aucun montant et n'interroge jamais la page de dons.
3. **D-087, gestionnaire d'une caisse** : désigné par l'adresse de sa fiche dans l'annuaire (retrouvée par son identifiant après une rotation de clé) ou par son nom ; trois états dits en mots (vérifiée, vérification retirée, absente de l'annuaire) ; la page de l'organisation s'ouvre depuis la caisse et le retour ramène à la caisse.
4. **D-088, format des missions** : contenu éditorial de type 30023, `ppf-type` = `mission`, publié seulement par une clé éditoriale ; compétences dans une liste fermée (section 2.2), à distance ou avec un lieu ; statuts `ouverte`, `pourvue`, `close`.
5. **D-089, ce qui reste sur l'appareil** : recherche, filtres, « près de chez moi » et compétences en mémoire seulement, jamais envoyés ni enregistrés, oubliés par l'effacement rapide ; synchronisation par deux requêtes générales avec leurs curseurs ; « Proposer mon aide » et l'entraide annoncées « bientôt » tant que les questions 1 à 3 ne sont pas tranchées.

## 7. Fichiers

Nouveaux :
- `shared/core/src/commonMain/kotlin/org/politisationpopulairefrancaise/core/agir/solidarity/` : `SolidarityFormats.kt` (vocabulaire), `SolidarityFund.kt` (caisse : lecture, construction, gestionnaire), `VolunteerMission.kt` (mission), `SolidarityQuery.kt` (recherches des caisses et des missions).
- `shared/ui/src/commonMain/kotlin/org/politisationpopulairefrancaise/ui/agir/solidarity/` : `SolidarityLibrary.kt` (import, synchronisation, état), `SolidarityScreen.kt` (onglets, listes, filtres, un ou deux volets), `SolidarityPages.kt` (page d'une caisse, page d'une mission), `SolidarityLabels.kt`.
- `shared/ui/src/commonMain/composeResources/values/strings_solidarite.xml`.
- `tools/content-signer/.../SolidarityContent.kt` et les exemples `content/solidarite/`.
- Tests : `shared/core/src/commonTest/.../core/agir/solidarity/` et `shared/ui/src/jvmTest/.../ui/agir/solidarity/`.

Modifiés (fichiers d'Agir) : `ui/agir/AgirHome.kt` (deux rubriques ouvertes), `ui/agir/AgirScreen.kt` (route `solidarite`, paramètre facultatif `solidarity` ajouté à la fin, retour du système), `strings_agir.xml` (textes des deux cartes), `AgirScreenTest.kt` (trois rubriques « En préparation » au lieu de cinq). Commun : `ContentSigner.kt` (une ligne dans `main`, une ligne de KDoc).

## 8. À reporter dans `docs/FORMATS.md`

Les sections 2.1 et 2.2, sous les sections des contenus d'Agir (fiches d'organisation, section 4 sexies).

## 9. À faire au moment de compiler

- `gradlew :tools:content-signer:run` pour écrire `shared/ui/src/commonMain/composeResources/files/contents/solidarite.json` (puis ne garder que ce fichier, la signature BIP-340 changeant les autres à chaque passage, comme l'ont fait les notes de l'annuaire et des appels). Ajouter ensuite à `tools/check-apk.sh` la ligne qui exige `solidarite.json` dans l'APK.
- Tests à lancer : `gradlew :shared:core:jvmTest --tests "*agir.solidarity*"`, `gradlew :shared:ui:jvmTest --tests "*agir.solidarity*"` et `--tests "*AgirScreenTest*"`, puis `gradlew :tools:content-signer:test`.
- Points de doute listés dans le rapport de l'agent (imports, signatures, API de Compose).

## 10. Comment tester (à reporter dans `docs/GUIDE_PROPRIETAIRE.md` une fois compilé et signé)

1. Ouvrez « Agir » : les cartes « Solidarité et caisses de grève » et « Je veux aider » sont maintenant dans les rubriques ouvertes ; « En préparation » n'en garde que trois.
2. Touchez « Solidarité et caisses de grève » : des caisses fictives marquées [EXEMPLE], chacune avec son type, son territoire, à qui elle sert et qui la gère (« organisation vérifiée par PPF » ou « n'est pas dans l'annuaire de PPF »).
3. Dans « Près de chez moi », tapez 59000 : d'abord les caisses du Nord, puis celles qui servent partout en France. Ce que vous tapez reste sur votre appareil.
4. Ouvrez une caisse ouverte, touchez « Donner sur la page de la caisse » : « Vous quittez PPF » nomme le site et rappelle que les fausses cagnottes existent ; « Rester sur PPF » est en premier et ne fait rien d'autre que refermer.
5. Touchez « Voir la page de l'organisation » : la fiche de l'annuaire s'ouvre ; le retour ramène à la caisse.
6. Onglet « Je veux aider » : cochez « Traduction » : seules les missions de traduction restent. La phrase dessous rappelle que vos compétences restent sur votre appareil. Sur une mission, « Proposer mon aide » est grisé avec l'explication.
7. Onglet « Entraide » : l'explication de ce qui se prépare (covoiturage, hébergement) et pourquoi ce n'est pas encore là.
8. Tournez le téléphone ou élargissez la fenêtre : sur ordinateur, la liste et la page sont côte à côte.
