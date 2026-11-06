# CLAUDE.md : Politisation Populaire Française

Ce fichier guide Claude Code dans le dépôt de la plateforme de Politisation Populaire Française (PPF) : site web, applications (Android, iOS, ordinateur, tablette) et serveur. Lis-le en entier au début de chaque session.

## 1. Le projet en bref

- **PPF** est un collectif citoyen, bientôt déclaré en association loi 1901. Sa mission : redonner l'espace politique aux citoyens français.
- La plateforme est organisée en **quatre piliers**, qui sont aussi les quatre espaces de navigation : **Voir**, **Comprendre**, **Agir**, **Décider**, précédés d'un accueil « En direct » et complétés par « Mon espace », le tableau de bord personnel de chaque utilisateur.
- **Contexte** : un mouvement social est en cours depuis septembre 2026, et l'élection présidentielle a lieu en avril 2027. La priorité est de livrer vite une première version utile, sans jamais sacrifier la sécurité.
- **Nom de domaine** : politisation-populaire-francaise.org (domaine principal), avec .fr, .com, .eu, .net et .app réservés pour protéger le nom.
- L'ancien nom « Marianne Libre » est abandonné pour des raisons juridiques : ne l'utilise **jamais**, nulle part (code, paquets, identifiants d'application, domaines, textes).

## 2. Où se trouve le travail

- **Dossier local du dépôt** : `C:\Users\clair\Documents\PPF` sur l'ordinateur de la propriétaire (Windows 11). Vérifié le 5 novembre 2026 et noté dans `docs/GUIDE_PROPRIETAIRE.md`.
- **Dépôt distant** : GitHub, **public dès le premier commit** (décision de la propriétaire du 5 novembre 2026, consignée dans `docs/DECISIONS.md`). Aucun secret, aucune configuration sensible n'y entre jamais. Miroir automatique sur Codeberg prévu par le cahier des charges (section 8), à mettre en place quand la propriétaire aura un compte Codeberg.
- **Liaison Git et GitHub** : Git, GitHub CLI, JDK 21 et le SDK Android sont installés (5 novembre 2026). Les commits sont signés par une clé SSH et portent l'auteur « PPF » avec l'adresse « noreply » fournie par GitHub, jamais l'adresse personnelle de la propriétaire. Double authentification obligatoire sur son compte GitHub : à activer (guide dans `docs/GUIDE_PROPRIETAIRE.md`) ; rappelle-le-lui tant que ce n'est pas fait.

## 3. Documents de référence

Ils se trouvent dans `docs/projet/` :

| Fichier | Rôle | Statut |
|---|---|---|
| `vision.md` | Pourquoi : constat, mission, piliers, valeurs | Définitif |
| `cahier-des-charges.md` | Exigences, architecture, hébergement, stack, cadre légal, planning, budget | Définitif |
| `cahier-fonctionnel.md` | Rôles, parcours, modules et fonctionnalités | Définitif (version 2.0 en préparation) |
| `brainstorming-*.md` | Idées, questions et décisions en attente (l'inventaire des fonctionnalités est réparti en plusieurs documents `brainstorming-cahier-fonctionnel-inventaire-*.md`) | Brouillon |

- **Priorité en cas de contradiction** : vision > cahier des charges > cahier fonctionnel. Ne tranche pas seul : signale la contradiction et ouvre une entrée de brainstorming.
- **Les documents définitifs ne sont jamais modifiés sans la validation explicite de la propriétaire.** Toute idée, option ou remise en question va dans le brainstorming correspondant. Le format des entrées est décrit en tête de chaque brainstorming.
- Une entrée « À valider » ou « À étudier » n'est **pas** une spécification : ne code rien qui en dépend avant qu'elle soit validée.
- La version de référence de ces documents vit dans le projet claude.ai de la propriétaire. Les copies de `docs/projet/` peuvent prendre du retard : en cas de doute, demande-lui la dernière version avant de coder. Si tu modifies une copie locale (avec sa validation), rappelle-lui de reporter la modification dans le projet claude.ai.
- Avant de coder une fonctionnalité, relis sa section dans le cahier fonctionnel et les exigences du cahier des charges qui s'y appliquent.

Documents techniques que tu tiens à jour dans `docs/` :
- `ROADMAP.md` : avancement de chaque élément du planning ;
- `DECISIONS.md` : chaque décision structurante (contexte, options, choix, raison) ;
- `THREAT_MODEL.md` : modèle de menaces (menaces minimales listées dans le cahier des charges, section 5.5) ;
- `REGISTRE_DONNEES.md` : registre RGPD (quoi, pourquoi, combien de temps) ;
- `GUIDE_PROPRIETAIRE.md` : installer, tester, déployer, administrer, en langage simple ;
- `CHANGELOG.md`.

## 4. Travailler avec la propriétaire

La propriétaire est une ancienne développeuse web. Elle maîtrise la logique et l'UX mais pas les technologies actuelles, et **elle n'écrit aucune ligne de code**.

- Échange avec elle en français, simplement. Explique chaque terme technique en une phrase.
- Ne lui demande jamais de modifier du code ou un fichier. Livre-lui toujours des fichiers complets.
- Toute action manuelle (achat, compte, DNS, installation sur son téléphone ou son ordinateur) fait l'objet d'un **guide pas à pas, clic par clic**.
- **Avant toute décision structurante ou irréversible** (technologie, schéma de base de données, licence, sécurité, juridique) : présente 2 ou 3 options avec avantages, inconvénients, coûts et ta recommandation, inscris-les dans le brainstorming concerné, puis attends sa validation. Consigne ensuite la décision dans `docs/DECISIONS.md`.
- Travaille par **petites étapes livrables** : annoncer, faire, tester, vérifier, puis expliquer comment elle peut tester elle-même.
- **En début de session**, rappelle-lui les entrées « À valider » des brainstormings qui bloquent la prochaine étape.
- **En fin de session**, résume ce qui a été fait, ce qu'elle doit tester et comment, les décisions en attente et la prochaine étape.
- **Son matériel de test** : un téléphone Android et un ordinateur, pas d'iPhone. Prévois toujours un APK installable et la procédure pour l'installer. La messagerie Bluetooth demandera au moins deux téléphones Android : préviens-la à l'avance.
- **Sa préférence typographique** : pas de tiret cadratin, sauf pour marquer un dialogue (y compris dans l'interface et la documentation).

## 5. Budget

- Le budget de mise en route est de **300 €** : noms de domaine, un serveur payé 24 mois d'avance, sauvegardes hors site, modération par IA au démarrage. La suite dépend des adhésions et des dons.
- **N'engage aucune dépense, aucun abonnement, aucun essai payant sans l'accord explicite de la propriétaire.** Annonce toujours le coût, y compris au renouvellement.
- Reste dans les **quotas gratuits** de GitHub Actions : pas de machines macOS en intégration continue pour l'instant, mise en cache des builds, pas de tâches inutiles. Surveille la consommation et préviens-la avant d'approcher la limite.
- Coûts reportés, à ne pas engager : compte Apple Developer et compilation iOS, deuxième serveur, serveur d'IA dédié, audit de sécurité externe, avocat, expert-comptable (cahier des charges, section 12).

## 6. Vérifier avant d'affirmer ou d'utiliser

- Tes connaissances peuvent dater. Avant d'ajouter une bibliothèque, un outil ou une API, vérifie sa version actuelle, sa maintenance, sa licence et sa documentation officielle.
- Pour toute règle juridique ou institutionnelle utilisée dans le code ou les textes (seuils de pétitions, règles électorales, financement politique, données publiques réutilisables), vérifie le droit en vigueur auprès de sources officielles et cite-les.

## 7. Règles non négociables dans le code

### Vie privée
- **Aucun traceur, aucun tiers** : pas de Google (Analytics, Firebase, Maps, Fonts), pas de Meta, pas de SDK publicitaire ou d'analyse, pas de CDN. Toutes les ressources (polices, icônes, scripts) sont auto-hébergées. Ajoute en intégration continue une vérification qui bloque toute dépendance ou requête réseau vers un tiers non autorisé.
- **Minimisation** : ne collecte que le strict nécessaire. Toute nouvelle donnée stockée doit être justifiée dans `REGISTRE_DONNEES.md`.
- **Protéger par défaut, laisser le choix** (cahier des charges, principe 10) : chaque réglage de confidentialité et d'anonymat est livré à son niveau le plus protecteur. L'utilisateur peut l'assouplir autant qu'il le veut pour ses propres données, y compris les rendre publiques ; chaque assouplissement affiche d'abord une mise en garde simple sur les risques, avec un bouton pour confirmer et un pour annuler, et reste réversible. Ne code jamais de choix imposé à la place de l'utilisateur sur ses propres données. Cette liberté ne s'étend jamais aux données des autres, ne lève aucune obligation légale et ne modifie pas les protections communes (le serveur ne relie jamais un vote à un compte, même si l'utilisateur publie ses votes lui-même).
- **Identités** : comptes pseudonymes sous forme de paires de clés générées sur l'appareil, compatibles Nostr (secp256k1, signatures Schnorr BIP-340). Récupération par phrase secrète. Ni numéro de téléphone ni e-mail obligatoire. La lecture se fait sans compte.
- **Opinions politiques = données sensibles (RGPD, article 9)** : aucun vote, soutien ou engagement ne doit pouvoir être relié à une identité réelle par la plateforme. Seuls des résultats agrégés sont exposés. La progression dans les parcours éducatifs reste sur l'appareil.
- **Graphe social** : relations, statuts et cercles restent sur les appareils (avec une sauvegarde chiffrée facultative) ; les listes de membres des collectifs sont chiffrées sur le serveur. Choix technique précis à valider (brainstorming, point d'attention n° 26).
- **Médias** : suppression des métadonnées (EXIF, GPS, modèle d'appareil) **sur l'appareil, avant l'envoi**. Outil de floutage des visages optionnel. Empreinte SHA-256 de l'original, horodatée (OpenTimestamps ou RFC 3161).
- **Contenus sensibles en attente** (témoignages, signalements) : chiffrés avec la clé publique de l'équipe de modération.
- **Adresses IP** : aucune journalisation, y compris dans le reverse proxy et les journaux applicatifs. Si un journal anti-abus est indispensable : adresse tronquée, hachée, effacée sous 24 heures maximum.

### Sécurité
- **Secrets** : jamais dans le dépôt. `.env`, keystores et clés de signature sont exclus par `.gitignore`. Détection automatique des secrets (par exemple gitleaks) avant chaque commit et en intégration continue.
- HTTPS partout, HSTS, en-têtes stricts (CSP sans `unsafe-inline`, etc.).
- Double authentification obligatoire pour les comptes de l'équipe, permissions minimales par rôle, journal d'audit de toutes les actions de modération et d'administration.
- Anti-spam : limitation de débit et preuve de travail auto-hébergée (type ALTCHA). Jamais de reCAPTCHA.
- Référentiels : OWASP ASVS (serveur) et OWASP MASVS (mobile). Une checklist de sécurité est passée avant chaque release.
- **Ne sacrifie jamais la sécurité pour aller plus vite.** Si un compromis te semble nécessaire, explique-le et demande.

### Fondations à poser dès la phase 1
- **Signature des contenus** : chaque contenu publié est signé dans un format compatible avec les événements Nostr. La clé de la plateforme n'est jamais stockée en clair sur le serveur principal (service de signature séparé ou clé hors ligne). Rotation et révocation sont prévues dès le départ.
- **Port de publication unique** : toute publication passe par une seule interface. En phase 1, l'adaptateur publie sur le serveur ; en phase 3, des adaptateurs Nostr et miroirs statiques s'y brancheront sans toucher au reste.
- **Identifiants de contenu stables** et **export JSON ouvert**, documenté par un schéma versionné.
- **Offline-first** : copie locale des contenus consultés et des données essentielles (fiches pratiques, carte, agenda), synchronisation dès que le réseau revient.
- **Archivage horodaté des engagements publics** (Pacte démocratique, déclarations de candidats).

### Plateformes, accessibilité et compatibilité
- **Une seule base de code dès la première ligne** pour Android, l'ordinateur (Windows, macOS, Linux) et iOS.
- **iOS** : le code commun et l'interface restent compatibles iOS (aucune bibliothèque réservée à Android ou à la JVM dans le code partagé ; ce qui est propre à une plateforme passe par `expect`/`actual` ou par une interface). Pas de compilation ni de publication iOS tant que le compte Apple Developer n'existe pas.
- **Écrans adaptatifs dès le premier écran** : téléphone, tablette et ordinateur, en portrait comme en paysage (classes de taille de fenêtre). Teste chaque écran au moins sur ces trois formats.
- WCAG 2.2 niveau AA (RGAA) : lecteurs d'écran, contrastes, tailles de texte, navigation au clavier. Teste-le automatiquement quand c'est possible.
- Android 8 et plus (API 26), connexion faible, mode économie de données.
- Site lisible sans JavaScript, installable en PWA, contenus essentiels disponibles hors ligne. C'est aussi la solution sur iPhone tant que l'application iOS n'est pas publiée.
- Tous les textes de l'interface sont externalisés pour la traduction ; le français est la langue par défaut.
- Modes clair et sombre.

### Contenus et cadre légal
- Respecte la charte de contenu (cahier des charges, section 9.3). Aucun appel ni incitation à la violence : c'est une obligation légale, pas un choix éditorial.
- Tout contenu d'exemple est marqué **[EXEMPLE]**. Aucune donnée personnelle réelle dans les tests, les jeux de données, la préproduction ou les captures.
- Tout ce qui concerne des responsables publics suit la méthode prévue : sources primaires, distinction visible entre établi, allégué et en cours d'enquête, statut de la procédure, droit de réponse.
- Les outils électoraux (Pacte démocratique, comparateur, baromètre des élus) suivent les règles du cahier des charges (section 9.2).
- **IA** : tout contenu produit avec l'aide d'une IA affiche son origine. La modération par IA (cahier des charges, section 5.6) masque provisoirement les seuls contenus graves et manifestement illicites (alerte rouge), signale sans les masquer les contenus limites (alerte orange) et laisse tout le reste se publier sans délai ; la décision finale reste toujours humaine ; tout utilisateur peut signaler un contenu ou un autre utilisateur ; l'IA ne lit jamais les échanges chiffrés. L'hébergement du modèle reste à valider. L'**assistant citoyen** et les crédits associés sont mis de côté : ne code rien à leur sujet.
- La relecture avant publication est conservée pour les témoignages, les appels à témoins et les revendications.
- Ne reprends jamais l'identité visuelle officielle de l'État (logo Marianne, bloc-marque).

## 8. Hébergement et environnements

Détail dans le cahier des charges, section 3.5.
- **Hostinger pour tout** : serveurs et noms de domaine. Centre de données dans l'Union européenne, hors de France (Lituanie, Allemagne ou Pays-Bas). Seule exception : les sauvegardes chiffrées, stockées chez un autre prestataire européen.
- **Un seul serveur au départ** (VPS KVM 1 : 1 vCPU, 4 Go de mémoire, 50 Go de disque), loué au moment du premier déploiement, pas avant. Pendant le développement, il ne sert que de préproduction ; au lancement public, il accueille la production et la préproduction. Conçois donc l'infrastructure pour qu'elle tienne dans 4 Go de mémoire, préproduction comprise.
- **Production et préproduction séparées autant que possible** : projets Docker Compose, conteneurs, bases de données, réseaux, volumes, utilisateurs système et secrets distincts ; limites de mémoire et de processeur sur la préproduction ; aucune ressource partagée entre les deux, sauf le reverse proxy si nécessaire.
- **Préproduction fermée au public** : accessible uniquement par un tunnel WireGuard, en plus d'un mot de passe ; jamais de données réelles, uniquement des données fictives marquées [EXEMPLE].
- **Durcissement** : pare-feu, connexion SSH par clé uniquement, pas de connexion root, mises à jour de sécurité automatiques.
- **Circuit de mise en ligne** : tests automatiques, déploiement en préproduction, validation par la propriétaire, puis production. Jamais de déploiement direct en production.
- L'infrastructure est décrite en code pour pouvoir tout redéployer ailleurs en quelques heures à partir des sauvegardes. Un deuxième serveur dédié à la production viendra dès que le budget le permettra : rends ce passage simple.

## 9. Architecture et technologies

La cible est décrite dans le cahier des charges (sections 3 et 4). En résumé :
- **Applications** : Kotlin Multiplatform + Compose Multiplatform, logique partagée, base locale SQLDelight (ou équivalent maintenu).
- **Serveur** : Kotlin + Ktor, PostgreSQL + PostGIS, recherche plein texte PostgreSQL.
- **Site web** : rendu côté serveur ; framework à choisir avec la propriétaire. La version web de Compose Multiplatform, encore en bêta et dépendante de JavaScript, ne convient pas au site.
- **Infrastructure** : Docker Compose, Caddy (HTTPS automatique), infrastructure décrite en code.
- **Services** : cartes OpenStreetMap via MapLibre ; vidéos via PeerTube ; notifications UnifiedPush avec un ntfy auto-hébergé (sur iOS, APNs envoie seulement un signal de réveil, jamais de contenu).
- **Plus tard** : Nostr, miroirs statiques, .onion, Tor intégré (phase 3) ; messagerie bitchat (phase 4), en conservant les mentions de licence du code réutilisé.

Si une meilleure option existe, propose-la avec ta justification **avant** de commencer.

**Décidé le 5 novembre 2026** (détail dans `docs/DECISIONS.md`) : licence AGPL-3.0-or-later avec permissions pour les magasins d'applications ; dépôt public dès le premier commit ; site web en Ktor avec kotlinx.html ; chaîne d'outils (Kotlin 2.4.21, Compose Multiplatform 1.12.2, AGP 9.3.x, Gradle 9.7.1, JDK 21, Ktor 3.6.1). Sous autorisation générale, à confirmer par la propriétaire (D-016 à D-018) : identités et signature avec `secp256k1-kmp` et `bitcoin-kmp` (ACINQ) ; phrase de récupération BIP-39 de 12 mots, français par défaut, dérivation NIP-06 ; contenus signés au format d'événement Nostr NIP-01, décrit dans `docs/FORMATS.md`.

**Identité visuelle (6 novembre 2026, D-025)** : celle des maquettes de la propriétaire ; jetons dans `PpfTheme.kt` (`PpfPalette`), police Atkinson Hyperlegible, icônes Lucide (`Lucide.kt` généré), composants communs dans `shared/ui/.../components`. Toute nouvelle vue se construit avec ces composants, sans couleur ni police en dur.

**Décidé le 6 et 7 novembre 2026** (D-026 à D-028) : barre du bas à cinq entrées (Fil, En direct, Carte, Messages, Revendications), vues du fil parcourues au doigt ; messagerie option A (NIP-17 pour le privé, NIP-29 pour les salons, notre serveur comme relais, MLS plus tard derrière une interface) ; conservation des messages sur le serveur de trois mois au minimum, réglable par l'utilisateur ; création des salons ouverte à tous avec charte, territoire et refus des doublons.

**Choix encore ouverts** (consulte les brainstormings avant d'y toucher, ne code rien qui en dépend) : hébergement du modèle d'IA de modération, briques de contenu communes, méthode « une personne, une voix », prestataire de paiement (HelloAsso à confirmer), navigation détaillée (réécrite lors du découpage en MVP).

### Structure du dépôt (adoptée le 5 novembre 2026)

```
/shared/core  Kotlin Multiplatform sans interface : modèles, logique métier, signature, synchronisation (utilisé aussi par le serveur)
/shared/ui    interface Compose Multiplatform commune (Android, ordinateur, iOS)
/android      application Android, simple hôte de /shared/ui
/ios          présent pour garder le code compatible ; non compilé pour l'instant
/desktop      application Windows, macOS, Linux, simple hôte de /shared/ui
/server       Ktor
/web          site web rendu côté serveur (kotlinx.html), monté par /server
/infra        Docker Compose (production et préproduction séparées), Caddy, WireGuard, déploiement, sauvegardes
/tools        scripts de vérification (traceurs, dépendances), /tools/content-signer (signature des contenus de /content), /tools/icons (icônes Lucide et leur convertisseur)
/content      contenus éditoriaux intégrés à l'application (Markdown avec en-tête), tous marqués [EXEMPLE] pour l'instant
/docs         documents techniques
  /projet     documents de référence (vision, cahiers, brainstormings)
  /guides     guides pas à pas pour la propriétaire (achats, installations)
```

## 10. Commandes

Tout passe par le wrapper Gradle (`gradlew.bat` sous Windows, `./gradlew` ailleurs). Prérequis installés sur l'ordinateur de la propriétaire : JDK 21 (Temurin), SDK Android (plateforme 36, outils de compilation 36.0.0), GitHub CLI, gitleaks. Tiens cette section à jour à chaque changement d'outillage.

| Besoin | Commande |
|---|---|
| Tout compiler et lancer tous les tests | `gradlew build` |
| APK de test (signature de test partagée) | `gradlew :android:assembleDebug` puis `android/build/outputs/apk/debug/` |
| Application de bureau, lancement direct | `gradlew :desktop:run` |
| Application de bureau empaquetée (dossier, sans installateur) | `gradlew :desktop:createDistributable` puis `desktop/build/compose/binaries/main/app/` |
| Installateur Windows (demande WiX, fait par l'intégration continue) | `gradlew :desktop:packageMsi` |
| Serveur en local | `gradlew :server:run` puis http://localhost:8080 et http://localhost:8080/health |
| Serveur empaqueté (jar unique) | `gradlew :server:buildFatJar` puis `server/build/libs/ppf-server.jar` |
| Tests d'un seul module | `gradlew :shared:ui:jvmTest`, `gradlew :server:test`, etc. |
| Vérification des traceurs et des tiers | `bash tools/check-no-trackers.sh` |
| Vérification des dépendances interdites | `bash tools/check-dependencies.sh` |
| Vérification du contenu de l'APK (ressources Compose, bibliothèques natives) | `bash tools/check-apk.sh android/build/outputs/apk/debug/android-debug.apk` |
| Mise à jour des fichiers de verrouillage après un changement de dépendance | `gradlew build --write-locks` |
| Détection des secrets avant commit | crochets locaux : `git config core.hooksPath .githooks` |
| Déploiement en préproduction (sur le serveur) | `infra/scripts/deploy.sh preproduction` |
| Signer les contenus de `content/` et les intégrer à l'application | `gradlew :tools:content-signer:run` (clé dans `~/.ppf/content-example.nsec`, hors dépôt) |
| Serveur de développement (PostgreSQL 17 embarqué, fiches d'exemple chargées) | `gradlew :server:runDev` puis http://localhost:8080 ; `PPF_EDITOR_KEYS` pour ajouter des clés d'éditeurs |
| Application de bureau reliée à un serveur local | variable d'environnement `PPF_SERVER_URL=http://localhost:8080` avant `gradlew :desktop:run` ou le lancement de l'exécutable |
| Modules Java à embarquer dans l'application de bureau, après tout ajout de dépendance | `gradlew :desktop:suggestRuntimeModules`, à reporter dans `desktop/build.gradle.kts` |
| Régénérer les icônes après un ajout dans `tools/icons/lucide/` | `python -I tools/icons/generate_icons.py` |
| Captures d'écran des écrans principaux (téléphone clair et sombre, ordinateur), sans appareil | variable `PPF_SCREENSHOTS_DIR=<dossier>` puis `gradlew :shared:ui:jvmTest --tests "*ScreenshotTest*"` |

Signature de test des APK : la variable d'environnement `PPF_TEST_KEYSTORE` (chemin du fichier) et `PPF_TEST_KEYSTORE_PASSWORD` sont définies sur l'ordinateur de la propriétaire et dans les secrets GitHub ; sans elles, Gradle utilise la clé de débogage par défaut.

## 11. Conventions

- **Langues** : code, identifiants, commentaires et commits en anglais ; interface, documentation et échanges avec la propriétaire en français.
- **Commits** : petits et fréquents, au format Conventional Commits, signés.
- **Branches** : une branche par fonctionnalité et une pull request. La branche principale est protégée : jamais de push direct, jamais de force-push, fusion uniquement avec des tests au vert et une relecture. Explique simplement à la propriétaire comment relire et accepter une pull request.
- **Dépendances** : le moins possible, versions verrouillées (catalogue de versions, fichiers de verrouillage), mises à jour via Renovate ou Dependabot. Chaque nouvelle dépendance est justifiée dans la pull request.
- **Tests** : unitaires, intégration et interface pour chaque fonctionnalité, écrits avec elle.
- **Builds** : reproductibles, releases signées. Les clés de signature des applications restent hors du dépôt.
- **Configuration privée hors dépôt** : secrets, clés, seuils et règles anti-fraude, réglages de modération.

## 12. Définition de « terminé »

Une tâche n'est terminée que si :
- les tests passent, en local comme en intégration continue ;
- les règles de la section 7 sont respectées (traceurs, vie privée, sécurité, accessibilité, écrans adaptatifs) ;
- la documentation technique, `ROADMAP.md` et `CHANGELOG.md` sont à jour ;
- la propriétaire a reçu une explication simple pour tester elle-même (avec un APK si l'application est concernée).

## 13. Ce que tu ne fais pas

- Rédiger les contenus éditoriaux ou les prises de position publiées. Tu peux produire des brouillons sourcés et des contenus marqués [EXEMPLE], à faire valider.
- Choisir les revendications, les campagnes ou les mesures du Pacte démocratique : elles sont fixées par le vote des citoyens.
- Modifier un document définitif sans validation.
- Déployer en production, supprimer des données, toucher au DNS ou engager une dépense sans l'accord explicite de la propriétaire.
- Rendre le dépôt public sans sa décision explicite.
- Ajouter un traceur, un service tiers ou une dépendance non nécessaire.

## 14. Point de départ

Commence par la **phase 0** du planning (cahier des charges, section 10.2), dans cet ordre :
1. **Vérifier l'environnement** : chemin du dossier, système d'exploitation, Git, GitHub (`gh auth status`), outils nécessaires (JDK, SDK Android). Ce qui manque est installé avec l'accord de la propriétaire, avec un guide simple si elle doit cliquer quelque part.
2. **Créer le dépôt GitHub privé** et la protection de la branche principale.
3. **Squelette** : Kotlin Multiplatform et Compose Multiplatform pour Android et l'ordinateur, avec un premier écran adaptatif (téléphone, tablette, ordinateur) ; cible iOS déclarée compatible mais non compilée ; squelette du serveur Ktor avec un point de contrôle de santé ; `/infra` avec deux projets Docker Compose séparés (production et préproduction).
4. **Intégration continue** : compilation de l'APK et des applications de bureau, tests, détection des secrets, vérification de l'absence de traceurs et d'appels vers des tiers, dans les quotas gratuits.
5. **Documents** : `ROADMAP.md`, `DECISIONS.md` (avec les décisions déjà prises), `THREAT_MODEL.md` (première version), `REGISTRE_DONNEES.md`, `GUIDE_PROPRIETAIRE.md` (installer l'APK sur son téléphone Android, lancer l'application de bureau), `CHANGELOG.md`.
6. **Guides d'achat** : guide pas à pas pour acheter les six noms de domaine chez Hostinger (à faire tout de suite) ; guide pour le serveur KVM 1, à n'utiliser qu'au moment du premier déploiement.

Les décisions de cette phase (licence, dépôt public, framework du site) ont été prises le 5 novembre 2026 et sont consignées dans `docs/DECISIONS.md` ; les décisions antérieures (nom de domaine, toutes les plateformes dès le départ, hébergement, budget) sont intégrées au cahier des charges 1.3. L'avancement de chaque élément est suivi dans `docs/ROADMAP.md`.
