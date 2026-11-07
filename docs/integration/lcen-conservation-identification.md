# Note de recherche juridique : conservation des données d'identification par la plateforme PPF

**Date :** 7 novembre 2026
**Objet :** obligation de conservation des données d'identification des hébergeurs (LCEN, article 6, V) appliquée à la plateforme PPF
**Statut :** brouillon de recherche, à inscrire sur la liste « à faire relire par un avocat ».

> **Avertissement.** Cette note n'est pas un avis juridique ni une consultation. Elle rassemble des textes officiels et des décisions de justice, avec une analyse qui doit être validée par un avocat inscrit au barreau avant toute décision. Les appréciations de risque sont des estimations de recherche, pas des certitudes.

**Contexte.** PPF est une application et un site civiques. Les utilisateurs y ont des comptes pseudonymes (paires de clés Nostr créées sur l'appareil, sans e-mail ni téléphone). Ils publient des messages courts, des revendications, des événements, des appels, des témoignages et des messages. Les messages privés chiffrés de bout en bout sont seulement relayés, et les votes sont anonymes. Règle actuelle : aucune adresse IP n'est jamais stockée (journaux du reverse proxy désactivés, adresse du client retirée avant l'application). Décision de la propriétaire du 7 novembre 2026 : « Conserver le minimum dans le cadre de la loi (on ne conserve rien du tout si le cadre legal rend ça possible) ».

**Méthode.** Tous les textes ont été lus le **7 novembre 2026** sur les sites officiels (Légifrance, EUR-Lex, conseil-etat.fr, conseil-constitutionnel.fr, cnil.fr, arcep.fr), dans leur version en vigueur à cette date. Les citations sont volontairement courtes (moins de 15 mots). Les numéros de points des décisions ont été relevés à la lecture et doivent être recoupés par l'avocat. La liste complète des sources, avec la date de vérification, figure en annexe A, et ce qui n'a pas pu être vérifié figure en annexe B.

---

## 1. Réponse courte

1. L'obligation figure aujourd'hui à l'**article 6, V, A de la LCEN** (ancien article 6, II, renuméroté par l'article 48 de la loi SREN n° 2024-449). Elle est précisée par le **décret n° 2021-1362** et sanctionnée par l'article 6, V, B : un an d'emprisonnement et 250 000 € d'amende (jusqu'à 1 250 000 € pour une personne morale).
2. PPF est très probablement un « fournisseur de services d'hébergement » pour ses **contenus publics** (messages, commentaires, événements, revendications, appels, profils, salons ouverts). Elle ne l'est pas pour la lecture ni pour les votes, et très probablement pas, au sens du décret, pour les messages privés chiffrés qu'elle relaie.
3. Le décret ne vise que les données **effectivement collectées** (article 8). Le Conseil d'État (30 juin 2023, n° 459724) et la CNIL (délibération n° 2021-115) l'ont confirmé : il n'y a pas d'obligation de collecter. Ne jamais collecter l'IP est donc une lecture solide, mais aucun juge pénal ne l'a encore tranchée.
4. PPF doit en revanche conserver ce qu'elle détient : la clé publique et les pseudonymes (un an après la clôture du compte). Tant que dure l'**injonction du décret n° 2026-920** (en vigueur depuis le 21 octobre 2026, pour un an), elle doit aussi garder, pour chaque création, modification ou suppression, l'identifiant du contenu, la nature de l'opération, la date et l'heure, et la clé de l'auteur.
5. **Recommandation :** conserver uniquement ces données pseudonymes, pendant un an, dans un stockage séparé, et tenir prête une conception « IP scellée sous clé hors ligne » au cas où l'avocat jugerait le risque pénal trop élevé.

---

## 2. Textes applicables

### 2.1 LCEN, article 6 (version en vigueur au 7 novembre 2026)

- **Référence :** loi n° 2004-575 du 21 juin 2004 pour la confiance dans l'économie numérique, article 6, version en vigueur depuis le 26 août 2026 (modifiée par la loi n° 2026-813 du 24 août 2026, article 2).
- **Historique utile :**
  - La loi n° 2026-813 (protection des mineurs sur les réseaux sociaux) n'a modifié que le IV, A (ajout d'une référence à l'article 223-14 du code pénal). Le **V n'a pas changé**.
  - La rédaction actuelle du V vient de l'**article 48 de la loi SREN** n° 2024-449 du 21 mai 2024. Légifrance affiche pour cette version « en vigueur du 17 février 2024 au 26 août 2026 ».
  - Avant la loi SREN, la même obligation figurait à l'**article 6, II** (version du 1er janvier 2023 au 12 mai 2024), et elle renvoyait aux « II bis, III et III bis » de l'article L. 34-1 du CPCE. Le décret n° 2021-1362 vise encore « le II de l'article 6 », c'est-à-dire l'ancienne numérotation.
- **I, 2 (définition) :** est fournisseur de services d'hébergement « toute personne fournissant les services définis au iii du même paragraphe g ». Le texte renvoie donc à la définition de l'hébergement dans le règlement (UE) 2022/2065 (DSA), article 3, g), iii). La mention « même à titre gratuit » de l'ancienne définition n'y figure plus.
- **V, A, premier alinéa :** dans les conditions fixées aux II bis à III bis de l'article L. 34-1 du CPCE, les fournisseurs d'accès et d'hébergement « détiennent et conservent les données de nature à permettre l'identification de quiconque » « a contribué à la création du contenu ou de l'un des contenus » des services dont ils sont prestataires.
- **V, A, second alinéa :** un décret en Conseil d'État, pris après avis de la CNIL, définit ces données et fixe la durée et les modalités de leur conservation.
- **V, B (sanctions) :**
  - tout manquement au A est puni d'« un an d'emprisonnement et de 250 000 euros d'amende » ;
  - même peine pour « Le fait de ne pas déférer à la demande d'une autorité judiciaire » portant sur ces données ;
  - les personnes morales peuvent être déclarées pénalement responsables (article 121-2 du code pénal), avec l'amende de l'article 131-38 et les peines des 2° et 9° de l'article 131-39 (dont l'interdiction d'exercer l'activité, cinq ans au plus).
- **VI :** les plateformes en ligne dont l'activité en France dépasse un seuil de connexions fixé par décret doivent conserver temporairement les contenus retirés pour l'autorité judiciaire. PPF est a priori sous ce seuil (seuil non vérifié ici).
- **Liens :** [article 6 (Légifrance)](https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000054744218) ; [version au 1er janvier 2025](https://www.legifrance.gouv.fr/loda/id/JORFTEXT000000801164/2025-01-01) ; [version au 1er juin 2023](https://www.legifrance.gouv.fr/loda/id/JORFTEXT000000801164/2023-06-01) ; [loi SREN](https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000049563368) ; [loi n° 2026-813](https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000054743001).
- **Vérifié le :** 7 novembre 2026.

### 2.2 LCEN, articles 1 et 1-1

- **Article 1, IV :** la « communication au public en ligne » est une transmission sur demande individuelle de données numériques « n'ayant pas un caractère de correspondance privée ». Cette notion compte, car le décret de 2021 ne vise que les données collectées pour des services de communication au public en ligne (voir 2.4, article 8). Lien : [article 1](https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000006421540).
- **Article 1-1, II :** un éditeur non professionnel peut rester anonyme envers le public s'il a donné ses éléments d'identification à son hébergeur. L'hébergeur est alors tenu au secret professionnel, mais « Ce secret professionnel n'est pas opposable à l'autorité judiciaire. » Ce régime vise les éditeurs d'un service (site, blog), pas a priori les auteurs de messages sur une plateforme. Lien : [article 1-1](https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000049568614).
- **Vérifié le :** 7 novembre 2026.

### 2.3 Code des postes et des communications électroniques (CPCE), article L. 34-1

- **Version :** en vigueur depuis le 15 juin 2025 (loi n° 2025-532 du 13 juin 2025, article 29, qui concerne la vérification d'identité des acheteurs de cartes SIM prépayées par les opérateurs, sans effet direct sur PPF).
- **II :** principe : les opérateurs « effacent ou rendent anonymes » les données relatives aux communications, sous réserve des exceptions qui suivent.
- **II bis :** obligation de conservation :
  - **1°** identité civile, cinq ans après la fin du contrat, pour les procédures pénales, la prévention des menaces contre la sécurité publique et la sécurité nationale ;
  - **2°** autres informations fournies à l'inscription et informations de paiement, un an après la fin du contrat ou la clôture du compte, pour les mêmes finalités ;
  - **3°** données techniques permettant d'identifier la source de la connexion, un an, pour la lutte contre la criminalité et la délinquance grave, la prévention des menaces graves contre la sécurité publique et la sécurité nationale.
- **III :** en cas de menace grave, actuelle ou prévisible contre la sécurité nationale, le Premier ministre peut enjoindre par décret de conserver un an certaines données de trafic et de localisation. Selon le texte, il s'agit d'une injonction « dont la durée d'application ne peut excéder un an », renouvelable.
- **III bis :** « injonction de conservation rapide » (gel des données déjà conservées) par les autorités disposant d'un accès légal, pour la criminalité et la délinquance grave.
- **VI :** les données conservées « ne peuvent en aucun cas porter sur le contenu des correspondances ».
- L'article L. 34-1 vise les opérateurs. Il s'applique aux hébergeurs **par le renvoi** de l'article 6, V, A de la LCEN (« dans les conditions fixées aux II bis à III bis »).
- **Lien :** [article L. 34-1](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000051741279). **Vérifié le :** 7 novembre 2026.

### 2.4 Décret n° 2021-1362 du 20 octobre 2021

- **Titre :** décret relatif à la conservation des données permettant d'identifier toute personne ayant contribué à la création d'un contenu mis en ligne, pris en application du II de l'article 6 de la LCEN.
- **Publication et entrée en vigueur :** JORF du 21 octobre 2021, en vigueur le 21 octobre 2021. Aucune modification de ses articles depuis (Légifrance, version au 7 novembre 2026). Il abroge le décret n° 2011-219 du 25 février 2011.
- **Contenu, pour les hébergeurs (« personnes mentionnées au 2 du I de l'article 6 ») :**
  - **article 2 :** identité civile (nom, prénom, date et lieu de naissance, adresses postales, adresses e-mail, numéros de téléphone), cinq ans après la fin de validité du contrat ;
  - **article 3 :** « L'identifiant utilisé », « Le ou les pseudonymes utilisés », données permettant de vérifier ou de modifier le mot de passe, un an après la fin du contrat ou la clôture du compte ;
  - **article 4 :** données de paiement (type, référence, montant, date, heure et lieu pour les paiements physiques), un an après la fin du contrat ou la clôture du compte ;
  - **article 5, 2° :** pour chaque opération de création de contenu, « L'identifiant de la connexion à l'origine de la communication » et les « types de protocoles utilisés pour la connexion au service », un an à compter du jour de l'opération ;
  - **article 6, 2° (seulement sur injonction du Premier ministre) :** pour chaque opération de création, « L'identifiant attribué par le système d'information au contenu », « La nature de l'opération », « Les date et heure de l'opération », « L'identifiant utilisé par l'auteur de l'opération lorsque celui-ci l'a fourni », un an ;
  - **article 7 :** la contribution à la création couvre « Des créations initiales de contenus », « Des modifications des contenus et de données liées aux contenus » et « Des suppressions de contenus » ;
  - **article 8 :** ces données « ne doivent être conservées que dans la mesure où elles sont collectées » par les hébergeurs « lorsqu'elles assurent la mise en œuvre des services de communication au public en ligne ».
- **Note :** pour les fournisseurs d'accès, l'article 5, 1° cite expressément « L'adresse IP attribuée à la source de la connexion et le port associé ». Pour les hébergeurs, le texte ne parle que de « l'identifiant de la connexion à l'origine de la communication » (voir la partie 4).
- **Liens :** [décret](https://www.legifrance.gouv.fr/loda/id/JORFTEXT000044228912) ; [art. 2](https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000044230081) ; [art. 3](https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000044230083) ; [art. 4](https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000044230085) ; [art. 5](https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000044230063) ; [art. 6](https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000044230065) ; [art. 7](https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000044230067) ; [art. 8](https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000044230069).
- **Vérifié le :** 7 novembre 2026.

### 2.5 Décrets d'injonction (sécurité nationale)

Chaque année depuis 2021, un décret enjoint aux opérateurs **et aux personnes mentionnées aux 1 et 2 du I de l'article 6 de la LCEN** (fournisseurs d'accès et hébergeurs) de conserver un an les données de trafic et de localisation, dont celles de l'**article 6 du décret n° 2021-1362**.

| Décret | Signé le | Publié au JORF | Lien |
|---|---|---|---|
| n° 2021-1363 | 20 octobre 2021 | n° 246, 21 octobre 2021 | [texte](https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000044228976) |
| n° 2022-1327 | 17 octobre 2022 | n° 242, 18 octobre 2022 | [texte](https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000046437495) |
| n° 2023-933 | 10 octobre 2023 | n° 236, 11 octobre 2023 | [texte](https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000048186276) |
| n° 2024-901 | 7 octobre 2024 | n° 240, 9 octobre 2024 | [texte](https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000050320931) |
| n° 2025-980 | 15 octobre 2025 | n° 244, 16 octobre 2025 | [texte](https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000052396496) |
| **n° 2026-920** | **30 septembre 2026** | **n° 231, 3 octobre 2026** | [texte](https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000054945173) |

- **Décret n° 2026-920 :** son article 1er vise les données du V de l'article R. 10-13 du CPCE et de l'article 6 du décret n° 2021-1362, conservées un an « aux fins de la sauvegarde de la sécurité nationale ». Il est entré en vigueur le **21 octobre 2026** (article 2). Sauf renouvellement, l'injonction court jusqu'au 20 octobre 2027 environ.
- Le contenu des décrets de 2021 et 2026 a été lu ; ceux de 2022 à 2025 n'ont été repérés que par leur titre et leur référence au JORF.
- **Vérifié le :** 7 novembre 2026.

### 2.6 Code pénal

- **Article 131-38 :** l'amende des personnes morales est « égale au quintuple de celui prévu pour les personnes physiques », soit 1 250 000 € ici. [Lien](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000006417334).
- **Article 111-4 :** « La loi pénale est d'interprétation stricte. » [Lien](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000006417178).
- **Article 111-5 :** le juge pénal peut apprécier la légalité d'un acte réglementaire (par exemple le décret) quand la solution du procès en dépend. [Lien](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000006417179).
- **Vérifié le :** 7 novembre 2026.

### 2.7 Accès aux données

- **Code de procédure pénale, article 60-1-2** (en vigueur depuis le 4 mars 2022, créé par la loi n° 2022-299). « A peine de nullité », les réquisitions portant sur les données techniques d'identification de la source de la connexion et sur les données de trafic et de localisation ne sont possibles que dans quatre cas :
  - crime ou délit puni d'au moins trois ans d'emprisonnement ;
  - délit puni d'au moins un an commis par un réseau de communications électroniques, si la réquisition vise seulement à identifier l'auteur ;
  - demande de la victime portant sur ses propres équipements ;
  - recherche d'une personne disparue.

  L'article ne restreint pas les réquisitions portant sur l'identité civile. Il sera abrogé au **1er janvier 2029** par l'ordonnance n° 2025-1091 du 19 novembre 2025 (refonte du code, à suivre). [Lien](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000045289397).
- **Code de la sécurité intérieure, article L. 851-1** (renseignement) : recueil possible auprès des opérateurs et auprès de « les personnes mentionnées aux 1 et 2 du I de l'article 6 » de la LCEN, sous le contrôle de la Commission nationale de contrôle des techniques de renseignement (CNCTR). [Lien](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000030935595).
- **Vérifié le :** 7 novembre 2026.

### 2.8 Droit de l'Union européenne

- **Règlement (UE) 2022/2065 (DSA) :**
  - article 3, a) : le service de la société de l'information est défini par renvoi à la directive (UE) 2015/1535 ;
  - article 3, g), iii) : l'hébergement consiste à stocker des informations fournies par un destinataire du service, à sa demande ;
  - article 6 : exemption de responsabilité de l'hébergeur sans connaissance effective ;
  - article 8 : « aucune obligation générale de surveiller les informations » ;
  - article 10 : injonctions de fournir des informations ; le fournisseur informe l'autorité et, au plus tard au moment où il agit (ou au moment fixé par l'autorité), l'utilisateur concerné ;
  - article 15 : rapport annuel de transparence avec le nombre d'injonctions reçues, dont les micro et petites entreprises sont dispensées (paragraphe 2) ;
  - considérant 14 : les messageries privées entre un « nombre fini de personnes » ne sont pas des plateformes en ligne, à la différence des groupes publics et canaux ouverts ;
  - considérant 29 : exemples d'hébergement, dont le stockage et le partage de fichiers.

  [Lien](https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:32022R2065).
- **Directive (UE) 2015/1535, article 1er, 1, b) :** est un service de la société de l'information « tout service presté normalement contre rémunération », à distance, par voie électronique et à la demande individuelle d'un destinataire. [Lien](https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:32015L1535).
- **Règlement (UE) 2016/679 (RGPD) :**
  - article 5, 1, c) : données « adéquates, pertinentes et limitées à ce qui est nécessaire » ;
  - article 5, 1, e) : limitation de la conservation ;
  - article 6, 1, c) : obligation légale ;
  - article 9 : données sensibles, dont les « opinions politiques », avec les exceptions du paragraphe 2, d), e), f) et g) ;
  - article 23 : limitations par le droit national ;
  - article 25 : protection des données dès la conception, par exemple par « la pseudonymisation » ;
  - article 32, 1, a) : « la pseudonymisation et le chiffrement des données à caractère personnel ».

  [Lien](https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:32016R0679).
- **Vérifié le :** 7 novembre 2026.

### 2.9 CPCE, article L. 32 (services de communications interpersonnelles)

- **Version :** en vigueur depuis le 23 mai 2024 (loi SREN, article 34).
- **6° :** services de communications électroniques, dont les services de communications interpersonnelles.
- **6° bis :** est un service de communications interpersonnelles un service d'échange direct et interactif entre un nombre fini de personnes. Sont exclus les services où cette fonction n'existe « uniquement en tant que fonction mineure accessoire » d'un autre service.
- **6° quater :** service non fondé sur la numérotation.
- **15° :** l'opérateur est toute personne fournissant au public un service de communications électroniques.
- Le 6° de la version française ne reprend pas la condition « normalement contre rémunération ».
- **Lien :** [article L. 32](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000049571421). **Vérifié le :** 7 novembre 2026.

---

## 3. PPF est-elle concernée, et pour quels contenus

### 3.1 La qualification d'hébergeur

Depuis la loi SREN, l'hébergeur au sens de la LCEN est celui qui fournit un service d'hébergement au sens du DSA : stocker des informations fournies par un destinataire du service, à sa demande (article 3, g), iii)). Quand un utilisateur publie un message, un événement ou une revendication, PPF stocke une information qu'il a fournie, à sa demande, et la rend accessible au public. C'est le cas typique de l'hébergement, et même de la « plateforme en ligne » du DSA. **PPF est donc très probablement visée par l'article 6, V, A pour ses contenus publics.**

La formule « les personnes dont l'activité consiste à fournir [...] des services d'hébergement » ne paraît pas offrir d'échappatoire : sur PPF, l'hébergement des publications des utilisateurs est une activité centrale.

### 3.2 Une association à but non lucratif est-elle concernée de la même façon ?

- **Ce qui joue pour une exclusion (argument fragile) :**
  - Le renvoi au DSA fait dépendre la qualification de la notion de « service de la société de l'information », qui suppose un service « presté normalement contre rémunération » (directive 2015/1535).
  - La Cour de justice exige une « activité économique » (arrêt Mc Fadden, C-484/14, 15 septembre 2016, point 40, s'appuyant sur le considérant 18 de la directive 2000/31) ([lien](https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:62014CJ0484)).
  - Un service gratuit, sans publicité, financé par des dons et des adhésions, pourrait ne pas en être un.
- **Ce qui joue contre :**
  - Selon le même arrêt, la rémunération « n'est pas nécessairement versée par les personnes qui en bénéficient » (point 41), et le mot « normalement » renvoie au type de service (un réseau social est normalement rémunéré, par exemple par la publicité).
  - L'ancienne définition de la LCEN visait expressément l'hébergement « même à titre gratuit ». Rien n'indique que la loi SREN ait voulu exclure les associations (travaux préparatoires non vérifiés).
  - Le texte dit « toute personne », sans distinguer selon le but lucratif.
  - Le RGPD s'applique de toute façon, quel que soit le statut.
- **Conclusion :** il ne faut pas fonder la conformité de PPF sur l'idée qu'une association échapperait à l'obligation. On peut garder cet argument comme moyen de défense subsidiaire (voir les questions pour l'avocat).

### 3.3 Avant la déclaration de l'association

Tant que l'association n'est pas déclarée, le collectif n'a pas de personnalité juridique. Les obligations et la responsabilité pénale (article 6, V, B) pèseraient alors sur la ou les personnes physiques qui exploitent effectivement le service, au premier rang desquelles la propriétaire. C'est une raison de plus pour déclarer l'association **avant** l'ouverture publique (point à confirmer par l'avocat).

### 3.4 Tableau par type de contenu

| Contenu ou action sur PPF | Qualification probable | Décret n° 2021-1362 applicable ? | Commentaire |
|---|---|---|---|
| Messages courts publics, réponses, commentaires | Hébergement (plateforme en ligne) | Oui | Cas central. |
| Événements de l'agenda, appels, revendications publiées par les utilisateurs | Hébergement | Oui | Idem. |
| Profil public (pseudonyme, présentation, avatar) | Hébergement | Oui | Le profil est un contenu publié (événement Nostr de type 0). |
| Photos et vidéos envoyées (y compris via PeerTube) | Hébergement | Oui | Vérifier que PeerTube ne journalise pas l'IP (voir 6.8). |
| Réactions publiques visibles nominativement | Hébergement | Probablement | Si seuls des totaux anonymes sont affichés, on se rapproche du vote. |
| Témoignages publiés après relecture par l'équipe | Hébergement **ou** édition | Discutable | Une relecture avant publication peut faire de PPF l'éditeur (rôle actif) : responsabilité d'éditeur au lieu du régime d'hébergeur. À trancher par l'avocat. |
| Contenus éditoriaux de PPF (`/content`, fiches pratiques) | Édition | Non | PPF en est l'éditeur (article 1-1 : mentions, directeur de la publication). |
| Salons ouverts à tous, lisibles sans validation humaine | Hébergement, communication au public | Oui | Considérant 14 du DSA : les canaux ouverts relèvent de la diffusion au public. |
| Salons fermés (membres admis par une personne) | Hébergement privé, voire service de communications interpersonnelles | Probablement non (article 8 du décret) | La correspondance privée est hors de la « communication au public en ligne ». Voir 3.5. |
| Messages privés chiffrés de bout en bout (NIP-17), seulement relayés | Stockage technique et, peut-être, service de communications interpersonnelles | Probablement non | Voir 3.5. PPF ne voit ni le contenu ni, idéalement, les métadonnées utiles. |
| Votes, bulletins, soutiens anonymes | Pas de « contenu mis en ligne » par l'utilisateur | Non (à confirmer) | Seul le résultat agrégé est publié, et c'est PPF qui le publie. Toute conservation liant un vote à une personne heurterait l'article 9 du RGPD et la promesse d'anonymat. |
| Lecture, consultation, recherche, téléchargement | Aucune création de contenu | Non | Le décret ne vise que les opérations de création, de modification et de suppression (article 7). |
| Signalements et contenus en attente chiffrés pour la modération | Échange non public avec l'équipe | Non | Ce n'est pas de la communication au public ; la modération est tracée par le journal d'audit interne. |

### 3.5 Le relais d'enveloppes chiffrées est-il un hébergement ?

- **Au sens du DSA, probablement oui :** le relais stocke, à la demande de l'expéditeur, une enveloppe chiffrée jusqu'à ce que le destinataire la récupère (conservation prévue d'au moins trois mois). La définition de l'hébergement n'exige pas de diffusion au public, et le considérant 29 cite le stockage de fichiers.
- **Mais le décret n° 2021-1362 ne s'y applique très probablement pas :**
  - son article 8 limite la conservation aux données collectées par l'hébergeur « lorsqu'elles assurent la mise en œuvre des services de communication au public en ligne » ;
  - un message privé a, par définition, « un caractère de correspondance privée » (LCEN, article 1er, IV) ;
  - de plus, PPF ne connaît pas le contenu et, avec l'emballage NIP-17, ne connaît pas l'expéditeur réel.
- **Autre qualification possible :**
  - La messagerie de PPF pourrait être un **service de communications interpersonnelles non fondé sur la numérotation** (CPCE, L. 32, 6° bis et 6° quater). Elle n'est pas une « fonction mineure accessoire », puisque « Messages » est une entrée de la barre principale.
  - La Cour de justice a admis qu'un service en ligne puisse être un service de communications électroniques (La Quadrature du Net, 6 octobre 2020, points 203 à 205).
  - PPF serait alors, pour cette partie, un « opérateur » au sens du CPCE, soumis directement à l'article L. 34-1 : effacement des données de trafic, conservation de l'identité civile et des données de connexion collectées, injonction du Premier ministre (V de l'article R. 10-13).
  - Même dans ce cas, la logique « on ne conserve que ce qu'on collecte » vaut aussi pour les opérateurs (Conseil d'État, 30 juin 2023, n° 459724, point 9, à propos de l'article R. 10-12), et la conservation ne peut jamais porter sur le contenu des correspondances (L. 34-1, VI).
- **Conclusion :** ne rien collecter pour la messagerie privée paraît défendable, mais la qualification de PPF comme « opérateur » doit être examinée par l'avocat. Elle pourrait aussi entraîner d'autres obligations (déclaration ou autres règles du CPCE, non vérifiées ici).

---

## 4. Données exactes, durée, accès

### 4.1 Ce que le décret impose à un hébergeur

| Article du décret | Données | Durée | Point de départ | Condition |
|---|---|---|---|---|
| 2 | Identité civile : nom, prénom, date et lieu de naissance, adresses postales, **adresses e-mail**, **numéros de téléphone** | 5 ans | Fin de validité du contrat | Seulement si collectées (article 8) |
| 3 | Identifiant utilisé ; pseudonyme(s) utilisé(s) ; données de vérification ou de modification du mot de passe (dernière version) | 1 an | Fin du contrat ou clôture du compte | Seulement si collectées |
| 4 | Paiement : type, référence, montant ; date, heure et lieu pour un paiement physique | 1 an | Fin du contrat ou clôture du compte | Seulement si collectées |
| 5, 2° | Pour chaque opération de création : identifiant de la connexion à l'origine de la communication ; types de protocoles utilisés | 1 an | Jour de l'opération | Seulement si collectées |
| 6, 2° | Pour chaque opération de création : identifiant du contenu ; nature de l'opération ; date et heure ; identifiant de l'auteur s'il l'a fourni | 1 an | Jour de l'opération | **Seulement sur injonction du Premier ministre** (en vigueur : décret n° 2026-920, depuis le 21 octobre 2026), et seulement si collectées |

**L'adresse IP et le port sont-ils inclus ?**

- Pour les hébergeurs, le décret ne nomme pas l'adresse IP. Il parle de l'« identifiant de la connexion à l'origine de la communication ».
- Dans son avis sur le projet de décret, la CNIL a toutefois considéré l'adresse IP et le port comme « un cas spécifique d'identifiant de connexion » (délibération n° 2021-115 du 7 octobre 2021, [lien](https://www.legifrance.gouv.fr/cnil/id/CNILTEXT000044231736)).
- En pratique, il faut donc lire ce champ comme l'adresse IP de l'utilisateur **et** son port source. Sans le port, l'adresse ne permet souvent pas d'identifier un abonné derrière un partage d'adresse chez son fournisseur d'accès, alors que les fournisseurs d'accès conservent justement « L'adresse IP attribuée à la source de la connexion et le port associé » (article 5, 1°, c)).
- **Mais seulement si l'hébergeur les collecte** (article 8).

**Historique utile.** Le projet de décret ajoutait une condition de pertinence au regard des finalités de la loi. L'Arcep a relevé l'insécurité juridique de cette formule (avis n° 2021-2080 du 7 octobre 2021, [lien](https://www.arcep.fr/uploads/tx_gsavis/21-2080.pdf)), et le texte final ne l'a pas gardée. Il ne reste que la condition de collecte.

### 4.2 Ce que PPF détient aujourd'hui, catégorie par catégorie

| Catégorie du décret | Situation actuelle de PPF | Conséquence |
|---|---|---|
| Identité civile (article 2) | Rien : ni nom, ni e-mail, ni téléphone | Rien à conserver. **Attention :** tout champ e-mail ou téléphone, même facultatif, ferait naître une conservation de **cinq ans**. |
| Identifiant (article 3, 1°) | Clé publique Nostr de l'utilisateur | À conserver un an après la clôture du compte. |
| Pseudonymes (article 3, 2°) | Nom affiché (événement de profil), éventuel identifiant NIP-05 | À conserver un an après la clôture du compte. |
| Mot de passe (article 3, 3°) | Aucun : la clé et la phrase de récupération restent sur l'appareil | Rien à conserver. |
| Paiement (article 4) | Aucun lien entre paiements (adhésions, dons) et comptes, à condition de garder cette séparation | Rien à conserver si la séparation est stricte. |
| Identifiant de connexion (article 5) | Adresse IP retirée avant l'application, aucun journal | Rien à conserver si l'IP n'est jamais « collectée » (voir partie 6). |
| Protocoles (article 5) | Connus par construction (HTTPS, WebSocket) | Coût nul : on peut les noter par opération (un octet). |
| Données d'injonction (article 6) | Contenues dans l'événement signé : identifiant de l'événement, type, date de création, clé publique de l'auteur | Déjà détenues tant que le contenu existe ; il faut aussi les garder un an après une modification ou une suppression. |

### 4.3 Qui peut accéder aux données, et comment

- **Autorité judiciaire :**
  - L'article 6, V, B sanctionne le refus de déférer à sa demande.
  - En pratique, les officiers de police judiciaire agissent par réquisition, sous le contrôle du procureur ou du juge d'instruction (articles 60-1 et 60-2 du code de procédure pénale, et articles équivalents pour l'enquête préliminaire et l'instruction).
  - Pour les données de connexion (IP) et de trafic, l'article 60-1-2 limite les réquisitions aux infractions punies d'au moins trois ans d'emprisonnement, ou d'au moins un an si l'infraction est commise en ligne et que la réquisition vise seulement à identifier l'auteur.
  - La chambre criminelle exige en outre un contrôle préalable par une juridiction ou une autorité administrative indépendante pour l'accès aux données de trafic et de localisation (Cass. crim., 12 juillet 2022, n° 21-83.710 et trois autres arrêts, [résumé officiel sur curia](https://curia.europa.eu/site/jcms/p1_1000070643/hr/france-cour-de-cassation-crim-arret-du-12-juillet-2022-n21-83-710)).
- **Services de renseignement :** recueil auprès des hébergeurs prévu par l'article L. 851-1 du code de la sécurité intérieure, sous le contrôle de la CNCTR.
- **Autres autorités administratives :** plusieurs codes (douanes, finances, marchés financiers, etc.) prévoient des droits de communication de données de connexion. Ils n'ont **pas** été vérifiés pour cette note.
- **Victimes et parties civiles :** elles passent par un juge (par exemple une ordonnance en référé) pour obtenir des données d'identification. Le secret de l'hébergeur « n'est pas opposable à l'autorité judiciaire » (article 1-1, II).
- **Obligations de PPF à réception (DSA, article 10) :** informer l'autorité de la suite donnée, puis informer l'utilisateur concerné au plus tard au moment où PPF agit, ou au moment fixé par l'autorité.

---

## 5. Jurisprudence

### 5.1 CJUE, grande chambre, 6 octobre 2020, La Quadrature du Net e.a. (C-511/18, C-512/18, C-520/18, EU:C:2020:791)

- **Point 1 du dispositif (directive 2002/58) :** interdiction de la conservation généralisée et indifférenciée des données de trafic et de localisation, avec des exceptions :
  - injonction limitée dans le temps en cas de menace grave pour la sécurité nationale ;
  - conservation ciblée ;
  - « une conservation généralisée et indifférenciée des adresses IP attribuées à la source d'une connexion », pour une durée limitée au strict nécessaire, pour la sécurité nationale, la criminalité grave et les menaces graves contre la sécurité publique ;
  - conservation généralisée de l'identité civile, pour la lutte contre la criminalité en général ;
  - conservation rapide (gel), pour la criminalité grave.
- **Point 3 du dispositif (hébergeurs) :** la directive 2000/31 n'est pas applicable ; la protection relève de la directive 2002/58 ou du RGPD. L'article 23 du RGPD s'oppose à une réglementation imposant « aux fournisseurs de services d'hébergement la conservation généralisée et indifférenciée » des données afférentes à ces services (voir aussi le point 212).
- **Points 203 à 205 :** certains services en ligne, notamment de messagerie, peuvent être des services de communications électroniques.
- [Lien EUR-Lex](https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:62018CJ0511). Vérifié le 7 novembre 2026.

### 5.2 CJUE, grande chambre, 20 septembre 2022, SpaceNet et Telekom Deutschland (C-793/19 et C-794/19, EU:C:2022:702)

- Confirme la jurisprudence de 2020 et ses exceptions.
- La conservation généralisée des adresses IP suppose un accès soumis au « strict respect des conditions matérielles et procédurales » (point 101), avec des garanties strictes contre le traçage de l'activité en ligne (point 102).
- [Lien EUR-Lex](https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:62019CJ0793). Vérifié le 7 novembre 2026.

### 5.3 CJUE, assemblée plénière, 30 avril 2024, La Quadrature du Net e.a. (C-470/21, EU:C:2024:370)

- L'adresse IP est moins sensible que les autres données de trafic (points 75 et 76).
- Sa conservation généralisée peut être justifiée par la lutte contre les infractions **en général**, à une condition : le dispositif doit garantir « une séparation effectivement étanche des différentes catégories de données conservées » (point 84), techniquement sûre et fiable (points 86 à 88).
- La fiabilité de cette séparation doit être contrôlée régulièrement par une autorité publique distincte de celle qui demande l'accès (point 89).
- L'accès doit servir à la seule identification, sans surveillance de l'activité en ligne (points 99 à 101).
- C'est la référence la plus proche, en droit de l'Union, d'un « stockage scellé » (voir la partie 7).
- [Lien EUR-Lex](https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:62021CJ0470). Vérifié le 7 novembre 2026.

### 5.4 Conseil constitutionnel, 25 février 2022, n° 2021-976/977 QPC, M. Habib A. et autre

- L'ancienne rédaction de l'article L. 34-1 (conservation générale d'un an pour toutes les infractions) est déclarée contraire à la Constitution.
- Motif : elle s'appliquait « sans considération de la nature et de la gravité des infractions » (point 12).
- [Lien](https://www.conseil-constitutionnel.fr/decision/2022/2021976_977QPC.htm). Vérifié le 7 novembre 2026.

### 5.5 Conseil d'État, Assemblée, 21 avril 2021, French Data Network et autres (n° 393099, 394922, 397844, 397851, 424717, 424718, ECLI:FR:CEASS:2021:393099.20210421, publié au Recueil)

Points relevés à la lecture, à recouper :

- **Points 24 et 25 :** les hébergeurs ne relèvent pas de la directive 2002/58, mais du RGPD.
- **Points 34 à 36 :** conservation généralisée admise pour l'identité civile, ainsi que pour les données de compte et de paiement.
- **Points 37 à 41 :** conservation générale d'un an des adresses IP admise pour la criminalité grave, la gravité s'appréciant au cas par cas sous le contrôle du juge pénal.
- **Points 42 à 46 :** conservation générale des données de connexion justifiée pour la sécurité nationale, compte tenu de la menace, mais à condition d'un réexamen périodique, alors absent.
- **Point 58 :** pour les autres finalités, la conservation générale des données de connexion par les hébergeurs est contraire au droit de l'Union.
- **Dispositif, articles 1er et 2 :** annulation des refus d'abroger l'article R. 10-13 du CPCE et le décret n° 2011-219, en tant qu'ils ne limitent pas les finalités à la sécurité nationale et ne prévoient pas de réexamen de la menace ; abrogation à faire dans les six mois.
- **Suites :** la loi n° 2021-998 du 30 juillet 2021, puis les décrets du 20 octobre 2021.
- [Lien](https://www.conseil-etat.fr/fr/arianeweb/CE/decision/2021-04-21/393099). Vérifié le 7 novembre 2026.

### 5.6 Conseil d'État, 30 juin 2023, Free, Free Mobile et Scaleway (n° 459724 et 459726, ECLI:FR:CECHR:2023:459724.20230630, inédit au Recueil) : recours contre le décret n° 2021-1362 lui-même

- **Requêtes rejetées.**
- **Point 9 :** les fournisseurs et hébergeurs « ne sont tenus de conserver que les données qu'ils sont amenés à collecter ». L'argument selon lequel le décret imposerait de collecter est écarté.
- **Point 16 :** la définition de la contribution (article 7) reprend celle de 2011 et n'allonge pas les durées.
- **Point 17 :** le décret ne règle pas les conditions d'accès, qui relèvent d'autres textes.
- [Lien conseil-etat.fr](https://www.conseil-etat.fr/fr/arianeweb/CE/decision/2023-06-30/459724) ; [Légifrance](https://www.legifrance.gouv.fr/ceta/id/CETATEXT000047773951). Vérifié le 7 novembre 2026.

### 5.7 Conseil d'État, 30 juin 2023, M. C. et autre, Free et Free Mobile (n° 468361 et 469712, ECLI:FR:CECHR:2023:468361.20230630, mentionné aux tables) : recours contre le décret d'injonction n° 2022-1327

- **Requêtes rejetées.**
- **Point 5 :** l'injonction vise aussi les hébergeurs.
- **Point 7 :** garanties jugées suffisantes (un an maximum, renouvellement sous conditions, recours en annulation et en référé).
- **Point 8 :** un nouveau décret n'a pas à reposer sur une menace différente de la précédente.
- **Point 9 :** menace terroriste, ingérences étrangères, cybermenace.
- Deux référés contre le même décret avaient été rejetés faute d'urgence : CE, ord., 26 octobre 2022, n° 468364 ([lien](https://www.legifrance.gouv.fr/ceta/id/CETATEXT000046519823)) et CE, ord., 28 octobre 2022, n° 468489 ([lien](https://www.legifrance.gouv.fr/ceta/id/CETATEXT000046519828)).
- [Lien conseil-etat.fr](https://www.conseil-etat.fr/fr/arianeweb/CE/decision/2023-06-30/468361). Vérifié le 7 novembre 2026.

### 5.8 Décisions postérieures sur le décret n° 2021-1362

Une recherche sur Légifrance (jurisprudence administrative, mention « 2021-1362 ») ne renvoie que quatre décisions, la plus récente étant celle du 30 juin 2023. Aucune décision plus récente n'a été trouvée sur ce décret, ni sur les décrets d'injonction de 2023 à 2026 (recherche faite le 7 novembre 2026 ; voir l'annexe B).

### 5.9 Ce que cela signifie pour un hébergeur

| Donnée | Conservation généralisée permise ? | Finalités | Durée en droit français |
|---|---|---|---|
| Identité civile, données de compte et de paiement | Oui | Criminalité en général, sécurité publique, sécurité nationale | 5 ans (identité) ; 1 an (compte, paiement) |
| Adresse IP et port à la source de la connexion | Oui, mais seulement pour des finalités graves | Criminalité grave, menaces graves contre la sécurité publique, sécurité nationale (la CJUE admet depuis 2024 la criminalité en général si la séparation est étanche, mais la loi française n'a pas élargi les finalités) | 1 an |
| Autres données de trafic (métadonnées de chaque publication) | Seulement sur injonction du Premier ministre | Sécurité nationale, menace réexaminée chaque année | 1 an |
| Contenu des correspondances | Jamais | Aucune | Aucune |

Dans tous les cas, l'obligation ne porte que sur ce que l'hébergeur collecte. **Aucune décision n'impose de collecter une donnée pour pouvoir la conserver.**

---

## 6. Peut-on ne rien conserver ?

### 6.1 Ce qui ne peut pas être « rien »

Même dans la lecture la plus protectrice, PPF détient forcément certaines données, et doit donc les conserver :

- la clé publique de chaque utilisateur et ses pseudonymes (article 3), un an après la clôture du compte ;
- pendant l'injonction en cours, les métadonnées de chaque création, modification ou suppression de contenu public (article 6) : identifiant de l'événement, nature, date et heure, clé de l'auteur, un an.

Ces données sont pseudonymes et, pour l'essentiel, déjà publiques dans les événements signés. Le « minimum légal » de PPF est donc un **registre pseudonyme**, sans aucune donnée d'identité réelle ni adresse IP.

### 6.2 Lecture A : ne jamais collecter l'adresse IP, donc ne rien avoir à conserver

- **Arguments :**
  - article 8 du décret (conservation « dans la mesure où elles sont collectées ») ;
  - Conseil d'État, 30 juin 2023, n° 459724, point 9 ;
  - CNIL, délibération n° 2021-115 : les textes ne leur imposant pas de conserver « des données qu'ils n'auraient pas préalablement collectées » (le passage parle des données que les fournisseurs « détiennent ») ;
  - suppression, dans le texte final, de la condition de pertinence critiquée par l'Arcep ;
  - interprétation stricte de la loi pénale (code pénal, article 111-4) ;
  - minimisation et protection dès la conception (RGPD, articles 5 et 25) ;
  - cohérence technique : un accès par Tor (prévu en phase 3) ou par un VPN rend l'IP réelle inaccessible, ce que l'article 8 permet manifestement.
- **Faiblesses :**
  - Techniquement, le serveur **reçoit** l'adresse IP pour établir la connexion. Un procureur pourrait soutenir que PPF « collecte » l'IP puis choisit de la jeter, et que ce choix organise le manquement. Le Conseil d'État parle des données que l'hébergeur est « amené à collecter », une formule qu'on pourrait lire largement.
  - Le verbe « détiennent » de l'article 6, V, A pourrait être lu comme une obligation de détenir.
  - Aucun juge pénal ne s'est prononcé sur un hébergeur qui, par conception, ne garde aucune IP.
- **Réponse aux faiblesses :**
  - Un traitement en mémoire vive, limité à l'acheminement de la requête, sans enregistrement, n'est pas une « détention ». La CNIL parle des données que les fournisseurs « détiennent au titre de la fourniture des services ».
  - L'article 111-5 du code pénal permettrait en outre au juge pénal d'apprécier la légalité d'une lecture extensive du décret au regard du droit de l'Union (CJUE 2020, point 3 du dispositif).
- **Condition indispensable :** l'IP ne doit être enregistrée **nulle part**, y compris dans les fonctions anti-abus et dans les logiciels tiers (voir 6.8). Une limitation de débit par adresse IP qui écrit des adresses, même hachées, fragiliserait la lecture A : on pourrait dire que PPF collecte l'IP.

### 6.3 Lecture B : la clé publique de l'utilisateur comme seul identifiant

Les contenus sont signés par la clé de l'utilisateur, et PPF ne détient que cette clé pseudonyme. Le décret s'en accommode bien :

- l'article 3 vise « L'identifiant utilisé » ;
- l'article 6 vise l'identifiant fourni par l'auteur « lorsque celui-ci l'a fourni ».

Cette lecture n'est pas une alternative à la lecture A : elle en est le complément. Elle décrit ce que PPF conserve, alors que la lecture A explique pourquoi PPF n'a pas d'IP à conserver. Elle ne suffirait pas si PPF collectait l'IP, car l'article 5 s'appliquerait alors.

### 6.4 Lecture C : une association gratuite n'est pas un « fournisseur de services d'hébergement »

Cet argument repose sur la condition de rémunération du service de la société de l'information (voir 3.2). Il est **fragile**, contraire à l'esprit de l'ancienne définition (« même à titre gratuit ») et risqué devant un juge pénal. À ne garder qu'en défense subsidiaire.

### 6.5 Lecture D : tout conserver par prudence

Conserver l'IP de toutes les connexions, ou pour la lecture, les votes et les messages privés, n'est exigé par aucun texte. Ce serait contraire au RGPD (minimisation, article 9) et à la décision de la propriétaire. **À écarter.**

### 6.6 Risques

- **Pénal :**
  - un an d'emprisonnement et 250 000 € d'amende pour la personne physique (dirigeant de droit ou de fait) ;
  - jusqu'à 1 250 000 € d'amende pour l'association, avec des peines complémentaires dont l'interdiction d'exercer l'activité ;
  - le refus de répondre à une réquisition judiciaire est puni des mêmes peines, mais PPF peut toujours répondre en transmettant ce qu'elle détient (clé, pseudonymes, métadonnées).
- **Civil :** une victime qui ne peut pas identifier l'auteur d'un contenu illicite pourrait reprocher une faute à PPF (code civil, article 1240). Aucune décision en ce sens n'a été recherchée ni vérifiée pour cette note.
- **Contexte :** dans un mouvement social, une plateforme sans IP peut attirer l'attention des autorités. L'incrimination de l'article 6, V, B pourrait servir de levier. La réactivité de PPF sur les retraits de contenus manifestement illicites (DSA, article 6) devient alors d'autant plus importante.
- **Risque inverse :** conserver les IP crée un fichier très sensible (qui a publié quoi, dans un contexte politique), exposé aux saisies, aux fuites et aux piratages. C'est précisément le risque que la conception scellée de la partie 7.2 réduit.
- **Transparence envers les utilisateurs :** même si PPF ne garde rien, d'autres intermédiaires (fournisseur d'accès de l'utilisateur, hébergeur du serveur) peuvent garder des traces. PPF ne doit pas promettre un anonymat absolu, et peut recommander Tor à ceux qui en ont besoin.

### 6.7 Ce que diraient probablement la CNIL et un juge

- **CNIL :** très probablement favorable à l'absence de collecte, au vu de sa délibération n° 2021-115, de son rappel du principe de minimisation (« limiter cette conservation aux données strictement nécessaires ») et de l'article 25 du RGPD.
- **Juge administratif :** le Conseil d'État a déjà jugé qu'il n'y a pas d'obligation de collecte (2023, point 9).
- **Juge pénal :** aucune décision trouvée. L'interprétation stricte joue en faveur de PPF. Le principal danger est une lecture large du mot « collecter », appliquée à l'adresse reçue puis jetée. Estimation de recherche, à valider par l'avocat : risque **faible à modéré**, et plus faible si le choix est documenté, motivé par le RGPD et accompagné d'une coopération active avec la justice.

### 6.8 Recommandation

Adopter la **lecture A complétée par la lecture B**, c'est-à-dire le « minimum légal pseudonyme » (partie 7.1), aux conditions suivantes :

1. **Aucune IP enregistrée nulle part :**
   - reverse proxy sans journal ;
   - application sans IP ;
   - anti-abus par preuve de travail et limites par clé, pas par adresse ;
   - vérifier la configuration de Caddy, de PeerTube et de ntfy, dont les réglages par défaut n'ont pas été vérifiés ici ;
   - pas de rapports d'erreurs contenant des adresses.
2. **Ni e-mail ni téléphone**, même facultatifs. Sinon, cinq ans de conservation (article 2).
3. **Aucun lien entre paiements ou adhésions et clés publiques.**
4. **Méthode « une personne, une voix » (choix encore ouvert) :** une vérification d'identité dont PPF recevrait le résultat nominatif ferait naître des données d'identité civile « collectées », donc conservées cinq ans. Il faut privilégier une méthode où PPF ne reçoit jamais l'identité, par exemple une attestation anonyme. Point à examiner avec l'avocat **avant** de choisir.
5. **Registre pseudonyme** des opérations sur les contenus publics, conservé un an (partie 7.1).
6. **Choix documenté :** une page dans `docs/DECISIONS.md` et dans le registre RGPD (base légale, raisonnement, sources de cette note), et une procédure écrite de réponse aux réquisitions.
7. **Conception « IP scellée » (partie 7.2) prête à être activée** si l'avocat estime que le risque pénal impose de collecter l'IP pour les publications.

---

## 7. Conception minimale proposée

### 7.1 Niveau 1 (recommandé dès maintenant) : registre pseudonyme

- **Opérations couvertes, et seulement elles :**
  - créations, modifications et suppressions de contenus publics : messages, commentaires, événements, appels, revendications, profils, médias, réactions nominatives, messages dans les salons ouverts ;
  - **jamais** la lecture, les votes, les bulletins, les soutiens anonymes, les messages privés chiffrés, les salons fermés ni les signalements.
- **Champs, et rien d'autre :**
  - identifiant de l'événement ;
  - nature de l'opération (création, modification, suppression) ;
  - date et heure de réception par le serveur, en temps universel (UTC) à la seconde, en plus de la date indiquée dans l'événement ;
  - clé publique de l'auteur ;
  - type de protocole (un octet).
  - Pour chaque compte : clé publique et pseudonymes successifs, avec la date de chaque changement.
- **Contenus supprimés :**
  - on garde un an les seules métadonnées, jamais le contenu ;
  - pour une suppression Nostr (NIP-09), l'événement de suppression signé suffit, avec les métadonnées de l'événement supprimé ;
  - pour un événement remplaçable, on garde l'identifiant de l'ancienne version, sa date et la clé.
- **Durées :**
  - un an à compter de l'opération (articles 5 et 6) ;
  - un an après la clôture du compte pour la clé et les pseudonymes (article 3) ;
  - purge automatique quotidienne au-delà ;
  - la « clôture du compte » doit être définie dans les conditions d'utilisation (par exemple une demande d'effacement du compte), puisqu'un compte Nostr n'a pas de date de fin naturelle.
- **Stockage :**
  - base séparée de la base de l'application, jamais utilisée par les fonctions de la plateforme ;
  - accès réservé à deux personnes désignées, chaque accès étant journalisé ;
  - chiffrement au repos.

  C'est la logique d'« archivage intermédiaire » de la CNIL : « une séparation avec la base active doit être opérée », et les données ne sont consultées « de manière ponctuelle et motivée par des personnes spécifiquement habilitées » ([CNIL, durées de conservation](https://www.cnil.fr/fr/limiter-la-conservation-des-donnees), page datée du 2 avril 2026, lue le 7 novembre 2026).
- **Après l'injonction :** si le décret n° 2026-920 n'est pas renouvelé à l'automne 2027, les données de l'article 6 ne sont plus exigées, en dehors de ce que PPF détient pour d'autres raisons. Il faudra alors réévaluer le registre.
- **Mise en œuvre (7 novembre 2026, D-077) :** niveau 1 seulement, comme décidé. Code : `server/src/main/kotlin/org/politisationpopulairefrancaise/server/legal/LegalRegister.kt` (liste des types de contenus publics, écriture dans la transaction de l'événement par `EventRepository`, rappels sur l'adresse IP, l'e-mail et le téléphone), `LegalRegisterPurge.kt` (purge quotidienne) et `TransparencyApi.kt` (compteur public `GET /api/v1/transparence/requisitions`) ; schéma séparé `legal_register` et fonctions `purge`, `answer_requisition`, `requisitions_by_month` dans `server/src/main/resources/db/migration/V11__legal_register.sql` (numéro provisoire), droits du rôle du serveur (ajout seulement) dans `afterMigrate.sql` ; réquisition à deux dans l'espace de l'équipe (`server/backoffice/LegalRequisitions.kt`, permission `legal.requisition`). Écarts avec cette section : la base séparée est un schéma séparé de la même base PostgreSQL, auquel le serveur ne peut qu'ajouter des lignes ; le chiffrement au repos relève du chiffrement du disque et des sauvegardes du serveur ; pour une suppression NIP-09, le serveur garde l'événement de suppression et les métadonnées des contenus visés, mais n'efface pas encore ces contenus.

### 7.2 Niveau 2 (à activer seulement si l'avocat l'exige) : adresse IP scellée

- **Quand :** uniquement au moment d'une opération du niveau 1, jamais pour la lecture, les votes ou les messages privés.
- **Quoi :** adresse IP, port source, horodatage UTC précis, identifiant de l'événement, type de protocole. Ni navigateur, ni appareil, ni localisation.
- **Comment :**
  - Le processus du relais scelle l'enregistrement dès la réception, avec une **clé publique** (chiffrement « scellé » de type X25519, choix de bibliothèque à faire par l'équipe technique).
  - La clé privée correspondante n'existe **jamais sur le serveur**. Elle est conservée hors ligne et partagée entre deux ou trois détenteurs désignés par le bureau, par exemple un partage de secret « deux sur trois » dont l'avocat serait l'un des détenteurs.
  - Le serveur peut écrire mais pas lire : un piratage ou une saisie du serveur ne révèle aucune adresse.
- **Séparation :** stockage distinct du registre du niveau 1 et de la base des contenus, en écho à la « séparation effectivement étanche » de la CJUE (2024, point 84).
- **Suppression garantie :**
  - une paire de clés par mois ;
  - la clé privée du mois M est détruite après M + 12 mois, plus une courte marge ;
  - les sauvegardes deviennent ainsi illisibles (effacement cryptographique).
  - Avant toute destruction, on exécute les injonctions de conservation rapide reçues (L. 34-1, III bis).
- **Ouverture :**
  - seulement sur réquisition judiciaire écrite, ou sur demande prévue par le code de la sécurité intérieure, vérifiée par l'avocat ;
  - déchiffrement hors ligne des seuls enregistrements nécessaires (jour et contenu visés) ;
  - registre des réquisitions ;
  - information de l'utilisateur selon l'article 10 du DSA ;
  - publication annuelle du nombre de réquisitions (volontaire : PPF serait dispensée de l'article 15 du DSA comme micro ou petite entreprise), sans aucun détail susceptible de compromettre une enquête.
- **Contrôle :** audit indépendant régulier de la séparation et de la gestion des clés, sur le modèle du point 89 de l'arrêt de 2024.

### 7.3 Vérification au regard du RGPD

- **Minimisation (article 5, 1, c) :** le niveau 1 ne contient que les champs du décret que PPF détient déjà. Le niveau 2 n'ajoute que l'identifiant de connexion de l'article 5, pour les seules publications.
- **Limitation de la conservation (article 5, 1, e) :** un an, puis purge ou destruction des clés. Garder plus longtemps serait illicite.
- **Base légale :** obligation légale (article 6, 1, c), avec l'article 6, V, A de la LCEN et le décret.
- **Opinions politiques (article 9) :**
  - Le contenu publié révèle souvent une opinion politique. Relier ce contenu à une clé (niveau 1) ne révèle rien de plus que l'événement public signé.
  - Le relier à une adresse IP (niveau 2) rend l'opinion rattachable à une personne réelle : c'est le cœur du risque.
  - Exceptions à discuter avec l'avocat : article 9, 2, g) (motif d'intérêt public important prévu par le droit national, avec des garanties), article 9, 2, e) (données manifestement rendues publiques par la personne, pour le contenu lui-même), article 9, 2, d) (activités légitimes d'une association à but politique, pour ses membres).
  - Le scellement, la séparation et l'accès limité aux réquisitions sont les garanties qui rendent le niveau 2 défendable.
- **Article 23 :** l'obligation nationale est une limitation des droits qui doit rester strictement nécessaire (CJUE 2020, point 3 du dispositif). Il ne faut donc rien conserver au-delà du texte.
- **Articles 25 et 32 :** pseudonymisation par construction, et chiffrement.
- **Analyse d'impact (article 35) :** conseillée au niveau 1 et très probablement nécessaire au niveau 2 (données sensibles), à faire confirmer.
- **Documentation :** entrées dans `REGISTRE_DONNEES.md` et information claire des utilisateurs dans la politique de confidentialité.

### 7.4 La CNIL a-t-elle des recommandations sur un tel stockage scellé ?

**Aucune recommandation spécifique** de la CNIL n'a été trouvée sur le stockage scellé, sous clé hors ligne, des données de la LCEN. Sa délibération n° 2021-115 ne traite pas de la sécurité des données conservées. Les repères les plus proches sont les suivants :

- **archivage intermédiaire :** séparation physique ou logique d'avec la base active, et accès ponctuel et motivé ([lien](https://www.cnil.fr/fr/limiter-la-conservation-des-donnees)) ;
- **Guide de la sécurité des données personnelles, édition 2024, fiche « Chiffrement, hachage, signature »** (14 mars 2024) : protéger les clés secrètes par des « droits d'accès restrictifs et d'un mot de passe sûr », et rédiger une procédure de gestion des clés et certificats ([lien](https://www.cnil.fr/fr/securite-chiffrement-hachage-signature)) ;
- **recommandation sur la journalisation** (délibération n° 2021-122 du 14 octobre 2021) : journaux d'accès conservés entre six mois et un an ([lien](https://www.cnil.fr/fr/la-cnil-publie-une-recommandation-relative-aux-mesures-de-journalisation)) ;
- **en droit de l'Union :** l'exigence de séparation étanche et de contrôle régulier de l'arrêt C-470/21 du 30 avril 2024.

---

## 8. Questions pour l'avocat

> Rappel : cette note n'est pas un avis juridique. Elle est inscrite sur la liste « à faire relire par un avocat ».

1. **Lecture A :** confirmez-vous qu'un hébergeur qui ne journalise jamais l'adresse IP, et ne la traite qu'en mémoire vive pour acheminer la requête, ne « collecte » pas cette donnée au sens de l'article 8 du décret n° 2021-1362 et du point 9 de la décision du Conseil d'État n° 459724 ? Quel est le risque pénal réel au titre de l'article 6, V, B de la LCEN ?
2. **Identifiant de connexion :** pour un hébergeur, l'« identifiant de la connexion à l'origine de la communication » désigne-t-il nécessairement l'adresse IP et le port source, ou un identifiant de session interne pourrait-il suffire ?
3. **Anti-abus :** une limitation de débit par adresse IP en mémoire vive, sans écriture, ferait-elle basculer PPF dans la « collecte » ? Et un journal d'IP tronquées et hachées, effacé en 24 heures ?
4. **Association :** la loi SREN, en renvoyant à la définition du DSA (service « normalement contre rémunération »), a-t-elle changé la situation d'une association gratuite sans publicité ? L'argument peut-il servir en défense subsidiaire ?
5. **Avant la déclaration :** qui porte la responsabilité pénale tant que l'association n'est pas déclarée ? Faut-il interdire toute ouverture publique avant la déclaration ?
6. **Messagerie privée :** le relais d'enveloppes chiffrées (NIP-17) fait-il de PPF un « opérateur » fournissant un service de communications interpersonnelles non fondé sur la numérotation (CPCE, L. 32) ? Avec quelles obligations (L. 34-1, déclaration, autres) ?
7. **Salons :** où passe la frontière entre salon « ouvert » (communication au public, décret applicable) et salon « fermé » (correspondance privée) ?
8. **Témoignages relus avant publication :** PPF en devient-elle l'éditeur ? Quelles conséquences, notamment sur la responsabilité de la propriétaire ou du directeur de la publication ?
9. **Votes et soutiens :** confirmez-vous qu'ils ne sont pas des « contenus mis en ligne » par l'utilisateur au sens du décret ?
10. **Injonction :** le registre pseudonyme du niveau 1 suffit-il à respecter le décret n° 2026-920 pour les données de l'article 6 ? Faut-il garder la date indiquée dans l'événement, la date de réception, ou les deux ?
11. **Clôture du compte :** comment la définir pour une identité Nostr, afin de faire courir le délai d'un an de l'article 3 ?
12. **« Une personne, une voix » :** quelles méthodes de vérification éviteraient que PPF « collecte » une identité civile soumise à cinq ans de conservation ?
13. **RGPD, article 9 :** quelle exception retenir pour le registre du niveau 1 et pour un éventuel niveau 2 ? Une analyse d'impact est-elle obligatoire ?
14. **Réquisitions :** quelle procédure suivre (vérification de l'auteur et de la base légale, article 60-1-2, délai, information de l'utilisateur selon l'article 10 du DSA) ? Peut-on publier le nombre annuel de réquisitions sans heurter le secret de l'enquête ?
15. **Mesures ciblées futures :** PPF pourrait-elle être tenue de collecter l'IP d'un compte précis à l'avenir (interception, recueil en temps réel prévu par le code de la sécurité intérieure) ? Doit-elle s'y préparer techniquement ?
16. **Autres autorités administratives :** lesquelles peuvent exiger de PPF des données de connexion (douanes, finances, marchés financiers, Arcom, etc.), et selon quelle procédure ?
17. **Évolutions à surveiller :** une proposition européenne sur la conservation des données (annoncée pour 2026) a-t-elle été publiée ? Le renouvellement annuel de l'injonction a-t-il fait l'objet de recours après 2023 ?

---

## Annexe A : sources et date de vérification

Tous les documents ci-dessous ont été consultés le **7 novembre 2026**.

| Source | Référence | Lien |
|---|---|---|
| LCEN, article 6 (version du 26 août 2026) | Loi n° 2004-575, art. 6 | https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000054744218 |
| LCEN, version au 1er janvier 2025 | Art. 6 issu de la loi SREN, art. 48 | https://www.legifrance.gouv.fr/loda/id/JORFTEXT000000801164/2025-01-01 |
| LCEN, version au 1er juin 2023 | Ancien art. 6, II et VI | https://www.legifrance.gouv.fr/loda/id/JORFTEXT000000801164/2023-06-01 |
| LCEN, article 1er | Art. 1er, IV | https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000006421540 |
| LCEN, article 1-1 | Art. 1-1, II | https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000049568614 |
| Loi SREN | Loi n° 2024-449 du 21 mai 2024 | https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000049563368 |
| Loi sur les mineurs et les réseaux sociaux | Loi n° 2026-813 du 24 août 2026 | https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000054743001 |
| CPCE, L. 34-1 | Version du 15 juin 2025 | https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000051741279 |
| CPCE, L. 32 | Version du 23 mai 2024 | https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000049571421 |
| Décret n° 2021-1362 | Articles 1 à 13 | https://www.legifrance.gouv.fr/loda/id/JORFTEXT000044228912 |
| Décret n° 2026-920 | Injonction 2026-2027 | https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000054945173 |
| Décrets d'injonction 2021 à 2025 | n° 2021-1363, 2022-1327, 2023-933, 2024-901, 2025-980 | Voir le tableau du 2.5 |
| Code pénal | Art. 111-4, 111-5, 131-38 | Voir le 2.6 |
| Code de procédure pénale | Art. 60-1-2 | https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000045289397 |
| Code de la sécurité intérieure | Art. L. 851-1 | https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000030935595 |
| CNIL | Délibération n° 2021-115 du 7 octobre 2021 | https://www.legifrance.gouv.fr/cnil/id/CNILTEXT000044231736 |
| Arcep | Avis n° 2021-2080 du 7 octobre 2021 | https://www.arcep.fr/uploads/tx_gsavis/21-2080.pdf |
| CJUE | C-511/18 e.a., 6 octobre 2020 | https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:62018CJ0511 |
| CJUE | C-793/19 et C-794/19, 20 septembre 2022 | https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:62019CJ0793 |
| CJUE | C-470/21, 30 avril 2024 | https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:62021CJ0470 |
| CJUE | C-484/14, Mc Fadden, 15 septembre 2016 | https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:62014CJ0484 |
| Conseil constitutionnel | n° 2021-976/977 QPC, 25 février 2022 | https://www.conseil-constitutionnel.fr/decision/2022/2021976_977QPC.htm |
| Conseil d'État | Ass., 21 avril 2021, n° 393099 et autres | https://www.conseil-etat.fr/fr/arianeweb/CE/decision/2021-04-21/393099 |
| Conseil d'État | 30 juin 2023, n° 459724 | https://www.conseil-etat.fr/fr/arianeweb/CE/decision/2023-06-30/459724 |
| Conseil d'État | 30 juin 2023, n° 468361 | https://www.conseil-etat.fr/fr/arianeweb/CE/decision/2023-06-30/468361 |
| Conseil d'État | Ord. 26 octobre 2022, n° 468364 ; ord. 28 octobre 2022, n° 468489 | Voir le 5.7 |
| Cour de cassation | Crim., 12 juillet 2022, n° 21-83.710 (résumé officiel sur curia) | https://curia.europa.eu/site/jcms/p1_1000070643/hr/france-cour-de-cassation-crim-arret-du-12-juillet-2022-n21-83-710 |
| DSA | Règlement (UE) 2022/2065 | https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:32022R2065 |
| Directive 2015/1535 | Art. 1er, 1, b) | https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:32015L1535 |
| RGPD | Règlement (UE) 2016/679 | https://eur-lex.europa.eu/legal-content/FR/TXT/?uri=CELEX:32016R0679 |
| CNIL | Durées de conservation (page du 2 avril 2026) | https://www.cnil.fr/fr/limiter-la-conservation-des-donnees |
| CNIL | Fiche « Chiffrement, hachage, signature » (14 mars 2024) | https://www.cnil.fr/fr/securite-chiffrement-hachage-signature |
| CNIL | Recommandation sur la journalisation (délibération n° 2021-122) | https://www.cnil.fr/fr/la-cnil-publie-une-recommandation-relative-aux-mesures-de-journalisation |

## Annexe B : ce qui n'a pas pu être vérifié

- **Arrêts de la chambre criminelle du 12 juillet 2022 :** lus seulement par leur résumé officiel publié sur curia et par des commentaires, pas dans leur texte intégral.
- **Numéros de points des décisions :** ils ont été relevés à la lecture d'outils de synthèse et doivent être recoupés dans les textes intégraux, surtout pour la décision French Data Network (points 24 à 59).
- **Décrets d'injonction de 2022 à 2025 :** seuls leur numéro, leur date et leur publication au JORF ont été vérifiés, pas leur contenu détaillé.
- **Entrée en vigueur de la loi SREN :** Légifrance affiche le 17 février 2024 pour la version de l'article 6 issue de l'article 48, alors qu'une source secondaire indique le 23 mai 2024. Point sans incidence pratique aujourd'hui.
- **Seuil de connexions de l'article 6, VI :** le décret qui fixe ce seuil n'a pas été consulté.
- **Autres droits d'accès administratifs** (douanes, finances, marchés financiers, Arcom) : non vérifiés.
- **Proposition européenne sur la conservation des données :** annoncée par la Commission pour 2026. Sa publication n'a pas pu être confirmée au 7 novembre 2026.
- **Recours postérieurs à 2023 :** aucune décision trouvée contre les décrets d'injonction de 2023 à 2026, ni contre le décret n° 2021-1362. L'absence de résultat dans une recherche n'est pas une preuve d'absence.
- **Configuration par défaut des logiciels utilisés** (Caddy, PeerTube, ntfy) en matière de journalisation des adresses IP : non vérifiée.
- **Intention du législateur de 2024** sur l'abandon de la mention « même à titre gratuit » : travaux préparatoires non consultés.
