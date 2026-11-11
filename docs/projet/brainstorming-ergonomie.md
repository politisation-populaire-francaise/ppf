# Brainstorming : ergonomie et parcours utilisateur

Brouillon de travail de la session « Ergonomie ». On y note les constats, les propositions et les questions sur l'ergonomie (téléphone d'abord, puis ordinateur et site). Rien de ce qui figure ici n'est validé tant que l'entrée n'a pas le statut « Intégré ».

Statuts possibles : Idée, Question, À étudier, À valider, Intégré le [date], Rejeté le [date].

---

### [2026-11-11] E-01 Constats de la propriétaire sur le téléphone
- Statut : Question (relevé, sert de base aux entrées suivantes)
- Ses mots, résumés : elle aime l'identité visuelle ; tutoriel bien, mais contenu trop long, pas assez fédérateur, à mettre dans une belle carte blanche moderne ; barres du haut et du bas bien, bouton AIDE un peu trop gros ; menu latéral trop pauvre, sans sous-rubriques, sans indicateur de sous-menu, et Comprendre ouvre sa page au lieu de se déplier ; Mon espace n'est qu'un écran de réglages, il doit devenir un intranet personnel avec tableau de bord, widgets et un parcours guidé gamifié du citoyen démocrate ; pages trop chargées, décourageantes ; boutons trop gros ; s'inspirer des réseaux sociaux et des médias ; aucun parcours utilisateur n'a jamais été défini.
- Contrainte : les captures d'écran sont bloquées par l'application sur son téléphone (protection volontaire). Les captures se feront par les tests automatiques sur l'ordinateur.

### [2026-11-11] E-02 Menu latéral en sept rubriques avec sous-rubriques en accordéon
- Statut : Intégré le 2026-11-11 (D-240), à l'essai
- Concerne : `Space.kt`, `Sections.kt`, `AdaptiveShell.kt` (tiroir, barre latérale du bureau) ; vision (quatre piliers Voir, Comprendre, Agir, Décider) ; décisions D-210, D-215
- **Contradiction à signaler** : la vision et le cahier fonctionnel organisent la plateforme en quatre piliers (Voir, Comprendre, Agir, Décider). Les sept rubriques demandées (S'informer, Comprendre, Témoigner, Revendiquer, S'unir et s'organiser, Se politiser, Agir) reprennent celles du tutoriel version 2, mais font disparaître « Décider » comme rubrique (ses outils vont dans Revendiquer et Se politiser) et renomment « Voir » en « S'informer ». À confirmer par la propriétaire ; les documents définitifs ne changent qu'avec son accord.
- Fonctionnement proposé : toucher une rubrique la déplie (chevron vers le bas, vers le haut une fois ouverte) ; une seule rubrique ouverte à la fois ; la première sous-rubrique est toujours « Accueil de … » (la page actuelle de l'espace) ; la rubrique de l'écran courant est ouverte à l'arrivée ; champ de recherche en tête ; Mon espace et réglages en pied de menu. Les rubriques sans sous-rubrique n'ont pas de chevron (aucune dans la proposition).
- Sous-rubriques proposées, toutes construites sur des écrans qui existent déjà (« Bientôt » sinon) :
  - **S'informer** (ex-Voir) : Accueil ; Actu ; Le mouvement (chiffres, chronologie, victoires, dans le monde) ; Le pouvoir (partis, responsables publics, textes en discussion) ; Le Parlement (parlementaires, cette semaine, mes élus ont voté) ; Libertés et contre-pouvoirs ; Vérifier une info reçue ; Transparence de PPF.
  - **Comprendre** : Accueil ; Le cap du mouvement ; Comprendre en 5 minutes ; Qui décide ? ; Mes élus ; Connaître ses droits (fiches pratiques) ; Le vrai du faux ; Histoire des luttes ; Glossaire.
  - **Témoigner** : Envoyer un témoignage ; Caméra protégée ; Harcèlement en ligne (kit de preuves) ; Appels à témoins (Bientôt) ; Mes témoignages (Bientôt).
  - **Revendiquer** : Toutes les revendications ; Proposer une revendication ; Mes revendications ; Constitution Citoyenne et Démocratique ; Pétitions officielles ; Pétitions locales ; Consultations publiques.
  - **S'unir et s'organiser** : Messages ; Collectifs ; Organisations, associations, syndicats ; Fédérer mon entourage ; Mes proches à mobiliser ; Solidarité et caisses de grève ; Je veux aider.
  - **Se politiser** : S'engager en politique (listes électorales, candidatures, partis) ; Prochaines élections ; Comparer les candidats ; Interpeller mes élus ; Demander un document administratif.
  - **Agir** : Appels ; Carte et agenda des mobilisations ; Proposer une mobilisation ; Kits d'action citoyenne ; Simulateur du gréviste ; Mode manifestation ; Sans nouvelles de moi.
- Sur le recoupement Revendiquer, Témoigner, S'organiser et Agir : recommandation de garder les sept, car ils forment une progression lisible (de s'informer à agir) et chacun répond à une intention différente (« je veux dire ce qui doit changer », « je veux raconter ce que j'ai vu », « je veux trouver des gens », « je veux faire quelque chose aujourd'hui »). Agir est alors défini comme « les mobilisations et les actions concrètes », ce qui correspond à l'onglet Agir de la barre du bas.
- Options : A, sept rubriques telles quelles (recommandé) ; B, cinq rubriques en fusionnant Témoigner dans S'informer et Revendiquer dans Se politiser (menu plus court, mais perd la progression du tutoriel) ; C, garder les quatre piliers de la vision avec des sous-rubriques (aucune contradiction, mais ne suit pas sa demande).
- Coût : gratuit, environ une demi-journée de travail.

### [2026-11-11] E-03 Tutoriel : carte blanche et textes courts
- Statut : Intégré le 2026-11-11 (D-241), à l'essai ; textes de la propriétaire sauvegardés
- Concerne : `strings_tutoriel.xml`, écran du tutoriel ; D-186, D-216 (écrans 1 à 6 rédigés par la propriétaire elle-même)
- Proposition visuelle : fond bleu PPF, contenu dans une carte blanche arrondie (illustration en haut, titre, deux lignes de texte, points de progression, bouton « Suivant ») ; « Passer » en haut à droite.
- Proposition de contenu : six écrans, 25 mots au plus chacun, une idée par écran, ton rassembleur (« nous », pas de liste de griefs) :
  1. « Ensemble, reprenons la démocratie » : « En colère, impuissant ? Nous sommes des millions. Ici, on s'unit au-delà de nos opinions. »
  2. « Tout pour agir, au même endroit » : les sept rubriques en sept pictogrammes, trois mots chacune.
  3. « Agir près de chez vous » : « Manifestations, grèves, blocages : trouvez en un geste ce que vous pouvez faire aujourd'hui. »
  4. « Seuls on subit, ensemble on pèse » : « Rejoignez un collectif, une association, un syndicat. Échangez en sécurité. »
  5. « Votre voix compte » : « Proposez et votez les revendications. Faites signer la Constitution Citoyenne et Démocratique aux candidats. »
  6. « Protégé, toujours » : « Sans nom, sans traceur, votes jamais reliés à vous. En cas de problème : le bouton AIDE. »
  7. « Je veux… » et ses boutons (inchangé).
- Les textes longs actuels ne sont pas perdus : ils deviennent la page « Qui sommes-nous » ou l'accueil de chaque rubrique.
- Décision : textes à valider ou à réécrire par la propriétaire (les écrans 1 à 6 actuels sont les siens).

### [2026-11-11] E-04 Mon espace : tableau de bord et parcours du citoyen
- Statut : Idée générale acceptée le 2026-11-11 (« l'idée me va mais j'ai du mal à visualiser c'est un test à faire ») ; prototype à construire
- Concerne : `account/MySpaceScreen.kt` ; parcours Comprendre existant (`LearningProgressStore`) ; règles de CLAUDE.md section 7 (progression sur l'appareil, opinions politiques = données sensibles)
- Idée : Mon espace devient un tableau de bord fait de widgets que l'utilisateur ajoute, retire et range ; les réglages passent derrière une roue dentée.
- Widgets possibles : Mon parcours citoyen (progression, prochaine mission) ; Près de chez moi (prochaine mobilisation) ; Mes élus ; Compte à rebours de la prochaine élection ; Leçon du jour ; Mes revendications suivies ; Mes appels (réponses données) ; Mes collectifs ; Mes badges.
- Parcours du citoyen démocrate, premier jet : sept étapes qui suivent les sept rubriques, trois à cinq missions courtes chacune (deux minutes), un badge à chaque étape :
  1. S'informer (lire trois faits sourcés, suivre un sujet) ; 2. Comprendre (finir « Comprendre en 5 minutes », trouver qui décide) ; 3. Témoigner (savoir témoigner sans s'exposer, préparer la caméra protégée) ; 4. Revendiquer (voter pour une revendication, en proposer une) ; 5. S'unir (rejoindre un collectif, convaincre un proche) ; 6. Se politiser (vérifier son inscription sur les listes, connaître ses élus, interpeller un élu) ; 7. Agir (répondre à un appel, préparer le mode manifestation).
- Garde-fous proposés, non négociables : la progression et les badges restent sur l'appareil (ils révèlent des opinions) ; aucun classement entre utilisateurs ; badges privés par défaut, l'utilisateur peut choisir de les afficher (principe 10, avec mise en garde) ; pas de série quotidienne culpabilisante ni de notification de relance par défaut ; défis collectifs possibles seulement en chiffres agrégés (« 12 000 inscriptions vérifiées cette semaine »).
- Questions : noms des niveaux (exemple : Curieux, Éclairé, Témoin, Engagé, Organisé, Citoyen actif, Acteur) ; faut-il un parcours unique ou plusieurs (« je n'ai que 5 minutes », « je veux m'engager à fond ») ?

### [2026-11-11] E-05 Principes de design : épuré, cartes blanches, inspiration réseaux sociaux
- Statut : À valider
- Proposition de règles communes, appliquées par les composants partagés :
  - **Une idée par carte**, carte blanche arrondie sur fond légèrement teinté ; « L'essentiel en 3 lignes » d'abord, « En savoir plus » ensuite (divulgation progressive).
  - **Accueil de chaque rubrique** : trois blocs au plus visibles d'emblée, le reste derrière « Tout voir ».
  - **Boutons plus discrets** : une seule action principale pleine par écran, les autres en contour ou en texte ; hauteur visuelle réduite, zone tactile de 48 dp conservée (accessibilité).
  - **Bouton AIDE** : plus petit, toujours rouge et toujours libellé.
- Des réseaux sociaux et des médias, à garder : bandeau horizontal « à la une » en haut d'En direct (comme les stories), cartes avec image et titre court, barre du bas, glisser pour actualiser, réactions rapides, profil comme tableau de bord. À jeter : défilement infini addictif, compteurs de « j'aime » qui mettent en compétition, algorithme opaque, notifications de relance, tout piège à attention.

### [2026-11-11] E-06 Définir les parcours utilisateurs avant de redessiner les écrans
- Statut : À valider
- Proposition : écrire quatre profils et leur parcours idéal, puis vérifier chaque écran contre eux : la personne en colère qui découvre (« je veux comprendre et ne pas me sentir nulle ») ; le manifestant pressé (« où et quand, maintenant ») ; la militante organisée (« mon collectif, mes appels, mes salons ») ; l'électeur qui veut peser en 2027 (« suis-je inscrit, pour qui, quelles revendications »). Chaque parcours tient en 3 gestes au plus depuis l'ouverture de l'application.
