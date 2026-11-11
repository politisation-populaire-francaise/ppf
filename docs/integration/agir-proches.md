# Note d'intégration : « Mes proches à mobiliser » (AG-63), étape 1

Rédigée le 9 novembre 2026 par un agent de la session « Agir » (branche `worktree-agent-a2012756a6e73d786`, partie de cbab9a68). Le bloc de numéros de décisions d'« Agir » est épuisé : la décision de conception est écrite en section 9 (« Décision à numéroter »), à numéroter par le chef d'équipe et à reporter dans `docs/DECISIONS.md`.

> **Rien n'est compilé ni testé.** Les compilations de branches étaient interdites ce jour-là (machine fragile). Le code et les tests ont été écrits en lisant les sources réelles de chaque API appelée (section 13) ; ils doivent être compilés et lancés (`gradle-safe.ps1`, section 12) avant toute entrée dans main.

## 1. Ce qui est fait

Cahier fonctionnel, AG-63 (ligne 405) : « Liste privée, sur l'appareil, de quelques proches (surnom, sans import du carnet d'adresses), avec un contenu adapté, un rappel et une étape suivante. » Dépendance notée : « Opinions supposées d'autrui (RGPD, article 9) : jamais envoyées, mise en garde ».

1. **Une rubrique de l'accueil d'« Agir »**, « Mes proches à mobiliser » (icône `UsersRound`), placée après « Je veux aider » et avant « Collectifs ». Route `AgirRoute.PROCHES` (`proches`).
2. **Mise en garde avant le premier proche** : tant que la liste est vide, « Ajouter un proche » affiche d'abord « Avant de commencer » : ce qui est gardé, que la liste reste sur l'appareil, n'est jamais envoyée ni liée à un compte, que l'effacement rapide l'efface, et que noter ce qu'une autre personne pourrait penser est sensible (« Un surnom suffit », « des thèmes, pas des opinions »). **« Annuler » avant « Commencer ma liste »** ; « Annuler » ne garde rien ; « Commencer ma liste » ouvre le formulaire, et rien n'est écrit avant « Enregistrer ».
3. **Le formulaire** : surnom (40 caractères au plus, compteur, « Une initiale ou un mot à vous suffit, jamais son nom complet ») ; trois sujets au plus parmi les quinze thèmes des contenus (puces à cocher, les autres se grisent au troisième) ; la prochaine étape sur une **échelle fermée en boutons radio** (« En parler », « Partager un contenu », « L'inviter à une mobilisation », « Y aller ensemble », « C'est fait ») ; le rappel en boutons radio (« Pas de rappel », « Demain », « Dans 3 jours », « Dans une semaine », « Dans deux semaines », « Dans un mois », la date sous chaque choix). Le formulaire vit en mémoire seulement.
4. **La liste** : d'abord « À faire aujourd'hui » (les proches dont la date de rappel est arrivée, la plus ancienne d'abord, avec un badge), puis « Ma liste » dans l'ordre d'ajout, ceux à « C'est fait » à la fin ; « Votre liste : 3 sur 12 » ; « Ajouter un proche » tant qu'elle n'est pas pleine. Chaque carte : le surnom en titre (pour les lecteurs d'écran), la prochaine étape, les sujets, la date du rappel, « Passer à : … » (l'étape suivante), « Modifier », « Retirer » (avec confirmation dans la carte).
5. **Contenus adaptés** : sous chaque carte, « Des contenus sur ses sujets » : quatre au plus parmi les fiches pratiques, parcours, mobilisations à venir et appels ouverts **déjà sur l'appareil** ; l'ordre suit l'étape (section 6). Chacun a « Copier le lien » et, sur Android, « Partager » (feuille de partage du système).
6. **Effacement** : partie `agir-proches` de l'effacement rapide, inscrite par le shell juste après `agir-camera` ; « Effacer mes clés » efface aussi la liste (section 5).
7. **Adaptatif** : une colonne lisible (720 dp) sur téléphone et tablette ; sur grand écran, deux cartes par ligne à partir de 840 dp ; le formulaire et la mise en garde gardent 720 dp. Modes clair et sombre par les jetons de `PpfTheme`.

## 2. Ce qui est gardé, où, combien de temps

Un seul petit document JSON (`ProchesData`, version 1), comme le minuteur d'AG-55 : **aucune table, aucune migration**.

| Donnée | Valeur |
|---|---|
| `id` | 16 caractères hexadécimaux tirés au hasard : ne dit rien de la personne |
| `nickname` | ce que la personne a tapé, nettoyé (une ligne, sans caractère de contrôle, espaces resserrés, 40 caractères au plus sans couper un emoji) |
| `subjects` | 0 à 3 identifiants de thèmes de `RevendicationThemes.ALL` (`sante`, `logement`, `travail`…), dans l'ordre choisi ; tout autre texte est refusé |
| `step` | identifiant d'une étape de l'échelle (`en-parler`, `partager`, `inviter`, `y-aller-ensemble`, `fait`) |
| `reminder` | date ISO (`2026-11-12`) ou rien |

- **Où** : Android, préférence privée `ppf-proches` (`MODE_PRIVATE`, `allowBackup="false"` dans le manifeste) ; ordinateur, fichier `proches.json` du dossier privé de l'application (à côté de l'identité, écrit d'un seul coup par un fichier temporaire) ; iOS, rien à l'étape 1 (l'écran dit « Pas encore disponible sur cet appareil »). Avec une identité en mémoire (tests, aperçus), la liste reste en mémoire.
- **Jamais** : de nom complet demandé, de numéro, d'adresse, de contact du carnet d'adresses, de clé de compte, de date de création, d'opinion en texte libre, d'étiquette politique. La liste n'est liée à aucun compte et fonctionne sans compte.
- **Durée** : jusqu'à ce que la personne retire le proche ; quand la liste devient vide, la préférence ou le fichier est supprimé (pas même une liste vide n'est gardée) ; effacement rapide ; « Effacer mes clés ».
- **Un fichier abîmé ou écrit à la main** est lu en liste vide ou nettoyé entrée par entrée (sujets inconnus retirés, étape inconnue ramenée à « En parler », date impossible retirée, doublons et entrées sans surnom écartés, douze au plus).
- **Rien n'est envoyé** : le module ne fait aucun appel réseau. Les contenus proposés sont lus dans la copie locale (`LocalEventStore`), jamais demandés au serveur pour la liste. Un test l'affirme (section 11).
- Registre : `docs/REGISTRE_DONNEES.md`, ajout du 9 novembre 2026. Modèle de menaces : `docs/THREAT_MODEL.md`, ajout du même jour.

## 3. Pourquoi c'est ainsi (RGPD, article 9)

- Ce qu'une personne note des centres d'intérêt d'un proche, dans une application politique, peut laisser deviner les **opinions supposées d'un tiers**. L'article 9, paragraphe 1, du RGPD vise les données « qui révèle[nt] […] les opinions politiques ». Ce tiers n'a rien demandé et ne sait pas qu'il est dans la liste.
- Le RGPD ne s'applique pas aux traitements faits « par une personne physique dans le cadre d'une activité strictement personnelle ou domestique » (article 2, paragraphe 2, point c ; considérant 18). Une liste tenue par la personne, pour elle, sur son appareil, sans publication ni envoi, relève très probablement de ce cas. PPF, qui ne la reçoit jamais, n'en détermine pas les finalités pour son compte (article 4, point 7, « responsable du traitement ») : l'application est l'outil, pas le destinataire. **Ce raisonnement est à faire confirmer par l'avocat** (question 6) ; la conception ne dépend pas de sa réponse, elle est faite pour rester protectrice dans les deux cas.
- Sources : texte du RGPD reproduit par la CNIL, lu le 9 novembre 2026 (EUR-Lex ne répondait pas) : https://www.cnil.fr/fr/reglement-europeen-protection-donnees (considérant 18), https://www.cnil.fr/fr/reglement-europeen-protection-donnees/chapitre1 (articles 2 et 4), https://www.cnil.fr/fr/reglement-europeen-protection-donnees/chapitre2 (article 9).
- D'où les choix : minimisation (un surnom, des thèmes fermés, aucune opinion), rien sur le serveur, aucun lien avec le compte, mise en garde avant le premier proche (et de nouveau chaque fois que la liste redevient vide), effacements, et un lien partagé qui ne dit jamais pour qui.
- **Principe 10** : rien ici n'est un assouplissement de la protection (la liste n'est jamais envoyée ni rendue publique, et l'étape 1 ne propose aucune option qui le ferait). Si un jour la personne pouvait sauvegarder sa liste (sauvegarde chiffrée facultative du graphe social, point d'attention n° 26), ce serait un assouplissement avec mise en garde, « Annuler » et réversibilité.

## 4. Le rappel

- **Étape 1** : une date facultative ; le jour venu (heure de Paris, `ParisTime`), le proche passe en tête de la liste sous « À faire aujourd'hui », avec un badge. Un proche à « C'est fait » n'est jamais « à faire ». Aucune notification.
- **Plus tard**, avec les rappels de l'appareil de « Décider » (`ReminderStore`, contrat `docs/integration/rappels.md` sur la branche `feature/decider-une-voix`, **pas encore dans main**) : à l'enregistrement d'un proche avec une date, `schedule(Reminder(id = "agir:proches:<id>", owner = "agir:proches:<id>", category = MOBILISATIONS, title = <texte neutre>, detail = "", target = "ppf:agir/proches", dueAt = <10 h, heure de Paris, ce jour-là>))` ; `remove("agir:proches:<id>")` au retrait du proche, au changement de date et à « Pas de rappel » ; l'effacement de la liste retire tous les rappels de préfixe `agir:proches:` (la partie `core-reminders` les efface de toute façon). Les constantes sont prêtes : `ProchesState.REMINDER_OWNER_PREFIX` et `ProchesState.TARGET`. Le lien `ppf:agir/proches` se branche dans `openEntry` d'`AdaptiveShell` (ouvrir « Agir » sur la rubrique).
- **Le texte de la notification ne contient jamais le surnom** ni les sujets (par exemple « Un rappel de votre liste ») : un écran verrouillé ne doit rien révéler, même si la personne a autorisé les détails. Rien de ce branchement n'est écrit : le code ne dépend pas de `ReminderStore`.

## 5. Effacement (S-44) et « Effacer mes clés »

- Partie **`agir-proches`** (`ProchesState.ERASABLE_ID`), inscrite dans `AdaptiveShell` juste après `agir-camera` : `ProchesState.eraseFromDevice()` vide le stockage, relit pour vérifier que la liste est vide (sinon lève une erreur, et l'effacement dit « Parties non effacées »), puis oublie la liste affichée, le formulaire en cours et la mise en garde acceptée. Elle attend la fin et peut être relancée.
- **« Effacer mes clés »** : `rememberProchesState` suit l'état du compte comme le minuteur (`AccountMemory`) ; quand des clés vues pendant la session sont effacées, la liste est effacée aussi (`forget()`, qui n'arrête jamais l'application en cas d'échec). Une personne sans compte garde sa liste ; elle n'est jamais rattachée au compte pour autant.
- `docs/integration/agir-securite-appareil.md` et le test `DeviceSecurityTest` comptent `agir-proches` parmi les parties attendues.

## 6. Contenus adaptés et partage (S-37)

- **Ce qui est proposé** (`ProcheCandidates.fromDevice`, `core/agir/proches/ProcheSuggestions.kt`) : les fiches pratiques et parcours signés par l'équipe éditoriale ; les mobilisations à venir, ni annulées ni passées ; les appels ouverts dont l'échéance n'est pas passée. Sont écartés : ce qui attend encore le serveur (`pending`), les mobilisations et appels de la personne elle-même (ils peuvent attendre la relecture, leur adresse ne mènerait nulle part), les contenus d'un éditeur inconnu. Lu à chaque ouverture de la rubrique, sur un fil de travail.
- **Correspondance avec les sujets** (`ProcheSuggestions.themesOf`) : un contenu touche un thème quand l'un de ses sujets `t` le nomme (`RevendicationTheme.matches`, alias compris : « énergie » pour « pouvoir d'achat »), ou quand son titre ou son résumé emploie le nom du thème ou l'un de ses alias en mots entiers (les mobilisations et les appels n'ont pas de sujets : « Marche pour l'hôpital public » touche « santé », « hausse des loyers » touche « logement »). Comparaison de mots, pas de sens (même limite que `FrenchText`).
- **Ordre selon l'étape** : « En parler » et « Partager un contenu » : parcours, fiches, appels, mobilisations ; « L'inviter », « Y aller ensemble » et « C'est fait » : mobilisations (la plus proche d'abord), appels, fiches, parcours. Puis le nombre de sujets touchés, la date, le titre. Quatre au plus, deux d'une même sorte avant que les autres aient leur tour. Pas de sujet : « Choisissez un ou deux sujets pour voir des contenus adaptés ».
- **Partager** : le mécanisme existant de l'application. Il n'y a pas de bouton « Partager » commun (S-37 est « en partie ») : j'ai repris la feuille de partage du système déjà écrite pour le rapport de plantage (`rememberTextSharer`, Android seulement ; rien sur ordinateur) et la copie dans le presse-papiers des appels (`LocalClipboardManager`). Le texte partagé (`ProcheSuggestion.shareText()`) : le titre (avec « [EXEMPLE] » pour un exemple) et **l'adresse publique stable** (`ComprendreLinks` pour les fiches et parcours, `AgirLinks` d'AG-61 pour les mobilisations et appels), **sans paramètre ni fragment**. Jamais le surnom, jamais pour qui, jamais qui partage.

## 7. Accessibilité

- Titres de section, titre de page et surnom de chaque carte annoncés comme titres ; l'échelle et le rappel en boutons radio dans des groupes (`selectableGroup`) ; les sujets en cases à cocher avec leur état (`PpfSelectableChip`) ; la mise en garde, les erreurs et « Lien copié » dans des régions annoncées ; cibles d'au moins 44 à 48 dp (composants communs) ; état jamais dit par la couleur seule (« À faire aujourd'hui » en texte) ; le retour du système ferme d'abord le formulaire ou la mise en garde.
- Textes dans `strings_proches.xml` (français, vouvoiement, apostrophe typographique, pas de tiret cadratin).

## 8. Limites (dites honnêtement)

- **Rien n'est compilé** (bandeau en tête).
- **Pas de notification** à la date du rappel (section 4).
- **Pas de chiffrement propre à PPF** : la liste est dans le stockage privé de l'application (chiffré par Android quand le téléphone est verrouillé), pas sous une clé du Keystore comme la caméra protégée. Un effacement ne garantit donc pas que la mémoire flash ne garde aucun ancien bloc (question 1).
- **iPhone** : pas de liste (l'écran le dit).
- Les adresses des mobilisations et appels (`/agir/agenda/…`, `/agir/appels/…`) n'ont pas encore leur page sur le site (note d'AG-61) : le lien est stable mais mène pour l'instant à une page absente.
- Les mobilisations et appels n'apparaissent que si l'agenda ou les appels ont été ouverts au moins une fois (c'est là que l'appareil les reçoit) ; la ligne « Pas encore de contenu sur ces sujets » le dit.
- La correspondance par mots peut manquer un contenu (« repas scolaire » n'est pas « cantine ») ou en prendre un de trop.

## 9. Décision D-194

**« Mes proches à mobiliser » (AG-63), conception de l'étape 1.** Contexte : cahier fonctionnel, AG-63 (ligne 405), avec la dépendance « Opinions supposées d'autrui (RGPD, article 9) : jamais envoyées, mise en garde » ; conception donnée par la session « Agir » le 9 novembre 2026. Choix :

1. **Sur l'appareil seulement**, un petit JSON dans le stockage privé existant (même modèle que le minuteur d'AG-55), sans table ni migration, sans serveur : douze proches au plus ; pour chacun un surnom tapé (40 caractères, « un surnom suffit », jamais de nom complet demandé), jusqu'à trois sujets choisis parmi les thèmes des contenus (`RevendicationThemes`, jamais de texte libre sur les opinions, jamais d'étiquette politique), la prochaine étape sur une échelle fermée (« En parler », « Partager un contenu », « L'inviter à une mobilisation », « Y aller ensemble », « C'est fait ») et une date de rappel facultative. Ni numéro, ni contact, ni import du carnet d'adresses, ni clé de compte : la liste n'est liée à l'identité de personne.
2. **Mise en garde d'abord** (RGPD, article 9) avant le premier proche : ce qui est gardé, que cela reste sur l'appareil, n'est jamais envoyé et part avec l'effacement rapide, que noter ce qu'une autre personne pourrait penser est sensible et qu'un surnom suffit ; « Annuler » avant « Commencer ma liste ».
3. **Contenu adapté** : les fiches, parcours, mobilisations à venir et appels déjà sur l'appareil qui touchent les sujets, dans un ordre qui suit l'étape, avec « Partager » (feuille de partage existante) et « Copier le lien » (adresse publique stable sans paramètre), sans jamais dire pour qui.
4. **Rappel** : à l'étape 1, en tête de liste (« À faire aujourd'hui ») ; la notification se branchera sur les rappels de l'appareil de « Décider » (propriétaire `agir:proches:`), sans le surnom dans le texte.
5. **Effacement** : partie `agir-proches` de l'effacement rapide ; « Effacer mes clés » efface aussi la liste.
6. **Entrée** : une rubrique de l'accueil d'« Agir ».

Options écartées : une table de la base locale (migration, numéros disputés entre équipes) ; un champ libre « ce qu'il ou elle pense » (opinion supposée d'un tiers en clair) ; l'import du carnet d'adresses (données de tiers, permission sensible) ; une synchronisation par le serveur (la liste quitterait l'appareil). Raison : donner un outil concret pour entraîner ses proches pendant le mouvement, sans jamais garder ni transmettre ce que l'on suppose des opinions d'autrui.

## 10. Questions pour le chef d'équipe (avec recommandations)

1. **Chiffrer la liste** par une clé de l'Android Keystore (comme la caméra protégée, effacement cryptographique) ? **Recommandation** : oui, à l'étape 2, en réutilisant le mécanisme du coffre d'AG-58 une fois compilé et testé ; ce n'est pas fait à l'étape 1 pour ne pas ajouter de code de plateforme non compilé.
2. **Notification à la date du rappel** : la brancher sur `ReminderStore` dès qu'il est dans main ? **Recommandation** : oui, catégorie « Mobilisations », titre neutre « Un rappel de votre liste », aucun détail, et un mot sur l'écran avant d'allumer la catégorie (contrat de `rappels.md`).
3. **Rappeler le verrouillage de PPF** sur l'écran de la liste quand il n'est pas activé (« Protégez cette liste : activez le verrouillage ») ? **Recommandation** : oui, une ligne avec un bouton vers « Mon espace », comme « Préparer mon téléphone ».
4. **Effacer la liste avec les clés** : c'est ce que la conception demande, mais la liste n'est pas liée au compte ; une personne qui efface ses clés pour en recréer pourrait être surprise. **Recommandation** : garder l'effacement (protection par défaut) et le dire dans la confirmation d'« Effacer mes clés » (texte de « Communauté », à leur demander).
5. **Montrer les rappels arrivés sur l'accueil d'« Agir »** (une ligne discrète « 1 proche à recontacter aujourd'hui ») ? **Recommandation** : pas à l'étape 1 (l'accueil se lit par-dessus l'épaule) ; à décider avec la propriétaire après la phase de test.
6. **Lecture juridique** (section 3) : faire confirmer par l'avocat que la liste relève de l'exception domestique et que PPF n'en est pas responsable du traitement. **Recommandation** : l'ajouter à la liste de relecture de `ROADMAP.md` ; aucun changement de code n'en dépend.
7. **Sauvegarde de la liste** (avec la sauvegarde chiffrée facultative du graphe social, point d'attention n° 26) ? **Recommandation** : plus tard, désactivée par défaut, avec mise en garde du principe 10.

## 11. Comment tester

Guide pas à pas pour la propriétaire : `docs/GUIDE_PROPRIETAIRE.md`, « Tester « Mes proches à mobiliser » » (9 novembre 2026), une fois compilé. Tests automatiques (écrits, pas lancés) :

- `gradlew :shared:core:jvmTest --tests "*ProchesDataTest*" --tests "*ProcheSuggestionsTest*" --tests "*ProcheCandidatesTest*"` : aller-retour JSON, fichier abîmé ou écrit à la main nettoyé, douze proches, 40 caractères (sans couper un emoji), sujets de la liste fermée seulement, échelle, ordre (rappels arrivés d'abord, « C'est fait » à la fin), magasin qui n'écrit que ce qui change et supprime une liste vide, rien du surnom dans les journaux ; correspondance par sujets `t` et par mots du titre, ordre selon l'étape, quatre au plus et deux d'une sorte, texte partagé ; lecture de l'appareil (éditeur inconnu, événement annulé ou passé, appel clos ou échu, propositions de la personne et contenus en attente écartés ; adresses stables).
- `gradlew :shared:ui:jvmTest --tests "*ProchesScreenTest*" --tests "*DeviceSecurityTest*" --tests "*AgirScreenTest*"` : mise en garde avant le premier proche et « Annuler » qui ne garde rien (360, 700 en sombre, 1000 dp), « Annuler » d'abord ; surnom obligatoire ; troisième sujet qui grise les autres ; ajout, étape suivante, copie du lien sans le surnom, retrait après confirmation ; aucune publication et aucune requête qui porte le surnom ; rappels arrivés en tête ; deux cartes par ligne sur ordinateur, empilées sur téléphone ; effacement rapide qui vide la liste ; « Effacer mes clés » qui l'efface ; `agir-proches` parmi les parties du shell.

## 12. À faire au moment de compiler

- Compiler `:shared:core` et `:shared:ui` (Android, ordinateur et klib iOS), lancer les tests de la section 11, puis le build complet selon la règle de l'équipe.
- Vérifier que `Res.string.proches_*` sont bien générés (nouveau fichier `strings_proches.xml`) et que `%1$d` s'y formate.
- Vérifier l'appel de `rememberTextSharer` (déclaré `internal expect` dans `ui/crash`, même module) depuis `ui/agir/proches`.
- Si `twoCardsShareARowOnADesktopAndStackOnAPhone` échoue d'un pixel sur le haut des cartes, comparer `abs(first.top - second.top) < 1f`.
- Si l'attente des contenus groupés (`harness.library.guides.isNotEmpty()`) ne suffit pas pour qu'une suggestion apparaisse, ouvrir la rubrique une seconde fois (la lecture se fait à chaque ouverture).
- `tools/check-apk.sh` : rien de nouveau à exiger (aucun fichier embarqué ajouté).

## 13. Fichiers et API lues pour écrire sans compiler

Créés (pôle « Agir ») :
- `shared/core/.../core/agir/proches/Proches.kt` (`ProcheStep`, `Proche`, `ProcheInput`, `ProcheChange`, `ProchesRules`, `ProchesData`, `ProchesStorage`, `InMemoryProchesStorage`, `ProchesStore`) ;
- `shared/core/.../core/agir/proches/ProcheSuggestions.kt` (`SuggestionKind`, `ProcheSuggestion`, `ProcheSuggestions`, `ProcheCandidates`) ;
- `shared/ui/.../ui/agir/proches/ProchesState.kt`, `ProchesScreen.kt`, `ProchesEditor.kt` ; `ProchesStorage.android.kt`, `ProchesStorage.jvm.kt`, `ProchesStorage.ios.kt` ;
- `shared/ui/src/commonMain/composeResources/values/strings_proches.xml` ;
- tests : `shared/core/src/commonTest/.../core/agir/proches/ProchesDataTest.kt`, `ProcheSuggestionsTest.kt`, `shared/core/src/jvmTest/.../core/agir/proches/ProcheCandidatesTest.kt`, `shared/ui/src/jvmTest/.../ui/agir/proches/ProchesScreenTest.kt`.

Modifiés : `ui/agir/AgirHome.kt` (rubrique `PROCHES` dans `available`, entre `HELP` et `COLLECTIVES` : « Témoigner » reste en troisième position comme l'attend `AgirScreenTest`) ; `ui/agir/AgirScreen.kt` (route `PROCHES`, paramètre `proches`) ; **`ui/shell/AdaptiveShell.kt` (fichier commun à tous les pôles)** : la liste du shell, sa partie d'effacement après `agir-camera`, et un paramètre `proches` de `SpaceContent` passé à `AgirScreen` ; test `ui/agir/security/DeviceSecurityTest.kt` (`agir-proches` attendu) ; `docs/integration/agir-securite-appareil.md`, `REGISTRE_DONNEES.md`, `THREAT_MODEL.md`, `GUIDE_PROPRIETAIRE.md`, `CHANGELOG.md`, `ROADMAP.md`.

Lus et utilisés sans modification (autres pôles) : `core/revendications/RevendicationThemes.kt` (`ALL`, `matches`, `aliases`) et `ui/revendications/ThemeLabels.kt` (`themeLabel`) de « Revendications » ; `core/comprendre/ComprendreLinks.kt` (`pathOf(article)`, `urlOf(path)`) de « Comprendre » ; `ui/crash/CrashReportDialog.kt` (`internal expect fun rememberTextSharer(title: String): ((String) -> Unit)?`) ; `core/text/FrenchText.kt` (`fold`) ; `core/local/LocalEventStore.kt` (`byTag`, `recent`, `pending`) ; `core/agir/agenda/ParisTime.kt` (`epochDay`, `epochDayOf`, `dateOf`, `moment`) ; `core/time/CivilDate.kt` (`parseIso`, `toIso`, `toFrench`).

API d'« Agir » utilisées : `AgirLinks.urlOf(mobilization)` et `urlOf(call)` (AG-61) ; `Mobilization.from`, `displayTitle`, `example`, `cancelled`, `isPast(now)`, `start`, `summary` ; `Call.from`, `isOpen`, `deadline`, `dueAt`, `displayTitle`, `isExample` ; `RegisterErasable(dependencies, id) { }` ; `DeviceEraseRegistry.eraseAll()` et `ids`. Composants : `PpfCard(modifier, onClick, selected, contentPadding, content)`, `Inset`, `InfoBox(icon, modifier, content)`, `PpfBadge(text, style, modifier)`, `ExampleBadge()`, `PageHeader(title, description)`, `PpfPrimaryButton` et `PpfSecondaryButton(onClick, text, modifier, enabled, small, icon)`, `PpfTextButton(onClick, text, modifier, enabled, color)`, `PpfIconButton(icon, contentDescription, onClick, modifier, enabled, tint, badge)`, `PpfTextField(value, onValueChange, placeholder, modifier, minLines, singleLine, …)` (son `contentDescription` est le `placeholder`), `PpfSelectableChip(label, selected, onClick, modifier, enabled, leadingIcon)`, `PpfRadioRow(label, selected, onClick, modifier, supporting, enabled)`, `SystemBack(enabled, onBack)` ; icônes `Lucide.UsersRound`, `ShieldCheck`, `TriangleExclamationPoint`, `Lock`, `Plus`, `Pencil`, `ChevronRight`, `Share2`, `Copy`, `Check`, `BookOpen`, `Lightbulb`, `Calendar`, `Megaphone`, `Info`, `ArrowLeft`.
