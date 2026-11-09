# Tableau de bord fédérateur (PP-07) : conception, version 0.1

Rédigé le 9 novembre 2026 par le pôle « Voir » (« [Voir] Team Dev 2 »), à la demande du chef d'équipe, d'après le cahier fonctionnel (M34, PP-07 : « Chiffres de la force du mouvement (adhérents, comptes actifs, revendications, votes, appels, organisations, collectifs, signatures, engagements de candidats, argent collecté et redistribué), sur toute période », dépendance : statistiques agrégées S-76). Décision D-107. Branche `feature/voir-presence`, pas encore compilée.

## 1. Principes

1. **Des nombres, jamais une personne** : le serveur compte ce qu'il publie déjà (des contenus publics), sans traceur, sans cookie, sans adresse IP, sans identifiant.
2. **La liste fixe du cahier fonctionnel, toujours dans cet ordre.** Un chiffre qui n'a pas encore de source honnête est « non mesuré », jamais 0. Un 0 veut dire « mesuré, et rien encore ».
3. **Sur toute période** : chaque chiffre est donné mois par mois (ce que chaque mois a ajouté) ; le site additionne les mois de la période choisie (`?depuis=AAAA-MM&jusqua=AAAA-MM`, par un formulaire sans JavaScript).
4. **Le même fichier pour tous** : `GET /api/v1/tableau-de-bord` (JSON, `core/voir/Dashboard.kt`, format 1), réutilisable tel quel par la presse (PP-04), mis en cache 10 minutes.

## 2. Ce qui est compté aujourd'hui, et par qui

| Chiffre (`code`) | Source | État |
|---|---|---|
| `revendications` | revendications publiées (type `revendication`, après relecture) : une par adresse, au mois de sa première publication | Compté par « Voir » |
| `appels` | appels publiés de l'espace « Agir » (type `appel`) | Compté par « Voir » |
| `organisations` | fiches d'organisations publiées (type `organisation`) | Compté par « Voir » |
| `votes` | bulletins anonymes et votes publics des élus, sans les retraits, au mois de leur dernière réception (`UrneStore.votesByMonth`) | Compté par « Décider » depuis le 9 novembre 2026 (en production, quand les urnes sont configurées) |
| `signatures` | relevés officiels des pétitions relayées : chaque mois ajoute ce que son dernier relevé dépasse du plus haut relevé d'avant (`DeciderDashboardFigures.signatures`) | Compté par « Décider » depuis le 9 novembre 2026 |
| `engagements` | candidats qui ont signé au moins une mesure d'un Pacte, une fois par Pacte, au mois de leur première réponse signée (`DeciderDashboardFigures.engagements`) | Compté par « Décider » depuis le 9 novembre 2026 |
| `collectifs` | collectifs et salons créés | Non mesuré : à fournir par « Communauté » |
| `adherents` | nombre d'adhérents (PP-08, registre à part) | Non mesuré : après la création de l'association |
| `argent-collecte`, `argent-redistribue` | trésorerie publique (PP-06), en centimes | Non mesuré : après PP-06 |
| `comptes-actifs` | estimation sans traceur (S-76) | Non mesuré : après S-76 ; sinon, la page l'explique |

Le serveur ne publie un contenu de type 30023 qu'après sa relecture (revendications) ou par l'espace « Agir » (appels) : `PublicationPolicy`. Le décompte suit donc ce qui est réellement publié.

**Brancher un chiffre** (pour les autres pôles) : une `DashboardProvider` (`server/.../voir/DashboardService.kt`), qui renvoie ce que chaque mois `AAAA-MM` a ajouté, passée dans `extra` de `DashboardService`. C'est une ligne dans `Application.kt`, et rien d'autre ne change.

## 3. Pages

- **Site** : `/tableau-de-bord`, un tableau des totaux de la période et un tableau mois par mois, des liens vers le JSON, les données ouvertes et la transparence de PPF ; un lien dans le pied de page et depuis `/presse`.
- **Application** : un lien depuis « Espace presse », qui ouvre la page du site après la confirmation habituelle. Une page native, lue hors ligne, demandera une méthode de lecture dans le client commun du serveur (`RemoteEvents`, Directeur technique) : plus tard.

## 4. Données et menaces

- Aucune donnée personnelle : des nombres de contenus publics par mois.
- Gonfler un chiffre demanderait de faire publier de faux contenus. Or chacun passe par la relecture ou par les règles de l'espace « Agir », et chaque adresse est comptée une seule fois.
