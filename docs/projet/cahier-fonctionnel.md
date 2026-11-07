# Politisation Populaire Française : cahier fonctionnel

- Version : 1.5
- Date : 7 novembre 2026
- Statut : définitif. Toute évolution passe d'abord par `brainstorming-cahier-fonctionnel.md`.
- Documents liés : `vision.md` (pourquoi), `cahier-des-charges.md` (exigences, architecture, cadre légal, planning).

Ce document décrit **ce que fait la plateforme** : qui l'utilise, comment, et le détail de chaque module. Les exigences de sécurité, de vie privée, d'accessibilité et le cadre légal du cahier des charges s'appliquent à toutes les fonctionnalités décrites ici.

## 1. Rôles

| Rôle | Peut faire |
|---|---|
| Lecteur anonyme | Tout consulter, sans compte |
| Contributeur (pseudonyme, paire de clés) | Proposer, voter, soutenir, commenter, signaler, témoigner |
| Organisation vérifiée | Publier des événements et des contenus, effectuer des signalements de confiance |
| Modérateur | Traiter la file de modération, décider, motiver, notifier |
| Éditeur | Rédiger, relire, valider et publier les contenus éditoriaux |
| Administrateur | Gérer les comptes de l'équipe, les rôles, la configuration |

Tous les comptes de l'équipe ont la double authentification obligatoire et des permissions minimales. Toutes les actions de modération et d'administration sont journalisées.

## 2. Parcours clés

- **Découvrir en 30 secondes** : un lecteur ouvre l'application et voit les chiffres clés, les prochaines mobilisations, les informations essentielles, la revendication et la campagne prioritaires.
- **Comprendre** : un lecteur choisit un sujet de sa vie quotidienne et découvre qui en décide, ses élus et ce qu'ils ont voté (M13.1).
- **Revendiquer** : un contributeur propose une revendication, vote pour celles des autres et suit la synthèse (M11, M13.3).
- **Agir** : un contributeur soutient une campagne, signe une pétition officielle, interpelle ses élus (M10, M13.5, M13.1).
- **S'engager** : un citoyen crée un collectif ou se prépare à être candidat avec le kit du candidat citoyen (M13.4).
- **Se mobiliser** : un lecteur trouve une manifestation près de chez lui et consulte ses droits hors ligne (M1, M6).
- **Témoigner** : un contributeur publie une photo ; les métadonnées sont supprimées sur son téléphone avant l'envoi (M5).

## 3. Règles transverses

- **Sources** : chaque donnée affichée comporte sa source, sa date et son niveau de fiabilité.
- **« Et maintenant ? »** : chaque contenu du bloc « Se politiser » se termine par une action concrète proposée.
- **Hors ligne** : les fiches pratiques, la carte, l'agenda et les contenus consultés restent disponibles sans réseau.
- **Un compte, un vote** : chaque vote ou soutien est protégé contre la fraude ; seuls les résultats agrégés sont publiés.
- **Traitement identique** : tout ce qui concerne des candidats ou des partis les présente tous de la même façon.
- **Facile à lire** : version simplifiée des contenus essentiels.

## 4. Modules

### M1. Carte et agenda des mobilisations
- Manifestations, blocages, rassemblements, grèves, AG : passés et à venir.
- Filtres par date, lieu, type, organisation.
- Ajout direct par les organisations vérifiées ; proposition par les contributeurs, validée par la modération.
- Export calendrier (iCal).
- Consultable hors ligne.

### M2. Observatoire : les chiffres
- **Indicateurs configurables par l'équipe sans code** : création, définition, unité, méthode de calcul, sources acceptées.
- Indicateurs initiaux :
  - nombre de manifestants par manifestation (passée ou à venir) ;
  - nombre de personnes participant au mouvement en France ;
  - nombre de syndicats et d'associations membres du mouvement ;
  - nombre de lycées et d'universités bloqués, actualisé en temps réel ;
  - blessés légers, blessés graves, décès (avec la mention « dont mineurs ») ;
  - vitrines et commerces dégradés ou pillés ;
  - victimes de violences policières, avec un **indice de gravité** en cinq niveaux (validé par la propriétaire le 7 novembre 2026) : 1 sans incapacité (pas d'incapacité totale de travail, ITT), 2 blessure légère (ITT de 8 jours ou moins), 3 blessure grave (ITT de plus de 8 jours), 4 mutilation ou infirmité permanente, 5 décès ; pour chaque cas : mineur ou non, arme ou moyen en cause, niveau de preuve (certificat médical, vidéo, décision de justice, déclaration seule) ; les cas déclaratifs sont comptés à part, jamais mélangés aux cas vérifiés ; l'indice d'une journée est la répartition entre les cinq niveaux, jamais une note unique ;
  - interpellations, gardes à vue, nasses (nombre et durée moyenne) ;
  - MICA reçues par manifestation ;
  - comptes de réseaux sociaux déclarant un shadowban (indicateur **déclaratif**, signalé comme tel).
- **Chaque donnée** comporte : valeur, date, lieu, type de source (préfecture/ministère, syndicats/organisateurs, comptage indépendant, médias, signalements d'utilisateurs), lien vers la source, méthode, niveau de fiabilité.
- Quand les sources divergent, elles sont **affichées côte à côte**, jamais fusionnées en un chiffre unique sans explication.
- Signalements d'utilisateurs protégés contre la manipulation : un compte, un signalement ; détection des comportements anormaux ; validation avant intégration.
- Visualisations : compteurs, courbes, cartes. Méthodologie publique pour chaque indicateur.

### M3. Annuaire des organisations
- Associations, avocats bénévoles, legal teams, street medics, collectifs locaux et nationaux, syndicats.
- Fiche, carte, contacts, badge « vérifiée ».
- Les organisations vérifiées ont un compte pour publier des événements et des contenus et pour effectuer des signalements de confiance.

### M4. Documentation et archives
- **Chronologie du mouvement** : manifestations, déclarations officielles, décisions (Journal officiel, décrets, arrêtés préfectoraux, circulaires), réponses du gouvernement.
- Chaque élément : date, sources, copie archivée (empreinte + horodatage, archive propre et lien Internet Archive), historique des versions.
- **Fiches des responsables politiques** : déclarations, votes, affaires judiciaires. Le **statut de chaque procédure** est toujours affiché clairement (enquête, mise en examen, condamnation en première instance, appel, condamnation définitive, relaxe), avec les sources. Respect strict de la présomption d'innocence.
- **Votes des partis et des parlementaires** (phase 5) : import des données ouvertes de l'Assemblée nationale et du Sénat, puis extension historique jusqu'à 50 ans (numérisation du Journal officiel). Visualisations : qui a voté quoi, cohérence entre discours et votes.
- **Accès aux archives publiques** : fiche pratique expliquant les délais légaux de communication et comment demander une consultation anticipée.

### M5. Témoignages
- Photo, vidéo, audio, texte.
- Date et lieu déclarés ; métadonnées supprimées sur l'appareil avant l'envoi ; outil de floutage optionnel.
- Empreinte horodatée ; modération avant publication.
- Phase ultérieure : conservation chiffrée de l'original, accessible uniquement aux avocats partenaires pour des procédures.

### M6. Informer
- **Fil d'actualité** court et accessible : articles, vidéos, podcasts, radio, formats type publication de réseau social, avec boutons de partage.
- **Fiches pratiques**, disponibles hors ligne : droits en manifestation, que faire en cas d'interpellation, garde à vue, nasse, filmer les forces de l'ordre (légal en France), contacts des legal teams, premiers secours.
- **Décryptage des fausses informations** : affirmation, verdict, explication, sources.
- **Dossiers thématiques** rédigés par l'équipe éditoriale (séparation des pouvoirs, contrôle de la police et rôle de l'IGPN, libertés publiques, corruption, etc.).
- **CMS** pour l'équipe : rédaction, relecture, validation, publication, mise à jour.

### M7. Éduquer
- Histoire des révolutions et des mouvements sociaux en France et dans le monde.
- Stratégies d'action **non violentes** : organisation collective, information massive, grève, boycott, blocage économique, désobéissance civile, pétitions, mobilisation électorale.
- Analyses de ce qui a fonctionné ou échoué, et pourquoi.
- Aucun appel ni incitation à la violence.

### M8. Bibliothèque numérique
- Œuvres du domaine public et sous licence libre hébergées directement.
- Liens vers les éditeurs et les bibliothèques publiques pour les autres.
- Aucun contenu piraté.

### M9. International
Carte et chronologie des mouvements sociaux dans le monde liés à la vie chère et à la démocratie : revendications, déroulé, résultats (y compris les changements de gouvernement obtenus).

### M10. Pétitions et mobilisation massive (bloc « Se politiser »)
- Relais des pétitions officielles : plateformes de l'Assemblée nationale et du Sénat, initiative citoyenne européenne, soutiens aux référendums d'initiative partagée lorsqu'une procédure est ouverte.
- Explication claire des seuils et de leurs effets réels. État du droit vérifié le 4 novembre 2026, à re-vérifier avant développement :
  - Assemblée nationale : à 100 000 signatures, la pétition est mise en avant sur le site de l'Assemblée ; à 500 000 signatures issues d'au moins 30 départements ou collectivités d'outre-mer, elle **peut** être débattue en séance si la Conférence des présidents le décide, sans vote à l'issue du débat ;
  - Sénat : 100 000 signatures en 6 mois permettent la transmission à la Conférence des présidents en vue d'un éventuel débat ;
  - référendum d'initiative partagée : initiative d'un cinquième des parlementaires (185), soutenue par un dixième des électeurs inscrits ; pas de référendum si le Parlement examine le texte.
- Suivi des compteurs, kits de partage, objectifs de signatures par territoire (y compris la répartition sur 30 départements).
- Objectif : rendre possibles des mobilisations de plusieurs millions de signatures.

### M11. Mur des revendications (bloc « Se politiser »)
- Les citoyens proposent ce qui doit changer.
- Vote : un compte, un vote, protection anti-fraude.
- Regroupement par thème, assisté par IA et validé par l'équipe.
- Synthèse publique, régulièrement mise à jour, des revendications prioritaires.
- **Vues et filtres** (décision de la propriétaire du 6 novembre 2026) : une **vue** est un regard enregistré sur le mur : un thème (Société, Santé...), les revendications portées par les comptes que l'on suit, les siennes, les revendications nationales. Chacun compose ses propres vues, les nomme, les ordonne et les retire ; elles restent sur l'appareil. Un **filtre** affine pour un moment la vue en cours (thème, territoire, statut, auteurs, mots-clés) et un **tri** l'ordonne (nouveauté, popularité, mises à jour récentes) ; un ensemble de filtres peut être enregistré comme vue.
- **Vérifications automatiques** d'une revendication proposée (demande de la propriétaire du 6 novembre 2026) : pendant la saisie puis après l'envoi, l'outil repère les doublons et les revendications proches, une loi déjà en vigueur ou en discussion, une revendication contraire, plusieurs idées dans une seule, les données personnelles et ce qui enfreint la charte. Il conseille l'auteur et prépare la relecture ; il ne publie ni ne refuse jamais rien : la relecture humaine décide.
- Les revendications prioritaires alimentent les cahiers de doléances et le Programme du peuple (M13.3), le choix des campagnes et les mesures du Pacte démocratique (M13.5).

### M12. S'organiser (mobilisation)
- Guides : créer une association loi 1901, un collectif, déclarer une manifestation en préfecture, organiser une AG, une grève, une campagne d'information, structurer un mouvement. (Les guides d'organisation politique sont dans M13.4.)
- Outil de proposition d'actions : quoi, qui, quand, où, comment, dans quel but. Discussion, soutien, vote, transformation en événement sur la carte.
- Groupes locaux par ville ou par établissement.

### M13. Se politiser : reprendre le pouvoir

Le cœur de la mission. Ce bloc intègre M10 et M11. Il est organisé en six étages, du plus simple au plus ambitieux, plus un volet élections.

#### M13.1 Étage 1 : Comprendre
- **« Qui décide de ça ? »** : l'utilisateur choisit un sujet de sa vie quotidienne et entre son code postal ; l'application affiche la chaîne de décision (commune, intercommunalité, département, région, État, Union européenne), les élus concernés, leurs votes récents sur le sujet et comment les contacter.
- **Parcours « Comprendre en 5 minutes »** : comment une loi est votée, le budget de l'État, le rôle de chaque élu, le 49.3, la motion de censure, la dissolution, le référendum, la révision de la Constitution, le fonctionnement des élections. Quiz courts. Progression stockée uniquement sur l'appareil.
- **Mes élus** : fiche de chacun de ses élus par code postal, avec ses votes et ses engagements.
- **Interpeller ses élus** : modèles de courrier à personnaliser ; suivi public des réponses reçues ou non (sans données personnelles au-delà du mandat public).
- **Participer à toutes les formes de démocratie existantes** : inscription sur les listes électorales, procuration, devenir assesseur, budgets participatifs, consultations et enquêtes publiques, conseils de quartier, pétitions (M10).
- Public visé en priorité : les adultes, qui pour la plupart n'ont jamais reçu d'éducation civique concrète.

#### M13.2 Étage 2 : Voir pour qui la loi est faite
Montrer les faits, sans affirmer de conclusion :
- **Promesses et votes** : comparaison entre ce que les élus et les partis ont promis et ce qu'ils ont voté.
- **Influence** : représentants d'intérêts ayant rencontré les décideurs sur chaque loi importante (répertoire public de la HATVP).
- **Intérêts des élus** : déclarations d'intérêts publiées par la HATVP, dans le respect des règles de réutilisation (voir cahier des charges, 9.2).
- **Qui gagne, qui perd** : pour chaque loi importante, effets chiffrés et sourcés sur les différentes catégories de la population.

#### M13.3 Étage 3 : Revendiquer
- **Mur des revendications** (M11).
- **Cahiers de doléances** par territoire : regroupement des difficultés et des revendications locales, synthèse validée par l'équipe, publication.
- **Programme du peuple** : synthèse nationale des revendications prioritaires, votée par les citoyens, versionnée et mise à jour régulièrement.

#### M13.4 Étage 4 : S'organiser politiquement
- **Guides pas à pas**, juridiquement vérifiés : créer un collectif, une association politique, une liste citoyenne aux élections locales, un parti politique (statuts, déclaration, mandataire financier ou association de financement agréée par la CNCCFP, plafonds de dons, obligations comptables).
- **Kit du candidat citoyen** (législatives en priorité, puis autres élections) :
  - conditions d'éligibilité ; choix du remplaçant ;
  - déclaration de candidature en préfecture (formulaire Cerfa officiel, pièces à fournir, dépôt en personne) ;
  - mandataire financier et compte de campagne ; plafond de dépenses ; seuils de remboursement (5 % des suffrages exprimés) ;
  - seuil d'accès au second tour (12,5 % des électeurs inscrits) ;
  - modèle de budget de campagne ; calendrier ;
  - **mode « dissolution »** affichant automatiquement le calendrier raccourci (élections 20 à 40 jours après la dissolution) ;
  - carte des circonscriptions avec les résultats passés.
  Le kit est identique pour tous les utilisateurs.
- **Outils de démocratie interne** pour les groupes : votes, AG en ligne, tirage au sort, mandats courts et révocables, rotation des responsabilités.

#### M13.5 Étage 5 : Changer les règles
- **Campagnes pour de nouveaux droits démocratiques**, choisies par les citoyens par vote. Exemples, marqués [EXEMPLE] tant que le vote n'a pas eu lieu : référendum d'initiative citoyenne, reconnaissance du vote blanc comme suffrage exprimé avec nouveau scrutin tant qu'il est majoritaire, débat et vote obligatoires pour les pétitions dépassant un seuil, conventions citoyennes tirées au sort dont les décisions s'imposent, transparence des archives présidentielles.
- **Fiche de chaque campagne** :
  - droit actuel et changement demandé ;
  - texte exact à modifier (article de la Constitution, loi organique, Code électoral) ;
  - propositions de loi déjà déposées et leur sort ;
  - réponses sourcées aux principales objections ;
  - chemin pour obtenir la réforme.
- **Baromètre des élus** par campagne : qui s'est engagé, qui a voté pour, contre, s'est abstenu ou n'a pas répondu.
- **Compteur de soutiens** par territoire et lien avec les pétitions officielles (M10).
- **Pacte démocratique** :
  - liste courte (5 à 10 mesures) d'engagements précis et vérifiables, choisis par vote des citoyens ;
  - envoyé à **tous** les candidats d'une élection, sans exception, avec le même texte et le même délai ;
  - tableau public par candidat et par mesure : signé, refusé, sans réponse ;
  - engagements archivés avec empreinte et horodatage ;
  - après l'élection, suivi de ce que chaque élu fait réellement (textes déposés, votes) ;
  - première échéance : présidentielle d'avril 2027 (diffusion au début de l'année 2027), puis les législatives.

#### M13.6 Étage 6 : Décider nous-mêmes, dès maintenant
- **Votations citoyennes** sur les grandes questions : sans valeur légale, destinées à devenir un fait politique par leur ampleur.
- **Assemblées citoyennes tirées au sort** parmi des volontaires, avec une information contradictoire et un rapport public.
- La méthode garantissant « une personne, une voix » sans collecter d'identité doit être validée avant tout développement.

#### M13.7 Élections
- **Présidentielle 2027** : comparateur des programmes et des votes passés des candidats et des partis, présenté de façon strictement identique pour tous.
- **Comprendre les élections** : fonctionnement des scrutins, rôle de chaque élu (lien avec M13.1).
- **Phase 6, module isolé** : désignation citoyenne d'un candidat populaire. Une personne = une voix, ce qui impose une vérification d'identité **limitée à ce module**, avec des données supprimées après le vote. Le cadre légal (droit électoral, financement des campagnes) doit être validé par un avocat avant tout développement.

### M14. Messagerie chiffrée
- La messagerie fait partie des ambitions fonctionnelles dès la phase 1 (décision de la propriétaire, 6 novembre 2026) : accès rapide dans la barre du bas, même logique et même fonctionnement qu'une messagerie de réseau social ou que WhatsApp : conversations privées, de groupe, de collectif, de syndicat ou d'association.
- Trois vues : **Privé** (conversations entre deux personnes et petits groupes, chiffrées de bout en bout), **Collectifs** (salons publics ou réservés des associations, syndicats et collectifs), **Flux** (publications des médias et des élus suivis, présentées comme des conversations à sens unique). Fonctions détaillées : S-124 à S-128 (brainstorming).
- Mode hors ligne : sans Internet, en Bluetooth de proche en proche, compatible avec les applications bitchat existantes (voir cahier des charges, 3.4), sans lien avec le serveur central.
- Le protocole de chiffrement reste à choisir (brainstorming du cahier des charges, entrée du 6 novembre 2026).

### M15. Modération
- File de modération pour l'équipe.
- Trois sources de signalement : IA (pré-signalement, jamais de suppression automatique sauf contenus manifestement illégaux les plus graves), communauté, organisations partenaires (signaleurs de confiance).
- Signalement possible d'un contenu, d'un commentaire ou d'un compte.
- Chaque décision est motivée, notifiée, et peut faire l'objet d'un recours.
- Rapport de transparence public (Digital Services Act).
- Signalement des contenus pédocriminels et terroristes aux autorités compétentes (PHAROS).

### M16. Comptes et rôles
- Rôles : voir section 1.
- Compte contributeur créé sur l'appareil (paire de clés), sans numéro de téléphone ni e-mail obligatoire, récupérable par phrase secrète.
- Lecture possible sans compte.

### M17. Mettre en lumière (espace « Voir » : le pouvoir, libertés et contre-pouvoirs)

Validé par la propriétaire le 7 novembre 2026. Au niveau national et européen (représentants français au Parlement européen et au Conseil).
- **Affaires et condamnations** : fiche par responsable public avec le statut exact de chaque procédure, la peine prononcée, la peine réellement exécutée, les aménagements, réductions, prescriptions ou relaxes, et la comparaison avec le traitement habituel d'une même infraction pour un citoyen ordinaire.
- **Promesses** : suivi des engagements électoraux (tenus, en cours, abandonnés, contraires), avec la source de la promesse et celle de l'acte.
- **Discours et votes** : votes contraires aux déclarations publiques et aux programmes (lien avec M4 et M13.2), y compris les votes des eurodéputés français.
- **Déclarations contredites par les faits** : affirmation, date, contexte, faits établis, sources (lien avec le décryptage de M6).
- **Argent public** : détournements et condamnations pour atteinte à la probité, rapports de la Cour des comptes et des chambres régionales des comptes, suites données à leurs recommandations.
- **Conflits d'intérêts et pantouflage** : allers-retours entre fonctions publiques et secteur privé, avis de la HATVP, répertoire des représentants d'intérêts, liens documentés entre décisions publiques et intérêts privés.
- **Indépendance de la justice** : modes de nomination, liens hiérarchiques avec l'exécutif, interférences établies, décisions et rapports qui en traitent.
- **À qui appartiennent les médias** : propriétaires, autres activités économiques, liens avec le pouvoir ; analyse des lignes éditoriales selon une méthode publique et chiffrée, jamais par simple appréciation.
- **État des contre-pouvoirs** : tableau de bord régulier (recours au 49.3, suites données aux rapports d'autorités indépendantes, classements de liberté de la presse, interdictions de manifester, etc.).
- **Méthode obligatoire** :
  - uniquement des faits établis par des sources primaires (décisions de justice, Journal officiel, comptes rendus parlementaires, rapports officiels) ou par des enquêtes de presse identifiées ; distinction toujours visible entre ce qui est établi, allégué et en cours d'enquête ;
  - présomption d'innocence : statut de la procédure affiché sur chaque fiche ;
  - vocabulaire normé : « déclaration contredite par les faits » plutôt que « mensonge », sauf décision de justice ;
  - contradictoire : la personne concernée est invitée à réagir avant publication, et sa réaction est publiée ;
  - droit de réponse (LCEN, article 6, IV) publié sous le contenu dans les trois jours ; journal public des corrections ; double validation éditoriale avant publication ;
  - points juridiques à faire valider par un avocat avant la publication de personnes réelles : diffamation, amnistie et réhabilitation, réutilisation des décisions de justice en données ouvertes, droit à l'oubli.
- **Fiches identiques pour tous** : chaque fiche présente les mêmes rubriques dans le même ordre, quelle que soit la personne ; les couleurs décrivent un fait (promesse tenue en vert, contredite en rouge ; « y gagne » en vert, « y perd » en rouge), toujours accompagnées du mot ; les votes restent neutres.
- **Ordre d'entrée des responsables publics** (décision de la propriétaire du 7 novembre 2026) : d'abord les personnes citées dans les contenus publiés, puis les grands partis, puis les personnes les plus médiatisées de ces partis, puis les personnes condamnées en justice, celles qui ont une affaire en cours, celles dont une affaire a été classée sans suite ou s'est terminée par une relaxe. Les mêmes critères s'appliquent à tous les partis.

### M18. Fil social
- **L'écran par défaut de l'application est un fil**, sur le modèle d'un réseau social : le premier réseau social de la vie politique.
- Contenu du fil : publications des comptes, organisations et collectifs que l'on suit (S-120, AG-22) ; contenus suggérés par un algorithme ; publications éditoriales de PPF (MA-03) ; mobilisations, revendications et campagnes du moment.
- Actions : publier (compte requis), partager (S-37), aimer, commenter, signaler (S-38), suivre.
- Navigation : barre du bas fixe sur téléphone à cinq entrées (Fil, En direct, Carte, Messages, Revendications) ; « Mon espace » par l'avatar de la barre du haut ; les piliers Voir, Comprendre, Agir et Décider dans le menu.
- Vues du fil : des onglets sous la barre du haut (Tout, Comptes PPF, Revendications, Mes publications, puis Mon cercle, Collectifs, Suivis, Près de moi avec le bloc communautaire) ; on passe de l'une à l'autre en glissant le doigt de droite à gauche. La vue « Revendications » ne montre que les cards du mur des revendications (M11).
- Règles : la liste des comptes suivis est privée par défaut et reste sur l'appareil ; les réactions ne constituent jamais une liste publique « qui a aimé quoi » par défaut ; les suggestions se calculent sans profil individuel côté serveur et un fil chronologique sans profilage reste toujours disponible (DSA, article 27) ; toute publication est soumise à la charte, à la modération assistée par IA après publication et au signalement par tous. Modalités détaillées : brainstorming du cahier fonctionnel, entrée du 6 novembre 2026.

## Journal des versions

| Version | Date | Modifications |
|---|---|---|
| 1.0 | 4 novembre 2026 | Création à partir des instructions initiales du projet et des échanges sur le bloc « Se politiser ». |
| 1.1 | 6 novembre 2026 | Ajout du module M18 « Fil social » (écran par défaut en fil de réseau social, barre du bas fixe) et de la direction de design des fiches pratiques (encarts « En bref », « Légal », « Illégal »), décisions de la propriétaire du 6 novembre 2026 consignées dans les brainstormings. Copie locale modifiée par l'IA sur demande de la propriétaire ; à reporter dans le projet claude.ai. |
| 1.3 | 6 novembre 2026 | M11 : vues et filtres du mur des revendications, vérifications automatiques des revendications proposées (doublons, loi déjà en vigueur, autres problèmes), demandes de la propriétaire du 6 novembre 2026 (D-029, D-030). Copie locale modifiée par l'IA ; à reporter dans le projet claude.ai. |
| 1.4 | 7 novembre 2026 | Ajout du module M17 « Mettre en lumière » (espace « Voir » : le pouvoir, libertés et contre-pouvoirs) avec sa méthode, les fiches identiques pour tous et l'ordre d'entrée des responsables publics, validés par la propriétaire le 7 novembre 2026 (D-036). Copie locale modifiée par l'IA ; à reporter dans le projet claude.ai. |
| 1.5 | 7 novembre 2026 | M2 : grille de l'indice de gravité des violences policières en cinq niveaux, validée par la propriétaire le 7 novembre 2026 (D-057). Copie locale modifiée par l'IA ; à reporter dans le projet claude.ai. |
| 1.2 | 6 novembre 2026 | Barre du bas à cinq entrées (Fil, En direct, Carte, Messages, Revendications), vues du fil parcourues au doigt dont une vue « Revendications » (M18) ; messagerie confirmée dans les ambitions fonctionnelles avec ses trois vues (M14). Décisions de la propriétaire du 6 novembre 2026 (D-026). Copie locale modifiée par l'IA ; à reporter dans le projet claude.ai. |
