# Guide : créer les deux clés de l'équipe (boîte de contact et équipe de sécurité)

- Version : 8 novembre 2026.
- Pourquoi : deux adresses de messagerie chiffrée de l'équipe ont besoin de leur propre clé, distincte de votre compte personnel et des clés qui signent les contenus :
  - **la boîte de l'équipe** (« contact, réclamations et Mes échanges », module M30, décision D-140) : les messages des citoyens à l'équipe sont chiffrés pour cette clé ; seuls les membres qui traitent les dossiers pourront les lire, avec ses douze mots ;
  - **l'équipe de sécurité** (signalement des failles, S-92) : les chercheurs qui trouvent une faille vous écrivent un message chiffré à cette clé, annoncée sur la page `/securite` et dans le fichier `security.txt` du site. Elle est obligatoire avant la mise en production.
- Quand : sans urgence, avant l'ouverture au public. Comptez 20 minutes.
- Ce qu'il vous faut : votre ordinateur, **deux feuilles de papier** et un stylo. Pas de téléphone : rien ne doit être photographié.
- Ce qui reste secret : les **douze mots** de chaque clé, sur papier seulement. Ce que vous m'envoyez : la **clé publique** de chaque clé (elle commence par `npub1`), qui n'est pas secrète, puisqu'elle sera affichée publiquement.

## 1. Préparer les deux feuilles

1. En haut de la première feuille, écrivez : **« PPF : boîte de l'équipe (contact et réclamations), production »** et la date.
2. En haut de la seconde : **« PPF : équipe de sécurité »** et la date.
3. Sur chaque feuille, numérotez douze lignes de 1 à 12.

## 2. Ouvrir l'atelier des clés

1. Écrivez-moi dans la discussion : **« Je suis prête pour les clés de l'équipe »**.
2. Je lance sur votre ordinateur une version de l'application PPF **avec un profil vide, séparé de tout compte existant** (un dossier à part, `C:\Users\clair\.ppf\atelier-cles`). Votre compte personnel, s'il existe sur cet ordinateur, n'est ni lu ni touché.
3. Une fenêtre « Politisation Populaire Française » s'ouvre. Je vous préviens quand elle est prête.

## 3. Créer la clé de la boîte de l'équipe

1. Dans la fenêtre, cliquez sur **« Mon espace »**.
2. Cliquez sur **« Créer mon compte »**.
3. L'écran « Votre phrase de récupération » affiche douze mots. Recopiez-les **sur la première feuille**, dans l'ordre, un par ligne, lisiblement. Vérifiez chaque mot une seconde fois.
4. Cliquez sur **« J'ai noté ces douze mots »**.
5. L'écran « Vérification » demande trois mots par leur numéro : tapez-les depuis votre feuille, puis cliquez sur **« Valider »**. Si « Un mot ne correspond pas » s'affiche, cliquez sur **« Revoir la phrase »** et corrigez votre feuille.
6. L'écran « Votre identité » apparaît. Sous **« Votre clé publique (identifiant public) »**, une ligne commence par `npub1`. Sélectionnez toute cette ligne à la souris, faites **Ctrl + C**, puis collez-la (**Ctrl + V**) dans la discussion, précédée de « Boîte : ».
7. Faites défiler l'écran jusqu'en bas et cliquez sur **« Se déconnecter de cet appareil »**, puis sur **« Effacer mes clés de cet appareil »**. La clé n'existe plus que sur votre feuille.

## 4. Créer la clé de l'équipe de sécurité

Recommencez les étapes 1 à 7 de la section 3, avec **la seconde feuille**, et collez la clé publique dans la discussion précédée de « Sécurité : ».

## 5. Fermer l'atelier

1. Fermez la fenêtre de l'application.
2. Je supprime le dossier `atelier-cles`, qui ne contient plus aucune clé, et je vous le confirme.

## 6. Ranger les feuilles

- Rangez les deux feuilles **en lieu sûr, séparément de la phrase de votre compte personnel** et de celle de la racine de la plateforme (12 mots sur papier, décision du 7 novembre 2026).
- Le mieux : une copie de chaque feuille, recopiée à la main, dans un second endroit sûr (chez une personne de confiance, sous enveloppe fermée).
- Ne les photographiez pas, ne les tapez dans aucun autre logiciel, aucun site, aucun courriel.
- **Si une feuille est perdue**, les messages chiffrés pour cette clé deviennent illisibles : prévenez-moi, on crée une nouvelle clé et l'application l'annonce.
- **Si quelqu'un a pu voir une feuille**, prévenez-moi aussitôt : on remplace la clé.

## 7. Ce que je fais ensuite

- La clé publique de la boîte est inscrite dans l'application (pour la production) et dans la configuration du serveur de production ; la préproduction utilise une clé d'exemple, puisqu'elle ne reçoit que des données fictives.
- La clé publique de l'équipe de sécurité est inscrite dans la configuration des deux serveurs (`PPF_SECURITY_NOSTR_KEY`) : elle apparaît alors sur la page « Signaler une faille de sécurité ».
- Plus tard, les membres qui traiteront les dossiers recevront les douze mots de la boîte dans le « client de l'équipe » (étape 3 de M30), sur leur propre ordinateur, avec un guide.
