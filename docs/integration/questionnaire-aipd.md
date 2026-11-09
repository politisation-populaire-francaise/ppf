# Questionnaire citoyen anonyme et baromètre politique (M26) : analyse d'impact sur la protection des données

- Pôle « Décider », 9 novembre 2026, **première version, à relire par un juriste** (cahier des charges, section 9.8). Méthode : celle de la CNIL (description, nécessité et proportionnalité, risques, mesures), appliquée à M26 (cahier fonctionnel 2.1 : PR-01 à PR-07). Rien de M26 n'est codé : le cahier demande cette analyse **avant** le lancement, et elle fait apparaître des choix à valider (section 7).
- Références vérifiées le 9 novembre 2026 : RGPD, [article 35](https://www.cnil.fr/fr/reglement-europeen-protection-donnees/chapitre4) (analyse d'impact obligatoire pour un traitement à grande échelle de catégories particulières de données, paragraphe 3, b) et article 37, paragraphe 1, c (délégué à la protection des données dans le même cas) ; article 9 (opinions politiques), paragraphe 2, a (consentement explicite) ; [loi Informatique et Libertés, article 45](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000037823135) (seul, un mineur consent à partir de 15 ans) ; lignes directrices du groupe de l'article 29 sur l'analyse d'impact ([WP248](https://www.cnil.fr/sites/default/files/atoms/files/wp248_rev.01_fr.pdf)).

## 1. Le traitement

| Élément | Description |
|---|---|
| Finalité | Montrer qu'une revendication ou une proposition est soutenue par des citoyens de tous âges, de tous territoires et de tous bords (DE-21), par des répartitions agrégées ; rien d'autre (ni profil, ni ciblage, ni prospection) |
| Données | Facultatives, chacune avec « Je préfère ne pas répondre » : tranche d'âge (la réponse « moins de 15 ans » clôt le questionnaire), sexe (femme, homme, non binaire), département, position sur le baromètre politique (PR-07) ; jamais religion, orientation sexuelle ni identité de genre |
| Personnes | Les votants qui choisissent de répondre, à partir de 15 ans déclarés |
| Flux | Appareil (réponses gardées en mémoire pendant la session seulement, PR-06) → **dans le bulletin anonyme** (PR-03 ; jeton VOPRF, D-110 ; envoi différé, D-112 ; aucun compte, aucune signature) → serveur des urnes (D-113) → **agrégats publics** au-dessus d'un seuil (PR-04) |
| Durées | Sur l'appareil : la session (par exemple 30 minutes d'inactivité, PR-06). Sur le serveur : réponses bulletin par bulletin seulement jusqu'au calcul des agrégats (section 5, mesure M2) |
| Destinataires | Personne : le public ne voit que des agrégats ; l'équipe n'a aucun accès bulletin par bulletin |
| Base légale | Consentement explicite (article 9, 2, a) recueilli par un geste clair après un écran d'explication (PR-02) ; voter ne l'exige jamais |

Ce sont des **opinions politiques** (article 9), potentiellement de nombreuses personnes : l'analyse d'impact est obligatoire (article 35, 3, b), et un **délégué à la protection des données** l'est probablement aussi (article 37, 1, c ; voir la section 7).

## 2. Nécessité et proportionnalité

- **Minimisation** : quatre questions, toutes facultatives ; grandes catégories (tranches d'âge, département, neuf cases du baromètre) ; pas de commune, pas de date de naissance, pas de texte libre.
- **Séparation** : les réponses ne touchent jamais le compte. La seule information sur leur auteur est le niveau du bulletin (D-112).
- **Pas de profil** : aucune réponse n'est gardée durablement, ni sur l'appareil ni sur le serveur ; afficher son bord politique sur son profil (PR-05) est un geste à part, désactivé par défaut, réversible, jamais relié aux votes.
- **Mineurs** : en dessous de 15 ans déclarés, le questionnaire s'arrête (article 45) ; aucune vérification d'âge (décision de la propriétaire, point d'attention n° 13).
- **Droits** : comme les réponses ne sont reliées à personne, le serveur ne peut ni les retrouver ni les effacer bulletin par bulletin après coup (article 11 du RGPD) ; l'écran d'explication le dit avant le consentement. Retirer son consentement arrête les réponses suivantes.

## 3. Le risque principal : la réidentification par croisement

Aujourd'hui, la **liste anonyme des bulletins est publiée à la clôture** pour que chacun puisse recompter (D-110, 6.7). Si les réponses y figuraient, une ligne « département peu peuplé, tranche d'âge, sexe, case du baromètre, vote » pourrait désigner une seule personne connue de son entourage, et révéler son opinion et son vote. **Les réponses ne doivent donc jamais figurer dans la liste publiée** (mesure M1).

## 4. Risques et gravité

| Risque | Sources | Gravité | Vraisemblance sans mesure | Avec les mesures |
|---|---|---|---|---|
| Réidentification d'un votant par croisement des réponses publiées | Liste des bulletins, cases trop petites | Maximale (opinion politique révélée) | Importante | Négligeable (M1, M2, M3) |
| Accès illégitime au serveur pendant le vote : réponses bulletin par bulletin | Pirate, administrateur malveillant | Importante | Limitée | Limitée (M2, M4, M5) |
| Rapprochement d'un bulletin et d'un compte par l'heure d'arrivée ou l'adresse IP | Serveur, hébergeur | Maximale | Limitée | Négligeable (envoi différé D-112, aucune IP journalisée) |
| Réponses fausses ou massives pour déformer les répartitions | Robots, militants | Modérée (tromper le public) | Importante | Limitée (jetons, niveaux, mention « déclaratif », M3) |
| Pression pour répondre (employeur, proche) | Entourage | Importante | Limitée | Limitée (tout facultatif, rien gardé, M6) |
| Perte des réponses | Panne | Négligeable | | |

## 5. Mesures

- **M1. Réponses hors de la liste publiée** : la liste des bulletins publiée à la clôture garde le vote et ce qui sert au recomptage, jamais les réponses du questionnaire.
- **M2. Agrégation puis effacement** : à la clôture, le serveur calcule les répartitions, les publie signées (avec le nombre de bulletins qui ont répondu), puis **efface les réponses bulletin par bulletin** ; pendant le vote, il ne publie que des agrégats au-dessus du seuil.
- **M3. Seuil par case** : une case n'est montrée que si elle compte au moins *k* personnes (valeur privée, PR-04) ; sinon « pas assez de votes ». Pas de croisement de deux catégories à la fois (âge par département, par exemple), seulement une catégorie à la fois face au vote.
- **M4. Accès** : aucun écran du back-office ne montre une réponse ; les réponses sont dans une table à part, accessible au seul rôle du service des urnes.
- **M5. Journal** : chaque calcul d'agrégats est inscrit au journal d'audit ; les répartitions publiées sont signées et archivées (D-111).
- **M6. Sur l'appareil** : réponses en mémoire seulement pendant la session, effacées à la fermeture, après l'inactivité, et par l'effacement rapide (S-44) ; jamais sur le disque.
- **M7. Information** : écran d'explication la première fois (PR-02), mention « données déclaratives » toujours visible, et ce que PPF ne peut pas faire (retrouver ou effacer une réponse).

Conséquence sur la vérifiabilité : chacun peut recompter les votes, pas les réponses (elles ne sont plus publiées bulletin par bulletin) ; le public voit les agrégats signés et le nombre de bulletins qui ont répondu. C'est le prix de la protection des personnes, à dire tel quel.

## 6. Risque résiduel

Avec M1 à M7, le risque résiduel est **limité**. Il resterait élevé sans M1 et M3 : dans ce cas, la consultation préalable de la CNIL (article 36) serait nécessaire.

## 7. À valider avant de coder

1. **Mesures M1 et M2** (retirer les réponses de la liste publiée, les effacer après agrégation) : elles changent ce que la liste publiée permet de vérifier ; recommandé.
2. **Seuil *k*** des cases (PR-04) : recommandé 20, et la tranche d'âge et le sexe seulement à l'échelle nationale ou régionale quand la question est locale.
3. **Département ou région** : recommandé, la région pour toute question nationale (moins de croisements possibles), le département seulement quand la question est départementale ou plus large et que chaque case dépasse *k*.
4. **Délégué à la protection des données** (article 37, 1, c) : la plateforme traite des opinions politiques à grande échelle (votes, soutiens, questionnaire) ; la désignation d'un délégué semble obligatoire avant l'ouverture publique, à faire confirmer par l'avocat ; un délégué peut être bénévole, mutualisé ou externe (coût à chiffrer).
5. **Libellés neutres des hauteurs** du baromètre (point d'attention n° 9) et **durée de la session** (30 minutes proposées).
6. Relecture de cette analyse par un juriste, puis signature par le responsable du traitement (l'association, une fois déclarée).

Ces points sont inscrits dans le brainstorming du cahier fonctionnel (entrée du 9 novembre 2026, « M26 : analyse d'impact »).
