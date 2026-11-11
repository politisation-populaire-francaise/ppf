# Comptage de foule participatif (VO-40) : la part de « Décider », l'urne, conception 0.1

- Pôle « Décider », 9 novembre 2026. Cahier fonctionnel 2.1, VO-40 : « Estimation de la foule d'une manifestation par la méthode de Jacobs (surface multipliée par densité), un bulletin par compte, vérifié ou non, grâce à l'urne anonyme ; résultat affiché à côté des chiffres de la préfecture et des organisateurs, méthode publique. » Priorité : tôt, **après la carte de la manifestation** (AG-24, M25, pôle « Agir », en cours). Outil sur la carte : « Agir » ; observatoire : « Voir » ; urne : « Décider ».
- Rien n'est codé : ce document propose le contrat entre les trois pôles, à confirmer avec « Agir » et « Voir » quand la carte de la manifestation sera dans `main`. Décisions techniques, gratuites.

## 1. Principe

1. « Agir » découpe le parcours d'une manifestation en **zones** (tronçons de rue, place de départ, place d'arrivée), chacune avec sa **surface** en mètres carrés, calculée sur la carte et publiée avec l'événement (contenu signé).
2. Chaque personne présente dit, une fois, **dans quelle zone elle est et quelle densité elle voit**, par un bulletin anonyme (D-110) : une urne par manifestation, dont chaque choix est « zone, densité ». Un bulletin par compte (un jeton par urne), remplaçable jusqu'à la clôture, comme partout.
3. À la clôture, l'estimation de chaque zone est sa surface multipliée par la **densité médiane** des bulletins de la zone ; l'estimation de la manifestation est la somme des zones. Le calcul se fait sur les résultats publiés, refaisable par tous (recomptage, DE-29).
4. « Voir » affiche l'estimation à côté des chiffres de la préfecture et des organisateurs, avec la méthode, le nombre de bulletins et la mention « estimation participative, déclarative ».

## 2. L'urne

| Élément | Proposition |
|---|---|
| Une urne | par manifestation qui active le comptage (l'organisation qui la publie le demande) |
| Choix | `<zone>:<densité>` ; densités proposées : `1`, `2`, `3`, `4` personnes au mètre carré, décrites par des mots et un schéma (« on circule librement » … « on ne peut plus bouger ») ; valeurs à fixer avec « Agir » et « Voir » et publiées avec la méthode de Jacobs |
| Ouverture | à l'heure de départ annoncée |
| Clôture | trois heures après l'heure de fin annoncée |
| Résultats | à la clôture seulement (`liveResults = false`), pour qu'une estimation en cours n'influence pas les suivantes |
| Niveaux | vérifié et non vérifié montrés à part, comme pour les revendications ; élus à part |
| Seuil | une zone avec moins de 5 bulletins n'a pas d'estimation (« pas assez de bulletins »), la manifestation non plus si une zone de départ ou d'arrivée en manque |

Côté serveur, un gardien sur le modèle de `server/urne/RevendicationUrnes.kt` ouvre et ferme ces urnes d'après les contenus de l'événement (`ppf-crowd-zone` pour chaque zone et sa surface, format à écrire avec « Agir » dans `docs/FORMATS.md`).

## 3. Ce qui protège la personne

- Le bulletin dit une zone et une densité, jamais un compte : la zone choisie dit qu'une personne anonyme était là, pas qui (jetons aveugles, envoi différé, D-112).
- Aucune géolocalisation n'est envoyée ni vérifiée : la personne choisit sa zone sur la carte, l'appareil ne transmet pas sa position.
- Délivrance des jetons : tous les comptes reçoivent un jeton pour chaque urne ouverte (« tout le monde demande tout ») ; recevoir le jeton d'une manifestation ne dit pas qu'on y va.

## 4. Limites, dites telles quelles

- Une personne absente peut voter : l'estimation est déclarative ; la médiane et le seuil limitent l'effet de quelques bulletins faux, les colonnes vérifié et non vérifié permettent de comparer.
- Un cortège qui avance occupe des surfaces différentes au fil des heures : la première version estime la foule **au moment où la zone est la plus remplie**, selon les déclarations ; une estimation par heure viendra si la carte le permet.
- Coût de la délivrance des jetons : une urne de plus par manifestation pour chaque compte (environ 1 ms par jeton côté serveur, mesures de l'étape 1) ; à surveiller les jours de forte mobilisation.

## 5. À faire

1. Avec « Agir », quand la carte de la manifestation est dans `main` : format des zones, outil de choix sur la carte.
2. Avec « Voir » : l'observatoire, et la ligne de la méthode.
3. Code de l'urne (gardien, choix, calcul de l'estimation dans `core/decider`, tests), puis l'écran du vote dans l'outil d'« Agir ».
