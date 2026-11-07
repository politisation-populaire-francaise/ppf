# Guide : relier PPF à GitHub

- Version : 7 novembre 2026.
- Durée : 15 minutes de votre côté.
- Pourquoi : GitHub héberge le code (public, décision D-005), fait tourner les tests et fabrique l'APK, l'application de bureau et l'image du serveur que la préproduction installe. Rien ne peut aller sur le serveur tant que le code n'est pas sur GitHub.
- Tout ce qui est gratuit : compte, organisation, dépôt public, intégration continue, registre d'images. Aucun achat.

## 1. Double authentification sur votre compte GitHub (si ce n'est pas déjà fait)

Ce compte contrôlera le code de la plateforme : la double authentification est obligatoire (CLAUDE.md, section 7).

1. Connectez-vous sur https://github.com.
2. Cliquez sur votre photo de profil, en haut à droite, puis **« Settings »**.
3. Menu de gauche : **« Password and authentication »**.
4. Sous **« Two-factor authentication »**, cliquez sur **« Enable two-factor authentication »**.
5. Choisissez **« Authenticator app »** et scannez le QR code avec Aegis (ou l'application que vous utilisez pour Hostinger). Tapez le code à six chiffres.
6. GitHub affiche des **codes de récupération** : cliquez sur **« Download »**, imprimez le fichier ou recopiez-le sur papier, rangez-le hors de l'ordinateur, puis supprimez le fichier téléchargé. Cliquez sur **« I have saved my recovery codes »**.
7. Facultatif mais recommandé : dans la même page, **« Passkeys »**, ajoutez une clé d'accès (passkey) avec Windows Hello ou votre téléphone.

## 2. Garder votre adresse e-mail privée

1. Toujours dans **« Settings »**, menu de gauche : **« Emails »**.
2. Cochez **« Keep my email addresses private »**.
3. Cochez **« Block command line pushes that expose my email »**.

GitHub affiche alors une adresse du type `12345678+votrenom@users.noreply.github.com` : c'est elle que je mettrai sur les commits, jamais votre adresse personnelle (décision D-014).

## 3. Créer l'organisation PPF (recommandé)

Une organisation est un espace GitHub qui appartient au projet plutôt qu'à une personne : vous en êtes la propriétaire, vous pourrez y inviter d'autres membres de l'équipe (avec double authentification obligatoire) et la transmettre à l'association. C'est gratuit.

1. Ouvrez https://github.com/account/organizations/new?plan=free
2. **Organization name** : `politisation-populaire-francaise` (libre au 7 novembre 2026).
3. **Contact email** : l'adresse du projet (celle de Hostinger), pas une adresse personnelle si vous pouvez l'éviter. Elle n'est pas affichée publiquement.
4. **This organization belongs to** : **« My personal account »** (l'association n'existe pas encore ; on changera ce réglage après sa déclaration).
5. Cochez l'acceptation des conditions d'utilisation, puis **« Next »**. Si GitHub propose d'inviter des membres, cliquez sur **« Skip this step »** ; s'il propose un essai payant (« Enterprise », « Team »), refusez.
6. Dans l'organisation : **« Settings »**, menu de gauche **« Authentication security »**, cochez **« Require two-factor authentication for everyone in the politisation-populaire-francaise organization »**, puis **« Save »**.

## 4. Autoriser mon outil à agir sur GitHub

Je vous préviens dans la conversation au moment de cette étape : j'ouvre un onglet de terminal dans l'application Claude avec la commande de connexion de GitHub CLI.

1. Le terminal affiche un **code de 8 caractères** (par exemple `ABCD-1234`).
2. Ouvrez https://github.com/login/device (sur l'ordinateur ou sur votre téléphone, connecté à GitHub).
3. Tapez le code, puis **« Continue »**.
4. GitHub affiche **« Authorize GitHub CLI »** avec la liste des autorisations demandées : accès aux dépôts, aux workflows, aux clés de signature SSH et à l'organisation. Cliquez sur **« Authorize github »**. Si GitHub demande votre code de double authentification, donnez-le.
5. Le terminal affiche « Logged in as … ». Dites-le-moi.

Vous pourrez retirer cette autorisation à tout moment : **« Settings »**, **« Applications »**, onglet **« Authorized OAuth Apps »**, **« GitHub CLI »**, **« Revoke »**.

## 5. Ce que je fais ensuite, sans action de votre part

1. J'enregistre sur votre compte la **clé de signature** des commits (créée sur votre ordinateur le 5 novembre 2026) : chaque commit portera la mention « Verified ».
2. Je corrige l'auteur de tous les commits existants (voir l'encadré ci-dessous), en accord avec les autres sessions de travail.
3. Je crée le dépôt public, j'envoie le code, je protège la branche principale (aucun envoi direct, tests au vert et relecture obligatoires) et je vérifie que l'intégration continue passe au vert.

> **Pourquoi corriger l'auteur des commits ?** Depuis le 5 novembre 2026, les commits portent l'adresse `ppf@users.noreply.github.com`. Le 7 novembre 2026, j'ai découvert que cette adresse appartient déjà à un compte GitHub sans rapport avec PPF (le compte « ppf », créé en 2011) : si on envoyait le code tel quel, GitHub attribuerait tout le travail de PPF à cet inconnu. Je remplace donc l'adresse par la vôtre (version « noreply », privée) sur tous les commits, avant le premier envoi. Le contenu du code ne change pas.

## 6. Deux actions de votre part après le premier envoi

### Donner à GitHub la clé de test des APK

Pour que les APK fabriqués par GitHub s'installent par-dessus ceux que je fabrique sur votre ordinateur (même signature de test, décision D-012), GitHub doit connaître la clé de test. Elle est sur votre ordinateur ; c'est vous qui la transmettez, en cliquant sur **« Run »** à côté de chacune des deux commandes que je vous donnerai dans la conversation. Elles envoient la clé et son mot de passe dans les **secrets** du dépôt : GitHub les garde chiffrés et ne les montre plus jamais, à personne.

### Rendre publique l'image du serveur

Après la première exécution verte sur la branche principale :

1. Ouvrez https://github.com/orgs/politisation-populaire-francaise/packages
2. Cliquez sur **`ppf-server`**, puis, à droite, **« Package settings »**.
3. Tout en bas, **« Danger Zone »**, **« Change visibility »**, choisissez **« Public »**, tapez `ppf-server` pour confirmer, puis **« I understand the consequences, change package visibility »**.

L'image contient le même code que le dépôt public, sans aucun secret : la rendre publique permet au serveur de la télécharger sans mot de passe.
