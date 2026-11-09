# Pétitions locales (DE-26) : conception

- Pôle « Décider », 9 novembre 2026. Cahier fonctionnel 2.1, M10 : « Demander à une collectivité d'organiser une consultation : un dixième des électeurs d'une commune, un vingtième dans les autres collectivités (Constitution, article 72-1 ; CGCT, article L1112-16), l'assemblée décidant librement ; guide et suivi. » Décision de la propriétaire D-062 ; référentiel des territoires.
- Décisions techniques prises en autonomie (gratuites, aucun tiers) ; rien de nouveau à trancher.

## 1. Le droit (vérifié sur Légifrance le 9 novembre 2026)

- [Code général des collectivités territoriales, article L1112-16](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000045210334), rédaction de la loi n° 2022-217 du 21 février 2022 : dans une commune, **un dixième** des électeurs inscrits sur les listes électorales et, dans les autres collectivités territoriales, **un vingtième** des électeurs peuvent demander l'inscription à l'ordre du jour de l'assemblée délibérante d'une consultation sur toute affaire relevant de sa décision ; **chaque trimestre**, un électeur ne peut signer qu'une seule demande tendant à l'organisation d'une consultation par une même collectivité ; la demande va au maire ou au président de l'assemblée, qui **décide librement**.
- Attention : la rédaction de 2004 (un cinquième, un dixième, une demande par an) n'est plus en vigueur ; c'est elle qu'on trouve encore dans beaucoup de guides.
- Constitution, article 72-1 (droit de pétition des électeurs de chaque collectivité). La consultation organisée n'est qu'un avis (CGCT, article L1112-17, à relire avant le premier contenu réel).

## 2. Ce que fait PPF, et ce qu'il ne fait pas

- **PPF ne recueille aucune signature.** On signe sur la page de la collectivité quand elle en offre une, ou sur papier auprès des organisateurs ; le contenu le dit (`ppf-official-url` ou `ppf-how-to-sign`).
- Le **seuil** est calculé sur l'appareil à partir du nombre d'électeurs inscrits que l'équipe a relevé et cité (`ppf-voters`), arrondi à l'unité supérieure : PPF ne fixe aucune date ni aucun chiffre qui ferait foi.
- Les **décomptes** sont ceux des organisateurs, avec leur date ; l'application l'écrit (« Décompte des organisateurs, relevé le … »).
- Une pétition locale n'apparaît que pour **la commune choisie sur l'appareil dans « Mes élus » et son département** ; rien n'est envoyé. La même commune sert désormais aux scrutins locaux (DE-28) et aux consultations locales (DE-27).

## 3. Format

Un contenu `petition-locale` ; détail dans `docs/FORMATS.md` (« Pétition locale »). Exemple [EXEMPLE] : `content/decider/petitions-locales/petition-locale-exemple.md` (commune 69123).

## 4. Étapes

1. Fait le 9 novembre 2026 : type, lecture et contrôles, section « Pétitions locales » de « Décider » et page de chaque pétition (décompte contre le seuil légal, où et comment signer, ce que dit la loi, ce qui s'est passé), filtre par la commune choisie sur l'appareil.
2. Un guide « Lancer une pétition locale » dans « Comprendre » (fiche pratique), à proposer au pôle Comprendre.
3. Site sans JavaScript, avec les autres pages « Décider ».
4. Contenus réels : à partir des demandes des collectifs locaux, relues par l'équipe.
