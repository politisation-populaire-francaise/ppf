# Intégration : fiches des partis politiques dans « Voir » (branche `feature/voir-partis`)

Décision de la propriétaire du 7 novembre 2026 (cahier fonctionnel, M17, « Ordre d'entrée des responsables publics ») : après les personnes citées dans les contenus publiés viennent **les grands partis**. Cette branche leur donne une fiche, sur l'axe « Le pouvoir ».

## Ce qui a été fait

- **Cœur** : nouvelle brique `ppf-type` = `parti` (et non `organisation`, déjà prise par l'annuaire d'« Agir »), lue par `PoliticalParty` (`core/voir/Parties.kt`) : nom (titre), sigle, date de fondation, sièges par assemblée et par source, responsables, site web, sources, corrections, « Et maintenant ? ». Une personne se rattache à son parti par un lien `a` de rôle `parti` ; un fait peut aussi viser un parti (promesse du programme, affaire visant le parti comme personne morale, vote d'un groupe).
- **Compléments demandés par la coordination** (repris de la branche doublon `worktree-agent-abf19b929e669beef`, arrêtée) : structure de financement **obligatoire** (association de financement agréée ou mandataire financier, loi n° 88-227 du 11 mars 1988, articles 11 à 11-2) avec sa référence d'agrément, **jamais le nom d'un mandataire financier** (personne physique) ; aide publique de l'État par année, avec le décret pour source ; parti politique européen et groupe au Parlement européen ; partis rangés **par nom** (et non plus par sièges), comme le tableau public du Pacte.
- **Catalogue** (`VoirCatalog`) : `parties` (triés par nom, accents, casse et mention [EXEMPLE] ignorés, jamais par taille), `party(address)`, `partyOf(person)`, `membersOf(party)`, `leadersOf(party)`, `partiesInUse()`, `persons(role, party)`, `VoirItem.OfParty`, `factsAbout` accepte l'adresse d'un parti (seulement les faits qui visent le parti lui-même), la recherche trouve les partis (nom, sigle, résumé), le journal des corrections les inclut, `TYPES` contient `PARTI`.
- **Interface** : section « Partis politiques » en tête de l'axe « Le pouvoir », page de liste, fiche de parti aux rubriques identiques pour tous (présentation, identité : financement, parti et groupe européens ; sièges avec sources côte à côte, aide publique de l'État par année avec son décret, direction, membres qui ont une fiche, promesses du programme, votes du groupe, affaires qui concernent le parti, argent public, sources, corrections, droits de réponse, « Et maintenant ? »), puce du parti sous le nom sur la fiche d'une personne, filtre par parti sur la liste des responsables publics, partis cités dans « Concerne » sur la page d'un fait et sur sa carte.
- **Pas de culpabilité par association** : la fiche d'un parti n'affiche jamais un fait qui vise seulement un membre ; les cartes des membres y sont affichées sans leurs compteurs de faits (paramètre `showFacts = false` de `PersonCard`), et un encadré l'explique.
- **Outil de signature** : clés `party:` et `leader:` (liens par `slug`) ; le signataire refuse un `party:` qui ne mène pas à un parti et un `leader:` ou `person:` qui ne mène pas à une personne, un parti sans structure de financement (ou avec deux), un mandataire financier dont le deuxième champ est rempli (aucun nom de personne physique), une ligne de sièges ou d'aide publique illisible, la même année d'aide écrite deux fois.
- **Contenus [EXEMPLE]** : quatre partis fictifs (`content/voir/partis/parti-exemple-a.md` à `-d.md`), trois faits (`promesse-parti-a-smic`, `promesse-parti-d-referendum`, `affaire-parti-b-comptes`), un `party:` ajouté aux quatre personnes (leur `group` est conservé). Sources fictives sur https://example.org.

## Fichiers touchés

- Nouveaux : `shared/core/.../core/voir/Parties.kt`, `shared/core/src/commonTest/.../core/voir/PartiesTest.kt`, `shared/ui/.../ui/voir/PartyPages.kt`, `shared/ui/src/commonMain/composeResources/values/strings_voir_partis.xml`, `shared/ui/src/jvmTest/.../ui/voir/PartyScreenTest.kt`, `content/voir/partis/*.md`, `content/voir/faits/promesse-parti-a-smic.md`, `content/voir/faits/promesse-parti-d-referendum.md`, `content/voir/faits/affaire-parti-b-comptes.md`, ce document.
- Modifiés (petites retouches) : `core/editorial/Article.kt` (`ArticleType.PARTI` ajouté en fin), `core/voir/VoirTags.kt` (`ROLE_PARTY`, `ROLE_LEADER`, `ppf-acronym`, `ppf-founded`, `ppf-seats`, `ppf-website`), `core/voir/VoirCatalog.kt`, `ui/voir/VoirScreen.kt`, `ui/voir/VoirPages.kt`, `ui/voir/VoirComponents.kt` (`PARTY_CHIP`, partis dans la carte d'un fait), `ui/voir/VoirState.kt` (`Parties`, `partyFilter`), `ui/content/ComprendreScreen.kt` (libellé du type `parti`), `tools/content-signer/.../ContentSigner.kt`, `content/voir/personnes/*.md`, `composeResources/files/contents/voir.json` (seul fichier json régénéré conservé).
- `Facts.kt` n'est pas touché : le parti d'une personne (`PublicPerson.partyAddress`) et les partis d'un fait (`Fact.parties`) sont des propriétés d'extension définies dans `Parties.kt`.

## Format, prêt à coller

### Dans `docs/FORMATS.md`, section 4 quinquies

Dans la ligne `a` du tableau des étiquettes communes, remplacer la liste des rôles par :

> rôle : `personne`, `texte`, `indicateur`, `cible` (contenu auquel répond un droit de réponse), `parti` (d'une personne ou d'un fait vers un parti) ou `responsable` (d'un parti vers les personnes qui le dirigent)

Dans la ligne `personne`, ajouter : « ; un lien `a` de rôle `parti` vers son parti (un seul est lu) ». Dans la ligne `fait`, ajouter : « Un fait peut viser un parti par un lien `a` de rôle `parti` (promesse du programme, affaire visant le parti comme personne morale, vote d'un groupe) ; il figure alors sur la fiche du parti. Un fait qui ne vise qu'un membre n'y figure jamais. »

Nouvelle ligne du tableau des types :

| `ppf-type` | Étiquettes propres |
|---|---|
| `parti` | `ppf-financing` (**obligatoire**, une seule) : `association-de-financement` ou `mandataire-financier`, référence de l'agrément (vide et ignorée pour un mandataire financier : jamais de nom de personne physique), date de l'agrément ou de la déclaration, type de source, nom de la source, adresse ; `ppf-public-funding` (une par année, 1988 ou après) : année, montant en euros (`4 210 345,60`), décret de répartition, adresse ; `ppf-european-party` ; `ppf-european-group` ; `ppf-acronym` (sigle), `ppf-founded` (date de fondation), `ppf-seats` (une par assemblée et par source) : assemblée (`assemblee`, `senat`, `parlement-europeen`, `autre`), nombre, date, type de source, nom de la source, adresse ; `ppf-website` (site du parti, adresse `https://` seulement) ; liens `a` de rôle `responsable` vers les fiches des personnes qui le dirigent. Le titre est le nom, le résumé une phrase de présentation, le corps la présentation. Le chiffre retenu pour une assemblée est celui de la date la plus récente, d'une source `parlement` ou `texte-officiel` s'il y en a une ; les partis sont rangés par nom (accents, casse et mention [EXEMPLE] ignorés), jamais par taille. Un parti sans structure de financement lisible n'est pas affiché. |

Et dans la ligne `ppf-type` de la section 4 bis, ajouter `parti` aux briques de « Voir ».

### Dans `content/README.md`, section « Format des contenus de « Voir » »

Remplacer la phrase des sous-dossiers et la liste des liens par :

> Les sous-dossiers (`indicateurs`, `faits`, `personnes`, `partis`, `textes`, `monde`, `dossiers`, `reponses`) ne servent qu'à ranger : c'est le champ `type` qui compte (`indicateur`, `fait`, `personne`, `parti`, `texte`, `mouvement`, `dossier`, `reponse`).
>
> - `person`, `text`, `indicator`, `target`, `party` et `leader` nomment une autre brique par son `slug` (lien). `party` (sur une personne ou un fait) doit mener à un parti, `leader` (sur un parti) et `person` à une personne : l'outil refuse sinon.
> - Exemple de financement : `financing: association-de-financement | Agrément CNCCFP n° … | 2009-07-02 | texte-officiel | Journal officiel, avis d'agrément | https://…` ; pour un mandataire financier, laisser le deuxième champ vide (`financing: mandataire-financier |  | 1998-12-15 | texte-officiel | Récépissé de déclaration en préfecture | https://…`) : son nom n'est jamais écrit.
> - Exemple d'aide publique : `public-funding: 2026 | 4 210 345,60 | Décret n° … portant répartition de l'aide publique pour 2026 | https://…`.
> - Exemple de sièges : `seats: assemblee | 120 | 2026-10-01 | parlement | Assemblée nationale, composition des groupes | https://…`. Une deuxième ligne pour la même assemblée et la même date (par exemple le chiffre annoncé par le parti) est affichée à côté, jamais fusionnée.

## Choix techniques à consigner (proposition : une décision D-0xx)

1. Type `parti` distinct de `organisation` (annuaire d'« Agir ») : un parti a des sièges, des responsables publics et des faits ; une association de l'annuaire n'en a pas.
2. Lien personne → parti porté par la fiche de la personne (rôle `parti`), et lien parti → responsables porté par la fiche du parti (rôle `responsable`) : les membres se déduisent, sans liste à tenir à jour en double.
3. Sièges : une étiquette par assemblée et par source ; le chiffre retenu est celui de la source primaire la plus récente ; les autres sources restent visibles côte à côte (même règle que les indicateurs : les sources ne sont jamais fusionnées).
4. Ordre des partis : ordre alphabétique seulement, jamais la taille (neutralité, comme le tableau public du Pacte) ; les sièges et l'aide publique sont sur la fiche, pas dans l'ordre.
7. Structure de financement obligatoire : c'est le critère légal qui fait d'un groupement un parti pour les règles de financement (loi n° 88-227, articles 11 à 11-2) ; le même pour tous, vérifiable sur une source primaire (avis d'agrément au Journal officiel, récépissé de préfecture). Le nom d'un mandataire financier (personne physique) n'est jamais stocké : minimisation des données, rien à ajouter à `REGISTRE_DONNEES.md`.
8. Aide publique : une ligne par année, montant stocké en centimes, le décret annuel de répartition comme source (type `texte-officiel`).
5. Pas de culpabilité par association : la fiche d'un parti ne montre que les faits qui visent le parti ; les cartes des membres y apparaissent sans leurs compteurs d'affaires.
6. Rubriques identiques pour tous les partis, même vides (« Rien de recensé pour l'instant »), et « Et maintenant ? » toujours présent (par défaut : les revendications des citoyens).

## Questions pour la propriétaire

1. **Décrets d'aide publique** : les numéros des décrets réels (2025, 2026) cités par des sources secondaires dans la branche doublon sont **à vérifier au Journal officiel** avant toute donnée réelle ; les exemples n'en citent aucun. Faut-il afficher le détail des deux fractions (article 9) et la retenue pour la parité (article 9-1), ou le seul total ?
1. **Quels partis entrent en premier ?** Proposition neutre : tout parti qui a au moins un élu à l'Assemblée nationale, au Sénat ou au Parlement européen, ou qui a obtenu au moins 1 % des voix au premier tour des dernières législatives (seuil du financement public, à vérifier dans la loi n° 88-227 du 11 mars 1988 avant usage). Le même critère s'applique à tous.
2. **Rubriques de la fiche** : faut-il ajouter « Financement » (comptes annuels publiés par la CNCCFP, aide publique reçue) et « Programme » (lien vers le programme complet archivé et horodaté) ?
3. **Groupe parlementaire et parti** : les fiches gardent les deux (`group` et `party`). Faut-il aussi des fiches de groupes parlementaires, ou le parti suffit-il ?
4. **Votes du groupe** : un vote « du groupe » est-il un fait du parti (vote collectif publié) ou seulement la somme des votes des membres ? La branche permet le premier cas, sans calcul automatique.
5. **Droit de réponse d'un parti** : le bouton est le même que pour une personne ; faut-il prévoir un contact propre aux partis (mandataire, secrétariat national) ?
6. Points à faire valider par l'avocat avant la publication de vrais partis (comme pour les personnes) : diffamation d'une personne morale, présentation des affaires visant un parti.

## Résultats des tests

RESULTS_PLACEHOLDER
