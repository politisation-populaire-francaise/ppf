# Délibération sur les mesures du Pacte 2027 : brouillon NON PUBLIÉ

- Pôle « Décider », 11 novembre 2026. Proposition de dates soumise à la propriétaire par le chef d'équipe ; rien n'est signé ni publié tant qu'elle n'a pas répondu.
- Format : `docs/integration/deliberation.md` (section 2) et `core/decider/Deliberation.kt`. L'application et le site lisent la délibération dès qu'elle est publiée par une clé de l'équipe (`DeliberationBoard`).

## Dates proposées (durées de la conception, validées le 7 novembre : 3, 2 et 2 semaines)

| Étape | Début (heure de Paris) | Fin (heure de Paris) |
|---|---|---|
| Propositions | mardi 1er décembre 2026, 0 h | mardi 22 décembre 2026, 0 h |
| Amendements | mardi 22 décembre 2026, 0 h | mardi 5 janvier 2027, 0 h |
| Vote final | mardi 5 janvier 2027, 0 h | mardi 19 janvier 2027, 0 h |

Ensuite : envoi du Pacte à tous les candidats le même jour, avec le même délai de réponse (DE-08), bien avant le premier tour du 18 avril 2027.

## L'événement à publier (à signer par la clé de l'équipe, une fois les dates validées)

Type 30023, `ppf-type` = `deliberation` ; secondes depuis 1970, heure de Paris (UTC+1 en hiver) :

```
["d", "pacte-presidentielle-2027"]
["title", "Les mesures du Pacte démocratique pour la présidentielle de 2027"]
["ppf-subject", "Proposer, amender et voter les mesures que PPF enverra à tous les candidats."]
["ppf-phase", "propositions", "1796079600", "1797894000"]
["ppf-phase", "amendements", "1797894000", "1799103600"]
["ppf-phase", "vote", "1799103600", "1800313200"]
["ppf-measures", "5", "10"]
```

Valeurs : 1er décembre 2026 0 h (Paris) = 1796079600 ; 22 décembre = 1797894000 ; 5 janvier 2027 = 1799103600 ; 19 janvier = 1800313200. À recalculer si les dates changent.

La proposition de départ de PPF (`ppf-origin` = `depart`) reprend les mesures du Pacte proposé (`content/decider/pacte`), une proposition par mesure, signée par la même clé.
