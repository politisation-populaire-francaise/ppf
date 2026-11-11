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

## Blancs que seule la propriétaire peut remplir

1. **Éditeur** tant que l'association n'est pas déclarée : qui (personne physique) en répond, et si elle choisit l'anonymat permis à un éditeur non professionnel (son identité est alors donnée à Hostinger seulement).
2. **Directeur ou directrice de la publication** : nom.
3. **Moyen de contact officiel** (point de contact des utilisateurs et des autorités, demandes RGPD, contestations, droit de réponse) : adresse électronique dédiée (coût éventuel à annoncer) ou clé de messagerie chiffrée de l'équipe.
4. **Moyen de signalement pendant la bêta**, tant que le bouton « Signaler ce contenu » n'existe pas dans l'application (par exemple un message chiffré à la clé de l'équipe de modération).
5. **Hostinger** : confirmer au contrat la société « Hostinger International Limited, 61 Lordou Vironos Street, 6023 Larnaca, Chypre » (relevée dans ses conditions générales) et donner son téléphone. Le centre de données est en Allemagne (relevé par la session « Mise en ligne »).
6. **Après la déclaration** : numéro RNA et adresse du siège.
7. **Délégué à la protection des données** : désigné ou non (à trancher avec l'avocat).
8. **Délai promis** pour traiter une demande de suppression d'un contenu ou d'un compte pendant la bêta.
9. **Durée de la bêta** et sort des comptes et des contenus à sa fin.

## À confirmer par l'équipe technique

- Aucun cookie sur le site public, y compris la page protégée de la préproduction.
- Outil de retrait et d'effacement manuel d'un contenu ou d'un compte côté serveur.
- La page `/securite` dit que les contenus des utilisateurs se signalent « depuis le contenu lui-même » : ce bouton n'existe pas encore.

## Questions pour l'avocat

Chaque fichier se termine par ses questions. Les plus importantes : qui est l'éditeur et le responsable du traitement avant la déclaration ; base juridique des traitements qui touchent aux opinions politiques (RGPD, article 9) ; délégué à la protection des données et analyse d'impact ; mineurs de moins de 15 ans ; conservation des données d'identification sans adresse IP ; statut d'hébergeur pour les contenus relus avant publication.
