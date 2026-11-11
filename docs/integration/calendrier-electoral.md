# Calendrier électoral personnel (DE-28) : conception

- Pôle « Décider », 8 novembre 2026. Cahier fonctionnel 2.1, M13.7 : « Dates des scrutins, date limite d'inscription sur les listes électorales, procuration, lien vers la vérification officielle de son inscription, rappels gardés sur l'appareil (TE-09). » Priorité : tôt (présidentielle d'avril 2027).
- Décisions techniques prises en autonomie (gratuites, aucun tiers) ; rien de nouveau à trancher par la propriétaire.

## 1. Le droit et les sources (à revérifier avant la mise en ligne)

- **Date limite d'inscription** : code électoral, article L17 (rédaction de la loi n° 2016-1048 du 1er août 2016, en vigueur depuis le 1er janvier 2019) : les demandes sont déposées au plus tard le **sixième vendredi précédant le scrutin** ; des dérogations jusqu'au dixième jour avant le scrutin existent pour certaines personnes (mutation, retour de service militaire…). La téléprocédure a une date plus courte, fixée par la partie réglementaire du code : **article et date à vérifier sur Légifrance avant toute mise en ligne** (le site du ministère de l'Intérieur, `elections.interieur.gouv.fr`, n'a pas pu être lu par nos outils le 8 novembre 2026).
- **Dates des tours** : tant que le décret de convocation n'est pas publié au Journal officiel, une date n'est qu'« annoncée » et l'application le dit. Pour une élection présidentielle ordinaire, le décret est publié au moins dix semaines avant le premier tour (décision du Conseil constitutionnel n° 2021-815 DC du 25 mars 2021, à relire) ; en 2022, le décret n° 2022-66 a paru au Journal officiel du 27 janvier 2022.
- **Vérifier son inscription, établir une procuration** : liens vers les services officiels seulement (service-public.fr, « Interroger sa situation électorale » ; maprocuration.gouv.fr), après l'avertissement « Vous quittez PPF ». Adresses exactes à vérifier au moment de rédiger les contenus.
- PPF ne calcule jamais seule une date limite qui ferait foi : chaque date est écrite par l'équipe d'après la source officielle et citée ; le code vérifie seulement sa cohérence (par exemple, une date limite d'inscription en mairie qui ne serait pas un vendredi est signalée à la signature).

## 2. Format : un contenu `election` signé par l'équipe

Nouveau type `ppf-type` = `election` (ajouté à la fin de `ArticleType`), le même pour tous les scrutins (présidentielle, législatives, municipales, européennes, référendum), relu comme les autres contenus éditoriaux :

| Étiquette | Contenu |
|---|---|
| `ppf-election` | `identifiant`, `libellé` (comme la Constitution Citoyenne et Démocratique et le comparateur, qui y renvoient) |
| `ppf-election-status` | `annoncee` (date annoncée, décret attendu) ou `convoquee` (décret publié) |
| `ppf-decree` | Le décret de convocation : `numéro`, `date du Journal officiel`, `adresse sur Légifrance` (obligatoire pour `convoquee`) |
| `ppf-round` | Les tours, au format du comparateur (`numéro`, `jour en métropole`, `premier jour de vote outre-mer`, `fermeture des derniers bureaux`) |
| `ppf-deadline` | Une échéance : `type` (`inscription-mairie`, `inscription-en-ligne`, `procuration-conseillee`, `candidatures`…), `date`, `source (https)` ; répétée |
| `ppf-territory-code` | Pour un scrutin local, le territoire (référentiel des territoires) ; absent pour un scrutin national |
| `ppf-source` | Les sources officielles |

## 3. Dans l'application

- **Mes élections** (dans « Décider » et dans « Mon espace », TE-09) : les prochains scrutins nationaux, et les scrutins locaux de la commune choisie dans « Mon territoire » (calcul sur l'appareil, rien n'est envoyé) ; pour chacun : les tours, chaque échéance avec ce qu'il faut faire, la mention « date annoncée, en attente du décret » tant qu'il le faut, et les liens officiels.
- **Rappels** : désactivés par défaut (principe 10), proposés sur la page de l'élection ; programmés et gardés sur l'appareil seulement, par le centre de notifications de « Communauté » (M31) ; sept jours avant chaque date limite d'inscription, dix jours avant chaque tour pour la procuration, la veille de chaque tour, à 10 h ; chaque sorte se coupe, chaque rappel se retire, sur la page de l'élection et, plus tard, dans « Mes rappels » (TE-09).
- Hors ligne : le contenu `election` est groupé avec les autres contenus de « Décider » et synchronisé comme eux.
- Le serveur ne sait jamais qui a programmé un rappel ni pour quelle commune.

## 4. Étapes

1. Cœur : `ArticleType.ELECTION`, `ElectionCalendar` (lecture et contrôles), contrôles dans l'outil de signature ; contenu [EXEMPLE] d'une élection fictive.
2. Écran « Mes élections » dans « Décider » et carte dans « Mon espace ».
3. Rappels sur l'appareil, branchés sur le centre de notifications de « Communauté » : **fait le 9 novembre 2026** (D-118, contrat commun `docs/integration/rappels.md`, section 4 pour les élections) ; sur ordinateur, un rappel s'affiche à l'ouverture de l'application. L'écran « Mes rappels » (TE-09) est à « Communauté ».
4. Contenu réel de la présidentielle 2027 : rédigé par l'équipe dès la publication du décret de convocation, puis des législatives et municipales suivantes.
