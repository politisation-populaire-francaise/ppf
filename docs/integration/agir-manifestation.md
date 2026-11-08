# Note d'intégration : bouton AIDE et mode manifestation (M25 : AG-13, AG-27 première version, AG-36, AG-14)

Rédigée le 7 novembre 2026 par un agent de la session « Agir », branche `worktree-agent-a6bbc59bcf9fcaf8f` (partie de `feature/agir` 3051dc7, `feature/agir` dd50129 intégré). La session « Agir » fusionne, numérote les décisions proposées (section 5) et met à jour `DECISIONS.md`, `CHANGELOG.md`, `ROADMAP.md` et `GUIDE_PROPRIETAIRE.md`. Cette note a mis à jour elle-même `REGISTRE_DONNEES.md` et `THREAT_MODEL.md` (sections « Ajout du 7 novembre 2026 : bouton AIDE et mode manifestation »). La suite (alerte aux street medics, tableau de coordination) est **conçue sans être codée** dans `agir-secours-conception.md`.

## 1. Ce qui est fait

1. **Bouton AIDE** (AG-27, D-044) : couleur d'accent, mot « AIDE » toujours écrit avec la bouée, 56 dp de haut au moins, nom pour le lecteur d'écran « AIDE : urgence, avocat, point de ralliement », atteignable au clavier (c'est un bouton). Il est visible depuis tous les espaces :
   - téléphone : bouton flottant au-dessus de la barre du bas (emplacement `floatingActionButton` du `Scaffold`, jamais sur la barre) ;
   - tablette : en tête du rail de navigation (icône au-dessus du mot) ;
   - ordinateur : en tête de la barre latérale, sur toute sa largeur.
2. **Feuille d'urgence** (fenêtre par-dessus l'écran, `Dialog` : le lecteur d'écran y reste, « Retour » du système et la touche Échap ferment d'abord une fiche ouverte, puis la feuille), dans cet ordre :
   - **Urgence vitale** : « PPF ne remplace jamais les secours. Si une vie est en danger, appelez d'abord le 15 ou le 112. », puis deux gros boutons d'accent « Appeler le 15 » (SAMU) et « Appeler le 112 » (numéro européen), puis 17 (police secours), 18 (pompiers), 114 par SMS (personnes sourdes, malentendantes ou aphasiques) ; chaque bouton dit à quoi sert le numéro ; source citée ;
   - **Besoin d'un avocat** : les legal teams et réseaux d'avocats bénévoles de l'annuaire qui ont un téléphone, ceux du territoire d'abord, chacun appelé en un geste ; « Lire : que faire en garde à vue » (la fiche existante, lue dans la feuille, avec retour) ; « Alerter un avocat bénévole » annoncé **bientôt** ;
   - **Alerter les street medics** et **J'ai besoin d'aide pour sortir** : annoncés **bientôt**, désactivés, avec une phrase honnête (secouristes certifiés en personne, préparation avec les organisations ; en attendant, le 15 ou le 112) ;
   - **Mon point de ralliement** (AG-36) : un lieu en mots et des précisions, notés à l'avance, gardés sur l'appareil seulement, modifiables et effaçables en un geste.
   - Appel direct par `tel:` et `sms:` ouverts par le système (`LocalUriHandler`) : **aucune permission**, l'utilisateur confirme dans son application de téléphone. Sur ordinateur, où rien ne compose, la feuille le dit (« composez le numéro vous-même sur un téléphone »).
3. **Mode manifestation** (AG-13), rubrique d'Agir qui remplace la carte « En préparation » de l'accueil (sept rubriques ouvertes, cinq en préparation) : bouton « Ouvrir l'AIDE », encadré « [EXEMPLE] Textes à relire par un avocat », puis
   - **Avant de partir** : dix points [EXEMPLE] à cocher, en mémoire seulement ;
   - **Vos droits** : « Droits en manifestation », « Garde à vue : vos droits », « Filmer les forces de l'ordre », lues sur place (fiches de `content/fiches`, déjà hors ligne) ;
   - **Numéros utiles** : les cinq de la feuille, puis 119 (enfant en danger), 116 000 (enfant disparu), 116 006 (aide aux victimes), 31 14 (prévention du suicide) ;
   - **Legal team et avocats près de chez vous** : champ « commune, département ou code postal » en mémoire seulement ; par défaut, le département de la commune choisie dans « Comprendre » (Mes élus), lu sur l'appareil ;
   - **Point de ralliement** (le même que la feuille AIDE) ;
   - **Sécurité de votre téléphone** : trois conseils [EXEMPLE] et l'annonce de S-43 à S-45 (verrouillage, effacement rapide, application discrète), aucune fiche n'existant encore ;
   - **Après une blessure** (AG-14) : nouvelle fiche pratique [EXEMPLE] sourcée « Après une blessure en manifestation » (soins, certificat médical et ITT, preuves, plainte au commissariat, à la gendarmerie ou au procureur, signalement IGPN ou IGGN qui n'est pas une plainte, Défenseur des droits gratuit et ses trois voies, délais qui courent, 116 006, legal teams), et le renvoi vers « Témoigner ».
4. **Rien n'est envoyé** : ni le territoire, ni le point de ralliement, ni les cases cochées, ni les appels. La seule requête possible à l'ouverture d'AIDE est la synchronisation générale de l'annuaire (la même que « Organisations », sans aucune donnée de l'utilisateur). Aucune position n'est lue : aucune permission de localisation.

## 2. Fichiers

Nouveaux (pôle Agir) :
- `shared/core/.../core/agir/manifestation/EmergencyNumbers.kt` (numéros, ordre, `tel:`/`sms:`, source), `RallyPoint.kt` (point de ralliement, nettoyage, limites 200 et 500 caractères, encodage JSON, interface `RallyPointStorage`, `InMemoryRallyPointStorage`), `LegalContacts.kt` (legal teams et avocats avec téléphone, proches d'abord) ;
- `shared/ui/.../ui/agir/manifestation/HelpState.kt` (état partagé, `LocalHelpState`, `ProvideHelp`, `LocalRallyPointStorage`, `expect rememberPlatformRallyPointStorage`), `HelpSheet.kt` (`HelpButton`, `HelpSheetHost`), `ManifestationParts.kt` (numéros, avocats, point de ralliement, lecteur de fiche, « bientôt »), `ManifestationScreen.kt` ;
- `actual` : `androidMain/.../RallyPointStorage.android.kt` (préférence privée), `jvmMain/.../RallyPointStorage.jvm.kt` (`FileRallyPointStorage`, fichier remplacé en un seul déplacement), `iosMain/.../RallyPointStorage.ios.kt` (aucun stockage tant qu'iOS n'est pas compilé : la feuille invite à noter sur papier) ;
- `shared/ui/src/commonMain/composeResources/values/strings_manifestation.xml` ;
- `content/fiches/apres-une-blessure-en-manifestation.md` et `fiches.json` régénéré (seul JSON gardé après la signature) ;
- tests : `shared/core/src/commonTest/.../agir/manifestation/ManifestationCoreTest.kt`, `shared/ui/src/jvmTest/.../agir/manifestation/ManifestationTest.kt` ;
- `docs/integration/agir-manifestation.md` (cette note), `docs/integration/agir-secours-conception.md`.

Modifiés :
- `ui/agir/AgirHome.kt` (rubrique `MANIFESTATION` ouverte, retirée de « En préparation »), `ui/agir/AgirScreen.kt` (route `mode-manifestation`) ;
- **`ui/shell/AdaptiveShell.kt`** (fichier partagé, 15 lignes) : l'état `rememberHelpState`, `ProvideHelp` autour de l'interface, `HelpSheetHost`, le bouton dans le `Scaffold` du téléphone, l'en-tête du rail et la barre latérale ;
- tests d’Agir : `AgirScreenTest` (5 rubriques en préparation), `CallsLibraryTest` (6 fiches pratiques) ; tests de « Comprendre » (une ligne chacun, nombre de fiches intégrées 5 vers 6) : `shared/ui/.../content/ComprendreScreenTest.kt`, `web/.../comprendre/ComprendrePagesTest.kt` ;
- `docs/REGISTRE_DONNEES.md`, `docs/THREAT_MODEL.md`.

Aucune dépendance nouvelle, aucune icône nouvelle, aucune migration (locale ou Flyway), aucun hôte contacté.

## 3. Registre des données

Voir la section ajoutée à `REGISTRE_DONNEES.md`. En bref : le point de ralliement est la seule donnée écrite, sur l'appareil seulement (préférence privée Android sans sauvegarde du système, fichier privé sur ordinateur) ; le territoire et la liste cochée restent en mémoire ; la commune de « Comprendre » est lue sans être copiée.

**Pourquoi pas la base locale** : une table aurait demandé une migration SQLDelight (numéro à demander, tests de migration des autres pôles à compléter) pour une seule valeur. La préférence privée est le stockage normal d'Android pour cela et elle disparaît avec les données de l'application. Si l'effacement rapide (S-44) veut un seul geste pour tout, il appellera `HelpState.clearRallyPoint()` (ou `RallyPointStorage.clear()`).

## 4. Menaces

Voir la section ajoutée à `THREAT_MODEL.md` : retard des secours (15 et 112 d'abord, rien de « bientôt » ne se presse), numéro erroné (source officielle, tests), téléphone saisi (point de ralliement effaçable, S-43 et S-44 ensuite), fuite du territoire (mémoire seulement, testé), faux numéro de legal team (fiches signées et vérifiées seulement), conseils juridiques erronés ([EXEMPLE], avocat), position des forces de l'ordre (aucune donnée de ce genre).

## 5. Décisions proposées (à numéroter par la session « Agir » : bloc D-053, D-081 à D-089)

Prises en autonomie, techniques et gratuites :
1. **Emplacement du bouton AIDE** : flottant au-dessus de la barre du bas sur téléphone (D-044), en tête du rail sur tablette, en tête de la barre latérale sur ordinateur ; toujours visible, y compris dans « Mon espace » et les messages.
2. **Feuille en fenêtre** (`Dialog`) plutôt qu'en panneau du bas : même rendu sur les trois formats, lecteur d'écran confiné à la feuille, retour et Échap gérés, testable.
3. **Ordre de la feuille** : urgence vitale (15, 112, puis 17, 18, 114 par SMS), avocat, street medics et sortie (bientôt), point de ralliement. Les street medics sont placés avant la sortie, plus urgents.
4. **Numéros** : ceux de service-public.gouv.fr (F33954, vérifiée le 25 août 2026) ; 3919 non retenu (numéro d'écoute « non urgent » pour les violences faites aux femmes, sans lien direct avec la manifestation ; facile à ajouter), 119, 116 000, 116 006 et 31 14 retenus pour le mode manifestation.
5. **Point de ralliement** sur l'appareil, hors base locale (section 3), lieu en mots seulement.
6. **Fiches lues dans Agir** avec `MarkdownText` (composant public de « Comprendre », non modifié) plutôt qu'en renvoyant vers « Comprendre » : on garde la feuille d'urgence et le mode manifestation sous la main, et le retour ramène où l'on était.
7. **« Après une blessure »** écrite comme fiche pratique de `content/fiches` (signée par l'outil, lisible aussi dans « Comprendre »), la liste « Avant de partir » comme ressources de l'interface (cases à cocher).

## 6. Questions, avec recommandations (à transmettre au chef d'équipe)

1. **Le bouton AIDE partout, ou seulement pendant une manifestation ?** L'entrée « Maquettes de l'interface communautaire (bouton AIDE) » est encore en attente. Recommandation : **partout, tout le temps** (règle « en un geste » ; une urgence n'attend pas que l'application sache qu'une manifestation a lieu) ; plus tard, le rendre plus visible pendant une manifestation suivie.
2. **Le bouton flottant cache parfois la dernière ligne d'une liste sur téléphone.** Recommandation : garder le bouton visible et ajouter, écran par écran, un espace en bas des listes longues (fait pour le mode manifestation) ; les autres pôles peuvent le faire pour leurs écrans.
3. **Ajouter le 3919 et d'autres numéros d'écoute** au mode manifestation ? Recommandation : non pour l'instant (pas de lien direct), oui dans une page « Numéros d'écoute » commune à toute l'application si la propriétaire le souhaite.
4. **Relecture** : la liste « Avant de partir », les conseils de sécurité du téléphone et la fiche « Après une blessure » sont des [EXEMPLE] préparés avec l'aide d'une IA. Recommandation : les ajouter à la liste « à faire relire par un avocat » de la feuille de route et les faire relire aussi par une legal team et une organisation de street medics.
5. **Conception des street medics** : neuf questions dans `agir-secours-conception.md`, section 9 (attestation gardée par le medic, effacement à 2 heures, relais aveugle par cases de 5 km, seuils).

## 7. Comment tester

Tests ciblés (par le lanceur `gradle-safe.ps1`) :
- `:shared:core:jvmTest --tests "*ManifestationCoreTest*"` : ordre des numéros (15, 112, 17, 18, 114), adresses `tel:` et `sms:`, point de ralliement nettoyé et relu, legal teams proches d'abord ;
- `:shared:ui:jvmTest --tests "*ManifestationTest*"` : bouton présent sur 360, 700 et 1000 dp (au-dessus de la barre du bas sur téléphone) et feuille ouverte en un geste depuis l'application entière ; 15 et 112 en premier, appels `tel:15`, `tel:112`, `sms:114` ; fonctions « bientôt » désactivées ; legal teams de l'annuaire appelées en un geste, fiche « Garde à vue » lue dans la feuille ; point de ralliement enregistré, relu au lancement suivant, effacé ; mode manifestation (liste cochée, territoire « 59000 », fiche « Après une blessure ») ; **aucune requête ne contient ni le territoire ni le point de ralliement, rien n'est publié** ; thème sombre ; fichier du point de ralliement sur ordinateur ;
- `:shared:ui:jvmTest --tests "*AgirScreenTest*" --tests "*CallsLibraryTest*"`.

Pour la propriétaire (application de bureau, `gradlew :desktop:run`, ou APK de test) : toucher « AIDE » en bas à droite (téléphone) ou en haut de la barre de gauche (ordinateur) ; vérifier que « Appeler le 15 » et « Appeler le 112 » sont en tête ; sur le téléphone, toucher « Appeler le 15 » ouvre le téléphone avec le 15 composé **sans appeler** (raccrocher sans lancer l'appel) ; noter un point de ralliement, fermer l'application, la rouvrir : il est toujours là ; l'effacer. Puis « Agir », « Mode manifestation » : parcourir les sections, ouvrir « Après une blessure ». Essai sur téléphone : reporté à la phase de test (ROADMAP).
