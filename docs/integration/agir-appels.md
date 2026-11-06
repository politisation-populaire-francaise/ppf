# Note d'intégration : les appels de l'espace « Agir » (AP-01 à AP-10), côté appareil

Rédigée le 6 novembre 2026 par l'agent « appels » (branche `worktree-agent-a72a9ba3ac26f5ad1`, partie de `feature/agir` au commit `bb3931b`, contrat commun version 1 avec l'étiquette `ppf-boycott-duration`). Elle s'adresse à la session principale, qui fusionne et met à jour `CHANGELOG.md`, `ROADMAP.md`, `DECISIONS.md`, `FORMATS.md`, `REGISTRE_DONNEES.md`, `THREAT_MODEL.md`, `GUIDE_PROPRIETAIRE.md` et `content/README.md`. Aucun de ces documents n'a été modifié ici.

## 1. Ce qui est fait

### Modèle commun (`shared/core/.../core/agir/calls/`)

- **`CallTypes.kt`** : `CallType` (les treize types d'AP-01, valeurs de `AgirFormats.CALL_TYPES`, dans le même ordre), avec pour chacun ses exigences (`requirements`, lues dans `AgirFormats.CALL_REQUIRED_TAGS`, jamais écrites en dur : « `location|r` » devient une exigence à deux possibilités) et sa réponse (`CallResponse` : `ATTEND` « J'y serai » pour se réunir, débattre, s'organiser et manifester ; `SIGNED` « J'ai signé » pour une pétition ; `HELP` « J'aide » pour les bénévoles et la collecte ; `TESTIFY` « Je témoigne » pour les témoins ; `SUPPORT` « Je soutiens » pour le reste). `CallStatus` (`ouvert` par défaut, `clos`). `CallFields` : les noms des étiquettes propres aux types et la façon de les saisir (`CallFieldKind` : texte, texte long, nombre, date et heure, choix dans une liste, case à cocher) ; les champs communs (lieu, lien, objectif, échéance) restent dans la partie commune du formulaire même quand un type les exige.
- **`Call.kt`** : `Call.from(event)` lit un appel (kind 30023, `ppf-type` = `appel`) ; il refuse un événement sans titre, sans territoire, sans `d` ou d'un type inconnu. `dueAt` = l'échéance, sinon le début (une réunion est « due » quand elle commence). `missingRequirements()` dit ce qui manque à un appel reçu. `closedVersion(report, now)` prépare la clôture (AP-09). `CallDraft` est le brouillon du formulaire (dates saisies en texte, `DateTimeText`) et `toUnsignedEvent` produit l'événement du contrat.
- **`CallRules.kt`** (AP-10) : seules ces règles bloquent l'envoi. Type choisi ; titre de 10 à 140 caractères ; texte de 20 à 8000 ; résumé 300 au plus ; territoire obligatoire (80 au plus) ; chaque exigence du type remplie (« lieu ou lien », « lieu ou à distance ») ; nombres entiers positifs (9 chiffres au plus) ; dates lisibles, à venir (une heure de tolérance) sauf pour un appel à témoins, dont les faits sont passés, et fin après le début ; liens complets en `https://` avec un nom de domaine ; choix dans la liste (plateforme d'une pétition, déclaration d'une manifestation) ; aucune coordonnée personnelle dans aucun texte (les motifs de `DraftRules.containsPersonalData` des revendications : adresse électronique, téléphone français, IBAN, numéro de sécurité sociale ; le texte trouvé n'est jamais gardé) ; collecte : refusée hors organisation vérifiée, et case d'engagement « ni candidat ni parti » obligatoire. `checkReport` pour le bilan (2000 caractères, pas de coordonnées).
- **`CallQuery.kt`** : les filtres du mur (AP-05) : types, territoires (forme repliée, puces construites à partir des appels présents), échéance (`THIS_WEEK` : de maintenant à dimanche minuit ; `THIS_MONTH` : jusqu'à la fin du mois ; `NONE` : sans date ; calculés à l'heure de Paris), statut (ouverts par défaut, clos, tous), mots (titre, résumé, texte, territoire, lieu, bilan, champs propres) ; tris : nouveauté, échéance la plus proche (les dates à venir, puis les appels sans date, puis les dates passées). **Pas de tri par popularité**, et l'écran le dit sous le menu de tri. `CallSimilarity` (AP-02) réutilise `SimilarityDetector` des revendications sur les appels ouverts ; deux appels de types différents ne sont jamais « en double », seulement « proches ».
- **`ParisTime.kt`** : l'heure de Paris sans base de fuseaux (UTC+1 l'hiver, UTC+2 du dernier dimanche de mars au dernier dimanche d'octobre à 1 h UTC, directive 2000/84/CE), lecture des dates tapées (`14/11/2026`, `14-11-2026`, `2026-11-14`) et des heures (`18:30`, `18h30`, `18 h`), fin de semaine et de mois. Code commun, compatible iOS.
- **`CallResponseStore.kt`** : les réponses de l'utilisateur, sur l'appareil seulement (voir section 4).

### Base locale

- Nouveau fichier `shared/core/src/commonMain/sqldelight/.../db/CallResponse.sq` : table `call_response(address TEXT NOT NULL PRIMARY KEY)`. Seule l'adresse de l'appel (`30023:<clé>:<d>`, stable d'une version à l'autre) est gardée, sans date.
- **Migration `migrations/6.sqm` : schéma de la version 6 à la version 7.** `main` a maintenant aussi un `6.sqm` (indiqué par la session principale) : à renuméroter à la fusion (même contenu, en-tête « version 7 to 8 », `theSchemaIsAtVersion7` à passer à 8, commentaire du test à compléter, `writeVersion6Database` à adapter).
- `DatabaseMigrationTest` : version 7, une base de version 1 et une base de version 6 gagnent la table et s'en servent ; `SchemaMigrationTest` : tables `group_membership` et `call_response` présentes.
- Les appels eux-mêmes sont dans la table d'événements existante (`LocalEventStore`), comme le demande le contrat.

### Bibliothèque et interface (`shared/ui/.../ui/agir/calls/`)

- **`CallsLibrary`** (et `rememberCallsLibrary(dependencies)`) : importe `files/contents/appels.json` (seulement les appels signés par une clé éditoriale de confiance, signature vérifiée), garde la copie locale, synchronise par `RemoteEvents.fetchByTags(tag=ppf-type:appel, kind=30023)` avec son propre curseur (`pull:30023:ppf-type=appel` dans `sync_cursor`), envoie d'abord les appels de l'utilisateur restés sur l'appareil (seulement les appels, pas les autres publications), puis demande l'état de relecture par `HeldStatusSource.heldStatuses` (20 identifiants par requête). « Mes appels » : en attente d'envoi, en attente de relecture, publié, non publié (avec la raison de l'équipe). Elle garde aussi les filtres et l'onglet pour la durée de l'exécution (en mémoire), et le formulaire (`composer`), dont le brouillon survit à une visite ailleurs. `closeCall(signer, call, report)` publie la clôture. `toggleResponse`, `clearResponses`.
- **`CallComposer`** : l'état du formulaire et l'envoi (signature, copie locale en attente, port de publication unique) ; `201` donne « Publié », `202` « En attente de relecture », serveur injoignable « gardé sur l'appareil », refus « refusé, rien n'est gardé ».
- **`CallsScreen(library, session, windowClass, onBack, modifier, isVerifiedOrganisation = { false }, onCreateEvent = null, onCreateDemand = null, onCreateAccount = null)`** : l'écran autonome.
  - Mur (AP-05) : onglets « Tous les appels », « Mes appels », « Mes réponses » ; barre avec « Filtres » (nombre de filtres actifs), « Tri », « Lancer un appel » ; filtres dans la page (statut, types, échéance, territoire, mots), puces retirables et « Tout effacer » ; nombre d'appels ; cartes (type avec icône, « Clos », état de relecture pour ses propres appels, badge [EXEMPLE], titre, résumé, territoire, date à l'heure de Paris, compte à rebours, « Vous avez répondu »). La vue sur la carte viendra plus tard.
  - Détail : badges, titre, résumé, territoire, « Lancé par npub1… », lieu et dates, **objectif et compte à rebours sans barre de progression** (AP-04, explication affichée), **réponse** adaptée au type (AP-03), gardée sur l'appareil, avec l'explication « ni compteur public ni liste des personnes » ; « Je témoigne » désactivé avec l'explication du canal sécurisé en préparation ; un appel clos ne prend plus de réponse ; **bilan** d'un appel clos ; texte de l'appel (texte brut, sélectionnable) ; informations pratiques du type ; liens (« Ouvrir la pétition », « Ouvrir la page de collecte », « Ouvrir le lien », « Ouvrir la source ») ; **kit de partage** (texte prêt à copier, bouton « Copier le texte ») ; « Transformer en événement » et « En faire une revendication » seulement si les crochets sont donnés ; « Discuter de l'appel » présent mais désactivé (AP-06) ; « Clore l'appel » pour son auteur (AP-09).
  - **« Vous quittez PPF »** avant tout lien externe : nom du site, mise en garde sur les fausses cagnottes pour une collecte, « Rester sur PPF » en premier et en évidence, puis « Ouvrir la page ». Seules les adresses `https://` complètes sont ouvertes.
  - Formulaire (AP-01, AP-10) : type d'abord (une phrase par type ; « Collecter des fonds » désactivé avec « Réservé aux organisations vérifiées » pour les autres comptes) ; puis modèle guidé du type (conseils et exemple de titre), règles propres aux témoins et à la collecte, champs communs, champs propres au type (dates et heures tapées, sans geste obligatoire), engagement de la collecte, liste « Avant d'envoyer », appels proches avec « Voir l'appel », charte et case d'acceptation, bouton d'envoi (ou, sans compte, l'explication et « Créer mon compte » si `onCreateAccount` est donné), résultat.
  - Deux volets sur ordinateur (liste de 400 dp et détail), un volet avec retour sur téléphone et tablette, portrait et paysage, clair et sombre ; palette et typographie du thème, composants communs et icônes Lucide existantes (aucune icône nouvelle).
- **Textes** : `shared/ui/src/commonMain/composeResources/values/strings_appels.xml` (préfixe `appels_`, aucun tiret cadratin, apostrophe typographique). `strings.xml` n'est pas modifié.

### Exemples (`content/appels/`, signés dans `appels.json`)

Huit appels fictifs marqués [EXEMPLE] : assemblée de quartier sur les loyers (se réunir), marche pour des urgences de nuit (manifester), pétition pour un bureau de poste (lien vers `example.org`, domaine réservé à la documentation), lettre aux conseillers départementaux avant un vote (interpeller), bénévoles pour l'aide aux devoirs, rapport public sur l'eau (information, source `example.org`), appel à témoins sur une dispersion, et un appel clos avec son bilan (soutenir). Aucune collecte, aucune organisation, personne ni élu réels ; les lieux sont fictifs (« Villeneuve-sur-Fictive », « quartier des Tilleuls (lieu fictif) ») et chaque fait est dit « dans cet exemple fictif » ; aucun numéro de téléphone ni adresse électronique. Les territoires sont de vrais territoires (Villeurbanne, Nord, Creuse...), comme pour les revendications d'exemple.

`tools/content-signer` lit `content/appels/*.md` et écrit `appels.json` : chaque appel est construit avec `CallDraft` et vérifié par `CallRules` à sa date de publication (un exemple qui enfreint une règle arrête l'outil) ; un appel `status: clos` est la version de clôture de l'appel ouvert. Après avoir lancé l'outil, j'ai remis `fiches.json`, `revendications.json` et `fil.json` dans leur état d'origine : la signature Schnorr utilise un aléa, l'outil les réécrit donc à chaque passage avec d'autres signatures (mêmes identifiants), ce qui créerait des conflits inutiles entre branches.

Format d'en-tête à reporter dans `content/README.md` :

| Champ | Rôle |
|---|---|
| `slug` | identifiant stable (`d`), 64 caractères `a-z0-9-` au plus |
| `title`, `summary` | titre (avec [EXEMPLE]) et résumé |
| `call_type` | un type de `AgirFormats.CALL_TYPES` |
| `territory` | territoire |
| `status`, `report` | `ouvert` ou `clos` ; le bilan d'un appel clos |
| `published_at`, `updated_at` | `AAAA-MM-JJTHH:MM`, heure de Paris |
| `deadline`, `start`, `end` | `AAAA-MM-JJ` (échéance qui dure toute la journée) ou `AAAA-MM-JJTHH:MM`, heure de Paris |
| `goal`, `location`, `r` | objectif, lieu, lien |
| `ppf-...` | les champs propres au type, sous le nom de leur étiquette ; un champ qui n'appartient pas au type arrête l'outil |

Le corps est le texte de l'appel (texte brut).

### Fiches pratiques

`ContentLibrary` filtre déjà les fiches par `ppf-type` = `fiche-pratique` et les revendications par `ppf-type` = `revendication` : les appels n'y apparaissent pas (test `thePracticalGuidesAndTheDemandsNeverShowTheCalls`). Aucune correction n'a été nécessaire.

## 2. Fichiers partagés touchés hors de mon périmètre

- `shared/core/.../core/editorial/Article.kt` : une ligne `APPEL("appel")` dans `ArticleType` (avec son commentaire). Conséquence : la seule expression `when` exhaustive sur `ArticleType`, `typeLabel` de **`shared/ui/.../ui/content/ComprendreScreen.kt`**, reçoit une ligne `ArticleType.APPEL -> Res.string.appels_title` et l'import correspondant (2 lignes). L'agent « organisations » ajoutera sans doute la même chose pour son type : conflit d'une ligne à résoudre en gardant les deux.
- `shared/core/src/jvmTest/.../core/local/DatabaseMigrationTest.kt` et `SchemaMigrationTest.kt` : étendus (voir « Base locale »).
- `tools/content-signer/.../ContentSigner.kt` : le bloc « appels » (une fonction `callOf`, une lecture des dates à l'heure de Paris, deux lignes dans `main`, les imports).
- Nouveau fichier de ressources `shared/ui/src/commonMain/composeResources/files/contents/appels.json`.
- Non modifiés : `AdaptiveShell.kt`, `AppDependencies.kt`, `strings.xml`, `Lucide.kt`, le serveur, le site.

### Branchement par la session principale

Dans `AdaptiveShell`, à côté de la bibliothèque des contenus :

```kotlin
val callsLibrary = rememberCallsLibrary(dependencies)
// dans LaunchedEffect(accountKey) : callsLibrary.setAccount(accountKey)
// dans onSignOut (proposition, voir la question 3) : callsLibrary.clearResponses()
```

puis, dans l'emplacement préparé par l'agent « organisations » sur l'accueil d'Agir :

```kotlin
CallsScreen(
    library = callsLibrary,
    session = accountSession,
    windowClass = windowClass,
    onBack = { /* retour à l'accueil d'Agir */ },
    modifier = modifier,
    isVerifiedOrganisation = { key -> /* règle de l'agent « organisations » */ },
    onCreateEvent = { call -> /* formulaire de l'agenda, pré-rempli, avec l'étiquette a = adresse de l'appel */ },
    onCreateDemand = { call -> /* formulaire des revendications, pré-rempli */ },
    onCreateAccount = { onNavigate(Space.MON_ESPACE) },
)
```

La bibliothèque doit vivre pour toute l'exécution (comme `contentLibrary`) : les filtres, l'onglet et le brouillon du formulaire y sont gardés.

## 3. Formats

Les appels suivent le contrat, section 3. Précisions et écarts à valider (rien n'a été tranché seul) :

1. **`ppf-origin` = `human`** est écrit sur chaque appel, comme sur les revendications et toutes les autres publications de type 30023 (`docs/FORMATS.md`, section 4 bis : étiquette obligatoire). Le contrat, section 3, ne le cite pas : à y ajouter.
2. **`ppf-call-status` = `ouvert`** est écrit explicitement à la création (le contrat dit « ouvert par défaut ») ; l'absence de l'étiquette se lit aussi « ouvert ».
3. **`start_tzid` = `Europe/Paris`** accompagne `start` (comme pour les mobilisations).
4. **`ppf-remote` = `oui`** quand une mission de bénévoles peut se faire à distance ; la valeur n'est pas fixée par le contrat.
5. **Valeurs libres** : `ppf-object`, `ppf-organizer`, `ppf-accessibility`, `ppf-cause`, `ppf-action`, `ppf-targets`, `ppf-request`, `ppf-company`, `ppf-source`, `ppf-alternatives`, `ppf-boycott-duration`, `ppf-beneficiary`, `ppf-use`, `ppf-mission`, `ppf-skills`, `ppf-period`, `ppf-evidence`, `ppf-recipient` sont du texte (300 caractères, 2000 pour les champs longs : action, demande, usage, mission, alternatives, accessibilité) ; `ppf-people` et `ppf-goal` des entiers positifs ; `ppf-platform` et `ppf-declaration` des valeurs du contrat.
6. **Le parcours d'une manifestation** (« parcours s'il est connu », inventaire AP-10) va dans le texte de l'appel, comme le conseille le modèle guidé : aucune étiquette nouvelle. Une étiquette facultative `ppf-route` pourrait être ajoutée au contrat si l'agenda ou la carte en ont besoin.
7. **`d`** : premiers mots du titre repliés et quatre caractères hexadécimaux aléatoires, 64 caractères `a-z0-9-` au plus.
8. **`content`** : texte brut, affiché tel quel (pas de Markdown, donc aucun lien cliquable qui échapperait à la mise en garde).
9. **Clôture** : nouvelle version de la même adresse, toutes les étiquettes gardées sauf `ppf-call-status` (`clos`) et `ppf-report` (absent si le bilan est vide), datée de maintenant ou d'une seconde après la version précédente.

## 4. Décisions techniques proposées (à numéroter D-xxx)

1. **Réponses gardées sur l'appareil, sans date** : table `call_response(address)` ; aucune requête au serveur ne dépend des réponses ; pas de compteur, pas de liste ; « Mes réponses » et « Effacer mes réponses » (avec mise en garde, le choix sûr en premier). Répondre ne demande pas de compte.
2. **Heure de Paris calculée sans base de fuseaux** (`ParisTime`), pour tous les écrans et les filtres « cette semaine » et « ce mois-ci » (semaine du lundi au dimanche, de maintenant à la fin de la période) ; à revoir si l'Union européenne abandonne le changement d'heure ou pour les territoires d'outre-mer (voir la question 6).
3. **Dates tapées** (`JJ/MM/AAAA` et `HH:MM`) plutôt qu'un sélecteur de calendrier : accessible au clavier et aux lecteurs d'écran, sans geste, testable ; une échéance sans heure dure jusqu'à 23 h 59.
4. **Liens en `https://` seulement**, ouverts uniquement après « Vous quittez PPF » ; une source en lien passe par la même mise en garde.
5. **État d'un appel inconnu du serveur de relecture = publié** : le serveur garde un contenu en attente 30 jours après sa décision ; un identifiant accepté qu'il ne connaît pas a donc été publié directement (`201`). Sans réponse du serveur, l'état reste « en attente de relecture ».
6. **Filtres et onglet en mémoire seulement** (jamais enregistrés ni envoyés) ; pas de vues personnalisées comme le mur des revendications pour l'instant.
7. **Appels proches** : comparaison par mots réutilisée des revendications, sur les appels ouverts ; un type différent n'est jamais « en double ».
8. **Contenus d'exemple vérifiés par les règles du formulaire** dans l'outil de signature.
9. **Presse-papiers** : `LocalClipboardManager` (marqué obsolète par Compose au profit de `LocalClipboard`, qui demande un `ClipEntry` construit par du code propre à chaque plateforme) ; à remplacer quand un `expect`/`actual` sera ajouté.

## 5. À reporter dans `docs/REGISTRE_DONNEES.md`

Données sur l'appareil (section 1 bis) :

| Donnée | Finalité | Où | Durée | Accès |
|---|---|---|---|---|
| Appels auxquels l'utilisateur a répondu (« J'y serai », « Je soutiens », « J'ai signé », « J'aide ») : seulement l'adresse de chaque appel, sans date | Afficher la réponse sur l'appel et l'onglet « Mes réponses » | Table `call_response` de la base locale de l'application | Jusqu'à ce que l'utilisateur retire la réponse, efface ses réponses ou désinstalle l'application (et, si la session principale le retient, quand il efface ses clés) | L'utilisateur, par l'application. Jamais transmises : aucun compteur, aucune liste de personnes, aucune requête qui en dépende |
| Appels lancés par l'utilisateur, en attente d'envoi | Les envoyer dès que le serveur répond (hors ligne d'abord) | Table `event` (`pending`), comme les autres publications | Jusqu'à l'acceptation par le serveur ; ensuite, copie locale comme tout contenu publié | L'utilisateur ; puis public une fois publiés |
| Filtres, tri et onglet du mur des appels | Garder l'affichage choisi pendant l'utilisation | Mémoire de l'application seulement | Jusqu'à la fermeture de l'application | Personne d'autre ; jamais transmis |

Données publiées (section des contenus publics) : un appel publié est public et signé par son auteur (pseudonyme), avec son texte, son territoire, ses dates, son lieu et ses champs propres ; la clôture et le bilan aussi. Le serveur ne reçoit, pour le suivi de la relecture, que les identifiants des appels de l'auteur, qu'il connaît déjà.

## 6. À reporter dans `docs/THREAT_MODEL.md`

- **Réponses aux appels** : données sensibles (opinions politiques). Elles restent sur l'appareil, dans la base locale ; le serveur ne reçoit ni réponse, ni compteur, ni filtre. Sur un appareil saisi ou piraté, la table `call_response` révèle les appels auxquels la personne a répondu : chiffrement de la base sur ordinateur à faire (S-43) ; « Effacer mes réponses » est proposé.
- **Fausses cagnottes et hameçonnage** : collecte réservée aux organisations vérifiées (règle de l'appareil et du serveur), « Vous quittez PPF » avant tout lien avec le nom du site, rappel sur les fausses cagnottes ; seules les adresses `https://` sont ouvertes ; PPF ne manipule jamais l'argent.
- **Appels à témoins** : toujours relus par l'équipe (règle du serveur, `202`) ; l'écran demande de décrire les faits sans accuser nommément personne ; « Je témoigne » est désactivé tant que le canal sécurisé (AG-07) n'existe pas, pour qu'aucun témoignage n'arrive par un moyen non protégé.
- **Données personnelles dans un appel** : bloquées à la saisie (adresse électronique, téléphone, IBAN, sécurité sociale), puis par le serveur ; les noms ou adresses de personnes ne sont pas détectés par des motifs (la modération par IA et la relecture humaine restent nécessaires).
- **Suivi de la relecture** : seuls des identifiants d'événements déjà connus du serveur partent, par lots de 20.

## 7. Questions pour la propriétaire

1. **Compteur public des réponses** (AP-03 : « le compteur est public ») et **barre de progression** (AP-04) : ils attendent la méthode de comptage anonyme (comme les réactions du fil, option B). En attendant, aucun compteur : est-ce bien ce que vous souhaitez ?
2. **Discussion d'un appel** (AP-06) : chaque appel publié doit-il ouvrir automatiquement un salon de collectif (avec son territoire), ou l'auteur choisit-il d'en ouvrir un ? Qui en est l'administrateur ? Le bouton est présent mais désactivé.
3. **Effacer les réponses quand on efface ses clés** : je le propose (protection par défaut), même si répondre ne demande pas de compte. D'accord ?
4. **Clôture d'un appel déjà publié** : le serveur met la version de clôture en relecture pour un compte non vérifié ; pendant ce temps, les autres voient l'appel encore ouvert. Faut-il publier directement une clôture quand l'appel était déjà publié (le bilan restant signalable) ?
5. **Prévenir les participants** quand l'objectif est atteint ou que l'appel change (AP-04) : demande les notifications (UnifiedPush et ntfy auto-hébergé) et un moyen de savoir qui a répondu sans que le serveur le sache (abonnement local de l'appareil). Non fait.
6. **Territoires d'outre-mer** : les dates sont affichées à l'heure de Paris. Faut-il afficher l'heure locale d'un appel en Guadeloupe ou à La Réunion (fuseau propre à l'appel) ?
7. **Tri par popularité** : il reviendra avec le compteur ; d'ici là, faut-il proposer un tri « près de chez moi » (territoire choisi sur l'appareil) ?
8. **Parcours d'une manifestation** : dans le texte pour l'instant ; voulez-vous un champ dédié (et plus tard un tracé sur la carte) ?

## 8. Comment tester

### Tests automatiques

- `gradlew :shared:core:jvmTest --tests "*agir.calls*"` (et `*Migration*`) : modèle, règles par type, filtres, appels proches, heure de Paris, réponses, migrations.
- `gradlew :shared:ui:jvmTest --tests "*agir.calls*"` : mur, filtres, détail, réponse locale, mise en garde avant un lien, formulaire par type, collecte, témoins, clôture, trois largeurs, bibliothèque.
- Captures : `PPF_SCREENSHOTS_DIR=<dossier>` puis `gradlew :shared:ui:jvmTest --tests "*CallsScreenshotTest*"` : `*-appels.png`, `*-appels-filtres.png`, `*-appels-filtres-actifs.png`, `*-appel-detail.png`, `*-appel-repondu.png`, `*-appel-quitter-ppf.png`, `*-appel-clos.png`, `*-appel-temoins.png`, `*-appels-mes-reponses.png`, `*-lancer-type.png`, `*-lancer-formulaire.png`, `*-lancer-verifications.png`, en clair et sombre sur téléphone, en clair sur ordinateur.

### Pour la propriétaire (à reporter dans `GUIDE_PROPRIETAIRE.md` une fois l'écran branché)

1. Ouvrez « Agir », puis « Appels ». Sept appels d'exemple sont affichés ; l'appel clos n'apparaît qu'avec le filtre « Clos » ou « Tous ».
2. Touchez « Filtres » : choisissez « Ce mois-ci » (l'assemblée du 14 novembre et la marche du 21 novembre restent), un type, un territoire, ou tapez des mots. Chaque filtre apparaît en puce : touchez sa croix pour le retirer.
3. Touchez « Tri » : « Échéance la plus proche » met en tête ce qui arrive bientôt. Une phrase explique qu'il n'y a pas encore de tri par popularité.
4. Ouvrez la pétition : l'objectif (1 000 signatures) et le compte à rebours s'affichent, sans barre de progression. Touchez « J'ai signé » : la réponse est enregistrée sur votre téléphone seulement. Elle apparaît dans l'onglet « Mes réponses », où « Effacer mes réponses » vous demande confirmation (« Garder mes réponses » en premier).
5. Touchez « Ouvrir la pétition » : « Vous quittez PPF » s'affiche avec le nom du site ; « Rester sur PPF » est en premier.
6. Ouvrez l'appel à témoins : « Je témoigne » est grisé, avec l'explication du canal sécurisé.
7. Touchez « Lancer un appel » : choisissez un type (« Collecter des fonds » est grisé, réservé aux organisations vérifiées), remplissez les champs ; la liste « Avant d'envoyer » dit ce qui manque. Tapez « Garder le bureau de poste » dans le titre d'une pétition : l'appel proche s'affiche. Avec un compte, cochez la charte et envoyez : l'appel est publié, ou mis en attente de relecture ; son état est dans « Mes appels ». Sans serveur (téléphone de test), il reste « En attente d'envoi ».
8. Sur un de vos appels publiés, « Clore l'appel » demande un bilan, avec « Garder l'appel ouvert » en premier.

## 9. Ce qui reste

- Branchement dans l'accueil d'Agir et dans `AdaptiveShell` (session principale) ; règle `isVerifiedOrganisation` (agent « organisations ») ; crochets vers l'agenda et les revendications.
- Compteur public anonyme, barre de progression, tri par popularité, notifications aux participants (questions 1 et 5).
- Salon de discussion d'un appel (AP-06, question 2) ; proposer un appel comme campagne (DE-04, AP-07).
- Vue sur la carte des appels qui ont un lieu (AP-05), avec le choix de la bibliothèque de carte.
- Canal sécurisé des témoignages (AG-07) et galerie (VO-08).
- Appels réservés aux membres d'un collectif (AG-50) ; appels co-organisés (AG-21, AG-52).
- Modification d'un appel publié (nouvelle version) autre que la clôture.
- Site web : page publique des appels.
