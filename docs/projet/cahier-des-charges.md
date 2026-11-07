# Politisation Populaire Française : cahier des charges

- Version : 2.0
- Date : 7 novembre 2026
- Statut : définitif. Toute évolution passe d'abord par `brainstorming-cahier-des-charges.md`.
- Documents liés : `vision.md` (pourquoi, version 4.2), `cahier-fonctionnel.md` (ce que fait le produit, version 2.0), `docs/DECISIONS.md` (décisions numérotées), `docs/FORMATS.md` (formats ouverts), `docs/THREAT_MODEL.md` (modèle de menaces), `docs/REGISTRE_DONNEES.md` (registre RGPD), `infra/EGRESS.md` (connexions sortantes du serveur).

Ce document fixe **ce que la plateforme doit respecter** : périmètre, principes, architecture, technologies, sécurité, accessibilité, cadre légal, planning et moyens. Le détail des fonctionnalités se trouve dans le cahier fonctionnel.

**Version 2.0.** Réécriture faite à la demande de la propriétaire du 7 novembre 2026 : ajouter aux deux cahiers tout ce qui est nécessaire à l'accomplissement de la vision, y compris ce qui avait pu être oublié. Elle intègre les décisions déjà prises depuis la version 1.4. Conventions de lecture :
- « (D-xxx) » renvoie à une décision consignée dans `docs/DECISIONS.md` ;
- « (à confirmer) » marque une décision technique prise par l'IA sous l'autorisation générale de la propriétaire, appliquée mais réversible, qu'elle n'a pas encore confirmée ;
- « (décision en attente : … ) » marque une question encore ouverte dans un brainstorming, désignée par le titre de son entrée : rien de ce qui en dépend n'est codé avant la réponse de la propriétaire ;
- « (ajout du chef d'équipe, 7 novembre 2026) » marque une exigence qui ne figurait ni dans les documents ni dans l'inventaire des fonctionnalités validé le 4 novembre 2026.

Les numéros des sections de la version 1.4 sont conservés, parce que le code et les autres documents y renvoient (par exemple 3.4, 3.5, 5.6, 9.2, 9.3, 10.2). Les sections nouvelles prennent des numéros nouveaux : 1.5, 3.6 à 3.8, 4.1, 5.7 à 5.12, 9.4 à 9.8, 13 et les annexes.

## 1. Objet et périmètre

### 1.1 Objet
Construire la plateforme numérique de Politisation Populaire Française (PPF), au service de la mission décrite dans `vision.md` : redonner l'espace politique aux citoyens français. La plateforme suit le chemin des quatre piliers de la vision : **voir** ce qui se passe, **comprendre** comment cela fonctionne, **agir** ensemble et **décider** à nouveau.

### 1.2 Plateformes
- **Site web** : rendu côté serveur en Kotlin (Ktor et kotlinx.html, D-007), lisible sans JavaScript, installable comme une application (PWA), contenus essentiels consultables hors ligne. C'est aussi la solution sur iPhone tant que l'application iOS n'est pas publiée.
- **Application Android** : Android 8 et plus (API 26).
- **Application iOS** : le code est écrit compatible dès le départ ; la compilation et la publication attendent l'ouverture d'un compte Apple Developer (D-004).
- **Application de bureau** : Windows, macOS, Linux.
- **Une seule base de code dès la première ligne** (Kotlin Multiplatform et Compose Multiplatform, D-004), avec des **écrans adaptatifs** (téléphone, tablette, ordinateur, en portrait et en paysage) dès le premier écran ; les seuils de largeur (600 et 840 dp) sont calculés dans le code commun (D-011).
- **Noms de domaine** : domaine principal politisation-populaire-francaise.org ; extensions .fr, .com, .eu, .net et .app réservées pour protéger le nom, achetées chez Hostinger le 5 novembre 2026 (D-001).
- **Identifiants techniques définitifs** : paquet `org.politisationpopulairefrancaise`, application Android `org.politisationpopulairefrancaise.app` (suffixe `.test` pour les versions de test), application macOS `org.politisationpopulairefrancaise.desktop` (D-010). Un identifiant publié ne change plus.

### 1.3 Espaces et modules
La plateforme est organisée comme la vision 4.2 : un accueil « En direct », les quatre espaces **Voir, Comprendre, Agir, Décider**, « Mon espace » (le tableau de bord personnel), le **fil social** (écran par défaut) et la **messagerie**, plus des modules transverses qui servent partout. Le détail de chaque module est dans le cahier fonctionnel 2.0.

Les identifiants M1 à M18 sont conservés, parce que le code et les documents y renvoient ; leurs intitulés suivent le cahier fonctionnel 2.0. Un module qui sert plusieurs espaces est rangé sous son espace principal. Les modules nouveaux du cahier fonctionnel 2.0 sont numérotés de M19 à M35 ; en cas de doute, le cahier fonctionnel fait foi.

| Espace | Modules (numéros du cahier fonctionnel 2.0) | Rôle dans le chemin du citoyen |
|---|---|---|
| **En direct** (accueil) | M19 En direct (MA-01 à MA-06, D-037) : chiffres clés de « Voir » (D-036), prochaines mobilisations d'« Agir » (D-039), « Cette semaine au Parlement » repris de M20 (ajout du chef d'équipe, 7 novembre 2026) | Comprendre ce qui se passe en moins de 30 secondes |
| **Voir** | M2 Observatoire : les chiffres (avec l'indice de gravité) ; M4 Documentation et archives (chronologie, archives horodatées, témoignages publiés) ; M9 International ; M17 Mettre en lumière (absorbe M13.2 : fiches des responsables publics et des partis, promesses, influence, intérêts, actes et discours de haine) ; M20 Le Parlement : votes et textes en cours (données ouvertes, D-064) ; M21 État du pays et argent public local (ajout du chef d'équipe, 7 novembre 2026) | Rendre visible, de façon chiffrée et sourcée, le mouvement, le pouvoir, les libertés et les contre-pouvoirs |
| **Comprendre** | M13.1 « Qui décide de ça ? » et comprendre les élections ; M6 Fiches pratiques, décryptages (cinq verdicts), dossiers et glossaire ; M7 Histoire des luttes et valeurs (dont laïcité et lutte contre le fascisme) ; M8 Bibliothèque numérique ; M22 Simulateurs et visualisations ; M23 Apprendre pas à pas et en classe (enseignants et élèves) | Savoir qui décide de quoi, connaître ses droits, démêler le vrai du faux, apprendre de l'histoire des luttes |
| **Agir** | M1 Carte et agenda des mobilisations ; M3 Annuaire des organisations ; M12 S'organiser (appels, guides, matériel) ; M24 Collectifs ; M5 Témoignages (témoigner, provenance, lanceurs d'alerte) ; M25 Secours, sécurité et défense en manifestation (mode manifestation, bouton AIDE) | Se mobiliser, s'organiser, s'entraider, témoigner |
| **Décider** | M11 Mur des revendications ; M26 Questionnaire citoyen anonyme et baromètre politique ; M13.3 Revendiquer ; M10 Pétitions et mobilisation massive (dont pétitions locales) ; M13.5 Changer les règles (campagnes, baromètre des élus, Pacte démocratique et délibération citoyenne) ; M13.1 suite (interpeller ses élus, consultations publiques officielles) ; M13.4 S'organiser politiquement ; M13.6 Décider nous-mêmes (votations, assemblées tirées au sort) ; M13.7 Élections (calendrier électoral personnel) | Revendiquer, faire campagne, obliger élus et candidats à s'engager, se présenter, décider ensemble |
| **Mon espace** | M27 Mon espace (TE-01 à TE-09, dont « Mes élus », niveau de votant, mes votes et soutiens, mes suivis, mes rappels ; sauvegarde chiffrée S-112) | Le tableau de bord personnel, gardé sur l'appareil |
| **Fil social** | M18 Fil social ; M28 Relations et cercles | La porte d'entrée : l'écran par défaut (D-023) |
| **Messagerie** | M14 Messagerie chiffrée : par Internet dès la phase 1 (D-027, D-028), sans Internet en phase 4 | Échanger en privé et en collectif, même sans Internet ; elle a sa propre entrée dans la barre du bas (D-026) |
| **Transverses** | M29 Socle de l'application (recherche unique, hors ligne, partage, aide) ; M16 Comptes, rôles et intégrité (« une personne, une voix », D-110 à D-113) ; M15 Modération, administration et IA ; M30 Contact, réclamations et obligations légales ; M31 Notifications ; M32 Accessibilité et inclusion ; M33 Résistance à la censure et sécurité numérique (dont « Vérifier un contenu PPF ») ; M34 Transparence de PPF et ouverture des données ; M35 Solidarité, soutien et bénévolat | Ce qui sert dans tous les espaces |

### 1.4 Parties prenantes
- **Propriétaire du projet** : valide toutes les décisions structurantes. Ne code pas. Elle a désigné le chef d'équipe comme son interlocuteur pour transmettre ses décisions et ses validations (D-112).
- **IA développeuse** : plusieurs sessions organisées en pôles (Voir, Comprendre, Agir, Décider, Directeur technique), coordonnées par un chef d'équipe ; elles conçoivent, codent, testent et documentent.
- **Équipe PPF** : éditeurs, modérateurs, administrateurs, deux super-administrateurs au minimum (point d'attention n° 8 de l'inventaire), validateurs bénévoles agréés pour la validation des comptes en personne (D-112).
- **Organisations partenaires vérifiées** de l'annuaire d'« Agir » (D-039, D-042).
- **Avocats partenaires** : relecture juridique avant l'ouverture publique (section 9.8) ; seuls destinataires des originaux chiffrés des témoignages (M5).
- **Utilisateurs** : lecteurs sans compte, contributeurs pseudonymes, élus au compte certifié (D-112).
- **Interlocuteurs officiels** : Arcom (coordinateur pour les services numériques en France), PHAROS (plateforme officielle de signalement des contenus illicites), CNIL, autorités judiciaires.

### 1.5 Statut de PPF
- PPF est un collectif citoyen, bientôt déclaré en **association loi 1901**. La propriétaire a tranché le 7 novembre 2026 la question du statut : PPF est une association, engagée et militante, dont l'objectif premier est la politisation et le retour d'une gouvernance réelle au peuple (D-062 ; brainstorming de la vision, entrée « Statut de PPF : association, parti, ou les deux ? »).
- Noms de domaine et comptes sont au nom de la propriétaire en attendant la déclaration, puis transférés à l'association (D-001).
- En tant que personne morale, l'association ne finance aucune campagne et n'accorde aucun avantage à un candidat, même en nature (section 9.2).

## 2. Principes non négociables

1. **Sécurité et vie privée d'abord.** On ne collecte que le strict nécessaire.
2. **Tout est sourcé.** Chaque chiffre, fait ou citation affiché a une source, une date et un niveau de fiabilité. Quand les sources divergent, elles sont montrées côte à côte.
3. **Résistance à la censure.** L'information doit survivre à la coupure du serveur, du nom de domaine ou au retrait des stores.
4. **Légalité.** Respect du droit français et européen ; aucune prise juridique pour fermer la plateforme.
5. **Accessibilité.** Conformité WCAG 2.2 niveau AA (référentiel RGAA). Fonctionne sur des téléphones anciens (Android 8 et plus) et avec une connexion faible.
6. **Aucun traceur.** Aucun service Google (Analytics, Firebase, Maps, Fonts), Meta, aucun SDK publicitaire ou d'analyse, aucun réseau de diffusion de contenus (CDN) : polices, icônes, cartes et scripts sont auto-hébergés. L'intégration continue bloque toute dépendance ou requête vers un tiers non autorisé (D-013).
7. **Open source**, sous licence AGPL-3.0-or-later avec des permissions pour les magasins d'applications (D-006), dans un dépôt public dès le premier commit et protégé (D-005, section 8).

<!-- Principe 8 retiré le 7 novembre 2026 (version 1.4) ; les principes 9 et 10 gardent leur numéro, auquel renvoient les autres documents. -->

9. **Les opinions politiques sont des données sensibles** (article 9 du RGPD) : votes, soutiens, revendications, participation à un groupe ou à une campagne ne sont jamais reliés à une identité réelle par la plateforme, sont stockés de façon minimale et ne servent jamais à constituer un profil politique individuel. Seuls des résultats agrégés sont publiés. Un utilisateur peut toutefois choisir de rendre publiques ses propres informations (principe 10).
10. **Protéger par défaut, laisser le choix.** Tous les réglages de confidentialité et d'anonymat sont au niveau le plus élevé par défaut. Chaque utilisateur peut les assouplir autant qu'il le souhaite pour ses propres données, y compris les rendre publiques ; avant chaque assouplissement, une mise en garde simple explique les risques de rendre une information publique et de la stocker dans l'application. La plateforme est un garde-fou : elle ne décide jamais à la place de l'utilisateur sur sa confidentialité et ses données personnelles. Cette liberté porte sur ses propres données, jamais sur celles des autres, et ne lève ni les obligations légales ni les protections techniques communes à tous (par exemple, le serveur ne relie jamais un vote à un compte).
11. **La transparence d'abord** (décision de la propriétaire du 7 novembre 2026, D-044 ; principe validé par elle le 7 novembre 2026, « 3. Oui ») : « la transparence passe avant tout le reste ». PPF montre ses méthodes, ses règles, ses chiffres, ses décisions et ses erreurs, et pousse « le curseur au maximum de ce qu'on peut faire en tant que citoyen » ; ce qui est ainsi poussé au maximum est inscrit dans la liste de relecture par un avocat (section 9.8). Cette transparence porte sur PPF et sur l'action publique ; elle ne lève ni les obligations légales (aucun appel ni incitation à la violence, signalement à PHAROS, charte de contenu) ni la protection des personnes (principes 1, 9 et 10). Une contradiction entre transparence et protection des personnes est signalée à la propriétaire, jamais tranchée seule.

## 3. Architecture

L'architecture cible est hybride. Elle est construite progressivement, mais **les fondations de la cible sont posées dès la phase 1**.

### 3.1 Cœur : serveur central (phases 1 et 2)
- Un serveur sur un VPS Hostinger (pas l'hébergement mutualisé), dans un centre de données de l'Union européenne situé hors de France. Hébergement et environnements : section 3.5.
- Il gère ce qui nécessite une autorité : données vérifiées, annuaires, archives, modération, recherche, comptes, urnes de vote anonymes (section 3.7), relais de messagerie (section 3.4).
- Les applications sont **offline-first** : copie locale des contenus consultés et des données essentielles (fiches pratiques, carte, agenda), consultables sans réseau, synchronisées dès qu'une connexion est disponible. Une action faite hors ligne (publication, vote) attend sur l'appareil et part au retour du réseau (S-27, D-020, D-021).

### 3.2 Fondations obligatoires dès la phase 1
- **Signature des contenus** : chaque contenu publié est un événement au format Nostr NIP-01, identifié par l'empreinte SHA-256 de sa forme canonique et signé par une signature Schnorr BIP-340 sur la courbe secp256k1 (D-018), pour pouvoir publier plus tard sur Nostr sans tout re-signer. Toute mise en œuvre refuse un événement dont l'identifiant ou la signature ne correspond pas.
- **Clés de PPF séparées par usage** : clé de la plateforme, clés de l'équipe éditoriale (D-022), clé d'import des données ouvertes (D-064), clé de la boîte de l'équipe pour les demandes de droit de réponse (D-056), graines des urnes de vote (D-110), clé « audit » gardée hors ligne par la propriétaire (conception de « une personne, une voix », approuvée en D-110). Une clé n'est jamais réutilisée d'un usage à l'autre. La clé de la plateforme n'est jamais stockée en clair sur le serveur principal (service de signature séparé ou clé hors ligne) ; rotation et révocation sont prévues pour chacune ; la clé des contenus d'exemple est retirée de la liste de confiance avant le lancement public (D-022).
- **Identités utilisateurs = paires de clés** générées sur l'appareil, compatibles Nostr, avec les bibliothèques `secp256k1-kmp` et `bitcoin-kmp` d'ACINQ (D-016, à confirmer). Pas de numéro de téléphone, pas d'e-mail obligatoire. Récupération par une phrase de 12 mots BIP-39, liste française par défaut, dérivation NIP-06, et export de la clé « nsec » pour les autres applications Nostr (D-017, à confirmer ; décision en attente : « Identités, phrase de récupération et signature des contenus : choix techniques »). Clés stockées dans le coffre de l'appareil (Android Keystore, trousseau iOS, fichier protégé sur ordinateur), jamais envoyées.
- **Port de publication unique** : toute publication passe par une interface unique (`PublicationPort`, D-018). Phase 1 : publication sur le serveur (`POST /api/v1/events`, D-021), avec des règles de publication par rôle (équipe éditoriale, organisations vérifiées, autres comptes en relecture, D-039). Phase 3 : branchement de Nostr et des miroirs sans modifier le reste.
- **Identifiants de contenu stables** (`type:clé:identifiant`, D-018) et **export JSON ouvert**, documenté par un schéma versionné dans `docs/FORMATS.md` ; toute évolution incompatible donne une nouvelle version, les anciens fichiers restent lisibles (S-48).
- **Base locale** SQLDelight sur l'appareil (D-020), avec les mêmes règles de remplacement des versions que le serveur.
- **Archivage horodaté des engagements publics** (D-111) : empreinte SHA-256 et double preuve d'existence à une date, pour qu'aucun engagement ne puisse être nié ou effacé :
  - preuve principale **OpenTimestamps**, avec un petit client écrit par nous en Kotlin commun, au moins deux calendriers indépendants par empreinte, mise à niveau de la preuve une fois ancrée dans Bitcoin, format `.ots` standard vérifiable par le client officiel ;
  - preuve immédiate en complément, **horodatage RFC 3161** côté serveur seulement, auprès d'une autorité gratuite dont les conditions permettent cet usage, sans compte ni dépense ; à défaut, OpenTimestamps seul ;
  - seul le serveur contacte ces services et seule une empreinte sort, jamais un contenu ni une donnée d'utilisateur ;
  - premiers usages : le texte du Pacte démocratique et chaque réponse de candidat (D-060), puis les copies archivées de « Voir » (M4) et les empreintes des témoignages (M5).
- **Vote anonyme « une personne, une voix »** : socle commun de tous les votes et compteurs de participation (section 3.7).
- **Vérification de l'authenticité** de tout contenu PPF, dans l'application et sur le site, même hors ligne (section 5.12).

### 3.3 Couches de résilience (phase 3)
- **Module Nostr** : copie automatique des contenus validés vers plusieurs relais Nostr publics et un relais propre au projet. L'application sait lire depuis Nostr si le serveur est inaccessible.
- **Miroirs statiques** : export régulier du site en pages statiques, déployable n'importe où (le rendu en Kotlin du site le permet, D-007).
- **Accès Tor** : adresse .onion pour le site et l'API.
- **Adresses de secours** : si l'adresse principale est bloquée, bascule automatique vers les miroirs, puis vers Tor et Nostr (S-47).
- **Partage sans Internet** : l'application elle-même et les fiches essentielles se transmettent d'un téléphone à l'autre (S-97) ; les contenus signés se partagent hors ligne par code QR ou par fichier et sont vérifiés à la réception (ajout du chef d'équipe, 7 novembre 2026, plus tard).

### 3.4 Messagerie
#### 3.4.1 Par Internet (phase 1)
- **Protocole** (décision de la propriétaire du 7 novembre 2026, D-027) : conversations privées et de petit groupe en **NIP-17** (message chiffré par NIP-44, puis double enveloppe NIP-59 qui cache l'expéditeur, le destinataire et l'heure) ; salons des collectifs, syndicats et associations en **NIP-29** (groupes gérés par le relais, avec rôles d'administrateurs) ; notre serveur joue le rôle de relais. Le chiffrement de groupe **MLS** reste la cible ultérieure : la couche de messagerie est écrite derrière une interface, pour changer de protocole sans toucher aux écrans.
- **Règles communes** : clés sur l'appareil ; aucune liste de conversations ni de contacts sur le serveur ; vérification des contacts par code QR (S-128) ; signalement d'un message chiffré par son seul destinataire (S-127) ; accusés de lecture et « en train d'écrire » désactivés par défaut et jamais envoyés (S-125, D-032) ; l'IA ne lit jamais les échanges chiffrés ; relevé des messages seulement écran ouvert, jamais en arrière-plan en attendant les notifications (D-032).
- **Limites connues et dites à l'utilisateur** : pas de confidentialité persistante (une clé volée déchiffre les anciens messages) ; les salons publics ne sont pas chiffrés de bout en bout et le pseudonyme du créateur reste attaché au salon (D-032, modèle de menaces).
- **Conservation sur le serveur** (décision de la propriétaire, D-028) : trois mois au minimum (90 jours), durée réglable par chaque utilisateur pour sa propre boîte jusqu'à un an, par un réglage signé (NIP-78) ; mise en garde avant d'allonger (D-032) ; au plus 5000 enveloppes par boîte, contre l'inondation.
- **Salons** (D-028, D-032) : création ouverte à tout compte, avec l'acceptation de la charte et un territoire ; le serveur refuse les doublons (même nom et même territoire) et propose de rejoindre le salon existant ; création possible seulement serveur joignable.
- **Collectifs** : un collectif (page, rôles, membres, S-129 à S-136) est un objet distinct d'un salon. Sa création est réservée aux comptes vérifiés (point d'attention n° 28 de l'inventaire, décision de la propriétaire du 5 novembre 2026). Les niveaux de compte étant désormais « non vérifié, vérifié, élu » (D-112), le niveau qui correspond au « compte certifié » de cette décision reste à confirmer par la propriétaire (question nouvelle, ajout du chef d'équipe, 7 novembre 2026).
- **Graphe social** : relations, statuts et cercles restent sur les appareils, avec une sauvegarde chiffrée facultative (S-112) ; les listes de membres des collectifs sont chiffrées sur le serveur (décision en attente : choix technique précis, point d'attention n° 26).

#### 3.4.2 Sans Internet (phase 4)
- Module indépendant, sans aucun lien avec le serveur.
- Basé sur le protocole bitchat (Bluetooth Low Energy en réseau maillé, chiffrement Noise, Nostr en relais Internet), compatible avec les applications bitchat existantes.
- Réutilisation du code bitchat Android (licence MIT) et iOS (domaine public) en conservant les mentions de licence.

### 3.5 Hébergement et environnements
- **Prestataire unique : Hostinger** (société établie en Lituanie) pour les serveurs et les noms de domaine (D-002). Seule exception : les sauvegardes chiffrées, stockées chez un autre prestataire européen (section 5.10).
- **Centre de données dans l'Union européenne, hors de France** (Lituanie, Allemagne ou Pays-Bas), pour rester dans le cadre du RGPD tout en imposant aux autorités les procédures de coopération entre États pour toute saisie physique. Cette localisation est un frein limité : la protection principale contre la censure reste l'architecture de résilience (section 3.3) et la minimisation des données (section 5.1).
- **Un seul serveur au départ** (VPS KVM 1 : 1 vCPU, 4 Go de mémoire, 50 Go de disque), loué au moment du premier déploiement, pas avant. Pendant le développement, il ne sert que de préproduction. Au lancement public, il accueille la production et la préproduction. L'infrastructure tient dans 4 Go de mémoire, préproduction comprise. (Décision en attente : « Serveur de la préproduction : achat », où la recommandation révisée est le KVM 2, pour un pic d'utilisateurs après une diffusion sur les réseaux sociaux.)
- **Environnements séparés autant que possible** sur ce serveur : projets Docker Compose, conteneurs, bases de données, réseaux, volumes, utilisateurs système et secrets distincts pour la production et la préproduction ; ressources limitées pour la préproduction, pour qu'elle ne puisse jamais ralentir ou bloquer la production ; aucune ressource partagée, sauf le reverse proxy (D-073).
- **Préproduction fermée au public** : adresse `preprod.politisation-populaire-francaise.org` avec un vrai certificat, qui ferme sans répondre toute connexion ne venant pas du tunnel chiffré WireGuard installé sur les appareils de l'équipe (D-071). Mot de passe sur les pages web, tunnel seul pour l'API des applications (D-072 ; la propriétaire a fourni le mot de passe le 7 novembre 2026, il sera saisi par elle au déploiement, jamais écrit dans le dépôt). Elle travaille avec la **vraie information publique** plutôt qu'avec du fictif (décision de la propriétaire du 7 novembre 2026 : « on reste dans la vraie info meme en preprod pour ne pas faire travailler les équipes pour rien, on vise l'efficacité ») : sources officielles des données ouvertes, de l'annuaire des élus et des statistiques ; **jamais de données personnelles réelles de citoyens** (comptes, messages, témoignages, votes) ; le dépôt et ses tests gardent des jeux fictifs pour les personnes. Le serveur refuse de démarrer en production avec les contenus d'exemple (D-074).
- **Durcissement décrit en code** (D-073) : Ubuntu 24.04 LTS, mises à jour de sécurité automatiques, connexion SSH par clé seulement, pas de connexion root, pare-feu sans journalisation, journal du système en mémoire et oublié après un jour, Docker durci.
- **Chaîne de déploiement** (D-070) : le serveur ne compile jamais rien ; l'intégration continue construit l'image du serveur sur la branche principale et la signe sans clé stockée (Sigstore) ; le serveur vérifie la signature avant de lancer et déploie toujours une empreinte précise, jamais un nom de version ; la préproduction se met à jour seule et revient à la version précédente si la nouvelle n'est pas saine ; la production ne reçoit qu'une empreinte déjà validée en préproduction, par une commande manuelle, après une sauvegarde de la base.
- **Conteneurs à part** pour ce qui manipule une clé ou une source extérieure : l'importeur des données ouvertes tourne dans son propre conteneur, avec son propre utilisateur, seul à voir sa clé (D-064).
- **Tuiles de carte** : un fichier de la France entière servi par le reverse proxy, 10 Go au plus sur le disque (D-080).
- **Deuxième serveur dédié à la production** dès que le budget le permet, pour un isolement complet ; l'infrastructure est conçue pour que ce passage soit simple.
- **Circuit de mise en ligne** : tests automatiques, déploiement en préproduction, validation par la propriétaire, puis production. Jamais de déploiement direct en production.

### 3.6 Formats et protocoles ouverts
Toutes les données que PPF publie ou échange suivent des formats ouverts et documentés dans `docs/FORMATS.md`, pour que n'importe qui puisse les lire, les vérifier et les réutiliser.

| Format | Usage dans PPF |
|---|---|
| NIP-01 | Événement signé : format de tout contenu publié et de l'export JSON (D-018) |
| NIP-19 | Clés lisibles « npub » (publique) et « nsec » (secrète) |
| NIP-06 | Dérivation de la clé à partir de la phrase de récupération (D-017) |
| NIP-23 | Contenus longs : fiches pratiques, parcours, dossiers, décryptages, histoire des luttes, glossaire, briques de « Voir », revendications, appels, fiches d'organisation, pétitions, Pacte et réponses, avec des étiquettes structurées `ppf-…` (D-022, D-033, D-034, D-038, D-039, D-048, D-059, D-060) |
| NIP-52 | Mobilisations de l'agenda (événements de calendrier) et réponses publiques des organisations (D-039, D-045) |
| NIP-17, NIP-44, NIP-59 | Messages privés chiffrés et leurs enveloppes (D-027) |
| NIP-29 | Salons des collectifs gérés par le relais (D-027) |
| NIP-78 | Réglages signés par l'utilisateur (conservation des messages, D-028) et index signé des données ouvertes (D-064) |
| NIP-13 | Preuve de travail contre les robots (D-110) |
| NIP-98 | Authentification d'une requête par une signature, sans mot de passe (D-110) |
| NIP-56 | Signalements envoyés à la modération, jamais publiés (contrat du back-office) |
| NIP-09 | Demande de suppression d'un contenu par son auteur |
| iCalendar (RFC 5545) | Export d'une mobilisation et flux d'agenda (D-040, D-043) |
| PMTiles | Fonds de carte OpenStreetMap auto-hébergés (D-046, D-080) |
| OpenTimestamps (`.ots`), RFC 3161 | Preuves d'horodatage (D-111) |
| VOPRF (RFC 9497) | Jetons de vote anonymes (D-110) |

### 3.7 Vote anonyme « une personne, une voix »
Méthode approuvée par la propriétaire (D-044), demandée pour la délibération citoyenne (D-062), précisée par ses réponses du 7 novembre 2026 (D-112) ; conception technique approuvée par le chef d'équipe (D-110 ; détail dans `docs/integration/une-personne-une-voix.md`).
- **Usages** : votes du mur des revendications (M11) ; délibération citoyenne sur les mesures du Pacte démocratique et les modalités des campagnes (proposer, amender, voter), car leur liste de départ n'est qu'une proposition que « c'est le peuple qui doit la modifier et la valider via notre appli » (D-062) ; votations et assemblées citoyennes (M13.6) ; compteurs de participation d'« Agir » (D-044) ; signalements chiffrés des citoyens dans « Voir » (VO-03) ; décisions internes d'un collectif (AG-51).
- **Garanties** : le serveur ne peut pas relier un vote à un compte, même s'il le voulait (impossibilité mathématique, pas seulement une règle) ; ni téléphone, ni e-mail, ni pièce d'identité, ni adresse IP ; vote modifiable jusqu'à la clôture (D-112) ; vote possible hors ligne, envoyé au retour du réseau ; recomptage possible par n'importe qui après la clôture ; fonctionnement sur le serveur KVM 1.
- **Trois niveaux de votants** (décision de la propriétaire, D-112) : **non vérifié** (inscription avec une preuve de travail) ; **vérifié** (validation en personne par un bénévole désigné par une organisation vérifiée de l'annuaire et agréé par PPF, sans noter aucune identité ni aucun lieu) ; **élu** (compte d'élu certifié en personne, mandat contrôlé dans l'annuaire officiel des élus, D-047). Pas de parrainage. Les organisations ne votent pas. Le niveau d'un compte est figé la semaine d'ouverture de chaque vote.
- **Résultats** (D-112) : le vote des citoyens, avec les comptes vérifiés en premier, puis les comptes non vérifiés, puis le total des citoyens ; à part, le vote des élus, jamais additionné à celui des citoyens.
- **Mécanisme** (D-110) : jetons anonymes VOPRF (RFC 9497, mode 0x01, suite ristretto255-SHA512), une clé par urne et par niveau, une preuve par jeton que le serveur a utilisé la clé publiée ; chaque urne est un contenu signé, le même pour tous ; l'application demande un jeton pour chaque urne ouverte, qu'elle vote ou non ; le bulletin part par une connexion séparée, sans aucune authentification, après un délai aléatoire appliqué automatiquement à tous les votants (D-112) : c'est une protection commune du système de vote, comme le fait que le serveur ne relie jamais un vote à un compte, et non un réglage de confidentialité (principe 10) ; heure des bulletins arrondie à l'heure ; à la clôture, publication des clés secrètes de l'urne et de la liste anonyme des bulletins, pour le recomptage par tous (D-112) ; journal public des jetons délivrés par urne et par niveau.
- **Exigences de réalisation** (D-110) : bibliothèque du groupe ristretto255 (`multiplatform-crypto-libsodium-bindings`) derrière une interface à nous, avec ses conditions (vecteurs officiels de la RFC, autotest au premier usage qui empêche de voter s'il échoue, application de bureau empaquetée vérifiée, taille de l'APK mesurée, iOS) et un repli sur les signatures RSA aveugles (RFC 9474) ; graine des clés dans un fichier secret lisible seulement par le serveur, jamais en variable d'environnement ni en base, incluse dans la sauvegarde chiffrée, puis confiée au service de signature séparé ; aucune corrélation possible par la base de données ; relecture indépendante du code de l'urne avant sa fusion ; seuils anti-fraude dans la configuration privée, méthode et code publics.
- **Détection des manipulations** (S-90) : rafales de votes, comptes créés en série, quotas des validateurs ; gel provisoire d'un résultat suspect et examen par l'équipe ; sans adresse IP.
- **Limites dites publiquement** : un attaquant qui prendrait le contrôle du serveur en direct pourrait observer le réseau (parade complète avec Tor et l'adresse .onion, phase 3) ; après la clôture, la liste publique des bulletins permet à un votant de prouver son vote s'il le veut (choix accepté par la propriétaire, D-112) ; les comptes non vérifiés restent faciles à multiplier, d'où leur colonne séparée.

### 3.8 Données publiques importées et données ouvertes publiées
- **Règles communes des imports** : uniquement des sources officielles ouvertes, avec leur licence et leur date de mise à jour affichées ; import par le serveur ou par un outil séparé, jamais par l'appareil ; seuls les champs nécessaires sont gardés (jamais date de naissance, sexe, profession, adresse, photo) ; un import est refusé si le nombre d'éléments baisse de plus de 20 % (fichier tronqué) et le dernier import réussi reste servi ; jeux fictifs au format officiel en dehors de la production (D-047, D-064).
- **Annuaire officiel des élus** (décision de la propriétaire, D-047) : Répertoire national des élus importé sur le serveur et servi par département ; la commune choisie par l'utilisateur reste sur l'appareil, jamais envoyée ; annuaires officiels proposés tant que le serveur ne répond pas.
- **Données du Parlement** (D-064, avancées de la phase 5 pour la législature en cours ; validation de la propriétaire relayée le 7 novembre 2026 : « Oui, commencez ») : députés, sénateurs, eurodéputés élus en France, scrutins et votes nominatifs, liens vers les déclarations de la HATVP ; index signé par une clé d'import dédiée, que le serveur principal ne détient pas ; jamais de déclaration de patrimoine (publication interdite). Les affaires, promesses et déclarations restent saisies et relues à la main.
- **Compteurs des pétitions officielles** relevés automatiquement par le serveur à partir des données ouvertes de data.gouv.fr ; l'application ne contacte jamais ces sites (décision de la propriétaire, D-062).
- **Textes de loi** pour la vérification « loi déjà en vigueur » des revendications proposées : interface unique côté serveur ; d'abord sans recherche (toute référence citée de mémoire reste « à vérifier »), puis un index des données ouvertes de Légifrance (base LEGI) sur notre serveur (D-030, à confirmer).
- **Référentiel des territoires** (codes, noms, population, électeurs inscrits), module partagé qui sert aussi aux objectifs de signatures par territoire (D-062).
- **Fonds de carte** OpenStreetMap auto-hébergés, avec la mention « © contributeurs d'OpenStreetMap » toujours visible (licence ODbL) (D-046, D-080).
- **Nouvelles sources** (ajouts du chef d'équipe, 7 novembre 2026) : ordre du jour, textes en discussion et scrutins à venir de l'Assemblée et du Sénat, pour « Cette semaine au Parlement » ; budgets et comptes des collectivités (données ouvertes de la DGFiP), données essentielles de la commande publique et subventions publiées, pour l'argent public local ; consultations publiques officielles (ministères, Commission nationale du débat public, enquêtes publiques, collectivités), sources à vérifier ; dates des scrutins et des inscriptions sur les listes électorales pour le calendrier électoral personnel. Chaque source est vérifiée (licence, maintenance) et son hôte inscrit dans la liste des sorties (section 5.8) avant tout code.
- **Données ouvertes publiées par PPF** : export JSON de tous les contenus publiés (S-48) ; API publique en lecture seule, sans compte, documentée ; flux RSS (S-51) et iCal (D-043) ; jeux de données et bouton « Citer ce contenu » avec une référence stable, pour les journalistes, chercheurs et observateurs (ajout du chef d'équipe, 7 novembre 2026). La licence des contenus et des données publiés par PPF est à proposer à la propriétaire dans le brainstorming avant la première publication réelle (ajout du chef d'équipe, 7 novembre 2026).

## 4. Technologies retenues

Si une meilleure option existe au moment du développement, elle est proposée avec justification avant de démarrer. Chaque dépendance est vérifiée (version, maintenance, licence compatible avec l'AGPL, documentation officielle) et justifiée.

- **Chaîne d'outils** (D-009) : Kotlin 2.4.21, Compose Multiplatform 1.12.2, plugin Android Gradle 9.3.x, Gradle 9.7.1, JDK 21, Ktor 3.6.1, kotlinx.html 0.12.0, kotlinx.serialization 1.12.0 ; Android compileSdk 36, minSdk 26 ; versions verrouillées (catalogue de versions, fichiers de verrouillage).
- **Structure du dépôt** (D-008) : `shared/core` (logique sans interface, aussi utilisée par le serveur), `shared/ui` (interface Compose), `android`, `desktop`, `ios` (non compilé), `server`, `web`, `infra`, `tools`, `content`, `docs`.
- **Applications** : Kotlin Multiplatform et Compose Multiplatform, logique métier partagée ; base locale SQLDelight (D-020) ; client HTTP Ktor (D-021) ; cryptographie ACINQ (D-016, à confirmer) ; carte MapLibre Compose (D-046) ; tout ce qui est propre à une plateforme passe par `expect`/`actual` ou par une interface, pour rester compatible iOS. Ce choix permet aussi de réutiliser le code Kotlin de bitchat Android.
- **Site web** : Ktor et kotlinx.html, module `web` monté par le serveur (D-007) ; pages lisibles sans JavaScript ; graphiques dessinés par le serveur en SVG avec leur tableau de données (D-055) ; politique de sécurité du contenu stricte, sans `unsafe-inline`.
- **Serveur** : Kotlin et Ktor (modèles partagés avec les applications), PostgreSQL et PostGIS, accès par JDBC, HikariCP et migrations Flyway (D-019), recherche plein texte PostgreSQL (moteur dédié plus tard si besoin) ; BouncyCastle côté serveur seulement, pour l'horodatage RFC 3161 (D-112).
- **Infrastructure** : Docker Compose, Caddy (HTTPS automatique), WireGuard, Ubuntu 24.04 LTS, images signées (D-070, D-073) ; infrastructure décrite en code pour pouvoir tout redéployer chez un autre hébergeur en quelques heures.
- **Cartes** : OpenStreetMap via MapLibre, jamais Google Maps ni aucun serveur de tuiles tiers ; fond de carte intégré à l'application et disponible hors ligne ; fond détaillé de la France entière, à la même précision partout, servi par notre serveur (D-046, D-080).
- **Vidéos** : pas de diffusion directe depuis le VPS ; instance PeerTube (propre ou partenaire). Podcasts via flux RSS.
- **Notifications** : UnifiedPush avec un serveur ntfy auto-hébergé sur Android (pas de Firebase). Sur iOS, APNs : aucun contenu sensible, seulement un signal de réveil.
- **Anti-spam** : limitation de débit (60 publications par minute et par compte, 600 pour tout le serveur, D-039) et preuve de travail auto-hébergée (NIP-13 dans les applications, ALTCHA sur le site, D-110). Jamais de reCAPTCHA.
- **IA** : connecteur au format standard des modèles de conversation, éteint tant qu'aucun modèle n'est configuré (D-030) ; décision en attente : « Modération et assistant IA : hébergement du modèle » (section 5.6).
- **Identité visuelle** (D-025) : jetons de `PpfTheme` (`PpfPalette`), police Atkinson Hyperlegible (licence SIL Open Font License), icônes Lucide (licence ISC) converties en vecteurs Kotlin, composants communs ; tout est auto-hébergé.
- **Données publiques** : section 3.8.
- **Intégration continue** (D-013, D-031, D-070) : GitHub Actions sur Linux et Windows, sans machine macOS pour l'instant ; compilation de l'APK et des applications de bureau, tests, détection des secrets (gitleaks), vérification des tiers et des dépendances interdites, intégrité du wrapper Gradle, vérification du contenu de l'APK (non débogable, certificat attendu), vérification des scripts et de la configuration de l'infrastructure, construction et signature de l'image du serveur, tests d'accessibilité automatisés (section 6) ; actions épinglées sur un commit précis ; Dependabot ; dans les quotas gratuits (cache des builds, pas de tâches inutiles). La compilation iOS sera ajoutée avec le compte Apple Developer.

### 4.1 Distribution des applications
- **Versions de test Android** : version d'essai non débogable, seule envoyée sur les téléphones, avec une signature de test partagée vérifiée par l'intégration continue (D-012, D-031). La publication utilisera une clé distincte, gardée hors ligne (D-012).
- **Versions publiques Android** : un APK par architecture de processeur (arm64-v8a, armeabi-v7a, x86_64 ; environ 26 Mo au lieu de 38 Mo pour un APK universel, D-016, D-046) pour le site, les publications du dépôt et F-Droid ; un paquet AAB pour Google Play, qui l'exige (ajout du chef d'équipe, 7 novembre 2026).
- **F-Droid** : retenu par la propriétaire (point d'attention n° 5 de l'inventaire, 4 novembre 2026), comme garantie d'accès si Google ou Apple retirent l'application.
- **Constructions reproductibles** : les mêmes sources donnent, octet pour octet, les mêmes APK et le même fichier du serveur (jar) ; l'intégration continue le vérifie par une seconde construction ; c'est la condition pour que F-Droid publie l'application avec notre signature et pour qu'un tiers puisse vérifier que le code publié est bien celui qui tourne (ajout du chef d'équipe, 7 novembre 2026).
- **Google Play** : test interne puis publication (décision en attente : « Distribution des versions de test sur Android (avertissements Play Protect et Samsung) », 25 dollars une seule fois) ; le même compte servira à l'enregistrement des développeurs que Google exige en 2027 pour toute application installée sur un téléphone Android certifié, y compris hors magasin.
- **Empreinte du certificat de signature** publiée sur le site et dans le dépôt, pour vérifier un APK reçu d'une autre source ; partage de l'APK hors ligne entre téléphones (S-97).
- **Bande passante** : l'APK n'est jamais servi par le serveur principal (publications du dépôt, F-Droid, Google Play, miroirs), pour que les téléchargements ne saturent pas le serveur (ajout du chef d'équipe, 7 novembre 2026).
- **Bureau** : applications et installateur Windows dans les publications du dépôt.
- **iOS** : TestFlight puis App Store dès que le compte Apple Developer est ouvert ; d'ici là, le site installable (PWA).

## 5. Sécurité et vie privée

### 5.1 Données
- Lecture sans compte. Compte (pseudonyme, par paire de clés) seulement pour contribuer, voter, soutenir, signaler.
- Votes et soutiens enregistrés de manière à ne pas pouvoir être reliés à une personne réelle (section 3.7) ; aucun historique individuel de votes exposé ; publication de résultats agrégés uniquement.
- **Restent sur l'appareil**, jamais envoyés : la progression dans les parcours (D-033), les vues du mur des revendications (D-029), les comptes et organisations suivis (S-120, D-042), les contenus suivis (D-034), la commune choisie pour « Mes élus » (D-047), les réponses « J'y serai », « Je soutiens », « J'ai signé » (D-039 à D-041, effacées peu après l'événement), les filtres et recherches, les marque-pages et l'historique (S-07, S-08), les rappels (S-98) et le tableau de bord de « Mon espace ». Une sauvegarde chiffrée de bout en bout reste facultative (S-112). Tant que le comptage anonyme n'est pas en service, aucun compteur public ni liste de participants n'existe.
- **Confidentialité maximale par défaut et libre choix** (principe 10) : tous les réglages de confidentialité et d'anonymat sont au plus haut à la création du compte. Tout assouplissement est fait par l'utilisateur lui-même, pour ses propres données, après une mise en garde simple sur les risques ; il peut revenir en arrière à tout moment, la mise en garde rappelant qu'une information déjà vue ou copiée par d'autres ne peut pas être effacée partout. Un écran montre ce qui est stocké sur l'appareil et ce qui est envoyé au serveur, avec un bouton « tout effacer » (S-21).
- **Suppression systématique des métadonnées** (GPS, modèle d'appareil, date EXIF, etc.) de toutes les photos et vidéos, **sur l'appareil, avant l'envoi**. Outil de floutage des visages optionnel avant publication (pas de floutage par défaut).
- Valeur de preuve : empreinte SHA-256 du fichier original, horodatée (section 3.2, D-111).
- Témoignages en attente de modération et signalements chiffrés avec la clé publique de l'équipe de modération ; demandes de droit de réponse et signalements d'erreur envoyés par message chiffré à la boîte de l'équipe (D-056).
- Pas de journalisation des adresses IP, ni dans le reverse proxy, ni dans l'application, ni dans le système (D-015, D-073). Si un journal anti-abus est indispensable : tronqué, haché, effacé sous 24 heures maximum. (Voir le point juridique de la section 9.2 sur la conservation des données d'identification.)
- Registre des données (RGPD) listant exactement ce qui est stocké, pourquoi et combien de temps ; toute nouvelle donnée y est justifiée avant d'être stockée.

### 5.2 Protection des adresses IP
- Adresse .onion (phase 3).
- Option Tor intégrée dans l'application (Arti ou bibliothèque équivalente maintenue) ou compatibilité avec Orbot.
- Guide dans l'application expliquant Tor et recommandant un VPN fiable (Proton VPN a une offre gratuite), en signalant que beaucoup de VPN gratuits revendent les données.
- Avant d'ouvrir un site extérieur, l'application prévient que ce site verra l'adresse IP du lecteur (« Vous quittez PPF », avec « Rester sur PPF » proposé en premier, D-022, D-034).

### 5.3 Serveur et équipe
- HTTPS partout, HSTS, en-têtes de sécurité stricts posés par l'application (CSP sans `unsafe-inline`, etc.) (D-015).
- Double authentification obligatoire pour tous les comptes de l'équipe ; le contrat du back-office retient la clé d'accès (passkey) ou la clé de sécurité physique, plus sûres qu'un code à usage unique (D-120, en cours). Deux super-administrateurs au minimum, chacun avec une clé de sécurité physique (point d'attention n° 8 ; achat à soumettre à la propriétaire).
- Rôles et permissions minimales, accès temporaires, validation à deux pour les actions sensibles, coupure d'urgence d'un compte compromis, journal d'audit de toutes les actions de modération et d'administration, que personne ne peut modifier ni effacer (S-56 à S-63).
- Compte de l'hébergeur (qui gère aussi les noms de domaine) protégé par une double authentification forte (clé de sécurité physique si Hostinger l'accepte) ; domaines verrouillés contre les transferts, renouvellement automatique activé, DNSSEC si disponible.
- Compte GitHub de la propriétaire avec double authentification (à activer).
- Aucune mise en production sans passage par la préproduction et validation de la propriétaire (section 3.5).
- Sauvegardes chiffrées hors site et plan de reprise : section 5.10.

### 5.4 Code
- Aucun secret dans le dépôt (fichiers `.env`, keystores et clés exclus ; détection automatique des secrets avant chaque commit et en intégration continue).
- Dépendances verrouillées, surveillées automatiquement (Dependabot), le moins de dépendances possible, chacune justifiée dans la demande de fusion.
- Commits signés par une clé SSH, auteur « PPF » avec l'adresse « noreply » de GitHub, jamais l'adresse personnelle de la propriétaire (D-014).
- Aucune cryptographie maison quand une bibliothèque éprouvée existe ; vecteurs de test officiels exécutés à chaque compilation (D-016, D-110) ; relecture indépendante du code de vote et de signature avant sa fusion (D-110).
- Référentiels : OWASP MASVS pour les applications mobiles, OWASP ASVS pour le serveur et le site. Liste de contrôle de sécurité avant chaque version : section 5.11.

### 5.5 Modèle de menaces
Un modèle de menaces est rédigé et tenu à jour (`docs/THREAT_MODEL.md`). Menaces minimales à couvrir : réquisitions judiciaires, saisie du serveur, suspension du nom de domaine, retrait des stores, attaques DDoS, piratage, campagnes de faux comptes et de manipulation des chiffres, manipulation des votes (mur des revendications, engagements, votations), désinformation injectée, infiltration de l'équipe, contributions de code malveillantes, tentatives de faire passer la plateforme pour le soutien d'un candidat ou d'un parti afin d'exposer l'association à des poursuites (section 9.2). S'y ajoutent (ajout du chef d'équipe, 7 novembre 2026) : faux contenus, fausses captures, faux sites ou fausses applications attribués à PPF ; vol ou abus d'une clé de signature (plateforme, équipe éditoriale, import, urnes) ; validateur complice qui valide de faux comptes ; ciblage et harcèlement des militants et des membres de l'équipe ; compromission d'une source de données importée.

### 5.6 Modération assistée par IA
- Décision de la propriétaire (5 novembre 2026) : une IA analyse les contenus publics juste après leur mise en ligne, sans retarder la publication.
  - **Alerte rouge** : un contenu estimé grave et manifestement illicite (appel à la violence, menaces, contenu pédocriminel ou terroriste, divulgation d'informations personnelles) est masqué immédiatement, en attendant la décision de l'équipe, prévenue par une notification du plus haut niveau d'importance.
  - **Alerte orange** : un contenu limite (risque de diffamation ou de harcèlement, doute sur la charte) reste publié ; l'équipe est prévenue.
  - Tous les autres contenus sont publiés directement, sans notification à l'équipe.
  - La décision finale reste toujours humaine : l'équipe confirme ou annule chaque masquage ; l'auteur est informé du motif et du recours à un outil automatisé, et peut contester (section 9.4).
  - Tout utilisateur peut à tout moment signaler manuellement un contenu ou un autre utilisateur.
  - L'IA ne lit jamais les échanges chiffrés.
- Relecture humaine avant publication conservée pour les témoignages, les appels à témoins et les revendications (point d'attention n° 33, décision du 5 novembre 2026).
- L'IA aide aussi à vérifier les revendications proposées (doublons dits autrement, loi déjà en vigueur, revendication contraire, charte) : elle conseille l'auteur et prépare la relecture, sans jamais publier ni refuser ; le texte lui est transmis comme une donnée encadrée, avec l'ordre de ne suivre aucune instruction qu'il contiendrait ; sa réponse est lue strictement (D-030).
- Le taux d'erreur est suivi, pour repérer les biais, notamment politiques (point d'attention n° 33). Les rapports d'analyse rédigés par une IA ne partent que de statistiques agrégées (S-114).
- Tout contenu produit avec l'aide d'une IA affiche son origine (D-022). L'assistant citoyen et ses crédits sont mis de côté : rien n'est codé à leur sujet.
- Envoyer les contenus des utilisateurs à une API d'IA externe crée un risque pour la vie privée : privilégier un modèle auto-hébergé, ou limiter l'usage d'une API externe aux contenus déjà publics. Choix soumis à la propriétaire (décision en attente : « Modération et assistant IA : hébergement du modèle », avec, si un prestataire est retenu, l'extension de cette section aux contenus envoyés pour publication, sans pseudonyme, sans adresse IP, sans conservation chez le prestataire).

### 5.7 Signalement des failles et transparence sur la sécurité
- Page publique de divulgation responsable (S-92) : comment signaler une faille, engagement de réponse et de correction, remerciements publics ; délais publiés avant l'ouverture publique.
- Fichier `/.well-known/security.txt` (RFC 9116) sur le site et sur chaque domaine, avec au moins le moyen de contact, la date d'expiration du fichier, le lien vers la politique et les langues acceptées (français, anglais) ; fichier `SECURITY.md` dans le dépôt ; signalement privé des failles par le dépôt GitHub (gratuit) (ajout du chef d'équipe, 7 novembre 2026).
- Le moyen de contact est chiffré ou chiffrable (par exemple un message chiffré vers une clé publiée) ; une adresse électronique dédiée, si elle a un coût, est soumise à la propriétaire (ajout du chef d'équipe, 7 novembre 2026).
- Engagement de ne pas poursuivre les chercheurs de bonne foi : à faire relire par un avocat (section 9.8).
- Publication des audits de sécurité et des incidents, avec ce qui a été corrigé (S-93).

### 5.8 Connexions sortantes autorisées
(Ajout du chef d'équipe, 7 novembre 2026, à partir de la liste tenue par le Directeur technique.)
- **Applications** : elles ne contactent que les serveurs de PPF (et plus tard ses miroirs, relais et adresse .onion). Elles n'interrogent jamais un site tiers, même officiel (pétitions, annuaires, données publiques) : c'est le serveur qui s'en charge. Un lien extérieur n'est ouvert que par l'utilisateur, dans son navigateur, après « Vous quittez PPF ». L'intégration continue vérifie l'absence de tiers dans le code (`tools/check-no-trackers.sh`).
- **Serveur** : il ne contacte que les hôtes inscrits dans `infra/EGRESS.md`, chacun avec sa raison, son port, ses environnements et le pôle qui en répond. Aucune donnée personnelle ne sort vers ces hôtes. Chaque nouvel hôte est inscrit avant le code, ainsi que dans `docs/THREAT_MODEL.md` et `docs/REGISTRE_DONNEES.md`. Aujourd'hui vérifiée en relecture, la liste sera appliquée par le réseau avant la production (proxy de sortie filtrant, décision technique à venir du Directeur technique, bloc D-075 à D-079).
- **Sources réelles en production seulement** : en développement, en test et en préproduction, le serveur sert des jeux fictifs et ne contacte aucune source officielle (D-047, D-064).
- Liste actuelle, par familles : mises à jour du système et de Docker, registre des images et dépôt GitHub, vérification des signatures (Sigstore), certificats (Let's Encrypt), modèle d'IA (éteint), data.gouv.fr (pétitions, annuaire des élus), Insee et La Poste (annuaire des élus, D-047), calendriers OpenTimestamps, autorité RFC 3161 et explorateurs publics de la chaîne Bitcoin (D-111), sources du Parlement et de la HATVP (D-064), prestataire des sauvegardes hors site (à choisir).

### 5.9 Rapports de plantage sans télémétrie
- Aucun outil de suivi des plantages ni de mesure d'usage fourni par un tiers ; aucun envoi automatique (principe 6).
- Après un plantage, l'application propose, au lancement suivant, un rapport technique écrit sur l'appareil (version, système, trace de l'erreur), affiché en entier, sans contenu, sans clé, sans identifiant (S-101). L'utilisateur choisit de le partager lui-même (copier, feuille de partage, ou joindre à un ticket, S-102), ou de l'effacer ; il n'est jamais gardé longtemps (durée inscrite au registre des données) (ajout du chef d'équipe, 7 novembre 2026, pour le partage par l'utilisateur).
- La version de l'application et le système sont écrits en bas de « Mon espace », pour qu'une testeuse puisse les donner avec un problème (D-061).

### 5.10 Sauvegardes chiffrées hors site et plan de reprise
- Sauvegardes de la production (base de données et fichiers indispensables, dont les graines des urnes, D-110) chiffrées sur le serveur avant de partir, avec une clé que le prestataire de sauvegarde ne détient jamais, gardée aussi hors ligne par la propriétaire ; stockées chez un autre prestataire européen que l'hébergeur (D-002). Prestataire et coût à soumettre à la propriétaire avant la production (de l'ordre de 1 à 4 euros par mois selon l'entrée « Serveur de la préproduction : achat »).
- Fréquence et durée de conservation fixées dans le plan de reprise et inscrites au registre des données : une donnée effacée à la demande d'une personne disparaît aussi des sauvegardes au terme de leur rotation (ajout du chef d'équipe, 7 novembre 2026).
- La préproduction ne contient aucune donnée personnelle de citoyens et ses données publiques se réimportent depuis les sources officielles : elle n'a pas besoin de sauvegarde hors site.
- **Restauration testée** avant la mise en production, puis à intervalle régulier, avec un compte rendu écrit (ajout du chef d'équipe, 7 novembre 2026, pour le compte rendu).
- **Plan de reprise** écrit, dans `infra/` et dans le guide de la propriétaire : redéployer le service ailleurs en quelques heures à partir du code et des sauvegardes. Il couvre au moins : saisie ou panne du serveur, suspension du compte Hostinger, suspension d'un nom de domaine (autres extensions, miroirs), perte du compte GitHub (miroir Codeberg), vol ou fuite d'une clé (rotation, révocation, annotation publique des urnes concernées, D-110), rançongiciel (ajout du chef d'équipe, 7 novembre 2026, pour la liste des cas).

### 5.11 Liste de contrôle de sécurité avant chaque version
- Une liste de contrôle écrite, dans le dépôt, est passée avant chaque version publiée des applications et avant chaque passage en production du serveur ; son résultat (date, version, personne) est noté dans le journal des modifications.
- Elle s'appuie sur la dernière version publiée de l'OWASP ASVS pour le serveur et le site, et de l'OWASP MASVS (avec son guide de tests) pour les applications.
- Elle vérifie au moins (ajout du chef d'équipe, 7 novembre 2026, pour le contenu de la liste) : aucun secret ; aucun tiers ; dépendances à jour et sans alerte de sécurité connue ; APK non débogable, signé par le bon certificat, réduction du code vérifiée sur un vrai téléphone avant d'être activée ; en-têtes de sécurité et CSP ; aucune adresse IP dans aucun journal ; aucun exemple en production et aucune donnée réelle en préproduction (D-074) ; liste des sorties à jour (section 5.8) ; migrations de base relues ; dernière restauration de sauvegarde réussie ; tests d'accessibilité au vert ; modèle de menaces, registre des données et journal des modifications à jour.
- Un audit de sécurité externe a lieu avant le lancement public (coût reporté, section 12).

### 5.12 Authenticité des contenus et provenance des témoignages
- **« Vérifier un contenu PPF »** (S-46 ; ajout du chef d'équipe, 7 novembre 2026, pour les captures, les liens et le site) : dans l'application et sur le site, même hors ligne, n'importe qui peut vérifier qu'un contenu, une capture d'écran ou un lien vient bien de PPF et n'a pas été modifié : signature vérifiée avec les clés publiées de PPF, identifiant stable, empreinte. L'application affiche « contenu authentique PPF » ou une mise en garde claire. Les images de partage (S-37) portent une référence courte (identifiant, empreinte abrégée, code QR) qui permet cette vérification. La liste des clés de confiance est publiée dans l'application, sur le site et dans le dépôt, puis signée par la clé de la plateforme (D-022).
- **« Citer ce contenu »** : une référence stable (titre, date, adresse, identifiant, version) à copier (ajout du chef d'équipe, 7 novembre 2026).
- **Provenance des témoignages** (M5 ; ajout du chef d'équipe, 7 novembre 2026) : signature et horodatage dès la capture, sur l'appareil ; la clé qui signe est choisie pour ne pas relier le témoin à son pseudonyme (décision technique à prendre avec le module M5) ; empreinte horodatée (D-111) ; la copie publiée est sans métadonnées ; l'original est conservé chiffré pour les seuls avocats partenaires, et n'est envoyé qu'avec l'accord explicite du témoin (principe 10) ; compatibilité avec la norme C2PA à étudier, en vérifiant qu'elle n'ajoute aucune donnée qui identifie le témoin.

## 6. Accessibilité, compatibilité et performance

- **WCAG 2.2 niveau AA (RGAA)** : lecteurs d'écran, contrastes vérifiés en mode clair et sombre, tailles de texte, navigation au clavier (y compris dans le menu en accordéon), textes alternatifs, transcriptions et sous-titres des vidéos et des podcasts (S-41) ; la couleur ne porte jamais seule le sens (D-038, D-060).
- **Tests d'accessibilité automatisés** à chaque modification, dans l'intégration continue (ajout du chef d'équipe, 7 novembre 2026) : étiquettes de tous les éléments interactifs, taille minimale des zones à toucher, contrastes des jetons de couleur calculés, ordre de lecture, sur les trois formats (téléphone, tablette, ordinateur) ; pages du site contrôlées par un outil libre exécuté dans l'intégration continue, sans service tiers (outil à choisir en vérifiant sa licence et sa maintenance). Ces tests complètent, sans les remplacer, un essai manuel avec un lecteur d'écran avant chaque version.
- **Déclaration d'accessibilité** (S-42) : page publique dans l'application et sur le site, sur le modèle du RGAA : niveau atteint, défauts connus, solution de remplacement, moyen de signaler un problème d'accessibilité ; mise à jour à chaque version.
- Mode « Facile à lire et à comprendre » (S-18) ; lecture à voix haute par la synthèse vocale du téléphone (S-95) ; vidéos en langue des signes française pour les fiches essentielles (ajout du chef d'équipe, 7 novembre 2026, plus tard) ; taille du texte et réduction des animations (S-16).
- Android 8 et plus ; fonctionnement avec une connexion faible ; mode économie de données (S-19) ; aucune tâche réseau en arrière-plan sans besoin.
- Lecture du site sans JavaScript ; site installable (PWA).
- Contenus essentiels consultables hors ligne, intégrés à l'application dès l'installation (D-022).
- Interface en français, tous les textes externalisés pour la traduction (anglais et autres langues plus tard).
- Modes clair et sombre.
- **Performance** (ajout du chef d'équipe, 7 novembre 2026) : poids de l'APK surveillé par l'intégration continue ; infrastructure qui tient dans 4 Go de mémoire, préproduction comprise ; test de charge avant le lancement public ; pages publiques du site servies en fichiers statiques par le reverse proxy pour tenir un pic d'audience venu des réseaux sociaux.

## 7. Design et expérience utilisateur

- **Identité visuelle** (décision de la propriétaire, D-025) : celle de ses maquettes « interface communautaire » : bleu de marque #2B3A8F et accent carmin #C8213A sur des surfaces claires, police unique Atkinson Hyperlegible, icônes Lucide, arrondis de 6, 10 et 16 dp, cartes bordées à ombre légère, badges [EXEMPLE], encarts teintés. Toute nouvelle vue se construit avec les composants communs, sans couleur ni police écrite en dur. Moderne, épuré, « ludique mais mature », d'inspiration nationale sans jamais pouvoir être confondu avec un site de l'État ; mention « Plateforme citoyenne indépendante, sans lien avec l'État ni le gouvernement ». Modes clair et sombre.
- **Navigation** (décisions de la propriétaire, D-023, D-026, D-044) : l'écran par défaut est le fil social ; sur téléphone, une barre du bas fixe à cinq entrées : Fil, En direct, Carte, Messages, Revendications ; « Mon espace » par l'avatar de la barre du haut ; les quatre espaces Voir, Comprendre, Agir et Décider dans le menu en accordéon ; rail ou barre latérale sur les grands écrans ; vues du fil parcourues en glissant le doigt. Le bouton AIDE pourra flotter au-dessus de la barre (décision en attente : contenu du bouton AIDE et du mode manifestation, brainstorming du cahier fonctionnel, entrée « Agir : questions ouvertes après la première version »). La navigation détaillée sera réécrite lors du découpage en MVP (décision en attente : « Navigation et UX : quatre espaces, un accueil, Mon territoire »).
- Le bouton ou le geste « retour » du système ferme d'abord ce qui est ouvert, puis ramène au fil (D-035).
- **Chemin du citoyen** : Voir, Comprendre, Agir, Décider. Chaque contenu se termine par une passerelle « Et maintenant ? » qui propose l'étape suivante dans un autre espace (D-033).
- Comprendre ce qui se passe en moins de 30 secondes dès l'écran « En direct » : direct, alertes, chiffres clés, prochaines mobilisations, revendication à la une (D-037).
- Langage clair, version « Facile à lire et à comprendre » pour les fiches pratiques et les contenus essentiels ; mots difficiles expliqués partout par le glossaire, sans quitter sa lecture (D-048).
- Fiches ultra lisibles, avec des encarts « En bref », « Légal », « Illégal », « Attention », « Conseil », chacun avec une icône et un titre (D-024, D-025) ; verdicts des décryptages toujours écrits en toutes lettres avec une icône (D-038).
- Accueil des nouveaux utilisateurs en trois écrans au plus, qu'on peut passer (S-01), avec une explication simple de la sécurité (Tor, VPN, ce que l'application collecte ou non, y compris sur les votes et soutiens).
- Confidentialité visible : « Qui voit quoi » et « Mes votes et soutiens : jamais visibles » sur le profil (D-025).
- Aucune interface trompeuse ni manipulatrice (règlement sur les services numériques, article 25) ; un fil chronologique sans profilage reste toujours disponible (article 27, M18) (ajout du chef d'équipe, 7 novembre 2026, pour l'article 25).
- Maquettes ou captures présentées à la propriétaire pour validation avant de finaliser chaque écran important ; les captures se produisent sans appareil, pour le téléphone (clair et sombre) et l'ordinateur.
- Interdiction d'imiter l'identité visuelle officielle de l'État (logo Marianne officiel, bloc-marque) et de réutiliser l'ancien nom du projet, abandonné pour des raisons juridiques.

## 8. Open source et protection du dépôt

- **Licence** (D-006) : AGPL-3.0-or-later, avec une permission supplémentaire pour les magasins d'applications (`LICENSE-ADDITIONAL-PERMISSIONS.md`) et l'accord des contributeurs (`CONTRIBUTING.md` : certificat d'origine du développeur et clause de permission). Compatible avec les licences de bitchat (MIT, domaine public), dont les mentions sont conservées. Toute bibliothèque ajoutée doit être compatible avec l'AGPL. Licences des polices et icônes jointes dans `licenses/`. Relecture par un avocat avant le lancement public.
- **Dépôt public dès le premier commit** (D-005) : aucun secret ni configuration sensible ; il n'est jamais rendu privé ni supprimé sans décision de la propriétaire. Hébergement sous une organisation GitHub (décision en attente : « Organisation GitHub du projet »). Miroir automatique sur Codeberg dès que la propriétaire aura un compte.
- Branche principale protégée : aucune modification sans demande de fusion, relecture et tests au vert ; jamais de poussée directe ni de réécriture forcée. Seuls les mainteneurs valident les contributions externes, toutes relues ligne par ligne.
- Double authentification obligatoire pour les mainteneurs, commits signés (D-014).
- Configuration privée hors dépôt : secrets, clés, seuils et règles anti-fraude, réglages de modération.

## 9. Cadre légal

Toute règle juridique ou institutionnelle utilisée dans le code ou les textes est vérifiée dans le droit en vigueur auprès de sources officielles, qui sont citées.

### 9.1 Documents juridiques à produire
Modèles marqués « À FAIRE RELIRE PAR UN AVOCAT » : mentions légales (LCEN), politique de confidentialité (RGPD), conditions d'utilisation conformes à l'article 14 du règlement sur les services numériques (restrictions, modération y compris par des outils automatisés, traitement des réclamations), charte de contenu, procédure de signalement et de recours, page « Notre méthode » (D-038), politique de divulgation responsable des failles (section 5.7), déclaration d'accessibilité (section 6), rapport de transparence (section 9.4).

### 9.2 Points d'attention
- **LCEN** : un éditeur non professionnel peut rester anonyme pour le public si l'hébergeur dispose de son identité.
- **Loi de 1881** : diffamation, injure, provocation.
- **Présomption d'innocence** et droit à l'image.
- **Méthode éditoriale** (M17, D-036, D-038) : sources primaires ; distinction toujours visible entre établi, allégué et en cours d'enquête ; statut de chaque procédure ; contradictoire et droit de réponse ; journal public des corrections ; échelle publique des cinq verdicts des décryptages (« Exact », « Plutôt exact », « Trompeur », « Contredit par les faits », « Invérifiable ») ; objections présentées et réfutées de façon sourcée ; fiches identiques pour tous ; un indice (par exemple la gravité des violences policières) est publié comme une répartition, jamais comme une note unique (D-057).
- **RGPD, article 9** : opinions politiques = données sensibles (section 9.6).
- **Règlement sur les services numériques (DSA)** : section 9.4.
- **Signalement obligatoire** des contenus pédocriminels et terroristes aux autorités compétentes (PHAROS) : section 9.5.
- **Conservation des données d'identification** (ajout du chef d'équipe, 7 novembre 2026) : la LCEN (article 6) et ses décrets n° 2021-1362 et n° 2021-1363 du 20 octobre 2021 imposent aux hébergeurs de conserver certaines données permettant d'identifier les personnes qui ont contribué à la création d'un contenu. Cela peut entrer en contradiction avec la règle « aucune adresse IP journalisée » (section 5.1). Point à faire trancher par un avocat avant l'ouverture publique, puis à présenter à la propriétaire avec des options ; rien n'est changé à la règle actuelle en attendant.
- **Droit électoral et financement politique** :
  - une personne morale (dont l'association PPF) ne peut ni financer une campagne électorale ni consentir à un candidat des avantages, y compris en nature (site, fichiers, communication) ;
  - tout outil qui concerne des candidats (engagements, comparateur, baromètre) les traite tous de façon strictement identique, sans appel à voter ; pour le Pacte démocratique : même texte pour tous, vérifiable par son empreinte, même jour d'envoi et même délai, une ligne par candidat même sans réponse, ordre alphabétique, réponse arrivée après la date limite non comptée mais visible avec sa date (D-060, D-062) ;
  - les dons aux partis sont réservés aux personnes physiques (de nationalité française ou résidant en France) et plafonnés à 7 500 euros par personne et par an ; les dons à un candidat sont plafonnés à 4 600 euros par élection (montants à re-vérifier avant tout guide) ;
  - PPF peut lancer des appels à dons pour elle-même, comme les organisations vérifiées, jamais pour un candidat ou un parti (D-039) ;
  - veille sur les règles applicables en période électorale (propagande, sondages).
- **Données de la HATVP** : les déclarations d'intérêts sont publiées en ligne ; les déclarations de patrimoine des parlementaires ne sont consultables qu'en préfecture et leur publication est interdite. Dans la première version, PPF ne fait que renvoyer vers la page officielle de chaque déclaration (D-064) ; toute autre réutilisation est vérifiée avant le développement.
- **Réutilisation des informations publiques** : licence citée pour chaque source ; quand une page officielle ne précise pas de licence, la réutilisation suit le code des relations entre le public et l'administration (source et date citées, données ni altérées ni dénaturées), avec relecture par un avocat (brainstorming, entrée « Licence des résultats des départementales de 2021 »).
- **Pétitions officielles** : seuils et effets réels vérifiés sur les textes en vigueur et affichés avec leur source (D-059) ; PPF ne recueille jamais de signature et ne renvoie que vers les sites officiels ; pétitions locales selon la Constitution (article 72-1) et le code général des collectivités territoriales (article L1112-16) (D-062).
- **Mineurs** (point d'attention n° 13, décision de la propriétaire du 4 novembre 2026) : plateforme ouverte à tous sans limite d'âge, sans vérification d'âge ; seules les personnes qui déclarent 15 ans ou plus remplissent le questionnaire citoyen et affichent leur bord politique ; réglages protecteurs par défaut pour tous.
- **Droit d'auteur** : aucun contenu piraté.

### 9.3 Charte de contenu

**Autorisé** : critique du gouvernement, des institutions et des élus ; revendications et propositions de réformes, y compris institutionnelles ; images de manifestations et des forces de l'ordre en intervention ; témoignages ; analyses militantes ; contenus satiriques ; histoire de toutes les luttes, y compris leurs épisodes violents, racontée de façon factuelle et sourcée (D-038) ; appels au boycott (D-044, relecture par un avocat, section 9.8).

**Interdit** :
- Appel ou incitation à la violence, aux crimes ou aux délits
- Divulgation d'informations personnelles (adresse, famille, identité complète, lieu de travail) de qui que ce soit, y compris des policiers et des élus
- Harcèlement, menaces
- Diffamation (accuser une personne nommée d'un crime ou d'un délit sans condamnation ni source établie)
- Haine et discrimination
- Contenus pédocriminels, terroristes
- Contenus protégés par le droit d'auteur sans autorisation
- Fausses informations délibérées

La charte est affichée dans l'application, acceptée par chaque contributeur (y compris à la création d'un salon, D-028) et sert de référence à la modération.

### 9.4 Règlement européen sur les services numériques (DSA)
(Ajout du chef d'équipe, 7 novembre 2026, pour le détail des obligations ; le rapport de transparence, le recours et les signaleurs de confiance figuraient déjà dans la version 1.4 et dans l'inventaire.)

PPF héberge des contenus publiés par ses utilisateurs : elle est un service d'hébergement et une plateforme en ligne au sens du règlement (UE) 2022/2065. Les micro et petites entreprises sont dispensées de certaines obligations (articles 15, paragraphe 2, et 19) ; par choix de transparence (principe 11), PPF les applique toutes, ce que l'inventaire prévoyait déjà pour le rapport de transparence (point d'attention n° 33). Le coordinateur pour les services numériques en France est l'Arcom (loi n° 2024-449 du 21 mai 2024).
- **Points de contact** (articles 11 et 12) : un point de contact pour les autorités et un pour les utilisateurs, publiés sur le site et dans l'application, joignables par voie électronique, avec les langues acceptées.
- **Conditions générales** (article 14) : claires, en langage simple, publiques.
- **Signalement et action** (article 16) : tout utilisateur, même sans compte, signale en un geste un contenu qu'il estime illicite (S-38) ; accusé de réception ; décision en temps opportun, diligente et objective ; l'auteur du signalement est informé de la décision et du recours éventuel à un outil automatisé. Un message chiffré se signale par son destinataire (S-127).
- **Exposé des motifs** (article 17) : toute restriction (masquage, retrait, restriction ou suspension d'un compte, S-66) est motivée : faits, règle de la charte ou de la loi, recours à un outil automatisé, voies de recours.
- **Soupçon d'infraction qui menace la vie ou la sécurité de personnes** (article 18) : information rapide des autorités compétentes, tracée au journal d'audit.
- **Réclamations** (article 20) : système interne gratuit, ouvert au moins six mois après la décision, réclamation examinée par une autre personne que celle qui a décidé (S-67), réponse motivée ; information sur le règlement extrajudiciaire des litiges (article 21).
- **Signaleurs de confiance** (article 22) : les signalements des signaleurs de confiance désignés par l'Arcom sont traités en priorité. En plus, PPF traite en priorité les signalements des organisations vérifiées de son annuaire (S-69), qui n'ont pas pour autant ce statut légal.
- **Protection contre les abus** (article 23) : suspension temporaire, après avertissement, des comptes qui publient souvent des contenus manifestement illicites et des auteurs de signalements manifestement infondés.
- **Rapport de transparence** (articles 15 et 24) : publié au moins une fois par an, en nombres agrégés (signalements reçus, décisions, délais, recours, transmissions aux autorités, outils automatisés utilisés, leur précision et leur taux d'erreur), jamais un contenu ni un compte. Le nombre moyen mensuel d'utilisateurs actifs n'est publié que s'il peut être estimé sans traceur (S-76) ; sinon, le rapport l'explique.
- **Interfaces et recommandations** (articles 25 et 27) : section 7 et M18.
- **Protection des mineurs** (article 28) : réglages protecteurs par défaut, sans collecte de données supplémentaire (section 9.2).
- **Injonctions des autorités** (articles 9 et 10) : traitées, tracées, et la personne concernée en est informée sauf interdiction légale ; PPF ne peut fournir que le peu qu'elle détient (minimisation).
- **Contenus à caractère terroriste** (règlement (UE) 2021/784) : retrait ou blocage dans l'heure qui suit une injonction de retrait (article 3) ; point de contact pour les injonctions (article 15) ; menace imminente pour la vie : information immédiate des autorités (article 14). Le délai d'une heure suppose une personne de l'équipe joignable à tout moment : organisation à définir avant le lancement public.

### 9.5 Procédure PHAROS
(Ajout du chef d'équipe, 7 novembre 2026, pour la procédure écrite ; l'obligation figurait déjà dans la version 1.4.)
1. Un contenu pédocriminel, terroriste ou relevant d'une autre infraction grave est masqué dès qu'il est repéré (alerte rouge de l'IA ou signalement).
2. Une personne de l'équipe qui en a le droit confirme la qualification.
3. Elle le signale sur le portail officiel PHAROS (internet-signalement.gouv.fr).
4. Le back-office enregistre la date et le numéro de dossier, et l'action entre au journal d'audit (contrat du back-office).
5. Un contenu pédocriminel n'est jamais téléchargé, copié ni transmis par l'équipe en dehors de ce que la procédure officielle demande.
6. Le nombre de transmissions figure dans le rapport de transparence.

### 9.6 Droits des personnes
- **RGPD** : registre des données ; minimisation ; droits d'accès, de rectification, d'effacement et d'opposition, avec une réponse sous un mois (article 12) ; export de ses données et suppression du compte (S-14) : les votes et soutiens restent comptés de façon anonyme, les contenus publiés sont détachés du pseudonyme (« compte supprimé ») et peuvent être supprimés un par un (point d'attention n° 3).
- **Durées de conservation des échanges** (point d'attention n° 20, décision du 4 novembre 2026) : messages anodins supprimés trois ans après la clôture ; réclamations conservées cinq ans puis anonymisées ; archive juridique motivée et réexaminée chaque année. L'e-mail reste facultatif et ne contient jamais le contenu du message.
- **Analyse d'impact relative à la protection des données** (RGPD, article 35) avant l'ouverture publique, parce que la plateforme touche à des opinions politiques (ajout du chef d'équipe, 7 novembre 2026).
- **Droit de réponse** : publié sous le contenu, dans les trois jours de sa réception (LCEN, article 1-1, III, numérotation issue de la loi du 21 mai 2024, à revérifier sur Légifrance ; D-056) ; signalement d'une erreur anonyme par défaut, sous chaque contenu (D-056).

### 9.7 Outils électoraux et désignation d'un candidat
- Les outils électoraux (Pacte démocratique, comparateur, baromètre des élus) suivent les règles de la section 9.2.
- La désignation citoyenne d'un candidat (M13.7, phase 6) impose une vérification d'identité limitée à ce module, avec des données supprimées après le vote ; son cadre légal doit être validé par un avocat avant tout développement.

### 9.8 À faire relire par un avocat avant l'ouverture publique
Doctrine de la propriétaire (D-044) : pousser le curseur au maximum, relecture juridique à la fin. La liste est tenue à jour dans `docs/ROADMAP.md`. Elle comprend au moins :
- les modèles juridiques (section 9.1) et la licence avec ses permissions (D-006) ;
- le statut de PPF pour les contenus publiés par d'autres (hébergeur) et pour les contenus relus avant publication ;
- les contenus de « Voir » sur des personnes réelles (diffamation, amnistie et réhabilitation, décisions de justice en données ouvertes, droit à l'oubli) et les fiches pratiques ;
- la mention publique « non déclarée » d'une manifestation (D-044, D-045) ;
- les appels au boycott ;
- l'annuaire et les cas limites des partis, candidats et comités de soutien (Code électoral, article L52-8), et tout appel de PPF à voter en dehors des trois outils électoraux ;
- les secours, les sorties et l'équipe citoyenne de sécurité en manifestation ;
- les guides de résistance civile pour aujourd'hui (limite de l'appel à la désobéissance civile) ;
- la réutilisation des résultats des départementales de 2021 ;
- la validation des comptes en personne par des bénévoles et la durée de conservation du journal d'audit ;
- la conservation des données d'identification face à la règle « aucune adresse IP » (section 9.2) (ajout du chef d'équipe, 7 novembre 2026) ;
- l'engagement de ne pas poursuivre les chercheurs en sécurité de bonne foi (ajout du chef d'équipe, 7 novembre 2026) ;
- les jurys citoyens de recours en modération, tirés au sort parmi les comptes vérifiés volontaires (ajout du chef d'équipe, 7 novembre 2026, à étudier) ;
- les statuts de l'association, au regard des conditions des prestataires de paiement ;
- la désignation citoyenne d'un candidat (section 9.7).

## 10. Planning

### 10.1 Contraintes de calendrier
- Le mouvement social est en cours : une première version utilisable doit sortir le plus vite possible.
- Élection présidentielle en avril 2027 : Pacte démocratique diffusé au début de l'année 2027, calendrier électoral personnel et comparateur avant le scrutin.
- Législatives possibles à tout moment en cas de dissolution (notamment après la présidentielle), avec un scrutin 20 à 40 jours après la dissolution.

### 10.2 Phases
- **Phase 0, fondations** : dépôt, intégration continue, documentation, modèle de menaces, squelette des applications commun à toutes les plateformes, achat des noms de domaine chez Hostinger dès le départ, guide pas à pas d'achat et de configuration du serveur Hostinger au moment du premier déploiement, déploiement d'un squelette en préproduction, modèles juridiques.
- **Phase 1, première version (site web, Android et ordinateur)** :
  - socle : comptes pseudonymes (M16), signature des contenus, port de publication, archivage horodaté (D-111), urne « une personne, une voix » (section 3.7), signalement et back-office de modération avec les obligations du DSA (M15, section 9.4), CMS éditorial ;
  - fil social (M18), « En direct » et messagerie par Internet (M14) ;
  - Voir : observatoire avec les indicateurs prioritaires (M2) ; votes des parlementaires de la législature en cours (D-064, avancés de la phase 5) ; la chronologie et les fiches des responsables publics (M4, M17), prévues en phase 2, ont été commencées en phase 1 à la demande de la propriétaire (D-034, D-036) ;
  - Comprendre : fiches pratiques hors ligne (M6), parcours, « Qui décide de ça ? », « Mes élus » (D-047), glossaire (D-048) ; les décryptages et l'histoire des luttes, prévus en phase 2, ont été commencés en phase 1 à la demande de la propriétaire (D-038) ;
  - Agir : carte et agenda (M1), annuaire des organisations (M3), appels (M12) ;
  - Décider, priorités liées au calendrier : mur des revendications avec vote protégé (M11), pétitions officielles (M10, commencées en phase 1, D-059), puis Pacte démocratique version 1 (M13.5) et sa délibération citoyenne (D-062), pour une diffusion au début de l'année 2027.
- **Phase 2** : témoignages avec leur provenance (M5), documentation et archives complètes (M4), suite des fiches des responsables publics, des décryptages et des pétitions commencés en phase 1, modération assistée par IA (après la décision sur l'hébergement du modèle). Décider : comparateur de la présidentielle 2027 (avant avril 2027), étages 1 à 5 de M13, dont le kit du candidat citoyen, prêt avant une éventuelle dissolution.
- **Phase 3, résilience** : module Nostr, miroirs statiques, adresse .onion, Tor dans l'application. Étage 6 de M13 (votations et assemblées citoyennes). La méthode « une personne, une voix », dont ces outils dépendaient, est validée (D-112) : avancer les votations et les assemblées tirées au sort est une question à poser à la propriétaire (ajout du chef d'équipe, 7 novembre 2026).
- **Phase 4** : messagerie hors ligne bitchat (M14).
- **Phase 5** : votes des parlementaires sur 50 ans (numérisation du Journal officiel), visualisations avancées, international (M9), histoire des luttes et bibliothèque complètes (M7, M8), outils d'organisation et de démocratie interne avancés.
- **Phase 6** : désignation citoyenne d'un candidat (M13.7), après validation juridique par un avocat.
- **iOS, hors phase** : compilation, tests (simulateur, puis bénévoles équipés via TestFlight) et publication sur l'App Store dès que le compte Apple Developer est ouvert, quelle que soit la phase en cours ; jusque-là, le site installable (PWA) sert de solution sur iPhone.
- **Phase de test et de débogage** : à la demande de la propriétaire (7 novembre 2026), les essais sur son téléphone (autotest de l'urne, carte réelle, réduction du code) sont regroupés dans une phase de test et de débogage, avant l'ouverture publique (D-112).
- Les fonctionnalités nouvelles du cahier fonctionnel 2.0, marquées « Tôt » ou « Plus tard », sont rangées dans ces phases lors du découpage en MVP (décision en attente : « Phase 1 trop chargée face à l'urgence ? »). L'avancement de chaque élément est suivi dans `docs/ROADMAP.md`.

## 11. Critères d'acceptation

Une fonctionnalité n'est livrée que si :
- ses tests automatisés (unitaires, intégration, interface) passent, en local comme en intégration continue ;
- elle respecte les principes de la section 2 et les exigences de sécurité de la section 5 (aucun traceur ni tiers, vie privée, secrets) ;
- elle est conforme à WCAG 2.2 AA, avec les tests d'accessibilité automatisés au vert, et s'affiche correctement sur téléphone, tablette et ordinateur, en portrait et en paysage, en mode clair et sombre ;
- son code partagé reste compatible iOS et ses textes sont externalisés ;
- toute nouvelle donnée stockée est justifiée dans le registre des données et toute nouvelle connexion sortante inscrite dans la liste des sorties ;
- elle a été présentée à la propriétaire, avec une explication pour la tester elle-même (et un APK si l'application est concernée) ;
- la documentation technique, `docs/ROADMAP.md` et `docs/CHANGELOG.md` sont à jour.

## 12. Moyens et hypothèses

- Hébergement : VPS Hostinger, détaillé à la section 3.5.
- Budget : 300 € couvrent la mise en route (noms de domaine, serveur payé 24 mois d'avance, sauvegardes hors site, modération par IA au démarrage) (D-003) ; 500 € au grand maximum, à éviter (brainstorming, entrée « Budget »). La suite dépend des adhésions et des dons. Aucune dépense, aucun abonnement, aucun essai payant n'est engagé sans l'accord explicite de la propriétaire, et chaque coût est annoncé, y compris au renouvellement.
- Déjà payé : les six noms de domaine (environ 145 € TTC ; D-001, brainstorming, entrée « Serveur de la préproduction : achat »).
- Dépenses à soumettre à la propriétaire : le serveur (KVM 1 ou KVM 2 pour 24 mois, décision en attente : « Serveur de la préproduction : achat ») ; les sauvegardes hors site (avant la production) ; le modèle d'IA (décision en attente : « Modération et assistant IA : hébergement du modèle ») ; le compte Google Play (décision en attente : « Distribution des versions de test sur Android (avertissements Play Protect et Samsung) ») ; les clés de sécurité physiques des super-administrateurs ; le prestataire de paiement des dons et adhésions (décision en attente : « Prestataire de paiement : éligibilité de HelloAsso »).
- Coûts reportés, à financer plus tard : compte Apple Developer (99 € par an, dispense possible pour une organisation à but non lucratif) et compilation iOS, deuxième serveur pour la production, serveur d'IA dédié, audit de sécurité externe avant le lancement public, avocat, expert-comptable.
- Intégration continue dans les quotas gratuits de GitHub Actions, sans machine macOS ; la consommation est surveillée.
- Matériel de test : un téléphone Android et un ordinateur ; pas d'iPhone. Les tests de la messagerie Bluetooth nécessiteront au moins deux appareils Android. Les essais sur téléphone sont regroupés dans la phase de test et de débogage (section 10.2).
- La propriétaire n'écrit pas de code : toute action manuelle fait l'objet d'un guide pas à pas.

## 13. Décisions en attente

Rien de ce qui dépend de ces questions n'est codé avant la réponse de la propriétaire. Les entrées sont dans les brainstormings indiqués.

| Question | Entrée | Ce qui en dépend |
|---|---|---|
| Hébergement du modèle d'IA (et, avec un prestataire, extension de la section 5.6 aux contenus envoyés pour publication) | Cahier des charges : « Modération et assistant IA : hébergement du modèle » | Modération assistée par IA, vérifications des revendications par l'IA |
| Achat du serveur : KVM 1 ou KVM 2 | Cahier des charges : « Serveur de la préproduction : achat » | Mise en ligne de la préproduction, puis de la production |
| Mot de passe de la préproduction et applications (D-072) | Cahier des charges : « Préproduction : le mot de passe et les applications » | Accès de l'équipe à la préproduction |
| Google Play (25 dollars une fois) et enregistrement des développeurs en 2027 | Cahier des charges : « Distribution des versions de test sur Android (avertissements Play Protect et Samsung) » | Installation sans avertissement, publication sur Google Play |
| Phrase de récupération (français par défaut, 12 mots) et bibliothèques (D-016 à D-018) | Cahier des charges : « Identités, phrase de récupération et signature des contenus : choix techniques » | Confirmation de choix déjà appliqués |
| Prestataire de paiement | Cahier des charges : « Prestataire de paiement : éligibilité de HelloAsso » | Dons et adhésions |
| Organisation GitHub | Cahier des charges : « Organisation GitHub du projet » | Premier envoi du dépôt, chaîne de déploiement |
| Briques de contenu communes (principe général) | Cahier des charges : « Briques de contenu communes » | Organisation des contenus hors de « Voir » |
| Vérification « loi déjà en vigueur » (D-030, à confirmer) | Cahier des charges : « Vérification loi déjà en vigueur : source des textes » | Index des textes de loi sur le serveur |
| Réutilisation des données de la HATVP | Cahier des charges : « Réutilisation des données de la HATVP » | Tout usage au-delà du lien vers la page officielle |
| Découpage en MVP et navigation détaillée | Cahier des charges : « Phase 1 trop chargée face à l'urgence ? » et « Navigation et UX : quatre espaces, un accueil, Mon territoire » | Ordre de livraison, menus |
| Visibilité par défaut de la participation (cercle, élus), en contradiction avec le principe 10 (D-044) | Cahier fonctionnel : « Agir : questions ouvertes après la première version » | Affichage de la participation dans « Agir » |
| Bouton AIDE et mode manifestation | Cahier fonctionnel : « Agir : questions ouvertes après la première version » | Secours et sécurité en manifestation |
| Niveau de compte requis pour créer un collectif, depuis les niveaux de D-112 | Question nouvelle (section 3.4.1), à relire avec le point d'attention n° 28 de l'inventaire | Création des collectifs |
| Choix technique du graphe social | Inventaire, point d'attention n° 26 | Relations, cercles, membres des collectifs |
| Avancer les votations et les assemblées tirées au sort | Question nouvelle (section 10.2) | Étage 6 de M13 |
| Licence des contenus et données publiés par PPF | Question nouvelle (section 3.8) | Réutilisation par les journalistes et chercheurs |

## Annexe A. Décisions intégrées

| Décision | Objet | Section |
|---|---|---|
| D-001 à D-004 | Domaines, hébergement, budget, une seule base de code | 1.2, 3.5, 12 |
| D-005, D-006 | Dépôt public, licence AGPL-3.0-or-later | 2, 8 |
| D-007 | Site en Ktor et kotlinx.html | 1.2, 4 |
| D-008, D-009 | Structure du dépôt, chaîne d'outils | 4 |
| D-010, D-011 | Identifiants techniques, classes de taille de fenêtre | 1.2 |
| D-012, D-013, D-031 | Signature des APK, intégration continue, version d'essai | 4, 4.1 |
| D-014 | Identité des commits | 5.4, 8 |
| D-015 | Aucune journalisation, en-têtes stricts | 5.1, 5.3 |
| D-016 à D-018 (à confirmer) | Cryptographie, phrase de récupération, format des contenus signés | 3.2, 3.6 |
| D-019 à D-022 | Base du serveur, base locale, API des événements, contenus éditoriaux | 3.2, 4 |
| D-023, D-025, D-026, D-035, D-044 | Fil par défaut, identité visuelle, barre du bas, retour, navigation | 7 |
| D-027, D-028, D-032 | Messagerie, conservation, salons | 3.4 |
| D-029, D-030 | Vues du mur, vérifications des revendications | 3.8, 5.1, 5.6 |
| D-033, D-034, D-036 à D-038, D-048, D-049, D-055 à D-057 | Comprendre, Voir, méthode, verdicts, glossaire, droit de réponse, grille de gravité | 5.1, 7, 9.2, 9.6 |
| D-039 à D-043, D-045, D-046, D-080 | Agir, agenda, appels, annuaire, carte, tuiles | 3.5, 4, 5.1 |
| D-044 | Transparence d'abord, curseur au maximum | 2, 9.8 |
| D-047, D-064 | Annuaire des élus, données du Parlement | 3.8 |
| D-059 à D-062 | Pétitions, Pacte, Décider, statut de PPF, non-partisanerie | 1.5, 9.2 |
| D-070 à D-074 | Déploiement, préproduction, durcissement, exemples | 3.5 |
| D-110 à D-112 | Vote anonyme, archivage horodaté, niveaux de votants | 3.2, 3.7 |
| D-120 (en cours) | Connexion de l'équipe par clé d'accès | 5.3 |

## Annexe B. Petit lexique

- **AAB** : format de paquet qu'exige Google Play ; Google en tire lui-même l'APK adapté à chaque téléphone.
- **APK** : fichier d'installation d'une application Android.
- **API** : porte d'entrée par laquelle les applications et d'autres programmes demandent des données au serveur.
- **Arcom** : autorité française qui contrôle l'application du règlement sur les services numériques.
- **Construction reproductible** : compiler deux fois le même code donne exactement le même fichier, ce qui permet à quiconque de vérifier qu'un fichier publié vient bien du code public.
- **C2PA** : norme ouverte qui décrit l'origine d'une photo ou d'une vidéo et ses modifications.
- **CSP, HSTS** : réglages envoyés par le serveur au navigateur pour bloquer les scripts piégés et imposer une connexion chiffrée.
- **DSA** : règlement européen sur les services numériques, qui fixe les obligations des plateformes envers leurs utilisateurs.
- **Empreinte (SHA-256)** : suite de caractères calculée à partir d'un fichier ; le moindre changement du fichier change l'empreinte.
- **F-Droid** : magasin d'applications libres pour Android, indépendant de Google.
- **Jeton anonyme (VOPRF)** : jeton délivré par le serveur sans qu'il voie ce qu'il signe, si bien qu'il ne peut pas le reconnaître quand il revient avec un bulletin de vote.
- **MLS** : protocole moderne de chiffrement des conversations de groupe.
- **Nostr, NIP** : protocole ouvert de publication de messages signés ; un « NIP » est l'un de ses documents de normalisation.
- **OpenTimestamps, RFC 3161** : deux méthodes pour prouver qu'un document existait à une date donnée, l'une par la chaîne Bitcoin, l'autre par une autorité d'horodatage.
- **OWASP ASVS, MASVS** : listes de référence des exigences de sécurité, pour les serveurs et sites web d'une part, pour les applications mobiles d'autre part.
- **PHAROS** : plateforme officielle du ministère de l'Intérieur pour signaler les contenus illicites en ligne.
- **PMTiles** : format de fichier unique qui contient toute une carte, servi par simple lecture de morceaux.
- **Préproduction** : copie de test du service, fermée au public, où l'on vérifie une version avant la mise en ligne.
- **Preuve de travail** : petit calcul demandé à l'appareil, qui coûte peu à une personne mais beaucoup à un robot qui veut créer des milliers de comptes.
- **PWA** : site web qui s'installe et fonctionne comme une application, même hors ligne.
- **RGAA, WCAG** : référentiels d'accessibilité numérique (français et international).
- **security.txt** : petit fichier public qui indique comment signaler une faille de sécurité.
- **Télémétrie** : envoi automatique de données d'usage ou d'erreur à un serveur, sans action de l'utilisateur ; PPF n'en fait pas.
- **Tor, adresse .onion** : réseau qui cache l'adresse IP de l'utilisateur, et adresse de site accessible seulement par ce réseau.
- **UnifiedPush, ntfy** : système de notifications libre et serveur auto-hébergé qui remplacent le service de Google.
- **WireGuard** : tunnel chiffré qui relie les appareils de l'équipe au serveur de préproduction.

## Journal des versions

| Version | Date | Modifications |
|---|---|---|
| 1.0 | 4 novembre 2026 | Création à partir des instructions initiales du projet et des échanges sur le bloc « Se politiser ». |
| 1.1 | 5 novembre 2026 | Ajout du principe 10 « Protéger par défaut, laisser le choix » et précision du principe 9 ; section 5.1 : confidentialité maximale par défaut et libre choix de l'utilisateur ; section 5.6 : décision sur la modération assistée par IA (alerte de l'équipe, décision humaine). Validé par la propriétaire. |
| 1.2 | 5 novembre 2026 | Section 5.6 : fonctionnement de la modération assistée par IA (alerte rouge avec masquage immédiat, alerte orange sans masquage, publication directe sans notification pour le reste, décision finale humaine, signalement manuel ouvert à tous). Validé par la propriétaire. |
| 1.3 | 5 novembre 2026 | Section 1.2 : une seule base de code et écrans adaptatifs dès le départ, iOS compatible mais compilé et publié plus tard, noms de domaine. Nouvelle section 3.5 : Hostinger pour les serveurs et les domaines, centre de données dans l'Union européenne hors de France, un seul serveur au départ avec production et préproduction séparées, préproduction fermée par tunnel chiffré, circuit de mise en ligne. Section 4 : intégration continue sans iOS pour l'instant, distribution. Section 5.3 : sauvegardes chez un autre prestataire, protection du compte de l'hébergeur. Section 10.2 : phase 0 et phase 1 (application de bureau dès la première version), iOS hors phase. Section 12 : budget de mise en route et coûts reportés. Validé par la propriétaire. |
| 1.4 | 7 novembre 2026 | Section 2 : retrait du principe 8 « Non partisane », sans texte de remplacement (les principes 9 et 10 gardent leur numéro). Section 5.5 : la menace devient « faire passer la plateforme pour le soutien d'un candidat ou d'un parti ». Restent, comme règles de méthode et de droit : le traitement identique des candidats dans les outils électoraux (9.2) et les fiches identiques pour tous (cahier fonctionnel, M17). Décision de la propriétaire du 7 novembre 2026, transmise par le chef d'équipe (D-062). Copie locale modifiée par l'IA ; à reporter dans le projet claude.ai. |
| 2.0 | 7 novembre 2026 | Réécriture sur mandat de la propriétaire du 7 novembre 2026 (ajouter aux cahiers tout ce qui est nécessaire à l'accomplissement de la vision), conduite par le chef d'équipe. Section 1.3 réorganisée selon les espaces de la vision 4.2 (En direct, Voir, Comprendre, Agir, Décider, Mon espace, fil social, messagerie, transverses) ; nouvelle section 1.5 (statut : association, D-062) ; principe 11 « La transparence d'abord » (D-044). Intégration des décisions déjà prises : licence, dépôt public, site en Ktor et kotlinx.html, chaîne d'outils, identités et signature (à confirmer), formats Nostr, messagerie et conservation, navigation, identité visuelle, données ouvertes et annuaire des élus, tuiles auto-hébergées, archivage OpenTimestamps et RFC 3161, vote anonyme à trois niveaux (D-110 à D-112), avec leurs renvois D-xxx (annexe A). Exigences ajoutées : sections 3.6 à 3.8, 4.1 (distribution, F-Droid, constructions reproductibles, APK par architecture et AAB), 5.7 à 5.12 (divulgation responsable et security.txt, sorties autorisées, rapports de plantage sans télémétrie, sauvegardes et plan de reprise, liste de contrôle avant chaque version, authenticité et provenance), tests et déclaration d'accessibilité (6), DSA et procédure PHAROS (9.4, 9.5), droits des personnes (9.6), liste de relecture par un avocat (9.8), décisions en attente (13), lexique. Copie locale modifiée par l'IA ; à reporter dans le projet claude.ai. |
