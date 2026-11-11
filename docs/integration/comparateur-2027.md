# Comparateur de la présidentielle 2027 (DE-16) : conception

- Pôle « Décider », 7 novembre 2026. À valider par le chef d'équipe (questions de la section 7) avant tout code.
- Sources :
  - cahier fonctionnel 2.0, M13.7 (DE-16) et règle de méthode 9 (traitement identique) ;
  - cahier des charges, section 9.2 (droit électoral et financement politique) ;
  - données des votes de « Voir » (M20, D-064, D-065, dans `main` depuis le 7 novembre 2026) ;
  - Constitution Citoyenne et Démocratique (DE-08, D-060, D-062).

## 1. Le cadre légal vérifié (Légifrance, version en vigueur au 7 novembre 2026)

- **Code électoral, article L52-8, alinéa 2** : les personnes morales autres que les partis « ne peuvent participer au financement de la campagne électorale d'un candidat, ni en lui consentant des dons sous quelque forme que ce soit, ni en lui fournissant des biens, services ou autres avantages directs ou indirects à des prix inférieurs à ceux qui sont habituellement pratiqués ».
  - D'où la règle : rien qu'un candidat pourrait recevoir de plus qu'un autre (place, ordre, mise en forme, mots, lien vers ses dons).
  - Une publication gratuite et identique pour tous est-elle un « avantage » au sens de cet article ? Question à faire trancher par un avocat avant la mise en ligne (point 9.8 du cahier des charges).
- **Article L48-1** : les règles de propagande s'appliquent « à tout message ayant le caractère de propagande électorale diffusé par tout moyen de communication au public par voie électronique ». Le comparateur ne doit donc jamais être un message de propagande : il ne dit pas pour qui voter.
- **Article L52-1, alinéa 1** : la publicité commerciale à des fins de propagande est interdite dans la presse et l'audiovisuel pendant les six mois qui précèdent le mois de l'élection. Conséquences pour PPF :
  - aucune publicité payée pour promouvoir le comparateur pendant cette période ;
  - jamais aucune publicité pour un candidat.
- **Article L49** : aucun message de propagande électronique « à partir de la veille du scrutin à zéro heure ». Le comparateur n'en est pas un, mais par prudence il reste figé (aucune mise à jour du contenu) de la veille du scrutin jusqu'à la fermeture des bureaux. C'est une option (Q4).
- **Article L47 A** : la campagne officielle s'ouvre le deuxième lundi qui précède le scrutin.
- **Non vérifié** (à chercher dans la loi n° 62-1292 du 6 novembre 1962 et le décret n° 2001-213 du 8 mars 2001 avant le développement) :
  - qui publie la liste officielle des candidats, et quand ;
  - la date du premier tour de 2027 : aucune date n'est tenue pour officielle tant que le décret de convocation n'est pas publié.

## 2. Ce que le comparateur montre (mêmes rubriques, même ordre, mêmes limites pour tous)

| Rubrique | Contenu | Source |
|---|---|---|
| Identité | Nom, parti ou soutien déclaré, fonction actuelle | Liste officielle des candidats ; fiche du parti de « Voir » (M17) |
| Programme, par thème | Pour chaque thème (les mêmes pour tous, par exemple les quinze thèmes des revendications), une ou deux phrases qui résument la position, avec une **citation courte** et le lien vers le document officiel du candidat | Programme publié par le candidat (page et date) |
| Réponse à la Constitution Citoyenne et Démocratique | Signé, refusé, en attente, sans réponse, mesure par mesure | Tableau de la Constitution Citoyenne et Démocratique (D-060, D-062) |
| Votes passés | Pour un candidat parlementaire ou eurodéputé : ses votes nominatifs sur les scrutins publics liés aux thèmes, en toutes lettres ; pour les autres, la mention « pas de mandat parlementaire » ; votes du groupe du parti à part, jamais attribués à la personne | Données ouvertes de « Voir » (M20) |
| Faits | Seulement les faits de « Voir » qui visent la personne, avec la distinction établi, allégué, en cours d'enquête et le droit de réponse | « Voir » (M17) |

Règles d'affichage :
- **ordre alphabétique** des noms, toujours ;
- même longueur maximale par rubrique et la même mise en forme ;
- aucune photo, ou la même pour tous (Q3) ;
- aucun lien vers des dons ou des sites de campagne autres que le programme officiel ;
- aucune note, aucun classement, aucun « score de proximité » ;
- un texte manquant est affiché comme manquant (« pas de position publiée sur ce thème au … »), jamais comblé.

Rédaction :
- les résumés sont des contenus éditoriaux de l'équipe, sourcés, relus par deux personnes, marqués « avec l'aide d'une IA » si c'est le cas ;
- droit de réponse de chaque candidat, sur le modèle de « Voir » (D-056) ;
- journal public des corrections ;
- chaque version est horodatée (D-111).

## 3. Côté technique (aperçu)

- **Contenus** : briques signées du format de « Voir » (type 30023), `ppf-type` = `candidat` et `position` (une position par candidat et par thème), `a` vers l'élection (`ppf-type` = `election`, qui sert aussi au calendrier DE-28). Signées par l'équipe éditoriale, avec un circuit de relecture.
- **Cœur commun** : `core/decider/Comparator.kt`.
  - Il construit une grille candidats × thèmes en ordre alphabétique.
  - Une case vide reste vide.
  - Un test vérifie que chaque candidat a exactement les mêmes rubriques.
- **Interface** :
  - sur un téléphone : un thème, puis tous les candidats l'un sous l'autre, dans le même ordre ;
  - sur une tablette ou un ordinateur : un tableau, avec un candidat par colonne ;
  - lecture d'écran ligne par ligne ; hors ligne.
- **Site sans JavaScript** : la même grille (tâche « Constitution Citoyenne et Démocratique et pétitions sur le site »).
- **Aucune donnée sur l'utilisateur** : le comparateur ne sait pas ce qu'on regarde (contenus synchronisés en bloc, comme le reste). Aucun questionnaire de proximité, qui serait une opinion politique (RGPD, article 9).

## 4. Calendrier proposé

1. Novembre 2026 : grille, format et écrans avec des candidats [EXEMPLE] fictifs ; relecture juridique demandée.
2. Dès la publication de la liste officielle : contenus réels, rédigés et relus.
3. Campagne officielle : mises à jour seulement pour les corrections et les droits de réponse ; gel de la veille du scrutin à la fermeture des bureaux (Q4).

## 5. Ce que le comparateur ne fait pas

- Pas de questionnaire « quel candidat me ressemble ».
- Pas de sondage.
- Pas d'appel à voter.
- Pas de désignation citoyenne d'un candidat (DE-17, phase 6, avocat).

## 6. Dépendances

- Données des votes de « Voir » (M20) : dans `main`.
- Fiches des partis de « Voir » (D-054).
- Tableau de la Constitution Citoyenne et Démocratique (fait).
- Archivage horodaté (serveur fait, branchement à faire).
- Relecture par un avocat (L52-8), à ajouter à la liste des relectures de la section 9.8.

## 7. Décisions de la propriétaire (7 novembre 2026, relayées par le chef d'équipe)

- **Q1, option B** : figurent au comparateur les candidats déclarés qui le demandent, selon la même règle pour tous. Règle publiée :
  - toute personne qui a déclaré publiquement sa candidature et qui en fait la demande est ajoutée, dans le même délai pour toutes ;
  - la demande, sa date et la date d'ajout restent visibles ;
  - à la publication de la liste officielle, le comparateur s'y aligne.
- **Q2** : « Depuis aussi loin que possible sur le parti portant le candidat à la présidentielle, tant que les données officielles existent, tous les votes en France et à l'Union européenne. »
  - Les votes viennent des données ouvertes de « Voir » (Assemblée nationale, Sénat, Parlement européen), par législature, aussi loin que les données officielles existent.
  - Ce sont les votes des élus du parti qui porte le candidat, et les votes de la personne si elle a eu un mandat.
  - Tous les scrutins publics, sans sélection éditoriale, rangés par thème.
  - Import historique par « Voir » : paquets par législature et par chambre, téléchargés en entier pour tous les partis, jamais une requête par parti ; répartition par parti et par scrutin (pour, contre, abstention, non-votant), sans position majoritaire calculée ; classement par thème côté « Décider », par une règle publiée.
  - Ce qui existe en données ouvertes (vérifié par « Voir » le 7 novembre 2026, `docs/integration/voir-historique-sources.md`) :
    - Assemblée nationale : votes nominatifs depuis la 14e législature (2012), parti de rattachement daté ;
    - Sénat : votes nominatifs depuis le 1er octobre 2006, aucun rattachement au parti publié, donc le groupe avec la mention « groupe, rattachement au parti non publié », pour tous les sénateurs ;
    - Parlement européen : vote de chaque député depuis le 15 juillet 2019, totaux seuls de 2014 à 2019, parti national daté ;
    - Conseil de l'UE : laissé de côté (plus de jeu ouvert par État).
- **Q3** : « Si, on inclue la photo. »
  - La photo officielle de chacun, au même format, sur le même fond.
  - Elle est servie par le serveur de PPF, jamais par un site tiers (règle « aucun tiers »).
  - Pour chaque photo : licence, auteur et source vérifiés, puis inscrits dans la liste des relectures par un avocat (droit à l'image et droit d'auteur).
  - Sans droit vérifié, pas de photo, pour personne.
- **Q4, option A** :
  - le contenu est figé de la veille du scrutin à zéro heure jusqu'à la fermeture des bureaux, à chaque tour ;
  - le serveur refuse toute publication ou mise à jour du comparateur et des positions pendant ce temps ;
  - l'application reste lisible.
- Préproduction : des sources officielles réelles (« on reste dans la vraie info meme en preprod »). Le dépôt et ses tests gardent des personnes fictives.

## 8. Questions posées (historique)

- **Q1. Qui figure au comparateur avant la liste officielle ?**
  - A (recommandée) : personne avant la liste officielle. On publie la grille des thèmes et les positions des **partis** (même critère d'entrée pour tous les partis, celui des fiches de « Voir », M17), puis les candidats dès la liste officielle.
  - B : les candidats déclarés qui remplissent un critère objectif annoncé à l'avance (par exemple un parti représenté au Parlement). Plus tôt, mais le critère sera contesté.
- **Q2. Les votes passés.**
  - A (recommandée) : seulement les scrutins publics des thèmes de la grille, choisis par une règle publiée (tous les scrutins sur un texte dont le titre relève du thème, depuis 2022) et non à la main.
  - B : une sélection éditoriale de scrutins marquants. Plus parlante, mais exposée au reproche de choisir.
- **Q3. Photos.**
  - A (recommandée) : aucune photo, le nom seulement.
  - B : la photo officielle de chacun, au même format.
- **Q4. Gel la veille du scrutin.**
  - A (recommandée) : contenu figé de la veille à zéro heure jusqu'à la fermeture des bureaux, l'application restant lisible.
  - B : aucun gel, le comparateur n'étant pas de la propagande.

## 9. Réalisation

- **7 novembre 2026** : grille identique pour tous (`ComparatorGrid`), page dans « Décider » (un thème à la fois sur téléphone, tableau sur grand écran), contenus [EXEMPLE].
- **8 novembre 2026, Q1 et Q4** :
  - tours de l'élection (`ppf-round`) et **gel** : de 0 h, heure de Paris, la veille du premier jour de vote (l'outre-mer qui vote la veille compte) jusqu'à la fermeture des derniers bureaux (20 h par défaut), le serveur refuse toute publication, mise à jour ou suppression du comparateur et de ses positions (`server/decider/ComparatorFreeze.kt`, branché dans `POST /api/v1/events`, refus 409 `comparator_frozen`) ; les tours qui comptent sont ceux des comparateurs déjà enregistrés ; l'application affiche « Contenu figé jusqu'à la fermeture des bureaux de vote » et reste lisible ;
  - **demandes des candidats** (`ppf-request`, `ppf-addition-delay`) : chaque demande reste visible avec sa date et la date de l'ajout, dans un délai publié, le même pour tous ; avant la liste officielle, un candidat sans demande, ou ajouté après le délai, fait refuser la fiche par l'outil de signature ;
  - **liste officielle** (`ppf-official-list`) : dès qu'elle est publiée, le comparateur la suit et le dit, avec le lien vers la source.
- **8 novembre 2026, Q2 (D-117)** : `ppf-theme-words`, `ppf-words-version`, `ppf-party` ; `core/decider/ComparatorVotes.kt` (`ThemeWords`, `WordsVersion`, `CandidateParty`, `CandidateVotes` : les paquets lus un à un, seuls les scrutins du parti gardés) ; page « Votes passés du parti de … » (`ui/decider/PastVotesPage.kt`, `PastVotesState.kt`) : « Tous les votes de ce parti » par défaut, chaque thème, « Autres scrutins », recherche libre dans les titres, la règle, ses mots et sa version sous la liste, téléchargement de tout l'historique en une fois pour tous les partis, signalement sans compte d'un scrutin mal rangé (envoyé comme les signalements d'erreur de « Voir », D-056) ; l'outil de signature refuse des mots sans historique ou publiés pour la première fois après la liste officielle.
- **8 novembre 2026, Q3** : `ppf-photo`, `CandidatePhoto`, `ComparatorSheet.photosShown` (tout ou rien), route `GET /api/v1/decider/photos/{sha256}` (`server/decider/ComparatorPhotosRoutes.kt`, dossier `PPF_DECIDER_PHOTOS_DIR`, octets vérifiés contre leur nom), `RemoteEvents.fetchComparatorPhoto`, `ui/decider/ComparatorPhotosState.kt` (toutes les photos d'une fiche demandées ensemble, vérifiées par leur empreinte, aucune montrée si une manque), photo de 72 dp au même endroit pour tous, mentions sous la grille.
- **8 novembre 2026, votes de la personne** : vue « Ses propres votes » des votes passés, quand la fiche du candidat dans « Voir » porte son identifiant officiel de parlementaire (`ppf-official-id`) : ses votes nominatifs du mandat en cours, tels qu'enregistrés (délégation, mise au point à côté, jamais à la place), d'après les fichiers mensuels déjà sur l'appareil (`ParliamentCatalog.votesOf` de « Voir »).
- **Reste** : photos gardées sur l'appareil pour le hors-ligne (en mémoire seulement pour l'instant) ; votes nominatifs des mandats passés (absents des paquets historiques, sans nom par construction) ; filtre par commission saisie au fond quand « Voir » la donnera (D-117, point 5) ; comparateur sur le site sans JavaScript ; relecture par un avocat (L52-8) ; vrais candidats.
- **À vérifier avant la mise en ligne** (Légifrance, décret de convocation de 2027) : les jours de vote de l'outre-mer et l'heure de fermeture des derniers bureaux de chaque tour, à reporter dans `ppf-round`.

## 10. Votes passés rangés par thème (Q2) : option A validée par le chef d'équipe (D-117)

Demande de la propriétaire (Q2) : « Depuis aussi loin que possible sur le parti portant le candidat à la présidentielle, tant que les données officielles existent, tous les votes en France et à l'Union européenne. » Données : les paquets historiques de « Voir » (D-066, `docs/integration/voir-historique-import.md`), téléchargés en entier sur l'appareil pour tous les partis, avec pour chaque scrutin public la répartition par parti (pour, contre, abstention, non-votants, membres), sans nom ni position majoritaire.

### 10.1 Quel parti pour quel candidat

- Sur la fiche du comparateur, une étiquette répétée `ppf-party` : `candidat | identifiant du parti dans les paquets (an:PO…, senat-grp:…, pe-org:…) | du (AAAA-MM-JJ) | au (facultatif)`, avec ses sources (`ppf-source`). C'est un fait public (le parti qui porte la candidature, puis ses identifiants officiels par chambre et par période), relu comme le reste.
- Sénat : le paquet ne donne que le groupe ; la mention « groupe, rattachement au parti non publié » reste affichée, pour tous les candidats.
- Les votes nominatifs de la personne (si elle a eu un mandat) ne sont pas dans ces paquets : ils viendront des paquets mensuels de la législature en cours, dans une étape suivante.

### 10.2 Comment ranger les scrutins par thème : options

| Option | Comment | Pour | Contre |
|---|---|---|---|
| **A (recommandée)** | Une règle de mots publiée dans la fiche signée du comparateur, `ppf-theme-words` (le thème, puis ses mots) ; un scrutin entre dans un thème si son titre officiel contient l'un des mots (sans accents ni majuscules, mot entier ou début de mot) ; un scrutin peut entrer dans plusieurs thèmes ; ceux qui n'entrent dans aucun sont comptés et listés à part (« autres scrutins ») | Aucun choix scrutin par scrutin : la même règle pour tous les partis, lisible et vérifiable par n'importe qui, calculée sur l'appareil ; corrigeable par une nouvelle version, journalisée | Les mots eux-mêmes sont un choix de l'équipe : à relire à deux et à publier sous la liste ; des titres d'amendements peu parlants tombent dans « autres scrutins » |
| B | La commission saisie au fond, rapprochée des thèmes | Officiel | Champ vide aujourd'hui dans les paquets ; absent pour le Sénat et le Parlement européen ; trop grossier (huit commissions) |
| C | Classement à la main par l'équipe, relu à deux | Précis | Sélection éditoriale, contraire à la demande (« tous les votes », sans choix) ; des milliers de scrutins |

**Décision du chef d'équipe (8 novembre 2026, D-117) : A**, avec cinq garde-fous :
1. noms de thèmes neutres, repris des intitulés officiels (commissions permanentes, domaines des ministères) ;
2. mots fixés et publiés avant la liste officielle des candidats ; ensuite, toute modification datée, motivée et visible dans un historique public de la fiche signée ; aucune pendant le gel ; chaque fiche montre la version des mots ;
3. partout, « Tous les votes de ce parti » sans thème et une recherche libre par mot dans les titres officiels, sur l'appareil : les thèmes aident à lire, ils ne filtrent jamais ;
4. sous chaque liste : « classement automatique par mots du titre officiel, il peut contenir des erreurs », les mots affichés et un moyen de signaler un scrutin mal rangé (correction par la règle de mots, jamais à la main) ;
5. B plus tard, en filtre de plus, quand la commission sera dans les paquets ; C refusée.

### 10.3 Affichage

- Pour un thème et un candidat : le nombre de scrutins, puis chacun (date, titre officiel, chambre, résultat, et la répartition des élus du parti : « 12 pour, 3 contre, 1 abstention, 2 non-votants sur 18 »), du plus récent au plus ancien, par pages de 20, avec un filtre par chambre ; jamais de position majoritaire calculée, jamais de note.
- La règle de mots et sa date sont affichées sous chaque liste, avec le nombre de « autres scrutins ».
- Hors ligne : tout est calculé sur l'appareil à partir des paquets déjà téléchargés ; le serveur ne sait pas quel candidat ou quel thème est regardé.

## 11. Photos officielles (Q3) : conception

Décision de la propriétaire : « Si, on inclue la photo. » La photo officielle de chacun, au même format et sur le même fond, servie par PPF (jamais par un site tiers), avec licence, auteur et source vérifiés et inscrits dans la liste des relectures par un avocat ; sans droit vérifié, pas de photo, pour personne.

- **Format** : sur la fiche signée du comparateur, `ppf-photo` (répétée, une par candidat) : `candidat`, `sha256` du fichier, `licence`, `auteur`, `page de la source (https)`. L'empreinte relie la fiche signée au fichier exact : un fichier changé n'est plus montré.
- **Règle « tout ou rien »** (`ComparatorSheet.photosShown`) : les photos ne s'affichent que si **chaque** candidat de la fiche a la sienne, avec ses trois mentions ; sinon aucune, pour personne, et la page le dit.
- **Fichiers** : préparés par l'équipe au même cadrage (carré, 400 pixels, fond uni), métadonnées retirées ; servis par le serveur de PPF à `GET /api/v1/decider/photos/{sha256}` depuis un dossier (`PPF_DECIDER_PHOTOS_DIR`, comme les tuiles de la carte), seulement quand l'empreinte du fichier correspond à son nom ; mise en cache longue (le nom change avec le contenu).
- **Application** : à l'ouverture du comparateur, toutes les photos de la fiche sont demandées ensemble (jamais une seule, qui dirait au serveur quel candidat intéresse), vérifiées par leur empreinte, gardées sur l'appareil pour le hors-ligne ; même taille et même place pour tous ; texte de remplacement « Photo officielle de … » ; mentions de licence, d'auteur et de source sous la grille.
- **Exemple** : aucune photo dans le jeu [EXEMPLE] (aucun droit à vérifier pour des personnes fictives) ; les tests utilisent des images générées.
- **Avant la mise en ligne** : droit à l'image et droit d'auteur de chaque photo dans la liste des relectures par un avocat (section 9.8 du cahier des charges).
