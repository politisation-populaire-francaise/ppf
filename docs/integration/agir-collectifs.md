# Note d'intégration : collectifs d'« Agir » (M24, étape 1)

- **État (9 novembre 2026)** : conception, code du cœur, du serveur, de l'interface, des exemples du serveur de développement et des tests **écrits sans compiler** (consigne du chef d'équipe : aucune compilation de branche pendant qu'une autre équipe compile sur cette machine). **Rien n'a été compilé ni exécuté.** Branche de l'agent partie de `feature/agir` au commit `75d234e`. Décision D-087.
- **À faire au moment de compiler** : section 10.
- Fonctionnalités couvertes : S-129 (créer un collectif), S-130 en partie (portée, territoire, thèmes, type, langue, charte interne ; public et ouvert seulement), AG-47 (trouver et rejoindre), AG-48 (page), AG-49 et AG-50 (événements et appels au nom du collectif, publics seulement). Le reste de M24 est en section 8.

## 1. Ce qui est fait

| Partie | Fait à l'étape 1 | Pas encore |
|---|---|---|
| Format | Un collectif est un salon NIP-29 de Communauté dont la description (39000) porte `["ppf-collective", "1"]` et ses étiquettes (section 2) | Collectifs privés et secrets, adhésion sur demande ou sur invitation |
| Création (S-129) | Assistant en quatre étapes, réservé aux comptes validés en personne ; explication et renvoi vers la carte « Mon niveau de votant » pour les autres, avec la création d'un salon, ouverte à tous ; création par le chemin des salons (doublons, noms proches, création retenue pour l'équipe, hors ligne) | Image, nombre maximal de membres |
| Serveur | Règle de création (compte de personne, non compromis, validé en personne), étiquettes vérifiées avec des codes précis, ce qui ne change jamais, rôles exigés pour publier au nom du collectif | Suspension par PPF (S-135) |
| Annuaire (AG-47) | Recherche, filtres par thème, type et portée, « près de chez moi » calculé sur l'appareil, lien vers les salons ordinaires | Filtre par mode d'adhésion (une seule valeur aujourd'hui) |
| Page (AG-48) | Présentation, badge [EXEMPLE], portée, territoire, type, thèmes, langue, charte interne repliée, rôles publics, rejoindre et quitter sur l'appareil, salon du collectif, prochains événements, appels en cours | Présentation détaillée : attend la décision « Maquettes de l'interface communautaire » |
| Calendrier et appels (AG-49, AG-50) | Les compositeurs de l'agenda et des appels, ouverts depuis la page avec l'étiquette `ppf-collective`, pour le fondateur, les administrateurs et les modérateurs | Événements et appels réservés aux membres |
| Membres | Rejoindre = rejoindre le salon sur l'appareil (`group_membership`), rien n'est publié | Liste des membres (S-133), statuts personnalisés (S-131) |

L'accueil d'« Agir » garde sa carte « Collectifs » ; elle ouvre maintenant l'annuaire des collectifs au lieu de la vue « Collectifs » de « Messages ». Les salons ordinaires restent à un geste (« Voir les salons de discussion »), avec un retour vers l'annuaire.

## 2. Format (reporté dans `docs/FORMATS.md`, section 4 quaterdecies)

Description du salon (39000), en plus de ce que `Group.build` écrit (`d`, `name`, `about`, `ppf-territory`, `ppf-charter`, et `ppf-similar` / `ppf-difference` après une confirmation, D-132) :

| Étiquette | Obligatoire | Valeur |
|---|---|---|
| `ppf-collective` | oui | `1` |
| `ppf-scope` | oui | `national`, `regional`, `departemental`, `local`, `quartier`, `etablissement` |
| `ppf-collective-type` | oui | `collectif`, `club`, `comite`, `assemblee`, `reseau`, `autre` |
| `t` | 0 à 5 | thème normalisé (minuscules, espaces réduits, sans `#`), 40 caractères au plus, sans doublon |
| `l` | non | ISO 639-1, `fr` par défaut |
| `ppf-territory-code` | non | code du référentiel des territoires, choisi parmi les propositions, jamais deviné |
| `ppf-internal-charter` | non | 3000 caractères au plus |
| `ppf-visibility` | non | `public` seulement (absente : public) |
| `ppf-join` | non | `open` seulement (absente : ouvert) |

L'application écrit ces étiquettes dans cet ordre (`CollectiveFormat.tags`) ; la lecture (`Collective.from`) est tolérante pour une version plus récente (type inconnu lu `autre`, portée inconnue lue « inconnue », étiquette facultative illisible ignorée), la vérification (`CollectiveFormat.problem`) est stricte et commune à l'application et au serveur.

**Mobilisation ou appel au nom d'un collectif** : `["ppf-collective", "<identifiant du salon>"]` ajouté par `CollectiveFormat.withCollective` (une seule, remplacée si elle existe). Même nom d'étiquette que le marqueur, sens différent selon le type d'événement.

## 3. Règles du serveur et codes d'erreur

Code : `server/.../agir/collectives/CollectiveRules.kt` (`CollectiveRules` pour les descriptions, `CollectiveRoles` pour les mobilisations et les appels).

**Description d'un salon** (appelée par `GroupDirectory.admit`, juste après le refus d'un identifiant tenu par une autre clé) :

| Cas | Réponse |
|---|---|
| Nouveau salon sans marqueur | rien de nouveau (règles des salons) |
| Nouveau collectif, étiquette mal formée | `400` avec le code précis : `collective_bad_marker`, `collective_bad_scope`, `collective_bad_type`, `collective_too_many_themes`, `collective_bad_theme`, `collective_bad_language`, `collective_bad_territory_code`, `collective_bad_internal_charter`, `collective_charter_too_long`, `collective_visibility_unsupported`, `collective_join_unsupported` |
| Nouveau collectif, auteur déclaré compromis avant la date de l'événement (`CompromisedAccounts.heldVerdict` = `CreatedAfter`) | `403 account_compromised` (le refus général de S-146 répond déjà avant pour toute clé compromise) |
| Nouveau collectif, compte de collectif (D-133) | `403 collective_needs_verified` |
| Nouveau collectif, niveau autre que `verifie` ou `elu` (`UrneStore.account(clé).level`), ou pas de stockage des urnes | `403 collective_needs_verified` |
| Nouvelle version (même clé) qui change `ppf-collective`, `ppf-scope`, `ppf-territory-code`, `ppf-territory`, `ppf-charter`, `ppf-visibility` ou `ppf-join` | `409 collective_immutable` (vaut aussi pour un salon ordinaire qui voudrait devenir un collectif, et l'inverse) |
| Nouvelle version d'un collectif, étiquettes modifiables mal formées | `400` comme ci-dessus |

Le niveau lu est `vote_account.level`, celui que `GET /api/v1/urnes/me` montre à l'appareil (un compte validé cette semaine compte déjà, même si son niveau ne vaut pour les urnes qu'à partir du lundi suivant). Les garde-fous de D-132 (3 salons par jour, noms proches, noms d'organisations) s'appliquent ensuite comme à tout salon. Une création retenue pour l'équipe (`202 held`) a déjà passé la règle du collectif ; elle n'est pas revérifiée à l'approbation.

**Mobilisation ou appel portant `ppf-collective`** (`AgirRules` vérifie la forme, `AgirSpace.route` appelle `CollectiveRoles`) :

| Cas | Réponse |
|---|---|
| Plusieurs étiquettes, ou identifiant invalide | `400 missing_field` (message `ppf-collective`) |
| Aucun collectif de ce nom (salon inconnu, ou salon ordinaire) | `404 collective_not_found` |
| Auteur ni fondateur, ni `admin`, ni `moderator` du salon (état de `GroupModeration` à la réception) | `403 collective_role_required`, sauf l'annulation ou la clôture par son auteur de ce qu'il a déjà publié ou proposé (`AuthorChanges`) |
| Autorisé | règles habituelles d'« Agir » : publié pour une clé éditoriale ou une organisation vérifiée, retenu pour relecture sinon |

L'équipe éditoriale n'a pas de passe-droit : elle aussi a besoin d'un rôle dans le collectif pour publier en son nom.

## 4. Sur l'appareil

Code : `shared/ui/.../ui/agir/collectives/` (`CollectivesLibrary`, `CollectivesScreen`, `CollectivePage`, `CollectiveCreationWizard`), textes dans `strings_collectifs.xml`.

- **Annuaire** : les collectifs sont les salons de `GroupsLibrary` (celle de l'interface, partagée avec « Messages ») qui portent le marqueur ; aucune requête de plus. Recherche (nom, territoire, présentation, thèmes), filtres par thème (les plus fréquents), type et portée, « près de chez moi » (`CollectiveQuery`). Les collectifs secrets n'apparaîtraient jamais (il n'en existe pas à l'étape 1).
- **« Près de chez moi »** : la commune choisie dans « Mes élus » (`ElusStore.commune()`, table locale), puis les territoires qui la contiennent, lus dans le référentiel sur l'appareil (`Territoires.find`). Un collectif est « près » si son `ppf-territory-code` est l'un d'eux (France exclue). Sans commune choisie, le filtre est caché. Rien n'est envoyé.
- **Page** : rôles publics calculés sur l'appareil à partir du journal public du salon (`RoomModeration.compute`, après `GET /api/v1/events?kind=9000…&tag=h:<id>`, comme la vue d'un salon) ; les événements et les appels du collectif sont ceux que l'agenda et le mur ont déjà reçus (aucune requête par collectif : le serveur ne sait pas quelle page on regarde). L'appareil fait confiance au serveur pour la règle des rôles sur ces contenus.
- **Rejoindre** : `GroupsLibrary.join` (local), avec la phrase « Rejoindre ne publie rien ».
- **Composeurs** : depuis la page, « Proposer un événement du collectif » et « Lancer un appel du collectif » ouvrent les formulaires existants de l'agenda et des appels, avec une propriété `collective` qui ajoute l'étiquette et un encadré « Publié au nom du collectif ». Sans elle, rien ne change dans ces formulaires.
- **Assistant** : le niveau vient du dernier statut reçu par le vote (`LocalVoting`, `VoterStatus.level`), sinon d'un `UrneRemote.status` signé par le compte (seul son propre niveau revient). Quatre étapes ; charte de PPF décochée par défaut ; mise en garde « Votre pseudonyme sera publiquement relié à ce collectif » (RGPD, article 9) ; « Annuler » avant « Créer le collectif » ; bouton grisé pour un compte compromis (S-146, `LocalCanPublish` et `PublishBlockedNotice`). L'envoi passe par `GroupsLibrary.create` ; les réponses du serveur sont montrées par les vues du formulaire des salons (`CreationErrorView`, `HeldView`). Un refus propre aux collectifs (`collective_*`) arrive comme `RejectionReason.REFUSED` : l'assistant ajoute une phrase qui renvoie vers « Mon niveau de votant » (voir la question 4).
- **Exemples** : deux collectifs [EXEMPLE] et un événement [EXEMPLE] au nom du premier, stockés par le serveur de développement au démarrage (`server/src/test/.../agir/collectives/DevCollectives.kt`), comme les salons d'exemple. Aucun fichier de contenu embarqué nouveau.

## 5. Accroches dans les fichiers des autres pôles

Toutes de quelques lignes, sans changement de comportement pour les salons ordinaires :

| Fichier (pôle) | Accroche |
|---|---|
| `server/.../groups/GroupDirectory.kt` (Communauté) | paramètre `collectives: CollectiveRules = CollectiveRules()` du constructeur (ligne 35) et un appel dans `admit`, juste après le refus `group_taken` (ligne 140) ; par défaut, aucune création de collectif n'est acceptée |
| `server/.../Application.kt` (commun) | `val urneStore` déplacé avant la construction des salons (ligne 425, sans autre changement) et `collectives = CollectiveRules(urneStore, accountTypes, compromised)` (ligne 437) |
| `server/src/test/.../DevServer.kt` (commun) | une ligne qui stocke les collectifs d'exemple, après les salons d'exemple |
| `shared/ui/.../groups/GroupsLibrary.kt` (Communauté) | champ `extraTags` de `GroupDraft` (ligne 70, vide par défaut) et son ajout aux étiquettes dans `create` (ligne 503) |
| `shared/ui/.../groups/GroupCreationForm.kt` (Communauté) | `HeldView` et `CreationErrorView` passent de `private` à `internal`, pour être réutilisées par l'assistant |
| `shared/ui/.../shell/AdaptiveShell.kt` (commun) | deux arguments `agendaLibrary` et `callsLibrary` passés à `AgirScreen` |

Fichiers d'« Agir » modifiés : `AgirScreen.kt` (route `collectifs` vers l'annuaire, nouvelle route `salons` pour les salons ordinaires, retour système), `AgirHome.kt` (étiquette de test `agir-rooms`), `strings_agir.xml` (texte de la carte « Collectifs »), `MobilizationComposer.kt`, `CallComposer.kt` (propriété `collective`), `ProposeMobilizationScreen.kt`, `CallComposerScreen.kt` (encadré), `server/.../agir/AgirRules.kt` (forme de l'étiquette), `server/.../agir/AgirSpace.kt` (règle des rôles), `AgirScreenTest.kt` (le test de la carte « Collectifs » suit l'annuaire puis les salons).

## 6. Ce qui reste sur l'appareil, ce qui part au serveur

- **Publié** (public, signé par le fondateur) : la description du collectif. Le fondateur est donc publiquement relié au collectif ; l'assistant le dit avant le bouton.
- **Jamais envoyé** : l'appartenance (table locale `group_membership`, déjà au registre), la recherche, les filtres, la commune de « près de chez moi ».
- **Envoyé pour vérifier le niveau** : seulement quand l'assistant s'ouvre sans statut connu, une requête `GET` du statut de vote signée par le compte ; le serveur connaît déjà ce compte et son niveau.
- **Effacement rapide (S-44)** : rien de nouveau sur l'appareil ; l'appartenance aux collectifs est celle des salons, couverte par la future partie `communaute-groups` du pôle Communauté (aucune partie d'effacement ajoutée ici).

## 7. Menaces (reportées dans `docs/THREAT_MODEL.md`)

| Menace | Réponse à l'étape 1 |
|---|---|
| Faux collectifs créés en série pour occuper des noms ou usurper un mouvement | Création réservée aux comptes validés en personne, vérifiée par le serveur ; garde-fous des salons (3 par jour, noms proches, noms d'organisations retenus pour l'équipe) ; détection des manipulations (S-90) à venir |
| Fondateur exposé (engagement politique, syndical ou associatif) | Mise en garde avant la création ; aucune donnée d'identité dans la description ; le pseudonyme seul |
| Appartenance révélée | Jamais publiée ni envoyée ; nombre de membres non affiché ; liste chiffrée seulement à l'étape 2 (question 1) |
| Contenus publiés au nom d'un collectif par un inconnu | Étiquette acceptée seulement du fondateur, des administrateurs et des modérateurs, à la réception |
| Détournement d'un collectif par changement de portée, de territoire ou de visibilité | Ces étiquettes ne changent jamais ; seule la clé du fondateur republie la description |
| Compte compromis qui fonde un collectif | Refusé (S-146) |

## 8. Pas fait à l'étape 1

- S-133 : liste des membres chiffrée sur le serveur, appartenance visible des administrateurs (question 1).
- S-132 : adhésion sur demande ou sur invitation, file des demandes, invitation par lien ou code QR (question 1).
- Collectifs privés et secrets, salons chiffrés de bout en bout (question 2).
- S-131 : statuts personnalisés (référent, trésorier, porte-parole) (question 3) ; deux administrateurs recommandés.
- S-134 : canal d'annonces et commissions avec leurs salons.
- S-135 : suspension ou fermeture d'un collectif par PPF ; modération assistée par IA des contenus publics.
- S-136 : transmission du rôle de fondateur, export, dissolution ; vote si tous les administrateurs sont partis.
- AG-51 à AG-53 : décider ensemble, alliances entre collectifs, du collectif à l'association.
- S-150 : réunion de collectif clé en main.
- Reprise d'un salon existant par un collectif (`docs/integration/salons-reprise.md`).
- Image du collectif, nombre maximal de membres.

## 9. Questions pour le chef d'équipe

1. **Liste des membres pour l'adhésion sur demande ou sur invitation (point d'attention n° 26, S-133).**
   - Option A : rien n'est envoyé, l'appartenance reste sur l'appareil (comportement de l'étape 1). La plus protectrice ; mais les administrateurs ne connaissent pas leurs membres, donc pas d'adhésion sur demande.
   - Option B : la demande et l'acceptation sont des messages privés NIP-17 enveloppés (NIP-59) adressés aux administrateurs, avec la messagerie privée existante (aucune cryptographie nouvelle) ; chaque appareil d'administrateur garde la liste ; un instantané chiffré (NIP-44 pour chaque administrateur, type 30078) est déposé sur le serveur pour synchroniser leurs appareils. Le serveur voit qu'un compte écrit aux administrateurs, pas ce qu'il écrit.
   - Option C : attendre MLS (D-027).
   - **Recommandation** : A maintenant, B à l'étape 2.
2. **Collectifs privés et secrets** : ils demandent des salons chiffrés de bout en bout (MLS plus tard, D-027). **Recommandation** : public seulement à l'étape 1, dit honnêtement dans l'assistant (« bientôt »), ce qui est fait.
3. **Statuts personnalisés** (référent, trésorier, porte-parole) : publiés, ils révéleraient l'appartenance. **Recommandation** : à l'étape 2, montrés seulement si le membre le choisit, après une mise en garde (principe 10).
4. **Codes de refus précis dans l'application** (question technique, Directeur technique) : `RejectionReason` (`core/publication`) ne connaît pas les codes `collective_*`, qui arrivent comme `REFUSED`. L'assistant vérifie tout avant l'envoi, donc ce refus ne devrait arriver que si le niveau a changé entre-temps. **Recommandation** : ajouter `COLLECTIVE_NEEDS_VERIFIED` et `COLLECTIVE_IMMUTABLE` à `RejectionReason` et à `ServerPublicationPort` lors d'un prochain passage du Directeur technique ; non fait ici pour ne pas toucher à ce fichier commun.

## 10. À faire au moment de compiler

- `gradlew :shared:core:jvmTest --tests "*CollectiveTest*"`, `gradlew :server:test --tests "*CollectivesApiTest*"`, `gradlew :shared:ui:jvmTest --tests "*AgirCollectivesTest*" --tests "*AgirScreenTest*"`, puis les tests des salons (`*CollectivesScreenTest*`, `*GroupsLibraryTest*`, `*GroupDirectoryTest*`, `*GroupGuardrails*`, `*GroupModerationApiTest*`) et ceux d'« Agir » (`*ProposeMobilizationScreenTest*`, `*CallComposerTest*`, `*AgirPublicationApiTest*`), enfin `gradlew build`.
- Points dont l'écriture n'a pas pu être vérifiée par un compilateur : l'inférence de type de `mutableStateOf` pour les vues scellées (même forme que `GroupsLibrary`), `this@AgirCollectivesTest::library.isInitialized` dans le test d'interface, l'import avec alias `typeLabelResource as callTypeLabel` dans `CollectivePage.kt`, les propriétés `Res.string.collectifs_*` générées depuis `strings_collectifs.xml`.
- Captures d'écran (`PPF_SCREENSHOTS_DIR`) de l'annuaire, d'une page et de l'assistant sur téléphone clair et sombre et sur ordinateur, à ajouter au test de captures d'« Agir ».
- Prévenir Communauté des accroches de la section 5 et le chef d'équipe de la question 4.

## 11. Tests écrits (non exécutés)

| Fichier | Ce qu'il vérifie |
|---|---|
| `shared/core/src/commonTest/.../agir/collectives/CollectiveTest.kt` | ordre des étiquettes, refus à la construction, un code par défaut, lecture tolérante, ce qui ne change jamais, étiquette d'une mobilisation, recherche et « près de chez moi » |
| `server/src/test/.../agir/collectives/CollectivesApiTest.kt` | création réservée aux comptes validés en personne (et aux élus certifiés), codes `400` précis, `409 collective_immutable`, `group_taken`, rôles exigés pour publier au nom du collectif, équipe éditoriale comprise, annulation par un auteur qui a perdu son rôle, compte compromis et compte de collectif, règle sans stockage des urnes |
| `shared/ui/src/jvmTest/.../agir/collectives/AgirCollectivesTest.kt` | annuaire limité aux collectifs, filtres par thème, portée et type, « près de chez moi », filtre caché sans commune ; compte non validé : explication, pas d'assistant ; sans compte : demande d'un compte ; compte validé : création après la charte et la mise en garde, « Annuler » avant « Créer le collectif », étiquettes publiées, appartenance gardée sur l'appareil ; page : événements du collectif seulement, rejoindre sans rien publier, compositeurs pour le fondateur et un modérateur, jamais pour un autre membre |
| `shared/ui/src/jvmTest/.../agir/AgirScreenTest.kt` (modifié) | la carte « Collectifs » ouvre l'annuaire, puis les salons, avec les retours |

## 12. Comment tester (reporté dans `docs/GUIDE_PROPRIETAIRE.md`)

Voir la section « Tester les collectifs dans « Agir » » du guide. En résumé : serveur de développement, application de bureau reliée, « Agir », « Collectifs » : deux collectifs [EXEMPLE] apparaissent ; la page du collectif des cyclistes montre une balade [EXEMPLE] ; « Créer un collectif » montre l'explication tant que le compte n'est pas validé en personne.
