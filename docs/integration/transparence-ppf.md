# Transparence de PPF (PP-01) : conception, version 0.1

Rédigé le 9 novembre 2026 par le pôle « Voir » (« [Voir] Team Dev 2 »), à la demande du chef d'équipe, d'après le cahier fonctionnel (M34, PP-01 : « Comptes annuels, décisions importantes, composition et fonctionnement des instances, rapport de modération, bilan annuel des réclamations »), le principe 11 du cahier des charges (« la transparence d'abord ») et ses sections 9.4 (rapport de transparence, articles 15 et 24 du règlement sur les services numériques) et S-110 (bilan annuel des réclamations). Décision D-104. Branche `feature/voir-presence`, pas encore compilée.

Tout est **[EXEMPLE]** tant que l'association n'existe pas : aucun contenu réel, aucun nom réel.

## 1. Principes

1. **Les mêmes rubriques chaque année**, dans le même ordre, publiées comme des contenus signés de l'équipe, avec leurs sources, leurs archives horodatées et le journal public de leurs corrections (comme les briques de « Voir »).
2. **Des chiffres, jamais un contenu ni un compte** : le rapport de modération et le bilan des réclamations ne donnent que des nombres agrégés (cahier des charges, section 9.4).
3. **Les fonctions avant les noms** : la composition d'une instance donne les fonctions, la date d'entrée et le mode de désignation. Un nom n'est écrit qu'avec l'accord de la personne (principe 10 : protéger par défaut). Le champ est vide par défaut. Publier ou non les noms des membres du bureau oppose la transparence et la protection des personnes : la question est posée à la propriétaire (section 6), jamais tranchée seule (principe 11).
4. **Les comptes en entier** : le document approuvé est archivé (empreinte SHA-256, preuve d'horodatage, adresse) et ses grandes lignes sont saisies, produits et charges. Le résultat est calculé par l'application, jamais saisi.

## 2. Type de contenu `transparence` (D-104)

Une brique de l'espace « Voir » (même moteur éditorial, même formulaire du back-office, même relecture, même fichier `voir.json`), lue par `core/voir/Transparency.kt`. Rubrique obligatoire, puis les champs de sa rubrique :

| Champ (en-tête) | Étiquette | Rubrique | Contenu |
|---|---|---|---|
| `rubric` | `ppf-rubric` | toutes | `instance`, `decision`, `comptes` ou `rapport` |
| `instance` | `ppf-instance` | instance, décision | `bureau`, `conseil-administration`, `assemblee-generale`, `equipe-editoriale`, `equipe-moderation`, `autre` |
| `seat` | `ppf-seat` | instance | fonction, nom (vide par défaut), depuis (date), mode de désignation |
| `date` | `ppf-date` | décision (obligatoire) | date de la décision |
| `tally` | `ppf-tally` | décision | pour, contre, abstention |
| `year` | `ppf-year` | comptes (obligatoire) | exercice |
| `account-line` | `ppf-account-line` | comptes | `produit` ou `charge`, catégorie, montant en euros (« 1 234,56 ») |
| `approved` | `ppf-approved` | comptes | date d'approbation par l'assemblée générale |
| `period` | `ppf-period` | rapport (obligatoire) | début, fin |
| `figure` | `ppf-figure` | rapport | code, valeur |
| `archive`, `source`, `correction` | comme dans « Voir » | toutes | document archivé (procès-verbal, comptes), sources, journal des corrections |

Le corps (Markdown) explique : le fonctionnement de l'instance, les raisons de la décision, les commentaires des comptes, la méthode du rapport.

**Codes des chiffres du rapport** (liste fixe, toujours dans cet ordre ; une valeur absente est affichée « non mesuré », jamais 0) :
- Modération (règlement sur les services numériques, articles 15 et 24, cahier des charges 9.4) :
  - `injonctions-autorites` : injonctions et demandes d'information des autorités ;
  - `signalements-recus` : signalements reçus ;
  - `decisions-sur-signalement` : décisions prises sur signalement ;
  - `decisions-initiative` : décisions prises à l'initiative de l'équipe ;
  - `masquages-automatiques` : contenus masqués provisoirement par la modération automatique (alerte rouge) ;
  - `alertes-automatiques` : contenus signalés sans masquage par la modération automatique (alerte orange) ;
  - `delai-median-heures` : délai médian de décision, en heures ;
  - `recours-internes` : réclamations contre une décision de modération ;
  - `decisions-modifiees` : décisions annulées ou modifiées après réclamation ;
  - `transmissions-autorites` : transmissions aux autorités ;
  - `precision-automatique-pourcent` : précision de la modération automatique, en pourcentage ;
  - `erreur-automatique-pourcent` : taux d'erreur de la modération automatique, en pourcentage.
- Réclamations (S-110, M30) :
  - `reclamations-recues` : réclamations reçues ;
  - `reclamations-hors-delai` : réclamations traitées hors délai ;
  - `droits-donnees` : demandes d'exercice des droits sur les données ;
  - `requisitions` : réquisitions judiciaires reçues.

## 3. Pages

- **Application** : page « Transparence de PPF », ouverte depuis le bas de l'axe « Le pouvoir » de « Voir », à côté de « Vérifier un contenu ». Sections dans l'ordre : instances, décisions importantes (de la plus récente à la plus ancienne), comptes (de l'exercice le plus récent au plus ancien, avec le total des produits, le total des charges et le résultat), rapports (de la période la plus récente à la plus ancienne). Sous la page : liens vers « Notre méthode », les données ouvertes et « Vérifier un contenu ».
- **Site** : `/transparence`, même contenu, lisible sans JavaScript, lien dans le pied de page.
- **Aperçu du back-office** : celui du site.

## 4. Plus tard

- Le rapport de modération et le bilan des réclamations proposés automatiquement en brouillon par le back-office, à partir de ses journaux (avec le Directeur technique) : l'équipe relit, puis publie à deux.
- La page de transparence technique (cahier des charges, section 5.7) reste au Directeur technique ; la page de PPF y renvoie quand elle existe.
- La trésorerie publique (PP-06) a sa propre page, conçue avec le Directeur technique ; les comptes annuels y renverront.

## 5. Données et menaces

- Aucune donnée d'utilisateur : des contenus publiés par l'équipe. Un nom de membre d'une instance n'est publié qu'avec l'accord de la personne (à consigner hors de la plateforme, par l'association).
- Risque : une personne nommée devient une cible. Défense : les fonctions seulement par défaut ; le nom sur accord écrit ; retrait possible à tout moment par une nouvelle version, avec une ligne au journal des corrections.

## 6. Question pour la propriétaire (par le chef d'équipe)

Faut-il publier les noms des membres du bureau et du conseil d'administration ?
- **Option A (recommandée)** : les fonctions seulement par défaut ; un nom seulement si la personne le demande par écrit.
- **Option B** : les noms toujours publiés, comme exemplarité.
- **Option C** : aucun nom, jamais.

Ma recommandation est l'option A, pour trois raisons :
- elle protège les personnes dans le climat du mouvement social ;
- elle laisse chacun libre de se nommer ;
- les noms des dirigeants restent déclarés à la préfecture. L'article 5 de la loi du 1er juillet 1901 impose de déclarer « les noms et professions, domiciles et nationalités » de ceux qui sont chargés de l'administration, et de signaler tout changement dans les trois mois. Je l'ai lu dans le formulaire Cerfa 13971 de l'administration (préfecture de la Gironde) et dans des questions écrites de l'Assemblée nationale, pas encore sur Légifrance.

Point à faire relire par l'avocat, avec les statuts.
