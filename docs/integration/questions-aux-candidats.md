# Questions citoyennes aux candidats de 2027 (DE-30) : conception, version 0.1

- Pôle « Décider », 9 novembre 2026. Cahier fonctionnel 2.1, DE-30 : « Questions proposées et votées (une urne par question) ; les dix plus soutenues sont envoyées le même jour, avec le même délai, à tous les candidats ; réponses côte à côte dans le comparateur (DE-16) ; absence de réponse affichée. » Priorité : tôt, **ouverture en janvier 2027**. Dépendances : traitement identique (cahier des charges, section 9.2), « une personne, une voix » (D-110, D-112), avocat.
- **Rien n'est codé** : quatre choix attendent une décision (section 6, entrée « À valider » du brainstorming du cahier fonctionnel), puis la relecture par un avocat avant l'ouverture.

## 1. Les règles déjà fixées (cahier des charges, section 9.2 ; D-060, D-062 pour le Pacte)

- Mêmes questions, même jour d'envoi, même délai pour tous les candidats ; une ligne par candidat, même sans réponse ; ordre alphabétique ; une réponse arrivée après la date limite n'est pas comptée comme une réponse dans le délai, mais reste visible avec sa date.
- Aucun appel à voter, aucun classement des candidats, aucune note ; PPF ne dit jamais qui a « bien » répondu.
- Les questions sont choisies par le vote des citoyens, pas par l'équipe (CLAUDE.md, section 13 ; vision : redonner l'espace politique aux citoyens).

## 2. Le parcours prévu

1. **Proposer** (janvier 2027) : toute personne avec un compte propose une question courte (une phrase, 200 caractères au plus) et un thème parmi ceux des revendications. Relecture par l'équipe, **sur la forme seulement** (charte, section 9.3) : une question s'adresse à tous les candidats, ne nomme ni ne vise aucun d'eux, n'affirme pas un fait contesté, ne contient ni insulte ni accusation ; les doublons sont fusionnés et l'auteur en est prévenu. Le motif d'un refus est publié (journal public, comme pour les revendications).
2. **Voter** (de l'ouverture à une date publiée d'avance) : une urne par question, « Je veux que cette question soit posée », « Non », « Blanc », par les jetons anonymes (D-110) ; résultats par niveau (vérifié, non vérifié) comme pour les revendications ; élus comptés à part.
3. **Retenir** : les dix questions les plus soutenues, selon une règle publiée avant l'ouverture (choix Q2).
4. **Envoyer** le même jour à tous les candidats, avec le même délai (choix Q3) ; le texte envoyé est le même pour tous, vérifiable par son empreinte SHA-256, horodaté (D-111).
5. **Publier** les réponses côte à côte dans le comparateur (DE-16), rubrique « Questions citoyennes », candidats en ordre alphabétique, une ligne par candidat et par question ; sans réponse : « Pas de réponse au [date limite] ».

## 3. Côté technique (aperçu, à confirmer après les choix)

- **Contenus signés** (type 30023) : `ppf-type` = `question-candidats` (le texte de la question, son thème, l'élection visée par `a`, son statut : proposée, en vote, retenue, envoyée ; la date d'envoi et la date limite) et `reponse-candidat` (la réponse telle que reçue, l'adresse de la question et celle de la fiche du candidat, la date de réception, l'origine). Format décrit dans `docs/FORMATS.md` au moment du code.
- **Urnes** : sur le modèle des urnes des revendications (`server/urne/RevendicationUrnes.kt`) : une urne s'ouvre quand l'équipe passe la question « en vote », se ferme à la date publiée ; la liste publiée à la clôture permet le recomptage et « Mon bulletin a été compté » (DE-29).
- **Propositions** : le circuit des propositions de revendications (brouillon signé, file de relecture du back-office, journal d'audit), avec une file `decider.questions`.
- **Comparateur** : `core/decider/Comparator.kt` gagne une rubrique ; le test qui vérifie que chaque candidat a exactement les mêmes rubriques s'étend aux questions ; gel de la veille du scrutin à la fermeture des bureaux (Q4 de DE-16, déjà codé côté serveur).
- **Site sans JavaScript** : la même grille.
- **Données** : rien de nouveau sur l'appareil ; côté serveur, les propositions comme celles des revendications (auteur pseudonyme), les urnes comme les autres (aucun lien entre un bulletin et un compte).

## 4. Calendrier proposé

| Étape | Date proposée | Raison |
|---|---|---|
| Propositions | du lundi 4 au dimanche 24 janvier 2027 | « Ouverture en janvier 2027 » (cahier fonctionnel) |
| Vote | du lundi 25 janvier au dimanche 14 février 2027 | Trois semaines, le temps de recueillir assez de bulletins vérifiés |
| Envoi | lundi 1er mars 2027 | Avant la liste officielle des candidats, pour laisser le temps de répondre avant la campagne officielle |
| Date limite | 21 jours après l'envoi | Le même délai pour tous ; un candidat ajouté plus tard reçoit les mêmes questions avec le même délai, compté depuis son ajout (règle d'entrée du comparateur, DE-16) |

Aucune date de scrutin n'est tenue pour officielle tant que le décret de convocation n'est pas publié (comparateur, section 1) : le calendrier sera recalé sur le décret.

## 5. Risques

- **Avantage à un candidat** (code électoral, L52-8) : une question qui reprend le slogan ou le programme d'un seul candidat ; parade : la relecture sur la forme, la règle « s'adresse à tous », et la règle de sélection publiée.
- **Vote gonflé par une équipe de campagne** : jetons anonymes, niveaux, preuve de travail, résultats vérifiés à part ; la règle de sélection peut ne compter que les bulletins vérifiés (Q2).
- **Réponse qui n'est pas celle du candidat** : la provenance de chaque réponse est enregistrée et affichée (Q4).
- **Dernière ligne droite** : aucune mise à jour du comparateur de la veille du scrutin à la fermeture des bureaux.

## 6. Choix à valider (brainstorming du cahier fonctionnel, entrée du 9 novembre 2026)

- **Q1. Qui propose** : A, toute personne avec un compte, relecture sur la forme par l'équipe (recommandé) ; B, les questions sortent d'une délibération (DE-25) avec amendements (plus lent, plus riche) ; C, l'équipe présélectionne parmi les propositions (écarté : ce serait l'équipe qui choisit).
- **Q2. Règle de sélection** : A, les dix questions qui ont le plus de « Je veux qu'elle soit posée » chez les citoyens, deux au plus par thème, à égalité la plus ancienne (recommandé) ; B, la même chose mais en ne comptant que les bulletins vérifiés (plus robuste aux faux comptes, mais exclut les comptes non vérifiés) ; C, sans limite par thème.
- **Q3. Envoi et délai** : A, un envoi le 1er mars 2027 et 21 jours de délai, puis le même délai pour tout candidat ajouté (recommandé) ; B, envoi le jour de la publication de la liste officielle des candidats (même jour pour tous, mais délai très court avant le premier tour).
- **Q4. Comment un candidat répond** : A, par écrit, depuis l'adresse officielle de sa campagne, 1 500 caractères au plus par question ; l'équipe publie le texte tel quel, avec la date de réception et la mention « réponse transmise par l'équipe de campagne » ; l'original est archivé et horodaté (recommandé) ; B, depuis un compte certifié du candidat qui signe sa réponse (plus sûr, mais demande une certification des candidats comme celle des élus, DE-23) ; C, les deux, A d'abord.
- Puis **avocat** avant l'ouverture des propositions (cahier des charges, section 9.8) : L52-8, neutralité des questions, publication des réponses.
