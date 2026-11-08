# Alerte « mes élus ont voté » et mots suivis (VO-34) : conception, version 0.1

Rédigé le 8 novembre 2026 par le pôle « Voir » (« [Voir] Team Dev 2 »), d'après le cahier fonctionnel 2.1 (M20, VO-34 ; TE-01 « Mes élus » ; M31 notifications, règle D-044) et le cahier des charges 2.2 (sections 3.8 et 5). Document de conception : rien n'est codé. Trois questions attendent le chef d'équipe (section 7).

## 0. Ce qui existe déjà (relevé du 8 novembre 2026)

- **Mes élus (TE-01, D-047)** : la commune choisie reste sur l'appareil (`elus_commune` : `commune_code`, `department_code`, schéma `10.sqm`) ; les élus viennent du paquet du département (`ElusFinder.find` donne les députés de la ou des circonscriptions de la commune, les sénateurs du département, les eurodéputés) ; `ElectedOfficial.territory` vaut le code de circonscription (`6901`, `2A01`, `97101`), du département (`69`) ou `FR`. Aucun identifiant de mandat n'est sur l'appareil.
- **Données du Parlement (D-064, D-065)** : `Member.territory` utilise les mêmes codes ; un scrutin (`Ballot`) a un identifiant (`an:17:1234`, `pe:2026-10-21:12`), une date sans heure, un titre, un dossier ; un vote individuel donne la position, la délégation, la cause d'un non-vote et la mise au point (`VoteCorrection`) ; un paquet par mois et par chambre (`scrutins-an-AAAA-MM`, `votes-pe-AAAA-MM`), téléchargé aujourd'hui sur un geste seulement. Aucune trace des scrutins déjà vus.
- **Notifications (pôle Communauté, branche `feature/communaute`, pas encore dans main)** : une source implémente `NotificationSource { name; suspend fun collect(after: Long?): List<NotificationCandidate> }` dans une catégorie ; la catégorie `VOTES` (« votes du Parlement, Décider et Voir ») existe ; tout est éteint par défaut ; une source n'est lue que si sa catégorie est allumée ; première lecture sur 24 heures ; repère par source (`notification_mark`) ; silence total et écran verrouillé discret (S-25) gérés par le moteur. Le message poussé ne porte rien (D-130).
- Rien n'existe pour les mots suivis ; `FrenchText.fold` et `FrenchText.keywords` (core/text) font la comparaison sans accents ni majuscules.

## 1. Principes

1. **Le serveur ne sait jamais qui sont mes élus ni ce que je suis** : l'appareil télécharge les paquets entiers d'un mois et d'une chambre, les mêmes pour tout le monde, et fait tout le tri lui-même. Jamais de requête par élu, par circonscription ni par mot.
2. **Éteint par défaut** (principe 10) : rien ne se télécharge ni ne s'affiche tant que la personne n'a pas allumé « Votes au Parlement » (catégorie `VOTES` de Communauté) ; l'allumer explique ce qui sera téléchargé et combien cela pèse.
3. **Neutre** (M17, fiches identiques pour tous) : « Votre députée a voté pour », « contre », « s'est abstenue », « n'a pas pris part au vote (cause publiée) », « a voté par délégation », avec la mise au point quand elle existe ; jamais de jugement, jamais de classement.
4. **Pas d'inondation** : une notification par synchronisation qui résume (« Vos élus ont voté 7 scrutins »), le détail dans l'application.

## 2. Mes parlementaires (étape 1, cœur commun)

`MyParliamentarians.of(commune, elusPackage, catalog)` rapproche les élus de « Mes élus » des membres des paquets du Parlement :
- **députés** : même chambre (`AN`) et même code de circonscription ; une commune partagée entre plusieurs circonscriptions donne plusieurs députés, signalés comme tels (« votre commune compte deux circonscriptions ») ;
- **sénateurs** : chambre `SENAT`, même département, et nom identique une fois replié (`FrenchText.fold`, prénom et nom dans les deux ordres) ; un sénateur sans correspondance est montré « sans votes rapprochés » plutôt que deviné ;
- **eurodéputés** : la France est une seule circonscription (81 élus) ; voir la question 7.1 ;
- jamais de rapprochement sur le seul nom, jamais d'identifiant envoyé.

## 3. Nouveaux votes (étape 1)

- `VoteAlerts.newVotes(mine, catalog, seen)` donne, pour chaque scrutin pas encore vu, la position de chacun de mes parlementaires (`ParliamentCatalog.voteOf`), avec la mise au point.
- **Scrutins vus** : table locale `parliament_ballot_seen(ballot_id, first_seen_at)` (date de **réception** par l'appareil, pas la date du scrutin, qui arrive plusieurs jours après et sans heure) ; écrite quand un paquet est enregistré. Migration locale : le numéro qui suit celui de Communauté au moment de l'entrée (à demander au chef d'équipe).
- La première fois, tout ce qui est déjà sur l'appareil est marqué vu sans alerte (comme `skipToNow` du moteur de Communauté).

## 4. Mots suivis (étape 2)

- La personne suit des mots (`followed_word(word, folded, added_at)`, sur l'appareil seulement, 50 au plus) ; un scrutin nouveau dont le titre ou le dossier contient tous les mots repliés d'une entrée déclenche l'alerte « Un scrutin sur « retraites » ».
- Amendements et questions écrites viendront avec leur import (VO-41 pour les questions) ; la même table servira.
- Le serveur ne reçoit jamais les mots : il n'y a rien à lui envoyer, les paquets sont les mêmes pour tous.

## 5. Téléchargement et notification (étape 3, après l'entrée de Communauté)

- **Quand « Votes au Parlement » est allumé** (et seulement alors) : à chaque synchronisation de l'application, l'appareil relit l'index signé du Parlement et télécharge les paquets du mois en cours et du mois précédent des seules chambres de mes parlementaires, si leur empreinte a changé ; pas en mode économie de données (alors seulement à l'ouverture de « Voir », sur un geste, comme aujourd'hui).
- **Source de notification** `VoteAlertSource` (catégorie `VOTES`) : `collect(after)` rend un candidat par synchronisation qui a trouvé du nouveau (identifiant stable `vote-alert:<date de réception>`, `createdAt` = date de réception, cible `ppf:voir/parlement/mes-elus`), plus un candidat par mot suivi trouvé ; le titre et le détail ne s'affichent sur l'écran verrouillé que si la personne l'a choisi (S-25, moteur de Communauté).
- **Dans l'application** : page « Mes élus ont voté » dans le Parlement de « Voir » (liste par scrutin, position en toutes lettres, couleurs neutres, mise au point, lien vers le scrutin) ; gestion des mots suivis sur la même page ; lien depuis « Mes élus » (TE-01, fichiers de Comprendre : une ligne à leur demander).

## 6. Étapes et tests

| Étape | Contenu | Tests |
|---|---|---|
| 1 | `MyParliamentarians`, `VoteAlerts`, table des scrutins vus (migration locale à numéroter) | rapprochement par circonscription, commune partagée, sénateurs par nom replié, sans correspondance ; positions, délégation, mise au point ; première fois sans alerte |
| 2 | mots suivis (table, comparaison repliée, limite) | accents, majuscules, plusieurs mots, aucun envoi |
| 3 | téléchargement automatique si allumé, `VoteAlertSource`, page « Mes élus ont voté », lien depuis TE-01 | éteint par défaut : aucune requête ; mode économie de données ; une notification par synchronisation ; écran verrouillé discret ; lecteur d'écran |

## 7. Questions pour le chef d'équipe (avec recommandations)

1. **Eurodéputés** : la France élit ses 81 eurodéputés sur une seule liste nationale. Options : A) n'en suivre aucun par défaut, la personne choisit ceux qu'elle veut suivre (recommandé : protecteur et sans inondation) ; B) les suivre tous, résumé chaque semaine ; C) les suivre par parti national choisi par la personne. Coût : nul.
2. **Téléchargement automatique** quand « Votes au Parlement » est allumé (section 5) : je recommande de l'autoriser hors mode économie de données, pour les seules chambres de mes parlementaires, en affichant la taille au moment d'allumer ; sans cela, l'alerte ne peut pas respecter la règle D-044 (l'appareil vérifie lui-même à chaque synchronisation).
3. **Une notification par synchronisation** qui résume, plutôt qu'une par scrutin (recommandé : un député prend part à plusieurs dizaines de scrutins certaines semaines).
