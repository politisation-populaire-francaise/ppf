# « Mon bulletin a été compté » (DE-29) : conception

- Pôle « Décider », 9 novembre 2026. Cahier fonctionnel 2.1, DE-29 : « Après la clôture, l'appareil vérifie seul que son bulletin figure dans la liste publiée avec le bon choix et rejoue le recomptage (M13.6) ; le reçu n'est jamais montrable à un tiers. » Priorité : tôt.
- Décisions techniques prises en autonomie (gratuites, aucun tiers, aucune bibliothèque nouvelle, aucune donnée nouvelle) ; s'appuie sur « une personne, une voix » (D-110, section 6.7 ; D-112).

## 1. Ce que fait l'appareil

À la clôture d'une urne, le serveur publie la liste anonyme des bulletins, triée par identifiant secret `y`, les clés secrètes de chaque niveau et les totaux (`GET /api/v1/urnes/{n}/closing`, D-110). L'appareil a gardé, pour chaque urne, son jeton, son identifiant secret `y`, son dernier choix et le dernier choix accepté par le serveur (table `vote_token`).

Sur la page d'une revendication dont l'urne est close, le bouton **« Recompter et vérifier mon bulletin »** :

1. télécharge la liste publiée par le client **anonyme** (sans compte, connexion fermée après la requête, nom d'agent générique), comme les bulletins eux-mêmes ;
2. rejoue le recomptage de toute l'urne (`UrneRecount`, le même que celui du serveur et de quiconque recompte) : clés, jetons, doublons, nombre de bulletins par niveau, votes publics des élus, totaux ;
3. cherche son bulletin par `y` et compare le jeton, le niveau et le choix (`core/urne/MyBallot.kt`) :
   - compté avec son choix ; retrait compté comme tel ;
   - compté avec le choix précédent (le dernier n'est pas parti avant la clôture) ;
   - choix jamais parti ; aucun bulletin de cet appareil ;
   - **alerte** : bulletin accepté puis absent de la liste, ou présent avec un autre choix ou un autre niveau ; **alerte** aussi si le recomptage ne correspond pas, avec la raison.

La clé publique du niveau de l'appareil est celle **épinglée** à la réception du jeton : une clôture qui en annonce une autre fait échouer le recomptage. Les autres niveaux sont vérifiés avec les clés de la clôture elle-même (l'appareil ne garde pas la liste vue pendant le vote ; limite écrite ici).

## 2. Ce qui protège la personne

- **La même requête pour tous** : le bouton est le même que l'on ait voté ou non, et la vérification ne part que si on le touche. Télécharger la liste dit seulement qu'on s'intéresse au recomptage, jamais qu'on a voté. Aucune vérification automatique : elle téléchargerait la liste des seules urnes où l'on a voté.
- **Aucun reçu** : la réponse n'est gardée nulle part (ni base, ni fichier, ni journal), ne nomme pas le choix, ne montre ni `y` ni le jeton, et ne propose ni copie ni partage. Elle ne prouve donc rien à un tiers ; l'écran le dit (« personne ne peut vous obliger à prouver votre vote »). L'identifiant `y` reste la seule preuve possible d'un choix : il ne quitte jamais l'appareil, sauf dans le bulletin anonyme lui-même.
- Les élus certifiés votent publiquement (D-115) : pour eux, seul le recomptage a un sens.
- Rien n'est envoyé sur l'appareil ni sur le résultat de la vérification.

## 3. Coût

Recompter demande un calcul de jeton par bulletin, de l'ordre de la milliseconde (mesures de l'étape 1 : 1,1 ms par jeton délivré côté serveur, à mesurer sur un téléphone) : quelques secondes pour quelques milliers de bulletins, une à plusieurs minutes au-delà de cent mille, sur le processeur de l'appareil, hors du fil de l'interface. La liste peut peser plusieurs mégaoctets : l'écran conseille le Wi-Fi. Une barre de progression et le recomptage partiel (son seul bulletin d'abord) viendront si les urnes grossissent.

## 4. Étapes

1. Fait le 9 novembre 2026 (non compilé) : vérification et recomptage sur l'appareil, client anonyme pour la liste publiée, bouton sous les urnes closes des revendications ; tests `MyBallotTest` (cœur), `MyBallotCheckViewsTest` (interface).
2. Même bouton sous les urnes des questions, des votations et de la délibération (DE-25), quand leurs écrans existeront.
3. Sur le site : une page de recomptage sans compte (la liste est publique) ; la recherche de son bulletin reste dans l'application, seule à connaître `y`.
