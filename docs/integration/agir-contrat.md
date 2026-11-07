# Contrat commun de l'espace « Agir », version 1

Rédigé le 6 novembre 2026 par la session principale « Agir » (branche `feature/agir`). Il fixe ce que les blocs développés en parallèle (agenda, appels, annuaire et accueil d'Agir, serveur) doivent partager pour se fusionner sans conflit. Le code correspondant est dans `shared/core/.../core/agir/AgirFormats.kt` et `shared/core/.../core/publication/HeldContents.kt`. Toute divergence nécessaire est signalée dans la note d'intégration du bloc, jamais tranchée seul.

## 1. Périmètre de la première vague

Ce qui est spécifié et codable maintenant (cahier fonctionnel M1, M3, M12 ; inventaire B6 « Agir », liste validée par la propriétaire le 4 novembre 2026 ; points d'attention 6, 12, 14, 26 à 32 décidés) :

| Bloc | Fonctionnalités | Branche de l'agent |
|---|---|---|
| Agenda des mobilisations | M1, AG-01 (liste, filtres, hors ligne), AG-02 (proposer un événement, relecture), AG-19 et AG-21 (organisateurs, doublons), export iCal | worktree de l'agent « agenda » |
| Appels | AP-01 à AP-10, dans les limites de la section 6 | worktree de l'agent « appels » |
| Annuaire et accueil d'Agir | M3, AG-03, AG-17, AG-22 (suivre, liste sur l'appareil), écran d'accueil de l'espace Agir | worktree de l'agent « organisations » |
| Serveur | règles de publication, relecture avant publication, agenda et flux iCal, organisations vérifiées | worktree de l'agent « serveur » |

Hors de cette vague (décision ou avis en attente, ou vague suivante) : carte et tuiles (choix de la bibliothèque de rendu, tuiles auto-hébergées), mode manifestation et bouton AIDE (écart de navigation à trancher par la propriétaire), témoignages (horodatage à choisir, clé de l'équipe de modération), secours, sécurité et avocats en manifestation (AG-24 à AG-46, conception et avis d'un avocat avant le lancement), solidarité et caisses de grève (À étudier), boycotts (Idée, point juridique), lanceurs d'alerte (avocat), je veux aider, matériel militant, messagerie hors ligne (phase 4). Décider ensemble dans un collectif (AG-51) dépend de la méthode « une personne, une voix ».

## 2. Mobilisation de l'agenda (kind 31923, NIP-52)

Événement de calendrier daté (NIP-52, « time-based calendar event »), adressable par `31923:<clé>:<d>`.

| Étiquette | Obligatoire | Contenu |
|---|---|---|
| `d` | oui | identifiant stable, 1 à 64 caractères `a-z0-9._~-` |
| `title` | oui | 1 à 140 caractères |
| `start` | oui | début, secondes depuis 1970 |
| `end` | non | fin, strictement après `start` |
| `start_tzid` | non | `Europe/Paris` |
| `location` | oui | lieu en clair (adresse, place, ville) |
| `g` | non | géohash (9 caractères au plus), seulement si l'auteur a placé le point lui-même ; aucune géolocalisation par un service tiers |
| `ppf-event-type` | oui | `manifestation`, `blocage`, `rassemblement`, `greve`, `assemblee-generale`, `reunion`, `autre` |
| `ppf-event-status` | non | `prevu` (par défaut), `annule`, `reporte` |
| `ppf-territory` | oui | commune, département ou `France`, comme les salons |
| `ppf-charter` | oui | `1.0` |
| `ppf-declaration` | non | pour une manifestation : `faite`, `en-cours`, `non-faite` |
| `p` | non | `["p", "<clé hex>", "", "organizer"]` pour chaque organisation organisatrice (AG-21) |
| `a` | non | adresse de l'appel dont l'événement est issu (AP-07) |
| `r` | non | lien |
| `summary` | non | résumé court |

`content` : description en texte (8000 caractères au plus). Un événement corrigé est republié sous la même adresse (version plus récente). Annuler = republier avec `ppf-event-status` = `annule`.

## 3. Appel (kind 30023, `ppf-type` = `appel`)

Même forme que les revendications (contenu adressable `30023:<clé>:<d>`), avec :

- communes : `d`, `title`, `ppf-type` = `appel`, `ppf-call-type` (liste `AgirFormats.CALL_TYPES`), `ppf-territory`, `ppf-charter` = `1.0`, `published_at` ; facultatives : `summary`, `ppf-goal` (entier positif), `ppf-deadline` (secondes), `location`, `g`, `r`, `ppf-call-status` (`ouvert` par défaut, `clos`), `ppf-report` (bilan à la clôture, AP-09, 2000 caractères au plus) ;
- obligatoires selon le type (AP-10) : `AgirFormats.CALL_REQUIRED_TAGS` (« `location|r` » veut dire l'une ou l'autre) ;
- `content` : texte de l'appel (8000 caractères au plus) ; pour un appel à témoins, la description factuelle, sans accuser nommément personne.

## 4. Organisation de l'annuaire (kind 30023, `ppf-type` = `organisation`)

Publiée par une clé de l'équipe éditoriale (`PPF_EDITOR_KEYS` sur le serveur, `EditorialTrust` sur l'appareil) : `d`, `title` (nom), `summary`, `ppf-org-type` (`AgirFormats.ORG_TYPES`), `ppf-territory`, `ppf-verified` (date de vérification `AAAA-MM-JJ`), `published_at` ; facultatives : `p` (clé de l'organisation si elle a un compte), `r` (site, plusieurs possibles), `ppf-contact-phone`, `ppf-contact-email`, `ppf-contact-address` (coordonnées publiques de l'organisation, jamais celles d'une personne), `g`. `content` : présentation (Markdown simple, comme les fiches).

**Organisation vérifiée** : une clé qui figure en `p` d'une fiche d'organisation signée par une clé éditoriale de confiance. C'est la même règle sur l'appareil et sur le serveur. Version 2 (7 novembre 2026) : étiquette facultative `ppf-org-status` = `verifiee` (défaut, absente) ou `revoquee` ; quand la fiche la plus récente qui nomme une clé est `revoquee`, cette clé n'est plus vérifiée (`docs/FORMATS.md`, section 4 sexies). Les écrans de fiches pratiques ne doivent pas afficher ces fiches (filtrer sur `ppf-type`).

## 5. Publication et relecture (serveur)

Tout passe par `POST /api/v1/events` (port de publication unique).

| Contenu | Clé éditoriale ou organisation vérifiée | Autre compte |
|---|---|---|
| Mobilisation (31923) | publiée (201) | mise en attente de relecture (202) |
| Appel, sauf ci-dessous | publié (201) | mise en attente de relecture (202) jusqu'à l'arrivée des comptes confirmés (S-91) |
| Appel à témoins (`temoins`) | toujours mis en attente (202) | toujours mis en attente (202) |
| Appel à collecter des fonds (`collecte`) | organisations vérifiées seulement (201) | refusé (`403 not_allowed`) |
| Organisation (30023, `ppf-type` = `organisation`) | clé éditoriale seulement | refusé (`403 not_allowed`) |

Erreurs de forme : `400` avec `missing_title`, `missing_start`, `bad_end`, `missing_location`, `bad_event_type`, `bad_call_type`, `missing_territory`, `missing_charter`, `missing_field` (message : nom de l'étiquette manquante), `too_long`.

Suivi par l'auteur : `GET /api/v1/held?id=<hex>&id=<hex>` (1 à 20 identifiants) répond `[{"id","status":"pending|approved|rejected","received_at","decided_at","reason"}]`, sans cache (`Cache-Control: no-store`), sans jamais renvoyer la clé ni le contenu. Côté appareil : `HeldStatusSource.heldStatuses(ids)`, déjà mis en œuvre par `ServerPublicationPort`. Un contenu approuvé est publié tel qu'il a été signé. Rien de la requête n'est conservé (ni adresse, ni en-tête) ; un contenu décidé est effacé de la file 30 jours après la décision.

Lecture : l'appareil synchronise par l'API générique (`GET /api/v1/events?kind=31923`, `kind=30023&tag=ppf-type:appel`, `tag=ppf-type:organisation`). Le serveur ajoute `GET /api/v1/agenda?from=&to=&territory=&type=&organizer=&limit=` (mobilisations triées par date de début, pour le site et les filtres par date) et le flux `GET /api/v1/agenda.ics` (iCalendar, mêmes filtres).

## 6. Règles communes à tous les blocs

- **Vie privée** : répondre à un appel (« J'y serai », « Je soutiens ») ou à un événement reste **sur l'appareil** ; aucun compteur public tant que la méthode de comptage anonyme n'est pas validée (comme les réactions du fil, option C en attendant B) ; aucune liste publique de participants, jamais. Les organisations suivies restent sur l'appareil (`FollowStore`). Rien de sensible dans les adresses web (pas de territoire ni de filtre personnel en paramètre envoyé au serveur sans nécessité : les filtres « près de chez moi » se calculent sur l'appareil).
- **Contenus d'exemple** : tous marqués [EXEMPLE], fictifs (aucune organisation réelle, aucune personne réelle, numéros de téléphone fictifs dans une tranche réservée à la fiction, vérifiée et citée), signés par `tools/content-signer` depuis `content/agenda/`, `content/appels/`, `content/organisations/` vers des fichiers JSON séparés.
- **Fichiers partagés** : textes dans un fichier de ressources propre au bloc (`strings_agenda.xml`, `strings_appels.xml`, `strings_organisations.xml`, `strings_agir.xml`) ; `strings.xml` n'est pas modifié. `AppDependencies` : seulement des paramètres facultatifs ajoutés à la fin. `AdaptiveShell.kt` : le moins de lignes possible. Nouvelles icônes : SVG ajouté à `tools/icons/lucide/` puis `python -I tools/icons/generate_icons.py` (la session principale régénère après fusion). `ArticleType` : une ligne par nouveau type.
- **Base locale** : préférer la table d'événements existante (`LocalEventStore`) pour tout contenu signé ; une migration SQLDelight seulement pour un état propre à l'appareil, numérotée `6.sqm` (la session principale renumérote à la fusion). Serveur : une seule migration Flyway pour le bloc serveur, `V5__agir.sql` (renumérotée à la fusion si besoin).
- **Documents** : aucun agent ne modifie `CHANGELOG.md`, `ROADMAP.md`, `DECISIONS.md`, `FORMATS.md`, `REGISTRE_DONNEES.md`, `THREAT_MODEL.md`, `GUIDE_PROPRIETAIRE.md`, les cahiers ni les brainstormings. Chacun écrit `docs/integration/agir-<bloc>.md` (fait, fichiers, formats, décisions proposées, lignes du registre des données, menaces, questions pour la propriétaire, comment tester) ; la session principale fusionne et met tout à jour.
