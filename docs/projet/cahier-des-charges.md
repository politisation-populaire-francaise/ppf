# Politisation Populaire Française : cahier des charges

- Version : 1.4
- Date : 7 novembre 2026
- Statut : définitif. Toute évolution passe d'abord par `brainstorming-cahier-des-charges.md`.
- Documents liés : `vision.md` (pourquoi), `cahier-fonctionnel.md` (ce que fait le produit).

Ce document fixe **ce que la plateforme doit respecter** : périmètre, exigences, architecture, technologies, cadre légal et planning. Le détail des fonctionnalités se trouve dans le cahier fonctionnel.

## 1. Objet et périmètre

### 1.1 Objet
Construire la plateforme numérique de Politisation Populaire Française, au service de la mission décrite dans `vision.md` : redonner l'espace politique aux citoyens.

### 1.2 Plateformes
- **Site web** : rendu côté serveur, lisible sans JavaScript, installable en application web (PWA), consultable hors ligne pour les contenus essentiels.
- **Application Android** (Android 8 et plus).
- **Application iOS** : le code est écrit compatible dès le départ ; la compilation et la publication sont reportées jusqu'à l'ouverture d'un compte Apple Developer.
- **Application de bureau** (Windows, macOS, Linux).
- Les applications partagent **une seule base de code dès la première ligne**, avec des **écrans adaptatifs** (téléphone, tablette, ordinateur) dès le premier écran.
- **Noms de domaine** : domaine principal politisation-populaire-francaise.org ; extensions .fr, .com, .eu, .net et .app réservées pour protéger le nom.

### 1.3 Modules
Le périmètre fonctionnel comprend 16 modules, détaillés dans le cahier fonctionnel :

| Module | Pilier |
|---|---|
| M1 Carte et agenda des mobilisations | Recenser |
| M2 Observatoire : les chiffres | Recenser |
| M3 Annuaire des organisations | S'organiser |
| M4 Documentation et archives | Documenter |
| M5 Témoignages | Documenter |
| M6 Informer | Informer |
| M7 Éduquer | Éduquer |
| M8 Bibliothèque numérique | Éduquer |
| M9 International | Informer |
| M10 Pétitions et mobilisation massive | Se politiser |
| M11 Mur des revendications | Se politiser |
| M12 S'organiser (mobilisation) | S'organiser |
| M13 Se politiser : reprendre le pouvoir | Se politiser |
| M14 Messagerie hors ligne chiffrée | Communiquer |
| M15 Modération | Transverse |
| M16 Comptes et rôles | Transverse |

### 1.4 Parties prenantes
- **Propriétaire du projet** : valide toutes les décisions structurantes. Ne code pas.
- **IA développeuse** : conçoit, code, teste et documente.
- **Équipe PPF** : éditeurs, modérateurs, administrateurs.
- **Organisations partenaires** vérifiées.
- **Utilisateurs** : lecteurs anonymes et contributeurs pseudonymes.

## 2. Principes non négociables

1. **Sécurité et vie privée d'abord.** On ne collecte que le strict nécessaire.
2. **Tout est sourcé.** Chaque chiffre, fait ou citation affiché a une source, une date et un niveau de fiabilité.
3. **Résistance à la censure.** L'information doit survivre à la coupure du serveur, du nom de domaine ou au retrait des stores.
4. **Légalité.** Respect du droit français et européen ; aucune prise juridique pour fermer la plateforme.
5. **Accessibilité.** Conformité WCAG 2.2 niveau AA (référentiel RGAA). Fonctionne sur des téléphones anciens (Android 8+) et avec une connexion faible.
6. **Aucun traceur.** Aucun service Google Analytics, Firebase, Meta, aucun SDK publicitaire, aucune police ou ressource chargée depuis un tiers.
7. **Open source**, avec un dépôt protégé (section 8).

<!-- Principe 8 retiré le 7 novembre 2026 (version 1.4) ; les principes 9 et 10 gardent leur numéro, auquel renvoient les autres documents. -->

9. **Les opinions politiques sont des données sensibles** (article 9 du RGPD) : votes, soutiens, revendications, participation à un groupe ou à une campagne ne sont jamais reliés à une identité réelle par la plateforme, sont stockés de façon minimale et ne servent jamais à constituer un profil politique individuel. Seuls des résultats agrégés sont publiés. Un utilisateur peut toutefois choisir de rendre publiques ses propres informations (principe 10).
10. **Protéger par défaut, laisser le choix.** Tous les réglages de confidentialité et d'anonymat sont au niveau le plus élevé par défaut. Chaque utilisateur peut les assouplir autant qu'il le souhaite pour ses propres données, y compris les rendre publiques ; avant chaque assouplissement, une mise en garde simple explique les risques de rendre une information publique et de la stocker dans l'application. La plateforme est un garde-fou : elle ne décide jamais à la place de l'utilisateur sur sa confidentialité et ses données personnelles. Cette liberté porte sur ses propres données, jamais sur celles des autres, et ne lève ni les obligations légales ni les protections techniques communes à tous (par exemple, le serveur ne relie jamais un vote à un compte).

## 3. Architecture

L'architecture cible est hybride. Elle est construite progressivement, mais **les fondations de la cible sont posées dès la phase 1**.

### 3.1 Cœur : serveur central (phases 1 et 2)
- Un serveur sur un VPS Hostinger (pas l'hébergement mutualisé), dans un centre de données de l'Union européenne situé hors de France. Hébergement et environnements : section 3.5.
- Il gère ce qui nécessite une autorité : données vérifiées, annuaire, archives, modération, recherche, comptes.
- Les applications sont **offline-first** : copie locale des contenus consultés et des données essentielles (fiches pratiques, carte, agenda), consultables sans réseau, synchronisées dès qu'une connexion est disponible.

### 3.2 Fondations obligatoires dès la phase 1
- **Signature des contenus** : chaque contenu publié est signé numériquement selon un schéma compatible Nostr (signatures Schnorr secp256k1, BIP-340), pour pouvoir publier plus tard sur Nostr sans tout re-signer. La clé de signature de la plateforme n'est pas stockée en clair sur le serveur principal. Rotation et révocation prévues.
- **Identités utilisateurs = paires de clés** générées sur l'appareil, compatibles Nostr. Pas de numéro de téléphone, pas d'e-mail obligatoire. Récupération par phrase secrète.
- **Couche de publication abstraite** : toute publication passe par une interface unique (un « port »). Phase 1 : publication sur le serveur. Phase 3 : branchement de Nostr et des miroirs sans modifier le reste.
- **Identifiants de contenu stables** et format d'export ouvert (JSON documenté), pour générer des miroirs statiques.
- **Archivage horodaté des engagements publics** (engagements de candidats, déclarations) : empreinte SHA-256 et horodatage, pour qu'aucun engagement ne puisse être nié ou effacé.

### 3.3 Couches de résilience (phase 3)
- **Module Nostr** : copie automatique des contenus validés vers plusieurs relais Nostr publics et un relais propre au projet. L'application sait lire depuis Nostr si le serveur est inaccessible.
- **Miroirs statiques** : export régulier du site en pages statiques, déployable n'importe où.
- **Accès Tor** : adresse .onion pour le site et l'API.

### 3.4 Messagerie hors ligne (phase 4)
- Module indépendant, sans aucun lien avec le serveur.
- Basé sur le protocole bitchat (Bluetooth Low Energy en réseau maillé, chiffrement Noise, Nostr en relais Internet), compatible avec les applications bitchat existantes.
- Réutilisation du code bitchat Android (licence MIT) et iOS (domaine public) en conservant les mentions de licence.

### 3.5 Hébergement et environnements
- **Prestataire unique : Hostinger** (société établie en Lituanie) pour les serveurs et les noms de domaine. Seule exception : les sauvegardes chiffrées, stockées chez un autre prestataire européen (section 5.3).
- **Centre de données dans l'Union européenne, hors de France** (Lituanie, Allemagne ou Pays-Bas), pour rester dans le cadre du RGPD tout en imposant aux autorités les procédures de coopération entre États pour toute saisie physique. Cette localisation est un frein limité : la protection principale contre la censure reste l'architecture de résilience (section 3.3) et la minimisation des données (section 5.1).
- **Un seul serveur au départ** (VPS KVM 1 : 1 vCPU, 4 Go de mémoire, 50 Go de disque), loué au moment du premier déploiement, pas avant. Pendant le développement, il ne sert que de préproduction. Au lancement public, il accueille la production et la préproduction.
- **Environnements séparés autant que possible** sur ce serveur : conteneurs, bases de données, réseaux, utilisateurs système et secrets distincts pour la production et la préproduction ; ressources limitées pour la préproduction, pour qu'elle ne puisse jamais ralentir ou bloquer la production.
- **Préproduction fermée au public** : accessible uniquement par un tunnel chiffré (WireGuard) installé sur les appareils de l'équipe, en plus d'un mot de passe. Elle ne contient **jamais de données réelles**, uniquement des données fictives marquées [EXEMPLE]. Même durcissement que la production : pare-feu, connexion par clé uniquement, mises à jour de sécurité automatiques.
- **Deuxième serveur dédié à la production** dès que le budget le permet, pour un isolement complet.
- **Circuit de mise en ligne** : tests automatiques, déploiement en préproduction, validation par la propriétaire, puis production.

## 4. Technologies retenues

Si une meilleure option existe au moment du développement, elle est proposée avec justification avant de démarrer.

- **Applications** : Kotlin Multiplatform + Compose Multiplatform, logique métier partagée, base locale SQLDelight (ou équivalent maintenu). Ce choix permet de réutiliser le code Kotlin de bitchat Android.
- **Site web** : framework de rendu côté serveur à proposer.
- **Serveur** : Kotlin + Ktor (modèles partagés avec les applications), PostgreSQL + PostGIS (carte), recherche plein texte PostgreSQL (moteur dédié plus tard si besoin).
- **Infrastructure** : Docker Compose, reverse proxy avec HTTPS automatique (Caddy ou équivalent), infrastructure décrite en code pour pouvoir tout redéployer chez un autre hébergeur en quelques heures.
- **Cartes** : OpenStreetMap via MapLibre, jamais Google Maps. Tuiles hors ligne pour les zones choisies (phase ultérieure).
- **Vidéos** : pas de diffusion directe depuis le VPS ; instance PeerTube (propre ou partenaire). Podcasts via flux RSS.
- **Notifications** : UnifiedPush avec un serveur ntfy auto-hébergé sur Android (pas de Firebase). Sur iOS, APNs : aucun contenu sensible, seulement un signal de réveil.
- **Anti-spam** : limitation de débit et preuve de travail auto-hébergée (type ALTCHA). Jamais de reCAPTCHA.
- **Données publiques** : imports automatisés depuis les sources ouvertes officielles (data.gouv.fr, Répertoire national des élus, données ouvertes de l'Assemblée nationale et du Sénat, répertoire des représentants d'intérêts de la HATVP), avec la date de mise à jour affichée.
- **Intégration continue** : compilation automatique de l'APK et des applications de bureau, tests et analyses de sécurité à chaque modification, dans les quotas gratuits du service d'intégration continue. Builds reproductibles et releases signées. La compilation iOS (machines macOS) sera ajoutée avec le compte Apple Developer ; d'ici là, le code commun n'utilise que des bibliothèques compatibles avec iOS.
- **Distribution** : APK téléchargeable sur le site et dans les releases du dépôt, applications de bureau dans les releases du dépôt, puis F-Droid, Google Play et, quand le compte Apple sera ouvert, l'App Store. Partage de l'APK hors ligne entre téléphones.

## 5. Sécurité et vie privée

### 5.1 Données
- Lecture sans compte. Compte (pseudonyme, par paire de clés) seulement pour contribuer, voter, soutenir, signaler.
- Votes et soutiens enregistrés de manière à ne pas pouvoir être reliés à une personne réelle ; aucun historique individuel de votes exposé ; publication de résultats agrégés uniquement. La progression dans les parcours éducatifs est stockée uniquement sur l'appareil.
- **Confidentialité maximale par défaut et libre choix** (principe 10) : tous les réglages de confidentialité et d'anonymat sont au plus haut à la création du compte. Tout assouplissement est fait par l'utilisateur lui-même, pour ses propres données, après une mise en garde simple sur les risques ; il peut revenir en arrière à tout moment, la mise en garde rappelant qu'une information déjà vue ou copiée par d'autres ne peut pas être effacée partout.
- **Suppression systématique des métadonnées** (GPS, modèle d'appareil, date EXIF, etc.) de toutes les photos et vidéos, **sur l'appareil, avant l'envoi**. Outil de floutage des visages optionnel avant publication (pas de floutage par défaut).
- Valeur de preuve : empreinte SHA-256 du fichier original, horodatée (OpenTimestamps ou horodatage RFC 3161).
- Témoignages en attente de modération et signalements chiffrés avec la clé publique de l'équipe de modération.
- Pas de journalisation des adresses IP. Si un journal anti-abus est indispensable : tronqué, haché, effacé sous 24 heures maximum.
- Registre des données (RGPD) listant exactement ce qui est stocké, pourquoi et combien de temps.

### 5.2 Protection des adresses IP
- Adresse .onion (phase 3).
- Option Tor intégrée dans l'application (Arti ou bibliothèque équivalente maintenue) ou compatibilité avec Orbot.
- Guide dans l'application expliquant Tor et recommandant un VPN fiable (Proton VPN a une offre gratuite), en signalant que beaucoup de VPN gratuits revendent les données.

### 5.3 Serveur et équipe
- HTTPS partout, HSTS, en-têtes de sécurité stricts (CSP, etc.).
- Double authentification obligatoire (TOTP ou clé de sécurité) pour tous les comptes de l'équipe.
- Rôles et permissions minimales. Journal d'audit de toutes les actions de modération et d'administration.
- Sauvegardes chiffrées de la production, stockées chez un autre prestataire que l'hébergeur, restauration testée régulièrement.
- Compte de l'hébergeur (qui gère aussi les noms de domaine) protégé par une double authentification forte (clé de sécurité physique si Hostinger l'accepte) ; domaines verrouillés contre les transferts, renouvellement automatique activé, DNSSEC si disponible.
- Aucune mise en production sans passage par la préproduction et validation de la propriétaire (section 3.5).
- Plan de reprise : documentation pour redéployer le service ailleurs, à partir des sauvegardes, si l'hébergeur coupe ou si le compte est suspendu.

### 5.4 Code
- Aucun secret dans le dépôt (fichiers .env exclus, détection automatique des secrets avant chaque commit).
- Dépendances verrouillées, auditées automatiquement (Renovate ou Dependabot), le moins de dépendances possible.
- Référentiels : OWASP MASVS pour les applications mobiles, OWASP ASVS pour le serveur. Checklist de sécurité avant chaque release.

### 5.5 Modèle de menaces
Un modèle de menaces est rédigé et tenu à jour. Menaces minimales à couvrir : réquisitions judiciaires, saisie du serveur, suspension du nom de domaine, retrait des stores, attaques DDoS, piratage, campagnes de faux comptes et de manipulation des chiffres, manipulation des votes (mur des revendications, engagements, votations), désinformation injectée, infiltration de l'équipe, contributions de code malveillantes, tentatives de faire passer la plateforme pour le soutien d'un candidat ou d'un parti afin d'exposer l'association à des poursuites (section 9.2).

### 5.6 Modération assistée par IA
- Décision de la propriétaire (5 novembre 2026) : une IA analyse les contenus publics juste après leur mise en ligne, sans retarder la publication.
  - **Alerte rouge** : un contenu estimé grave et manifestement illicite (appel à la violence, menaces, contenu pédocriminel ou terroriste, divulgation d'informations personnelles) est masqué immédiatement, en attendant la décision de l'équipe, prévenue par une notification du plus haut niveau d'importance.
  - **Alerte orange** : un contenu limite (risque de diffamation ou de harcèlement, doute sur la charte) reste publié ; l'équipe est prévenue.
  - Tous les autres contenus sont publiés directement, sans notification à l'équipe.
  - La décision finale reste toujours humaine : l'équipe confirme ou annule chaque masquage ; l'auteur est informé du motif et du recours à un outil automatisé, et peut contester (DSA).
  - Tout utilisateur peut à tout moment signaler manuellement un contenu ou un autre utilisateur.
  - L'IA ne lit jamais les échanges chiffrés.
- Envoyer les contenus des utilisateurs à une API d'IA externe crée un risque pour la vie privée : privilégier un modèle auto-hébergé, ou limiter l'usage d'une API externe aux contenus déjà publics. Choix soumis à la propriétaire.

## 6. Accessibilité, compatibilité et performance

- WCAG 2.2 niveau AA (RGAA) : lecteurs d'écran, contrastes, tailles de texte.
- Android 8 et plus ; fonctionnement avec une connexion faible ; mode économie de données.
- Lecture du site sans JavaScript.
- Contenus essentiels consultables hors ligne.
- Interface en français, structurée pour la traduction (anglais et autres langues plus tard).

## 7. Design et expérience utilisateur

- Moderne, sobre, lisible, avec une identité propre à Politisation Populaire Française. Mode clair et sombre.
- Comprendre ce qui se passe en moins de 30 secondes dès l'écran d'accueil : chiffres clés, prochaines mobilisations, dernières informations essentielles, et accès direct au bloc « Se politiser ».
- Navigation structurée selon le chemin des piliers : savoir, comprendre, agir, reprendre le pouvoir.
- Langage clair, option « Facile à lire et à comprendre » pour les fiches pratiques et les contenus essentiels du bloc « Se politiser ».
- Accueil des nouveaux utilisateurs avec une explication simple de la sécurité (Tor, VPN, ce que l'application collecte ou non, y compris sur les votes et soutiens).
- Maquettes ou captures présentées à la propriétaire pour validation avant de finaliser chaque écran important.
- Interdiction d'imiter l'identité visuelle officielle de l'État (logo Marianne officiel, bloc-marque) et de réutiliser l'ancien nom « Marianne Libre ».

## 8. Open source et protection du dépôt

- Licence à choisir par la propriétaire (AGPL-3.0 envisagée), après vérification de sa compatibilité avec l'App Store et les licences de bitchat.
- Dépôt principal et miroir automatique sur Codeberg.
- Branche principale protégée : aucune modification sans relecture et sans tests au vert. Seuls les mainteneurs valident les contributions externes, toutes relues ligne par ligne.
- Double authentification obligatoire pour les mainteneurs, commits signés.
- Configuration privée hors dépôt : secrets, clés, seuils et règles anti-fraude, réglages de modération.

## 9. Cadre légal

### 9.1 Documents juridiques à produire
Modèles marqués « À FAIRE RELIRE PAR UN AVOCAT » : mentions légales (LCEN), politique de confidentialité (RGPD), conditions d'utilisation, charte de contenu, procédure de signalement et de recours (DSA).

### 9.2 Points d'attention
- **LCEN** : un éditeur non professionnel peut rester anonyme pour le public si l'hébergeur dispose de son identité.
- **Loi de 1881** : diffamation, injure, provocation.
- **Présomption d'innocence** et droit à l'image.
- **RGPD, article 9** : opinions politiques = données sensibles.
- **Digital Services Act** : procédure de signalement et de recours, rapport de transparence.
- **Signalement obligatoire** des contenus pédocriminels et terroristes aux autorités compétentes (PHAROS).
- **Droit électoral et financement politique** :
  - une personne morale (dont l'association PPF) ne peut ni financer une campagne électorale ni consentir à un candidat des avantages, y compris en nature (site, fichiers, communication) ;
  - tout outil qui concerne des candidats (engagements, comparateur, baromètre) les traite tous de façon strictement identique, sans appel à voter ;
  - les dons aux partis sont réservés aux personnes physiques (de nationalité française ou résidant en France) et plafonnés à 7 500 euros par personne et par an ; les dons à un candidat sont plafonnés à 4 600 euros par élection (montants à re-vérifier avant tout guide) ;
  - veille sur les règles applicables en période électorale (propagande, sondages).
- **Données de la HATVP** : les déclarations d'intérêts sont publiées en ligne ; les déclarations de patrimoine des parlementaires ne sont consultables qu'en préfecture et leur publication est interdite. Vérifier précisément ce qui est réutilisable avant tout développement.
- **Droit d'auteur** : aucun contenu piraté.

### 9.3 Charte de contenu

**Autorisé** : critique du gouvernement, des institutions et des élus ; revendications et propositions de réformes, y compris institutionnelles ; images de manifestations et des forces de l'ordre en intervention ; témoignages ; analyses militantes ; contenus satiriques.

**Interdit** :
- Appel ou incitation à la violence, aux crimes ou aux délits
- Divulgation d'informations personnelles (adresse, famille, identité complète, lieu de travail) de qui que ce soit, y compris des policiers et des élus
- Harcèlement, menaces
- Diffamation (accuser une personne nommée d'un crime ou d'un délit sans condamnation ni source établie)
- Haine et discrimination
- Contenus pédocriminels, terroristes
- Contenus protégés par le droit d'auteur sans autorisation
- Fausses informations délibérées

La charte est affichée dans l'application, acceptée par chaque contributeur et sert de référence à la modération.

## 10. Planning

### 10.1 Contraintes de calendrier
- Le mouvement social est en cours : une première version utilisable doit sortir le plus vite possible.
- Élection présidentielle en avril 2027.
- Législatives possibles à tout moment en cas de dissolution (notamment après la présidentielle), avec un scrutin 20 à 40 jours après la dissolution.

### 10.2 Phases
- **Phase 0, fondations** : dépôt, intégration continue, documentation, modèle de menaces, squelette des applications commun à toutes les plateformes, achat des noms de domaine chez Hostinger dès le départ, guide pas à pas d'achat et de configuration du serveur Hostinger au moment du premier déploiement, déploiement d'un squelette en préproduction, modèles juridiques.
- **Phase 1, première version (site web, Android et ordinateur)** : carte et agenda (M1), observatoire avec les indicateurs prioritaires (M2), fiches pratiques hors ligne (M6), annuaire des organisations (M3), fil d'actualité (M6), CMS éditorial, comptes pseudonymes (M16), signalement et back-office de modération (M15), signature des contenus. Priorités liées au calendrier : mur des revendications avec vote protégé (M11), puis Pacte démocratique version 1 (M13.5) pour une diffusion au début de l'année 2027.
- **Phase 2** : témoignages (M5), documentation et chronologie (M4), fiches des responsables politiques (M4), décryptage des fausses informations (M6), pétitions (M10), modération assistée par IA. Bloc « Se politiser » : comparateur de la présidentielle 2027 (avant avril 2027), étages 1 à 5 de M13, dont le kit du candidat citoyen, prêt avant une éventuelle dissolution.
- **Phase 3, résilience** : module Nostr, miroirs statiques, adresse .onion, Tor dans l'application. Étage 6 de M13 (votations et assemblées citoyennes), après validation de la méthode « une personne, une voix ».
- **Phase 4** : messagerie hors ligne bitchat (M14).
- **Phase 5** : votes des parlementaires sur 50 ans, visualisations avancées, international (M9), éduquer et bibliothèque complets (M7, M8), outils d'organisation et de démocratie interne avancés.
- **Phase 6** : désignation citoyenne d'un candidat (M13.7), après validation juridique par un avocat.
- **iOS, hors phase** : compilation, tests (simulateur, puis bénévoles équipés via TestFlight) et publication sur l'App Store dès que le compte Apple Developer est ouvert, quelle que soit la phase en cours ; jusque-là, le site installable (PWA) sert de solution sur iPhone.

## 11. Critères d'acceptation

Une fonctionnalité n'est livrée que si :
- ses tests automatisés (unitaires, intégration, interface) passent ;
- elle respecte les principes de la section 2 et les exigences de sécurité de la section 5 ;
- elle est conforme à WCAG 2.2 AA ;
- elle a été présentée à la propriétaire, avec une explication pour la tester elle-même ;
- la documentation technique et le suivi d'avancement sont à jour.

## 12. Moyens et hypothèses

- Hébergement : VPS Hostinger, détaillé à la section 3.5.
- Budget : 300 € couvrent la mise en route (noms de domaine, serveur payé 24 mois d'avance, sauvegardes hors site, modération par IA au démarrage). La suite dépend des adhésions et des dons. Aucune dépense n'est engagée sans l'accord de la propriétaire.
- Coûts reportés, à financer plus tard : compte Apple Developer (99 € par an, dispense possible pour une organisation à but non lucratif) et compilation iOS, deuxième serveur pour la production, serveur d'IA dédié, audit de sécurité externe avant le lancement public, avocat, expert-comptable.
- Matériel de test : un téléphone Android et un ordinateur ; pas d'iPhone. Les tests de la messagerie Bluetooth nécessiteront au moins deux appareils Android.
- La propriétaire n'écrit pas de code : toute action manuelle fait l'objet d'un guide pas à pas.

## Journal des versions

| Version | Date | Modifications |
|---|---|---|
| 1.0 | 4 novembre 2026 | Création à partir des instructions initiales du projet et des échanges sur le bloc « Se politiser ». |
| 1.1 | 5 novembre 2026 | Ajout du principe 10 « Protéger par défaut, laisser le choix » et précision du principe 9 ; section 5.1 : confidentialité maximale par défaut et libre choix de l'utilisateur ; section 5.6 : décision sur la modération assistée par IA (alerte de l'équipe, décision humaine). Validé par la propriétaire. |
| 1.2 | 5 novembre 2026 | Section 5.6 : fonctionnement de la modération assistée par IA (alerte rouge avec masquage immédiat, alerte orange sans masquage, publication directe sans notification pour le reste, décision finale humaine, signalement manuel ouvert à tous). Validé par la propriétaire. |
| 1.3 | 5 novembre 2026 | Section 1.2 : une seule base de code et écrans adaptatifs dès le départ, iOS compatible mais compilé et publié plus tard, noms de domaine. Nouvelle section 3.5 : Hostinger pour les serveurs et les domaines, centre de données dans l'Union européenne hors de France, un seul serveur au départ avec production et préproduction séparées, préproduction fermée par tunnel chiffré, circuit de mise en ligne. Section 4 : intégration continue sans iOS pour l'instant, distribution. Section 5.3 : sauvegardes chez un autre prestataire, protection du compte de l'hébergeur. Section 10.2 : phase 0 et phase 1 (application de bureau dès la première version), iOS hors phase. Section 12 : budget de mise en route et coûts reportés. Validé par la propriétaire. |
| 1.4 | 7 novembre 2026 | Section 2 : retrait du principe 8 « Non partisane », sans texte de remplacement (les principes 9 et 10 gardent leur numéro). Section 5.5 : la menace devient « faire passer la plateforme pour le soutien d'un candidat ou d'un parti ». Restent, comme règles de méthode et de droit : le traitement identique des candidats dans les outils électoraux (9.2) et les fiches identiques pour tous (cahier fonctionnel, M17). Décision de la propriétaire du 7 novembre 2026, transmise par le chef d'équipe (D-062). Copie locale modifiée par l'IA ; à reporter dans le projet claude.ai. |
