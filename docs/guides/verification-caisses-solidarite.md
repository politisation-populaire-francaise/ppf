# Vérifier une caisse de solidarité avant de la publier (M35, AG-10)

Procédure de l'équipe éditoriale de PPF pour l'annuaire des caisses de grève et de solidarité juridique d'« Agir » (décision D-086, point 6, du 8 novembre 2026). Elle complète la vérification des organisations (`docs/integration/agir-organisations.md`, question 1) et le format des fiches (`docs/integration/agir-solidarite.md`, section 2.1).

**[EXEMPLE] Première version, à relire par l'équipe et par l'avocat avant l'ouverture publique.**

## Ce que l'on vérifie, et pourquoi

PPF ne détient ni ne fait transiter d'argent : la fiche d'une caisse renvoie seulement vers **sa propre page de dons**. Le risque est donc qu'une fausse cagnotte, ou une caisse qui finance un candidat ou un parti, profite de la confiance que les gens font à PPF. Avant de publier, l'équipe s'assure de trois choses :

1. **Qui gère la caisse** : une personne responsable est identifiée, de préférence une organisation déjà vérifiée de l'annuaire (syndicat, association, legal team) ; sinon une intersyndicale, une legal team ou un collectif nommé, dont l'équipe a rencontré un responsable (en personne ou en visioconférence).
2. **La page de dons est bien la sienne** : elle est annoncée sur le site officiel, le compte officiel ou un document public de celui qui gère la caisse, et son adresse commence par `https://`.
3. **Ni candidat ni parti** : la caisse ne sert à financer ni un candidat, ni un parti, ni une campagne électorale (Code électoral, article L52-8, déjà dans la liste de l'avocat : en dehors des partis politiques, les personnes morales ne peuvent pas participer au financement de la campagne d'un candidat). Les cas limites (caisse juridique d'un militant d'un parti, caisse de grève d'un syndicat proche d'un parti, caisse d'un collectif qui soutient ensuite une liste) **ne sont pas publiés** tant que l'avocat n'a pas répondu (feuille de route, « À faire relire par un avocat »).

## Les pièces à demander ou à retrouver

- Le nom de la caisse, à qui elle sert (grévistes d'un secteur, personnes poursuivies après une mobilisation) et le territoire.
- Le lien de la page de dons, et l'endroit public où celui qui gère la caisse l'annonce (copie d'écran datée, gardée par l'équipe, jamais publiée).
- Pour une organisation déclarée : son numéro RNA (association) ou SIREN, ses statuts ou son récépissé de déclaration.
- La façon dont l'argent est utilisé et rendu compte (assemblée générale, bilan publié), quand elle existe.
- L'engagement écrit « ni candidat ni parti » de celui qui gère la caisse.

On ne demande jamais l'identité des donateurs ni des bénéficiaires, et on n'écrit jamais dans la fiche les coordonnées d'une personne : seulement le nom de la caisse et celui de l'organisation ou du collectif qui la gère.

## Étapes

1. Un membre de l'équipe rassemble les pièces ci-dessus et remplit la fiche (fichier Markdown de `content/solidarite/`, ou formulaire de rédaction du back-office quand il existera pour « Agir »).
2. Il remplit `ppf-verified` avec la date du jour de la vérification et `ppf-no-party` avec `oui` seulement si le point 3 est vérifié ; sans `oui`, l'application écarte la fiche.
3. Un **deuxième membre** de l'équipe relit les pièces et la fiche avant la signature (la caisse touche à l'argent des gens).
4. La fiche est signée par la clé éditoriale et publiée.
5. L'équipe garde, hors du dépôt, la trace de la vérification (date, pièces vues, personnes de l'équipe) pour pouvoir répondre en cas de question.

## Revérifier

- **À chaque nouvelle grève** ou nouvel usage de la caisse, et **au moins tous les trois mois** : la page de dons est-elle toujours la même et toujours annoncée par celui qui gère la caisse ? La caisse est-elle encore ouverte ?
- Si l'organisation qui gère la caisse perd sa vérification dans l'annuaire, la fiche de la caisse le dit d'elle-même (« vérification retirée par PPF ») : l'équipe revérifie la caisse dans la semaine.

## Fermer ou retirer une fiche

- **La caisse ne reçoit plus de dons** : republier la fiche sous la même adresse avec `ppf-fund-status` = `close`. Le lien de dons disparaît, la fiche reste consultable.
- **La caisse ne respecte plus les règles** (page de dons changée sans explication, financement d'un candidat ou d'un parti, signalement confirmé de fausse cagnotte) : republier avec `ppf-fund-status` = `retiree` et la date. Une fiche n'est jamais effacée en silence : on dit qu'elle a été retirée.
- Un signalement d'un utilisateur sur une caisse est traité en priorité, comme un contenu grave.
