# Fiches pédagogiques pour la classe (CO-13) et kit d'atelier (CO-24) : conception, version 1

Rédigé le 8 novembre 2026 par le pôle « Comprendre », d'après la décision D-096 (chef d'équipe, par délégation de la propriétaire ; entrée « [2026-11-04] Ressources pour les enseignants » du brainstorming fonctionnel). Concerne le cahier fonctionnel, M23 (CO-13, CO-24), et la licence commune des contenus (D-103 : CC BY 4.0 pour les textes et les images).

## 1. Principe

- Une **fiche pédagogique** est rattachée à **un parcours** existant (« Comprendre en 5 minutes ») et à **un niveau**. Elle dit comment faire vivre ce parcours en 55 minutes avec un groupe : objectifs, prérequis, déroulé minuté, quiz à main levée, débat guidé avec les objections des deux côtés.
- Le même format sert au **kit d'atelier d'éducation populaire** (CO-24), avec le niveau `atelier` et une durée de 45 minutes à 2 heures (étape ultérieure).
- **Aucun compte, aucune donnée d'élève, rien n'est envoyé depuis la classe.** La fiche se lit, se projette et s'imprime ; elle ne mesure rien.
- **Neutralité** : chaque fiche affiche, par l'application et le site (texte fixe, jamais laissé au rédacteur), « L'enseignant reste libre de ses choix pédagogiques. Cette fiche respecte la neutralité de l'enseignement public : elle présente des faits et des débats, jamais une consigne de vote ni une position partisane. »
- **Programme officiel** : chaque fiche cite le programme du Bulletin officiel qu'elle sert, avec le numéro du BO, sa date et la date de vérification ; l'adresse figure dans les sources.
- **Licence** : celle de tous les textes de PPF (CC BY 4.0, attribution « Politisation Populaire Française »), affichée par le mécanisme commun de « Voir » (`ContentLicenses.of(type)`) dès qu'il est dans `main`.
- Tant qu'elles ne sont pas relues : titre en `[EXEMPLE]`, `origin: ai-assisted` et la phrase « rédigé avec l'aide d'une IA ».

## 2. Format (contenu signé, NIP-23, type 30023)

- Nouveau type `ArticleType.FICHE_PEDAGOGIQUE` (`ppf-type` = `fiche-pedagogique`), ajouté **à la fin** de l'énumération (règle commune ; « Décider » y ajoute `ELECTION` : celui qui entre second met le sien après).
- Dossier `content/enseignants/`, paquet `comprendre.json`, schéma `ComprendreSchemas.FICHE_PEDAGOGIQUE`.
- Identifiant : `classe-<slug du parcours>-<niveau>` (par exemple `classe-le-referendum-lycee`), pour ne jamais heurter un autre contenu de même clé.
- En-tête : les champs communs (`slug`, `title`, `summary`, `topics`, `type`, `origin`, `duration` = 55, `published_at`, `updated_at`) et :

| Champ | Valeur | Étiquette |
|---|---|---|
| `course` | identifiant d'un parcours publié (une seule fois) | `["a", "30023:<clé>:<slug>", "", "parcours"]` |
| `level` | `lycee`, `cycle-4` ou `atelier` (CO-24, section 6) | `ppf-level` |
| `programme` | le programme servi, en une phrase : intitulé, BO, date, « vérifié le AAAA-MM-JJ » (500 caractères au plus) ; obligatoire pour la classe, refusé pour un atelier | `ppf-programme` |

- Corps, sections obligatoires dans cet ordre (vérifiées par le schéma) : `## Objectifs`, `## Prérequis`, `## Déroulé` (étapes minutées dont la somme fait la durée), `## Débat guidé` (avec un bloc `::: objections` d'au moins deux objections, une de chaque côté du débat), `## Sources`. Facultatif : `## Pour aller plus loin`.
- Le **quiz à main levée** n'est pas réécrit : l'application et le site reprennent les questions du parcours lié, réponses à part (pour l'enseignant).

## 3. Affichage

- **Site sans JavaScript** : page `/comprendre/enseignants/{slug}` (fiche, quiz du parcours, rappel de neutralité, licence), page `/comprendre/enseignants` (liste par niveau), lien « Pour la classe : fiche pédagogique » sur la page du parcours. Feuille d'impression (`@media print`) : sans navigation, noir sur blanc, réponses du quiz sur une page à part.
- **Application** : sur l'introduction et la fin d'un parcours, lien « Pour la classe » vers la fiche (même rendu : texte, quiz du parcours avec réponses repliées, rappel de neutralité). Hors ligne comme les autres contenus.

## 4. Tests

- Schéma : fiche valide ; refus sans parcours lié, avec un parcours inconnu, sans programme, sans une section obligatoire, avec moins de deux objections, avec une durée dont le déroulé ne fait pas la somme.
- Outil de signature : non-régression des autres paquets inchangée ; la fiche d'exemple entre dans `comprendre.json`.
- Site : page sans `<script>`, rappel de neutralité présent, quiz du parcours repris, feuille d'impression liée, lien depuis la page du parcours.
- Application : lien « Pour la classe » présent seulement quand une fiche existe pour le parcours.

## 5. Étapes

1. Type, schéma, outil de signature, page du site, une fiche d'exemple de niveau lycée (programme vérifié sur le BO), tests.
2. Affichage dans l'application.
3. Cycle 4 (fait le 9 novembre 2026), puis kit d'atelier (CO-24, section 6).

## 6. Kit d'atelier d'éducation populaire (CO-24)

Ajouté le 9 novembre 2026 par le pôle « Comprendre », dans le format décidé par D-096 (« le même format sert au kit d'atelier »). Pour les associations, syndicats, centres sociaux et médiathèques.

- **Même type de contenu**, niveau `atelier`, même dossier `content/enseignants/`, même adresse `/comprendre/enseignants/{fiche}` (une adresse par type, D-091) ; identifiant `atelier-<identifiant du parcours>`.
- **Pas de programme officiel** : le champ `programme` est refusé pour un atelier et reste obligatoire pour la classe (règle vérifiée par le schéma, donc par le formulaire de la rédaction et par l'outil de signature).
- **Durée de 45 minutes à 2 heures**, les étapes du déroulé faisant la somme comme pour la classe.
- **Rappel de neutralité propre aux ateliers**, texte fixe de l'application et du site : « L'animatrice ou l'animateur reste libre de ses choix. Cette fiche présente des faits et des débats, jamais une consigne de vote ni une position partisane. »
- **Version sans écran** : la page du site imprime la fiche, puis les **cartes à découper** (une par question du parcours, sans sa réponse ; une par objection du débat, avec sa réponse ; deux par rangée, jamais coupées entre deux pages), puis les réponses « pour l'animation » sur une page à part. La section « Prérequis » dit le matériel avec et sans écran.
- **Aucun compte, aucune liste de participants**, rien n'est envoyé.
- **Liens** : « Pour un atelier : fiche d'animation (90 min) » sur la page du parcours (site) et sur son introduction (application) ; section « Atelier d'éducation populaire » dans la liste `/comprendre/enseignants`.
- **Étapes** : 1. niveau, règles, pages du site, cartes, lien et fiche dans l'application, une fiche [EXEMPLE] de 90 minutes sur « Les pétitions officielles » (fait le 9 novembre 2026, à signer et compiler à notre tour de la file) ; 2. mode animation dans l'application (fait le 9 novembre 2026, à compiler à notre tour) : bouton « Lancer le mode animation » sur chaque fiche (classe comme atelier) ; la fiche laisse la place à une diapositive à la fois, en grand : ouverture, questions du parcours (réponse et explication sur un geste de l'animation), question du débat guidé, objections (réponse sourcée sur un geste), fin ; flèches ou page précédente et page suivante (télécommande de présentation), Échap pour quitter ; hors ligne, sans compte, rien n'est enregistré (la position n'existe qu'en mémoire) ; `core/comprendre/TeachingAnimation.kt` (`AnimationSlide`, `AnimationState`) et `ui/comprendre/TeachingAnimationView.kt` ; 3. d'autres fiches d'atelier, relues par l'équipe.
