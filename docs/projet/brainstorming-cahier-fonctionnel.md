# Brainstorming : cahier fonctionnel

Brouillon de travail pour `cahier-fonctionnel.md`. On y note les idées nouvelles, les questions et les remises en question. Rien de ce qui figure ici n'est validé tant que l'entrée n'a pas le statut « Intégré ».

Statuts possibles : Idée, Question, À étudier, À valider, Intégré le [date], Rejeté le [date].

---

### [2026-11-07] Agir : publication des mobilisations et des appels
- Statut : Intégré le 2026-11-07 (décisions de la propriétaire ; code de l'espace Agir, contrat commun `docs/integration/agir-contrat.md`) ; à reporter dans le cahier fonctionnel version 2.0
- Concerne : cahier-fonctionnel.md, M1, M3 ; AG-02, AG-18, AP-02, AP-08, AP-09 ; points d'attention n° 6 et n° 12
- Contexte : la première version de l'espace « Agir » (agenda des mobilisations, appels, annuaire des organisations, accueil) met en attente de relecture les contenus des comptes qui ne sont ni l'équipe ni une organisation vérifiée. Cinq questions posées par le développement.
- **Décisions de la propriétaire (2026-11-07)** :
  1. PPF, association loi 1901, peut lancer des appels à dons, comme les organisations vérifiées ; jamais pour un candidat ou un parti ;
  2. pas de période probatoire pour une organisation nouvellement vérifiée : elle publie directement ;
  3. l'auteur a le droit d'annuler son événement sans nouvelle relecture (et de clore son appel avec son bilan) ;
  4. un événement encore en attente dont la date est passée est refusé automatiquement, avec un motif fixe ;
  5. aucune limite de nombre sur les contenus envoyés en relecture.
- Mise en œuvre : la limitation de débit générale de la publication (60 envois par minute et par compte, 600 au total), exigée par le cahier des charges (anti-spam, section 4), est conservée : elle ne limite pas le nombre de propositions, seulement leur cadence. La preuve de travail (type ALTCHA) reste à ajouter avant l'ouverture au public.

### [2026-11-06] Espace « Voir » : première version et questions ouvertes
- Statut : À valider
- Concerne : cahier-fonctionnel.md (M2, M4, M9, M13.2 ; futur module M17 « Mettre en lumière ») ; vision.md (pilier « Voir »)
- Contexte : demande de la propriétaire du 6 novembre 2026 : avancer les espaces que l'on peut déjà remplir, en commençant par « Voir », « ultra ergonomique », avec de petites fonctionnalités ajoutées en autonomie quand elles sont pertinentes.
- Fait (D-034, contenus tous fictifs et marqués [EXEMPLE]) : trois onglets parcourus au doigt (le mouvement, le pouvoir, libertés) ; recherche ; chiffres clés avec toutes les sources côte à côte ; fiche d'indicateur (graphique, tableau, méthode, corrections) ; chronologie ; observatoire des libertés et tableau de bord des contre-pouvoirs ; fiches des responsables publics ; législomètre (étape, prochain vote, 49.3, qui gagne et qui perd, votes, influence) ; mouvements dans le monde ; dossiers ; droit de réponse ; journal des corrections ; lexique des étiquettes.
- Ajouts faits en autonomie : « Suivre » un chiffre, une fiche ou un texte (liste sur l'appareil) ; « Copier avec la source » (un chiffre ne voyage jamais sans sa source) ; lexique des étiquettes ; bouton retour du système dans toute l'application ; barre de retour fixe en haut des pages longues.
- Questions pour la propriétaire :
  1. **Valider l'entrée M17 « Mettre en lumière »** (plus haut), dont « Voir : le pouvoir » applique déjà la méthode dans le code (statut établi, allégué, en cours d'enquête ; étape de la procédure ; présomption d'innocence ; droit de réponse ; journal des corrections). Sans cette validation, les rubriques « affaires », « déclarations contredites », « argent public » et « conflits d'intérêts » restent des démonstrations.
  2. **Couleurs de jugement** : une promesse « tenue » est en vert et « contredite par les actes » en rouge ; « y gagne » en vert, « y perd » en rouge dans « Qui gagne, qui perd ». Les votes, eux, sont volontairement neutres (gris). Recommandation : garder ces couleurs, toujours accompagnées du mot, parce qu'elles décrivent un fait et non une opinion ; les remplacer par une couleur neutre si vous craignez un reproche de partialité.
  3. **Qui figure dans « Responsables publics » au lancement** : tous les parlementaires (données ouvertes de l'Assemblée et du Sénat, prévues en phase 5 par le cahier des charges), seulement les ministres et les élus cités dans les contenus, ou les élus d'un territoire ? Recommandation : commencer par les personnes citées dans les contenus publiés, puis importer les données ouvertes.
  4. **Indice de gravité des violences policières** : la grille reste « À étudier » (entrée plus haut) ; l'indicateur affiche seulement le nombre en attendant.
  5. **Témoignages publiés** (VO-08) : ils rejoindront « Le mouvement » quand la modération existera ; d'accord ?
  6. **Chiffres clés dans « En direct »** (MA-01) : reprendre les quatre premiers chiffres de « Voir » sur l'écran « En direct » ? Recommandation : oui, c'est le parcours « Découvrir en 30 secondes ».
- Décision :

### [2026-11-06] Fil social : l'application comme premier réseau social de la vie politique
- Statut : Intégré le 2026-11-06 (cahier fonctionnel 1.1, module M18) ; modalités détaillées À étudier
- Concerne : cahier-fonctionnel.md (nouveau module M18, parcours clés) ; S-05 ; MA-03, S-120, S-121, S-23, S-37, S-38, S-39, AG-18, AG-22, TE-07
- **Décision de la propriétaire (2026-11-06)** : l'écran par défaut de l'application est un **fil**, sur le modèle d'un réseau social : les publications des comptes que l'on suit, des contenus suggérés par un algorithme, et la possibilité de partager, d'aimer, de commenter et de publier. « Le premier réseau social sur le thème de la politique. » À ajouter au cahier fonctionnel et à développer.
- Ce qui existe déjà dans l'inventaire et s'y rattache : fil d'actualité éditorial (MA-03), suivre un compte, une organisation ou un collectif (S-120, AG-22), abonnements par thème (S-23), partager (S-37), signaler (S-38), commenter (S-39), publications des organisations (AG-18), partager avec ses cercles (TE-07), modération assistée par IA (S-137) et file de modération (S-72).
- Ce qui est nouveau : tout contributeur publie des messages courts dans le fil (texte, puis image avec métadonnées supprimées, lien) ; des réactions (« j'aime ») ; des suggestions algorithmiques ; le fil comme porte d'entrée de toute l'application.
- Points à cadrer, dans l'esprit du cahier des charges (principes 9 et 10) :
  - **Comptes suivis** : liste privée par défaut, gardée sur l'appareil (S-120, AG-22) ; donc le fil « comptes suivis » est assemblé **par l'appareil**, qui demande au serveur les publications des clés qu'il suit, sans jamais lui confier la liste complète (requêtes par lots, ordre aléatoire) ; à étudier.
  - **Réactions (« j'aime »)** : aimer un contenu politique est un soutien, donnée sensible. Options : A. réaction publique signée sous son pseudonyme, avec un avertissement clair ; B. réaction comptée de façon anonyme (jeton anonyme ou preuve de travail), visible seulement en agrégé, l'appareil se souvenant de ce que l'on a aimé ; C. réaction privée, sur l'appareil seulement, sans compteur public. Recommandation : B, avec A possible pour qui le choisit (principe de libre choix) ; C en attendant B. Jamais de liste publique « untel a aimé » par défaut.
  - **Commentaires** : publics et signés, modérés comme toute publication ; dans les débats sur les revendications, ils gardent l'étiquette Pour, Contre ou Mitigé (S-39, DE-20) ; dans le fil, commentaire simple.
  - **Suggestions algorithmiques** : aucun profil individuel côté serveur (point d'attention n° 23). Les suggestions se calculent sur l'appareil à partir de choix explicites (thèmes, territoire, comptes suivis) et de signaux agrégés fournis par le serveur (tendances par thème et par territoire, comptages). Le règlement sur les services numériques (DSA, article 27) impose d'expliquer les principaux critères du fil et d'offrir une option sans profilage : le fil « chronologique » reste toujours disponible.
  - **Publier** : réservé aux comptes (paire de clés) ; relecture avant publication maintenue pour les témoignages, appels à témoins et revendications ; le reste se publie immédiatement, analysé par l'IA après publication (S-137), signalable par tous (S-38).
  - **Non-partisanerie et charte** : la charte de contenu s'applique au fil ; les candidats et partis restent traités à l'identique.
- Première version développée (6 novembre 2026) : fil chronologique des publications courtes ([EXEMPLE]), publication d'un message par un compte (envoyé au serveur dès qu'il est joignable), boutons de réaction et de commentaire présents mais inactifs en attendant le cadrage ci-dessus.
- Prochaine étape : maquettes du fil validées sur l'application, puis cadrage des réactions et des suggestions (entrées « À étudier » séparées) ; maillage interne (guides et contenus connexes) plus tard.

### [2026-11-06] Navigation : barre du bas fixe, fil par défaut
- Statut : Intégré le 2026-11-06 (cahier fonctionnel 1.1, S-05 ; complète le point d'attention n° 22)
- Concerne : cahier-fonctionnel.md, section 2 et M18 ; brainstorming du cahier des charges, entrée « Navigation et UX »
- **Décision de la propriétaire (2026-11-06)** : une **barre du bas fixe** sur téléphone avec l'accès rapide à la carte et à « En direct » ; l'écran par défaut est le fil.
- **Décision de la propriétaire (6 novembre 2026, nuit)** : la barre du bas est confirmée ; elle reçoit aussi l'accès rapide à la messagerie et aux revendications ; les vues du fil se parcourent en glissant le doigt (image de référence fournie).
- Mise en œuvre : barre du bas à cinq entrées, Fil (défaut), En direct, Carte, Messages, Revendications ; « Mon espace » est l'avatar en haut à droite, comme sur les maquettes de la propriétaire (6 novembre, soir) ; les quatre piliers (Voir, Comprendre, Agir, Décider) restent dans le menu ouvert par le bouton en haut à gauche ; sur tablette la barre devient une colonne d'icônes, sur ordinateur une barre latérale permanente avec les mêmes entrées puis les piliers et la mention d'indépendance. Les maquettes proposent à la place des boutons flottants « Carte » et « AIDE » : écart à trancher (brainstorming du cahier des charges, entrée du 2026-11-06).

### [2026-11-06] Messagerie : trois vues, accès dans la barre du bas
- Statut : Intégré le 2026-11-06 (cahier fonctionnel 1.2, M14) ; protocole À valider (brainstorming du cahier des charges)
- Concerne : cahier-fonctionnel.md, M14 ; S-124 à S-128 ; S-05
- **Décision de la propriétaire (6 novembre 2026)** : la messagerie a toujours fait partie des ambitions fonctionnelles. Accès rapide dans la barre du bas ; même logique et même fonctionnement qu'une messagerie de réseau social ou que WhatsApp : conversations privées, de groupe, de collectif, de syndicat ou d'association ; une vue différente selon qu'il s'agit de privé, de public (association, syndicat, collectif) ou des flux des médias et des élus suivis.
- Mise en œuvre (6 novembre) : écran « Messages » avec les trois vues (Privé, Collectifs, Flux) parcourues au doigt, bouton « Nouvelle conversation » désactivé, explication du chiffrement ; aucune conversation possible tant que le protocole n'est pas choisi.
- Points à cadrer : les « flux » des médias et des élus sont des comptes suivis (S-120, AG-22) dont les publications sont présentées comme des conversations à sens unique, sans réponse privée ; les salons de collectifs publics ne sont pas chiffrés de bout en bout (ils sont lisibles par tous et modérés), les salons réservés le sont ; la liste des conversations reste sur l'appareil ; signalement d'un message chiffré par son destinataire seulement (S-127).

### [2026-11-07] Salons de collectifs : création ouverte, charte, doublons
- Statut : Intégré le 2026-11-07 (D-028) ; détection par le sens À étudier
- Concerne : cahier-fonctionnel.md, M14 ; S-129 à S-136 (collectifs) ; AG-47 à AG-53
- **Décision de la propriétaire (7 novembre 2026)** : tout compte peut créer le salon d'un collectif, d'un syndicat ou d'une association (laisser exister les initiatives citoyennes, ne pas être accusé de censure) ; le salon respecte la charte de valeurs ; les doublons (même but, même localité) sont évités pour ne pas disperser les citoyens.
- Mise en œuvre : charte acceptée à la création (étiquette `ppf-charter`), territoire obligatoire (`ppf-territory`), refus des doublons par nom normalisé et territoire avec proposition du salon existant, recherche avant création. À étudier : rapprochement par le sens (modèle d'IA auto-hébergé, comme DE-19 pour les revendications), fusion de salons, modération a posteriori et signalement, rôles d'administrateurs du collectif (NIP-29, kinds 9000 à 9022).

### [2026-11-06] Messagerie terminée : questions ouvertes
- Statut : À étudier
- Concerne : cahier-fonctionnel.md, M14 ; D-027, D-028, D-032
- Mise en œuvre (6 novembre 2026, soir) : conversations privées de bout en bout, flux des comptes suivis, salons des collectifs, conservation réglable dans Mon espace ; le téléphone n'est pas encore relié à un serveur (il le sera avec la préproduction) : les échanges réels s'essaient sur l'ordinateur avec le serveur de développement.
- Questions à trancher :
  - **squat d'un nom de salon** : quelqu'un peut créer à l'avance « Collectif Éducation » à Lyon, puis le fermer ; le vrai collectif reçoit alors « ce salon existe déjà ». Options : reprise par l'équipe après signalement (avec journal d'audit), ne pas compter les salons fermés comme doublons, ou libérer un nom après une période sans activité. Recommandation : reprise par l'équipe, avec le back-office de modération ;
  - **clé distincte pour un collectif** : créer un salon relie publiquement son pseudonyme au collectif ; proposer de créer le salon avec une seconde identité ? Recommandation : oui, quand les identités multiples arriveront (S-43 et suivants) ;
  - **taille des conversations de groupe** : le protocole NIP-17 n'est pas fait pour de grands groupes ; limite proposée : 20 participants, au-delà un salon ;
  - **référentiel des territoires** : « Lyon », « Lyon 3e » et « 69 » sont aujourd'hui trois territoires différents ; une liste de communes et de départements réduirait la dispersion ;
  - **notifications** : sans elles, les messages n'arrivent que l'écran Messages ouvert (UnifiedPush avec ntfy auto-hébergé, prévu par le cahier des charges).

### [2026-11-07] Messagerie : conservation réglable par l'utilisateur
- Statut : Intégré le 2026-11-07 (D-028)
- Concerne : cahier-fonctionnel.md, M14 ; S-125
- **Décision de la propriétaire** : conservation des messages sur le serveur de trois mois au minimum, durée réglable par chaque utilisateur. Mise en œuvre : réglage dans Mon espace (3, 6 ou 12 mois), publié comme un événement NIP-78 signé que le serveur lit pour la boîte de l'utilisateur ; plafond haut du serveur à un an ; les appareils gardent leur copie déchiffrée sans limite.

### [2026-11-06] Revendications : vue du fil et accès dans la barre du bas
- Statut : Intégré le 2026-11-06 (cahier fonctionnel 1.2, M18) ; vote et débat bloqués par la méthode « une personne, une voix »
- Concerne : cahier-fonctionnel.md, M11, M18 ; DE-01, DE-18 à DE-21
- **Décision de la propriétaire (6 novembre 2026)** : accès rapide aux revendications dans la barre du bas, et une vue du fil qui ne montre que les revendications.
- Mise en œuvre : type de contenu `revendication` (une idée en une ou deux phrases, un résumé, un argumentaire, un thème, un territoire, un statut parmi nouvelle, en débat, très soutenue, retenue, campagne) ; mur en cards avec Pour, Contre et Débat affichés mais désactivés ; trois revendications [EXEMPLE] signées, qui ne sont pas des positions de PPF.
- Reste : proposer une revendication (DE-18, avec détection des doublons), vote et compteurs (méthode « une personne, une voix » à valider, protection anti-fraude), débat Pour, Contre, Mitigé (S-39, DE-20), tri et filtres (DE-01), baromètre (PR-07).

### [2026-11-06] Revendications : vues, filtres et vérifications automatiques
- Statut : Intégré le 2026-11-06 (cahier fonctionnel 1.3, M11 ; D-029, D-030) ; liste des thèmes À valider ; source des textes de loi et hébergement de l'IA : brainstorming du cahier des charges
- Concerne : cahier-fonctionnel.md, M11 ; DE-01, DE-18, DE-19, DE-20 ; S-137 ; point d'attention n° 33
- **Demande de la propriétaire (6 novembre 2026)** : l'utilisateur accède à différentes vues, personnalise ses propres vues et applique des filtres. « Filtre : par thème, par ville, popularité, nouveauté etc. Vue : thème société, médical, revendications portées par ceux que le user suit etc. » L'IA doit aussi gérer les problèmes possibles : doublons, loi déjà existante et en vigueur, etc.
- Mise en œuvre (6 novembre 2026) :
  - vues prêtes à l'emploi (Toutes, Comptes suivis, Mes revendications, Nationales, une vue par thème) et vues personnelles (nom, critères), rangées, renommées, retirées, rétablies ; enregistrées sur l'appareil seulement, car elles révèlent des centres d'intérêt politiques ;
  - filtres de la session (thème, territoire, statut, auteurs, mots-clés), tri (nouveauté, popularité, mises à jour récentes), « Enregistrer comme vue » ;
  - popularité provisoire : tant que le vote n'est pas ouvert, elle suit le statut donné par l'équipe (campagne, retenue, très soutenue, en débat, nouvelle) ; l'écran le dit ;
  - formulaire guidé « Proposer une revendication » : la revendication en une ou deux phrases, pourquoi, ce qui est demandé, ce qui existe déjà, un à trois thèmes, le territoire, l'acceptation de la charte ; les revendications qui disent la même chose s'affichent pendant la saisie ;
  - vérifications automatiques : règles du formulaire (longueur, une seule idée, thèmes, territoire, aucune coordonnée personnelle), seules à pouvoir bloquer l'envoi ; comparaison par les mots avec les revendications existantes (doublon, proche, déjà retenue ou devenue campagne), en tenant compte du territoire ; après l'envoi, sur le serveur, la même comparaison avec toutes les revendications, puis l'analyse par IA (doublon dit autrement, revendication contraire, loi en vigueur ou proposition de loi en discussion, plusieurs idées, hors sujet, personne visée, données personnelles, charte avec alertes rouge et orange) et la recherche dans les textes de loi, dès qu'elles seront en service ;
  - l'IA ne fait que conseiller : elle ne bloque, ne publie et ne refuse rien ; chaque avis porte la mention « Analyse par IA, à confirmer par l'équipe » ; une référence de loi citée de mémoire par l'IA est marquée « Référence à vérifier » ; seules les sources officielles (Légifrance, Assemblée nationale, Sénat, EUR-Lex) peuvent être liées.
- **À valider : la liste des thèmes**, proposée par l'équipe technique pour pouvoir coder les vues : Société, Santé, Éducation, Travail, Pouvoir d'achat, Logement, Transports, Environnement, Services publics, Démocratie et institutions, Justice et libertés, Impôts et finances publiques, Agriculture et alimentation, Numérique, Culture et médias. Ajouter un thème ne coûte rien ; en renommer un après le lancement oblige à convertir les vues enregistrées.
- Points ouverts :
  - popularité réelle et tri par débat ou par consensus : avec le vote (méthode « une personne, une voix » à valider) et le débat (DE-20) ;
  - vue « Près de chez moi » : avec « Mon territoire » (code postal gardé sur l'appareil) ;
  - statut des revendications citoyennes : aujourd'hui écrit dans l'événement signé par l'auteur (toujours « nouvelle ») ; quand l'équipe fera avancer une revendication, le statut devra venir d'une étiquette signée par la plateforme, pas de l'auteur ;
  - relecture par l'équipe : la file d'attente existe sur le serveur, l'écran de l'équipe viendra avec le back-office de modération (M15, double authentification, journal d'audit) ;
  - fusion des doublons (DE-19) : réunir les votes sans compter deux fois la même personne, quand le vote existera ;
  - durée maximale d'une proposition jamais traitée par l'équipe (par exemple 90 jours, puis refus automatique avec un motif fixe) : à étudier ;
  - une proposition refusée puis renvoyée à l'identique après 30 jours revient dans la file (l'empreinte du refus est oubliée) : acceptable ou empreinte à garder plus longtemps, à étudier ;
  - preuve de travail auto-hébergée (type ALTCHA) avant l'ouverture au public : sans elle, des comptes créés en masse rempliraient la file en quelques minutes.

### [2026-11-06] Maquettes de l'interface communautaire : cercles, visibilité, collectifs, messagerie, bouton AIDE
- Statut : À étudier
- Concerne : cahier-fonctionnel.md, M18 (fil), M16 (comptes), AG-22 et S-120 (collectifs, suivre), TE-07 (cercles), phase 4 (messagerie)
- Description : les maquettes de la propriétaire (6 novembre 2026) montrent des fonctionnalités qui ne sont pas encore spécifiées ; elles sont listées ici pour être cadrées une à une, dans l'esprit des principes 9 et 10 du cahier des charges.
  - **Cercles** (Amis proches, Amis, Famille, Collectifs) : relations gardées sur l'appareil (point d'attention n° 26) ; onglets du fil « Mon cercle », « Collectifs », « Suivis », « Près de moi ».
  - **Visibilité par publication** (« Visible par : votre cercle », public) : suppose des publications chiffrées pour un cercle, donc un protocole de partage de clés entre appareils ; à relier au choix du protocole de messagerie.
  - **« Voir mon profil comme »** (Public, Mon cercle, Amis proches) et **« Qui voit quoi »** (pseudonyme et avatar, ville, mes collectifs, participation aux actions ; votes et soutiens jamais visibles) : chaque assouplissement affiche une feuille d'avertissement avec le choix sûr en premier (« Garder “Amis proches” ») et « Rendre public quand même » (principe 10). Déjà repris dans l'application : la ligne « Mes votes et soutiens : jamais visibles » et l'explication.
  - **Pages de collectif** : bannière, badge « Collectif vérifié », Rejoindre, Suivre, onglets Publications, Agenda, Membres, À propos, charte épinglée, nombre de membres et ville.
  - **Événements dans le fil** : encart date, titre, lieu, participants annoncés, bouton « J'y vais » (la participation est une donnée sensible : comptage anonyme à prévoir, comme pour les réactions).
  - **Chiffres sourcés dans le fil** (Observatoire des mobilisations : ministère, organisateurs, comptage indépendant, lien « Méthode et sources »).
  - **Messagerie chiffrée de bout en bout** (phase 4) : groupes, mode hors ligne par Bluetooth, position partagée effacée après deux heures, alertes de proximité.
  - **Bouton flottant « AIDE »** : accès immédiat au secours en manifestation (numéros utiles, « je suis en garde à vue », fiche réflexes) ; à spécifier avec la fiche « Sécurité numérique et mode manifestation ».
  - **Recherche** (une personne, un collectif, un sujet) et **notifications** : présentes dans la barre du haut, désactivées tant que les modules n'existent pas.
- Décision :

### [2026-11-04] Modalités du choix des campagnes par les citoyens
- Statut : À valider
- Concerne : cahier-fonctionnel.md, M11 et M13.5
- Description : le principe est acquis (les citoyens choisissent par vote). Restent les modalités : fréquence des votes, nombre de campagnes actives en même temps, seuil de participation minimal, rôle de l'équipe.
- Recommandation : l'équipe vérifie seulement la faisabilité juridique et la conformité à la charte, sans filtrer selon ses préférences ; 3 campagnes actives au maximum pour concentrer les forces ; vote de priorisation tous les 6 mois.
- Décision :

### [2026-11-04] Liste de départ des mesures du Pacte démocratique
- Statut : À valider
- Concerne : cahier-fonctionnel.md, M13.5
- Description : en attendant que le mur des revendications fonctionne, proposer au vote une liste de départ, marquée [EXEMPLE] :
  - référendum d'initiative citoyenne (législatif, abrogatoire, constituant) à partir d'un nombre de signatures fixé ;
  - reconnaissance du vote blanc comme suffrage exprimé, avec nouveau scrutin tant qu'il est majoritaire ;
  - débat suivi d'un vote obligatoire à l'Assemblée pour toute pétition dépassant 500 000 signatures ;
  - référendum obligatoire pour toute révision de la Constitution ;
  - conventions citoyennes tirées au sort dont les propositions sont soumises au Parlement ou au référendum ;
  - transparence des archives des responsables publics.
- Point d'attention : chaque mesure doit être rédigée de façon précise et vérifiable (texte à modifier, délai), sinon l'engagement ne peut pas être contrôlé.
- Calendrier : liste à arrêter rapidement pour une diffusion au début de l'année 2027, avant la présidentielle d'avril.
- Décision :

### [2026-11-04] Vote blanc : questions à trancher pour une proposition solide
- Statut : À étudier
- Concerne : cahier-fonctionnel.md, M13.5 (fiche de campagne)
- Description : pour la proposition « nouveau scrutin tant que le vote blanc est majoritaire », la fiche doit répondre à : qui gouverne entre deux scrutins ? combien de fois peut-on revoter ? les candidats battus peuvent-ils se représenter ? comment éviter qu'un vote blanc soit compté comme un « non » lors d'un référendum (argument avancé en 2014 contre la réforme) ? Ces réponses relèvent du collectif ; l'IA prépare un document de comparaison des options, avec des exemples étrangers sourcés.
- Décision :

### [2026-11-04] Grille de l'indice de gravité des violences policières
- Statut : À étudier
- Concerne : cahier-fonctionnel.md, M2
- Description : l'IA propose une grille inspirée des observatoires existants, à valider par la propriétaire.
- Décision :

### [2026-11-04] Sujets de départ de « Qui décide de ça ? »
- Statut : Idée
- Concerne : cahier-fonctionnel.md, M13.1
- Description : commencer par une dizaine de sujets proches du quotidien : prix des carburants, loyers et logement, école, hôpital et santé, transports, impôts locaux, eau et énergie, salaires et retraites, services publics de proximité, environnement local.
- Décision :

### [2026-11-04] Déroulé du Programme du peuple
- Statut : À étudier
- Concerne : cahier-fonctionnel.md, M13.3
- Description : définir comment on passe des revendications individuelles au Programme du peuple : regroupement par thème, rédaction de synthèses, vote de validation, fréquence des mises à jour, gestion des revendications contradictoires.
- Décision :

### [2026-11-04] Ressources pour les enseignants
- Statut : À étudier
- Concerne : cahier-fonctionnel.md, M13.1
- Description : proposer des contenus librement réutilisables en classe (enseignement moral et civique), à condition qu'ils soient strictement pluralistes, l'école étant tenue à la neutralité. Mise à jour du 2026-11-04 : les professeurs et leurs élèves font désormais partie des publics de la vision (version 2.0) ; reste à définir la fonctionnalité dans le cahier fonctionnel (format des ressources, niveaux visés, licence de réutilisation).
- Décision :

### [2026-11-04] Campagne sur la transparence des archives
- Statut : Idée
- Concerne : cahier-fonctionnel.md, M13.5 et M4
- Description : pistes : supprimer le contrôle personnel des anciens présidents et ministres sur l'accès à leurs archives (protocoles de remise), imposer l'archivage des messageries officielles, fixer tous les délais d'accès par la loi (et non jusqu'à la « perte de valeur opérationnelle » laissée à l'appréciation de l'administration), raccourcir les délais.
- Décision :

### [2026-11-04] Désignation citoyenne d'un candidat (phase 6) et neutralité
- Statut : Question
- Concerne : cahier-fonctionnel.md, M13.7 ; brainstorming-vision.md (statut de PPF)
- Description : la désignation d'un candidat populaire est-elle compatible avec le traitement identique de tous les candidats par la plateforme ? Pistes : la confier à la structure séparée envisagée dans le brainstorming de la vision, ou l'organiser comme un outil ouvert que tout groupe peut utiliser. Dépend de la décision sur le statut de PPF et de l'avis d'un avocat.
- Décision :

### [2026-11-04] Campagne : inscrire la Déclaration de 1793 dans la Constitution
- Statut : Idée
- Concerne : cahier-fonctionnel.md, M13.5 (campagnes) ; brainstorming-vision.md (entrée sur la non-violence, option C)
- Description : faire campagne pour ajouter la Déclaration des droits de l'homme et du citoyen de 1793 au Préambule de la Constitution, à côté de celle de 1789. Elle proclame notamment le droit de s'assembler paisiblement (article 7), le droit à l'intervention populaire en matière législative (article 29) et le droit à l'insurrection quand le gouvernement viole les droits du peuple (article 35).
- Contexte : des députés ont déjà déposé des amendements en ce sens, notamment l'amendement n° 308 du 25 mars 2026 au projet de loi constitutionnelle relatif à la Nouvelle-Calédonie. La fiche de campagne devra retracer ces tentatives et leur sort, et répondre aux objections.
- Décision :

### [2026-11-04] Nouveau module M17 « Mettre en lumière »
- Statut : À valider
- Concerne : cahier-fonctionnel.md (nouveau module) ; regroupe les fiches des responsables politiques (M4) et l'étage 2 de M13 (« Voir pour qui la loi est faite ») ; brainstorming-vision.md (nouveau pilier)
- Description : fonctionnalités proposées, au niveau national et européen (représentants français au Parlement européen et au Conseil) :
  1. **Affaires et condamnations** : fiche par responsable public avec le statut exact de chaque procédure, la peine prononcée, la peine réellement exécutée, les aménagements, réductions, prescriptions ou relaxes, et la comparaison avec le traitement habituel d'une même infraction pour un citoyen ordinaire.
  2. **Promesses** : suivi des engagements électoraux (tenus, en cours, abandonnés, contraires), avec la source de la promesse et celle de l'acte.
  3. **Discours et votes** : votes contraires aux déclarations publiques et aux programmes (lien avec M4 et M13.2), y compris les votes des eurodéputés français.
  4. **Déclarations contredites par les faits** : affirmation, date, contexte, faits établis, sources ; recensement des fausses informations diffusées par des responsables publics (lien avec le décryptage de M6).
  5. **Argent public** : détournements et condamnations pour atteinte à la probité, rapports de la Cour des comptes et des chambres régionales des comptes, suites données à leurs recommandations.
  6. **Conflits d'intérêts et pantouflage** : allers-retours entre fonctions publiques et secteur privé, avis de la HATVP, répertoire des représentants d'intérêts, liens entre décisions publiques et intérêts privés documentés.
  7. **Indépendance de la justice** : modes de nomination (parquet, Conseil constitutionnel, Conseil supérieur de la magistrature), liens hiérarchiques avec l'exécutif, affaires où une interférence a été établie, décisions et rapports qui en traitent.
  8. **À qui appartiennent les médias** : carte des propriétaires, de leurs autres activités économiques et de leurs liens avec le pouvoir ; analyse des lignes éditoriales selon une méthode publique et chiffrée (par exemple : temps de parole, choix des sujets, invités), jamais par simple appréciation.
  9. **État des contre-pouvoirs** : tableau de bord régulier (recours au 49.3, suites données aux rapports d'autorités indépendantes, classements internationaux de liberté de la presse, interdictions de manifester, etc.).
- Méthode obligatoire, pour que le module soit inattaquable :
  - uniquement des faits établis par des sources primaires (décisions de justice, Journal officiel, comptes rendus parlementaires, rapports officiels) ou par des enquêtes de presse identifiées ; distinction toujours visible entre ce qui est **établi**, ce qui est **allégué** et ce qui est **en cours d'enquête** ;
  - présomption d'innocence : statut de la procédure affiché sur chaque fiche ;
  - vocabulaire normé : « déclaration contredite par les faits » plutôt que « mensonge » (qui suppose de prouver l'intention), sauf décision de justice ;
  - principe du contradictoire : demander sa réaction à la personne concernée avant publication et la publier ;
  - **droit de réponse obligatoire** : toute personne nommée ou désignée sur un site dispose d'un droit de réponse (article 6, IV de la LCEN), que le directeur de la publication doit insérer dans les trois jours sous peine d'amende ; prévoir un formulaire et une procédure ;
  - journal public des corrections ;
  - double validation éditoriale avant publication ;
  - points juridiques à faire valider par un avocat : diffamation (base factuelle suffisante, prudence, bonne foi), amnistie et réhabilitation, réutilisation des décisions de justice en données ouvertes (pseudonymisation), droit à l'oubli.
- Mise à jour du 2026-11-04 : dans la restructuration par espaces (entrée « Réorganiser le cahier fonctionnel par espaces »), ces fonctionnalités deviennent l'axe « Le pouvoir » et l'axe « Libertés et contre-pouvoirs » de l'espace « Voir ». Leur contenu et leur méthode restent valables.
- Mise à jour du 2026-11-06 : la première version de « Voir » (D-034) applique la méthode dans le code, avec des contenus fictifs [EXEMPLE] seulement : aucun fait sans statut, aucune affaire sans l'étape de sa procédure, présomption d'innocence affichée, rubriques identiques pour toutes les personnes, droit de réponse, journal des corrections. Voir l'entrée « Espace Voir : première version et questions ouvertes ».
- Décision :

### [2026-11-04] Canal sécurisé pour les lanceurs d'alerte
- Statut : À étudier
- Concerne : cahier-fonctionnel.md (M17 ou module dédié) ; cahier-des-charges.md (sécurité)
- Description : permettre de transmettre anonymement des documents (preuves de détournements, de conflits d'intérêts, etc.) à l'équipe, via un outil spécialisé auto-hébergé et accessible par Tor ; guide expliquant la protection légale des lanceurs d'alerte (loi du 21 mars 2022) et les précautions à prendre.
- Point d'attention : outil très sensible (sécurité des sources, responsabilité de l'association sur ce qu'elle publie) ; nécessite une procédure de vérification et l'avis d'un avocat.
- Décision :

### [2026-11-04] Suivi des lois en cours (« Législomètre »)
- Statut : Idée
- Concerne : cahier-fonctionnel.md, M4 ou M13.1
- Description : textes en discussion au Parlement, calendrier des votes, amendements importants, alertes par sujet, comptage des recours au 49.3 ; bouton « interpeller mes élus avant le vote ».
- Décision :

### [2026-11-04] Observatoire des libertés publiques
- Statut : Idée
- Concerne : cahier-fonctionnel.md, M2 ou M17
- Description : recenser les arrêtés d'interdiction de manifester, les dissolutions d'associations, les lois et décrets restreignant les libertés, les dispositifs de surveillance (drones, vidéosurveillance algorithmique), avec leurs sources et les recours engagés. Lien direct avec la valeur « défense de nos droits fondamentaux ».
- Décision :

### [2026-11-04] Solidarité concrète : caisses de grève et soutien juridique
- Statut : À étudier
- Concerne : cahier-fonctionnel.md, M12
- Description : annuaire des caisses de grève et des caisses de solidarité juridique (frais d'avocat des personnes poursuivies), avec liens vers leurs pages de dons ; entraide (covoiturage vers les manifestations, hébergement). Une grève dure moins longtemps quand on ne peut pas tenir financièrement : c'est un levier direct de la résistance civile.
- Points d'attention : choix des prestataires de paiement (sans traceurs), règles de l'appel public à la générosité.
- Décision :

### [2026-11-04] Boycotts et alternatives
- Statut : Idée
- Concerne : cahier-fonctionnel.md, M12
- Description : fiches sourcées sur les entreprises visées par un boycott (motifs, faits établis) et alternatives possibles. Point juridique à vérifier : l'appel au boycott est en principe une forme de liberté d'expression, mais le dénigrement d'une entreprise peut engager la responsabilité de l'association.
- Décision :

### [2026-11-04] Sécurité numérique et « mode manifestation »
- Statut : Idée
- Concerne : cahier-fonctionnel.md, M6
- Description : guides de sécurité numérique pour les militants (téléphone en manifestation, chiffrement, réseaux sociaux, protection de ses données) ; écran « mode manifestation » disponible hors ligne et en un geste, avec l'essentiel : droits, numéro de la legal team, premiers secours, que faire en cas d'interpellation.
- Décision :

### [2026-11-04] « Je veux aider » : bénévolat par compétences
- Statut : Idée
- Concerne : cahier-fonctionnel.md, M12 ou M16
- Description : permettre aux personnes de proposer leurs compétences (juristes, traducteurs, développeurs, graphistes, vérificateurs de faits, modérateurs) et à l'équipe de publier des missions. Indispensable pour faire vivre une plateforme de cette ampleur. Données à minimiser (compétences et pseudonyme, pas d'identité).
- Décision :

### [2026-11-04] Transparence de PPF elle-même
- Statut : Idée
- Concerne : cahier-fonctionnel.md (nouvelle section ou module) ; vision.md (valeurs)
- Description : publier les comptes de l'association, les dons reçus (montants agrégés), les décisions importantes, la composition et le fonctionnement des instances, le rapport de modération. Une plateforme qui exige des comptes du pouvoir doit être exemplaire : c'est aussi sa meilleure défense contre les accusations.
- Mise à jour du 2026-11-04 : la propriétaire demande une trésorerie publique et un tableau de bord fédérateur ; voir PP-06 à PP-08 dans l'entrée « Inventaire complet des fonctionnalités ».
- Décision :

### [2026-11-04] Soutien aux victimes
- Statut : Idée
- Concerne : cahier-fonctionnel.md, M3 et M6
- Description : orienter les victimes de violences (policières ou autres) vers les associations d'aide aux victimes, l'accompagnement juridique et le soutien psychologique ; fiche « que faire après une blessure en manifestation » (certificat médical, dépôt de plainte, saisine de l'IGPN ou du Défenseur des droits).
- Décision :

### [2026-11-04] Réorganiser le cahier fonctionnel par espaces
- Statut : À valider (décision prise le 2026-11-04, en attente d'intégration dans la version 2.0)
- Concerne : cahier-fonctionnel.md (structure complète) ; dépend des entrées « Restructurer les piliers » (brainstorming-vision.md), « Navigation et UX » et « Briques de contenu communes » (brainstorming-cahier-des-charges.md)
- Proposition : le cahier fonctionnel n'est plus organisé en 16 modules techniques, mais selon les espaces vus par l'utilisateur, chacun reposant sur les briques communes. Correspondance avec l'existant et les idées en cours (noms mis à jour selon la décision ci-dessous) :

| Espace | Rubriques | Reprend |
|---|---|---|
| **En direct** (anciennement « Maintenant ») | Chiffres clés, prochaines mobilisations, fil d'actualité, alertes, journée de mobilisation en direct | M1 (extrait), M2 (extrait), M6 (fil) |
| **Voir** : le mouvement | Chiffres de la mobilisation et de la répression, chronologie, archives, international | M2, M4 (chronologie, archives), M9 |
| **Voir** : le pouvoir | Fiches des responsables publics, votes, promesses, affaires et peines, déclarations contredites par les faits, conflits d'intérêts, argent public, influence des lobbies, législomètre | M4 (fiches), M13.2, M17 (volets 1 à 6), législomètre |
| **Voir** : libertés et contre-pouvoirs | Indépendance de la justice, propriété des médias, état des contre-pouvoirs, libertés publiques | M17 (volets 7 à 9), observatoire des libertés |
| **Comprendre** | Qui décide de ça ?, parcours « Comprendre en 5 minutes », fiches pratiques et droits, décryptage des fausses informations, dossiers, histoire des luttes, bibliothèque, sécurité numérique, ressources pour les enseignants | M6, M7, M8, M13.1 (sauf « Mes élus »), idées en cours |
| **Agir** | Carte et agenda, annuaire, groupes locaux, appels, témoigner, lanceurs d'alerte, solidarité et caisses de grève, boycotts, je veux aider, mode manifestation, secours et sécurité en manifestation, soutien aux victimes, messagerie | M1, M3, M5, M12, M14, idées en cours |
| **Décider** | Revendications et Programme du peuple, campagnes et pétitions, Pacte démocratique, interpeller ses élus, s'engager en politique (collectifs, partis, kit du candidat), votations et assemblées citoyennes, élections | M10, M11, M13.3 à M13.7, M13.1 (interpeller) |
| **Mon espace** (anciennement « Mon territoire ») | Tableau de bord personnel et personnalisable : territoire, élus, votes, pétitions, chiffres et contenus suivis | M13.1 (« Mes élus »), idées en cours |
| **Transverse** | Carte, recherche, comptes, modération, transparence de PPF | M15, M16, idées en cours |

- Ce qui ne change pas : le contenu détaillé des modules actuels, leurs règles et leur méthode ; seule leur organisation change. Les numéros de modules peuvent être conservés comme identifiants techniques dans le code.
- Décision : validée par la propriétaire le 2026-11-04, avec deux changements de nom : « Maintenant » devient « En direct » ; « Mon territoire » devient « Mon espace », un tableau de bord personnalisable par l'utilisateur (son territoire, mais aussi tout ce qu'il suit : pétitions, chiffres, contenus). Nouvelle navigation : voir S-05 et le point d'attention n° 22 de l'inventaire. L'entrée « Navigation et UX » du brainstorming du cahier des charges sera mise à jour en conséquence lors du découpage en MVP.

### [2026-11-04] Inventaire complet des fonctionnalités : socle applicatif et fonctionnalités PPF
- Statut : À valider (liste validée le 2026-11-04 ; intégration à venir dans la version 2.0 du cahier fonctionnel)
- Concerne : cahier-fonctionnel.md (structure et contenu complets, future version 2.0) ; découpage du cahier des charges en MVP
- Description : devenue trop longue pour un seul document, l'entrée est répartie en quatre documents :
  1. `docs/brainstorming-cahier-fonctionnel-inventaire-1-socle.md` : principes, historique des mises à jour, recommandation et décision, partie A (socle applicatif) ;
  2. `docs/brainstorming-cahier-fonctionnel-inventaire-2-ppf.md` : partie B (fonctionnalités PPF) et fonctionnalités retenues lors du tri ;
  3. `docs/brainstorming-cahier-fonctionnel-inventaire-3-points-attention.md` : points d'attention n° 1 à 25, options, analyses juridiques et décisions ;
  3 bis. `docs/brainstorming-cahier-fonctionnel-inventaire-3b-points-attention-suite.md` : points d'attention n° 26 à 34 (bloc communautaire, libre choix, intelligence artificielle).
- Décision : voir le document 1.

### [2026-11-04] Bloc communautaire : le nerf de la guerre
- Statut : À valider (décisions prises le 2026-11-05, en attente d'intégration dans la version 2.0 du cahier fonctionnel)
- Concerne : cahier-fonctionnel.md (socle A18, A21, A22 ; Agir ; Mon espace) ; inventaire, documents 1 à 3
- Description : la propriétaire estime que l'aspect communautaire est sans doute l'essentiel du projet. Sa définition (2026-11-05) :
  - le **cercle** d'un utilisateur regroupe ses amis et amis proches, sa famille et sa famille proche, et les organisations et collectifs dont il fait partie (un collectif est un groupe ou un club) ;
  - les **personnes suivies** forment un lien à sens unique, comme sur un réseau social classique ;
  - l'utilisateur invite des personnes par lien ou QR code, attribue un ou plusieurs statuts à chaque contact et gère les permissions de chacun de ses cercles ; un lien ne devient effectif que si l'autre accepte la demande ;
  - tout compte vérifié peut créer un **collectif** et le paramétrer (portée, nom, description, image, nombre de participants maximal et actuel, statuts des participants, etc.) ; on peut chercher un collectif et demander à le rejoindre ; le collectif a une page de profil pour partager un calendrier, des appels et tout type de contenu ;
  - des **canaux de communication chiffrés** entre les membres, les cercles et les collectifs, avec une gestion des demandes de contact.
- Proposition : relations et cercles (S-116 à S-123), messagerie chiffrée complétée (S-124 à S-128), collectifs (S-129 à S-136 pour le fonctionnement, AG-47 à AG-53 pour la page, le calendrier, les appels, les décisions et les alliances), partage avec ses cercles (TE-07), alertes en manifestation envoyées à ses cercles (AG-23). Les groupes locaux (AG-04) deviennent des collectifs.
- Décision (2026-11-05), détaillée dans les points d'attention n° 26 à 31 du document 3 bis de l'inventaire : graphe social minimisé (relations et cercles sur les appareils, listes de membres chiffrées sur le serveur) ; statuts privés ; création de collectifs par tout compte certifié, sans limite de nombre ; modération par IA qui alerte l'équipe, décision humaine ; appartenances et réglages protégés au maximum par défaut, que chacun peut librement assouplir après une mise en garde.

### [2026-11-05] Principe de libre choix : protéger par défaut, ne jamais décider à la place de l'utilisateur
- Statut : Intégré le 2026-11-05 dans la vision (version 4.2) et le cahier des charges (version 1.1, principe 10) ; à intégrer dans le cahier fonctionnel version 2.0
- Concerne : toute la plateforme ; vision.md (valeurs) ; cahier-des-charges.md (principes, section 5.1) ; cahier-fonctionnel.md ; code/CLAUDE.md
- Description : par défaut, la confidentialité et l'anonymat sont réglés au maximum. Chaque utilisateur peut assouplir autant qu'il le veut les réglages qui concernent ses propres données, jusqu'à les rendre publiques, après une mise en garde simple sur les risques. PPF est un garde-fou, pas un preneur de décision. Ce libre choix ne s'étend pas aux données des autres et ne lève ni les obligations légales ni les protections techniques communes. Détail : point d'attention n° 32 du document 3 bis de l'inventaire.
- Décision : demandée et validée par la propriétaire le 2026-11-05, avec la consigne de l'appliquer dans tous les documents qui en font mention.

### [2026-11-05] Intelligence artificielle : modération, assistant citoyen et crédits
- Statut : Décidé le 2026-11-05, sauf l'hébergement du modèle ; à intégrer dans le cahier fonctionnel version 2.0
- Concerne : cahier-fonctionnel.md (S-137, CO-20, S-138, S-139) ; cahier-des-charges.md (section 5.6, hébergement du modèle, fiscalité)
- Description : la propriétaire demande une modération par IA qui alerte l'équipe pour une décision humaine (décidé), et un assistant conversationnel pour les utilisateurs, avec des crédits mensuels gratuits puis un petit abonnement payant ou l'attente du mois suivant.
- Options et recommandations : points d'attention n° 33 et n° 34 du document 3 bis de l'inventaire (hébergement du modèle : service d'IA européen sans conservation des requêtes pour démarrer, puis serveur loué par PPF ; assistant gratuit avec limite mensuelle pour commencer, puis codes de recharge anonymes si les coûts l'exigent ; masquage provisoire des seuls contenus graves et manifestement illicites).
- Décision (2026-11-05) : modération par IA décidée : alerte rouge (contenu grave et manifestement illicite masqué immédiatement en attendant la décision humaine), alerte orange (contenu limite publié, équipe prévenue), publication directe sans notification pour tout le reste, signalement manuel ouvert à tous ; assistant : achat ponctuel d'un produit numérique plutôt qu'un abonnement, avis d'un expert-comptable avant toute vente. Restent à valider : la relecture préalable de certains contenus (point n° 33), l'hébergement du modèle (aucun serveur à installer chez soi, options révisées) et le prestataire de paiement (point n° 34).
- Décision complémentaire (2026-11-05) : relecture avant publication maintenue pour les témoignages, les appels à témoins et les revendications, publication immédiate sous contrôle de l'IA pour le reste ; assistant citoyen et crédits mis de côté, à ajouter plus tard sous forme de module autonome. Reste à valider : l'hébergement du modèle de modération (brainstorming du cahier des charges).
