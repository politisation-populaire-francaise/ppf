# Consultations publiques officielles (DE-27) : conception

- Pôle « Décider », 9 novembre 2026. Cahier fonctionnel 2.1, M13.1 : « Consultations en cours des ministères, de la Commission nationale du débat public, enquêtes publiques locales et consultations des collectivités : liste, dates limites, guide pour y répondre, rappel gardé sur l'appareil. » Priorité : tôt.
- Décisions techniques prises en autonomie (gratuites, aucun tiers) ; rien de nouveau à trancher par la propriétaire.

## 1. Le droit et les sources

Vérifiés sur Légifrance le 9 novembre 2026 (extraits ; à relire en entier avant la mise en ligne du premier vrai contenu) :

- **Consultation ouverte sur internet** : code des relations entre le public et l'administration, articles L132-1 à L132-3. Quand elle doit consulter une commission avant un acte réglementaire, l'administration peut organiser à la place une consultation ouverte sur internet ; elle dure **au moins quinze jours** (L132-3) et donne lieu à une synthèse des observations (L132-2). [Chapitre II du code](https://www.legifrance.gouv.fr/codes/section_lc/LEGITEXT000031366350/LEGISCTA000031367441/).
- **Enquête publique** : code de l'environnement, articles L123-1 à L123-18. Durée d'**au moins trente jours** pour un projet, plan ou programme soumis à évaluation environnementale, réductible à quinze jours sinon ; prolongation possible de quinze jours au plus par le commissaire enquêteur (L123-9). [Article L123-9](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000033038541).
- **Participation du public par voie électronique** : code de l'environnement, article L123-19 (observations reçues pendant au moins trente jours, avis publié quinze jours avant), et L123-19-1, L123-19-2 pour d'autres décisions (délais plus courts). [Article L123-19](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000048247310).
- **Débat public et concertation préalable** : code de l'environnement, articles L121-1 et suivants (Commission nationale du débat public). **À vérifier** avant le premier contenu réel.
- **Consultations locales** : code général des collectivités territoriales, article LO1112-1 (référendum local) et articles L1112-15 et suivants (consultation des électeurs). **À vérifier** avant le premier contenu réel ; la pétition qui demande une consultation (L1112-16) relève de DE-26.
- PPF ne calcule jamais une date qui ferait foi : chaque date est écrite par l'équipe d'après l'avis officiel et citée ; le code vérifie seulement sa cohérence (avertissements de l'outil de signature).

## 2. Format

Un contenu `consultation` signé par l'équipe et relu comme les autres contenus éditoriaux ; détail dans `docs/FORMATS.md` (« Consultation publique officielle »). Exemples [EXEMPLE] : `content/decider/consultations/` (une consultation ouverte en cours, une enquête publique à venir).

## 3. Dans l'application

- **« Consultations publiques »** sur l’accueil de « Décider » : les consultations ouvertes (la plus proche de sa clôture d’abord), puis celles à venir ; chaque carte dit son type, qui l’organise et le temps qui reste. Les consultations locales ne paraissent que pour la commune choisie sur l’appareil dans « Mes élus » et son département, comme les scrutins locaux (calcul sur l’appareil, rien n’est envoyé).
- **Page d'une consultation** : type, organisateur, dates (premier et dernier jour inclus), temps restant, bouton « Répondre sur le site officiel » (après « Vous quittez PPF », avec le nom du site), une phrase fixe par type qui dit ce que prévoit la loi et cite ses articles, « PPF ne recueille aucune réponse », le guide pour répondre, les sources, les suites possibles.
- **Rappels** (contrat commun `docs/integration/rappels.md`) : désactivés par défaut, « Me rappeler cette consultation » programme sur l'appareil un rappel à l'ouverture (si elle est à venir) et trois jours avant le dernier jour, à 10 h, heure de Paris ; recalculés à chaque synchronisation (une enquête prolongée déplace le rappel) ; chacun se retire ; catégorie « Votes et pétitions ».
- Le serveur ne sait jamais quelle consultation une personne regarde ni à laquelle elle répond.

## 4. Étapes

1. Fait le 9 novembre 2026 : type, lecture et contrôles, accueil et page, rappels, deux exemples.
2. Site sans JavaScript : liste et page des consultations, avec les pages « Décider » du site.
3. Consultations locales selon la commune de « Mon territoire ».
4. Contenus réels, rédigés par l'équipe d'après les avis officiels.
