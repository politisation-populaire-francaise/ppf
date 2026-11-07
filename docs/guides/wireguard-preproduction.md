# Guide : accéder à la préproduction avec WireGuard

- Version : 7 novembre 2026.
- Durée : 10 minutes par appareil.
- Pourquoi : la préproduction (la version d'essai en ligne de PPF) n'est pas ouverte au public. Elle ne répond qu'aux appareils de l'équipe, reliés au serveur par un **tunnel WireGuard** : une connexion chiffrée, comme un câble privé entre votre appareil et le serveur. Sans le tunnel, la page ne s'affiche pas du tout : c'est normal.
- WireGuard est un logiciel libre et gratuit. Il ne fait passer par le tunnel que les échanges avec le serveur de PPF : le reste de votre navigation n'est pas touché.

Je vous donne, pour chaque appareil, un **profil** : un fichier `.conf` pour l'ordinateur, un **QR code** pour le téléphone. Chaque profil contient la clé secrète de cet appareil : ne l'envoyez à personne, et supprimez le fichier une fois importé.

## 1. Sur votre ordinateur (Windows)

1. Téléchargez WireGuard depuis le site officiel : https://www.wireguard.com/install/ , section **Windows**, bouton **« Download Windows Installer »**. Le fichier s'appelle `wireguard-installer.exe`.
2. Ouvrez-le. Windows demande l'autorisation d'apporter des modifications : **« Oui »**. L'installation est automatique et ouvre la fenêtre de WireGuard.
3. Cliquez sur **« Importer le tunnel depuis un fichier »** (« Import tunnel(s) from file »).
4. Choisissez le fichier que je vous ai indiqué (`proprietaire-ordinateur.conf`, dans votre dossier `C:\Users\clair\.ppf\`).
5. Le tunnel `proprietaire-ordinateur` apparaît. Cliquez sur **« Activer »** (« Activate »). L'état passe à **« Actif »**.
6. Supprimez le fichier `.conf` de votre dossier (clic droit, **« Supprimer »**, puis videz la corbeille) : WireGuard en garde sa propre copie protégée.

## 2. Sur votre téléphone Android

1. Ouvrez le **Play Store**, cherchez **« WireGuard »** et installez l'application de **« WireGuard Development Team »** (icône d'un dragon blanc sur fond rouge). Elle existe aussi sur F-Droid.
2. Ouvrez WireGuard, touchez le bouton **« + »** en bas à droite, puis **« Scanner depuis un code QR »**. Autorisez l'appareil photo.
3. Scannez le QR code que je vous montre sur l'écran de l'ordinateur.
4. Donnez au tunnel le nom `ppf-preprod`, puis activez-le avec l'interrupteur. Android demande l'autorisation de créer une connexion VPN : **« OK »**. Une petite clé apparaît en haut de l'écran.

Vous pouvez laisser le tunnel actif en permanence : il ne concerne que le serveur de PPF.

## 3. Ouvrir la préproduction

- **Dans un navigateur** (tunnel actif) : https://preprod.politisation-populaire-francaise.org . Le navigateur demande un identifiant et un mot de passe : identifiant `equipe`, mot de passe dans le fichier que je vous ai indiqué. Enregistrez-les dans votre gestionnaire de mots de passe.
- **Dans l'APK d'essai** (tunnel actif sur le téléphone) : rien à faire. La version d'essai se connecte toute seule à la préproduction : le fil, les messages, les salons et les propositions passent par le serveur. Sans le tunnel, l'application fonctionne hors ligne, comme avant.
- **Sur l'application de bureau** : je vous donnerai la commande qui la relie à la préproduction.

## 4. Si ça ne marche pas

- La page reste blanche ou indique « connexion refusée » : vérifiez que le tunnel est **actif**. Dans WireGuard, la ligne **« Dernière poignée de main »** (« Latest handshake ») doit indiquer quelques secondes ou minutes. Si elle n'apparaît pas, envoyez-moi une capture d'écran de la fenêtre de WireGuard **sans** la partie « Clé privée ».
- Un appareil perdu ou volé : dites-le-moi tout de suite, je retire son profil du serveur en une commande.
