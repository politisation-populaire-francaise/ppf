# Comparateur de la présidentielle 2027 (DE-16) : conception

- Pôle « Décider », 7 novembre 2026. À valider par le chef d'équipe (questions de la section 7) avant tout code.
- Sources :
  - cahier fonctionnel 2.0, M13.7 (DE-16) et règle de méthode 9 (traitement identique) ;
  - cahier des charges, section 9.2 (droit électoral et financement politique) ;
  - données des votes de « Voir » (M20, D-064, D-065, dans `main` depuis le 7 novembre 2026) ;
  - Pacte démocratique (DE-08, D-060, D-062).

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
| Réponse au Pacte démocratique | Signé, refusé, en attente, sans réponse, mesure par mesure | Tableau du Pacte (D-060, D-062) |
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
- **Site sans JavaScript** : la même grille (tâche « Pacte et pétitions sur le site »).
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
- Tableau du Pacte (fait).
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
