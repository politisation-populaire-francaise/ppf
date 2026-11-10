# Feuille de route du MVP 1

**Statut : proposition du 11 novembre 2026, à valider par la propriétaire.** Tant qu'elle n'est pas validée, elle ne remplace pas `docs/ROADMAP.md`, qui reste le suivi détaillé de tout ce qui a été fait.

Un « MVP » est la première version réellement utilisable par le public : peu de choses, mais qui marchent bien.

## 1. Le cap fixé par la propriétaire (11 novembre 2026)

Le mouvement social est massif mais désorganisé. Deux craintes : qu'il s'essouffle avant d'avoir obtenu gain de cause, et qu'un accord signé par une partie des syndicats y mette fin sans que les gens soient satisfaits. L'application doit aider à tenir un cap ferme et à mener des actions communes autres que la manifestation.

Priorités, dans l'ordre :

1. **Agir**, en entier si possible (la carte peut venir plus tard), avec deux ajouts mis en avant : **fédérer son entourage** et **s'engager en politique** (s'inscrire pour voter, se présenter, constituer une liste citoyenne, créer ou rejoindre un parti).
2. **Décider** : revendications, pétitions officielles, Pacte démocratique.
3. **Comprendre** : les textes qui portent ce cap (limites de la manifestation, répression, autres formes d'action, mouvements dans le monde, mode d'emploi de l'engagement politique).
4. **Ergonomie irréprochable** sur téléphone, tablette et ordinateur. Constat sur son Samsung Galaxy : l'application paraît trop grande, et les barres du haut et du bas passent sous les barres du téléphone.

## 2. Ce qu'un citoyen pourra faire avec le MVP 1

Sans compte : lire, s'informer, trouver une action. Avec un compte pseudonyme : proposer, voter, s'organiser.

- **Trouver une action et s'y joindre** : agenda des mobilisations, appels (grève, boycott, collecte, bénévolat), organisations et collectifs près de chez soi, caisses de grève.
- **Tenir dans la durée** : simulateur du gréviste, solidarité, missions de bénévolat, suites d'une mobilisation (« Et après ? »).
- **Fédérer** : liste privée de proches à convaincre, argumentaires et réponses aux objections, contenus faciles à partager, affiches et tracts à code QR.
- **S'engager en politique** : s'inscrire sur les listes électorales, se présenter, monter une liste citoyenne, créer ou rejoindre un parti, avec les mêmes outils pour tous.
- **Se protéger** : bouton AIDE, mode manifestation, sécurité du téléphone, minuteur « sans nouvelles de moi », caméra protégée.
- **Décider** : proposer et voter les revendications (une personne, une voix), signer les pétitions officielles sur leurs sites, suivre le Pacte démocratique et délibérer sur ses mesures.
- **Comprendre** : une série de textes courts et sourcés sur le cap du mouvement, plus les fiches pratiques et les parcours déjà écrits.

## 3. Où en est-on vraiment (11 novembre 2026)

- **Beaucoup de code existe.** La branche principale (`main`, arrêtée le 9 novembre) contient déjà une première version d'Agir, de Décider, de Comprendre, de Voir, du fil, de la messagerie et de l'espace de l'équipe.
- **Beaucoup de travail attend sans avoir jamais été compilé** (pause du 9 novembre) : Agir (collectifs, minuteur, caméra protégée, simulateur du gréviste, affiche à code QR, « Et après ? », proches à mobiliser, 13 guides réels ; environ 23 000 lignes), Décider (rappels, courriers aux élus, consultations, pétitions locales, 4 vraies pétitions ; environ 7 000 lignes), Comprendre (124 vrais contenus, ateliers). Chaque entrée dans `main` demande une compilation complète d'environ 40 minutes, une seule à la fois sur l'ordinateur actuel.
- **Rien n'est en ligne.** Pas de serveur, pas de dépôt GitHub, pas de site public. Aujourd'hui l'application est un fichier installé à la main, avec ses contenus à l'intérieur : tout ce qui se partage entre personnes (proposer un événement, voter, lancer un appel) ne fonctionne pas encore pour de vrai.
- **L'application n'a presque pas été essayée sur un vrai téléphone** (essais reportés le 7 novembre). Le défaut des barres est confirmé dans le code : l'application dessine sous les barres du système sans laisser la place nécessaire. De plus, sur téléphone, Agir, Décider et Comprendre sont rangés dans un menu, alors que la barre du bas montre Fil, En direct, Carte, Messages et Revendications.
- **Les anciennes sessions de développement sont archivées.** Une nouvelle équipe, plus resserrée, repart des notes de passation.

## 4. Les jalons

Chaque jalon se termine par une version d'essai (APK) envoyée à la propriétaire, avec la liste de ce qu'elle peut tester. Les durées sont des ordres de grandeur, pas des promesses : elles dépendent surtout de l'ordinateur (une compilation à la fois) et des actions de la propriétaire au jalon 5.

| Jalon | Ce que vous pourrez tester à la fin | Contenu | Ordre de grandeur |
|---|---|---|---|
| **0. Remise en route** | Cette feuille de route, validée | Nouvelle équipe, règles du 9 novembre entrées dans `main` (fait), recherches sur le mouvement lancées (fait), décisions de la section 7 | 1 jour |
| **1. Une application agréable sur votre téléphone** | Une version où rien ne passe sous les barres du téléphone, à la bonne taille, avec Agir, Décider et Comprendre accessibles en un geste | Correction des barres du système sur les trois formats ; passage en revue des tailles (barres, marges, textes) aux dimensions des Galaxy et avec les réglages « grande police » ; nouvelle navigation du MVP (section 7, décision 2) ; accueil qui mène tout de suite à l'action ; ce qui n'est pas prêt est retiré de la vue ; captures d'écran automatiques des écrans prioritaires | 2 à 4 jours |
| **2. Agir** | Tout l'espace Agir, hors carte en direct | Entrée de la branche en attente (collectifs, minuteur, caméra protégée, simulateur du gréviste, affiche à code QR, « Et après ? », proches à mobiliser, 13 guides réels) ; kit contre le harcèlement et créneaux de bénévolat à finir ; **nouvelle rubrique « Fédérer »** ; **nouvelle rubrique « S'engager en politique »** ; passe d'ergonomie sur chaque écran | 1 à 2 semaines |
| **3. Décider** | Revendications, pétitions officielles et Pacte mis en avant | Entrée de la branche en attente (rappels, courriers aux élus, consultations, pétitions locales, vraies pétitions) ; les trois fonctions phares en tête de l'espace et sur l'accueil ; délibération sur les mesures du Pacte ; passe d'ergonomie | 1 semaine, en partie en parallèle du jalon 2 |
| **4. Comprendre : les textes du cap** | La série de textes prioritaires, lisible dans l'application et sur le site | Entrée des 124 vrais contenus ; série prioritaire (section 5) ; fiches de « S'engager en politique » et de « Fédérer » ; dossiers de recherche sourcés | En continu, dès le jalon 0 |
| **5. Mise en ligne** | L'application reliée à un vrai serveur ; le site visible | Dépôt GitHub et vérifications automatiques ; serveur ; préproduction fermée ; textes légaux minimum (mentions légales, confidentialité, conditions d'utilisation, charte) ; relecture des propositions des citoyens par l'équipe ; puis ouverture selon la décision 1 | 1 semaine après les actions de la propriétaire (GitHub, serveur) |
| **6. Ouverture et retours** | La version ouverte, et la liste des retours traités | Corrections rapides, mesure de ce qui sert vraiment, préparation du MVP 2 | Ensuite |

Les jalons 1 à 4 avancent sans serveur. Le jalon 5 est le chemin critique : sans lui, le MVP n'est pas « fonctionnel » pour les autres. Il peut démarrer dès que la propriétaire le décide, en parallèle du reste.

### Détail du jalon 2 (Agir)

| Rubrique | Déjà dans `main` | En attente (à faire entrer) | À créer |
|---|---|---|---|
| Se mobiliser | Agenda, proposer un événement, appels, annuaire des organisations, vue carte sur Android (jamais essayée sur un téléphone) | Collectifs, affiche et tract à code QR, « Et après ? » | Rappel d'un événement |
| Tenir | Caisses de grève et de solidarité, missions de bénévolat | Simulateur du gréviste, 13 guides réels (faire grève, déclarer une manifestation, créer une association, coller des affiches, collecter de l'argent, etc.) | Créneaux de bénévolat |
| **Fédérer** (nouveau) | | « Mes proches à mobiliser » (liste privée, sur l'appareil seulement) | Kit « Convaincre autour de soi » : argumentaires courts, réponses aux objections, « le monde bouge » ; cartes à partager |
| **S'engager en politique** (nouveau, mis en avant) | Parcours « S'inscrire et voter » et « Se présenter à une élection », calendrier électoral | Rappels d'inscription | Kit du candidat citoyen (législatives d'abord), guide de la liste citoyenne, créer ou rejoindre un parti (fiches identiques pour tous les partis), trouver des colistiers par les collectifs |
| Se protéger | Bouton AIDE, mode manifestation, verrouillage, effacement rapide, application discrète, « Besoin de parler ? » | Minuteur « sans nouvelles de moi », caméra protégée | Kit contre le harcèlement (à finir) |

### Détail du jalon 3 (Décider)

- **Revendications** : mur, proposition guidée, vote anonyme « une personne, une voix » (faits) ; à faire : mise en avant, statut posé par l'équipe, pétition officielle épinglée quand une revendication prend de l'ampleur. Question à trancher au jalon 3 : comment éviter un mur vide le jour de l'ouverture (par exemple en y présentant, sourcées et attribuées à leurs auteurs, les revendications déjà portées par le mouvement, que les citoyens votent).
- **Pétitions officielles** : règles des cinq plateformes et seuils (faits), 4 vraies pétitions (en attente), relevé automatique des compteurs (avec le serveur), rappels.
- **Pacte démocratique** : tableau public par candidat (fait) ; à faire : délibération citoyenne sur les mesures (proposer, amender, voter), page du site.
- Suivent dans le même espace : calendrier électoral et rappels, interpeller ses élus, consultations publiques, pétitions locales.

## 5. Les textes prioritaires de « Comprendre »

Série proposée (titres de travail), chaque texte court, sourcé, relié à une action possible dans l'application :

1. Ce que la manifestation a déjà gagné, et ses limites.
2. Maintien de l'ordre et libertés : les faits, selon l'ONU, le Défenseur des droits, Amnesty International, la Ligue des droits de l'homme.
3. Ce qui a fait céder un pouvoir : grèves, boycotts, blocages, votes, dans l'histoire sociale française.
4. Le monde bouge : tour du monde des mouvements populaires de 2024 à 2026, avec leurs victoires et leurs échecs.
5. Qui négocie en notre nom ? Comment un mouvement garde la main sur ses revendications.
6. Prendre les mairies, les départements, les régions, l'Assemblée : le mode d'emploi.
7. Fédérer autour de soi : pourquoi convaincre son entourage, c'est agir.

Quatre recherches documentaires ont été lancées le 11 novembre 2026 pour les nourrir (mouvement social de l'automne 2026 ; maintien de l'ordre et libertés ; mouvements populaires dans le monde ; droit électoral et candidatures). Elles s'appuient d'abord sur les médias et organisations désignés par la propriétaire (Mediapart, L'Humanité, Le Média, Blast, Off Investigation, Contre Attaque, Cerveaux non disponibles, Amnesty International, Ligue des droits de l'homme, ONU) et sur les sources officielles ; chaque fait porte sa source et son statut (établi, allégué, contesté).

Règle en vigueur depuis le 9 novembre (CLAUDE.md, section 7) : les contenus factuels sont rédigés par l'IA, sourcés et affichés « Rédigé par l'IA » ; **les prises de position de PPF ne sont jamais rédigées par l'IA**. Plusieurs textes de la série sont des prises de position : voir la décision 3.

## 6. Ce qui sort du MVP 1

Rien n'est supprimé : ces parties restent dans le code et reviendront au MVP 2.

- **Agir** : carte de la manifestation en direct, zones dangereuses et sorties, street medics et avocats certifiés, alerte à mes proches, soutien aux personnes interpellées, témoignages publiés. Raisons : elles dépendent de la carte, d'organisations partenaires, d'une relecture par un avocat ou d'une équipe de modération.
- **Voir** (observatoire, partis, Parlement), **fil social**, **messagerie**, **cercles** : laissés en l'état, sans nouveau développement, hors de la vue principale selon la décision 2.
- **Administration avancée** : suite du service de signature, back-office complet, modération par IA.
- **iOS** (les personnes qui ont un iPhone passent par le site), Nostr, Tor, messagerie sans Internet.

## 7. Décisions à prendre par la propriétaire

| N° | Question | Options | Recommandation |
|---|---|---|---|
| 1 | **Comment ouvre-t-on ?** | A. Bêta fermée sur invitation (quelques dizaines de personnes de confiance), puis ouverture publique. B. Ouverture publique directe. C. Essais privés seulement pour l'instant. | **A.** Tant que l'association n'est pas déclarée et que les textes n'ont pas été relus par un avocat, c'est la propriétaire qui porte personnellement la responsabilité de ce qui est publié. Une bêta fermée permet d'aller vite en limitant ce risque. |
| 1 bis | **Serveur** | Le guide prévoit un serveur Hostinger KVM 1 (131,76 € HT pour 24 mois, prix relevé le 7 novembre, à revérifier). La propriétaire avait parlé d'un KVM 2. | L'acheter au début du jalon 5, pas avant. Aucun achat sans son accord. |
| 2 | **Navigation du MVP sur téléphone** | A. Barre du bas : Accueil, Agir, Décider, Comprendre, Mon espace ; le reste (Voir, Fil, Messages, Carte) dans un menu « Plus ». B. Garder la barre actuelle (Fil, En direct, Carte, Messages, Revendications) et les piliers dans le menu. C. Barre : Accueil, Agir, Revendications, Comprendre, Plus. | **A.** Elle met les trois priorités à un geste et allège l'écran. Elle revient sur la décision du 6 novembre (fil social en écran par défaut). |
| 3 | **Qui écrit les textes de position ?** | A. La propriétaire les écrit ; l'IA fournit les faits sourcés et un plan. B. L'IA rédige tout, la propriétaire valide, mention « Rédigé par l'IA » (il faut alors modifier la règle du 9 novembre). C. Mixte : les textes de faits par l'IA, et un court texte « Le cap de PPF » écrit ou dicté par la propriétaire. | **C.** Les faits sourcés vont vite et sont solides ; la position engage le collectif et gagne à être dite avec ses mots. |
| 4 | **« S'engager en politique » : pour qui ?** | A. Le même outil pour toutes les listes et tous les partis, sans soutien de PPF à l'un d'eux. B. PPF choisit les listes ou les partis qu'elle met en avant. | **A.** Une association n'a pas le droit d'aider la campagne d'un candidat (code électoral, article L52-8) : un outil ouvert à tous aux mêmes conditions est la voie sûre. À confirmer par la vérification juridique en cours, puis par un avocat. |
| 5 | **Votre téléphone** | Modèle du Galaxy, 2 ou 3 captures d'écran du problème, et si la taille de police ou le zoom d'écran ont été agrandis dans les réglages. | Sert à régler les tailles au plus juste dès le jalon 1. |

À préciser plus tard, sans urgence : qui relit les propositions des citoyens à l'ouverture (jalon 5) ; calendrier de la déclaration de l'association.

Deux précisions de vocabulaire et de calendrier, à confirmer par la vérification juridique en cours :

- En droit, les « listes électorales » sont les registres des électeurs. Ce que la propriétaire décrit s'appelle des **listes de candidats** (ou listes citoyennes). Le MVP traite les deux : s'inscrire pour voter, et se présenter.
- Les élections municipales viennent d'avoir lieu (mars 2026) ; les prochaines sont prévues en 2032. Les scrutins les plus proches où des citoyens peuvent se présenter sont les législatives qui suivent la présidentielle d'avril 2027, puis les départementales et régionales (2028). Le kit commence donc par les législatives.

## 8. Organisation de l'équipe

Une session principale (chef d'équipe, seul interlocuteur de la propriétaire) et des sessions par bloc :

| Session | Bloc | Démarre |
|---|---|---|
| Socle et ergonomie | Coquille de l'application, navigation, tailles, accessibilité, versions d'essai | Jalon 1 |
| Agir | Jalon 2 | Dès que le jalon 1 est dans `main` |
| Décider | Jalon 3 | Après l'entrée d'Agir |
| Comprendre et contenus | Jalon 4 | Dès que les recherches sont revenues |
| Mise en ligne | Jalon 5 | À la décision de la propriétaire |

Les règles de l'équipe restent celles du registre commun : une seule compilation à la fois sur l'ordinateur, toujours par le lanceur protégé ; une seule entrée dans `main` à la fois, après une compilation complète et des tests au vert ; tout est commité au moins toutes les 30 minutes.

## 9. Risques

- **L'ordinateur** (plantages, une compilation à la fois) : c'est le premier frein. Le nouvel ordinateur prévu lèverait cette limite.
- **Le code jamais compilé** : chaque entrée peut révéler des erreurs ; les durées des jalons 2 et 3 en tiennent compte, sans garantie.
- **Le juridique** : ouverture avant la déclaration de l'association et sans relecture par un avocat (décision 1) ; textes qui mettent en cause des personnes ou des institutions (méthode M17, sources solides, pas de qualification sans source).
- **La modération** : dès que des citoyens publient, il faut des personnes pour relire.
- **Le mur vide** : une application d'action collective vaut par ce qu'on y trouve le premier jour (agenda, appels, organisations réels). À préparer avec des organisations partenaires avant l'ouverture.
