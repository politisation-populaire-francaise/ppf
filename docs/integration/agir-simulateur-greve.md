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
