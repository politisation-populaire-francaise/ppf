# Délibération citoyenne (DE-25) : conception

- Pôle « Décider », 7 novembre 2026. À valider par le chef d'équipe (questions de la section 8) avant tout code.
- Sources : cahier fonctionnel 2.0, M13.5 (DE-25, DE-08, DE-04) ; décision de la propriétaire D-062 (« c'est le peuple qui doit la modifier et la valider via notre appli ») ; conception du vote, section 11 (`docs/integration/une-personne-une-voix.md`) ; contrat `core/urne` (`docs/integration/urne-contrat.md`).
- Premier usage : les mesures du Pacte démocratique pour la présidentielle de 2027, ouvertes au plus tard en janvier 2027 ; puis les modalités des campagnes. Le même module servira au Programme du peuple (DE-03), aux cahiers de doléances (DE-02) et aux votations (M13.6).

## 1. Ce que la délibération doit garantir

1. Ce que PPF propose n'est qu'un point de départ : affiché « proposition de départ, pas encore votée » jusqu'au vote final.
2. Tout citoyen avec un compte peut proposer une mesure ou un amendement, sous pseudonyme ; la relecture humaine vérifie la charte et la faisabilité juridique, jamais les préférences de l'équipe (modalité de départ des campagnes).
3. Chaque proposition, chaque amendement et chaque mesure du vote final a son urne : une personne, une voix, sans que personne puisse relier un vote à un compte (D-110, D-112).
4. Le résultat se calcule par une règle publiée à l'avance, et chacun peut le recompter (clés et bulletins publiés à la clôture).
5. Le déroulé est daté et identique pour tous : rien ne change en cours de phase.

## 2. Les objets (événements signés, comme le reste)

| Objet | Format | Qui le publie |
|---|---|---|
| **Délibération** | Type 30023, `ppf-type` = `deliberation` : titre, objet, phases datées (`ppf-phase` : `propositions`, `amendements`, `vote`, chacune avec début et fin), règle du résultat, nombre de mesures retenues (par exemple de 5 à 10) | L'équipe |
| **Proposition** | Type 30023, `ppf-type` = `proposition`, `a` vers la délibération ; texte court et vérifiable (texte à modifier, délai), argumentaire | Un citoyen, après relecture ; ou l'équipe pour la proposition de départ (`ppf-origin` = `depart`) |
| **Amendement** | Type 30023, `ppf-type` = `amendement`, `a` vers la proposition ; **nouvelle rédaction complète** de la proposition (jamais un morceau de texte à recoller), avec sa justification | Un citoyen, après relecture |
| **Texte soumis au vote final** | Type 30023, `ppf-type` = `proposition-finale`, `a` vers la proposition d'origine et vers l'amendement retenu, empreinte SHA-256 du texte (horodatée, D-111) | L'équipe, calculé mécaniquement (section 4) |

Les propositions et amendements passent par la même file de relecture que les revendications (vérifications automatiques, données personnelles refusées, doublons signalés, relecture d'une personne). Limites pour tenir la charge du vote (une urne = un jeton par compte, D-110) : une proposition et trois amendements par compte et par délibération ; au plus 60 propositions et 10 amendements par proposition retenus pour le vote (au-delà, les plus soutenus pendant la phase de propositions, section 5).

## 3. Les phases

| Phase | Durée de départ (modalité délibérable) | Ce qui se passe | Urnes |
|---|---|---|---|
| 1. Propositions | 3 semaines | On propose des mesures ; la proposition de départ de PPF est déjà là | Une urne « soutien » par proposition (un seul choix, `soutien`) : sert seulement à départager si les propositions dépassent la limite |
| 2. Amendements | 2 semaines | On propose une autre rédaction d'une proposition ; on vote sur chaque amendement | Une urne par amendement : `pour`, `contre`, `abstention` |
| 3. Vote final | 2 semaines | Chaque mesure, dans sa rédaction retenue, est soumise au vote | Une urne par mesure (méthode : section 8, Q1) |

Calendrier pour le Pacte : ouverture en décembre 2026 dès que le module est prêt, au plus tard début janvier 2027 ; sept semaines en tout ; envoi du Pacte aux candidats ensuite (même jour, même délai pour tous, DE-08). Les dates exactes seront fixées avec le calendrier officiel de l'élection, à vérifier auprès des sources officielles.

## 4. Du vote des amendements au texte final (mécanique, sans choix de l'équipe)

- Un amendement est **adopté** si la règle du résultat (section 8, Q2) donne une majorité de `pour` sur `pour` + `contre` (l'abstention compte dans la participation, pas dans la majorité).
- Si plusieurs amendements d'une même proposition sont adoptés, la rédaction retenue est celle de l'amendement adopté avec la **plus forte part de `pour`** ; en cas d'égalité, le plus de bulletins `pour`, puis le plus ancien.
- Sans amendement adopté, la rédaction d'origine est soumise au vote final.
- Le texte de chaque mesure finale est publié avec son empreinte, l'amendement d'où il vient et les résultats qui l'expliquent ; n'importe qui peut refaire le calcul.

## 5. Le résultat du vote final

- Chaque mesure obtient son résultat par la méthode choisie (Q1) et la règle de majorité choisie (Q2).
- Sont retenues, dans l'ordre de leur résultat, les mesures majoritaires, au plus le nombre fixé par la délibération (de 5 à 10 pour le Pacte) ; s'il y en a moins de 5, la délibération le dit et l'équipe ne complète pas la liste elle-même.
- La liste retenue devient le Pacte : même texte (empreinte), envoi, tableau public (D-060, D-062).

## 6. Côté technique (aperçu)

- Serveur : comme les revendications (`RevendicationUrnes`), une tâche ouvre et ferme les urnes selon les phases publiées : `decider:deliberation:soutien:<adresse>`, `decider:deliberation:amendement:<adresse>`, `decider:deliberation:final:<adresse>` ; `closesAt` = fin de la phase (connue à l'ouverture, sans clôture anticipée). Aucune donnée nouvelle sur les personnes : les propositions sont des contenus publics signés par un pseudonyme.
- Cœur commun : `core/decider/Deliberation.kt` (lecture des événements, phases, calcul du texte final et du résultat, mêmes fonctions sur l'appareil et sur le serveur, testées sur des exemples) ; formats dans `docs/FORMATS.md`.
- Interface : un écran « Délibération » dans Décider (déroulé et dates, propositions avec leur texte et leurs amendements, vote avec le composant commun, résultats par niveau), le formulaire de proposition et d'amendement sur le modèle de celui des revendications ; la mention « proposition de départ, pas encore votée » sur le Pacte tant que le vote final n'a pas eu lieu.
- Site sans JavaScript : lecture des propositions, des amendements et des résultats (tâche « Pacte et pétitions sur le site »).

## 7. Ce qui reste hors de cette première version

- Débat argumenté (arguments pour et contre) : bouton « Débat » des revendications, plus tard.
- Changer les modalités elles-mêmes (durées, méthode, limites) : une délibération « modalités », ouverte avec le même module après celle du Pacte.
- Fusion de propositions en doublon : signalée par la relecture, décidée par l'équipe avec une trace publique, pas automatique.

## 8. Questions à trancher (recommandation en premier)

- **Q1. Méthode du vote final.**
  - A (recommandée) : **pour, contre, abstention** par mesure, comme les amendements. Simple, la même partout, et le résultat se lit d'un coup d'œil ; classement par part de `pour`.
  - B : **jugement majoritaire** par mesure (six mentions, de « Excellent » à « À rejeter »), classement par mention majoritaire. Plus fin et moins sensible au vote stratégique, mais plus long à expliquer et à afficher.
  - C : **approbation** de 5 à 10 mesures dans un seul bulletin. Impossible à vérifier avec des urnes anonymes séparées : écartée sauf nouveau format de bulletin.
- **Q2. Règle de la majorité** (les comptes non vérifiés peuvent être créés en masse ; D-112 montre les vérifiés en premier).
  - A (recommandée) : **double majorité**, chez les comptes vérifiés et chez l'ensemble des citoyens, dès qu'il y a au moins 30 bulletins vérifiés sur l'urne ; en dessous, la majorité de l'ensemble des citoyens, avec la mention « participation vérifiée faible ».
  - B : la majorité de l'ensemble des citoyens seulement (vérifiés et non vérifiés). Sensible aux vagues de faux comptes.
  - C : la majorité des comptes vérifiés seulement. Plus sûre, mais exclut presque tout le monde tant que la validation en personne n'a pas commencé.
- **Q3. Durées de départ** : 3 semaines de propositions, 2 d'amendements, 2 de vote final (recommandé), ou un déroulé plus court (2, 1 et 1 semaines) pour tenir le calendrier de la présidentielle si l'ouverture glisse en janvier.
- **Q4. Seuil de participation** : aucun seuil pour la première délibération (recommandé : la participation est affichée à côté de chaque résultat), ou un minimum de bulletins par urne en dessous duquel une mesure n'est pas retenue.
