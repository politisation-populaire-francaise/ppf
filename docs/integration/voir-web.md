# « Voir » sur le site web

Branche `feature/voir-web`. Ce document décrit ce qui a été fait pour afficher l’espace « Voir » sur le site (rendu côté serveur, lisible sans JavaScript), les choix techniques à consigner, les questions pour la propriétaire et la façon d’essayer.

## Ce qui a été fait

- **Une page par contenu, à une adresse stable** (partageable, liée depuis partout) : chiffres, faits sourcés, fiches de responsables publics, textes de loi, mouvements dans le monde, dossiers. L’adresse est construite à partir de l’identifiant du contenu (étiquette `d`), qui ne change pas quand le contenu est corrigé.
- **L’accueil de « Voir »** reprend les trois axes de l’application : le mouvement (chiffres clés avec toutes les sources côte à côte, aperçu de la chronologie, le monde), le pouvoir (responsables publics, textes en discussion, derniers faits sourcés), libertés et contre-pouvoirs (tableau de bord des contre-pouvoirs, observatoire des libertés, dossiers), avec un lien vers la méthode.
- **Les pages de liste** : chronologie du mouvement (filtre par type, par simples liens), observatoire des libertés publiques (filtre par catégorie), journal des corrections, méthode (« Lire les étiquettes de Voir »).
- **Le graphique d’un chiffre est dessiné par le serveur** en SVG intégré à la page : une courbe par type de source, légende, valeur au bout de chaque courbe, quadrillage fin, titre et description accessibles. Les mêmes données sont données juste après dans un **tableau HTML**. Couleurs exactes de la palette de l’application (`PpfPalette.chart`, clair et sombre), même couleur fixe par type de source que l’application (`chartSlot` d’`Observatory.kt`).
- **Un fait** affiche son statut (établi, allégué, en cours d’enquête), l’étape de la procédure et son explication, la **mention de présomption d’innocence pour toute procédure sans condamnation définitive**, la citation exacte, les personnes et textes concernés, les sources, les copies archivées (empreinte SHA-256), les corrections et les droits de réponse publiés dessous.
- **Une fiche de personne** montre les mêmes rubriques, dans le même ordre que l’application, pour tout le monde (règle « Traitement identique ») : mandats, votes, promesses, discours et votes, affaires, déclarations contredites, intérêts, rencontres avec des représentants d’intérêts, argent public. Une rubrique vide le dit (« Rien de recensé pour l’instant. »).
- **Un texte de loi** : nature (projet ou proposition), étape (« Étape 4 sur 7 : Sénat ») et parcours daté, prochain vote, recours au 49.3, qui gagne et qui perd, votes des élus, influence des représentants d’intérêts.
- **Chaque page se termine** par les sources, la mention d’origine (rédigé par l’équipe, ou avec l’aide d’une IA), puis « Et maintenant ? ». Les liens vers des espaces de l’application deviennent des liens vers les pages de ces espaces sur le site ; ceux vers un autre contenu de « Voir » mènent à sa page.
- **Seuls les contenus signés par les clés de l’équipe éditoriale** sont affichés (contrôle refait à la lecture : une clé retirée de l’équipe cesse d’être affichée). Une adresse inconnue répond une page 404 en HTML.
- **Liens sortants** : uniquement des adresses web (`https://`, `http://`), avec `rel="noopener noreferrer nofollow external"` et la mention visible « (site extérieur) ».
- **Serveur de développement** : `voir.json` est chargé avec les autres contenus d’exemple.

## Fichiers

| Fichier | Rôle |
|---|---|
| `shared/core/.../core/voir/VoirLinks.kt` | Chemins et adresses complètes des contenus (`pathOf`, `urlOf`), partagés par le site et, plus tard, le bouton « Partager » de l’application |
| `shared/core/src/commonTest/.../voir/VoirLinksTest.kt` | Tests des chemins et de l’encodage |
| `web/.../web/voir/VoirSource.kt` | Interface de lecture du catalogue (fournie par le serveur) et index par adresse |
| `web/.../web/voir/VoirPages.kt` | Routes et pages |
| `web/.../web/voir/VoirComponents.kt` | Cartes, badges, sources, archives, corrections, droit de réponse, « Et maintenant ? » |
| `web/.../web/voir/VoirChart.kt` | Graphique SVG rendu par le serveur |
| `web/.../web/voir/WebMarkdown.kt` | Rendu du Markdown des contenus (sous-ensemble, tout le texte est échappé) |
| `web/.../web/voir/VoirWebLabels.kt` | Libellés français (mêmes mots que `strings_voir.xml`) |
| `web/.../web/WebRoutes.kt` | Monte les pages de « Voir » à la place de l’emplacement réservé quand une base est configurée |
| `web/src/main/resources/static/site.css` | Styles de « Voir » (cartes, badges, encadrés, tableaux, graphique, couleurs claires et sombres) |
| `server/.../server/voir/EditorialVoirSource.kt` | Lit les contenus de « Voir » des clés éditoriales dans PostgreSQL, catalogue gardé 30 secondes |
| `server/.../server/Application.kt` | Branche la source sur les routes du site |
| `server/src/test/.../BundledContents.kt`, `DevServer.kt` | Chargement de `voir.json` au démarrage du serveur de développement |
| `server/src/test/.../voir/VoirPagesTest.kt` | Tests de bout en bout (serveur de test Ktor et vraie base PostgreSQL) |
| `web/src/test/.../voir/VoirChartTest.kt` | Tests du graphique, de la page d’un chiffre et du Markdown |

## Adresses

| Adresse | Contenu |
|---|---|
| `/voir` | Accueil de « Voir » (trois axes) |
| `/voir/chiffres/{identifiant}` | Un indicateur |
| `/voir/faits/{identifiant}` | Un fait sourcé |
| `/voir/personnes/{identifiant}` | Une fiche de responsable public |
| `/voir/textes/{identifiant}` | Un texte de loi |
| `/voir/monde/{identifiant}` | Un mouvement dans le monde |
| `/voir/dossiers/{identifiant}` | Un dossier |
| `/voir/chronologie` (`?type=mobilisation`, …) | Chronologie du mouvement |
| `/voir/libertes` (`?categorie=dissolution`, …) | Observatoire des libertés publiques |
| `/voir/corrections` | Journal des corrections |
| `/voir/methode` | Lire les étiquettes (statuts, fiabilité, procédure, présomption d’innocence) |

Sans base de données configurée, `/voir` reste l’emplacement réservé de la phase 0.

## Choix techniques à consigner (pour `DECISIONS.md`)

1. **Adresses des contenus de « Voir »** : `/voir/<type>/<identifiant>` avec des segments en français (`chiffres`, `faits`, `personnes`, `textes`, `monde`, `dossiers`), l’identifiant étant l’étiquette `d` encodée selon la RFC 3986. Construites par `core/voir/VoirLinks` pour que le site et l’application donnent toujours la même adresse. Si deux clés éditoriales publient le même identifiant pour le même type, l’adresse mène à la version la plus récente.
2. **Graphique en SVG rendu par le serveur**, sans bibliothèque ni script : la page reste lisible sans JavaScript et sur un iPhone. Aucune couleur n’est écrite dans la page (la CSP interdit les styles en ligne) : chaque courbe porte une classe `chart-s0` à `chart-s7`, dont la couleur est définie dans `site.css` en mode clair et en mode sombre. Les étiquettes de valeur restent à la couleur du texte, car certaines couleurs de la palette (le jaune `#EDA100` par exemple) n’atteignent pas le contraste exigé pour du texte ; le tableau donne toujours les mêmes données.
3. **Markdown** : un petit rendu maison (titres, listes, citations, gras, italique, liens web, encadré `::: en-bref`), sans dépendance, qui échappe tout le texte. Aucun HTML des contenus n’est jamais recopié tel quel.
4. **Lecture des contenus** : le serveur lit les événements de type 30023 dont le `ppf-type` est un bloc de « Voir », ne garde que ceux des clés éditoriales (`PPF_EDITOR_KEYS`) et construit le catalogue avec `VoirCatalog.from`, exactement comme l’application. Le catalogue est gardé 30 secondes en mémoire : une correction apparaît sur le site au plus tard 30 secondes après sa publication.
5. **Filtres sans script** : de simples liens (`?type=`, `?categorie=`), le filtre actif étant signalé aux lecteurs d’écran (`aria-current`).
6. **Correspondance des couleurs par source** : la table `chartSlot` est recopiée dans `VoirChart.slotOf` (le site ne dépend pas de l’interface Compose). À terme, la déplacer dans `core/voir` pour n’avoir qu’une seule source ; un test du site vérifie la correspondance actuelle.

## Questions pour la propriétaire

1. **« Et maintenant ? » par défaut** : quand un contenu ne propose aucune suite, le site en propose une selon son type (chiffre ou mouvement : agenda des mobilisations et Agir ; fait, fiche ou texte : Décider et les revendications ; dossier : Comprendre et Agir), plus « Tous les contenus de Voir ». Ces suites vous conviennent-elles ?
2. **Liens vers des contenus hors de « Voir »** (par exemple la fiche pratique « Droits en manifestation ») : le site n’a pas encore de page pour les fiches pratiques, ces liens sont donc omis pour l’instant. Ils apparaîtront quand les pages de « Comprendre » et des fiches existeront sur le site.
3. **Bouton « Partager »** de l’application : `VoirLinks.urlOf` est prêt ; l’agent de l’application peut l’utiliser pour partager l’adresse du site plutôt qu’un texte.
4. **Contraste des courbes** : la palette imposée contient des couleurs claires (jaune, rose) dont le contraste avec le fond blanc est inférieur à 3 pour 1. Le tableau compense (WCAG 1.1.1 et 1.4.11), mais souhaitez-vous des courbes un peu plus foncées sur le site ?

## Comment essayer

1. Lancer le serveur de développement : `gradlew :server:runDev` (une base PostgreSQL est démarrée automatiquement, les contenus d’exemple sont chargés ; la console affiche « Voir: … block(s) of voir.json »).
2. Ouvrir dans le navigateur :
   - http://localhost:8080/voir
   - http://localhost:8080/voir/chiffres/manifestants-journees (graphique et tableau)
   - http://localhost:8080/voir/faits/affaire-dominique-marche-public (procédure, présomption d’innocence, droit de réponse)
   - http://localhost:8080/voir/personnes/alex-modele (fiche, mêmes rubriques pour tous)
   - http://localhost:8080/voir/textes/pjl-ordre-public-manifestations (législomètre)
   - http://localhost:8080/voir/chronologie, http://localhost:8080/voir/libertes, http://localhost:8080/voir/corrections, http://localhost:8080/voir/methode
   - http://localhost:8080/voir/faits/inexistant (page 404)
3. Essayer en mode sombre (réglage du système), en réduisant la fenêtre à la largeur d’un téléphone, et en désactivant JavaScript : tout doit rester lisible.

## Résultats des tests

Voir la fin de ce document (mis à jour après la compilation complète).
