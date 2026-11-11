# Textes légaux de la bêta (brouillons)

**PROJET, À FAIRE RELIRE PAR UN AVOCAT.** Brouillons rédigés par l'IA le 11 novembre 2026, pour la bêta sur invitation (feuille de route du MVP 1, jalon 5 ; cahier des charges, section 9.1). Ils ne décrivent que ce que l'application et le serveur font aujourd'hui (registre des données lu le 11 novembre 2026) et sont à mettre à jour à chaque nouvelle donnée ou fonction.

| Fichier | Page prévue sur le site |
|---|---|
| `mentions-legales.md` | `/mentions-legales` |
| `politique-de-confidentialite.md` | `/confidentialite` |
| `conditions-d-utilisation.md` | `/conditions-utilisation` |
| `charte-de-contenu.md` | `/charte` |
| `procedure-de-signalement.md` | `/signaler` |

Ces fichiers sont hors de la signature des contenus. Au moment de la compilation, ils sont copiés dans les ressources du site (`web/build.gradle.kts`, dossier `legal/`) et servis par `web/.../LegalPages.kt`, avec un lien dans le pied de page de chaque page (emplacement convenu avec la session « Mise en ligne » le 11 novembre 2026). Format : un en-tête (`title`, `summary`, `origin`, `updated_at`) puis le Markdown du rendu de « Comprendre » (titres, listes, citations, liens ; ni tableau ni code).

## Démarrage refusé en production tant qu'un blanc reste

Un blanc s'écrit `[À COMPLÉTER : …]`, `[À REMPLIR : …]`, `[À CONFIRMER …]` ou `[À VÉRIFIER …]`. Tant qu'il en reste un dans ces textes, le serveur refuse de démarrer en production (`LegalPages.requireComplete`, appelé au démarrage) ; il démarre en développement et en préproduction. Les questions pour l'avocat (`[À FAIRE PRÉCISER PAR L'AVOCAT …]`) ne bloquent pas : la bêta ouvre avant sa relecture (feuille de route du MVP 1, décision 1). Le contact est en attente de la propriétaire (adresse `contact@` demandée) ; l'adresse du compte GitHub ne doit pas y être publiée.

## Réponses de la propriétaire (11 novembre 2026, relayées par le chef d'équipe)

- Rien n'est publié au public tant que l'association n'est pas déclarée : pas d'anonymat d'éditeur à traiter ; l'éditeur est « à compléter à la déclaration ».
- Directrice de la publication : la propriétaire pour l'instant ; son nom n'est pas écrit (« à compléter »).
- Contact officiel : contact@politisation-populaire-francaise.org (aussi pour les signalements et les demandes sur les données). L'adresse du compte GitHub n'est jamais publiée.
- Suppression d'un contenu ou d'un compte pendant la bêta : dans les 72 heures.
- Durée de la bêta et sort des données à sa fin : pas encore décidés (« à préciser »).
- Délégué à la protection des données : désigné au sein de l'association ou vu avec l'avocat.

## Blancs restants (le serveur refuse de démarrer en production tant qu'il en reste un)

1. Éditeur et responsable du traitement : dénomination, siège, numéro RNA, téléphone, à la déclaration de l'association.
2. Nom de la directrice de la publication.
3. Durée de la bêta et sort des données à sa fin.
4. Confirmer au contrat Hostinger International Limited (Larnaca, Chypre) et son téléphone.

## À confirmer par l'équipe technique

- Aucun cookie sur le site public, y compris la page protégée de la préproduction.
- Outil de retrait et d'effacement manuel d'un contenu ou d'un compte côté serveur.
- La page `/securite` dit que les contenus des utilisateurs se signalent « depuis le contenu lui-même » : ce bouton n'existe pas encore.

## Questions pour l'avocat

Chaque fichier se termine par ses questions. Les plus importantes : qui est l'éditeur et le responsable du traitement avant la déclaration ; base juridique des traitements qui touchent aux opinions politiques (RGPD, article 9) ; délégué à la protection des données et analyse d'impact ; mineurs de moins de 15 ans ; conservation des données d'identification sans adresse IP ; statut d'hébergeur pour les contenus relus avant publication.
