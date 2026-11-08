# Présence et délégations (VO-35) : conception et état, version 1.0

Rédigé le 8 novembre 2026 par le pôle « Voir » (« [Voir] Team Dev 2 »), d'après le cahier fonctionnel (M20, VO-35 : « Sur les grands scrutins, présence et délégations de vote par groupe et par chambre, avec la définition affichée ; aucun classement d'élus ») et la règle « Traitement identique » (M17). Branche `feature/voir-presence` (avec VO-34), pas encore compilée.

## 1. Définitions (affichées sur la page)

- **Grand scrutin** : un scrutin public solennel de l'Assemblée nationale (type `SPS` des données ouvertes). La Conférence des présidents le décide, en général pour le vote sur l'ensemble d'un texte important, à un moment choisi « afin de favoriser la présence du plus grand nombre de députés » (fiche de synthèse n° 56 de l'Assemblée nationale, juillet 2024 ; Règlement de l'Assemblée nationale, article 65-1).
- **Exclus** : les motions de censure (seuls les votes favorables y sont recensés, Constitution, article 49) et les scrutins publics ordinaires.
- **Quatre issues**, pour chaque député en fonction aujourd'hui et chaque scrutin solennel de son mandat présent sur l'appareil, comptées une seule fois : a voté lui-même (pour, contre, abstention) ; a voté par délégation (un député ne peut recevoir qu'une seule délégation, fiche n° 56 et Constitution, article 27) ; non-votant (par exemple le président de séance, avec la cause publiée) ; aucun vote enregistré (absent, ou présent sans voter : les données ouvertes ne le disent pas). Les quatre font toujours le total des votes attendus.
- **Par groupe** (groupes d'aujourd'hui), dans l'ordre alphabétique, jamais par élu, jamais classés.

## 2. Limites dites sur la page

Groupes d'aujourd'hui ; les députés qui ont quitté l'Assemblée ne sont plus dans la liste ; ne mesure ni le travail en commission ni les autres activités ; Sénat et Parlement européen : les données importées ne distinguent pas de grands scrutins, rien n'est affiché plutôt qu'une estimation.

## 3. Code

- Cœur : `core/voir/parlement/PresenceAndDelegations.kt` (`PresenceAndDelegations.of(catalog, chamber)`, `GroupPresence`, `ChamberPresence`), test commun `PresenceAndDelegationsTest`.
- Application : `ParliamentPage.Presence`, `ui/voir/parlement/PresencePage.kt` (carte sur la liste du Parlement, définition, tableau lu par les lecteurs d'écran une phrase par groupe), `strings_voir_presence.xml`, test `PresenceScreenTest`.
- Aucune donnée nouvelle : tout est calculé sur l'appareil à partir des paquets déjà téléchargés, rien n'est envoyé ni gardé.

## 4. Reste à faire

- Le site (pages du Parlement sur le site, quand elles existeront).
- Le Sénat et le Parlement européen, quand leurs votes et un équivalent des grands scrutins seront importés.
