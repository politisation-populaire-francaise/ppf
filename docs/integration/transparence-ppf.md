# Transparence de PPF (PP-01) : conception, version 0.2

Rédigé le 9 novembre 2026 par le pôle « Voir » (« [Voir] Team Dev 2 »), à la demande du chef d'équipe, d'après le cahier fonctionnel (M34, PP-01 : « Comptes annuels, décisions importantes, composition et fonctionnement des instances, rapport de modération, bilan annuel des réclamations »), le principe 11 du cahier des charges (« la transparence d'abord ») et ses sections 9.4 (rapport de transparence, articles 15 et 24 du règlement sur les services numériques) et S-110 (bilan annuel des réclamations). Décision D-104. Version 0.2 du 9 novembre 2026 : décision de la propriétaire sur les noms (option B, section 6).

Tout est **[EXEMPLE]** tant que l'association n'existe pas : aucun contenu réel, aucun nom réel.

## 1. Principes

1. **Les mêmes rubriques chaque année**, dans le même ordre, publiées comme des contenus signés de l'équipe, avec leurs sources, leurs archives horodatées et le journal public de leurs corrections (comme les briques de « Voir »).
2. **Des chiffres, jamais un contenu ni un compte** : le rapport de modération et le bilan des réclamations ne donnent que des nombres agrégés (cahier des charges, section 9.4).
3. **Les noms du bureau et du conseil d'administration, et rien d'autre** (décision de la propriétaire, option B, section 6) : chaque membre de ces deux instances est nommé, avec sa fonction, sa date d'entrée et son mode de désignation. Rien d'autre sur la personne : ni photo, ni contact personnel, ni adresse, ni profession. Chaque membre est informé avant la publication (RGPD), et la page le dit. Dans les autres instances (équipe éditoriale, équipe de modération, autre), les fonctions seules par défaut ; un nom seulement si la personne le demande (principe 10). Le formulaire du back-office refuse un membre du bureau ou du conseil d'administration sans nom (`voir.rule.seat_name`).
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

- Aucune donnée d'utilisateur : des contenus publiés par l'équipe. Données personnelles publiées : le nom et la fonction des membres du bureau et du conseil d'administration (base légale à faire confirmer par l'avocat : l'intérêt légitime de transparence d'une association politique, ces noms étant déjà déclarés en préfecture), et, dans les autres équipes, le nom d'une personne qui le demande. Chaque membre est informé avant la publication, par l'association, hors de la plateforme.
- Risque : une personne nommée devient une cible, dans le climat du mouvement social. Défenses : rien d'autre que le nom et la fonction (ni photo, ni contact, ni ville) ; les équipes éditoriale et de modération sans nom par défaut ; correction ou retrait par une nouvelle version, avec une ligne au journal des corrections, quand la personne quitte sa fonction ; un contact de l'association, jamais un contact personnel.

## 6. Décision de la propriétaire : option B (9 novembre 2026)

Question posée par le chef d'équipe : faut-il publier les noms des membres du bureau et du conseil d'administration ? Options : A, les fonctions seulement et un nom sur demande écrite (recommandée par le pôle « Voir ») ; B, les noms toujours publiés ; C, aucun nom.

**Décision de la propriétaire, le 9 novembre 2026 au matin, transmise par le chef d'équipe : option B**, au motif que « ces infos sont publiques de toute façon » (déclaration en préfecture : l'article 5 de la loi du 1er juillet 1901 impose de déclarer les noms, professions, domiciles et nationalités de ceux qui sont chargés de l'administration ; lu dans le formulaire Cerfa 13971 et des questions écrites de l'Assemblée nationale, pas encore sur Légifrance).

Ce que le code applique :
- nom et fonction seulement, rien d'autre (ni photo, ni contact personnel) ; la profession, le domicile et la nationalité déclarés en préfecture ne sont jamais repris ;
- une ligne de la page indique que chaque membre est informé avant la publication (RGPD) ;
- un membre du bureau ou du conseil d'administration sans nom est refusé par le formulaire du back-office ;
- les autres équipes gardent la règle protectrice : les fonctions seules, un nom à la demande de la personne.

Point à faire relire par l'avocat, avec les statuts : la base légale de la publication et l'information des membres.
