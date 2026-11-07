# « Comprendre » sur le site web

Branche `feature/comprendre-web` (D-091). Ce document décrit les pages de « Comprendre » sur le site rendu par le serveur (Ktor et kotlinx.html), lisibles sans JavaScript, leurs adresses, les choix d'accessibilité et les limites.

## Pages et adresses

Les adresses sont construites par `shared/core/.../core/comprendre/ComprendreLinks.kt`, dans le style de `VoirLinks` : identifiant du contenu (étiquette `d`, stable quand le contenu est corrigé), encodé selon la RFC 3986.

| Adresse | Contenu |
|---|---|
| `/comprendre` | Accueil : Connaître ses droits, Comprendre en 5 minutes, Qui décide de ça ?, Le vrai du faux, Histoire des luttes, Glossaire, Notre méthode |
| `/comprendre/fiches`, `/comprendre/fiches/{id}` | Fiches pratiques (`fiche-pratique`) |
| `/comprendre/parcours`, `/comprendre/parcours/{id}` | Parcours « Comprendre en 5 minutes », toutes les étapes sur une page |
| `/comprendre/decryptages`, `/comprendre/decryptages/{id}` | Décryptages : affirmation, verdict, explication, objections |
| `/comprendre/histoire`, `/comprendre/histoire/{id}` | Histoire des luttes : pays, période, revendications, résultats, chronologie |
| `/comprendre/qui-decide`, `/comprendre/qui-decide/{id}` | « Qui décide de ça ? » : chaîne de décision, personnes publiques liées, annuaires officiels |
| `/comprendre/glossaire` (`?q=mot`), `/comprendre/glossaire/{id}` | Glossaire par lettre, recherche sans accents, une page par mot (l'adresse omet le préfixe `glossaire-` des identifiants ; l'ancienne forme marche aussi) |
| `/comprendre/methode` | « Notre méthode » (`notre-methode`) ; 404 tant qu'elle n'est pas publiée |
| `/comprendre/hors-ligne` | Toutes les fiches en entier, la page de secours du service worker |
| `/sw.js` | Service worker (lecture hors ligne) |

Toute autre adresse sous `/comprendre/` répond une page 404 en HTML. Sans base de données configurée, `/comprendre` reste l'emplacement réservé de la phase 0 (comme `/voir`).

## Ordre d'une page de contenu

Fil d'Ariane, titre et badges (« Contenu d'exemple » pour les titres marqués [EXEMPLE], « Rédigé avec l'aide d'une IA »), résumé, texte (encarts `en-bref`, `legal`, `illegal`, `attention`, `conseil`, objections, chronologie), « Les mots du glossaire de cette page », « Et maintenant ? », sources, puis la ligne d'origine (rédigé par l'équipe ou avec l'aide d'une IA, dates). Les blocs `::: et-maintenant` et la section `## Sources` sont sortis du texte et placés à la fin.

« Et maintenant ? » : les actions du contenu ; `[texte](ppf:revendications)` devient un lien vers `/revendications`. Sans actions, le site propose des suites selon le type (agenda et Agir pour une fiche ou un récit, revendications et Décider pour une fiche « Qui décide de ça ? », décryptages pour la méthode), puis « Tout « Comprendre » ».

## Choix d'accessibilité

- **Glossaire dans les textes** : la première occurrence de chaque mot dans la page devient un lien vers sa page, souligné en pointillés (pas seulement une couleur). Le lien porte `aria-describedby` vers sa définition rapide, écrite dans la liste « Les mots du glossaire de cette page » en bas de la page : un lecteur d'écran lit la définition au passage sur le lien, tout le monde la voit en bas de page, et elle est imprimée avec la page. Jamais dans les titres, les liens, les adresses ni les sources ; le repérage est celui de l'application (`GlossaryIndex`). Écartés : `<abbr title>` (infobulle invisible au clavier et sur un écran tactile) et toute infobulle en JavaScript.
- **Quiz** : question, réponses dans une liste lettrée (A, B, C), puis un `<details>` « Voir la bonne réponse » qui montre la lettre, la réponse et l'explication sourcée. Aucun formulaire, rien n'est envoyé ni enregistré ; la page le dit (la progression reste une fonction de l'application, sur l'appareil).
- Repères HTML (`nav`, `main`, sections nommées par leur titre), fil d'Ariane avec `aria-current`, sommaire des étapes, liens sortants signalés en toutes lettres avec le nom du site (« site extérieur : www.legifrance.gouv.fr »), cibles tactiles de 44 pixels pour les lettres et le formulaire de recherche, contrastes des jetons du site en clair et en sombre.

## Hors ligne et PWA

- `/sw.js` est écrit par le serveur à partir de `web/src/main/resources/comprendre/sw-template.js` : liste des fiches à garder et version (empreinte des fiches, du glossaire et des fichiers du site), écrites en JSON. Il garde à l'installation l'accueil de « Comprendre », la liste et chaque fiche pratique, la page « Lire hors ligne » et les fichiers du site ; puis chaque page visitée sous `/comprendre` et `/voir`. Les pages sont demandées d'abord au réseau, la copie ne sert que sans réseau (sinon la page « Lire hors ligne ») ; les fichiers de `/static/` sont lus d'abord dans le cache. Seulement les requêtes GET du site ; jamais l'API.
- Il est enregistré par `/static/pwa.js` (chargé avec `defer` par les seules pages de « Comprendre », permis par `script-src 'self'`). C'est un plus : sans lui, tout se lit pareil en ligne, et la page « Lire hors ligne » peut être enregistrée ou imprimée.

## Fichiers

| Fichier | Rôle |
|---|---|
| `shared/core/.../core/comprendre/ComprendreLinks.kt` | Adresses stables |
| `web/.../web/comprendre/ComprendreSource.kt` | Interface de lecture et catalogue (une version par identifiant, la plus récente ; signatures revérifiées, clés éditoriales seules) |
| `web/.../web/comprendre/ComprendreMarkdown.kt` | Rendu Markdown et repérage du glossaire |
| `web/.../web/comprendre/ComprendreComponents.kt` | Libellés, annuaires officiels, sections communes |
| `web/.../web/comprendre/ComprendrePages.kt` | Routes et pages |
| `web/.../web/comprendre/OfflineWorker.kt`, `resources/comprendre/sw-template.js`, `resources/static/pwa.js` | Lecture hors ligne |
| `web/.../web/WebRoutes.kt` | Montage (fichier commun) |
| `web/src/main/resources/static/site.css` | Section « Comprendre » ajoutée à la fin (fichier commun) |
| `server/.../server/comprendre/EditorialComprendreSource.kt`, `Application.kt` | Lecture dans PostgreSQL, montage |
| `server/src/test/.../BundledContents.kt`, `DevServer.kt` | Chargement de `parcours.json`, `comprendre.json`, `glossaire.json` |
| `web/src/test/.../comprendre/ComprendrePagesTest.kt`, `server/src/test/.../comprendre/ComprendreSitePagesTest.kt` | Tests |

## Limites

- Les pages de « Voir » ne lient pas encore les fiches pratiques (fichiers de « Voir » non touchés) : `ComprendreLinks.pathOf` est prêt pour son pôle.
- Les dossiers (`dossier`) restent dans « Voir » ; pas de version « Facile à lire ».
- Le manifeste n'a qu'une icône SVG ; certains navigateurs demandent des PNG de 192 et 512 pixels pour proposer l'installation.
- Le service worker n'a été essayé que sur Chromium de bureau ; l'essai sur un vrai téléphone est reporté à la phase de test.

## Comment essayer

`gradlew :server:runDev`, puis http://localhost:8080/comprendre (voir `GUIDE_PROPRIETAIRE.md`, « Voir « Comprendre » sur le site »).
