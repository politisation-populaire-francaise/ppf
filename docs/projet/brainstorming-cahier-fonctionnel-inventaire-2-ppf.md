# Brainstorming : cahier fonctionnel, inventaire (2/3) : fonctionnalités PPF et pistes retenues

Suite de l'entrée « Inventaire complet des fonctionnalités » de `docs/brainstorming-cahier-fonctionnel.md`. L'inventaire est réparti en trois documents pour rester lisible : 1. principes et socle applicatif ; 2. fonctionnalités PPF et pistes retenues ; 3. points d'attention. Brouillon : rien n'est validé tant que l'entrée n'a pas le statut « Intégré ».

---

#### Partie B : fonctionnalités PPF (144 fonctionnalités ci-dessous, plus 24 retenues lors du tri)

**Règles communes à toutes les fonctionnalités PPF**
- Chaque donnée affiche sa source, sa date et son niveau de fiabilité (CF 1.0).
- La distinction entre ce qui est établi, allégué ou en cours d'enquête est toujours visible (Brainstorming, M17).
- Chaque contenu se termine par une passerelle « Et maintenant ? » vers une action dans un autre espace (CF 1.0, étendu à tous les espaces par l'entrée « Navigation et UX »).
- Un compte, un vote, un soutien ; seuls les résultats agrégés sont publiés (CF 1.0) ; les votes des comptes non confirmés sont comptés à part (S-91).
- Traitement identique dans les outils électoraux (Pacte démocratique, comparateur, baromètre) : selon la décision sur l'entrée « Conséquences du retrait de la notion de non-partisanerie » (brainstorming-cahier-des-charges.md).
- Tout contenu d'exemple est marqué [EXEMPLE].
- **Origine de chaque contenu** (décision de la propriétaire) : chaque contenu affiche son origine, rédigé par un humain ou produit avec l'aide d'une IA, et, le cas échéant, sa relecture par un membre de l'équipe (par exemple « synthèse réalisée avec l'aide d'une IA à partir de vos contributions, relue par l'équipe »). Le règlement européen sur l'IA (article 50, paragraphe 4, applicable depuis le 2 août 2026) impose de signaler les textes générés par IA publiés pour informer le public sur des questions d'intérêt public, sauf lorsqu'un humain les a relus et en assume la responsabilité éditoriale. Pour PPF, la mention est un choix de transparence, qui protège aussi les synthèses contre les accusations de manipulation.

**B1. En direct (accueil, anciennement « Maintenant »)**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| MA-01 | Chiffres clés | Les trois à cinq indicateurs du moment, avec leurs sources. | CF 1.0 (M2, parcours « Découvrir ») |
| MA-02 | Prochaines mobilisations | Les plus proches dans le temps, et près de chez soi si le territoire est renseigné. | CF 1.0 (M1) |
| MA-03 | Fil d'actualité | Articles, vidéos, podcasts, formats courts, de PPF et des organisations vérifiées, l'auteur de chaque publication étant toujours clairement indiqué. | CF 1.0 (M6), précisé |
| MA-04 | Alertes | Vote imminent au Parlement, interdiction de manifester, échéance d'une pétition, appel urgent. | Brainstorming (Navigation et UX) |
| MA-05 | À la une | La revendication et la campagne prioritaires du moment. | CF 1.0 (parcours « Découvrir ») |
| MA-06 | Journée de mobilisation en direct | Fil en direct pendant une journée de mobilisation, alimenté par l'équipe et les organisations vérifiées : rassemblements, changements de parcours, alertes, chiffres ; lien direct vers la carte de la manifestation (AG-24). | Nouveau |

**B2. Voir : le mouvement**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| VO-01 | Observatoire des chiffres | Indicateurs créés par l'équipe sans code ; liste de départ du CF 1.0 (manifestants, blocages, blessés, interpellations, nasses, MICA, shadowban déclaratif, etc.). Les nasses documentées en direct (AG-46) l'alimentent. | CF 1.0 (M2) |
| VO-02 | Sources côte à côte | Quand les sources divergent, toutes sont affichées, jamais fusionnées en un seul chiffre sans explication. | CF 1.0 (M2) |
| VO-03 | Signalements chiffrés des citoyens | Un compte, un signalement ; détection des comportements anormaux ; validation avant intégration. | CF 1.0 (M2) |
| VO-04 | Indice de gravité des violences policières | Grille à valider. | CF 1.0 (M2) + Brainstorming |
| VO-05 | Visualisations et méthodologie | Compteurs, courbes, cartes ; méthode publique pour chaque indicateur. | CF 1.0 (M2) |
| VO-06 | Chronologie du mouvement | Manifestations, déclarations officielles, décisions publiées, réponses du gouvernement. | CF 1.0 (M4) |
| VO-07 | Archives des documents officiels | Copie, empreinte, horodatage, lien Internet Archive, historique des versions. | CF 1.0 (M4) |
| VO-08 | Témoignages publiés | Galerie et carte des témoignages et vidéos vérifiés par la modération (point d'attention n° 19). | CF 1.0 (M5) |
| VO-09 | International | Carte et chronologie des mouvements sociaux dans le monde : revendications, déroulé, résultats. | CF 1.0 (M9) |

**B3. Voir : le pouvoir**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| VO-10 | Fiche de personne publique | Mandats, fonctions passées, déclarations d'intérêts publiques ; point d'entrée vers toutes les rubriques ci-dessous. | CF 1.0 (M4) + Brainstorming (briques) |
| VO-11 | Votes | Données ouvertes de l'Assemblée nationale et du Sénat, votes des eurodéputés français, puis historique sur 50 ans. | CF 1.0 (M4) + Brainstorming (M17) |
| VO-12 | Promesses | Suivi des engagements électoraux : tenus, en cours, abandonnés, contraires, avec la source de la promesse et celle de l'acte. | Brainstorming (M17) |
| VO-13 | Discours et votes | Votes contraires aux déclarations publiques et aux programmes. | CF 1.0 (M13.2) |
| VO-14 | Affaires et peines | Statut exact de chaque procédure, peine prononcée et peine exécutée, comparaison avec le traitement habituel de la même infraction. | CF 1.0 (M4) + Brainstorming (M17) |
| VO-15 | Déclarations contredites par les faits | Affirmation, date, contexte, faits établis, sources ; lien avec le décryptage des fausses informations. | Brainstorming (M17) |
| VO-16 | Argent public | Atteintes à la probité, rapports de la Cour des comptes et des chambres régionales des comptes, suites données. | Brainstorming (M17) |
| VO-17 | Conflits d'intérêts et pantouflage | Allers-retours entre fonctions publiques et secteur privé, avis de la HATVP, liens documentés entre décisions publiques et intérêts privés. | Brainstorming (M17) |
| VO-18 | Influence des lobbies | Rencontres entre représentants d'intérêts et décideurs sur chaque loi importante (répertoire de la HATVP). | CF 1.0 (M13.2) |
| VO-19 | Qui gagne, qui perd | Effets chiffrés et sourcés des lois importantes sur chaque catégorie de la population. | CF 1.0 (M13.2) |
| VO-20 | Législomètre | Textes en discussion, calendrier des votes, amendements importants, compteur de 49.3, bouton « interpeller mes élus avant le vote ». | Brainstorming |
| VO-21 | Droit de réponse | Bouton « Droit de réponse » en bas de chaque contenu qui nomme ou désigne une personne. Un compte vérifié (élu, organisation, personne publique) rédige directement sa réponse ; toute autre personne nommée ou désignée, y compris sans compte, passe par un formulaire où elle justifie de son identité. Longueur maximale : celle du contenu en cause, avec un minimum garanti de 1 000 mots. Contrôle par le directeur de la publication, puis publication sous trois jours sous le contenu, avec la mention « Droit de réponse de [nom] ». Ouvert aux partis et aux candidats comme à tous (point d'attention n° 7). Obligation légale (LCEN, article 6, IV ; décret n° 2007-1527). | Brainstorming (M17), précisé par la propriétaire |
| VO-22 | Journal des corrections | Liste publique de toutes les corrections apportées, avec leur date et leur motif. | Brainstorming (M17) |

**B4. Voir : libertés et contre-pouvoirs**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| VO-23 | Indépendance de la justice | Modes de nomination, liens avec l'exécutif, interférences établies, rapports qui en traitent. | Brainstorming (M17) |
| VO-24 | À qui appartiennent les médias | Carte des propriétaires et de leurs autres activités ; analyse des lignes éditoriales selon une méthode publique et chiffrée. | Brainstorming (M17) |
| VO-25 | Tableau de bord des contre-pouvoirs | Recours au 49.3, suites données aux rapports d'autorités indépendantes, classements de liberté de la presse, interdictions de manifester. | Brainstorming (M17) |
| VO-26 | Observatoire des libertés publiques | Arrêtés d'interdiction, dissolutions d'associations, lois et décrets restrictifs, dispositifs de surveillance, recours engagés. | Brainstorming |

**B5. Comprendre**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| CO-01 | Qui décide de ça ? | Choisir un sujet du quotidien et son code postal : chaîne de décision, élus concernés, leurs votes, comment les contacter. | CF 1.0 (M13.1) |
| CO-02 | Comprendre en 5 minutes | Parcours courts et quiz sur les institutions ; progression stockée uniquement sur l'appareil. | CF 1.0 (M13.1) |
| CO-03 | Fiches pratiques | Droits en manifestation, interpellation, garde à vue, nasse, filmer les forces de l'ordre, legal teams, premiers secours ; disponibles hors ligne. | CF 1.0 (M6) |
| CO-04 | Décryptage des fausses informations | Affirmation, verdict, explication, sources. | CF 1.0 (M6) |
| CO-05 | Dossiers thématiques | Séparation des pouvoirs, contrôle de la police, libertés publiques, corruption, etc. | CF 1.0 (M6) |
| CO-06 | Histoire des luttes et des révolutions | En France et dans le monde, avec l'analyse de ce qui a fonctionné ou échoué ; périmètre selon la décision sur la non-violence. | CF 1.0 (M7) + Brainstorming |
| CO-07 | Résistance civile stratégique | Grève, boycott, non-coopération, blocage économique, désobéissance civile, facteurs de réussite documentés par la recherche. | CF 1.0 (M7) + Brainstorming (option C) |
| CO-08 | Le droit de résistance à l'oppression en débat | Article 2 de la Déclaration de 1789, article 35 de celle de 1793, contradiction avec le droit pénal, débats historiques. | Brainstorming (option C) |
| CO-09 | Comprendre les élections | Fonctionnement des scrutins, rôle de chaque élu. | CF 1.0 (M13.7) |
| CO-10 | Accès aux archives publiques | Délais légaux de communication et demande de consultation anticipée. | CF 1.0 (M4) |
| CO-11 | Bibliothèque numérique | Œuvres du domaine public et sous licence libre ; liens vers éditeurs et bibliothèques publiques ; aucun contenu piraté. | CF 1.0 (M8) |
| CO-12 | Sécurité numérique des militants | Téléphone en manifestation, chiffrement, réseaux sociaux, protection de ses données. | Brainstorming |
| CO-13 | Ressources pour les enseignants | Contenus réutilisables en classe, strictement pluralistes ; format, niveaux et licence à définir. | Brainstorming |
| CO-14 | Glossaire | Mots de la politique et du droit expliqués simplement, accessibles d'un geste depuis n'importe quel texte. | Nouveau |
| CO-20 | Assistant citoyen IA | Un assistant conversationnel répond aux questions sur les institutions, les droits, les élus et les contenus de PPF, en s'appuyant sur les contenus publiés et sur des sources officielles, toujours citées avec leurs liens. Il annonce dès le premier message qu'il est une IA. Il ne donne jamais de consigne de vote, ne prend pas parti pour un candidat ou un parti et oriente vers les avocats ou les permanences juridiques pour les situations personnelles. Les conversations restent sur l'appareil. Utilisation selon les crédits mensuels (S-138, S-139). Voir le point d'attention n° 34. | Demande de la propriétaire |

**B6. Agir**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| AG-01 | Carte et agenda des mobilisations | Passées et à venir ; filtres par date, lieu, type, organisation ; disponibles hors ligne ; accessibles d'un geste par le bouton « Carte » (S-05). | CF 1.0 (M1) |
| AG-02 | Proposer un événement | Publication directe par les organisations vérifiées, validation par la modération pour les contributeurs. | CF 1.0 (M1) |
| AG-03 | Annuaire des organisations | Associations, avocats bénévoles, legal teams, street medics, collectifs, syndicats ; badge « vérifiée ». | CF 1.0 (M3) |
| AG-06 | Guides pour s'organiser | Créer une association ou un collectif, déclarer une manifestation, organiser une AG, une grève, une campagne d'information. | CF 1.0 (M12) |
| AG-07 | Témoigner | Photo, vidéo, audio, texte ; date et lieu déclarés ; envoi par un canal sécurisé ; vérification et modération avant publication (point d'attention n° 19). | CF 1.0 (M5) |
| AG-08 | Conservation des preuves | Original chiffré, accessible aux seuls avocats partenaires, pour des procédures (phase ultérieure). | CF 1.0 (M5) |
| AG-09 | Canal pour lanceurs d'alerte | Transmission anonyme de documents par un outil auto-hébergé accessible par Tor ; guide de la loi de 2022 ; avis d'avocat préalable. | Brainstorming |
| AG-10 | Solidarité | Caisses de grève, caisses de solidarité juridique, entraide (covoiturage, hébergement). | Brainstorming |
| AG-11 | Boycotts et alternatives | Fiches sourcées : motifs, faits établis, alternatives ; point juridique sur le dénigrement. | Brainstorming |
| AG-12 | Je veux aider | Bénévolat par compétences ; missions publiées par l'équipe. | Brainstorming |
| AG-13 | Mode manifestation | L'essentiel en un geste et hors ligne : droits, numéro de la legal team, premiers secours, que faire en cas d'interpellation. | Brainstorming |
| AG-14 | Soutien aux victimes | Orientation vers l'aide juridique et psychologique ; fiche « après une blessure en manifestation ». | Brainstorming |
| AG-15 | Matériel militant | Tracts, affiches et visuels à télécharger et imprimer, personnalisables avec son territoire et la date d'une mobilisation. | Nouveau |
| AG-16 | Messagerie hors ligne | Bluetooth de proche en proche, chiffrée de bout en bout, compatible bitchat, sans lien avec le serveur. | CF 1.0 (M14) |

L'ancienne fonctionnalité AG-04 « Groupes locaux » est remplacée par les collectifs (AG-47 à AG-53, et A22 pour leur fonctionnement) ; son identifiant n'est pas réutilisé.

**Espace des organisations vérifiées** : associations, syndicats, collectifs et legal teams publient directement sur PPF, qui devient le point de rendez-vous unique au lieu d'une information éparpillée sur des dizaines de sites et de réseaux sociaux.

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| AG-17 | Page de l'organisation | Page publique de chaque organisation vérifiée : présentation, contacts, publications, calendrier, campagnes en cours. | Nouveau (étend CF 1.0, M3) |
| AG-18 | Publications des organisations | Articles, communiqués, appels à mobilisation, fiches pratiques, publiés sous le nom de l'organisation et signés de sa clé ; repris dans le fil « En direct », la recherche et Mon espace, avec la mention « publié par [organisation] ». Publication directe pour les organisations vérifiées depuis un certain temps et sans incident, relecture par l'équipe pour les nouvelles (point d'attention n° 6, décidé). | CF 1.0 (M3), précisé |
| AG-19 | Calendrier de l'organisation | Chaque organisation gère son propre calendrier ; ses événements alimentent automatiquement la carte et l'agenda communs (AG-01). | CF 1.0 (M1 et M3), précisé |
| AG-20 | Import des agendas existants | Une organisation qui a déjà un agenda en ligne (flux iCal ou RSS) le relie une fois pour toutes : aucune double saisie. | Nouveau |
| AG-21 | Événements co-organisés | Un événement porté par plusieurs organisations n'apparaît qu'une seule fois, avec tous ses organisateurs ; détection des doublons. | Nouveau |
| AG-22 | Suivre une organisation | Abonnement à ses publications et à son calendrier, avec notifications. Par défaut, la liste des organisations suivies reste uniquement sur l'appareil (et dans la sauvegarde chiffrée, S-112) : suivre un syndicat peut révéler une appartenance syndicale, donnée sensible au sens du RGPD. L'utilisateur peut choisir de l'afficher sur son profil, après une mise en garde (principe de libre choix, point d'attention n° 32). | Nouveau |

**Collectifs** : groupes et clubs créés par les citoyens, de la portée nationale à celle d'un quartier ou d'un établissement (fonctionnement : A22, S-129 à S-136). Ils remplacent les groupes locaux (ancien AG-04). Points d'attention n° 26 à 31.

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| AG-47 | Trouver et rejoindre un collectif | Recherche par nom, thème, type, portée et territoire ; « près de chez moi » calculé sur l'appareil à partir du code postal ; filtres selon le mode d'adhésion (ouverte ou sur demande) ; demande d'adhésion en un geste. Les collectifs secrets n'apparaissent jamais. | Demande de la propriétaire |
| AG-48 | Page du collectif | Page de profil du collectif, publique ou réservée aux membres selon sa visibilité : présentation, portée, publications, calendrier, appels et tout type de contenu de la plateforme (articles, photos, vidéos, documents, sondages). | Demande de la propriétaire |
| AG-49 | Calendrier du collectif | Événements internes (réservés aux membres) ou publics ; les événements publics alimentent la carte et l'agenda communs (AG-01) ; réponse « J'y serai » ; rappels. | Demande de la propriétaire |
| AG-50 | Appels du collectif | Le collectif lance des appels en son nom (AP-01 à AP-10), publics ou réservés aux membres ; les appels publics passent par la modération assistée par IA (S-137, point d'attention n° 29). | Demande de la propriétaire |
| AG-51 | Décider ensemble | Votes, sondages et assemblées générales internes, avec les outils de démocratie interne (DE-13) : vote anonyme possible, tirage au sort, mandats courts et révocables. | CF 1.0 (M13.4), appliqué aux collectifs |
| AG-52 | Alliances entre collectifs | Collectifs et organisations s'associent : salon commun chiffré, événements et appels co-organisés (AG-21), page de coordination (par exemple une coordination départementale). | Nouveau |
| AG-53 | Du collectif à l'association | Accompagnement pour passer d'un collectif informel à une association déclarée (guides AG-06), puis demande de vérification (S-81) pour devenir organisation vérifiée. | Nouveau |

**Appels** : n'importe quel compte peut lancer un appel, c'est-à-dire tout ce qu'un militant peut vouloir proposer aux autres. Les appels remplacent et élargissent l'ancienne fonctionnalité « Proposer une action » (AG-05, CF 1.0, M12).

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| AP-01 | Lancer un appel | Choix du type : se réunir, débattre, s'organiser, manifester, soutenir une cause, signer une pétition, interpeller des élus, boycotter, collecter des fonds, chercher des bénévoles, faire circuler une information, appel à témoins ou à témoignages, autre. Puis titre, texte, objectif chiffré facultatif, échéance, lieu facultatif, lien, et les champs obligatoires du type choisi (AP-10). Un modèle guidé par type aide à bien rédiger. | Nouveau (reprend et élargit AG-05) |
| AP-02 | Publication et modération | Publication directe pour les comptes confirmés et les organisations vérifiées ; modération préalable pour les comptes nouveaux ; les appels à témoins passent toujours par la modération, quel que soit le niveau du compte ; détection des appels en double, comme pour les revendications (DE-18). | Nouveau |
| AP-03 | Répondre à un appel | Bouton adapté au type (« J'y serai », « Je soutiens », « J'ai signé », « J'aide », « Je témoigne ») ; le compteur est public, la liste des personnes qui ont répondu ne l'est jamais. | Nouveau |
| AP-04 | Objectif et progression | Barre de progression vers l'objectif, compte à rebours avant l'échéance ; les participants sont prévenus quand l'objectif est atteint ou que l'appel change (lieu, horaire). | Nouveau |
| AP-05 | Mur des appels | Cards interactives filtrables par type, territoire, échéance et popularité ; vue sur la carte pour les appels qui ont un lieu. | Nouveau |
| AP-06 | Discussion de l'appel | Chaque appel publié ouvre un salon de discussion (S-82) pour s'organiser. | Nouveau |
| AP-07 | Faire grandir un appel | Le transformer en événement sur la carte (AG-01), en revendication (DE-01) ou le proposer comme campagne (DE-04) ; kit de partage. | Nouveau (reprend AG-05) |
| AP-08 | Appels à collecter des fonds | Réservés aux organisations vérifiées ; l'appel renvoie vers leur propre page de collecte ou vers leur page HelloAsso ; PPF ne manipule jamais l'argent (point d'attention n° 12, décidé). | Nouveau, décidé |
| AP-09 | Clôture et bilan | À l'échéance, l'auteur publie un bilan (ce qui a été obtenu, combien de personnes ont répondu) ; les appels clos restent consultables. | Nouveau |
| AP-10 | Champs obligatoires selon le type | Chaque type d'appel impose les informations nécessaires pour le traiter efficacement ; l'appel ne peut pas être publié tant qu'elles manquent (détail ci-dessous). | Nouveau (demande de la propriétaire) |

Champs obligatoires proposés par type d'appel (AP-10) :

| Type d'appel | Champs obligatoires |
|---|---|
| Se réunir, débattre, s'organiser | Objet, date et heure, lieu ou lien de réunion en ligne, organisateur, accessibilité du lieu |
| Manifester | Date et heure, lieu de départ, parcours s'il est connu, organisateurs, déclaration en préfecture (faite, en cours ou non faite) |
| Soutenir une cause | Cause, action concrète demandée, échéance |
| Signer une pétition | Lien de la pétition, plateforme (officielle ou non), objectif de signatures, échéance |
| Interpeller des élus | Élus visés, demande précise, échéance (par exemple la date d'un vote) |
| Boycotter | Entreprise ou produit, motif appuyé sur des faits sourcés, alternatives, durée |
| Collecter des fonds | Bénéficiaire, objectif chiffré, usage prévu des fonds, lien de la page de collecte, échéance |
| Chercher des bénévoles | Mission, compétences recherchées, nombre de personnes, lieu ou à distance, période |
| Faire circuler une information | Source de l'information (lien ou document) |
| Appel à témoins ou à témoignages | Date et plage horaire, lieu précis, description factuelle de l'événement sans accuser nommément personne, type d'éléments recherchés (photos, vidéos, récits), destinataire des témoignages (organisation, legal team ou avocat). Les témoignages arrivent par le canal sécurisé de AG-07 pour être vérifiés et anonymisés (métadonnées supprimées, visages des tiers et des victimes floutés sauf accord), puis sont publiés dans la galerie et sur la carte des témoignages (VO-08), avec un lien depuis l'appel. |

**Secours, sécurité et défense en manifestation** (retenu par la propriétaire le 2026-11-04) : protéger les manifestants grâce à une coordination en temps réel des secours, de la sécurité et de la défense juridique. Règles de ce bloc :
- jamais de position ni de mouvement des unités des forces de l'ordre ; la carte montre les dangers pour les personnes (zones de gaz, de charges, de tirs) et les passages pour se mettre à l'abri, avec l'heure du signalement ;
- la position d'un blessé et la gravité de sa blessure sont transmises aux street medics certifiés concernés, chiffrées, et effacées automatiquement à la clôture de l'alerte ;
- PPF ne remplace jamais les secours publics : en cas d'urgence vitale, l'application propose d'abord d'appeler le 15 ou le 112 ;
- les bénévoles (street medics, équipe citoyenne de sécurité) informent, soignent et guident ; ils n'ont aucun pouvoir de police et ne s'interposent jamais physiquement.

Voir les points d'attention n° 17, n° 18 et n° 24.

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| AG-24 | Carte de la manifestation en direct | Tracés des parcours, horaires, points de rendez-vous et de dispersion, street medics disponibles à proximité (selon le point d'attention n° 17), zones dangereuses (AG-42) et sorties (AG-43) ; zone téléchargeable à l'avance pour fonctionner sans réseau (S-35). Aucune position des forces de l'ordre. Ouverte directement par le bouton « Carte » quand une manifestation est en cours. | Piste retenue et précisée par la propriétaire |
| AG-27 | Bouton « AIDE » | Ouvert à tous, avec ou sans compte. En un geste, la personne blessée ou un proche envoie sa position aux street medics certifiés disponibles à proximité et indique la gravité sur une échelle simple : 1. léger (peut marcher, petite plaie, irritation aux gaz) ; 2. sérieux (saignement important, fracture possible, malaise) ; 3. urgence vitale (inconscience, difficulté à respirer, hémorragie massive), qui propose d'abord d'appeler le 15 ou le 112. La personne voit ensuite « un medic arrive » et sa distance. Proposition : le même bouton propose aussi « J'ai besoin d'aide pour sortir » (AG-44) et « Besoin d'un avocat » (AG-31), pour n'avoir qu'un seul bouton à retenir. Aucune limite du nombre d'alertes par téléphone (décision de la propriétaire, point d'attention n° 17). | Nouveau (demande de la propriétaire) |
| AG-28 | Street medics certifiés | Compte « street medic » certifié en personne par QR code (S-88) ou par un représentant certifié (organisation vérifiée, syndicat, élu). Niveau de compétence affiché de 1 à 5 : 1. premiers secours (formation PSC1) ; 2. secouriste (PSE1, PSE2) ; 3. professionnel paramédical (infirmier, ambulancier) ; 4. médecin ; 5. médecin urgentiste ou chirurgien. Le niveau est déclaré et confirmé par celui qui certifie ; aucun diplôme n'est stocké. | Nouveau (demande de la propriétaire) |
| AG-29 | Prise en charge coordonnée | Un street medic répond « J'y vais » ; jusqu'à 5 medics peuvent répondre (nombre réglable selon la gravité), puis l'alerte passe « prise en charge » et disparaît des autres écrans. Le medic passe automatiquement « occupé », puis « disponible » quand il clôt l'intervention. Les alertes non prises en charge remontent en priorité. | Nouveau (demande de la propriétaire) |
| AG-30 | Tableau de coordination des secours | Réservé aux street medics et aux coordinateurs : carte des medics (croix verte pour disponible, croix rouge pour occupé, niveau de compétence dans le coin supérieur), alertes en cours par gravité, zones qui concentrent les alertes et zones sans medic, pour redéployer les équipes. | Nouveau (demande de la propriétaire) |
| AG-31 | Alerte « Besoin d'un avocat » | Une personne interpellée, ou un proche en son nom (le téléphone de la personne gardée à vue lui est souvent retiré), demande un avocat bénévole en indiquant la ville et, si possible, le lieu de garde à vue ; la demande est transmise aux avocats bénévoles disponibles de cette ville. Le premier qui accepte prend le dossier ; les échanges sont chiffrés de bout en bout et effacés après la prise en charge. | Nouveau (demande de la propriétaire) |
| AG-32 | Avocats bénévoles certifiés | Compte « avocat » certifié par une organisation vérifiée partenaire (association d'avocats, legal team, syndicat) après vérification de l'inscription au barreau ; ville d'exercice, disponibilité (disponible, occupé, absent), domaines (garde à vue, comparution immédiate, droit des étrangers, etc.). | Nouveau (demande de la propriétaire) |
| AG-41 | Regroupement des alertes simultanées | Quand plusieurs alertes partent presque au même endroit et au même moment (environ 15 mètres et 2 minutes, réglables par la coordination, point d'attention n° 17), l'application n'en envoie qu'une aux medics : la plus grave selon l'échelle, qui devient prioritaire. Une fois prise en charge, les autres alertes du groupe apparaissent sur l'écran des medics sous forme de petits points rouges clignotants. Les medics sur place vérifient d'un coup d'œil : « Valider » relance un appel aux medics proches pour cette alerte ; « Invalider » la clôt avec un motif : rien à signaler, situation gérée, même blessé, fausse alerte, autre. Aucune alerte n'est jamais supprimée sans qu'un medic l'ait vue. | Nouveau (demande de la propriétaire) |
| AG-42 | Signaler une zone dangereuse | Tout participant signale sur la carte une zone où les personnes sont en danger : gaz lacrymogène, charge, tirs de grenades ou de LBD, mouvement de foule, encerclement en train de se former (nasse). Chaque signalement affiche son heure et le nombre de confirmations ; il se renforce quand d'autres le confirment (priorité aux street medics et à l'équipe de sécurité) et disparaît automatiquement s'il n'est pas confirmé (par exemple après 15 minutes). On signale le danger, jamais la position des unités. | Nouveau (demande de la propriétaire) |
| AG-43 | Sorties de la manifestation | Carte des sorties possibles avec leur état : ouverte, fermée, contrôlée (sortie filtrée), inconnue ; heure de la dernière confirmation et personne qui confirme (membre de l'équipe de sécurité sur place, street medic, participant). Bouton « M'y guider » vers la sortie ouverte confirmée la plus proche. | Nouveau (demande de la propriétaire) |
| AG-44 | Aide pour sortir | Une personne vulnérable (personne âgée, avec un bébé ou un enfant, en situation de handicap, blessée, prise de panique, attendue à un rendez-vous médical), ou quelqu'un qui la voit, demande de l'aide pour quitter la zone. Les bénévoles proches (équipe de sécurité, puis participants volontaires) répondent « J'y vais », comme pour les medics, et la guident vers une sortie ouverte confirmée. | Nouveau (demande de la propriétaire) |
| AG-45 | Équipe citoyenne de sécurité | Bénévoles certifiés comme les street medics (S-88 ou représentant certifié), formés au préalable. Rôles : **veilleur de sortie** (posté à une sortie, il en tient l'état à jour), **guide** (accompagne les personnes qui ont besoin de sortir), **coordinateur** (vue d'ensemble, en lien avec les street medics, les legal teams et les observateurs, AG-40). Leurs positions ne sont visibles que par la coordination ; tableau de coordination commun avec AG-30. | Nouveau (demande de la propriétaire) |
| AG-46 | Constat en direct aux sorties | Le veilleur photographie ou filme la sortie (horodatage, position, empreinte, métadonnées retirées, S-36) ; diffusion en direct vers la coordination et les legal teams quand le réseau le permet, sinon envoi différé ou par Bluetooth (AG-39) ; conservation comme preuve (AG-08) ; publication après vérification (VO-08). Le veilleur peut montrer sur place aux forces de l'ordre que la sortie annoncée est fermée, pour demander l'ouverture d'un passage pour les personnes vulnérables. L'historique de l'état des sorties produit automatiquement un constat de nasse (début, fin, durée, sorties ouvertes ou non) qui alimente l'observatoire (VO-01). | Nouveau (demande de la propriétaire) |

**B7. Décider**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| DE-01 | Mur des revendications | Chaque revendication publiée est une card interactive qui se lit d'un coup d'œil : la revendication en une ou deux phrases, son thème et son territoire, les votes pour et les votes contre, le baromètre politique des votes (PR-07), le nombre de participants au débat, son statut (nouvelle, en débat, très soutenue, retenue dans le Programme du peuple, devenue campagne) et, le cas échéant, le badge de consensus (DE-21). On vote pour ou contre directement sur la card. Tri par soutien, nouveauté, débat ou consensus ; filtres par thème et territoire ; regroupement par thème assisté par IA et validé par l'équipe ; synthèse publique. | CF 1.0 (M11), précisé |
| DE-18 | Proposer une revendication | Formulaire guidé (une revendication = une idée, formulée simplement). Pendant la saisie, le système affiche les revendications qui disent la même chose et propose de soutenir l'existante plutôt que d'en créer une nouvelle. Modération avant publication. | CF 1.0 (M11), précisé |
| DE-19 | Fusion des doublons | Détection automatique des revendications proches par leur sens et non seulement par les mots (modèle d'IA auto-hébergé) ; fusion proposée à l'équipe et validée par un humain ; les votes sont réunis sans compter deux fois la même personne. | Nouveau |
| DE-20 | Espace de débat | Ouvert automatiquement pour chaque revendication publiée. Ordre d'affichage toujours identique : **la card et ses informations, puis la synthèse IA des arguments, puis les commentaires** (décision de la propriétaire). La synthèse, assistée par IA, validée par l'équipe et marquée comme telle, est tenue à jour. Chaque commentaire porte obligatoirement une étiquette **Pour**, **Contre** ou **Mitigé** ; compteur par étiquette, filtres par étiquette, réponses ; les arguments jugés utiles par les participants remontent. Règles de débat affichées, modération. | Nouveau, précisé par la propriétaire |
| DE-21 | Consensus au-delà des clivages | Répartition des votes selon le baromètre politique (PR-07), la tranche d'âge, le sexe et la région ; badge « soutenue par tous les bords » quand une majorité de votes pour est atteinte à gauche, au centre et à droite, chacun avec assez de votes. Seuls les votes de comptes certifiés comptent. Dépend du questionnaire citoyen anonyme (PR-01 à PR-07). | Nouveau |
| DE-02 | Cahiers de doléances | Par territoire, synthèse validée par l'équipe et publiée. | CF 1.0 (M13.3) |
| DE-03 | Programme du peuple | Synthèse nationale des revendications prioritaires, votée et versionnée. | CF 1.0 (M13.3) + Brainstorming (déroulé) |
| DE-04 | Campagnes pour de nouveaux droits | Choisies par vote ; fiche de campagne (droit actuel, texte à modifier, propositions déjà déposées, réponses aux objections, chemin vers la réforme). | CF 1.0 (M13.5) + Brainstorming (modalités) |
| DE-05 | Soutenir une campagne | Compteur de soutiens par territoire. | CF 1.0 (M13.5) |
| DE-06 | Baromètre des élus | Par campagne : engagé, pour, contre, abstention, sans réponse. | CF 1.0 (M13.5) |
| DE-07 | Pétitions officielles | Relais des pétitions de l'Assemblée, du Sénat, de l'initiative citoyenne européenne et des RIP ouverts ; seuils expliqués ; objectifs par territoire et suivi des 30 départements. | CF 1.0 (M10) |
| DE-08 | Pacte démocratique | Liste votée, envoi à tous les candidats, tableau public des réponses, archivage horodaté, suivi après l'élection. | CF 1.0 (M13.5) + Brainstorming (liste) |
| DE-09 | Interpeller ses élus | Modèles de courrier à personnaliser ; suivi public des réponses reçues ou non. | CF 1.0 (M13.1) |
| DE-10 | Participer à la démocratie existante | Listes électorales, procuration, assesseur, budgets participatifs, consultations et enquêtes publiques, conseils de quartier. | CF 1.0 (M13.1) |
| DE-11 | S'engager en politique | Guides : créer un collectif, une association politique, une liste citoyenne, un parti. | CF 1.0 (M13.4) |
| DE-12 | Kit du candidat citoyen | Éligibilité, déclaration, mandataire, compte de campagne, seuils, budget, calendrier, mode « dissolution », carte des circonscriptions. | CF 1.0 (M13.4) |
| DE-13 | Démocratie interne des groupes | Votes, AG en ligne, tirage au sort, mandats courts et révocables, rotation des responsabilités. | CF 1.0 (M13.4) |
| DE-14 | Votations citoyennes | Grandes questions soumises au vote, sans valeur légale ; dépend de la méthode « une personne, une voix ». | CF 1.0 (M13.6) |
| DE-15 | Assemblées citoyennes tirées au sort | Tirage parmi des volontaires, information contradictoire, rapport public. | CF 1.0 (M13.6) |
| DE-16 | Comparateur présidentielle 2027 | Programmes et votes passés des candidats et des partis, présentés de la même façon pour tous. | CF 1.0 (M13.7) |
| DE-17 | Désignation citoyenne d'un candidat | Phase 6, module isolé, avis d'avocat préalable. | CF 1.0 (M13.7) + Brainstorming |

**B8. Mon espace (anciennement « Mon territoire »)**

Tableau de bord personnel, accessible depuis la barre du haut et personnalisable par l'utilisateur. Tout ce qui est personnel (territoire, suivis, votes) est calculé et stocké sur l'appareil ; la sauvegarde entre appareils est facultative et chiffrée avec la clé de l'utilisateur (S-112). Le code postal n'est jamais envoyé au serveur.

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| TE-01 | Mes élus | Tous ses élus, de la commune au Parlement européen selon les données publiques disponibles, avec leurs votes et leurs engagements. | CF 1.0 (M13.1) |
| TE-02 | Près de chez moi | Mobilisations, groupes locaux, organisations. | Brainstorming (Navigation et UX) |
| TE-03 | Ce que veut mon territoire | Revendications et cahier de doléances locaux. | Brainstorming (Navigation et UX) |
| TE-04 | Ma circonscription | Résultats des élections passées, députés successifs, candidats déclarés. | Nouveau (reprend la carte du kit du candidat) |
| TE-05 | Tableau de bord personnalisable | L'utilisateur compose son espace avec des blocs qu'il ajoute, déplace ou retire : mon territoire (TE-01 à TE-04), pétitions suivies, chiffres suivis, contenus enregistrés, campagnes, organisations suivies, appels auxquels j'ai répondu, prochaines mobilisations, mes échanges avec l'équipe (S-105). Disposition par défaut proposée au premier usage. | Nouveau (demande de la propriétaire) |
| TE-06 | Mes votes et mes suivis | La liste de tous ses votes et soutiens (revendications, campagnes, votations, appels), avec l'évolution des résultats, l'activité des débats et les changements de statut ; notifications facultatives. Partage volontaire d'un vote ou d'un suivi avec ses cercles (TE-07). Reprend la piste S-99 « Mon espace d'engagement ». Voir le point d'attention n° 25. | Nouveau (demande de la propriétaire) |
| TE-07 | Partager avec mes cercles | Partager un vote, un appel, une revendication ou un contenu avec un ou plusieurs cercles choisis (S-118), dans leur discussion chiffrée (S-124) ; toujours volontaire, au cas par cas, jamais automatique ; l'utilisateur peut aussi rendre publics ses votes et ses suivis s'il le souhaite, après une mise en garde (principe de libre choix, point d'attention n° 32). | Demande de la propriétaire |

**B9. À propos de PPF**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| PP-01 | Transparence de PPF | Comptes annuels, décisions importantes, fonctionnement des instances, rapport de modération, bilan annuel des réclamations (S-110). | Brainstorming |
| PP-02 | Notre méthode | Comment PPF choisit ses sources, vérifie, distingue l'établi de l'allégué et corrige ses erreurs. | Nouveau |
| PP-06 | Trésorerie publique | Onglet public où chaque mouvement d'argent est publié rapidement (sous 48 heures) avec sa date, son montant, sa catégorie et, selon le cas, sa provenance ou sa destination : entrées (dons, cotisations, subventions), sorties (fonctionnement, hébergement, frais juridiques) et redistributions (caisses de grève, frais d'avocat, aide aux victimes), avec le justificatif quand c'est possible. Solde, graphiques et export des données. Les particuliers, donateurs comme bénéficiaires, ne sont jamais nommés (point d'attention n° 15). | Nouveau (demande de la propriétaire) |
| PP-07 | Tableau de bord fédérateur | Chiffres qui montrent la force du mouvement, mis à jour en continu : adhérents, comptes actifs, revendications, votes, appels lancés et réponses, organisations membres, collectifs créés, signatures obtenues, engagements de candidats, argent collecté et redistribué. Chaque chiffre est consultable sur toute la période, l'année, le mois, la semaine et le jour. | Nouveau (demande de la propriétaire ; reprend la piste PP-05) |
| PP-08 | Adhérer à PPF | Adhésion à l'association et paiement de la cotisation, avec un registre des adhérents séparé de la plateforme (point d'attention n° 16). | Nouveau (à étudier) |

**B10. Outils de l'équipe propres à PPF**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| EQ-01 | Gestion des indicateurs | Création d'indicateurs sans code ; validation des signalements chiffrés des citoyens. | CF 1.0 (M2) |
| EQ-02 | Import des données publiques | Assemblée nationale, Sénat, Parlement européen, HATVP, Journal officiel, résultats électoraux. | CF 1.0 (M4) |
| EQ-03 | Double validation et contradictoire | Pour tout contenu de « Voir : le pouvoir » : deux validations et demande de réaction à la personne concernée avant publication. | Brainstorming (M17) |
| EQ-04 | Suivi des droits de réponse | Délai de trois jours affiché, alerte avant échéance. | Brainstorming (M17) |
| EQ-05 | Synthèse des revendications | Regroupement assisté par IA, validation, rédaction des synthèses et des cahiers de doléances. | CF 1.0 (M11) |
| EQ-06 | Envoi du Pacte et suivi des réponses | Même texte et même délai pour tous les candidats, archivage horodaté des réponses. | CF 1.0 (M13.5) |
| EQ-07 | Vérification des organisations | Examen des demandes (S-81), contrôle de l'existence légale, attribution, suspension et retrait du badge « vérifiée ». | CF 1.0 (M3), précisé |
| EQ-08 | Votes de priorisation | Organisation des votes sur les campagnes, nombre de campagnes actives, archivage des résultats. | Brainstorming (modalités du choix des campagnes) |

**B11. Questionnaire citoyen anonyme (facultatif)**

But : montrer qu'une revendication est partagée par des citoyens de tous les âges, de tous les territoires et de tous les bords, ce qui lui donne un poids politique bien plus grand, et nourrir l'observatoire politique. Principe retenu par la propriétaire : aucun profil n'est stocké ; chacun répond, s'il le souhaite, au moment de chaque vote, comme à un formulaire anonyme. Voter ne demande de remplir aucun champ. Voir les points d'attention n° 9 et n° 13.

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| PR-01 | Questionnaire au moment du vote | Après un vote (revendication, votation), un court formulaire facultatif : tranche d'âge, sexe (femme, homme, non binaire, « je préfère ne pas répondre »), département, bord politique sur le baromètre (PR-07). Chaque champ peut rester vide ; le vote compte de la même façon. La réponse « moins de 15 ans » à la tranche d'âge clôt le questionnaire : seules les personnes qui déclarent 15 ans ou plus remplissent les autres champs. Aucune question sur la religion, l'orientation sexuelle ou l'identité de genre. | Nouveau (précisé par la propriétaire) |
| PR-02 | Consentement explicite | Écran d'explication affiché la première fois : à quoi servent ces réponses, comment elles sont protégées, rappel que voter ne les exige pas ; accord par un geste clair. | Nouveau |
| PR-03 | Réponses anonymes et certification | Les réponses partent avec le vote, accompagnées d'une seule information sur leur auteur : compte certifié (niveau « confirmé » ou « de confiance », S-86) ou non. Le serveur ne peut relier ni le vote ni les réponses à un compte (jeton anonyme, point d'attention n° 9, décidé). | Nouveau (précisé par la propriétaire) |
| PR-04 | Statistiques protégées | Répartitions affichées par grandes catégories (tranches d'âge, régions, positions du baromètre) et seulement si chaque groupe compte assez de personnes (seuil minimal, par exemple 50), sinon la case affiche « pas assez de votes », pour que personne ne puisse être reconnu ; les réponses « je préfère ne pas répondre » apparaissent comme « non renseigné » ; mention « données déclaratives » toujours visible. | Nouveau (libellés retenus par la propriétaire) |
| PR-05 | Afficher son bord politique | Option désactivée par défaut, réservée aux personnes qui déclarent 15 ans ou plus : afficher sa position sur le baromètre (PR-07) sur son profil public de pseudonyme ; retirable à tout moment ; jamais reliée aux votes de la personne par la plateforme (principe de libre choix, point d'attention n° 32). | Nouveau (demande de la propriétaire) |
| PR-06 | Mémorisation pendant la session | Sur le téléphone comme sur l'ordinateur, les dernières réponses sont gardées dans la mémoire de l'appareil pendant la session en cours, pour ne pas tout ressaisir à chaque vote. Elles ne sont jamais enregistrées durablement ni envoyées au serveur, et s'effacent à la fermeture de l'application ou de l'onglet, ou après une période d'inactivité (durée à fixer, par exemple 30 minutes) ; bouton pour les effacer à tout moment. | Nouveau (retenu par la propriétaire) |
| PR-07 | Baromètre politique | **Pour se situer** : un baromètre à trois colonnes, gauche, centre et droite. À gauche et à droite, trois hauteurs : en haut, proche de l'extrême ; au milieu, « classique » ; en bas, proche du centre. Le centre est une seule case. Hors du baromètre : « Je ne me reconnais pas dans ce clivage » et « Je préfère ne pas répondre ». **Pour lire une card** : le même baromètre, avec dans chaque case le pourcentage de votes pour et le nombre de votants de la case, placés à la bonne hauteur ; « hors clivage » et « non renseigné » sous le baromètre ; pas de moyenne, qui effacerait les différences entre les groupes (décision de la propriétaire). | Nouveau (décision de la propriétaire) |

#### Fonctionnalités retenues lors du tri du 2026-11-04 (30)

La propriétaire a retenu les 32 pistes proposées ; la piste S-99 a ensuite été fusionnée dans TE-06, et la piste S-100 dans S-116. La priorité indiquée est indicative : « tôt » signifie dans les premiers MVP (simple et utile tout de suite) ; « plus tard » signifie lourd ou dépendant d'autre chose. Elle sera arrêtée lors du découpage en MVP. Ces fonctionnalités rejoindront leur bloc lors de la réécriture du cahier fonctionnel en version 2.0.

**Socle (6)**

| ID | Fonctionnalité | Description | Pourquoi | Priorité |
|---|---|---|---|---|
| S-94 | Aide et questions fréquentes | Page d'aide accessible depuis la barre du haut, réponses aux questions courantes, petite visite guidée des espaces. | Indispensable pour un public large, pas toujours à l'aise avec le numérique. | Tôt |
| S-95 | Lecture à voix haute | Synthèse vocale intégrée pour écouter n'importe quel article ou fiche, en s'appuyant sur celle du téléphone. | Accessibilité pour les personnes malvoyantes, dyslexiques ou peu à l'aise avec l'écrit. | Tôt |
| S-96 | Widget d'écran d'accueil | Petit encart sur l'écran du téléphone : prochaine mobilisation, chiffre clé, alerte. | Garder le lien sans ouvrir l'application. | Plus tard |
| S-97 | Partager l'application sans Internet | Transmettre l'application elle-même et les fiches essentielles d'un téléphone à l'autre, par Bluetooth ou QR code. | Résistance à la censure : si l'application est retirée des magasins ou si le réseau est coupé. | Tôt |
| S-98 | Rappels personnels | « Me rappeler cet événement » ou « cette échéance », programmés uniquement sur l'appareil. | Pratique, sans rien confier au serveur. | Tôt |
| S-101 | Rapports de plantage volontaires | En cas de plantage, proposer d'envoyer un rapport technique anonyme, uniquement avec l'accord de l'utilisateur. | Corriger les bugs sans aucun outil de suivi. | Tôt |

La piste S-99 « Mon espace d'engagement » est fusionnée dans TE-06 et la piste S-100 « Inviter un proche » dans S-116 ; leurs identifiants ne sont pas réutilisés.

**Fonctionnalités PPF (24)**

| ID | Fonctionnalité | Description | Pourquoi | Priorité |
|---|---|---|---|---|
| VO-27 | Carte des fermetures de services publics | Maternités, services d'urgence, bureaux de poste, classes, gares fermés ou menacés, à partir des données ouvertes et de signalements citoyens vérifiés. | Rend visible, près de chez soi, le recul des services publics décrit dans le constat. | Plus tard |
| VO-28 | Observatoire de la vie chère | Évolution des prix des carburants, de l'énergie, des loyers et d'un panier alimentaire, à partir de données ouvertes. | Le mouvement est né en partie de la vie chère ; les chiffres le documentent. | Plus tard |
| VO-29 | État du pays | Tableau de bord des chiffres du constat de la vision (pauvreté, fortunes, lits d'hôpital, effectifs par classe, etc.), mis à jour avec leurs sources. | Le constat devient vivant et vérifiable. | Tôt |
| CO-15 | Ce que cette loi change pour moi | Simulateur personnel (retraite, chômage, impôts) calculé uniquement sur l'appareil, sans rien envoyer. | Passer du « qui gagne, qui perd » général à sa propre situation. | Plus tard (une erreur de calcul nous décrédibiliserait) |
| CO-16 | Où vont nos impôts | Budget de l'État et de sa commune en visualisation simple et interactive. | Comprendre les choix budgétaires dont dépendent les services publics. | Plus tard |
| CO-17 | Et si on votait autrement ? | Résultats d'élections réelles recalculés avec le vote blanc reconnu, la proportionnelle ou le jugement majoritaire. | Montre concrètement l'enjeu des campagnes pour de nouveaux droits démocratiques. | Plus tard, mais avant les élections |
| CO-18 | Demander une vérification | Soumettre une rumeur ou une information douteuse à l'équipe de décryptage (CO-04). | Lutter contre les fausses informations à partir de ce qui circule vraiment. | Tôt |
| CO-19 | Parcours « Je découvre » | Une micro-leçon par jour pendant sept jours pour les nouveaux venus, avec notifications facultatives. | Politiser pas à pas ceux qui n'ont jamais eu d'éducation civique concrète. | Plus tard (les leçons sont à écrire) |
| AG-23 | Alerte à mes proches | En un geste, envoyer un message préparé à l'avance (et sa position, si on le veut) aux cercles choisis, par exemple la famille proche (S-118, S-119), par la messagerie chiffrée de bout en bout, ou par SMS à des contacts qui n'utilisent pas PPF ; message « je suis bien rentré » à la fin. Complète le bouton AIDE (AG-27) et l'alerte avocat (AG-31). | Prévenir ses proches en cas d'interpellation ou de blessure. | Tôt |
| AG-25 | Trouver une salle | Lieux disponibles pour se réunir (bourses du travail, locaux associatifs, salles municipales), proposés par les organisations. | Le manque de lieu est un frein fréquent pour s'organiser. | Plus tard |
| AG-26 | Permanences juridiques | Prise de rendez-vous avec les avocats et juristes bénévoles des organisations partenaires, en dehors des urgences (AG-31). | Accès concret au droit pour les personnes poursuivies ou victimes. | Plus tard |
| AG-33 | Points utiles autour de la manifestation | Points d'eau, toilettes, pharmacies et services d'urgence les plus proches, lieux refuges (locaux syndicaux ou associatifs ouverts, commerces solidaires). | Se protéger et se ravitailler. | Tôt (avec la carte) |
| AG-34 | Accessibilité du parcours | Portions accessibles en fauteuil, cortège adapté, zones calmes pour les personnes sensibles au bruit ou à la foule. | Permettre à tous de manifester. | Tôt (avec la carte) |
| AG-35 | Retrouver son cortège | Position annoncée des cortèges (syndicats, collectifs, lycéens) par leurs organisateurs. | Rejoindre les siens dans une grande manifestation. | Plus tard |
| AG-36 | Point de ralliement | Lieu convenu à l'avance pour retrouver ses proches ou un enfant perdu, affiché sur la carte. | Se retrouver quand le réseau est saturé. | Tôt |
| AG-37 | Périmètres d'interdiction officiels | Zones où manifester est interdit par un arrêté préfectoral publié, avec le lien vers l'arrêté. | Information légale publique, qui évite des amendes et des interpellations. | Tôt |
| AG-38 | Transports et retour | Stations fermées, lignes perturbées, covoiturages de retour (AG-10). | Repartir en sécurité. | Plus tard |
| AG-39 | Relais des alertes sans réseau | Le bouton AIDE, les sorties et les messages de coordination passent par Bluetooth de téléphone en téléphone (comme AG-16) quand le réseau mobile est saturé, ce qui arrive souvent dans les grands rassemblements. | La coordination des secours et de la sécurité ne doit pas dépendre du réseau. | Plus tard (avec la messagerie hors ligne) |
| AG-40 | Observateurs et legal teams | Position des observateurs des pratiques policières et des legal teams, visible par la seule coordination, comme pour les street medics et l'équipe de sécurité (AG-45). | Documenter et défendre, pas seulement soigner. | Plus tard |
| DE-22 | Écrire la loi ensemble | Rédaction collaborative d'une proposition de loi citoyenne à partir d'une revendication très soutenue : texte, amendements citoyens, vote final, remise aux parlementaires. | Passer de la revendication au texte prêt à être déposé. | Plus tard |
| DE-23 | Réponses des élus | Compte « élu vérifié » permettant aux élus de répondre publiquement aux interpellations et au Pacte, tous avec exactement les mêmes droits et le même affichage. | Transformer l'interpellation en dialogue public et traçable. | Plus tard (avec un avis juridique) |
| DE-24 | Budget participatif de PPF | Les comptes certifiés votent l'usage d'une partie des dons reçus par l'association. | PPF applique à elle-même la démocratie qu'elle défend. | Plus tard (il faut d'abord des dons) |
| PP-03 | Nos victoires | Ce qui a été obtenu : engagements tenus, revendications satisfaites, lois modifiées, avec les sources. | Montrer que l'engagement sert à quelque chose, contre le découragement. | Tôt |
| PP-04 | Espace presse | Communiqués, kit média, chiffres réutilisables, contact presse. | Faciliter la reprise par les médias. | Tôt |

Pistes écartées volontairement : les classements publics de militants et les badges de récompense, qui poussent à la course à l'activité, favorisent la manipulation et révèlent l'engagement de chacun.
