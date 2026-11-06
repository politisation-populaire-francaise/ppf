# Contribuer

Merci de votre intérêt pour la plateforme de Politisation Populaire Française.

## Avant tout

- Le projet suit les règles de `CLAUDE.md` (vie privée, sécurité, accessibilité, aucun traceur, aucun tiers). Lisez-les avant de proposer un changement.
- Les documents de référence sont dans `docs/projet/`. Une fonctionnalité ne se code pas avant que sa spécification soit validée par la propriétaire du projet.
- Code, identifiants, commentaires et messages de commit en anglais ; interface, documentation et discussions en français.

## Licence des contributions

Le code est publié sous la licence GNU AGPL version 3 ou ultérieure (`LICENSE`), avec les permissions supplémentaires de `LICENSE-ADDITIONAL-PERMISSIONS.md` (distribution par les magasins d'applications).

En proposant une contribution, vous acceptez :

1. qu'elle soit publiée sous cette même licence, **permissions supplémentaires comprises**, afin que le projet puisse continuer à être distribué sur les magasins d'applications ;
2. le certificat d'origine du développeur (Developer Certificate of Origin, version 1.1, https://developercertificate.org), c'est-à-dire que vous avez le droit de soumettre ce code sous cette licence.

Pour le signifier, chaque commit porte la ligne `Signed-off-by: Nom <adresse>` (option `-s` de `git commit`).

## Règles de travail

- Une branche par changement, une demande de fusion (pull request), jamais de modification directe de la branche `main`.
- Messages de commit au format Conventional Commits : `feat(server): add health endpoint`.
- Commits signés.
- Tests écrits avec la fonctionnalité ; l'intégration continue doit être au vert.
- Chaque nouvelle dépendance est justifiée dans la demande de fusion (raison, licence, maintenance). Le moins de dépendances possible.
- Aucune donnée personnelle réelle dans le code, les tests ou les exemples ; les exemples sont marqués [EXEMPLE].
- Avant de commiter, activez les crochets locaux : `git config core.hooksPath .githooks` (détection des secrets et des tiers interdits).

## Signaler une faille de sécurité

Ne l'inscrivez pas dans un ticket public : suivez `SECURITY.md`.
