# Brainstorming : cahier fonctionnel, inventaire (1/3) : principes et socle applicatif

Suite de l'entrée « Inventaire complet des fonctionnalités » de `docs/brainstorming-cahier-fonctionnel.md`. L'inventaire est réparti en trois documents pour rester lisible : 1. principes et socle applicatif ; 2. fonctionnalités PPF et pistes retenues ; 3. points d'attention. Brouillon : rien n'est validé tant que l'entrée n'a pas le statut « Intégré ».

---

### [2026-11-04] Inventaire complet des fonctionnalités : socle applicatif et fonctionnalités PPF
- Statut : À valider (liste validée le 2026-11-04 ; décisions en attente sur certains points d'attention, intégration à venir)
- Concerne : cahier-fonctionnel.md (structure et contenu complets, future version 2.0) ; prépare le découpage du cahier des charges en MVP livrables ; s'appuie sur l'entrée « Réorganiser le cahier fonctionnel par espaces » (ci-dessus) et sur les entrées « Navigation et UX » et « Briques de contenu communes » (brainstorming-cahier-des-charges.md)
- Description : à la demande de la propriétaire, avant de rédiger le cahier des charges par MVP, recenser toutes les fonctionnalités en séparant nettement deux familles :
  - **Partie A, le socle applicatif** : ce que toute application sérieuse doit savoir faire, quelle que soit sa mission : s'installer, se mettre à jour, naviguer, gérer un compte, se régler, notifier, fonctionner hors ligne, chercher, afficher des médias, partager, être accessible, être protégée, être administrée.
  - **Partie B, les fonctionnalités PPF** : celles qui servent directement la mission, rangées par espace (En direct, Voir, Comprendre, Agir, Décider, Mon espace), plus les outils de l'équipe propres à PPF.
- Test pour classer une fonctionnalité : « Est-ce qu'elle montre, produit ou recueille un contenu ou un acte politique ? » Si non, elle relève du socle, même quand c'est le contexte de PPF qui la rend nécessaire (par exemple l'effacement rapide ou les adresses de secours en cas de censure). Si oui, c'est une fonctionnalité PPF.
- Pourquoi cette séparation : le socle se construit une fois, au début, et sert à toutes les fonctionnalités PPF. Chaque MVP pourra alors se définir simplement : « la partie du socle nécessaire + une liste de fonctionnalités PPF ».
- Origine indiquée pour chaque fonctionnalité :
  - **CF 1.0** : déjà dans le cahier fonctionnel définitif (module indiqué) ;
  - **CDC** : exigence déjà présente dans le cahier des charges, transformée ici en fonctionnalité visible ;
  - **Brainstorming** : reprise d'une entrée de brainstorming, pas encore validée ;
  - **Nouveau** : proposé dans cet inventaire.
  Valider l'inventaire ne valide pas automatiquement les entrées de brainstorming qu'il reprend : elles gardent leur propre statut et leurs options.
- Identifiants : chaque fonctionnalité reçoit un identifiant stable, réutilisé dans le cahier des charges, le découpage en MVP et le code : S pour le socle ; MA (En direct), VO, CO, AG, DE, TE (Mon espace) pour les espaces ; AP pour les appels ; PR pour le questionnaire citoyen ; PP pour « À propos de PPF » ; EQ pour les outils de l'équipe propres à PPF. Un identifiant supprimé n'est jamais réutilisé. Les préfixes MA et TE sont conservés malgré le changement de nom des espaces, pour ne pas casser les références.
- Mise à jour du 2026-11-04 : à la demande de la propriétaire, ajout de l'administration complète du site et de l'application (A14 à A16 : super-administrateur, nomination des membres de l'équipe, groupes de modération et d'administration, permissions configurables, gestion des utilisateurs et des contenus) et des comptes d'organisation (A17) dans le socle ; ajout de l'espace des organisations vérifiées dans « Agir » (AG-17 à AG-22), pour qu'associations et syndicats publient leurs contenus et leur calendrier au même endroit et que les citoyens n'aient plus à chercher l'information dans dix endroits différents. Rien n'étant encore validé, la numérotation du socle a été refaite à partir de S-56.
- Deuxième mise à jour du 2026-11-04 : à la demande de la propriétaire, ajout :
  - des **appels** (AP-01 à AP-09), que tout le monde peut lancer : se réunir, débattre, s'organiser, soutenir une cause, signer une pétition, collecter des fonds, etc. ; l'ancienne fonctionnalité AG-05 « Proposer une action » y est fusionnée et son identifiant n'est pas réutilisé ;
  - de **canaux de communication sécurisés** et d'outils plus interactifs (A18 et MA-06) ;
  - d'un **mur des revendications** en cards interactives, avec détection intelligente des doublons et un espace de débat ouvert automatiquement pour chaque revendication (DE-01, DE-18 à DE-21) ;
  - d'un **profil citoyen facultatif** (âge, sexe, territoire, bord politique, religion) pour montrer qu'une revendication rassemble au-delà des clivages (PR-01 à PR-04), avec les précautions décrites au point d'attention n° 9 ;
  - d'un bloc **intégrité et lutte contre les faux comptes** (A19).
- Troisième mise à jour du 2026-11-04 : la propriétaire précise le profil citoyen, qui devient un **questionnaire citoyen anonyme** (B11 et point d'attention n° 9) : aucun profil stocké, mais un formulaire facultatif et anonyme proposé à chaque vote, accompagné de la seule mention « compte certifié ou non » ; religion, orientation sexuelle et identité de genre ne sont ni demandées ni stockées ; sexe : femme, homme, non binaire, non renseigné ; chacun peut, s'il le souhaite, afficher publiquement son bord politique sur son profil (PR-05).
- Quatrième mise à jour du 2026-11-04 : la propriétaire retient la mémorisation des dernières réponses du questionnaire pendant la session en cours, sur ordinateur comme sur téléphone (PR-06), ainsi que les libellés « Je préfère ne pas répondre » (dans le formulaire) et « non renseigné » (dans les statistiques). Ajout, à sa demande, de 26 pistes supplémentaires à trier, d'une règle de transparence sur l'usage de l'IA et de deux points d'attention (mineurs, carte de manifestation en direct).
- Cinquième mise à jour du 2026-11-04 : à la demande de la propriétaire, ajout :
  - d'une **trésorerie publique en temps réel**, d'un **tableau de bord fédérateur** et de l'adhésion (PP-06 à PP-08) ; la piste PP-05 est fusionnée dans PP-07 et son identifiant n'est pas réutilisé ;
  - de sa **décision sur les mineurs** : plateforme ouverte à tous ; questionnaire et affichage du bord politique réservés aux personnes qui déclarent 15 ans ou plus ; aucune restriction de communication, seulement des réglages protecteurs par défaut (point d'attention n° 13) ;
  - de la **carte de la manifestation**, retenue sans aucune position des forces de l'ordre (AG-24), et d'un nouveau bloc **« Secours et défense en manifestation »** : bouton AIDE relié aux street medics certifiés, coordination des secours, alerte « Besoin d'un avocat » (AG-27 à AG-32) ;
  - du type **« appel à témoins ou à témoignages »** et de **champs obligatoires selon le type d'appel** (AP-01, AP-10) ;
  - de 8 nouvelles pistes de cartographie (AG-33 à AG-40).
- Sixième mise à jour du 2026-11-04 :
  - la propriétaire **retient les 32 pistes**, avec la priorité indicative proposée (tôt ou plus tard) ;
  - **trésorerie** : une transparence rapide plutôt que du temps réel (option A du point d'attention n° 15) ;
  - **IA** : chaque contenu affiche son origine (IA ou humaine) et, le cas échéant, sa relecture par un membre de l'équipe ;
  - **adhésion** : mesures de sécurité du paiement et du registre (point d'attention n° 16) ;
  - **données de santé** et **publication des témoignages** : positions de la propriétaire consignées (points d'attention n° 17 et n° 19) ;
  - nouveau bloc **contact, tickets et réclamations** (A20, S-102 à S-111) ; l'ancienne fonctionnalité S-54 « Contact et signaler un bug » y est remplacée et son identifiant n'est pas réutilisé.
- Septième mise à jour du 2026-11-04 :
  - la propriétaire **valide la liste**, la séparation en deux parties et les identifiants ;
  - **espaces** : « Maintenant » devient « En direct », « Mon territoire » devient « Mon espace », tableau de bord personnalisable (TE-05, TE-06) ; la piste S-99 « Mon espace d'engagement » y est fusionnée et son identifiant n'est pas réutilisé ;
  - **navigation** : barre du haut fixe, barre latérale dépliante en accordéon, bouton principal « Carte » (S-05, point d'attention n° 22) ;
  - **débats** : la synthèse IA se place toujours entre la card et les commentaires ; chaque commentaire porte une étiquette Pour, Contre ou Mitigé (DE-20) ;
  - **statistiques** : statistiques anonymes et agrégées, tableau de bord statistique de l'équipe et rapports assistés par IA (S-76, S-113, S-114, point d'attention n° 23) ;
  - **suivi de ses votes** dans Mon espace, avec sauvegarde chiffrée entre appareils (TE-06, S-112, point d'attention n° 25) ;
  - **baromètre politique** pour se situer et pour lire la tendance des votes sur les cards (PR-01, PR-07, point d'attention n° 9) ;
  - **alertes simultanées** regroupées, validées ou invalidées par les street medics sur place (AG-41, point d'attention n° 17) ;
  - nouvelles fonctionnalités de **sécurité en manifestation** : zones dangereuses, sorties, aide pour sortir, équipe citoyenne de sécurité, constat en direct aux sorties (AG-42 à AG-46, point d'attention n° 24) ;
  - décisions sur les points d'attention n° 1, 2, 3, 5, 6, 7, 10 et 12 ; le point n° 11 (salons) est reporté au **bloc communautaire**, que la propriétaire juge essentiel et qui fera l'objet d'une discussion dédiée (entrée « Bloc communautaire » ci-dessous) ;
  - archives des échanges : position de la propriétaire et proposition ajustée au droit (point d'attention n° 20).
- Huitième mise à jour du 2026-11-04 : la propriétaire confirme les points en attente : baromètre (centre en une seule case, pourcentage de votes pour et nombre de votants dans chaque case, badge de consensus sur les trois colonnes), rayon de regroupement des alertes d'environ 15 mètres et 2 minutes, alertes d'un téléphone signalé envoyées à la coordination avec la mention « à vérifier », statistiques uniquement agrégées et anonymes, sécurité en manifestation (AG-42 à AG-46), deux super-administrateurs avec clé physique, conservation des archives. Elle précise sa vision du **droit de réponse** (VO-21, point d'attention n° 7) : bouton sous l'article, compte vérifié, réponse de 1 000 mots au maximum ; ajustements juridiques proposés, à confirmer.
- Neuvième mise à jour du 2026-11-05 :
  - **droit de réponse** validé avec les quatre ajustements juridiques ; chaque demande déclenche dans l'administration une notification du niveau d'importance le plus élevé, 5 sur 5 (S-115) ;
  - proposition complète du **bloc communautaire**, à partir de la définition du cercle donnée par la propriétaire : relations et cercles (A21, S-116 à S-123), messagerie chiffrée complétée (S-124 à S-128), collectifs (A22, S-129 à S-136, et AG-47 à AG-53), partage avec ses cercles (TE-07) ; points d'attention n° 26 à 31 ;
  - les groupes locaux (AG-04) deviennent des collectifs et la piste S-100 « Inviter un proche » est fusionnée dans S-116 ; leurs identifiants ne sont pas réutilisés ;
  - l'inventaire est réparti en trois documents.
- Dixième mise à jour du 2026-11-05 :
  - décisions de la propriétaire sur le bloc communautaire (points d'attention n° 26 à 31) : graphe social minimisé, statuts privés, création de collectifs par tout compte vérifié sans limite de nombre, modération par IA avec décision humaine ;
  - **principe de libre choix** (point d'attention n° 32) : confidentialité et anonymat au maximum par défaut, liberté totale de chacun sur ses propres données après une mise en garde ; appliqué à toutes les fonctionnalités concernées, ainsi qu'à la vision (version 4.2), au cahier des charges (version 1.1, principe 10) et au guide du développeur (CLAUDE.md) ; le cahier fonctionnel l'intégrera dans sa version 2.0 ;
  - **intelligence artificielle** : modération assistée par IA (S-137, point d'attention n° 33) ; assistant citoyen IA (CO-20) avec crédits mensuels gratuits et recharge (S-138, S-139, point d'attention n° 34).
- Onzième mise à jour du 2026-11-05 :
  - **modération assistée par IA** décidée : alerte rouge (contenu grave et manifestement illicite masqué immédiatement, en attendant la décision humaine), alerte orange (contenu limite, publié, équipe prévenue), publication directe et sans notification pour tout le reste ; tout utilisateur peut signaler un contenu ou un autre utilisateur (S-38, S-115, S-137, point d'attention n° 33) ;
  - **hébergement du modèle d'IA** : précision qu'il n'y a jamais de serveur à installer chez soi ; nouvelles options (serveur loué ou service européen facturé à l'usage, sans conservation des requêtes) ;
  - **recharge de crédits** : achat ponctuel d'un produit numérique plutôt qu'un abonnement (S-139) ; l'éligibilité de PPF à HelloAsso et les règles des magasins d'applications sont à vérifier ; avis d'un expert-comptable accepté (point d'attention n° 34).

- Recommandation : une fois le point n° 34 tranché (hébergement du modèle d'IA, paiement des crédits) et la question de la relecture préalable du point n° 33 réglée, réécrire le cahier fonctionnel en version 2.0 : partie A (socle) et partie B (espaces), avec pour chaque fonctionnalité son but, qui peut l'utiliser, son comportement, ses règles, les données utilisées, son fonctionnement hors ligne et ses critères d'acceptation ; puis proposer dans le brainstorming du cahier des charges le découpage en MVP, qui remplacera l'entrée « Phase 1 trop chargée face à l'urgence ? ».
- Décision : liste des fonctionnalités, séparation en deux parties et identifiants validés par la propriétaire le 2026-11-04 ; renommage des espaces et nouvelle navigation décidés le même jour ; points d'attention n° 1 à 3, 5 à 10, 12, 17, 20, 23, 24 et 26 à 32 décidés (n° 7 complété le 2026-11-05) ; n° 11 remplacé par le n° 26 ; n° 33 décidé le 2026-11-05, sauf la relecture préalable de certains contenus ; n° 34 en partie décidé (achat ponctuel plutôt qu'abonnement, avis d'un expert-comptable), hébergement du modèle et prestataire de paiement à valider.

#### Partie A : socle applicatif (130 fonctionnalités ci-dessous, plus 6 retenues lors du tri, listées dans le document 2)

**A1. Installation, démarrage et mises à jour**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-01 | Premier lancement | Trois écrans au maximum, qu'on peut passer : ce qu'est PPF, « tout est lisible sans compte », proposition de renseigner son code postal. | Nouveau |
| S-02 | Toutes les plateformes | Android, iPhone, ordinateur, et site web installable comme une application (PWA). | CDC |
| S-03 | Mises à jour | Par les magasins d'applications et, sur Android, par un fichier d'installation téléchargeable directement (et F-Droid, à étudier), dont la signature est vérifiée ; notes de version lisibles ; mise à jour obligatoire en cas de faille de sécurité. | Nouveau |
| S-04 | État du service | Message affiché en cas de panne, de maintenance ou d'attaque, avec les adresses de secours. | Nouveau |

**A2. Navigation et lecture**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-05 | Navigation principale | **Barre du haut**, fixe : Mon espace, recherche, notifications, aide, paramètres, connexion ou déconnexion. **Barre latérale** dépliante : En direct, Voir, Comprendre, Agir, Décider ; un clic sur une section déplie la barre et ouvre la section en accordéon, avec ses accès rapides les plus importants. **Bouton principal « Carte »**, toujours visible, qui ouvre immédiatement la carte et ses couches (mobilisations, appels, organisations, témoignages, manifestation en direct). Même organisation sur le site et l'application ; adaptation au téléphone : point d'attention n° 22. | Décision de la propriétaire (remplace la proposition de l'entrée « Navigation et UX ») |
| S-06 | Liens directs | Un lien partagé ouvre directement le bon contenu dans l'application si elle est installée, sinon sur le site. | Nouveau |
| S-07 | Enregistrer pour plus tard | Marque-pages stockés uniquement sur l'appareil (et dans la sauvegarde chiffrée, S-112, si elle est activée). | Nouveau |
| S-08 | Historique | Historique de lecture et de recherche stocké uniquement sur l'appareil, effaçable en un geste. | Nouveau |

**A3. Compte et identité**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-09 | Utilisation sans compte | Lecture, carte, fiches pratiques et recherche accessibles sans compte. | CF 1.0 (M16) |
| S-10 | Créer un compte | Paire de clés créée sur l'appareil et pseudonyme, sans numéro de téléphone ni e-mail. | CF 1.0 (M16) |
| S-11 | Phrase de récupération | Affichée à la création, avec une vérification qu'elle a bien été notée ; restauration du compte sur un nouvel appareil. | CF 1.0 (M16), précisé |
| S-12 | Plusieurs appareils | Transfert du compte d'un appareil à l'autre par QR code. | Nouveau |
| S-13 | Profil public minimal | Par défaut : pseudonyme et avatar généré automatiquement, sans photo ni information personnelle. L'utilisateur peut ajouter ce qu'il souhaite (photo, présentation, appartenances), après une mise en garde (principe de libre choix, point d'attention n° 32). | Nouveau, précisé |
| S-14 | Exporter et supprimer ses données | Export de toutes ses données dans un fichier lisible ; suppression du compte (droits prévus par le RGPD). Voir le point d'attention n° 3. | Nouveau |
| S-15 | Comptes de l'équipe | Double authentification obligatoire (application dédiée ou clé de sécurité), sessions visibles et révocables. | CF 1.0 (section 1) |

**A4. Réglages**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-16 | Apparence | Thème clair, sombre ou automatique ; taille du texte ; réduction des animations. | CDC, précisé |
| S-17 | Langue | Français par défaut ; autres langues ajoutées au fil des traductions. | CDC |
| S-18 | Mode « Facile à lire » | Bascule générale vers les versions simplifiées des contenus, quand elles existent. | CF 1.0 (règles transverses), précisé |
| S-19 | Économie de données | Images et vidéos chargées seulement à la demande ; téléchargements réservés au wifi. | CDC |
| S-20 | Réglage « Mon territoire » | Saisie d'un code postal, stocké uniquement sur l'appareil, modifiable et effaçable ; il alimente Mon espace. | Brainstorming (Navigation et UX) |
| S-21 | Confidentialité | Écran qui montre ce qui est stocké sur l'appareil et ce qui est envoyé au serveur, avec un bouton « tout effacer ». | Nouveau |

**A5. Notifications**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-22 | Notifications sans Google | Par UnifiedPush et un serveur ntfy auto-hébergé ; sur iPhone, simple signal de réveil sans aucun contenu. | CDC |
| S-23 | Abonnements | Choix par thème, par campagne, par territoire et par type (mobilisations, alertes, votes au Parlement). | Nouveau |
| S-24 | Centre de notifications | Liste des notifications reçues, consultable dans l'application, accessible depuis la barre du haut. | Nouveau |
| S-25 | Notifications discrètes | Aucun contenu sensible affiché sur l'écran verrouillé ; option « silence total ». | Nouveau |

**A6. Hors ligne, sauvegarde et synchronisation**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-26 | Contenus hors ligne | Fiches pratiques, carte, agenda et contenus consultés ; choix des rubriques à télécharger ; espace occupé affiché et bouton pour le libérer. | CF 1.0 (règles transverses) |
| S-27 | Actions en attente | Un vote, un soutien ou un témoignage fait sans réseau part automatiquement au retour de la connexion, avec un indicateur « en attente d'envoi ». | Nouveau |
| S-28 | Indicateur de fraîcheur | L'utilisateur voit toujours s'il est connecté et de quand datent les données affichées. | Nouveau |
| S-112 | Sauvegarde et synchronisation chiffrées | Facultatif : Mon espace (tableau de bord, suivis, votes, marque-pages, réglages) est sauvegardé chiffré de bout en bout avec la clé de l'utilisateur et retrouvé sur tous ses appareils et après une restauration (S-11). Le serveur ne garde qu'un bloc illisible : ni PPF ni quelqu'un qui saisirait le serveur ne peut le lire. Voir le point d'attention n° 25. | Nouveau (pour répondre à la demande de la propriétaire sur Mon espace) |

**A7. Recherche**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-29 | Recherche unique | Dans la barre du haut, sur tous les contenus, avec des filtres par espace, type, date et territoire. | Brainstorming (Navigation et UX) |
| S-30 | Recherche hors ligne | Dans les contenus téléchargés. | Nouveau |

**A8. Médias et cartes**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-31 | Lecteur d'articles | Mise en page lisible, sources cliquables, version imprimable. | Nouveau |
| S-32 | Lecteur vidéo | Vidéos hébergées sur PeerTube, avec sous-titres. | CDC |
| S-33 | Lecteur audio | Podcasts et radio, lecture en arrière-plan. | CF 1.0 (M6), précisé |
| S-34 | Visionneuse | Images avec zoom, documents PDF. | Nouveau |
| S-35 | Carte | MapLibre et OpenStreetMap ; couches activables ; position facultative, utilisée uniquement sur l'appareil et jamais envoyée au serveur (sauf pour une alerte que l'on lance soi-même, AG-27) ; zones téléchargeables pour le hors ligne. | CDC |
| S-36 | Envoi de fichiers | Photo, vidéo, audio : métadonnées supprimées sur l'appareil avant l'envoi, floutage des visages optionnel, empreinte horodatée. | CF 1.0 (M5), généralisé |

**A9. Interactions communes**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-37 | Partager | Lien, QR code, image de partage générée automatiquement (chiffre ou citation, avec sa source et le nom de PPF). | CF 1.0 (M6), précisé |
| S-38 | Signaler | Tout utilisateur peut signaler, en un geste et à tout moment, un contenu, un commentaire ou un autre utilisateur, y compris quand la modération assistée par IA (S-137) n'a rien relevé ; le signalement rejoint la file de modération (S-72). | CF 1.0 (M15), précisé par la propriétaire |
| S-39 | Commenter | Sous forme de débats structurés, là où l'on construit ensemble (revendications, appels, campagnes) ; chaque commentaire porte une étiquette Pour, Contre ou Mitigé. Voir DE-20 et le point d'attention n° 1. | CF 1.0 (rôle contributeur), décidé |
| S-40 | Ajouter à son agenda | Export au format iCal, pour tout événement. | CF 1.0 (M1), généralisé |

**A10. Accessibilité**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-41 | Accessibilité WCAG 2.2 AA | Lecteurs d'écran, contrastes, navigation au clavier (y compris dans la barre latérale en accordéon), textes alternatifs, transcriptions des vidéos et des podcasts. | CDC |
| S-42 | Déclaration d'accessibilité | Page publique qui décrit le niveau atteint et permet de signaler un problème d'accessibilité. | Nouveau |

**A11. Sécurité, vie privée et résistance à la censure**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-43 | Verrouillage de l'application | Code ou empreinte digitale, facultatif. | Nouveau |
| S-44 | Effacement rapide | Un geste efface toutes les données stockées sur l'appareil (après rappel de la phrase de récupération). | Nouveau |
| S-45 | Application discrète | Option : nom et icône neutres, contenu masqué dans la liste des applications ouvertes. | Nouveau (à étudier) |
| S-46 | Authenticité des contenus | Chaque contenu est signé ; l'application vérifie la signature et l'indique (« contenu authentique PPF »), ce qui protège contre les faux qui circulent. | CDC (signature) |
| S-47 | Adresses de secours | Si l'adresse principale est bloquée, bascule automatique vers des miroirs, puis vers Tor et Nostr (phase 3). | CDC |
| S-48 | Données ouvertes | Export JSON documenté de tous les contenus publiés. | CDC |

**A12. Propre au site web**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-49 | Site lisible sans JavaScript | Pages construites sur le serveur, lisibles sur tout navigateur, même ancien. | CDC |
| S-50 | Aperçus de partage | Titre, résumé et image affichés quand un lien est partagé sur une messagerie ou un réseau social, sans aucun script tiers. | Nouveau |
| S-51 | Flux RSS | Par espace et par thème, pour suivre PPF sans application. | Nouveau |
| S-52 | Référencement | Plan du site et informations structurées pour être bien trouvé par les moteurs de recherche. | Nouveau |

**A13. Informations légales et soutien**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-53 | Pages légales | Mentions légales, conditions d'utilisation, politique de confidentialité, charte de contenu. | CDC (section 9) |
| S-55 | Faire un don à l'association | Voir le point d'attention n° 4. | Nouveau (à étudier) |

L'ancienne fonctionnalité S-54 « Contact et signaler un bug » est remplacée par le bloc A20 ; son identifiant n'est pas réutilisé.

**A14. Administration : équipe, groupes et permissions**

L'administration est un espace réservé à l'équipe, accessible sur le site web depuis un ordinateur ou un téléphone.

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-56 | Super-administrateur | Rôle de la propriétaire : nomme et retire les administrateurs, seul à pouvoir modifier les rôles les plus élevés. Deux super-administrateurs au minimum, chacun avec une clé de sécurité physique, pour ne jamais perdre l'accès à la plateforme (point d'attention n° 8). | Nouveau |
| S-57 | Nommer les membres de l'équipe | Invitation par un lien à usage unique et à durée limitée ; double authentification activée obligatoirement à l'acceptation ; retrait immédiat d'un membre. | Nouveau |
| S-58 | Groupes | Créer, renommer et supprimer des groupes (par exemple : modération nationale, modération d'une région, rédaction de « Voir », équipe juridique, vérification des organisations), y ajouter ou en retirer des membres. Une personne peut appartenir à plusieurs groupes. | Nouveau |
| S-59 | Permissions par groupe | Pour chaque groupe, configurable sans code : les actions autorisées (consulter, ajouter, modifier, publier, dépublier, supprimer, modérer, gérer les utilisateurs, gérer les organisations, envoyer des notifications, consulter les statistiques, modifier les réglages) et le périmètre (espace, type de contenu, thème, territoire). Groupes de départ fournis : modération, édition, administration. | CF 1.0 (section 1), étendu |
| S-60 | Accès temporaires | Permission accordée pour une durée limitée (par exemple un renfort de modération pendant une grande mobilisation), retirée automatiquement à l'échéance. | Nouveau |
| S-61 | Validation à deux | Les actions sensibles exigent l'accord d'une deuxième personne : nommer un administrateur, modifier des permissions, supprimer définitivement, bannir, envoyer une notification nationale. | Nouveau |
| S-62 | Coupure d'urgence | Si le compte d'un membre de l'équipe est compromis : toutes ses sessions et toutes ses permissions sont coupées en un clic. | Nouveau |
| S-63 | Journal d'audit | Trace de toutes les actions de l'équipe, consultable et filtrable, impossible à modifier ou à effacer. | CF 1.0 (section 1), précisé |
| S-64 | Tableau de bord de l'équipe | Ce qui attend chaque groupe : contenus à relire, signalements, demandes de vérification, droits de réponse, recours, messages, réclamations, alertes. | Nouveau |
| S-115 | Niveaux d'importance des notifications de l'équipe | Chaque notification de l'administration porte un niveau d'importance de 1 à 5, qui fixe son ordre d'affichage, sa couleur et son mode d'alerte ; au niveau 5, notification immédiate sur le téléphone des personnes concernées et rappel tant qu'elle n'est pas prise en charge. **Niveau 5** (décision de la propriétaire) : toute demande de droit de réponse (délai légal de trois jours) et toute **alerte rouge** de la modération assistée par IA (contenu masqué en attente de décision, S-137). **Niveau 4** (décision de la propriétaire) : toute **alerte orange** de la modération assistée par IA. Proposition pour le reste : 5 aussi pour une faille de sécurité signalée ou un compte de l'équipe compromis ; 4 pour une réclamation sur les données personnelles ou un contenu manifestement illicite ; 3 pour une contestation de modération ou une demande de vérification ; 2 pour un message de contact ; 1 pour une suggestion. Niveaux réglables sans code (S-77). | Nouveau (demande de la propriétaire) |

**A15. Administration : utilisateurs**

Règle de vie privée : aucun rôle, pas même le super-administrateur, ne peut voir les votes, les soutiens ou les abonnements d'un utilisateur. L'architecture doit rendre cette consultation impossible, et pas seulement interdite (méthode à définir avec l'entrée « Méthode technique une personne, une voix » du brainstorming du cahier des charges). Il n'y a par ailleurs aucune identité réelle à consulter. Le suivi de ses propres votes dans Mon espace (TE-06) ne remet pas en cause cette règle : il est stocké sur l'appareil de l'utilisateur, ou chiffré avec sa clé (point d'attention n° 25).

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-65 | Rechercher un utilisateur | Par pseudonyme ou identifiant public ; la fiche montre ses contributions publiques, les signalements le concernant et ses sanctions passées. | Nouveau |
| S-66 | Sanctions graduées | Avertissement, restriction (lecture seule, ou publications soumises à validation), suspension temporaire, bannissement ; motif obligatoire, notification à l'utilisateur. | Nouveau (précise CF 1.0, M15) |
| S-67 | Recours et levée des sanctions | Traitement des recours par une autre personne que celle qui a sanctionné ; levée d'une sanction, avec motif. | Nouveau (précise CF 1.0, M15) |
| S-68 | Supprimer un compte | À la demande de l'utilisateur ou après un bannissement, selon les règles du point d'attention n° 3. | Nouveau |
| S-69 | Signaleurs de confiance | Désigner les organisations ou les personnes dont les signalements sont traités en priorité. | CF 1.0 (M15) |

**A16. Administration : contenus, statistiques et plateforme**

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-70 | Outil de publication | Brouillon, relecture, validation, publication programmée, bibliothèque de médias. | CF 1.0 (M6) |
| S-71 | Gérer les contenus | Ajouter, modifier, dépublier, supprimer, restaurer depuis une corbeille, mettre en avant ; historique de toutes les versions. | Nouveau (précise CF 1.0, M6) |
| S-72 | Modération | File de modération, trois sources de signalement (IA, communauté, organisations partenaires), décision motivée et notifiée, recours, rapport de transparence (DSA), signalement à PHAROS. | CF 1.0 (M15) |
| S-73 | Pages et menus | Créer et modifier les pages fixes (à propos, aide, charte), l'ordre des rubriques et les accès rapides de chaque section de la barre latérale. | Nouveau |
| S-74 | Envoi de notifications | Par l'équipe, ciblé par thème ou territoire, avec validation à deux pour les envois nationaux. | Nouveau |
| S-75 | Traductions | Gestion des textes de l'interface et des contenus traduits. | CDC |
| S-76 | Mesure de fréquentation | Compteurs agrégés calculés sur le serveur (lectures par contenu et par jour, parcours d'ensemble entre les espaces, recherches sans résultat), sans cookie, sans adresse IP, sans identifiant (point d'attention n° 2, décidé). | Nouveau, décidé |
| S-77 | Réglages sans code | Contenus mis en avant, textes d'accueil et seuils modifiables par l'équipe sans intervention technique. | Nouveau |
| S-113 | Tableau de bord statistique de l'équipe | Dans l'administration, toutes les statistiques de la plateforme au même endroit : fréquentation, votes et soutiens, appels, revendications, questionnaire citoyen, carte et alertes de manifestation, contact et réclamations, trésorerie. Filtres par période, territoire, thème et type ; croisements ; courbes et cartes ; export ; alertes sur les évolutions inhabituelles. Accès selon les droits (S-59). Uniquement des données agrégées et anonymes (point d'attention n° 23). | Nouveau (demande de la propriétaire) |
| S-137 | Modération assistée par IA | Un modèle d'IA analyse chaque contenu public (publications, commentaires, appels, pages de collectifs et d'organisations) juste après sa mise en ligne, sans retarder la publication. **Alerte rouge** (niveau 5, S-115) : contenu estimé grave et manifestement illicite (appel à la violence, menaces, contenu pédocriminel ou terroriste, divulgation d'informations personnelles), masqué immédiatement en attendant la décision de l'équipe. **Alerte orange** (niveau 4) : contenu limite (risque de diffamation ou de harcèlement, doute sur la charte), qui reste publié. Tout le reste est publié directement, sans notification à l'équipe. La décision finale est toujours humaine : l'équipe confirme ou annule chaque masquage, l'auteur est prévenu et peut contester (S-66, S-67). L'IA ne lit jamais les échanges chiffrés. Détails : point d'attention n° 33. | Décision de la propriétaire |
| S-114 | Rapports d'analyse assistés par IA | Rapports périodiques (semaine, mois, année) ou à la demande, rédigés par une IA à partir des seules statistiques agrégées : tendances, points saillants, comparaisons. Modèle d'IA auto-hébergé, aucune donnée envoyée à un service extérieur. Origine IA affichée ; relecture par l'équipe avant toute publication (point d'attention n° 23). | Nouveau (demande de la propriétaire) |

**A17. Comptes d'organisation**

Le fonctionnement technique des comptes d'organisation relève du socle ; ce que les organisations publient relève des fonctionnalités PPF (AG-17 à AG-22).

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-78 | Compte d'organisation | Un compte partagé par plusieurs membres, chacun gardant son propre compte personnel ; l'organisation a sa propre clé de signature. | CF 1.0 (M3), précisé |
| S-79 | Équipe de l'organisation | Gérée par l'organisation elle-même : rôles de responsable, éditeur et contributeur ; invitation et retrait des membres ; double authentification obligatoire. | Nouveau |
| S-80 | Circuit de publication interne | Brouillon, relecture et validation par un responsable de l'organisation avant publication (même outil que S-70). | Nouveau |
| S-81 | Demande de vérification | Formulaire : nom, forme juridique, numéro au répertoire des associations ou SIRET, statuts, contact, acceptation de la charte ; suivi de l'avancement de la demande. | Nouveau (lié à EQ-07) |

**A18. Communication sécurisée**

L'outil de discussion relève du socle ; les débats sur les revendications, les appels et les campagnes qui l'utilisent sont des fonctionnalités PPF (DE-20, AP-06). Ces outils servent aussi aux cercles (A21) et aux collectifs (A22). Protection du graphe social et choix technique : point d'attention n° 26.

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-82 | Salons de discussion | Un salon peut être ouvert pour chaque groupe local, appel, campagne ou organisation. Deux types : **public** (lisible par tous, modéré par l'équipe) ou **privé** (sur invitation, chiffré de bout en bout, illisible même par PPF). | Nouveau |
| S-83 | Messages privés | Entre deux utilisateurs, chiffrés de bout en bout ; par défaut, personne ne peut écrire à quelqu'un qui ne l'a pas accepté (protection contre le harcèlement) ; chacun peut ouvrir ses messages à tous ou les désactiver (principe de libre choix, point d'attention n° 32). | Nouveau |
| S-84 | Messages éphémères | Effacement automatique des messages après un délai choisi par le salon. | Nouveau |
| S-85 | Réunions en visioconférence | Pour les AG en ligne et les réunions de groupe, avec un outil auto-hébergé. | Nouveau (à étudier) |
| S-124 | Discussions de cercle | Chaque cercle (famille, amis proches, etc.) peut avoir sa propre discussion de groupe, chiffrée de bout en bout, créée en un geste ; seules les personnes qui ont accepté le lien y participent. | Demande de la propriétaire |
| S-125 | Fonctions de messagerie | Texte, messages vocaux, photos et vidéos (métadonnées retirées, S-36), fichiers, réponses, réactions, mentions, messages épinglés, sondages ; accusés de lecture et indicateur « en train d'écrire » désactivés par défaut ; messages éphémères (S-84) ; fonctionne sur plusieurs appareils. | Nouveau |
| S-126 | Appels audio et vidéo chiffrés | Entre deux personnes et en petit groupe, chiffrés de bout en bout ; S-85 reste l'outil des grandes réunions. | Nouveau (à étudier) |
| S-127 | Signaler un message chiffré | PPF ne peut pas lire les discussions chiffrées : un participant peut signaler un message en transmettant lui-même à la modération une copie du message et de son contexte immédiat ; les conditions d'utilisation le prévoient. | Nouveau |
| S-128 | Vérification des contacts | Chacun peut vérifier qu'il parle bien à la bonne personne, en scannant un QR code en face à face ou en comparant un code de sécurité ; alerte si les clés d'un contact changent. | Nouveau |

**A19. Intégrité et lutte contre les faux comptes**

Sans numéro de téléphone ni pièce d'identité, aucune méthode ne garantit à elle seule qu'un compte correspond à une personne réelle. La protection repose donc sur plusieurs barrières combinées, et sur la transparence des résultats. Voir le point d'attention n° 10 (décidé).

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-86 | Niveaux de confiance | Chaque compte a un niveau (nouveau, confirmé, de confiance), gagné par l'ancienneté, une activité normale, des parrainages ou une validation en personne. Le niveau décide de ce que le compte peut faire : par exemple, un appel lancé par un compte nouveau passe par la modération. | Nouveau |
| S-87 | Parrainage | Un compte de confiance peut confirmer un nombre limité d'autres comptes ; si un compte parrainé se révèle faux, le parrain perd ce droit. | Nouveau |
| S-88 | Validation en personne | Lors d'événements (stands, AG, manifestations), un bénévole confirme qu'une personne réelle est derrière un compte en scannant un QR code, sans noter aucune identité ; une personne, une validation. | Brainstorming (méthode « une personne, une voix ») |
| S-89 | Barrières contre les robots | Preuve de travail auto-hébergée et limites de débit, sans reCAPTCHA. | CDC |
| S-90 | Détection des manipulations | Repérage des rafales de votes, des comptes créés en série et des comportements coordonnés ; gel provisoire d'un résultat suspect et examen par l'équipe ; sans adresse IP ni identité. | Nouveau (étend CF 1.0, M2) |
| S-91 | Résultats transparents | Chaque compteur indique combien de votes viennent de comptes confirmés ; c'est ce chiffre qui est affiché en premier. | Nouveau |
| S-92 | Signaler une faille de sécurité | Page publique et procédure de divulgation responsable : engagement de réponse, correction, remerciements publics. | Nouveau |
| S-93 | Transparence sur la sécurité | Publication des audits de sécurité et des incidents, avec ce qui a été corrigé. | Nouveau |

Les exigences de sécurité qui ne sont pas des fonctionnalités visibles (audit externe avant chaque lancement, protection contre les attaques par saturation, sauvegardes, durcissement des serveurs) seront renforcées dans le cahier des charges.

**A20. Contact, tickets et réclamations**

Principe retenu par la propriétaire : comme dans les espaces sécurisés des banques ou des administrations, tous les échanges avec l'équipe ont lieu dans l'application ou sur le site, jamais par e-mail. L'e-mail, facultatif, sert uniquement à prévenir qu'un nouveau message attend dans l'espace, sans en reproduire le contenu. Voir le point d'attention n° 20.

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-102 | Ouvrir un ticket | Type obligatoire : bug, suggestion ou signalement. Pour un bug, les informations techniques (version, type d'appareil) peuvent être jointes avec l'accord de l'utilisateur. Un signalement qui vise un contenu ou un compte rejoint directement la file de modération (S-38, S-72) ; une faille de sécurité rejoint la procédure dédiée (S-92). | Nouveau (demande de la propriétaire) |
| S-103 | Formulaire de contact | Catégorie d'expéditeur obligatoire : citoyen, association ou collectif, syndicat, journaliste, élu, institution, entreprise, autre ; objet et message obligatoires. La catégorie est déclarative, sauf pour les comptes vérifiés (organisation, élu), pour lesquels elle est remplie automatiquement et marquée comme vérifiée. | Nouveau (demande de la propriétaire) |
| S-104 | Formulaire de réclamation | Traité à part entière, distinct du contact. Motif obligatoire : contester une décision de modération ; exercer mes droits sur mes données (accès, rectification, suppression) ; demander l'insertion d'un droit de réponse (renvoie vers VO-21) ; usage des fonds de l'association ; comportement d'un membre de l'équipe ; autre. Le délai de réponse applicable est affiché à l'envoi. | Nouveau (demande de la propriétaire) |
| S-105 | Mes échanges | Espace où l'on retrouve tous ses tickets, messages et réclamations, et où l'on lit et envoie les réponses. Avec un compte : dans Mon espace. Sans compte : un numéro de dossier et un code secret, ou un lien unique, donnés à l'envoi, permettent de suivre l'échange. | Nouveau (demande de la propriétaire) |
| S-106 | Accusé de réception et suivi | Accusé de réception immédiat avec numéro de dossier ; statut visible : reçu, en cours, réponse envoyée, en attente de votre réponse, clos. Prévenu de chaque nouveau message par une notification (S-22) ou, si on l'a fourni, par un e-mail sans contenu. | Nouveau (demande de la propriétaire) |
| S-107 | Boîte de réception de l'équipe | Dans l'administration, tous les échanges triés par type (ticket, contact, réclamation) et par catégorie d'expéditeur, dans un tableau filtrable : date, type, catégorie, statut, priorité, personne ou groupe assigné, mots-clés. Assignation à un groupe ou à une personne, niveau de priorité. | Nouveau (demande de la propriétaire) |
| S-108 | Répondre et tracer | Réponse depuis l'administration. Chaque réponse est enregistrée avec l'identifiant du membre de l'équipe qui l'a écrite, la date et l'heure, et son contenu ; elle ne peut plus être modifiée après l'envoi (une correction se fait par un nouveau message). Vers l'extérieur, la réponse est signée « l'équipe PPF » (décidé). Notes internes invisibles pour l'expéditeur ; réponses types pour les questions fréquentes. | Nouveau (demande de la propriétaire) |
| S-109 | Archives | Les dossiers clos sont archivés, consultables par les seuls membres de l'équipe qui en ont le droit (S-59), avec toute leur traçabilité ; chaque consultation est inscrite au journal d'audit (S-63). Durées de conservation selon le type de dossier, avec une **archive juridique** pour les dossiers sensibles (point d'attention n° 20). | Nouveau (demande de la propriétaire) |
| S-110 | Traitement des réclamations | File séparée dans l'administration, avec ses propres droits d'accès. Délais affichés et alertes avant échéance (un mois pour les droits sur les données, trois jours pour un droit de réponse, en lien avec EQ-04). Une contestation est toujours examinée par une autre personne que l'auteur de la décision contestée (S-67). Les réclamations ne sont jamais supprimées (point d'attention n° 20). Bilan annuel des réclamations publié dans la transparence de PPF (PP-01). | Nouveau (demande de la propriétaire) |
| S-111 | Statistiques du service | Nombre d'échanges, délais moyens de réponse et de résolution, par type et par catégorie d'expéditeur, pour mesurer et améliorer la qualité du service (repris dans S-113). | Nouveau |

**A21. Relations et cercles**

Définition donnée par la propriétaire (2026-11-05) : le cercle d'un utilisateur regroupe ses amis et amis proches, sa famille et sa famille proche, et les organisations et collectifs dont il fait partie (un collectif est un groupe ou un club). Les personnes qu'il suit forment un lien à sens unique, comme sur un réseau social classique. Un lien entre deux personnes n'existe que si l'autre l'accepte. Protection du graphe social : points d'attention n° 26 à 31.

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-116 | Inviter quelqu'un | Lien ou QR code d'invitation sans traceur, à usage unique ou limité, avec une durée de validité. La personne qui crée son compte avec ce lien est mise en relation avec celle qui l'a invitée, après sa propre acceptation. Le QR code échangé en face à face peut aussi servir au parrainage (S-87). Reprend la piste S-100. | Demande de la propriétaire |
| S-117 | Demandes de mise en relation | Envoyer une demande à un compte (par son pseudonyme, son QR code ou depuis un collectif commun) ; boîte des demandes reçues : accepter, refuser, ignorer, bloquer. Le lien n'existe qu'après acceptation. Réglage « qui peut m'envoyer une demande » : tout le monde, les membres de mes collectifs, personne (seulement par QR code ou lien) ; par défaut, les membres de mes collectifs et le QR code ou le lien, l'utilisateur pouvant ouvrir à tout le monde (principe de libre choix, point d'attention n° 32). Nombre de demandes par jour limité pour les comptes nouveaux. | Demande de la propriétaire |
| S-118 | Cercles et statuts | Chaque relation reçoit un ou plusieurs statuts : ami, ami proche, famille, famille proche, et des cercles personnalisés (par exemple « collègues », « voisins »). Les organisations et collectifs dont on fait partie forment automatiquement des cercles. Statuts privés par défaut (point d'attention n° 27) ; l'utilisateur peut choisir de montrer à une personne le statut qu'il lui donne. | Demande de la propriétaire |
| S-119 | Permissions par cercle | Pour chaque cercle, l'utilisateur choisit ce que ses membres peuvent voir et faire : voir son profil complet, son bord politique s'il l'affiche (PR-05), les contenus qu'il partage (TE-07), sa participation à un événement (« J'y serai »), lui écrire directement, l'inviter dans un collectif, recevoir ses alertes en manifestation (AG-23). Réglages les plus protecteurs par défaut, modifiables sans limite, cercle par cercle et personne par personne, après une mise en garde (principe de libre choix, point d'attention n° 32). | Demande de la propriétaire |
| S-120 | Suivre | Lien à sens unique : on reçoit les publications publiques d'un compte, d'une organisation ou d'un collectif. Chacun choisit s'il peut être suivi librement ou sur acceptation. La liste des comptes suivis est privée par défaut ; l'utilisateur peut la rendre publique (principe de libre choix, point d'attention n° 32). | Demande de la propriétaire |
| S-121 | Mes relations | Liste de ses relations, cercles, comptes suivis et abonnés ; recherche dans cette liste ; modifier un statut ; retirer une relation, sans notification à l'autre. | Nouveau |
| S-122 | Bloquer et masquer | Bloquer un compte (il ne peut plus écrire, envoyer de demande ni voir le profil complet) ; masquer ses publications sans le bloquer ; signaler (S-38). | Nouveau |
| S-123 | Profil social | Ce que les autres voient selon leur cercle : pseudonyme, avatar, courte présentation facultative, bord politique s'il est affiché (PR-05), collectifs dont on accepte d'afficher l'appartenance ; par défaut, rien au-delà du pseudonyme et de l'avatar (principe de libre choix, point d'attention n° 32). Aperçu « voir mon profil comme » un inconnu, un ami, la famille. Étend S-13. | Nouveau |

**A22. Collectifs (fonctionnement)**

Un collectif est un groupe ou un club créé par des citoyens, sans forme juridique obligatoire, à la différence des organisations vérifiées (associations et syndicats déclarés, A17). Les groupes locaux (ancien AG-04) deviennent des collectifs de portée locale. Ce que les collectifs publient relève des fonctionnalités PPF (AG-47 à AG-53).

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-129 | Créer un collectif | Réservé aux comptes vérifiés (définition : point d'attention n° 28). Assistant de création en quelques étapes ; aucune limite au nombre de collectifs créés par une même personne (décision de la propriétaire), la détection des manipulations (S-90) repérant les créations en série ; acceptation de la charte de la plateforme. | Demande de la propriétaire |
| S-130 | Paramètres et identité | Nom, description, image de profil, portée (national, régional, départemental, ville, quartier, établissement : lycée, université, entreprise), territoire, thèmes, type (groupe local, club, comité de lutte, collectif de quartier, d'entreprise ou d'établissement, autre), langue, charte interne, nombre maximal de membres, affichage ou non du nombre de membres. Visibilité : **public** (trouvable, page publique), **privé** (trouvable, contenus réservés aux membres) ou **secret** (introuvable, sur invitation seulement). Adhésion : ouverte, sur demande ou sur invitation. | Demande de la propriétaire |
| S-131 | Rôles et statuts des membres | Rôles de base : fondateur, administrateur, modérateur, membre ; statuts personnalisés (référent, trésorier, porte-parole, etc.) ; permissions configurables par rôle (publier, inviter, accepter les demandes, gérer le calendrier, modérer, modifier les paramètres). Deux administrateurs au minimum recommandés. | Demande de la propriétaire |
| S-132 | Rejoindre et quitter | Demande d'adhésion avec un message facultatif ; file des demandes pour les administrateurs ; invitations par lien ou QR code ; validation ; quitter à tout moment ; exclusion motivée. | Demande de la propriétaire |
| S-133 | Membres | PPF ne publie jamais la liste des membres d'un collectif. Par défaut, l'appartenance d'un membre n'est visible que des administrateurs du collectif ; chaque membre choisit ensuite librement de se montrer aux autres membres, puis d'afficher son appartenance sur son profil public, après une mise en garde (points d'attention n° 30 et n° 32). | Nouveau, précisé |
| S-134 | Espaces de discussion du collectif | Salon général, canal d'annonces (seuls les administrateurs publient), commissions ou groupes de travail avec leurs propres salons ; chiffrés de bout en bout pour les collectifs privés et secrets (S-82). | Demande de la propriétaire |
| S-135 | Modération interne | Les administrateurs modèrent contenus et membres selon la charte du collectif ; les contenus publics sont analysés par la modération assistée par IA (S-137), qui alerte l'équipe ; tout membre peut signaler à PPF (S-38, S-127) ; PPF peut suspendre ou fermer un collectif qui enfreint la charte de la plateforme ou la loi (point d'attention n° 29). | Nouveau |
| S-136 | Transmission, archivage et dissolution | Transfert du rôle de fondateur ; export des contenus du collectif ; dissolution par les administrateurs, avec effacement des données ; si tous les administrateurs sont partis, les membres désignent de nouveaux administrateurs par un vote. | Nouveau |

**A23. Crédits de l'assistant IA**

Le fonctionnement technique des crédits relève du socle ; l'assistant lui-même est une fonctionnalité PPF (CO-20). Voir le point d'attention n° 34.

| ID | Fonctionnalité | Description | Origine |
|---|---|---|---|
| S-138 | Crédits mensuels gratuits | Chaque compte reçoit chaque mois une réserve de crédits gratuits pour l'assistant IA ; un compteur simple indique ce qui reste et la date de remise à zéro ; une fois la réserve épuisée, l'utilisateur attend le mois suivant ou recharge (S-139). | Demande de la propriétaire |
| S-139 | Recharge de crédits | Achat ponctuel de crédits supplémentaires, sous la forme d'un produit numérique (code de recharge), et non d'un abonnement (choix de la propriétaire) ; le code est échangé dans l'application contre des crédits anonymes, de sorte que le paiement ne peut pas être relié au compte. Prestataire de paiement, règles des magasins d'applications et fiscalité à valider (point d'attention n° 34). | Demande de la propriétaire (à étudier) |

