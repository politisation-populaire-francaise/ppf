# Feuille de route du MVP 1

**Statut : validée par la propriétaire le 11 novembre 2026** (« Je valide la roadmap »), avec les précisions de la section 7 (D-210 à D-214). Elle fixe les priorités ; `docs/ROADMAP.md` reste le suivi détaillé de tout ce qui a été fait.

Un « MVP » est la première version réellement utilisable par le public : peu de choses, mais qui marchent bien.

## 1. Le cap fixé par la propriétaire (11 novembre 2026)

Le mouvement social est massif mais désorganisé. Deux craintes : qu'il s'essouffle avant d'avoir obtenu gain de cause, et qu'un accord signé par une partie des syndicats y mette fin sans que les gens soient satisfaits. L'application doit aider à tenir un cap ferme et à mener des actions communes autres que la manifestation.

Priorités, dans l'ordre :

1. **Agir**, en entier si possible (la carte peut venir plus tard), avec deux ajouts mis en avant : **fédérer son entourage** et **s'engager en politique** (s'inscrire pour voter, se présenter, constituer une liste citoyenne, créer ou rejoindre un parti).
2. **Décider** : revendications, pétitions officielles, Constitution Citoyenne et Démocratique.
3. **Comprendre** : les textes qui portent ce cap (limites de la manifestation, répression, autres formes d'action, mouvements dans le monde, mode d'emploi de l'engagement politique).
4. **Ergonomie irréprochable** sur téléphone, tablette et ordinateur. Constat sur son Samsung Galaxy : l'application paraît trop grande, et les barres du haut et du bas passent sous les barres du téléphone.

## 2. Ce qu'un citoyen pourra faire avec le MVP 1

Sans compte : lire, s'informer, trouver une action. Avec un compte pseudonyme : proposer, voter, s'organiser.

- **Trouver une action et s'y joindre** : agenda des mobilisations, appels (grève, boycott, collecte, bénévolat), organisations et collectifs près de chez soi, caisses de grève.
- **Tenir dans la durée** : simulateur du gréviste, solidarité, missions de bénévolat, suites d'une mobilisation (« Et après ? »).
- **Fédérer** : liste privée de proches à convaincre, argumentaires et réponses aux objections, contenus faciles à partager, affiches et tracts à code QR.
- **S'engager en politique** : s'inscrire sur les listes électorales, se présenter, monter une liste citoyenne, créer ou rejoindre un parti, avec les mêmes outils pour tous.
- **Se protéger** : bouton AIDE, mode manifestation, sécurité du téléphone, minuteur « sans nouvelles de moi », caméra protégée.
- **Décider** : proposer et voter les revendications (une personne, une voix), signer les pétitions officielles sur leurs sites, suivre la Constitution Citoyenne et Démocratique et délibérer sur ses mesures.
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
| **1. Une application agréable sur votre téléphone** | Une version où rien ne passe sous les barres du téléphone, à la bonne taille, qui s'ouvre sur un court tutoriel, avec En direct, l'actualité, la carte, Agir et les appels à portée de pouce | Correction des barres du système sur les trois formats (**faite le 11 novembre 2026**, D-180, à confirmer sur le téléphone) ; passage en revue des tailles (barres, marges, textes) aux dimensions du Galaxy A34 et avec les réglages « grande police » ; **tutoriel du premier lancement** (voir plus bas ; **codé le 11 novembre 2026**, D-184, textes en attente de l'avis de la propriétaire) ; nouvelle barre du bas « En direct, Actu, Carte, Agir, Appels » et messagerie et bouton AIDE rouge dans la barre du haut (**codées le 11 novembre 2026**, D-183, bouton AIDE à plat D-181, nom en entier D-182) ; **rien n'est retiré** : tout ce qui est déjà codé reste actif et visible ; captures d'écran automatiques des écrans prioritaires | 3 à 5 jours |
| **2. Agir** | Tout l'espace Agir, hors carte en direct | Entrée de la branche en attente (collectifs, minuteur, caméra protégée, simulateur du gréviste, affiche à code QR, « Et après ? », proches à mobiliser, 13 guides réels) ; kit contre le harcèlement et créneaux de bénévolat à finir ; **nouvelle rubrique « Fédérer »** ; **nouvelle rubrique « S'engager en politique »** ; passe d'ergonomie sur chaque écran | 1 à 2 semaines |
| **3. Décider** | Revendications, pétitions officielles et Constitution Citoyenne et Démocratique mises en avant | Entrée de la branche en attente (rappels, courriers aux élus, consultations, pétitions locales, vraies pétitions) ; les trois fonctions phares en tête de l'espace et sur l'accueil ; délibération sur les mesures de la Constitution Citoyenne et Démocratique ; passe d'ergonomie | 1 semaine, en partie en parallèle du jalon 2 |
| **4. Comprendre : les textes du cap** | La série de textes prioritaires, lisible dans l'application et sur le site | Entrée des 124 vrais contenus ; série prioritaire (section 5) ; fiches de « S'engager en politique » et de « Fédérer » ; dossiers de recherche sourcés | En continu, dès le jalon 0 |
| **5. Mise en ligne** | L'application reliée à un vrai serveur ; le site visible | Dépôt GitHub et vérifications automatiques ; serveur ; préproduction fermée ; textes légaux minimum (mentions légales, confidentialité, conditions d'utilisation, charte) ; relecture des propositions des citoyens par l'équipe ; puis ouverture selon la décision 1 | 1 semaine après les actions de la propriétaire (GitHub, serveur) |
| **6. Ouverture et retours** | La version ouverte, et la liste des retours traités | Corrections rapides, mesure de ce qui sert vraiment, préparation du MVP 2 | Ensuite |

Les jalons 1 à 4 avancent sans serveur. Le jalon 5 est le chemin critique : sans lui, le MVP n'est pas « fonctionnel » pour les autres. Il peut démarrer dès que la propriétaire le décide, en parallèle du reste.

### Détail du jalon 1 : barres de navigation et tutoriel du premier lancement

**Barre du bas du téléphone** (décision de la propriétaire du 11 novembre 2026, D-215), dans cet ordre :

| Place | Entrée | Remarque |
|---|---|---|
| 1 | **En direct** | Premier écran après le tutoriel |
| 2 | **Actu** | Nouveau nom du fil social |
| 3 | **Carte** | Reste au centre |
| 4 | **Agir** | Remplace « Revendications », qui reste accessible par Décider et par le menu |
| 5 | **Appels** | Appels à la grève, à manifester, à soutenir, à bloquer, etc. |

**Barre du haut** : la messagerie y monte, à côté du bouton **AIDE**, qui est mis en valeur et ressort en rouge. Voir, Comprendre, Décider, Revendications et Mon espace restent accessibles (menu et avatar) : rien de ce qui est codé n'est retiré.

**Tutoriel du premier lancement** (D-211, ordre fixé par la propriétaire le 11 novembre 2026, D-216) : « un rapide tutoriel de prise en main de l'appli, de son objectif et du contexte, pas à pas ».

1. **Pourquoi cette application** : le moment que vit le pays, et l'objectif (reprendre l'espace politique, ensemble).
2. **Ici, vous êtes chez vous** : aucun compte demandé pour lire, aucun traceur, un pseudonyme si l'on veut participer.
3. **Agir** : trouver une action près de chez soi, tenir, fédérer son entourage, s'engager en politique ; avec la **carte**, qui montre les manifestations en cours et, idéalement, les rues bloquées.
4. **Se réunir** : rejoindre un collectif, une association ou un syndicat, ou en créer un ; communiquer (salons privés, publics, réservés ; messagerie hors réseau).
5. **Décider** : voter les revendications, signer les pétitions officielles, peser sur les candidats avec la Constitution Citoyenne et Démocratique.
6. **Aide** : urgence, street medics, avocat.
7. **Par où commencer ?** : la phrase « Je veux… » au-dessus de quatre boutons, « agir », « donner mon avis », « comprendre », « m'allier », qui mènent au bon endroit.

Règles : une idée par écran, moins d'une minute en tout, « Passer » toujours visible, lisible sans réseau, rejouable depuis Mon espace et la feuille AIDE, utilisable au lecteur d'écran et au clavier, textes externalisés.

État au 11 novembre 2026 (branche du socle, pas encore dans `main`) : barres, espace « Appels », menu et tutoriel codés et testés aux trois formats et en grande police (D-181 à D-184) ; « m'allier » ouvre la page « Se réunir » d'Agir ; reste la passe de densité (« l'application est trop grande »).

Honnêteté envers l'utilisateur : le tutoriel ne présente comme disponible que ce qui marche. Le 11 novembre 2026, ne sont pas encore codés : les rues bloquées sur la carte, la messagerie hors réseau (bitchat, phase 4), l'alerte aux street medics et l'alerte « Besoin d'un avocat » (conçues, elles demandent des organisations partenaires et la relecture d'un avocat). Ils apparaissent dans le tutoriel avec la mention « Bientôt », jusqu'à leur livraison.

### Détail du jalon 2 (Agir)

| Rubrique | Déjà dans `main` | En attente (à faire entrer) | À créer |
|---|---|---|---|
| Se mobiliser | Agenda, proposer un événement, appels, annuaire des organisations, vue carte sur Android (jamais essayée sur un téléphone) | Collectifs, affiche et tract à code QR, « Et après ? » | Rappel d'un événement |
| Tenir | Caisses de grève et de solidarité, missions de bénévolat | Simulateur du gréviste, 13 guides réels (faire grève, déclarer une manifestation, créer une association, coller des affiches, collecter de l'argent, etc.) | Créneaux de bénévolat |
| **Fédérer** (nouveau) | | « Mes proches à mobiliser » (liste privée, sur l'appareil seulement) | Kit « Convaincre autour de soi » : argumentaires courts, réponses aux objections, « le monde bouge » ; cartes à partager |
| **S'engager en politique** (nouveau, mis en avant) | Parcours « S'inscrire et voter » et « Se présenter à une élection », calendrier électoral | Rappels d'inscription | Dans l'ordre du calendrier électoral (voir plus bas) : s'inscrire sur les listes électorales et vérifier son inscription, procuration ; puis se présenter et constituer une liste de candidats ; créer ou rejoindre un parti (fiches identiques pour tous les partis) ; trouver des colistiers par les collectifs |
| Se protéger | Bouton AIDE, mode manifestation, verrouillage, effacement rapide, application discrète, « Besoin de parler ? » | Minuteur « sans nouvelles de moi », caméra protégée | Kit contre le harcèlement (à finir) |

### Détail du jalon 2 : « S'engager en politique » dans l'ordre du calendrier

Décisions de la propriétaire du 11 novembre 2026 : d'abord s'inscrire sur les listes électorales, ensuite créer des listes de candidats ; les priorités suivent le calendrier des élections. Calendrier relevé le 11 novembre 2026 sur les sources officielles (dossier de recherche « droit électoral et candidatures »), à revérifier avant chaque publication :

| Ordre | Échéance | Ce que l'application propose |
|---|---|---|
| 1 | **Inscription sur les listes électorales** : au plus tard le vendredi 12 mars 2027 pour la présidentielle (sixième vendredi avant le scrutin, code électoral, article L17 ; date à confirmer par le décret de convocation, pas encore publié) | S'inscrire, vérifier son inscription, se réinscrire après un déménagement, procuration, rappels |
| 2 | **Présidentielle** : 18 avril et 2 mai 2027 (dates annoncées) | Constitution Citoyenne et Démocratique, comparateur, calendrier, parrainages |
| 3 | **Législatives anticipées** : seulement si l'Assemblée est dissoute (scrutin 20 à 40 jours après la dissolution) ; sinon, législatives au plus tard en juin 2029 | Kit du candidat citoyen prêt à servir, « mode dissolution » |
| 4 | **Départementales et régionales** : mars 2028 (binômes femme-homme, listes paritaires) ; financement de campagne à partir de septembre 2027 | Constituer un binôme ou une liste, mandataire financier, compte de campagne |
| 5 | **Élections partielles** (municipales surtout), à tout moment ; européennes en juin 2029 ; municipales en mars 2032 | Guide de la liste citoyenne, retours d'expérience des listes citoyennes de 2020 et de 2026 |
| En continu | Créer ou rejoindre un parti | Mode d'emploi (association, mandataire ou association de financement, comptes), fiches identiques pour tous les partis |

Correction d'une affirmation de la première version de ce document : depuis la dissolution de juin 2024, les législatives ne suivent plus automatiquement la présidentielle. Il n'y en aura en 2027 que si le nouveau président dissout l'Assemblée.

Cadre légal à respecter (code électoral, article L52-8) : une association ne peut fournir ni don ni avantage à la campagne d'un candidat. L'outil est donc le même pour toutes les listes et tous les partis, aux mêmes conditions, sans soutien de PPF à l'un d'eux (décision de la propriétaire : « pour l'instant oui »). Aucune décision de justice ne valide expressément ce montage : à faire confirmer par un avocat et par la CNCCFP avant l'ouverture publique.

### Détail du jalon 3 (Décider)

- **Revendications** : mur, proposition guidée, vote anonyme « une personne, une voix » (faits) ; à faire : mise en avant, statut posé par l'équipe, pétition officielle épinglée quand une revendication prend de l'ampleur. Question à trancher au jalon 3 : comment éviter un mur vide le jour de l'ouverture (par exemple en y présentant, sourcées et attribuées à leurs auteurs, les revendications déjà portées par le mouvement, que les citoyens votent).
- **Pétitions officielles** : règles des cinq plateformes et seuils (faits), 4 vraies pétitions (en attente), relevé automatique des compteurs (avec le serveur), rappels.
- **Constitution Citoyenne et Démocratique** : tableau public par candidat (fait) ; à faire : délibération citoyenne sur les mesures (proposer, amender, voter), page du site.
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

Règle fixée par la propriétaire le 11 novembre 2026 (elle remplace celle du 9) : « Pour l'instant tout est rédigé par ia on doit aller très très vite pas le choix, puis les humains reliront et reprendront les textes au fur et à mesure. » Les contenus factuels sont affichés « Rédigé par l'IA » ; les textes de position sont affichés « Position de PPF, rédigée par l'IA, en attente de relecture ». Dans tous les cas : chaque fait porte sa source, une qualification politique est présentée comme l'opinion de PPF et non comme un fait, aucune personne n'est qualifiée sans source, aucun appel à la violence. Chaque chiffre des dossiers de recherche est relu sur sa source avant d'entrer dans un texte.

## 6. Ce qui sort du MVP 1

Rien n'est supprimé : ces parties restent dans le code et reviendront au MVP 2.

- **Agir** : carte de la manifestation en direct, zones dangereuses et sorties, street medics et avocats certifiés, alerte à mes proches, soutien aux personnes interpellées, témoignages publiés. Raisons : elles dépendent de la carte, d'organisations partenaires, d'une relecture par un avocat ou d'une équipe de modération.
- **Voir** (observatoire, partis, Parlement), **fil social**, **messagerie**, **cercles** : pas de nouveau développement, mais ils restent actifs, opérationnels et visibles (décision de la propriétaire du 11 novembre 2026) : « Actu » dans la barre du bas, la messagerie dans la barre du haut, le reste par le menu.
- **Administration avancée** : suite du service de signature, back-office complet, modération par IA.
- **iOS** (les personnes qui ont un iPhone passent par le site), Nostr, Tor, messagerie sans Internet.

## 7. Décisions de la propriétaire

Décisions prises le 11 novembre 2026 (consignées dans `docs/DECISIONS.md`, D-210 à D-217) :

| Sujet | Décision | Ses mots |
|---|---|---|
| Feuille de route | Validée. | « Je valide la roadmap » |
| Mémoire de l'ordinateur | Ne jamais dépasser la mémoire disponible : une seule compilation à la fois, seuil de 6 Go libres avant de compiler, rien de nouveau pendant une compilation. | « mes barrettes de rames sont défectueuses, ne depasse jamais la RAM dispo de mon pc sinon il crash » |
| Ce qui existe déjà | Rien n'est masqué ni désactivé. | « Tout ce qui a déjà été codé doit rester actif, operationnel et visible pour le moment » |
| Premier écran | Un tutoriel de prise en main au premier lancement, à la place du fil social ; ordre du tutoriel fixé par elle (détail du jalon 1). | « il me parait bien mieux de faire un rapide tutoriel avec de prise en main de l'appli, de son objectif et du contexte, pas à pas » |
| Barre du bas | En direct, Actu (nouveau nom du fil), Carte au centre, Agir (à la place de Revendications), Appels. | « En direct doit apparaitre en premier », « "Fil" doit s'appeler Actu », « La map reste au centre », « "Agir" qui remplace "revendication" », « Puis enfin "Appels" (a la grève, manifester, soutenir, bloquer etc) » |
| Barre du haut | Messagerie et bouton AIDE ; AIDE en rouge, mis en valeur. | « La messagerie doit passer en top bar ainsi que le bouton Aide, et Aide doit etre mis en valeur et ressortie en rouge. » |
| Textes | Tout est rédigé par l'IA pour l'instant, y compris les textes de position ; les humains relisent ensuite. | « Pour l'instant tout est rédigé par ia on doit aller très très vite pas le choix, puis les humains reliront et reprendront les textes au fur et à mesure. » |
| « S'engager en politique » | Le même outil pour toutes les listes et tous les partis, sans soutien de PPF à l'un d'eux, pour l'instant. L'inscription sur les listes électorales passe en premier, puis les listes de candidats ; l'ordre suit le calendrier des élections. | « pour l'instant oui » ; « il faut d'abord s'inscrire en priorité sur les listes électorales, et ensuite creer des listes de candidats » ; « mettons les priorités en fonction du calendrier des elections » |
| Ouverture | Une bêta sur invitation d'abord. | « Oui ca sera une beta sur invitation au debut » |
| Serveur | Acheté par la propriétaire le 12 novembre 2026, en suivant `docs/guides/serveur-hostinger-kvm1.md` (offre KVM 2 retenue le 7 novembre). | « Oui j'achète le serveur demain » |
| Modération | Règle déjà fixée (cahier des charges 5.6, `CLAUDE.md` section 7), rappelée par elle : l'IA filtre, l'équipe tranche ce que l'IA met en alerte, et les utilisateurs signalent. | « C'est l'ia qui filtre et l'equipe qui tranche ce qui est mis en alerte par l'ia, et y'a aussi les signalements des users » |
| Téléphone de référence | Samsung Galaxy A34. | « J'ai un samsung galaxy A34 » |
| Sources | Pas d'abonnement à Mediapart ; elle fournira des captures d'écran de publications Instagram des médias de confiance. | « je te donnerai des captures ecrans des slides insta » |

Reste à trancher avant l'ouverture publique, sans urgence pour la bêta : l'hébergement du modèle d'IA de modération (brainstorming du cahier des charges, entrée du 4 novembre 2026). Pendant la bêta sur invitation, la relecture humaine des files existantes suffit.

Vocabulaire : en droit, les « listes électorales » sont les registres des électeurs ; les listes que l'on présente à une élection sont des **listes de candidats** (ou listes citoyennes). Le MVP traite les deux, dans cet ordre.

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
