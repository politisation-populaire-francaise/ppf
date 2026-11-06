# Décisions structurantes

Chaque décision structurante est consignée ici : contexte, options, choix, raison. Les décisions sont prises par la propriétaire du projet ; les options lui sont d'abord présentées dans le brainstorming concerné (`docs/projet/brainstorming-*.md`). Une décision n'est jamais effacée : si elle change, une nouvelle entrée la remplace et le dit.

Format : D-numéro, date, titre ; contexte ; options ; décision ; raison ; conséquences.

## D-001 (2026-11-05) Nom de domaine principal et extensions

- Contexte : l'ancien nom « Marianne Libre » est abandonné pour des raisons juridiques.
- Décision : domaine principal politisation-populaire-francaise.org ; extensions .fr, .com, .eu, .net et .app réservées pour protéger le nom. Les six domaines ont été achetés chez Hostinger le 5 novembre 2026, au nom de la propriétaire en attendant l'association.
- Raison : nom clair et disponible ; répartition sur plusieurs registres contre une suspension.
- Source : brainstorming du cahier des charges, entrée « Nom de domaine » ; cahier des charges 1.3, section 1.2.

## D-002 (2026-11-05) Hébergement : Hostinger, un seul serveur au départ

- Décision : Hostinger pour les serveurs et les domaines ; centre de données dans l'Union européenne hors de France ; un seul VPS KVM 1 loué au premier déploiement, préproduction seule pendant le développement, production et préproduction séparées sur ce serveur au lancement ; deuxième serveur dès que le budget le permet ; sauvegardes chiffrées chez un autre prestataire européen.
- Source : brainstorming du cahier des charges, entrée « Hébergement de production et de préproduction » ; cahier des charges 1.3, section 3.5.

## D-003 (2026-11-05) Budget de mise en route

- Décision : 300 € pour la mise en route (domaines, serveur payé 24 mois d'avance, sauvegardes hors site, modération par IA au démarrage). Aucune dépense sans l'accord de la propriétaire. Coûts reportés : compte Apple Developer, deuxième serveur, serveur d'IA, audit externe, avocat, expert-comptable.
- Source : cahier des charges 1.3, section 12.

## D-004 (2026-11-05) Une seule base de code, toutes les plateformes dès le départ

- Décision : Kotlin Multiplatform et Compose Multiplatform pour Android, l'ordinateur et iOS ; écrans adaptatifs dès le premier écran ; iOS compatible mais ni compilé ni publié tant que le compte Apple n'existe pas.
- Source : brainstorming du cahier des charges, entrée « Toutes les plateformes dès le départ » ; cahier des charges 1.3, sections 1.2 et 4.

## D-005 (2026-11-05) Dépôt GitHub public dès le premier commit

- Contexte : avec le plan gratuit de GitHub, la protection de la branche principale n'existe pas sur un dépôt privé, et les minutes d'intégration continue y sont limitées.
- Options : A. public dès le premier commit ; B. privé avec GitHub Pro (environ 4 dollars par mois) ; C. privé avec le plan gratuit, sans protection de branche.
- Décision : A.
- Raison : protection de branche et intégration continue illimitée sans dépense ; cohérent avec la valeur d'ouverture ; miroir Codeberg possible.
- Conséquences : aucun secret ni configuration sensible dans le dépôt (détection avant chaque commit et en intégration continue) ; audit de sécurité avant le lancement public du service ; le dépôt ne sera jamais rendu privé ou supprimé sans décision de la propriétaire.

## D-006 (2026-11-05) Licence du code : AGPL-3.0-or-later avec permissions supplémentaires

- Options : A. AGPL-3.0-or-later avec permission pour les magasins d'applications et accord des contributeurs ; B. EUPL-1.2 ; C. MPL-2.0.
- Décision : A. Fichiers `LICENSE`, `LICENSE-ADDITIONAL-PERMISSIONS.md`, `CONTRIBUTING.md` (certificat d'origine du développeur et clause de permission).
- Raison : empêcher une récupération fermée du code, y compris sous forme de service en ligne ; compatible avec les licences de bitchat (MIT, domaine public) ; le conflit avec l'App Store est réglé par la permission supplémentaire que le titulaire des droits accorde.
- Conséquences : relecture par un avocat avant le lancement public ; toute bibliothèque ajoutée doit être compatible avec l'AGPL.

## D-007 (2026-11-05) Site web : Ktor et kotlinx.html

- Options : A. Ktor avec gabarits HTML en Kotlin ; B. Astro ; C. générateur statique plus API.
- Décision : A, module `web` monté par le serveur.
- Raison : un seul langage, modèles partagés, pages lisibles sans JavaScript, très peu de dépendances, export statique possible pour les miroirs de la phase 3.

## D-008 (2026-11-05) Structure du dépôt

- Décision : structure proposée par `CLAUDE.md`, avec `shared` scindé en `shared/core` (sans interface, utilisable par le serveur) et `shared/ui` (Compose) ; ajout de `tools` et `docs/guides`.
- Raison : le serveur ne doit pas embarquer l'interface graphique ; les modèles doivent être partagés entre applications et serveur.

## D-009 (2026-11-05) Chaîne d'outils et versions

- Décision : Kotlin 2.4.21 ; Compose Multiplatform 1.12.2 (Material 3 en version 1.12.0-alpha03, la seule que JetBrains associe officiellement à cette version ; la dernière version stable de Material 3, 1.9.0, n'est plus alignée) ; plugin Android Gradle 9.3.3 (dans la plage officiellement compatible avec Kotlin 2.4.21, jusqu'à 9.3.x) avec le plugin `com.android.kotlin.multiplatform.library` pour les modules partagés ; Gradle 9.7.1 ; JDK 21 Temurin ; Ktor 3.6.1 ; kotlinx.html 0.12.0 ; kotlinx.serialization 1.12.0 ; Android : compileSdk 36, minSdk 26 (Android 8), build-tools 36.0.0.
- Raison : versions stables vérifiées le 5 novembre 2026 sur les pages officielles et le guide de compatibilité de Kotlin Multiplatform.
- Conséquences : catalogue de versions (`gradle/libs.versions.toml`) et fichiers de verrouillage ; mises à jour par Dependabot, chacune relue.

## D-010 (2026-11-05) Identifiants techniques stables

- Décision : paquet Kotlin de base `org.politisationpopulairefrancaise` ; identifiant d'application Android `org.politisationpopulairefrancaise.app` (suffixe `.test` pour les versions de test) ; identifiant de bundle macOS `org.politisationpopulairefrancaise.desktop` ; identifiant de mise à niveau Windows fixe dans `desktop/build.gradle.kts`.
- Raison : dérivés du nom de domaine principal, uniques et définitifs (un identifiant publié ne peut plus changer).

## D-011 (2026-11-05) Classes de taille de fenêtre calculées dans le code commun

- Décision : les seuils téléphone, tablette et ordinateur (600 dp et 840 dp, selon Material Design) sont calculés dans `shared/core`, sans bibliothèque supplémentaire, et testés unitairement.
- Raison : le moins de dépendances possible ; même logique sur toutes les plateformes.

## D-012 (2026-11-05) Signature de test partagée des APK

- Décision : une clé de signature de test, créée sur l'ordinateur de la propriétaire et stockée hors dépôt, est aussi fournie à l'intégration continue par un secret GitHub ; toutes les versions de test ont ainsi la même signature et s'installent les unes sur les autres. La signature de publication (magasins) utilisera une clé distincte, gardée hors ligne.
- Raison : sans cela, chaque machine signerait avec une clé différente et le téléphone refuserait les mises à jour.

## D-013 (2026-11-05) Intégration continue et dépendances

- Décision : GitHub Actions avec machines Linux et Windows (pas de macOS pour l'instant) ; détection des secrets (gitleaks) ; vérification des tiers interdits (`tools/check-no-trackers.sh`), de l'intégrité du wrapper Gradle et des groupes de dépendances interdits ; Dependabot hebdomadaire pour Gradle et les actions ; versions verrouillées ; chaque action épinglée sur un commit précis.
- Raison : exigences du cahier des charges (section 4, intégration continue ; section 5.4, dépendances) dans les quotas gratuits.
- Note : le cache Gradle utilise `actions/cache` (libre) et non `gradle/actions` version 6, dont le composant de cache est devenu propriétaire et soumis à des conditions d'utilisation distinctes.

## D-014 (2026-11-05) Identité des commits

- Décision : auteur « PPF » avec l'adresse « noreply » fournie par GitHub ; commits signés par une clé SSH ; crochets locaux (secrets, tiers, format des messages).
- Raison : le dépôt est public ; l'adresse personnelle de la propriétaire ne doit pas apparaître dans l'historique (demande initiale de l'adresse personnelle écartée pour cette raison, réversible sur simple demande).

## D-015 (2026-11-05) Serveur : aucune journalisation des requêtes, en-têtes stricts

- Décision : aucun journal de requêtes ni dans le proxy ni dans l'application ; en-têtes de sécurité stricts posés par l'application (CSP sans `unsafe-inline`, etc.) ; TLS et HSTS assurés par Caddy.
- Raison : cahier des charges, sections 5.1 (adresses IP) et 5.3 (HTTPS, en-têtes).

## D-016 (2026-11-05) Bibliothèques de cryptographie pour les identités et la signature

- Contexte : le cahier des charges (section 3.2) impose des paires de clés secp256k1 créées sur l'appareil, des signatures Schnorr BIP-340 compatibles Nostr et une phrase secrète de récupération. Il faut la même implémentation sur Android, sur la JVM (ordinateur et serveur) et sur iOS.
- Options :
  - A. `secp256k1-kmp` (ACINQ) : enveloppe Kotlin Multiplatform de la bibliothèque libsecp256k1 de Bitcoin Core, auditée et utilisée par tout l'écosystème Bitcoin ; complétée par `bitcoin-kmp` (ACINQ) pour BIP-39 (phrase), BIP-32 (dérivation), SHA-256 et bech32. Apache 2.0, maintenues (versions 0.24.0 d'août 2026 et 0.32.0 de septembre 2026), utilisées en production dans le portefeuille Phoenix. Inconvénients : `bitcoin-kmp` dépasse le besoin ; code natif d'environ 1,3 Mo par architecture Android.
  - B. `secp256k1-kmp` seule, SHA-256 et HMAC par KotlinCrypto, BIP-39 et BIP-32 écrits par nous : moins de dépendances, mais du code cryptographique maison à maintenir, ce que le projet s'interdit.
  - C. `schnorr256k1-kmp` (bibliothèque dédiée à Nostr, MIT, 2026) : trop récente et portée par une seule personne ; pas de BIP-39.
  - D. Cryptographie des plateformes (`expect`/`actual`) : Schnorr BIP-340 n'existe dans aucune API Android, JVM ou iOS ; il faudrait l'écrire trois fois.
- Décision : option A, prise par l'IA sous l'autorisation générale donnée par la propriétaire le 5 novembre 2026 (« total autonomie ») ; à confirmer par elle. Réversible : aucun type de ces bibliothèques n'apparaît dans l'API du module `shared/core`.
- Conséquences : dépendances ajoutées au catalogue et aux fichiers de verrouillage ; bibliothèque native chargée au démarrage (à vérifier sur le téléphone de la propriétaire) ; APK plus lourd d'environ 4 Mo, limité aux trois architectures utiles (arm64-v8a, armeabi-v7a, x86_64) ; vecteurs de test officiels BIP-340 exécutés à chaque compilation.

## D-017 (2026-11-05) Phrase de récupération : 12 mots BIP-39, français par défaut, dérivation NIP-06

- Contexte : la phrase secrète est le seul moyen de retrouver un compte, puisque aucun serveur ne connaît la clé. Le standard Nostr correspondant (NIP-06 : BIP-39 puis chemin BIP-32 m/44'/1237'/compte'/0/0) est marqué « draft, unrecommended » par la communauté Nostr, qui préfère échanger directement la clé secrète « nsec ».
- Options :
  - A. NIP-06 avec 12 mots, liste française officielle par défaut et liste anglaise acceptée à la saisie ; export et import de la clé « nsec » pour la compatibilité avec toute application Nostr.
  - B. 24 mots encodant directement la clé secrète : aucun standard Nostr, deux fois plus de mots à noter.
  - C. Format maison : exclu (incompatible, invérifiable).
- Décision : option A, prise sous la même autorisation générale, à confirmer. Compte 0 ; pas de mot de passe BIP-39 supplémentaire en première version ; la langue est détectée à la saisie et une ambiguïté (phrase valable dans les deux listes, cas quasi impossible) est signalée au lieu d'être tranchée en silence.
- Raison : douze mots français sont faciles à noter pour le public visé ; le standard est documenté avec des vecteurs de test ; la clé « nsec » reste la forme portable vers les autres applications Nostr.

## D-018 (2026-11-05) Contenus signés au format d'événement Nostr, identifiants stables, port de publication

- Décision : chaque contenu publié est un événement NIP-01 (identifiant = SHA-256 de la sérialisation canonique, signature Schnorr de cet identifiant) ; le JSON de l'événement est le format d'export ouvert de la plateforme, documenté dans `docs/FORMATS.md` ; les contenus modifiables utilisent des types « adressables » (30000 à 39999) avec une étiquette `d`, ce qui donne l'identifiant stable `type:clé:identifiant` ; toute publication passe par l'interface unique `PublicationPort` ; la signature locale est assurée par `LocalEventSigner`, et la clé de la plateforme sera manipulée par un service de signature séparé.
- Raison : cahier des charges, section 3.2 (publication sur Nostr en phase 3 sans re-signer, miroirs statiques, identifiants stables).
- Conséquences : la sérialisation canonique est écrite à la main et vérifiée contre un événement Nostr réel ; toute mise en œuvre du port refuse un événement dont la signature ne correspond pas.

## D-019 (2026-11-05) Accès à la base de données du serveur : JDBC, HikariCP, Flyway, PostgreSQL 17 en tests

- Contexte : le serveur doit stocker les contenus signés dans PostgreSQL (cahier des charges, section 4). Décision technique prise en autonomie (règle du 5 novembre 2026 : autonomie totale quand c'est gratuit et conforme aux cahiers).
- Options : Exposed (JetBrains) ; jOOQ ; SQLDelight avec son dialecte PostgreSQL ; JDBC seul avec un pool de connexions et des migrations SQL.
- Décision : JDBC seul (requêtes SQL écrites à la main), pool HikariCP 7.1.0, migrations Flyway 13.12.1 (édition communautaire, Apache 2.0) en fichiers SQL versionnés, pilote PostgreSQL 42.7.14. Tests sur une vraie base PostgreSQL 17 démarrée dans le processus de test (embedded-postgres de Zonky, binaires pris sur Maven Central) : rien à installer, ni Docker ni PostgreSQL.
- Raison : le moins de dépendances et d'abstractions possible pour un schéma simple ; SQL lisible et relisible ; Flyway garde l'historique des évolutions du schéma ; les tests portent sur le vrai moteur et non sur une imitation.
- Conséquences : le jar unique fusionne les fichiers `META-INF/services` (sans quoi Flyway ne reconnaît pas PostgreSQL) ; les variables `DATABASE_URL`, `DATABASE_USER` et `DATABASE_PASSWORD_FILE` déjà prévues dans `infra/` sont utilisées ; sans base configurée, le serveur démarre quand même (santé et site web) et l'API répond 503.

## D-020 (2026-11-05) Base locale des applications : SQLDelight

- Contexte : les applications sont offline-first (cahier des charges, section 3.1) ; le cahier cite SQLDelight « ou équivalent maintenu ». Décision technique prise en autonomie.
- Options : SQLDelight 2.4.1 (Cash App, Apache 2.0, version d'octobre 2026, Android, JVM et iOS) ; Room en version multiplateforme (Google) ; fichiers JSON sans base.
- Décision : SQLDelight 2.4.1, dialecte SQLite 3.18 (celui d'Android 8), schéma dans `shared/core/src/commonMain/sqldelight`, pilotes Android, JVM (sqlite-jdbc) et iOS natif. Les étiquettes des événements sont indexées dans une table séparée, car les fonctions JSON de SQLite n'existent pas sur Android 8.
- Raison : prévu par le cahier des charges, requêtes SQL vérifiées à la compilation, code généré sans réflexion, maintenu activement.
- Conséquences : `LocalEventStore` applique les mêmes règles de remplacement que le serveur (`EventReplacement`) ; les publications de l'utilisateur attendent dans la base locale (`pending`) jusqu'à leur acceptation par le serveur (`SyncService`).

## D-021 (2026-11-05) API des événements signés et client HTTP

- Décision : le serveur expose `POST /api/v1/events` (publication d'un événement signé, vérifié avant stockage, 64 Ko au plus, types éphémères refusés) et `GET /api/v1/events` avec filtres (`kind`, `author`, `identifier`, `since`, `until`, `limit`), du plus récent au plus ancien ; détail dans `docs/FORMATS.md`. Côté applications, l'adaptateur `ServerPublicationPort` (client HTTP Ktor : moteur CIO sur Android et ordinateur, Darwin sur iOS) met en œuvre le port de publication ; les pannes réseau deviennent « indisponible » et l'événement reste en attente locale.
- Raison : cahier des charges, section 3.2 (port de publication, phase 1 sur le serveur) ; aucune journalisation de l'expéditeur.
- Reste à faire avant toute mise en ligne : limitation de débit et preuve de travail (anti-spam), règles sur qui peut publier quel type (comptes vérifiés, éditeurs), file de modération.

## D-022 (2026-11-05) Contenus éditoriaux : format NIP-23, auteurs de confiance, contenus intégrés à l'application

- Contexte : les fiches pratiques (M6, CO-03) doivent être lisibles hors ligne dès l'installation (S-26) et sans compte (S-09) ; chaque contenu affiche son origine, humaine ou assistée par IA (décision de la propriétaire du 2026-11-04). Décision technique prise en autonomie.
- Décision :
  - un contenu éditorial est un événement de type 30023 (NIP-23, « long-form content ») : corps en Markdown, étiquettes `d` (identifiant stable), `title`, `summary`, `published_at`, `t` (thèmes), complétées par `ppf-type` (fiche-pratique, dossier, decryptage, article) et `ppf-origin` (human, ai-assisted) ; détail dans `docs/FORMATS.md` ;
  - l'application n'affiche que les contenus d'auteurs de confiance (`EditorialTrust`) : en phase 1 une liste inscrite dans l'application ; plus tard une liste signée par la clé de la plateforme et distribuée comme un événement ;
  - les contenus de démonstration vivent dans `content/` (Markdown avec en-tête), sont signés par l'outil `tools/content-signer` avec une clé d'exemple gardée hors dépôt, puis intégrés à l'application (`shared/ui/.../files/contents/fiches.json`) et importés dans la base locale au premier démarrage ;
  - la lecture utilise un petit interpréteur Markdown maison (titres, paragraphes, listes, gras, liens affichés en texte) plutôt qu'une bibliothèque : les liens ne s'ouvrent pas sans avertissement « vous quittez PPF ».
- Raison : format déjà lu par l'écosystème Nostr, signé par construction, remplaçable par identifiant stable ; contenus essentiels disponibles sans réseau ; le moins de dépendances possible.
- Conséquences : la clé d'exemple doit être retirée de la liste de confiance avant le lancement public ; tout contenu de `content/` est marqué [EXEMPLE] et doit être relu par un avocat ; le circuit éditorial complet (brouillon, relecture, validation, S-70) reste à construire.

## D-023 (2026-11-06) Fil social par défaut, barre du bas, direction de design (décisions de la propriétaire)

- Décision de la propriétaire, consignée dans les brainstormings du 6 novembre 2026 et dans le cahier fonctionnel 1.1 (module M18) : l'écran par défaut est un fil de type réseau social (publications des comptes suivis, suggestions, partager, aimer, commenter, publier) ; barre du bas fixe sur téléphone avec Fil, En direct, Carte et Mon espace ; les quatre piliers dans le menu en accordéon ; design moderne, épuré, ludique mais mature, d'inspiration nationale sans confusion possible avec l'État ; fiches ultra lisibles avec encarts « En bref », « Légal » (vert) et « Illégal » (rouge).
- Conséquences techniques : nouveaux espaces `FIL` et `CARTE` ; refonte de la coquille (barre du bas, rail, barre latérale) ; `FeedScreen` et publication de notes courtes (type 1) par le port de publication ; réactions gardées sur l'appareil et commentaires fermés tant que leur cadrage de vie privée n'est pas tranché (entrée « À étudier » du brainstorming).

## D-024 (2026-11-06) Identité visuelle, version 1 : palette, polices libres, encarts

- Décision technique prise en autonomie, à valider par la propriétaire sur l'application : palette bleu nuit (#1E3A8A) et corail (#D9432E) avec un jaune soleil en tertiaire, volontairement éloignée du bleu et du rouge officiels de l'État, sans tricolore ni symbole officiel ; polices libres auto-hébergées, Inter 4.1 pour le texte et Outfit pour les titres, toutes deux sous licence SIL Open Font License 1.1 (textes dans `licenses/`) ; formes arrondies ; corps de texte à 17 sp pour la lecture des fiches ; mode sombre conservé ; contrastes vérifiés au niveau AA.
- Encarts : blocs `::: en-bref`, `::: legal`, `::: illegal`, `::: attention`, `::: conseil` dans les contenus Markdown, rendus par le composant `Callout` (couleur, barre d'accent, icône et titre, pour que la couleur ne porte jamais seule le sens).
- Raison : aucune ressource chargée depuis un tiers (cahier des charges, principe 6) ; licences compatibles avec l'AGPL et les magasins d'applications ; polices conçues pour l'écran.
- Conséquences : environ 1,8 Mo de polices dans l'application ; vérification de leur présence dans l'APK par `tools/check-apk.sh`.

## D-025 (2026-11-06) Identité visuelle définitive : maquettes de la propriétaire, Atkinson Hyperlegible, icônes Lucide

- Décision de la propriétaire (6 novembre 2026) : l'identité visuelle à respecter est celle de ses maquettes « Politisation Populaire Française : interface communautaire » (cinq écrans : fil sur téléphone, profil et confidentialité, page d'un collectif, messagerie chiffrée, fil sur ordinateur, chacun en clair et en sombre). Elle remplace la proposition D-024.
- Jetons extraits des maquettes et codés tels quels dans `PpfTheme` (`PpfPalette`) : bleu de marque #2B3A8F (sombre #9AA6F0), accent carmin #C8213A (sombre #FF7083), surfaces blanche et #F3F5FB, encre #14182B, bordures #DCE0EE, teintes de marque, d'accent, de succès (#1D6B45) et d'avertissement (#7A4F00), six couleurs d'avatar ; arrondis de 6, 10 et 16 dp ; ombres légères ; une seule police, Atkinson Hyperlegible (Braille Institute, SIL Open Font License 1.1, version 1.006 téléchargée depuis le dépôt officiel), en graisses 400 et 700 ; échelle de texte 40, 28, 22, 18, 16, 14 et 12 ; icônes Lucide 1.69.0 (licence ISC) converties en vecteurs Kotlin par `tools/icons/generate_icons.py`.
- Structure reprise des maquettes : barre du haut (menu, marque sur deux lignes, recherche, notifications, avatar qui ouvre Mon espace), onglets soulignés, bandeau « EN DIRECT », zone de publication, carte de publication (avatar, nom, badge de compte vérifié, heure et visibilité, badge [EXEMPLE], actions J'aime, Commenter, Relayer), barre latérale de 280 dp avec la mention « Plateforme citoyenne indépendante, sans lien avec l'État ni le gouvernement », profil avec « Qui voit quoi » et « Mes votes et soutiens : jamais visibles ».
- Écart assumé, à trancher par la propriétaire : les maquettes remplacent la barre du bas par deux boutons flottants « AIDE » et « Carte » ; l'application garde la barre du bas demandée le 6 novembre (Fil, En direct, Carte), « Mon espace » passant dans l'avatar de la barre du haut comme sur les maquettes. Le bouton « AIDE » (secours en manifestation) est une fonctionnalité à spécifier (brainstorming du cahier fonctionnel).
- Raison : respecter l'identité choisie par la propriétaire ; police conçue pour la lisibilité, cohérente avec l'exigence d'accessibilité ; aucune ressource externe ; licences compatibles avec l'AGPL et les magasins d'applications.
- Conséquences : polices ramenées de 1,8 Mo à 220 Ko (Inter et Outfit retirées) ; `tools/check-apk.sh` vérifie la nouvelle police ; le site web reprend les mêmes jetons (`web/src/main/resources/static/site.css`) avec la police servie depuis `/static/fonts` ; les sources SVG des icônes vivent dans `tools/icons/lucide/`.

## D-026 (2026-11-06) Barre du bas à cinq entrées, vues du fil au doigt, messagerie et revendications (décisions de la propriétaire)

- Décisions de la propriétaire (6 novembre 2026, nuit) : la barre du bas est confirmée et reçoit deux accès rapides de plus, **Messages** et **Revendications** (Fil, En direct, Carte, Messages, Revendications) ; la messagerie fait partie des ambitions fonctionnelles, sur le modèle des messageries de réseau social (conversations privées, de groupe, de collectif, syndicat ou association) avec des vues distinctes : privé, public (associations, syndicats, collectifs) et flux des médias et des élus suivis ; le fil propose une vue qui ne montre que les revendications ; les vues du fil (Mon cercle, Collectifs, Suivis, Près de moi, etc.) se parcourent en glissant le doigt de droite à gauche, comme sur son image de référence.
- Mise en œuvre : `Space.MESSAGES` et `Space.REVENDICATIONS` ; barre du bas propre (cinq entrées, étiquette « Revendications » coupée par un trait d'union conditionnel) ; `HorizontalPager` sous les onglets du fil (vues Tout, Comptes PPF, Revendications, Mes publications) et des messages (Privé, Collectifs, Flux) ; type de contenu `revendication` (événement NIP-23 avec `ppf-status` et `ppf-territory`, `docs/FORMATS.md`), trois revendications [EXEMPLE] signées ; écran « Revendications » (mur en cards, détail, boutons Pour, Contre et Débat désactivés tant que la méthode « une personne, une voix » n'est pas validée) ; écran « Messages » qui montre l'organisation prévue sans aucune conversation possible.
- Ce qui reste à décider : le protocole de messagerie chiffrée (brainstorming du cahier des charges, entrée du 2026-11-06, trois options et une recommandation) ; la méthode « une personne, une voix » (mur des revendications) ; les onglets Mon cercle, Collectifs, Suivis et Près de moi viendront avec les cercles et les collectifs.
- Raison : demandes explicites de la propriétaire ; rien n'est codé qui dépende des deux décisions ouvertes (sécurité et anti-fraude).

## D-027 (2026-11-07) Protocole de la messagerie chiffrée : messages privés NIP-17 et salons NIP-29 sur Nostr

- Décision de la propriétaire (7 novembre 2026), parmi les trois options du brainstorming du cahier des charges (entrée du 6 novembre) : option A. Conversations privées et de petit groupe en NIP-17 (chiffrement NIP-44 du message, puis double enveloppe « gift wrap » qui cache l'expéditeur, le destinataire et l'heure) ; salons des collectifs, syndicats et associations en NIP-29 (groupes gérés par le relais, rôles d'administrateurs) ; notre serveur joue le rôle de relais. Le chiffrement de groupe MLS (option B) reste la cible ultérieure : la couche de messagerie est écrite derrière une interface pour pouvoir changer de protocole sans toucher aux écrans.
- Règles qui s'appliquent quel que soit le protocole : clés sur l'appareil ; aucune liste de conversations ni de contacts sur le serveur ; vérification des contacts par QR code (S-128) ; signalement d'un message chiffré par son destinataire seulement (S-127) ; accusés de lecture et « en train d'écrire » désactivés par défaut (S-125) ; le mode hors ligne reste bitchat (cahier des charges, 3.4).
- Raison : mêmes clés que les comptes, aucun serveur supplémentaire, protocole documenté et interopérable, coût nul ; limites connues (pas de confidentialité persistante, petits groupes) acceptées pour la phase 1 et couvertes par le passage prévu à MLS.
- Conséquences : nouveau module de messagerie dans `shared/core` (chiffrement NIP-44, enveloppes NIP-17, groupes NIP-29), relais sur le serveur (abonnements, stockage des enveloppes sans lecture), écrans de conversation ; à inscrire dans le cahier des charges 3.4 lors de sa prochaine version.

## D-028 (2026-11-07) Messagerie : conservation des enveloppes, création des salons, suivre les comptes PPF (décisions de la propriétaire)

- **Conservation des messages sur le serveur** : trois mois au minimum (défaut `PPF_WRAP_RETENTION_DAYS` = 90), durée réglable par chaque utilisateur pour sa propre boîte, dans une limite haute du serveur (`PPF_WRAP_RETENTION_MAX_DAYS`, 365). Le réglage est un événement NIP-78 (type 30078, `d` = `ppf:mailbox`, étiquette `retention_days`) signé par l'utilisateur, que le serveur lit pour sa boîte. Le plafond par boîte passe de 1000 à 5000 enveloppes (`PPF_WRAP_MAX_PER_RECIPIENT`) : protection contre l'inondation, non limite d'usage. La propriétaire a refusé les 30 jours : perdre les messages reçus pendant une absence est hors de question.
- **Création des salons de collectifs** : ouverte à tout compte, pour laisser exister les initiatives citoyennes sans que PPF soit accusée de censure ; chaque salon porte l'acceptation de la charte de valeurs (étiquette `ppf-charter`) et un territoire (`ppf-territory`) ; le serveur refuse les doublons (même nom normalisé et même territoire, réponse `409 group_exists` avec le salon existant à rejoindre) pour éviter la dispersion des citoyens entre collectifs de même but et de même lieu ; la détection par le sens (IA auto-hébergée) et la modération a posteriori viendront ensuite (brainstorming).
- **Suivre les comptes de PPF** : autorisé comme pour tout compte ; PPF ne s'approprie pas d'espace particulier, la plateforme facilite la politisation et l'action citoyennes.
- Raison : réponses de la propriétaire aux questions ouvertes des blocs « serveur relais » et « comptes suivis » du 7 novembre 2026.

## D-029 (2026-11-06) Mur des revendications : vues personnalisables, filtres et tri

- Demande de la propriétaire (6 novembre 2026) : différentes vues, des vues personnelles, des filtres ; « Filtre : par thème, par ville, popularité, nouveauté etc. Vue : thème société, médical, revendications portées par ceux que le user suit etc. »
- Décision : une **vue** est une requête enregistrée et nommée (thèmes, territoires, statuts, auteurs : tout le monde, comptes suivis ou moi, mots-clés, tri par défaut) ; un **filtre** est une requête temporaire appliquée par-dessus la vue en cours : ce que la vue fixe reste fixé (affiché verrouillé), le filtre complète le reste. Vues prêtes à l'emploi : Toutes (permanente, toujours en premier), Comptes suivis, Mes revendications, Nationales, une vue par thème ; disposition par défaut : Toutes, Comptes suivis, Société, Santé, puis Mes revendications avec un compte. Vues personnelles : 20 au plus, nom de 1 à 40 caractères.
- Décisions techniques prises en autonomie : vues enregistrées **sur l'appareil seulement** (table locale `revendication_view`, migration du schéma local), parce qu'elles révèlent des centres d'intérêt politiques (RGPD, article 9) ; filtres gardés le temps de la session ; textes comparés sans accents ni majuscules (`FrenchText`) ; quinze thèmes à identifiants stables avec des alias pour les anciens sujets (`RevendicationThemes`), liste à valider par la propriétaire ; **popularité provisoire** : tant que le vote n'existe pas, rang du statut donné par l'équipe (campagne, retenue, très soutenue, en débat, nouvelle), puis date de mise à jour, dans une seule fonction qui sera remplacée par les vrais comptes de votes.
- Raison : demande explicite de la propriétaire ; rien n'est codé autour du vote, dont la méthode reste à décider ; aucune donnée nouvelle ne quitte l'appareil.
- Conséquences : cahier fonctionnel 1.3 (M11), entrée du 2026-11-06 du brainstorming du cahier fonctionnel ; « par vite » de la demande compris comme « par ville », c'est-à-dire par territoire.

## D-030 (2026-11-06) Revendications proposées : relecture avant publication et vérifications automatiques

- Demande de la propriétaire (6 novembre 2026) : l'IA gère aussi les problèmes possibles d'une revendication : doublons, loi déjà existante et en vigueur, etc. Rappel de sa décision du 5 novembre : les revendications sont relues avant publication.
- Décisions techniques prises en autonomie :
  - **port de publication unique** : l'application publie toujours par `POST /api/v1/events` ; le serveur garde une revendication d'un compte citoyen dans une file de relecture et répond `202 Accepted` (`pending_review`) ; l'application la garde sur l'appareil tant que le serveur ne l'a pas reçue (hors ligne d'abord) ;
  - **vérifications en trois couches, dans le cœur partagé** (`core/revendications/checks`, utilisé par l'application et par le serveur) : règles du formulaire, seules à pouvoir bloquer (longueurs, une idée, un à trois thèmes, territoire, aucune coordonnée personnelle, jamais recopiée dans le rapport) ; comparaison par les mots avec les revendications existantes, pondérée par la rareté des mots, avec prise en compte du territoire (doublon, proche, déjà retenue ou devenue campagne) ; puis, sur le serveur, l'IA et la recherche dans les textes de loi quand elles seront en service ;
  - **IA** : connecteur au format standard des modèles de conversation (compatible avec un modèle loué par PPF comme avec un service européen à l'usage), éteint tant que la propriétaire n'a pas choisi l'hébergement ; le texte de la revendication est transmis comme données encadrées, avec l'ordre de ne suivre aucune instruction qu'il contiendrait ; la réponse est lue strictement (types connus, jamais bloquante, revendications citées prises dans la liste fournie, liens vers les seuls sites officiels, explications courtes) ; une référence de loi citée de mémoire reste « à vérifier » ;
  - **textes de loi** : interface `LegalCorpus` ; d'abord sans recherche (l'IA cite de mémoire, l'équipe vérifie), puis un index des données ouvertes de Légifrance sur notre serveur (brainstorming du cahier des charges, entrée du 2026-11-06) ;
  - **conservation** : une proposition et son rapport sont effacés du serveur 30 jours après la décision de l'équipe (une revendication publiée vit ensuite dans la table des événements) ; limites par clé (10 en attente, 5 par 24 heures), 1000 propositions en attente pour tout le serveur, 200 relectures par l'IA par 24 heures ;
  - **ce que voit l'IA** : le texte de la proposition et les revendications déjà publiées les plus proches, jamais une proposition d'un autre auteur en attente, jamais une clé publique ni une adresse de contenu (remplacées par des alias) ; le rapport d'une revendication publiée n'est plus servi par le serveur (son identifiant devient public), l'application garde celui qu'elle a reçu.
- Ce qui reste à décider par la propriétaire : l'hébergement du modèle d'IA et, s'il est confié à un prestataire, l'extension de la section 5.6 aux contenus envoyés pour publication (brainstorming du cahier des charges, entrée « Modération et assistant IA : hébergement du modèle ») ; la liste des thèmes.
- Raison : l'outil aide l'auteur et prépare la relecture sans jamais décider à la place d'une personne (cahier des charges 5.6, DSA article 17) ; la même logique sert sur l'appareil et sur le serveur, ce qu'une application modifiée ne peut pas contourner.
- Conséquences : nouvelle table `revendication_proposal` sur le serveur (registre des données), nouvelles variables d'environnement `PPF_AI_*`, écran de relecture de l'équipe à construire avec le back-office de modération (M15).

## D-031 (2026-11-06) Version d'essai Android non débogable et numéro de version croissant

- Contexte : les Samsung de la propriétaire et de sa sœur bloquent l'installation et la mise à jour de l'APK de test (Google Play Protect, Bloqueur automatique de Samsung). Le fichier envoyé jusqu'ici était la version de développement : débogable, toujours numérotée 1.
- Décisions techniques prises en autonomie :
  - nouveau type de compilation **`preview`** (version d'essai), seul APK envoyé sur les téléphones (`gradlew :android:assemblePreview`, fichier `android/build/outputs/apk/preview/android-preview.apk`, artefact `ppf-android-apk` de l'intégration continue) : même identifiant (`.test`) et même clé de test que la version de développement, donc installable par-dessus, mais **non débogable** ; réduction du code (R8) désactivée tant qu'elle ne peut pas être essayée sur un vrai téléphone, parce qu'une erreur de réduction n'apparaît qu'à l'exécution ;
  - **numéro de version** (`versionCode`) = minutes écoulées entre le 1er janvier 2026 (UTC) et le dernier commit : croissant à chaque commit, identique pour toutes les compilations d'un même commit (ordinateur ou intégration continue), remplaçable par `PPF_VERSION_CODE` ; 1 sans Git ;
  - `tools/check-apk.sh` vérifie en plus, sur l'APK envoyé, qu'il n'est pas débogable (`--not-debuggable`) et qu'il est signé par le certificat de test attendu (`--cert-sha256`, empreinte publique inscrite dans `ci.yml`).
- Options écartées : envoyer la version de publication réduite par R8 (impossible à essayer sans téléphone ni émulateur) ; numéro de version donné par l'intégration continue (une version produite sur l'ordinateur aurait alors un numéro plus petit et serait refusée comme une version plus ancienne).
- Raison : une application débogable permet à toute personne qui branche le téléphone par câble de lire les fichiers privés de l'application et d'y attacher un débogueur (OWASP MASVS-RESILIENCE) ; un numéro croissant rend chaque mise à jour lisible et évite les refus. Ces changements ne suppriment pas, à eux seuls, les avertissements des téléphones : seule une distribution par un magasin le fait (brainstorming du cahier des charges, entrée « Distribution des versions de test sur Android », option payante en attente de la propriétaire).
- Conséquences : `android-debug.apk` reste réservé à l'ordinateur ; guide `docs/guides/installer-version-test-android.md` ; la compilation construit une variante de plus.

## D-032 (2026-11-06) Messagerie : choix techniques des conversations privées et des salons

- Contexte : achèvement de la messagerie demandé par la propriétaire (« Termine la messagerie », 6 novembre 2026), dans le cadre de D-027 et D-028.
- Décisions techniques prises en autonomie, à confirmer par la propriétaire :
  - **une seule session de messagerie** pour toute l'exécution de l'application, qui suit le compte affiché ; la conversation ouverte survit à une visite ailleurs ; effacer ses clés efface aussi les conversations, la date du dernier relevé et la liste des salons rejoints (protection par défaut) ;
  - **relevé des messages** à l'ouverture de l'écran Messages, toutes les 20 secondes tant qu'il est affiché et sur le bouton d'actualisation, jamais en arrière-plan en attendant les notifications ; marque « lu » posée par l'affichage de la conversation, jamais envoyée ;
  - **mise en garde avant d'allonger la conservation** des messages (6 mois ou 1 an) : une durée plus longue expose davantage d'anciens messages si la clé est volée (principe 10) ; revenir à 3 mois ne demande rien ;
  - **création d'un salon seulement avec le serveur joignable** (lui seul peut refuser un doublon) ; identifiant formé du nom et du territoire (`collectif-sante-lyon`, puis `-2`, `-3` s'il est pris) ; salons qui se ressemblent montrés comme simple indice, seul le refus du serveur compte ; mise en garde avant la création : le pseudonyme du créateur est publiquement relié au salon ;
  - **refus des doublons de salons sans course** : vérification et enregistrement sous un verrou du processus puis un verrou PostgreSQL ; annuaire des salons dans une table dédiée (`group_directory`), pour que la recherche et les doublons couvrent tous les salons ; description bornée à 8 Kio, nom et territoire à 100 caractères ;
  - deux volets (liste et conversation, liste et salon) sur les fenêtres larges, plein écran avec retour sur téléphone.
- Raison : demande de la propriétaire ; aucune dépense ; aucune donnée nouvelle ne quitte l'appareil hormis ce que les décisions D-027 et D-028 prévoient.
- À trancher par la propriétaire (brainstorming du cahier fonctionnel) : squat d'un nom de salon sur un territoire (modération par l'équipe, ou ne pas compter les salons fermés comme doublons) ; proposer une clé distincte pour créer un salon ; limite de taille des conversations de groupe.

