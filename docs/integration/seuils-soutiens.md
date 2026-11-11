# Seuils de soutiens : quand PPF porte une revendication (proposition)

- Pôle « Décider », 7 novembre 2026. À valider par le chef d'équipe avant tout code.
- Décision de la propriétaire du 7 novembre 2026 (mots exacts relayés par le chef d'équipe) :
  - « Une mesure ne peut pas être adoptée par PPF, nous ne sommes qu'une association qui a pour mission de porter la voix des citoyens » ;
  - fonctionner « comme le système de pétitions de l'assemblée nationale » ;
  - un seuil x à l'échelle de la population du territoire.
- Elle a écrit 200 000 et 500 000 ; le Règlement de l'Assemblée nationale (article 148, vérifié le 7 novembre 2026) dit « plus de 100 000 » (alinéa 1) et « plus de 500 000 pétitionnaires domiciliés dans trente départements ou collectivités d'outre-mer au moins » (alinéa 6). La proposition suit le texte officiel ; le chef d'équipe lui signale l'écart.
- Données : référentiel des territoires de « Comprendre » (D-090), populations municipales de référence 2023 de l'Insee (France : 68 094 280 habitants, hors Mayotte et collectivités d'outre-mer).

## 1. Le vote

- Trois choix sur chaque revendication en débat et sur chaque proposition de la délibération : **pour**, **contre**, **blanc**. Le blanc est compté et affiché ; ne pas voter, c'est l'abstention, montrée par la participation (jetons distribués et bulletins reçus).
- PPF n'adopte rien. Le vote mesure le soutien ; au-delà d'un seuil, PPF **porte** la revendication à l'élu ou à l'échelon compétent.

## 2. La formule

Pour une revendication dont le territoire est T (son `ppf-territory-code`, D-090), de population P(T) :

- **Premier seuil (porter)** : S1(T) = max(F1, arrondi(100 000 × P(T) / P(France))). Atteint quand les **soutiens** (bulletins `pour` des citoyens, comptes vérifiés et non vérifiés, sans les élus) sont **plus de** S1(T), comme les « plus de 100 000 » de l'Assemblée.
- **Second seuil (exiger une réponse publique)** : S2(T) = max(F2, arrondi(500 000 × P(T) / P(France))). Atteint quand les soutiens sont plus de S2(T).
- Les seuils se calculent avec le chiffre du référentiel au jour de l'ouverture de l'urne et restent fixes jusqu'à sa clôture (même règle pour tous pendant tout le vote).
- `contre` et `blanc` ne retirent rien aux soutiens. Ils sont affichés à côté, et transmis avec la revendication quand elle est portée.

## 3. Le plancher des petites communes

Sans plancher, un village de 500 habitants atteindrait le seuil avec un seul soutien. Proposition : F1 = 25 et F2 = 100.
- 25, parce qu'une seule famille ne suffit pas, et que c'est bien au-dessus du seuil d'anonymat de 5 bulletins.
- 100, parce qu'au second seuil on demande une réponse publique.

Le plancher joue en dessous d'environ 17 000 habitants pour S1, et d'environ 13 600 pour S2.

## 4. L'équivalent des « 30 départements »

L'Assemblée vérifie le domicile des signataires (identification par FranceConnect). PPF, par construction, **ne sait jamais où habite un votant** : le bulletin ne porte ni lieu ni compte (D-110). Une condition de répartition ne pourrait donc ni se vérifier ni se déclarer sans casser l'anonymat ; une urne par département pour chaque revendication nationale multiplierait par 106 la charge des jetons.

Proposition : pas de condition de répartition. Quand la validation en personne existera (étape 5), le second seuil pourrait demander en plus une part minimale de comptes vérifiés.

## 5. Exemples (populations du référentiel)

| Territoire | Population | Porter : plus de | Exiger une réponse : plus de |
|---|---|---|---|
| Village de 500 habitants | 500 | 25 (plancher) | 100 (plancher) |
| Ville de 50 000 habitants | 50 000 | 73 | 367 |
| Lozère | 76 486 | 112 | 562 |
| Hérault | 1 230 289 | 1 807 | 9 034 |
| Rhône (département et métropole) | 1 914 667 | 2 812 | 14 059 |
| Auvergne-Rhône-Alpes | 8 205 557 | 12 050 | 60 251 |
| France | 68 094 280 | 100 000 | 500 000 |

## 6. Ce que « porter » veut dire, selon l'échelle

| Territoire | Premier seuil : PPF transmet la revendication, avec les résultats, et publie l'envoi | Second seuil : PPF demande publiquement une réponse |
|---|---|---|
| Commune, arrondissement | Maire et conseil municipal | Proposition de lancer la pétition locale officielle (CGCT, article L1112-16 : un dixième des électeurs de la commune), avec les citoyens qui le veulent (DE-26) |
| Intercommunalité | Président et conseil communautaire | Pétition locale officielle (CGCT, L5211-49) |
| Département | Président du conseil départemental ; députés et sénateurs du département | Pétition locale officielle (un vingtième des électeurs, L1112-16) |
| Région | Président du conseil régional ; parlementaires de la région | Pétition locale officielle |
| France | Gouvernement, présidents de groupe, députés et sénateurs | Relais vers une pétition officielle de l'Assemblée ou du Sénat (M10) |

- Le destinataire exact dépend du sujet. L'équipe le choisit avec « Qui décide de ça ? » et l'annuaire des élus (D-047), et la page de la revendication le dit (envoi, date, réponse ou absence de réponse).
- La revendication porte un `ppf-territory-code`. Le formulaire choisira le territoire dans le référentiel (recherche sur l'appareil, D-090) ; sans code, elle compte comme nationale.

## 7. La liste des mesures de la Constitution Citoyenne et Démocratique dans ce modèle

- Les mesures de la Constitution Citoyenne et Démocratique sont les propositions de la délibération (DE-25), toutes de territoire `fr`, votées pour, contre ou blanc (3, 2 et 2 semaines, décision de la propriétaire).
- Proposition : la Constitution Citoyenne et Démocratique rassemble les 5 à 10 mesures qui ont le plus de soutiens parmi celles qui ont plus de `pour` que de `contre`. Chacune est affichée avec ses soutiens et le seuil national. Le seuil de 100 000 n'est pas exigé pour entrer dans la Constitution Citoyenne et Démocratique : la Constitution Citoyenne et Démocratique est elle-même le moyen de porter la voix des citoyens aux candidats, et l'audience de PPF ne l'atteindra pas avant janvier 2027. Une mesure qui le dépasse le dit.
- Variante : n'y faire entrer que les mesures au-dessus du seuil national. La Constitution Citoyenne et Démocratique serait probablement vide en 2027.

## 8. Points de vigilance

- Les comptes non vérifiés peuvent être créés en masse. Avant de porter une revendication, l'équipe regarde le journal public de l'urne : vague d'inscriptions, pic de bulletins (D-110, section 8). Le constat est publié.
- Les élus votent à part (D-112) et ne comptent jamais dans les soutiens.
- Les seuils et les planchers sont des réglages publics : chacun peut refaire le calcul.

## 9. Questions (recommandation en premier)

- **Q1. Planchers.** A : 25 et 100. B : 10 et 50. C : le dixième des électeurs inscrits pour une commune (la règle légale des pétitions locales), beaucoup plus haut.
- **Q2. Répartition.** A : aucune condition, en le disant (anonymat). B : une part de comptes vérifiés au second seuil dès que la validation existe.
- **Q3. La Constitution Citoyenne et Démocratique.** A : les 5 à 10 mesures les plus soutenues, avec plus de `pour` que de `contre`. B : seulement celles au-dessus du seuil national.
- **Q4. Les `contre`.** A : affichés et transmis, sans effet sur le seuil (comme les pétitions de l'Assemblée, qui n'ont pas de « contre »). B : ne porter que si les `pour` dépassent aussi les `contre`.

## Réalisation du second seuil (8 novembre 2026)

- Au second seuil, l'équipe dépose la pétition sur le site officiel, puis relie son adresse à la revendication dans la file `decider.petitions` du back-office (administrateurs) ; la pétition relayée (`ppf-type` = `petition`, lien de rôle `revendication`) est signée par la plateforme pour l'usage `decider.pacte` après l'approbation d'un membre sur la page du service de signature, puis publiée telle quelle.
- La carte montre le lien et épingle la revendication tant que la pétition est ouverte, dès que les applications reconnaissent les clés de la plateforme (étape 6 du contrat du service de signature).
