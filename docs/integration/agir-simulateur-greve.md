# Note d'intégration : simulateur du gréviste (M35 : AG-60)

- **État (9 novembre 2026)** : règles vérifiées le 9 novembre 2026 sur Légifrance, service-public.gouv.fr et senat.fr (section 2) ; cœur, interface, textes et tests écrits **sans compiler** (compilations de branche interdites ce jour-là sur cette machine). **Rien n'est compilé ni lancé** : voir la section 11 avant la première compilation.
- Rédigée par l'agent « simulateur du gréviste » du pôle Agir (worktree `agent-a71b16c862b975aaa`, parti de `feature/agir` au commit `253d7da9`). Elle s'adresse à la session « Agir », qui fusionne et numérote la décision de la section 8 (le bloc de numéros d'Agir est épuisé : rien n'est écrit dans `docs/DECISIONS.md`).
- Les textes de l'interface et de cette note ont été rédigés par une IA. La relecture par un avocat est inscrite dans `docs/ROADMAP.md` (« À faire relire par un avocat avant l'ouverture publique »).

## 1. Ce que fait le simulateur

Depuis l'onglet « Caisses » de la rubrique « Solidarité » d'« Agir », un bouton ouvre le simulateur. La personne choisit :

1. son **statut** : salarié d'une entreprise (droit privé), agent de la fonction publique de l'État, de la territoriale ou de l'hospitalière (fonctionnaire ou contractuel de droit public) ;
2. son **secteur**, quand il change le préavis ou la déclaration (transports terrestres de voyageurs, transport aérien, autre service public géré par une entreprise, école maternelle ou élémentaire publique, navigation aérienne, services territoriaux soumis à la continuité) ;
3. sa **rémunération mensuelle brute** (et, dans le privé, sa durée de travail par semaine et par jour) ;
4. les **jours de grève** du mois : journée entière, demi-journée ou nombre d'heures, et, dans la fonction publique, les jours sans service compris entre deux jours de grève sans reprise du travail.

Il affiche l'estimation de la retenue (montant brut, part de la rémunération, détail par jour), la règle appliquée et son explication, les sources, le préavis et la déclaration individuelle qui s'appliquent au secteur, ce qu'il faut savoir aussi, puis « Caisses de grève proches » (l'annuaire des caisses de M35, filtré sur les caisses de grève et, si la personne l'écrit, sur son territoire). La mention « estimation, pas un conseil juridique ni un bulletin de paie » est toujours visible au-dessus du résultat, et la ligne « Rédigé par l'IA, règles vérifiées le 9 novembre 2026 » sous les règles.

**Rien n'est enregistré ni envoyé** : la rémunération, les jours et le territoire restent en mémoire, dans l'écran, le temps de la visite d'« Agir » ; l'effacement rapide les oublie (partie `agir-strike-simulator`).

## 2. Règles vérifiées et sources

Toutes les pages ont été consultées le **9 novembre 2026** ; Légifrance affichait les versions « en vigueur au 09/11/2026 ». Les citations sont courtes ; le texte complet est à l'adresse donnée.

### 2.1 Salariés d'une entreprise (droit privé)

| Règle | Source |
|---|---|
| La retenue est proportionnelle à la durée de l'arrêt de travail ; toute retenue supérieure est interdite ; elle porte sur le salaire et ses éventuels accessoires (indemnité de déplacement, par exemple). Pas de préavis dans le privé : la grève peut être déclenchée à tout moment. L'exercice du droit de grève ne doit pas être mentionné sur le bulletin de paie. | service-public.gouv.fr, fiche F117 « Droit de grève d'un salarié du secteur privé », vérifiée le 10 août 2026 : https://www.service-public.gouv.fr/particuliers/vosdroits/F117 (elle renvoie au code du travail, L1132-1 à L1132-4, L2511-1, L2512-1 à L2512-5, R3243-4) |
| « la rémunération des salariés grévistes ne doit subir qu'un abattement proportionnel à la durée de l'arrêt de travail » ; pas de mesure discriminatoire en matière de rémunération | Cour de cassation, chambre sociale, 8 juillet 1992, n° 89-42.563, publié au bulletin : https://www.legifrance.gouv.fr/juri/id/JURITEXT000007028788/ |
| Aucune mesure discriminatoire, notamment en matière de rémunérations et d'avantages sociaux | Code du travail, article L2511-1 (en vigueur depuis le 1er mai 2008) : https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000006902372 |
| Base horaire : pour un horaire égal à la durée légale, la rémunération mensuelle est la rémunération horaire multipliée par les 52/12 de la durée légale hebdomadaire | Code du travail, article L3242-1 (en vigueur depuis le 1er mai 2008) : https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000006902858 |
| Durée légale : trente-cinq heures par semaine, d'où 35 × 52 ÷ 12 = 151,67 heures par mois | Code du travail, article L3121-27 (en vigueur depuis le 10 août 2016) : https://www.legifrance.gouv.fr/codes/section_lc/LEGITEXT000006072050/LEGISCTA000006189631/ |
| Primes : l'employeur peut tenir compte des absences, même motivées par la grève, pour une prime, si toutes les absences (sauf celles assimilées par la loi à du travail effectif) ont les mêmes effets | Cour de cassation, chambre sociale, 26 mars 2014, n° 12-18.125, publié au bulletin : https://www.legifrance.gouv.fr/juri/id/JURITEXT000028797769/ |
| Forfait en jours : pour une absence qui ne se compte pas en journées ou demi-journées, retenue identique à toute autre absence de même durée, calculée selon l'accord collectif ou, à défaut, sur un salaire horaire tiré du nombre de jours du forfait | Cour de cassation, chambre sociale, 13 novembre 2008, n° 06-44.608, publié au bulletin : https://www.legifrance.gouv.fr/juri/id/JURITEXT000019772498 (**non simulé**, voir 2.8) |
| Transports terrestres réguliers de voyageurs : la rémunération (salaire et compléments directs et indirects, sauf suppléments pour charges de famille) « est réduite en fonction de la durée non travaillée » | Code des transports, article L1324-11 (en vigueur depuis le 1er décembre 2010) : https://www.legifrance.gouv.fr/affichCode.do?idSectionTA=LEGISCTA000023085508&cidTexte=LEGITEXT000023086525 ; Cour de cassation, chambre sociale, 8 juillet 2020, n° 19-13.767, publié au bulletin : https://www.legifrance.gouv.fr/juri/id/JURITEXT000042128076/ |

**Calcul retenu** : retenue = rémunération mensuelle brute × heures de grève ÷ heures mensuelles, avec heures mensuelles = heures par semaine × 52 ÷ 12 (151,67 h pour 35 h ; 169 h pour 39 h). Une « journée entière » vaut les heures que la personne devait travailler ce jour-là (7 h par défaut, modifiable) ; une demi-journée, la moitié. Les jours sans travail prévu ne sont pas retenus. La retenue ne dépasse jamais la rémunération du mois.

### 2.2 Fonction publique de l'État (FPE)

| Règle | Source |
|---|---|
| « L'absence de service fait, pendant une fraction quelconque de la journée, donne lieu à une retenue » égale à la fraction de la rémunération frappée d'indivisibilité (le trentième), à l'exclusion des suppléments pour charges de famille et des remboursements de frais ; le dernier alinéa réserve cette règle aux agents publics de l'État en grève. Issu de l'article 4 de la loi de finances rectificative n° 61-825 du 29 juillet 1961, codifié depuis le 1er mars 2022 | Code général de la fonction publique (CGFP), articles L711-1, L711-2 et L711-3 (créés par l'ordonnance n° 2021-1574 du 24 novembre 2021, en vigueur depuis le 1er mars 2022) : https://www.legifrance.gouv.fr/codes/section_lc/LEGITEXT000044416551/LEGISCTA000044423741/ et https://www.legifrance.gouv.fr/codes/section_lc/LEGITEXT000044416551/LEGISCTA000044423747/ |
| Un trentième par jour de grève, même si la grève dure moins d'une journée ; assiette : traitement, indemnité de résidence, primes et indemnités (les primes annuelles ramenées à un équivalent mensuel d'après le montant de l'année précédente) ; supplément familial de traitement maintenu ; remboursements de frais non retenus ; retenue plafonnée à la quotité saisissable ; calculée sur la rémunération du mois de la grève ; part non versée non soumise à cotisations ; grève vendredi et lundi suivant = 4/30 | service-public.gouv.fr, fiche F499 « Droit de grève dans la fonction publique », vérifiée le 3 mars 2026 : https://www.service-public.gouv.fr/particuliers/vosdroits/F499 (elle cite la circulaire du 30 juillet 2003 relative aux retenues sur rémunération des agents de l'État en cas de grève) |
| Grève de plusieurs jours : « autant de trentièmes qu'il y a de journées comprises du premier jour inclus au dernier jour inclus », « même si, durant certaines de ces journées, cet agent n'avait, pour quelque cause que ce soit, aucun service à accomplir » | Conseil d'État, 7 juillet 1978, n° 03918, publié au recueil Lebon : https://www.legifrance.gouv.fr/ceta/id/CETATEXT000007654174/ |
| Un jour de récupération compte dans les trentièmes ; un congé annuel autorisé avant le dépôt du préavis n'est pas retenu | Conseil d'État, 4 décembre 2013, n° 351229 : https://www.legifrance.gouv.fr/ceta/id/CETATEXT000028275591/ |

La loi n° 82-889 du 19 octobre 1982 a voulu rendre la retenue proportionnelle dans la FPE ; ses articles 1er, 2, 5 et 6 ont été abrogés par l'article 89 de la loi n° 87-588 du 30 juillet 1987 (Légifrance : https://www.legifrance.gouv.fr/loda/id/JORFTEXT000000880394/), qui a rétabli le trentième indivisible pour l'État. Aujourd'hui, la règle de l'État est celle de L711-3.

**Calcul retenu** : retenue = rémunération × (jours de grève + jours sans service compris entre deux jours de grève) ÷ 30, au plus 30 trentièmes (la rémunération du mois). Chaque jour de grève compte pour un trentième, qu'il dure une heure, une demi-journée ou la journée.

### 2.3 Fonctions publiques territoriale (FPT) et hospitalière (FPH)

| Règle | Source |
|---|---|
| « A défaut de dispositions législatives applicables à ces agents précisant le régime de cette retenue, son montant doit être proportionné à la durée de la grève », en comparaison avec les obligations de service ; pour des gardes de 24 heures, nombre de gardes non faites rapporté au nombre moyen de gardes par mois | Conseil d'État, Section, 17 juillet 2009, n° 303588, publié au recueil Lebon (sapeurs-pompiers d'un SDIS) : https://www.legifrance.gouv.fr/ceta/id/CETATEXT000020871105/ |
| FPT et FPH : « L'absence de service fait donne lieu à une retenue proportionnelle à la durée de la grève » ; 1/30e pour une journée, 1/60e pour une demi-journée, 1/151,67e par heure ; gardes de 24 heures : gardes non faites rapportées au nombre moyen de gardes par mois ; même assiette, même maintien du supplément familial, même plafond (quotité saisissable) et même décompte des jours consécutifs que dans la FPE (vendredi et lundi suivant = 4/30) | service-public.gouv.fr, fiche F499, vérifiée le 3 mars 2026 : https://www.service-public.gouv.fr/particuliers/vosdroits/F499 |
| La retenue proportionnelle ne doit pas être plus sévère que la règle du trentième indivisible : le trentième est le maximum pour une journée normale de travail | Réponse du ministère de la fonction publique à la question écrite n° 05471 de M. Jean-Pierre Demerliat, JO Sénat du 7 janvier 1999, page 38 : https://www.senat.fr/questions/base/1998/qSEQ980105471.html |

**Calcul retenu** : par jour de grève, 1/30 (journée), 1/60 (demi-journée) ou heures ÷ 151,67, ce dernier plafonné à 1/30 pour la journée (réponse ministérielle de 1999) ; plus 1/30 par jour sans service compris entre deux jours de grève (fiche F499) ; au total, au plus la rémunération du mois. Le plafond horaire est signalé au chef d'équipe (section 9, question 2).

### 2.4 Contractuels

- « Agent public » désigne le fonctionnaire et l'agent contractuel de droit public (CGFP, article L7 : https://www.legifrance.gouv.fr/codes/section_lc/LEGITEXT000044416551/LEGISCTA000044420579/). L711-3 vise les agents publics de l'État : **les contractuels de l'État suivent le trentième indivisible**.
- FPT et FPH : la règle proportionnelle vient de l'absence de texte (Conseil d'État, 2009, ci-dessus) et du service fait, qui vaut pour tout agent public (L711-2). La décision de 2009 porte sur des fonctionnaires : l'application aux contractuels de droit public de ces deux fonctions publiques suit le même raisonnement, **à faire confirmer par l'avocat**. Le simulateur leur applique la règle de leur fonction publique, comme le demande la spécification.
- Les salariés de droit privé employés par une administration (contrats de droit privé) ne sont pas couverts : à confirmer, hors simulateur.

### 2.5 Préavis

| Règle | Source |
|---|---|
| Champ : personnels de l'État, des régions, des départements, des communes de plus de 10 000 habitants, et personnels des entreprises et organismes publics ou privés chargés de la gestion d'un service public | Code du travail, article L2512-1 (en vigueur depuis le 1er mai 2008) |
| Préavis par une organisation syndicale représentative (au niveau national, dans la catégorie professionnelle, l'entreprise, l'organisme ou le service), qui précise les motifs, le champ géographique, l'heure de début et la durée, limitée ou non ; il doit parvenir « cinq jours francs avant le déclenchement de la grève » à l'autorité hiérarchique ou à la direction ; obligation de négocier pendant le préavis | Code du travail, article L2512-2 (en vigueur depuis le 1er mai 2008) |
| Heures de cessation et de reprise identiques pour tous ; grèves tournantes interdites | Code du travail, article L2512-3 (en vigueur depuis le 1er mai 2008) |
| Sources des trois lignes ci-dessus | https://www.legifrance.gouv.fr/codes/section_lc/LEGITEXT000006072050/LEGISCTA000006177989/ |
| L2512-2 à L2512-4 s'appliquent aux agents de l'État et de ses établissements publics, des collectivités territoriales autres que les communes de 10 000 habitants ou moins, et des établissements de la FPH (article L5) | CGFP, article L114-2 (en vigueur depuis le 1er mars 2022) : https://www.legifrance.gouv.fr/codes/section_lc/LEGITEXT000044416551/LEGISCTA000044420629/ |
| Écoles maternelles et élémentaires publiques : négociation préalable obligatoire avant le préavis (huit jours francs) | Code de l'éducation, article L133-2, présenté par la fiche F499 (texte de L133-2 non relu sur Légifrance : la règle est seulement rappelée, sans délai, dans l'interface) |
| Secteur privé hors services publics : aucun préavis | Fiche F117 (2.1) |

### 2.6 Déclaration individuelle avant de faire grève

| Secteur | Règle | Source |
|---|---|---|
| Transports terrestres réguliers de voyageurs (services publics de transport terrestre régulier de personnes et services librement organisés de transport ferroviaire de voyageurs, sauf international : article L1324-1) | Les salariés des catégories désignées par l'accord collectif ou le plan de prévisibilité informent l'employeur « au plus tard quarante-huit heures avant de participer à la grève » ; renoncer ou reprendre : prévenir 24 heures avant ; sanction disciplinaire possible (L1324-8) | Code des transports, articles L1324-1 (en vigueur depuis le 5 juin 2019 : https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000038582777), L1324-7 (en vigueur depuis le 21 mars 2012 : https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000025542901) et L1324-8 |
| Transport aérien de passagers (exploitants d'aérodrome et entreprises de transport aérien : personnel navigant, assistance en escale, maintenance en ligne, sûreté aéroportuaire, secours et lutte contre l'incendie, lutte contre le péril animalier) | Les salariés « dont l'absence est de nature à affecter directement la réalisation des vols » informent l'employeur au plus tard 48 heures avant ; renoncer ou reprendre : 24 heures avant | Code des transports, article L1114-3 (en vigueur depuis le 21 mars 2012) : https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000025541651 ; section L1114-3 à L1114-6 : https://www.legifrance.gouv.fr/codes/id/LEGISCTA000025541649 |
| Écoles maternelles et élémentaires publiques | Quand un préavis a été déposé, « toute personne exerçant des fonctions d'enseignement » déclare son intention à l'autorité administrative « au moins quarante-huit heures, comprenant au moins un jour ouvré » avant | Code de l'éducation, article L133-4 (en vigueur depuis le 1er septembre 2008) : https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000019346685 |
| Services territoriaux de la continuité du service public : collecte et traitement des déchets ménagers, transport public de personnes, aide aux personnes âgées et handicapées, accueil des enfants de moins de trois ans, accueil périscolaire, restauration collective et scolaire | Quand un préavis a été déposé, les agents de ces services informent l'autorité « au plus tard quarante-huit heures avant » de participer, en comptant au moins un jour ouvré ; renoncer ou reprendre : 24 heures avant ; l'autorité peut exiger de faire grève dès la prise de service ; sanction disciplinaire possible (L114-10) | CGFP, articles L114-7 à L114-10 (en vigueur depuis le 1er mars 2022) : https://www.legifrance.gouv.fr/codes/section_lc/LEGITEXT000044416551/LEGISCTA000044420629/ |
| Navigation aérienne de l'État (contrôle, information de vol, alerte) | Quand un préavis a été déposé, les agents dont l'absence affecterait directement les vols déclarent leur intention « au plus tard à midi l'avant-veille de chaque journée de grève » ; renoncer : au plus tard à 18 heures l'avant-veille | CGFP, article L114-5-1 (créé par la loi n° 2023-1289 du 28 décembre 2023, en vigueur depuis le 30 décembre 2023), même adresse |

Hors de ces secteurs, aucune déclaration individuelle n'est demandée ; le simulateur ne l'affirme pas pour autant en termes généraux : il dit seulement qu'il ne connaît pas d'autre obligation pour le secteur choisi et renvoie au syndicat et au service du personnel.

### 2.7 À savoir aussi (seulement ce qui est sourcé)

- Privé : pas de mention de la grève sur le bulletin de paie (F117, R3243-4) ; aucune discrimination (L2511-1) ; effet sur les primes (Cour de cassation, 26 mars 2014) ; accessoires du salaire réduits aussi (F117).
- Fonction publique : supplément familial maintenu, remboursements de frais non retenus, part non versée non soumise à cotisations, retenue plafonnée à la quotité saisissable, calculée sur la rémunération du mois de la grève, jours de grève non rémunérés non pris en compte pour la retraite (F499) ; FPE : congé annuel autorisé avant le préavis non retenu (Conseil d'État, 2013) ; FPT et FPH : gardes de 24 heures calculées autrement (F499, Conseil d'État 2009), non simulées.
- Services publics : grèves tournantes interdites (L2512-3).

### 2.8 Laissé hors du simulateur, « à confirmer »

| Point | Pourquoi |
|---|---|
| Retenue des salariés d'une entreprise privée chargée d'un service public, hors transports terrestres (code du travail, L2512-5) | L2512-5 renvoie aux durées de l'article 2 de la loi n° 82-889 (1/160e jusqu'à une heure, 1/50e jusqu'à une demi-journée, 1/30e jusqu'à une journée : https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000006366450/1982-10-20/), abrogé le 31 juillet 1987 ; la Cour de cassation l'applique pourtant encore (1er décembre 2010, n° 09-67.952, RATP ; 8 juillet 2020, n° 19-13.767) et la durée d'une « demi-journée » n'est pas définie. Le simulateur affiche le préavis et les sources, **sans montant**, et le dit. |
| Forfait en jours | Dépend de l'accord collectif (Cour de cassation, 2008) : le simulateur le signale, sans calcul. |
| Gardes de 24 heures (FPT, FPH) | Calcul sur le nombre moyen de gardes du mois : signalé, non simulé. |
| Jour férié compris dans une grève (privé) | Aucune source officielle relue : non traité. |
| Effets sur l'assurance maladie, le chômage, les congés payés, l'ancienneté | Aucune source officielle relue : non traités. |
| Quotité saisissable (FPE, FPT, FPH) | Dépend du barème des saisies et de la situation de la personne : rappelée, non calculée. |
| Négociation préalable des écoles (L133-2) | Rappelée sans délai, texte à relire sur Légifrance. |
| Avis du Comité européen des droits sociaux sur le trentième indivisible | Hors droit applicable par l'employeur : non traité. |

## 3. Exemples calculés à la main (repris par `StrikeSimulatorTest`)

Montants arrondis au centime le plus proche, une seule fois, à la fin du calcul (un demi-centime est arrondi au centime supérieur).

| N° | Cas | Calcul | Résultat |
|---|---|---|---|
| 1 | FPE, 2 400,00 €, grève le vendredi (1 h) et le lundi suivant (journée), samedi et dimanche entre les deux | (2 + 2) trentièmes : 2 400 × 4 ÷ 30 | **320,00 €** (13,33 %) |
| 2 | FPE, 1 950,00 €, une heure de grève | 1 950 ÷ 30 | **65,00 €** |
| 3 | FPT, 2 100,00 €, une journée, une demi-journée, 2 heures un autre jour | 2 100 ÷ 30 + 2 100 ÷ 60 + 2 100 × 2 ÷ 151,67 = 70 + 35 + 27,6917 | **132,69 €** |
| 4 | FPT, 2 100,00 €, 7 heures le même jour | 7 ÷ 151,67 = 0,0462 > 1/30 : plafonné à 2 100 ÷ 30 | **70,00 €** |
| 5 | FPH, 2 300,00 €, deux journées qui se suivent | 2 300 × 2 ÷ 30 = 153,333 | **153,33 €** |
| 6 | Privé, 2 000,00 €, 35 h par semaine (151,67 h), une journée de 7 h | 2 000 × 7 × 12 ÷ (35 × 52) = 168 000 ÷ 1 820 = 92,3077 | **92,31 €** |
| 7 | Privé, même salaire, une journée de 7 h et 2 heures un autre jour | 2 000 × 9 × 12 ÷ 1 820 = 118,6813 | **118,68 €** |
| 8 | Privé, 2 500,00 €, 39 h par semaine (169 h), une journée de 7,8 h | 2 500 × 7,8 ÷ 169 = 115,3846 | **115,38 €** |
| 9 | Privé, 2 000,00 €, 35 h, 23 journées de 7 h (161 h) | 161 h > 151,67 h : plafonné à la rémunération du mois | **2 000,00 €** |
| 10 | FPE, 1 800,00 €, 25 jours de grève et 6 jours sans service | 31 trentièmes, plafonnés à 30 | **1 800,00 €** |
| 11 | FPE, 2 000,25 €, un jour | 200 025 centimes ÷ 30 = 6 667,5 : arrondi au centime supérieur | **66,68 €** |
| 12 | Privé, entreprise chargée d'un service public hors transports terrestres | règle de L2512-5 à confirmer | **pas de montant**, préavis de 5 jours francs affiché |
| 13 | Privé, demi-journée, 2 000,00 €, 35 h, journée de 7 h | 3,5 h : 2 000 × 3,5 × 12 ÷ 1 820 = 46,1538 | **46,15 €** |

Autres vérifications des tests : FPT, 2 100,00 € et 5 heures = 69,23 € (sous le trentième, non plafonné) ; FPH, 1 516,70 € et 1 heure = 10,00 € (1 516,70 ÷ 151,67), 1 h 30 = 15,00 € ; FPT, 3 000,00 €, deux journées et deux jours sans service = 400,00 € (4/30) ; le même 2 heures de grève sur 3 000,00 € coûte 100,00 € dans l'État et 39,56 € dans le privé, la territoriale et l'hospitalière ; privé, deux journées avec deux jours sans service entre elles = 184,62 € (les jours sans travail prévu ne comptent pas). Écran (`StrikeSimulatorScreenTest`) : 2 400,00 € donne 110,77 € (privé, une journée), 80,00 € (État), 31,65 € pour 2 heures (territoriale et privé) ; 2 471,93 € donne 114,09 €.

## 4. Conception

### 4.1 Cœur (`shared/core/.../core/agir/strike/`)

- `StrikeRules.kt` : le vocabulaire. `StrikeStatus` (privé, État, territoriale, hospitalière), `StrikeSector` (avec les statuts auxquels chaque secteur s'applique), `StrikeDay` (journée, demi-journée, heures en minutes), `StrikeRule`, `StrikeObligation` et `StrikeNote`, chacun avec ses `StrikeSource` ; `StrikeSource` porte l'adresse officielle (Légifrance, service-public.gouv.fr, senat.fr), le libellé est dans les textes de l'interface. `STRIKE_RULES_VERIFIED_ON` = 9 novembre 2026.
- `StrikeSimulator.kt` : le calculateur pur. Entrée `StrikeSimulation` (statut, secteur, rémunération en centimes, jours, jours sans service, heures par semaine et par jour) ; sortie `StrikeEstimate` (règle, retenue en centimes ou rien si non simulée, part en centièmes de pour cent, détail par jour, trentièmes comptés, heures du mois, plafond atteint, obligations, rappels, sources). Calcul exact en fractions d'entiers (`Fraction`, réduite à chaque étape), arrondi **une seule fois** au centime le plus proche (demi-centime vers le haut). Un secteur qui ne correspond pas au statut est lu comme « autre secteur ». `ruleFor`, `obligationsFor`, `notesFor` et `sourcesFor` servent aussi avant toute saisie de montant.
- `StrikeNumbers.kt` : lecture des montants (« 2 400,50 », « 2400.5 € » ; deux décimales au plus ; 1 000 000 € au plus) et des heures (« 7 », « 7,5 », « 7h30 », « 7 h 30 » ; 24 heures au plus par jour, 60 heures au plus par semaine) ; écriture à la française avec espaces insécables (« 2 400,00 € », « 13,33 % », « 7 h 30 », « 151,67 »).

### 4.2 Interface (`shared/ui/.../ui/agir/strike/`)

- `StrikeSimulatorState.kt` : ce que la personne tape, **en mémoire seulement** (`mutableStateOf` et `mutableStateListOf`, jamais `rememberSaveable`, que le système peut écrire sur le disque). `rememberStrikeSimulatorState(dependencies)` le garde le temps de l'affichage d'« Agir » et l'inscrit à l'effacement rapide (partie `agir-strike-simulator`, qui remet tout à zéro). Une journée entière au départ, statut « Salarié d'une entreprise », 35 heures par semaine et 7 heures par jour.
- `StrikeSimulatorScreen.kt` : une colonne sur téléphone et tablette (questions puis estimation), deux colonnes côte à côte sur grand écran (840 dp et plus). Questions : statut et secteur en boutons radio (`PpfRadioRow`, groupes `selectableGroup`), rémunération et heures en champs étiquetés (le libellé reste visible au-dessus et sert de nom au lecteur d'écran), jours en puces (journée, demi-journée, heures), bouton « Ajouter un jour de grève », boutons « moins » et « plus » pour les jours sans service (zone tactile de 48 dp). Réponses : mention « Estimation, pas un conseil juridique ni un bulletin de paie » toujours visible, montant annoncé poliment au lecteur d'écran quand il change (`liveRegion`), part, détail, règle avec ses sources et la ligne « Rédigé par l'IA, règles vérifiées le 9 novembre 2026 », « Avant de faire grève » (préavis, déclaration), « À savoir aussi », « Caisses de grève proches », « Sources officielles » (chaque source s'ouvre après « Vous quittez PPF »). Titres de section en `heading()`, erreurs de saisie écrites en toutes lettres (pas seulement en couleur), composants communs seulement, couleurs de `PpfTheme`, icônes de `Lucide.kt` existantes (aucune icône ajoutée).
- `StrikeLabels.kt` : libellés des statuts, secteurs, règles, obligations, rappels et sources ; textes dans `composeResources/values/strings_greve.xml` (français, vouvoiement, apostrophe typographique, sans tiret cadratin).

### 4.3 Entrée et retour

- Entrée la moins intrusive : un encadré « Simulateur du gréviste » **à la fin** de l'onglet « Caisses » de la rubrique Solidarité (`SolidarityScreen`, paramètre facultatif `onOpenStrikeSimulator` : sans lui, l'écran est inchangé). L'accueil d'« Agir » n'a pas changé (pas de nouvelle carte).
- `AgirRoute.STRIKE_SIMULATOR` (« simulateur-greve ») : le retour de l'écran et le retour du système ramènent à la rubrique Solidarité.
- « Caisses de grève proches » réutilise l'annuaire de M35 (`SolidarityLibrary.funds`, chargé une fois par `ensureStarted`) avec le même filtre que la rubrique : `FundQuery(types = {greve}, near = territoire tapé)` (caisses ouvertes seulement, celles du territoire avant celles qui servent partout en France). Le territoire du simulateur ne touche pas à la recherche de la rubrique ; il ne la rejoint que si la personne touche « Voir toutes les caisses de grève » (la rubrique s'ouvre alors sur les caisses de grève de ce territoire). Toucher une caisse ouvre sa page dans la rubrique. Les caisses sont des [EXEMPLE] pour l'instant, avec leur badge.

## 5. Ce qui reste sur l'appareil, ce qui part au serveur

- **Rien n'est enregistré** : ni base de données, ni préférence, ni fichier, ni journal, ni `rememberSaveable`. Quitter « Agir » oublie tout (test `thePayStaysInMemoryAndIsNeverSentNorStored`).
- **Rien n'est envoyé** : le simulateur ne fait aucune requête ; l'annuaire des caisses est celui que la rubrique Solidarité a déjà (ses deux requêtes générales, les mêmes pour tout le monde). Le test vérifie qu'aucune requête ne contient le montant ni le territoire tapés.
- Ouvrir une source mène hors de PPF, après la mise en garde : le site officiel reçoit alors l'adresse IP de la personne, comme pour tout lien extérieur.
- `docs/REGISTRE_DONNEES.md` : ajout du 9 novembre 2026.

## 6. Limites (dites honnêtement)

- **Rien n'est compilé ni lancé** (section 11).
- Une estimation : l'employeur peut arrondir autrement (par ligne de paie), prendre les heures réelles du mois plutôt que 52/12 de la semaine, prélever la retenue plus tard (fiche F499) ; le résultat est un montant brut.
- Un mois à la fois : une grève sur deux mois se simule mois par mois (le simulateur le dit au-delà de 31 jours).
- Non simulés : entreprises privées chargées d'un service public hors transports (L2512-5), forfait en jours, gardes de 24 heures, temps partiel (la rémunération tapée doit être celle du temps partiel), primes annuelles et treizième mois du privé, quotité saisissable, jours fériés compris dans une grève dans le privé, effets sociaux (maladie, chômage, congés, ancienneté) faute de source officielle relue.
- Les salariés de droit privé d'une administration ne sont pas couverts.
- Les caisses de grève sont des exemples fictifs tant que l'équipe n'a pas publié de vraies caisses.

## 7. Fichiers

Créés :
- `shared/core/src/commonMain/kotlin/org/politisationpopulairefrancaise/core/agir/strike/StrikeRules.kt`, `StrikeSimulator.kt`, `StrikeNumbers.kt`
- `shared/core/src/commonTest/kotlin/org/politisationpopulairefrancaise/core/agir/strike/StrikeSimulatorTest.kt`, `StrikeNumbersTest.kt`
- `shared/ui/src/commonMain/kotlin/org/politisationpopulairefrancaise/ui/agir/strike/StrikeSimulatorState.kt`, `StrikeSimulatorScreen.kt`, `StrikeLabels.kt`
- `shared/ui/src/commonMain/composeResources/values/strings_greve.xml`
- `shared/ui/src/jvmTest/kotlin/org/politisationpopulairefrancaise/ui/agir/strike/StrikeSimulatorScreenTest.kt` (avec son harnais `StrikeHarness`)
- cette note.

Modifiés (tous du pôle Agir, aucun fichier d'un autre pôle) :
- `shared/ui/src/commonMain/kotlin/org/politisationpopulairefrancaise/ui/agir/AgirScreen.kt` : route `STRIKE_SIMULATOR`, paramètre facultatif `strikeSimulator`, état créé par `rememberStrikeSimulatorState`, branche du retour du système, branche de l'écran, `onOpenStrikeSimulator` passé à la rubrique Solidarité.
- `shared/ui/src/commonMain/kotlin/org/politisationpopulairefrancaise/ui/agir/solidarity/SolidarityScreen.kt` : paramètre facultatif `onOpenStrikeSimulator`, encadré à la fin de l'onglet « Caisses », étiquettes `STRIKE_SIMULATOR` et `OPEN_STRIKE_SIMULATOR`.
- Documents communs : `docs/REGISTRE_DONNEES.md`, `docs/GUIDE_PROPRIETAIRE.md`, `docs/CHANGELOG.md`, `docs/ROADMAP.md`.

Non touchés : `content/` (la fiche `faire-greve-vos-droits.md` est réécrite par un autre agent ; les textes du simulateur suivent la loi, pas la fiche), `docs/DECISIONS.md`, le serveur, les migrations.

## 8. Décision D-191

**Simulateur du gréviste (AG-60), règles et conception.** Contexte : la spécification du cahier fonctionnel (AG-60) demande la retenue selon le statut, le préavis et la déclaration selon le secteur, et les caisses de grève proches, avec des règles vérifiées sur Légifrance et relues. Options étudiées pour l'entrée : (A) une nouvelle carte sur l'accueil d'« Agir » ; (B) un encadré à la fin des caisses de la rubrique Solidarité ; (C) un quatrième onglet de la rubrique. Choix : **B** (l'accueil et les onglets restent tels quels, l'encadré est à côté des caisses de grève, dont il réutilise l'annuaire). Règles retenues : section 2 de cette note (privé : 52/12 de la durée hebdomadaire ; État : trentième indivisible et jours sans service compris ; territoriale et hospitalière : 1/30, 1/60, 1/151,67, au plus un trentième par jour). Points laissés hors du simulateur, à confirmer : section 2.8. Données : tout en mémoire, rien d'enregistré ni d'envoyé, partie `agir-strike-simulator` de l'effacement rapide. Raison : livrer un outil utile pendant le mouvement sans jamais garder ni transmettre le salaire, et ne rien affirmer qu'une source officielle ne dit pas.

## 9. Questions pour le chef d'équipe (avec recommandations)

1. **Entreprises privées chargées d'un service public hors transports** (L2512-5, article 2 abrogé de la loi n° 82-889, mais appliqué par la Cour de cassation en 2010 et 2020) : faut-il coder les tranches 1/160, 1/50 et 1/30 ? **Recommandation** : non avant l'avis de l'avocat (la « demi-journée » n'est pas définie) ; garder « pas de montant, préavis affiché ».
2. **Plafond d'un trentième par jour pour les heures** (territoriale, hospitalière) : la fiche F499 donne 1/151,67 par heure sans plafond, la réponse ministérielle de 1999 dit que la retenue proportionnelle ne doit pas être plus sévère que le trentième. **Recommandation** : garder le plafond (il évite qu'une grève de 6 heures coûte plus qu'une journée entière) et le faire valider par l'avocat.
3. **Contractuels de la territoriale et de l'hospitalière** : même règle que les fonctionnaires, par le même raisonnement (absence de texte). **Recommandation** : garder, à faire confirmer par l'avocat (liste de relecture de `ROADMAP.md`).
4. **Entrée sur l'accueil d'« Agir »** : ajouter aussi une carte « Simulateur du gréviste » ? **Recommandation** : pas tout de suite ; mesurer d'abord dans la phase de test si l'encadré des caisses suffit, puis ajouter la carte si la propriétaire le souhaite (pendant le mouvement, la carte serait plus visible).
5. **Lien avec la fiche « Faire grève : vos droits »** (réécrite par un autre agent) : ajouter un lien croisé dans les deux sens une fois la fiche publiée. **Recommandation** : oui, à la fusion, en vérifiant que la fiche et le simulateur disent la même chose (le simulateur suit la loi, section 2).
6. **Date de revue** : les règles bougent (loi de finances, jurisprudence). **Recommandation** : revue des sources tous les six mois et à chaque loi de finances, inscrite dans « À vérifier avant publication ».

## 10. Comment tester

Reporté dans `docs/GUIDE_PROPRIETAIRE.md` (« Tester le simulateur du gréviste », 9 novembre 2026), une fois compilé. Tests automatiques : `gradlew :shared:core:jvmTest --tests "*StrikeSimulatorTest*" --tests "*StrikeNumbersTest*"` et `gradlew :shared:ui:jvmTest --tests "*StrikeSimulatorScreenTest*"`, puis les tests de la rubrique Solidarité (`SolidarityScreenTest`) et d'« Agir » (`AgirScreenTest`), qui doivent rester verts.

## 11. À faire au moment de compiler

- Compiler `:shared:core` et `:shared:ui` (Android, ordinateur et klib iOS), lancer les tests de la section 10, puis le build complet selon la règle de l'équipe.
- Vérifier que `Res.plurals.greve_between_value`, `greve_detail_between` et `greve_detail_thirtieths` sont bien générés (pluriels français « one » et « other »).
- Vérifier la résolution de `FundQuery(...).apply(funds)` (fonction membre, pas la fonction de portée `apply` de Kotlin) et le rendu des espaces insécables (`StrikeNumbers.NBSP`) dans les montants.
- Si le test de mise en page (`theSimulatorWorksOnAPhoneATabletAndADesktopInLightAndDark`) échoue sur les bornes des colonnes coupées par le défilement, comparer plutôt les positions horizontales (grand écran) et l'ordre des nœuds (téléphone).
- `tools/check-apk.sh` : rien de nouveau à exiger (aucun fichier embarqué ajouté).

## 12. API lues pour écrire sans compiler

- `SolidarityLibrary` (`ui/agir/solidarity/SolidarityLibrary.kt`) : `funds`, `status`, `ensureStarted()`, `selectTab`, `openFund`, `clearFundTypes`, `toggleFundType`, `setNear`, `fundQuery`.
- `FundQuery` (`core/agir/solidarity/SolidarityQuery.kt`) : `FundQuery(text, types, territory, near, includeClosed)` et `fun apply(funds: List<SolidarityFund>): List<SolidarityFund>` ; `SolidarityFormats.FUND_STRIKE = "greve"`.
- `SolidarityFund` : `slug`, `displayName`, `example`, `isNational`, `territory`, `summary`.
- Composants (`ui/components`) : `PpfCard(modifier, onClick, selected, contentPadding, content)`, `InfoBox(icon, modifier, content)`, `PpfRadioRow(label, selected, onClick, modifier, supporting, enabled)`, `PpfSelectableChip(label, selected, onClick, modifier, enabled, leadingIcon)`, `PpfTextField(value, onValueChange, placeholder, modifier, minLines, singleLine, keyboardOptions, keyboardActions)` (son `contentDescription` est le `placeholder`), `PpfIconButton(icon, contentDescription, onClick, modifier, enabled, tint, badge)`, `PpfSecondaryButton(onClick, text, modifier, enabled, small, icon)`, `PageHeader(title, description)`, `ExampleBadge()`, `LeavePpfDialog(url, title, text, stayLabel, openLabel, onStay, onDone, tags, note)`, `leavingHost(url)`.
- `PrivateLine(text, modifier)` (interne au module, `SolidarityScreen.kt`).
- `deviceErasable(id) { … }` et `AppDependencies.erasables.register(…)` (`core/device/DeviceErasable.kt`).
- `CivilDate.toFrench()` (« 9 novembre 2026 »), `WindowClass.width` et `WindowWidthClass` (840 dp pour `EXPANDED`).
- Tests : `AgirFixtures.dependencies(remote)`, `DirectoryServer` (`tagRequests`, `fetches`), `RecordingUriHandler`, `awaitTag`, `count`, `showInList`, `SolidarityExamples`, `SolidarityPageTags.FUND_PAGE` et `FUND_NAME`.
