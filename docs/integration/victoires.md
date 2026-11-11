# Nos victoires (PP-03) : conception, version 0.1

Rédigé le 9 novembre 2026 par le pôle « Voir » (« [Voir] Team Dev 2 »), à la demande du chef d'équipe, d'après le cahier fonctionnel (M34, PP-03 : « Ce qui a été obtenu : engagements tenus, revendications satisfaites, lois modifiées, avec les sources »). Décision D-105. Branche `feature/voir-presence`, pas encore compilée.

## 1. Principes

1. **Pas de victoire sans preuve** : chaque victoire a sa date et au moins une source, sinon elle n'est pas affichée.
2. **La victoire est celle du mouvement** : la page ne l'attribue jamais à PPF seule. Une étape franchie n'est pas présentée comme une revendication satisfaite : elle est rangée dans « Autre victoire ».
3. **Reliée à « Voir »** : une victoire renvoie vers les briques qu'elle concerne (la promesse tenue, le texte modifié, la personne). Elle passe par la même relecture à deux et le même journal public des corrections que les autres contenus.
4. **Jamais classées par importance** : de la plus récente à la plus ancienne, avec un filtre par genre.

## 2. Type de contenu `victoire`

Une brique de l'espace « Voir » (même moteur éditorial, même formulaire du back-office, fichier `voir.json`), lue par `core/voir/Victories.kt` :

| Champ (en-tête) | Étiquette | Contenu |
|---|---|---|
| `victory` | `ppf-victory` | `engagement-tenu`, `revendication-satisfaite`, `loi-modifiee` ou `autre` (obligatoire) |
| `date` | `ppf-date` | jour où la victoire a été obtenue (obligatoire) |
| `related` | `a`, rôle `lie` | une brique de « Voir » par ligne : promesse, texte, personne… |
| `archive`, `source` (au moins une), `correction`, `next` | comme dans « Voir » | |

Le corps (Markdown) dit ce qui a été obtenu et comment, sans s'en attribuer le mérite.

## 3. Pages

- **Application** : « Nos victoires », en bas de l'axe « Le mouvement » de « Voir ». Une carte par victoire : genre et date, titre, résumé, texte, briques liées (un geste les ouvre), archives, sources, corrections, « Et maintenant ? ».
- **Site** : `/victoires`, lisible sans JavaScript, lien dans le pied de page et depuis `/transparence`.
- **Aperçu du back-office** : celui du site.

## 4. Plus tard

- Lier une revendication citoyenne satisfaite (signée par son auteur, pas par l'équipe), avec le pôle des revendications ; en attendant, « Et maintenant ? » mène à l'espace des revendications.
- Lier un engagement de candidat de la Constitution Citoyenne et Démocratique (« Décider »), avec le pôle « Décider ».
- Compter les victoires dans le tableau de bord fédérateur (PP-07).
