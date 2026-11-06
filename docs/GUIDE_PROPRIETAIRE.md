# Guide de la propriétaire

Ce guide explique, en langage simple, comment installer, tester, relire et administrer la plateforme. Il est tenu à jour à chaque étape. Version du 5 novembre 2026 (phase 0).

## 1. Où sont les choses

- **Le code, sur votre ordinateur** : `C:\Users\clair\Documents\PPF`. C'est une copie de travail ; la référence est le dépôt GitHub.
- **Le dépôt GitHub** : public, sous votre compte. Son adresse est notée dans le fichier `README.md` dès sa création.
- **Les documents de référence** : `docs\projet` (vision, cahiers des charges et fonctionnel, brainstormings). La version de référence reste dans votre projet claude.ai : quand je modifie une copie locale avec votre accord, je vous le rappelle pour que vous reportiez le changement.
- **Les documents techniques** : `docs` (feuille de route, décisions, modèle de menaces, registre des données, ce guide, journal des modifications) et `docs\guides` (achats et installations, clic par clic).
- **Les outils installés le 5 novembre 2026** : Git, GitHub CLI, JDK 21 (le moteur Java nécessaire à Kotlin), SDK Android (outils de compilation Android), gitleaks (détection des secrets). Ils sont dans votre profil Windows ou dans `C:\Program Files`.

## 2. Installer une version de test sur votre téléphone Android

Chaque version de test est un fichier `.apk`, produit par l'intégration continue à chaque changement accepté, ou par moi en local. Toutes les versions de test portent la même signature : une nouvelle version s'installe par-dessus l'ancienne sans rien effacer.

### Récupérer l'APK depuis GitHub

1. Ouvrez le dépôt sur github.com, onglet **« Actions »**.
2. Cliquez sur la dernière exécution verte de **« CI »** (en haut de la liste).
3. En bas de la page, dans **« Artifacts »**, cliquez sur **« ppf-android-apk »** : un fichier `.zip` se télécharge.
4. Envoyez ce fichier sur votre téléphone : par câble USB (copie dans le dossier « Téléchargements »), ou en vous l'envoyant à vous-même par un canal que vous contrôlez. Décompressez-le sur le téléphone (appui long sur le zip, « Extraire ») pour obtenir le `.apk`.

Quand je produis l'APK sur votre ordinateur, il se trouve dans `C:\Users\clair\Documents\PPF\android\build\outputs\apk\debug\android-debug.apk` (version de test, identifiant `org.politisationpopulairefrancaise.app.test`, signée avec la clé de test).

### Installer l'APK (première fois)

1. Sur le téléphone, ouvrez l'application **« Fichiers »** (ou « Mes fichiers »), dossier « Téléchargements », et touchez le fichier `.apk`.
2. Android affiche « Pour votre sécurité, votre téléphone n'est pas autorisé à installer des applications inconnues provenant de cette source » : touchez **« Paramètres »**, activez **« Autoriser cette source »**, puis revenez en arrière.
3. Touchez **« Installer »**. Si Google Play Protect propose d'analyser l'application, acceptez ou refusez : les deux fonctionnent.
4. L'icône « Politisation Populaire Française » apparaît dans vos applications.

### Mettre à jour

Touchez le nouveau fichier `.apk` et confirmez **« Mettre à jour »**. Si le téléphone refuse avec un message sur la signature, dites-le-moi : c'est que les clés diffèrent, et je corrige.

### Ce qu'il faut vérifier dans chaque version

- Les écrans qui n'existent pas encore (En direct, Carte, Voir, Agir, Décider) affichent « Affichage téléphone » en portrait et « Affichage tablette » en paysage (le téléphone tourné) : la barre du bas devient alors une colonne d'icônes sur le côté.
- Le menu s'ouvre par le bouton en haut à gauche et contient les quatre piliers.
- Le mode sombre du téléphone est respecté (Paramètres, Affichage, Thème sombre).
- Avec TalkBack activé (Paramètres, Accessibilité), chaque bouton est annoncé par son nom.

### Tester le compte (ajouté le 5 novembre 2026)

Le compte est le premier élément réel de la phase 1. Il ne sert encore à rien d'autre qu'à exister : aucun contenu ne peut être publié. Il vous permet de vérifier la création, la sauvegarde et la restauration. Faites le test avec un compte d'essai, pas avec un compte que vous voudriez garder.

1. Touchez l'icône de personne en haut à droite (**« Mon espace »**), puis **« Créer mon compte »**.
2. Douze mots français s'affichent. Notez-les sur un papier, dans l'ordre, puis touchez **« J'ai noté ces douze mots »**.
3. L'application demande trois des douze mots (par exemple les mots n° 2, 7 et 11). Saisissez-les ; les majuscules, les espaces et les accents tapés autrement n'ont pas d'importance. Essayez d'abord un mot faux : l'application le refuse et explique pourquoi.
4. Après validation, votre **clé publique** s'affiche : une longue suite qui commence par `npub1`. C'est votre identifiant public, un pseudonyme qui ne dit rien de vous.
5. Fermez complètement l'application, puis rouvrez-la : la clé publique est toujours là. Sur Android, elle est enregistrée chiffrée par une clé que le téléphone garde dans sa puce sécurisée.
6. **« Afficher ma phrase de récupération »** : un avertissement s'affiche d'abord ; confirmez, les douze mots réapparaissent, puis **« Masquer »**.
7. **« Afficher ma clé secrète »** : même principe ; la clé commence par `nsec1`. Ne la montrez à personne.
8. **« Se déconnecter de cet appareil »** : un avertissement rappelle que, sans la phrase, le compte est perdu. Confirmez : l'écran revient à « Créer mon compte ».
9. **« J'ai déjà un compte »** : saisissez les douze mots notés à l'étape 2, puis **« Restaurer »**. La clé publique affichée doit être exactement la même qu'à l'étape 4. Vous pouvez aussi coller la clé `nsec1…` à la place des mots.

Si une étape ne se passe pas comme décrit, notez l'étape et le message affiché, et dites-le-moi.

### Tester la navigation et le fil (version du 6 novembre 2026, identité visuelle de vos maquettes)

1. L'application s'ouvre sur le **Fil**. En haut : le bouton du menu, le nom « Politisation Populaire Française » en bleu sur deux lignes, la loupe et la cloche (grisées : la recherche et les notifications n'existent pas encore) et, tout à droite, l'avatar qui ouvre **Mon espace**. En bas : la barre fixe à cinq entrées, Fil, En direct, Carte, Messages, Revendications. Le menu contient les quatre piliers, et la mention « Plateforme citoyenne indépendante, sans lien avec l'État ni le gouvernement ».
2. Sous la barre du haut, les onglets **Tout**, **Comptes PPF**, **Revendications** (et **Mes publications** quand vous avez un compte). Passez de l'un à l'autre en touchant l'onglet ou en glissant le doigt de droite à gauche sur le contenu. Puis le bandeau rouge **EN DIRECT** avec une publication d'exemple : il ouvre l'espace En direct.
3. Sans compte, un encart bleu vous invite à créer le vôtre. Avec un compte, la zone « Partager une publication… » apparaît, avec la mention « Visible par : tout le monde » et le bouton **Publier**. Écrivez un message et publiez-le : il s'affiche en haut, signé « Vous », avec l'horloge « En attente d'envoi au serveur » (aucun serveur n'est encore en ligne).
4. Chaque publication montre l'avatar et le nom de l'auteur (« Politisation Populaire Française » avec la coche bleue de compte vérifié pour les publications de l'équipe), l'heure, « Public », et le badge jaune [EXEMPLE] pour les contenus de démonstration. Les thèmes apparaissent en bleu (#manifestation).
5. Touchez **J'aime** : il passe en rouge carmin ; il ne compte que sur votre appareil pour l'instant. **Commenter** et **Relayer** sont grisés en attendant le cadrage de la vie privée.
6. Tournez le téléphone : la barre du bas devient une colonne d'icônes à gauche ; l'avatar reste en haut à droite.
7. Comparez avec vos maquettes, en clair puis en sombre : couleurs, police (Atkinson Hyperlegible), arrondis, ombres, badges. Deux écarts volontaires, à trancher : la barre du bas (vos maquettes ont à la place un bouton rond « Carte » et un bouton « AIDE »), et l'absence du bouton AIDE tant qu'il n'est pas spécifié.

### Tester les revendications et les messages (ajouté le 6 novembre 2026)

1. Touchez **Revendications** dans la barre du bas. Trois revendications d'exemple s'affichent en cards : le statut (Nouvelle, En débat, Très soutenue), la revendication en une phrase, son résumé, son territoire et ses thèmes. Les boutons **Pour**, **Contre** et **Débat** sont grisés : ils s'activeront quand la méthode « une personne, une voix » sera validée. Touchez une card : l'argumentaire s'ouvre ; la flèche revient à la liste. Sur l'ordinateur, la liste et le détail sont côte à côte.
2. Dans le Fil, l'onglet **Revendications** montre les mêmes cards.
3. Touchez **Messages** dans la barre du bas. Trois onglets, **Privé**, **Collectifs**, **Flux**, se parcourent au doigt ; chacun explique ce qu'il contiendra. Le bouton « Nouvelle conversation » est grisé : aucune conversation n'est possible avant le choix du protocole de chiffrement (une entrée « À valider » vous attend dans le brainstorming du cahier des charges, avec ma recommandation).

### Tester les fiches pratiques (ajouté le 5 novembre 2026, encarts le 6)

1. Ouvrez le menu (bouton en haut à gauche) et touchez **« Comprendre »**. Chaque fiche commence par un encart bleu « En bref », puis des encarts verts « Légal » et rouges « Illégal » avec des listes à puces, et des encarts « Attention » (jaune) et « Conseils » (gris). Les titres des fiches sont en bleu de marque, le texte en Atkinson Hyperlegible, 16 points.
2. La rubrique **« Fiches pratiques »** liste trois fiches de démonstration marquées [EXEMPLE] : droits en manifestation, filmer les forces de l'ordre, garde à vue. La ligne d'état indique « Hors ligne : contenus intégrés à l'application ».
3. Touchez une fiche : son texte s'affiche avec les titres, les listes et les sources ; la ligne sous le titre indique le type, la date de mise à jour et l'origine (« Rédigé par un humain »). La flèche en haut à gauche revient à la liste.
4. Coupez le réseau (mode avion) et refaites les étapes 1 à 3 : tout doit fonctionner à l'identique, puisque les fiches sont dans l'application.
5. Sur l'ordinateur, la liste et la fiche s'affichent côte à côte quand la fenêtre est large.

Les textes de ces fiches sont des brouillons écrits par moi pour la démonstration, avec les sources de droit citées en bas de chaque fiche ; ils doivent être relus par un avocat avant toute publication réelle. Pour modifier une fiche ou en ajouter une, dites-le-moi : je modifie les fichiers de `content/fiches`, je les signe et je vous livre une nouvelle version.

## 3. Lancer l'application de bureau sur votre ordinateur

### Depuis GitHub (installateur Windows)

1. Onglet **« Actions »**, dernière exécution verte, **« Artifacts »**, cliquez sur **« ppf-desktop-windows »** : un `.zip` se télécharge.
2. Décompressez-le (clic droit, « Extraire tout ») et double-cliquez sur le fichier `.msi`.
3. Windows SmartScreen peut afficher « Windows a protégé votre ordinateur » : cliquez sur **« Informations complémentaires »** puis **« Exécuter quand même »**. Ce message apparaît parce que l'installateur n'est pas encore signé par un certificat payant.
4. L'installation se fait sans droits d'administrateur ; l'application apparaît dans le menu Démarrer, dossier « Politisation Populaire Française ».

### Depuis votre ordinateur (sans installateur)

Quand je l'ai préparée, l'application se trouve dans `C:\Users\clair\Documents\PPF\desktop\build\compose\binaries\main\app\PolitisationPopulaireFrancaise\` : double-cliquez sur `PolitisationPopulaireFrancaise.exe`. Le dossier contient tout le nécessaire (environ 130 Mo, dont le moteur Java) ; rien n'est installé sur l'ordinateur.

### Ce qu'il faut vérifier

- Fenêtre large : la barre latérale est toujours visible avec les cinq sections ; la section ouverte montre ses accès rapides ; le bouton « Carte » est en bas à droite avec son libellé.
- Réduisez la fenêtre : à moins d'environ 840 pixels de large, la barre devient une colonne d'icônes ; à moins d'environ 600, elle devient un menu.
- Mode sombre de Windows respecté.
- Le compte : mêmes étapes que sur le téléphone (section 2, « Tester le compte »). Sur l'ordinateur, le compte est enregistré dans le fichier `%APPDATA%\PolitisationPopulaireFrancaise\identity.json` (tapez `%APPDATA%` dans la barre d'adresse de l'Explorateur). Ce fichier est privé à votre session Windows mais n'est pas encore chiffré par un mot de passe : c'est prévu avant le lancement public, et noté dans le modèle de menaces. Pour tout effacer, utilisez « Se déconnecter de cet appareil » ou supprimez ce fichier.

## 4. Relire et accepter une demande de fusion (pull request)

Je ne modifie jamais directement la branche principale. Chaque changement arrive dans une **demande de fusion**, que vous relisez et acceptez.

1. Sur github.com, ouvrez le dépôt, onglet **« Pull requests »**, puis la demande en attente.
2. Lisez la description : ce qui change, comment le tester. Les cases à cocher résument les vérifications faites.
3. Regardez les **« Checks »** : chaque vérification automatique (secrets, traceurs, compilation Linux, compilation Windows) doit être verte. Si une est rouge, je la corrige avant que vous relisiez.
4. Testez si la description le demande (APK ou application de bureau, section 2 ou 3).
5. Pour accepter : bouton **« Merge pull request »**, puis **« Confirm merge »**. Pour refuser ou demander un changement : bouton **« Review changes »**, choisissez « Request changes », écrivez votre remarque. Vous pouvez aussi simplement me le dire ici.
6. Après la fusion, GitHub propose de supprimer la branche : acceptez.

La branche principale refuse toute modification directe, toute suppression et toute réécriture de l'historique, et exige que les vérifications soient vertes.

## 5. La sécurité de vos comptes

- **GitHub** : la double authentification n'est pas encore activée ; elle est obligatoire (cahier des charges, section 8). Guide : sur github.com, photo de profil en haut à droite, **« Settings »**, **« Password and authentication »**, **« Enable two-factor authentication »**, choisissez **« Authenticator app »**, scannez le QR code avec Aegis (ou votre application), tapez le code, puis **téléchargez les codes de secours** et rangez-les sur papier. Dites-moi quand c'est fait.
- **Hostinger** : double authentification activée lors de l'achat des domaines (guide `docs\guides\achat-domaines-hostinger.md`).
- Un mot de passe différent pour chaque compte, dans un gestionnaire de mots de passe.

## 6. Clés et secrets, et où ils sont

- **Clé de signature de test des APK** : `C:\Users\clair\.ppf\ppf-test.keystore`, mot de passe dans `C:\Users\clair\.ppf\ppf-test.env`. Hors du dépôt. Une copie chiffrée est dans les secrets du dépôt GitHub pour l'intégration continue. Si elle est perdue, les téléphones devront désinstaller puis réinstaller l'application de test : rien de grave en phase 0.
- **Clé de signature des commits** : `C:\Users\clair\.ssh\ppf_signing_ed25519`. Sa partie publique est enregistrée dans votre compte GitHub comme clé de signature.
- **Clé de signature de publication** (magasins d'applications) : pas encore créée ; elle sera créée avant la première publication et gardée hors ligne, avec une procédure écrite.
- **Clé de signature des contenus d'exemple** : `C:\Users\clair\.ppf\content-example.nsec`, hors dépôt. Elle signe uniquement les fiches de démonstration intégrées à l'application ; elle sera retirée de la liste des auteurs de confiance avant le lancement public, quand l'équipe éditoriale aura ses propres clés.
- **Clés des comptes d'utilisateurs** (le vôtre compris) : uniquement sur l'appareil où le compte a été créé ou restauré ; sur Android dans le stockage privé de l'application, chiffré par le Keystore du téléphone ; sur l'ordinateur dans `%APPDATA%\PolitisationPopulaireFrancaise`. Aucun serveur ne les reçoit jamais : la phrase de douze mots est le seul moyen de récupération.
- Ne m'envoyez jamais un mot de passe ou une clé par la conversation : je n'en ai pas besoin, tout est sur votre ordinateur.

## 7. Comment je travaille avec vous

- Par petites étapes : j'annonce, je fais, je teste, je vous explique comment tester.
- Avant toute décision structurante, je vous présente des options dans le brainstorming concerné et j'attends votre validation ; la décision est ensuite consignée dans `docs\DECISIONS.md`.
- Aucune dépense, aucune installation, aucune mise en ligne sans votre accord explicite.
- En début de session, je vous rappelle les décisions en attente ; en fin de session, ce qui a été fait, ce que vous devez tester et la prochaine étape.

## 8. Petit lexique

- **Dépôt** : l'ensemble du code et de son historique, sur GitHub et en copie sur votre ordinateur.
- **Commit** : un changement enregistré dans l'historique, avec un message qui dit ce qu'il fait.
- **Branche principale (`main`)** : la version de référence ; tout le reste est du travail en cours.
- **Demande de fusion (pull request)** : une proposition de changement, relue avant d'entrer dans la branche principale.
- **Intégration continue** : les vérifications automatiques lancées à chaque changement (tests, secrets, traceurs, compilation).
- **APK** : le fichier d'installation d'une application Android.
- **Gradle** : l'outil qui compile le projet.
- **SDK Android** : les outils fournis par Android pour compiler une application Android.
