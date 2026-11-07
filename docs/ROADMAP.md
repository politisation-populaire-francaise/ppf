# Feuille de route

Suivi de l'avancement du planning du cahier des charges (section 10.2). Mise à jour à chaque étape livrée.

Légende : ✅ fait, 🔄 en cours, ⏳ à faire, ⛔ bloqué (décision ou action en attente).

## Phase 0 : fondations

| Élément | État | Détail |
|---|---|---|
| Vérification de l'environnement de la propriétaire | ✅ | 5 novembre 2026 : Windows 11, Git, JDK 21, SDK Android, GitHub CLI installés. |
| Dépôt GitHub public, branche principale protégée | 🔄 | Décision du 5 novembre 2026 : public dès le premier commit. Création en cours. |
| Squelette Kotlin Multiplatform et Compose Multiplatform (Android, ordinateur, iOS déclaré) | 🔄 | Modules `shared/core`, `shared/ui`, `android`, `desktop`, `ios` (non compilé). |
| Premier écran adaptatif (téléphone, tablette, ordinateur) | 🔄 | Coquille de navigation adaptée aux trois largeurs, tests automatiques des trois dispositions. |
| Squelette du serveur Ktor avec `/health` | 🔄 | Module `server`, en-têtes de sécurité, aucune journalisation d'adresse IP. |
| Squelette du site web (Ktor + kotlinx.html) | 🔄 | Module `web`, lisible sans JavaScript, manifeste PWA. |
| `/infra` : production et préproduction séparées | 🔄 | Deux projets Compose, proxy Caddy, modèle WireGuard, limites mémoire. |
| Intégration continue (APK, bureau, tests, secrets, traceurs) | 🔄 | GitHub Actions : Linux et Windows, gitleaks, vérification des tiers, Dependabot. Depuis le 6 novembre 2026, l'APK publié est la version d'essai non débogable, numérotée à chaque commit, dont la signature de test est vérifiée (D-031). |
| Distribution des versions de test Android sans avertissement | ⛔ | Guide d'installation pour les Samsung en place ; la suppression des avertissements passe par Google Play (25 dollars une fois), en attente de la propriétaire (brainstorming du cahier des charges, entrée du 2026-11-06). |
| Documents techniques | 🔄 | ROADMAP, DECISIONS, THREAT_MODEL, REGISTRE_DONNEES, GUIDE_PROPRIETAIRE, CHANGELOG. |
| Guide d'achat des noms de domaine | ✅ | `docs/guides/achat-domaines-hostinger.md` ; six domaines achetés le 5 novembre 2026. |
| Guide du serveur KVM 1 (premier déploiement) | 🔄 | `docs/guides/serveur-hostinger-kvm1.md`, première version. |
| Déploiement d'un squelette en préproduction | ⛔ | Attend la location du serveur, au moment du premier déploiement. |
| Modèles juridiques (mentions légales, confidentialité, CGU, charte, DSA) | ⏳ | Brouillons à produire, marqués « À FAIRE RELIRE PAR UN AVOCAT ». |
| Découpage de la phase 1 en MVP | ⏳ | Après la version 2.0 du cahier fonctionnel (brainstorming). |

## Phase 1 : fondations techniques obligatoires (cahier des charges, section 3.2)

Commencées le 5 novembre 2026, en parallèle de la fin de la phase 0, parce que tout le reste en dépend.

| Élément | État | Détail |
|---|---|---|
| Identités par paires de clés (M16) | 🔄 | Module commun : génération, clés « npub » et « nsec », phrase de récupération de 12 mots (français par défaut), tests sur les vecteurs officiels. Écran « Mon espace » : création avec vérification de trois mots, restauration par phrase ou clé, affichage des secrets après avertissement, déconnexion avec effacement. Stockage : Android Keystore ; fichier privé sur ordinateur. Reste : trousseau iOS, chiffrement par code sur ordinateur et verrouillage (S-43), plusieurs identités, transfert par QR code (S-12). |
| Signature des contenus | 🔄 | Format d'événement NIP-01, signature et vérification testées. Reste : service de signature séparé pour la clé de la plateforme, rotation et révocation. |
| Port de publication unique | 🔄 | Interface, mise en œuvre en mémoire, adaptateur vers le serveur (client HTTP), file d'attente hors ligne et synchronisation. Reste : anti-spam (limitation de débit, preuve de travail), règles de publication par rôle. |
| Identifiants stables et export JSON | 🔄 | `docs/FORMATS.md`, version 1, avec l'API du serveur. |
| Archivage horodaté des engagements | ⏳ | Empreinte SHA-256 disponible ; horodatage (OpenTimestamps ou RFC 3161) à choisir. |
| Offline-first : base locale | 🔄 | SQLDelight (D-020) : table des événements et de leurs étiquettes, remplacement des versions, publications en attente. Reste : branchement dans les écrans, purge et taille maximale du cache. |
| Serveur : base PostgreSQL et API des événements | 🔄 | JDBC, HikariCP, Flyway (D-019) ; `POST` et `GET /api/v1/events` (D-021), tests sur PostgreSQL 17 réel ; règle de publication (contenus éditoriaux réservés aux clés d'éditeurs) ; serveur de développement avec PostgreSQL embarqué ; filtres par étiquette, limitation de débit par clé et globale, relais d'enveloppes et salons (7 novembre). Reste : déploiement en préproduction, preuve de travail, modération. |

## Phase 1 : premiers modules

| Élément | État | Détail |
|---|---|---|
| Fil social (M18) : écran par défaut, publier, suivre, réagir, commenter, suggestions | 🔄 | Première version le 6 novembre 2026 : fil chronologique, publication de notes courtes par un compte (envoi au serveur dès qu'il répond), réactions locales [EXEMPLE], bandeau EN DIRECT, vues parcourues au doigt (Tout, Suivis, Comptes PPF, Revendications, Mes publications), suivre des comptes avec une liste gardée sur l'appareil (7 novembre). Reste : cadrage vie privée des réactions et des suggestions (brainstorming), commentaires, partage, vues Mon cercle, Collectifs, Près de moi, images sans métadonnées, modération, curseur de synchronisation par source. |
| Messagerie (M14) : privé, collectifs, flux | 🔄 | Protocole D-027, conservation et salons D-028. Fait le 6 novembre 2026 au soir : conversations privées de bout en bout (liste, fil, nouvelle conversation, hors ligne, flux des comptes suivis), salons des collectifs (recherche, rejoindre, lire, écrire, créer avec charte et territoire, doublons refusés), conservation réglable dans Mon espace, serveur relais complet. Reste : relier l'application Android à un serveur (préproduction), notifications (UnifiedPush et ntfy), vérification des contacts par QR code (S-128), signalement d'un message (S-127), suppression d'une conversation, rôles et modération des salons (NIP-29, 9000 à 9022), MLS, chiffrement de la base locale sur ordinateur (S-43), preuve de travail. |
| Design et navigation (décisions du 6 novembre 2026, D-023 et D-025) | 🔄 | Identité visuelle des maquettes de la propriétaire appliquée (couleurs, Atkinson Hyperlegible, icônes Lucide, composants) sur téléphone, tablette et ordinateur, en clair et en sombre. Reste : son avis sur le rendu réel, barre du bas ou boutons flottants (brainstorming), bouton AIDE, illustrations propres. |
| Fiches pratiques hors ligne (M6, CO-03, S-26) | 🔄 | Lecture dans « Comprendre » : trois fiches [EXEMPLE] signées et intégrées à l'application, liste et détail adaptatifs, origine et date affichées (D-022). Reste : contenus réels relus par un avocat, synchronisation depuis le serveur en production, choix des rubriques à télécharger, espace occupé. |
| « Comprendre » : accueil et parcours « Comprendre en 5 minutes » (M13.1, CO-02, D-033) | 🔄 | 6 novembre 2026 : accueil de l'espace (parcours, fiches pratiques, puis dossiers et décryptages quand il y en a), lecteur de parcours étape par étape, quiz avec explication sourcée, « Et maintenant ? », progression sur l'appareil seulement et effaçable ; quatre parcours [EXEMPLE] (loi, 49.3 et motion de censure, dissolution, présidentielle). 7 novembre 2026 (D-038) : décryptages avec l'échelle des cinq verdicts et la page « Notre méthode », histoire des luttes avec frise et revendications obtenues, « Qui décide de ça ? » avec l'encart des élus concernés, objections et réponses sourcées (pluralisme), huit parcours en tout. Reste : relecture de tous les contenus par l'équipe, noms des élus locaux (annuaire officiel des élus, à décider), « Demander une vérification » (CO-18), glossaire (CO-14), contenus sur le site sans JavaScript, version « Facile à lire ». |
| CMS éditorial (S-70, S-71) | ⏳ | Pour l'instant : fichiers Markdown dans `content/` signés par `tools/content-signer`. À venir : rédaction, relecture, validation, publication dans l'application pour les éditeurs. |
| Carte et agenda (M1) | ⏳ | Dépend du choix des tuiles de carte (coût éventuel à soumettre à la propriétaire). |
| Fil d'actualité (M6) | ⏳ | |
| Annuaire des organisations (M3) | ⏳ | |
| Observatoire (M2) et espace « Voir » | 🔄 | Fait le 6 novembre 2026 (D-034) : espace « Voir » complet sur les trois axes avec contenus [EXEMPLE] : chiffres clés et sources côte à côte, fiche d'indicateur avec graphique et tableau, chronologie (M4), observatoire des libertés et contre-pouvoirs, fiches des responsables publics (votes, promesses, discours et votes, affaires avec statut de procédure, déclarations contredites, intérêts, influence, argent public), législomètre, mouvements dans le monde (M9), dossiers, droit de réponse, journal des corrections, suivis sur l'appareil. Reste : contenus réels rédigés et relus par l'équipe (double validation, avocat), éditeur d'indicateurs sans code pour l'équipe (CMS), signalements chiffrés des citoyens (VO-03, avec la méthode anti-fraude), indice de gravité (grille à valider), témoignages publiés (M5, avec la modération), import des données ouvertes de l'Assemblée et du Sénat (phase 5), formulaire de droit de réponse (serveur), cartes. |
| Signalement et modération (M15) | ⏳ | |
| Mur des revendications (M11) | 🔄 | Mur en cards, détail et argumentaire, vue dans le fil (6 novembre 2026) ; vues personnalisables, filtres et tri (D-029) ; formulaire de proposition avec vérifications automatiques pendant la saisie, relecture avant publication sur le serveur, suivi par l'auteur (D-030) ; huit revendications [EXEMPLE]. Reste : écran de relecture de l'équipe (avec M15), corpus juridique, IA (hébergement à décider), preuve de travail avant l'ouverture publique, statut posé par l'équipe, vote et débat (méthode « une personne, une voix » à valider), fusion des doublons. |

## Phases suivantes (résumé, à découper en MVP)

- **Phase 1** : carte et agenda, observatoire, fiches pratiques hors ligne, annuaire, fil d'actualité, CMS, comptes pseudonymes, modération, signature des contenus ; mur des revendications, puis Pacte démocratique pour le début de 2027.
- **Phase 2** : témoignages, documentation et chronologie, fiches des responsables, décryptage, pétitions, modération assistée par IA, comparateur présidentielle 2027, kit du candidat citoyen.
- **Phase 3** : Nostr, miroirs statiques, adresse .onion, Tor dans l'application ; votations et assemblées citoyennes.
- **Phase 4** : messagerie hors ligne bitchat.
- **Phase 5** : votes des parlementaires sur 50 ans, international, éducation et bibliothèque complètes.
- **Phase 6** : désignation citoyenne d'un candidat, après avis d'un avocat.
- **iOS, hors phase** : compilation, tests et publication dès l'ouverture du compte Apple Developer.

## Décisions en attente qui bloquent une étape

- Hébergement du modèle d'IA de modération et des vérifications des revendications (et, avec un prestataire, extension de la section 5.6 aux contenus envoyés pour publication).
- Liste des thèmes du mur des revendications.
- Barre du bas ou boutons flottants « Carte » et « AIDE » ; spécification du bouton AIDE.
- Méthode « une personne, une voix » (mur des revendications, phase 1).
- Prestataire de paiement (dons, adhésions).
- Navigation détaillée et briques de contenu communes (découpage en MVP).
- Annuaire officiel des élus pour nommer les élus locaux dans « Qui décide de ça ? » (brainstorming du cahier des charges, entrée du 2026-11-07).
