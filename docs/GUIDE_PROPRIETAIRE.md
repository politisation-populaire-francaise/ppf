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

Quand je produis l'APK sur votre ordinateur, il se trouve dans `C:\Users\clair\Documents\PPF\android\build\outputs\apk\preview\android-preview.apk` : c'est la **version d'essai** (identifiant `org.politisationpopulairefrancaise.app.test`, signée avec la clé de test). Depuis le 6 novembre 2026, c'est le seul fichier à envoyer sur un téléphone. L'ancien fichier `android-debug.apk` reste réservé à l'ordinateur : il est « débogable », c'est-à-dire que n'importe qui branchant le téléphone par câble pourrait lire les fichiers privés de l'application (compte compris). La version d'essai ne l'est pas. Elle s'installe par-dessus l'ancienne version sans rien effacer, et chaque nouveau fichier porte un numéro de version plus grand que le précédent.

### Installer ou mettre à jour l'APK

Suivez le guide pas à pas `docs/guides/installer-version-test-android.md`, que vous pouvez aussi transmettre aux personnes qui testent avec vous (votre sœur, par exemple). En résumé :

1. Sur un Samsung, désactivez le temps de l'installation le **Bloqueur automatique** (Paramètres, Sécurité et confidentialité, Bloqueur automatique).
2. Dans **Mes fichiers**, touchez le fichier `.apk` ; la première fois, autorisez cette source.
3. Si Google Play Protect propose d'**envoyer l'application pour analyse**, touchez **Envoyer** ; s'il parle de développeur inconnu, touchez **Plus de détails** puis **Installer quand même**. S'il parle de **code malveillant** (message rouge), n'installez pas et envoyez-moi une capture.
4. Réactivez le Bloqueur automatique.

Ces avertissements viennent du fait que l'application ne passe pas encore par un magasin d'applications ; ils ne signalent pas un problème dans l'application. Seule une distribution par Google Play les fera disparaître : c'est une décision qui coûte 25 dollars, en attente de votre réponse (brainstorming du cahier des charges, entrée « Distribution des versions de test sur Android »).

Si le téléphone refuse avec un message sur un **conflit de package** (signature), ne désinstallez rien et dites-le-moi : c'est que les clés diffèrent, et je corrige.

### Ce qu'il faut vérifier dans chaque version

- Les écrans qui n'existent pas encore (au 7 novembre 2026, seulement Décider) affichent « Affichage téléphone » en portrait et « Affichage tablette » en paysage (le téléphone tourné) : la barre du bas devient alors une colonne d'icônes sur le côté.
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
7. Comparez avec vos maquettes, en clair puis en sombre : couleurs, police (Atkinson Hyperlegible), arrondis, ombres, badges. La barre du bas a remplacé le bouton rond « Carte » de vos maquettes (D-044). Le bouton rouge « AIDE » est présent dans tous les espaces (D-053) : touchez-le pour voir les numéros d'urgence, les avocats de votre territoire et votre point de ralliement ; le mode manifestation se trouve dans l'accueil d'« Agir ».

### Tester le suivi de comptes (ajouté le 7 novembre 2026)

1. Ouvrez l'application sans créer de compte. Dans le fil, l'onglet **Suivis** se trouve juste après « Tout » : touchez-le. Un encadré explique que vous ne suivez encore aucun compte, et une ligne discrète rappelle que la liste reste sur votre appareil.
2. Revenez sur « Tout ». Chaque publication porte, en haut à droite, un petit bouton **Suivre**. Utilisez-le sur une publication de Politisation Populaire Française : il devient « Suivi » avec une coche.
3. Touchez de nouveau l'onglet **Suivis** : les cinq publications d'exemple de ce compte s'y trouvent maintenant.
4. Sur l'une d'elles, utilisez le bouton « Suivi » : il redevient « Suivre » et la vue Suivis se vide.
5. Touchez l'avatar ou le nom de l'auteur d'une publication : une petite boîte s'ouvre avec son nom, sa clé publique complète (« npub1… », sélectionnable pour la copier) et un bouton « Suivre » ou « Ne plus suivre ». « Fermer » referme la boîte sans rien changer.
6. Suivez un compte, fermez complètement l'application, puis rouvrez-la : le compte est toujours suivi. La liste est enregistrée sur l'appareil, et nulle part ailleurs.
7. Si vous avez un compte : dans Mon espace, « Se déconnecter de cet appareil » prévient que la liste des comptes suivis sera aussi effacée ; après confirmation, l'onglet Suivis est de nouveau vide.
8. Sur téléphone, la rangée d'onglets défile de gauche à droite : balayez-la, ou balayez les vues, pour atteindre « Revendications ».

Pas encore là : la synchronisation de cette liste entre vos appareils (sauvegarde chiffrée, à étudier) et sa gestion depuis Mon espace.

### Tester les revendications et les messages (ajouté le 6 novembre 2026)

1. Touchez **Revendications** dans la barre du bas. Huit revendications d'exemple s'affichent en cards : le statut (Nouvelle, En débat, Très soutenue), la revendication en une phrase, son résumé, son territoire et ses thèmes. Les boutons **Pour** et **Contre** restent grisés sans connexion au serveur de PPF (c'est le cas de l'APK de test pour l'instant), et **Débat** viendra plus tard ; la ligne sous les boutons dit pourquoi. Pour essayer le vote, voir plus bas « Essayer le vote anonyme sur l'ordinateur ». Touchez une card : l'argumentaire s'ouvre ; la flèche revient à la liste. Sur l'ordinateur, la liste et le détail sont côte à côte.
2. Dans le Fil, l'onglet **Revendications** montre les mêmes cards.
3. Touchez **Messages** dans la barre du bas : trois onglets, **Privé**, **Collectifs**, **Flux**, se parcourent au doigt (détail dans « Tester la messagerie » ci-dessous).

### Tester les vues et les filtres du mur (ajouté le 6 novembre 2026)

1. Ouvrez « Revendications ». En haut, des onglets : Toutes, Comptes suivis, Société, Santé. Balayez l'écran vers la gauche ou touchez un onglet pour changer de vue. « Santé » ne montre que la revendication sur les médecins de garde.
2. Dans « Toutes », touchez « Filtres ». Choisissez le thème « Éducation » : le bouton du bas indique « Afficher 3 revendications ». Touchez-le. La liste n'en montre plus que trois, une puce « Éducation » apparaît au-dessus ; touchez sa croix pour retirer le filtre, ou « Tout effacer ».
3. Touchez « Tri : Nouveauté » et choisissez « Popularité » : en attendant l'ouverture des votes, l'ordre suit l'avancement (devenue campagne, retenue, très soutenue, en débat, nouvelle). Une phrase l'explique sous ce choix.
4. Ouvrez « Filtres » dans la vue « Santé » : le thème Santé est verrouillé, avec « Défini par la vue « Santé » ».
5. Dans « Toutes », mettez un filtre (par exemple le territoire « Métropole de Lyon »), puis « Enregistrer comme vue », donnez un nom et « Créer la vue ». Un nouvel onglet apparaît juste après « Toutes » et s'affiche. Fermez l'application et rouvrez-la : il est toujours là.
6. Touchez « Personnaliser » : montez ou descendez les vues avec les flèches, renommez ou modifiez une vue personnalisée, retirez une vue, ajoutez-en une (« Ajouter une vue », par exemple « Nationales »), ou « Rétablir les vues par défaut » (une question vous est posée d'abord, « Garder mes vues » en premier).
7. « Comptes suivis » : vide tant que vous ne suivez personne. Suivez le compte d'exemple depuis le fil (bouton « Suivre ») : les huit revendications d'exemple y apparaissent.
8. Avec un compte, l'onglet « Mes revendications » apparaît à la fin.

Vos vues restent sur votre appareil ; les filtres disparaissent quand l'application se ferme.

### Proposer une revendication et voir les vérifications automatiques (ajouté le 6 novembre 2026)

Il faut un compte (Mon espace) pour envoyer ; pour écrire et voir les vérifications, non.

1. Dans « Revendications », touchez **Proposer une revendication**. Le formulaire s'ouvre : la revendication en une ou deux phrases, « En résumé », « Pourquoi », « Ce que la revendication demande », « Ce qui existe déjà », les thèmes (un à trois), le territoire, puis la charte.
2. Écrivez comme revendication : « Cantine à un euro pour les familles modestes ». Pendant que vous tapez, un bloc **Revendications proches** apparaît sous le champ : « Cette revendication semble dire la même chose qu'une revendication existante », avec la revendication d'exemple sur les cantines. Touchez-la : elle s'ouvre ; touchez de nouveau « Proposer une revendication » : votre texte est toujours là.
3. Écrivez à la place : « Augmenter le SMIC et baisser la TVA sur l'énergie ». Le bloc **Vérifications automatiques** dit « À vérifier : votre texte semble contenir plusieurs idées ».
4. Écrivez un numéro de téléphone dans « Pourquoi » (par exemple 06 12 34 56 78) : « À corriger : retirez les coordonnées personnelles », et le bouton d'envoi se grise. Effacez-le.
5. Sans thème choisi, le bouton reste grisé avec « Corrigez d'abord les points marqués À corriger » ; choisissez un thème, puis cochez « J'ai lu la charte et ma revendication la respecte » : le bouton **Envoyer pour relecture** s'active.
6. Touchez-le. Sur votre téléphone, sans serveur, le message est « Revendication gardée sur cet appareil » : elle partira dès qu'un serveur répondra. Touchez « Retour aux revendications », puis l'onglet **Mes revendications** : votre revendication y est, avec le badge « En attente d'envoi ». Elle n'apparaît pas dans « Toutes » : rien n'est public avant la relecture par l'équipe.
7. Sous les vérifications, une ligne rappelle ce qui se passe après l'envoi : comparaison avec toutes les revendications sur le serveur, puis recherche des lois en vigueur et analyse par IA dès qu'elles seront en service, et toujours une relecture par une personne.

Sur l'ordinateur (pour qui veut aller plus loin) : avec le serveur de développement (`gradlew :server:runDev`) et l'application de bureau branchée dessus (`PPF_SERVER_URL=http://localhost:8080`), la revendication part au serveur et affiche « Revendication envoyée en relecture », puis « En relecture » dans « Mes revendications ». Le serveur de développement liste les propositions en attente avec leur rapport à l'adresse http://localhost:8080/dev/revendications/proposals (sur l'ordinateur seulement) ; je peux simuler la décision de l'équipe pour vous la montrer.

### Essayer l'espace « Agir » (ajouté le 7 novembre 2026)

Tout ce que vous verrez est inventé et marqué [EXEMPLE] : mobilisations, appels, organisations, lieux et personnes. Les numéros de téléphone des organisations d'exemple sont pris dans les blocs que l'Arcep réserve aux films et aux séries : ils n'appellent personne. Les sites et les courriels finissent par `example.org` ou `exemple.invalid`, des noms réservés aux exemples. Aucun compte n'est nécessaire pour lire et répondre ; il en faut un pour proposer un événement ou lancer un appel.

#### Sur le téléphone (version d'essai, sans serveur)

Installez l'APK d'essai comme d'habitude (section 2). Le téléphone n'est relié à aucun serveur : il montre les exemples intégrés à l'application, et ce que vous proposez reste sur le téléphone.

**L'accueil d'Agir**

1. Ouvrez le menu (les trois traits en haut à gauche) et touchez **Agir**. Une phrase d'introduction, puis cinq rubriques : Agenda des mobilisations, Appels, Organisations, Collectifs, Guides pour s'organiser. Plus bas, « En préparation » liste sept rubriques grisées, qui ne s'ouvrent pas.
2. Tournez le téléphone : les cartes passent d'une à deux colonnes (trois sur un grand écran).
3. Depuis chaque rubrique, le bouton retour du téléphone ramène à l'accueil d'Agir.

**L'agenda des mobilisations**

4. Touchez **Carte** dans la barre du bas (ou « Agenda des mobilisations » dans Agir). L'écran s'appelle « Carte et agenda » ; un encadré explique que la vue carte arrive bientôt.
5. Les mobilisations sont classées par jour (« Demain », puis la date). Chaque carte montre le type, le titre, l'heure, le lieu et le territoire. Le rassemblement de Seine-Saint-Denis porte la mention **Annulé**, le blocage de Toulouse **Reporté**.
6. Touchez **Passés** : la projection-débat de Rennes du 3 novembre y est.
7. Touchez **Filtres**, choisissez « Grève », puis « Afficher 1 mobilisation » : seule la grève de deux jours reste. Touchez la croix de la puce « Grève » pour revenir à toute la liste. Essayez aussi « Cette semaine », « Choisir des dates », un territoire ou un mot. Vos filtres restent sur le téléphone.
8. Ouvrez la marche de Marseille : la date, le lieu, la mention « Déclarée en préfecture » et la description. Touchez **Ouvrir le lien de l'événement** : la fenêtre « Vous quittez PPF » s'ouvre, avec « Rester sur PPF » en premier ; le lien d'exemple ne mène nulle part.
9. Touchez **Ajouter à mon agenda**, lisez l'avertissement, puis **Créer le fichier d'agenda** : choisissez votre application d'agenda (elle s'ouvre avec l'événement prérempli, vous confirmez ou vous annulez) ou une autre application.
10. Touchez **J'y serai** : la mention apparaît sur la carte de l'événement. Elle reste sur votre téléphone, n'est envoyée nulle part et disparaît un jour après l'événement.
11. Avec un compte, touchez **Proposer un événement**. Essayez une date déjà passée, ou une fin avant le début : le message s'affiche sous le champ. Tapez « Rassemblement pour les services publics » le 14/11/2026 : l'événement d'exemple du même jour est signalé avant l'envoi. Cochez la charte et envoyez : sans serveur, l'écran annonce « Gardé sur votre appareil » et la proposition apparaît dans l'onglet **Mes propositions**. Elle n'est pas dans l'agenda : rien n'est public avant la relecture par l'équipe.

**Les appels**

12. Dans Agir, touchez **Appels**. Sept appels d'exemple sont affichés ; le huitième, clos, n'apparaît qu'avec le filtre « Clos » ou « Tous ».
13. Touchez **Filtres** : choisissez « Ce mois-ci », un type, un territoire, ou tapez des mots. Chaque filtre apparaît en puce : touchez sa croix pour le retirer. Touchez **Tri** : « Échéance la plus proche » met en tête ce qui arrive bientôt ; une phrase explique qu'il n'y a pas encore de tri par popularité.
14. Ouvrez la pétition : l'objectif (1 000 signatures) et le compte à rebours s'affichent, sans barre de progression. Touchez **J'ai signé** : la réponse reste sur votre téléphone. Elle apparaît dans l'onglet **Mes réponses**, où « Effacer mes réponses » demande confirmation (« Garder mes réponses » en premier).
15. Touchez **Ouvrir la pétition** : « Vous quittez PPF » s'affiche avec le nom du site, « Rester sur PPF » en premier.
16. Ouvrez l'appel à témoins : « Je témoigne » est grisé, avec l'explication du canal sécurisé en préparation.
17. Touchez **Lancer un appel** : choisissez un type (« Collecter des fonds » est grisé, réservé aux organisations vérifiées et à PPF), remplissez les champs ; la liste « Avant d'envoyer » dit ce qui manque. Tapez « Garder le bureau de poste » dans le titre d'une pétition : l'appel proche s'affiche. Avec un compte, cochez la charte et envoyez : sans serveur, l'appel reste « En attente d'envoi » dans « Mes appels ».

**Les organisations**

18. Dans Agir, touchez **Organisations** : huit organisations fictives, toutes marquées [EXEMPLE] et « Vérifiée ». Touchez la puce « Syndicats » : il en reste deux. « Tous les types » les remet toutes.
19. Dans **Près de chez moi**, tapez 59000 : d'abord les organisations du Nord, puis celles qui agissent partout en France. Ce que vous tapez reste sur votre téléphone.
20. Ouvrez « Association fictive Les Ateliers de la Lanterne » : la date de vérification, le territoire, la présentation et les contacts. Touchez **Suivre** : le bouton devient « Suivi », et la phrase dessous rappelle que l'abonnement reste sur votre téléphone. Plus bas, l'agenda de l'organisation montre sa prochaine mobilisation, « [EXEMPLE] Assemblée générale ouverte du quartier » à Lyon ; touchez-la : elle s'ouvre dans « Carte ». Une organisation qui n'annonce rien affiche « Aucune mobilisation à venir annoncée par cette organisation. »
21. Touchez le site de l'association : « Vous quittez PPF » s'ouvre ; le grand bouton « Rester sur PPF » la referme sans rien ouvrir. Touchez le téléphone : l'application d'appel s'ouvre avec un numéro de fiction, qui n'appelle personne.
22. Revenez à l'accueil d'Agir : **Collectifs** montre les mêmes salons que dans Messages ; **Guides pour s'organiser** ouvre « Comprendre », où aucune organisation n'apparaît parmi les fiches pratiques.

**Ailleurs dans l'application**

23. Touchez **En direct** : « Prochaines mobilisations » montre les trois prochaines (sans les annulées) ; touchez-en une, elle s'ouvre dans « Carte ».
24. Avec un compte d'essai : dans Mon espace, « Se déconnecter de cet appareil » efface aussi, après confirmation, vos réponses « J'y serai », vos réponses aux appels et le suivi de vos propositions (protection par défaut).

#### Sur l'ordinateur, avec le serveur de développement (relecture, publication, annulation)

C'est là que l'on voit le circuit complet : une proposition attend la relecture de l'équipe, puis elle est publiée ou refusée. Le serveur de développement ne fonctionne que sur votre ordinateur et oublie tout quand on le ferme.

1. Lancez le serveur de développement et une application d'essai reliée à lui : étapes 1 à 4 de « Échanger un message entre deux comptes sur l'ordinateur » (plus bas dans ce guide). Dans l'application, créez un compte d'essai (Mon espace).
2. Dans le navigateur de l'ordinateur, ouvrez http://localhost:8080/agenda : l'agenda du site, lisible sans application et sans JavaScript, avec « [EXEMPLE] Assemblée générale de démonstration » et les mobilisations d'exemple. Tapez « Lyon » dans le territoire et touchez **Filtrer**. Le lien « S'abonner à cet agenda dans un logiciel de calendrier » donne le flux `.ics` (le même agenda pour un logiciel d'agenda).
3. Ouvrez http://localhost:8080/dev/held : la liste, en texte technique, des contenus qui attendent la relecture ; un rassemblement d'exemple y attend déjà. Chaque contenu a un `id`, une suite de 64 lettres et chiffres.
4. Dans l'application : **Carte**, **Proposer un événement**, remplissez le formulaire (titre commençant par [EXEMPLE]), cochez la charte, envoyez. Le message est « En attente de relecture par l'équipe » ; l'onglet **Mes propositions** le montre en relecture. Rechargez http://localhost:8080/dev/held : votre événement y est ; sélectionnez son `id` à la souris et copiez-le (Ctrl+C).
5. **Approuver** (c'est le geste que fera l'équipe dans son écran de relecture, à venir) : ouvrez une nouvelle fenêtre PowerShell et tapez `curl.exe -X POST http://localhost:8080/dev/held/ID/approve`, en remplaçant `ID` par l'identifiant copié (Ctrl+V), puis Entrée. Dans l'application, touchez **Actualiser l'agenda** : l'événement est publié et apparaît dans l'agenda, et sur la page http://localhost:8080/agenda.
6. **Annuler son événement** : ouvrez-le dans l'application, touchez **Annuler mon événement** : une confirmation s'ouvre, « Garder l'événement » en premier. Confirmez : l'événement porte la mention « Annulé » pour tout le monde, sans nouvelle relecture (rechargez la page de l'agenda du site pour le voir).
7. **Refuser** : proposez un autre événement, copiez son `id` dans http://localhost:8080/dev/held, puis tapez `curl.exe -X POST --data "[EXEMPLE] Motif du refus" http://localhost:8080/dev/held/ID/reject`. Dans l'application, rouvrez l'agenda : dans « Mes propositions », la proposition est « Non publié », avec « Raison donnée par l'équipe : [EXEMPLE] Motif du refus ». « Retirer de la liste » l'efface de l'ordinateur.
8. **Appels** : dans Agir, **Appels**, **Lancer un appel** avec ce compte : il part en relecture et apparaît dans http://localhost:8080/dev/held ; approuvez-le de la même façon (étape 5) puis rouvrez les appels : il est publié dans « Mes appels ». Ouvrez-le : **Clore l'appel** demande un bilan, avec « Garder l'appel ouvert » en premier ; après l'envoi, l'appel est affiché clos avec son bilan, sans relecture.
9. Où en est un contenu : http://localhost:8080/api/v1/held?id=ID. Une mobilisation en attente dont la date est passée y apparaît refusée, avec le motif « La date de l'événement est passée avant la relecture. ».
10. À la fin, fermez l'application et arrêtez le serveur (Ctrl+C dans sa fenêtre). Vous pouvez supprimer vous-même le dossier `ppf-essai-1` de votre dossier personnel : il ne contient que ce compte d'essai.

### Tester le bouton AIDE et la sécurité du téléphone (ajouté le 8 novembre 2026)

1. Touchez **AIDE** (rouge, en haut de l'écran du téléphone, en tête du menu sur tablette et ordinateur) : les numéros d'urgence s'affichent, le 15 et le 112 en premier ; touchez-en un, le téléphone propose d'appeler (vous pouvez annuler). Plus bas : les avocats de votre territoire, la rubrique **Besoin de parler ?** (lignes d'écoute : prévention du suicide, violences faites aux femmes, enfance en danger, aide aux victimes, harcèlement, puis deux associations), et votre point de ralliement, gardé sur le téléphone seulement.
2. Dans « Agir », ouvrez **Mode manifestation** : avant de partir, vos droits, numéros utiles, « Préparer mon téléphone ».
3. Dans **Mon espace**, carte « Sécurité de cet appareil » : activez le **verrouillage** avec un code de 6 chiffres au moins (pas 123456 ni 111111), quittez l'application puis revenez : le code est demandé. Le 15 et le 112 restent appelables sans le code.
4. Même carte : **icône discrète** ; après la mise en garde, l'icône de PPF devient « Outils » (gris) dans la liste des applications. Revenez en arrière de la même façon.
5. Le **contenu masqué** est activé : dans la liste des applications ouvertes, PPF apparaît vide, et les captures d'écran sont bloquées (c'est voulu).
6. **Effacement rapide** (à essayer en dernier, sur un compte d'essai) : il rappelle de noter la phrase de récupération, puis efface ce que PPF garde sur le téléphone ; pour retrouver le compte : Mon espace, « J'ai déjà un compte », puis la phrase.

### Tester l'écran « En direct » (ajouté le 7 novembre 2026)

1. Touchez **En direct** dans la barre du bas (deuxième bouton).
2. En haut, **le direct** : la dernière publication du thème « en direct » (fictive).
3. **Alertes** : « Vote dans 5 jours » (motion de censure sur le budget, le 12 novembre) et « Vote dans 11 jours » (Sénat, le 18 novembre). Les jours se comptent à partir d'aujourd'hui : dans quelques jours, ces exemples disparaîtront d'eux-mêmes. Touchez une alerte : la fiche du texte s'ouvre dans « Voir » ; le bouton retour ramène au Fil.
4. **Chiffres clés** : les quatre premiers de « Voir », avec leurs sources côte à côte ; touchez-en un pour ouvrir sa fiche.
5. **Prochaines mobilisations** : les trois prochaines mobilisations de l'agenda (sans les annulées) ; touchez-en une, elle s'ouvre dans « Carte » (mis à jour le 7 novembre 2026 avec l'espace « Agir »).
6. **À la une** : la revendication « devenue campagne » ; « Lire la revendication » l'ouvre dans le mur.
7. Plus bas : les dernières publications de PPF et les derniers faits du mouvement.

Sur l'ordinateur, l'écran se présente en deux colonnes.

### Tester l'espace « Décider » (ajouté le 7 novembre 2026)

Les pétitions sont réelles depuis le 9 novembre 2026 : les deux pétitions ouvertes les plus signées sur le site de l'Assemblée nationale et sur celui du Sénat ce jour-là, avec le nombre de signatures relevé sur le site officiel et la mention « Rédigé par l'IA ». Le Pacte, les candidats et leurs réponses restent inventés et marqués [EXEMPLE]. Aucun compte n'est nécessaire, et tout marche sans réseau.

1. Ouvrez le menu (les trois traits en haut à gauche) et touchez **Décider**. En haut, le **Pacte démocratique**, puis les **pétitions officielles ouvertes**, puis « Et aussi ».
2. Touchez la carte du **Pacte**. Lisez l'encart « Les mêmes règles pour tous les candidats » (il dit maintenant qu'une réponse reçue après la date limite n'est pas comptée, votre décision du 7 novembre ; aucune réponse d'exemple n'est en retard), puis les six mesures. Plus bas, chaque candidat a sa carte, par ordre alphabétique : Alex a tout signé, Camille a refusé une mesure (avec son commentaire), Dominique a répondu à trois mesures (les autres sont « En attente » jusqu'au 30 novembre), Sacha n'a pas répondu.
3. Touchez le nom d'un candidat : sa fiche s'ouvre dans « Voir ». Revenez avec le bouton retour.
4. En bas de la page du Pacte, **Empreinte du texte envoyé** : cette suite de caractères changerait si un seul mot des mesures changeait. Touchez **Copier l'empreinte**.
5. Revenez à l'accueil de « Décider » et touchez la pétition **Retirer le projet de loi relatif à l'ordre public**. Vous voyez le compteur, la barre vers 500 000 signatures, puis chaque seuil avec **ce qu'il obtient** et, en orange, **ce qu'il ne garantit pas** (le débat se termine sans vote).
6. Touchez **Signer sur le site officiel** : une mise en garde s'affiche avant d'ouvrir le vrai site de l'Assemblée nationale (sa page d'accueil, car la pétition est fictive). Touchez **Rester ici**.
7. Plus bas, **Faire signer autour de vous** : touchez **Copier le message** et collez-le dans une note.
8. Touchez **Suivre**, puis revenez à l'accueil : la pétition apparaît dans « Ce que vous suivez ». Cette liste reste sur votre téléphone.
9. Sur l'accueil, touchez **Comprendre les seuils** : une carte par plateforme (Assemblée, Sénat, CESE, référendum d'initiative partagée, initiative européenne), avec le texte officiel et la date de vérification.
10. Touchez **Pétitions closes** : la pétition au Sénat sur les retraites montre ce qu'elle a obtenu.
11. Allez dans **Mon espace** (l'avatar en haut à droite) : tout en bas, la version installée et votre système. Donnez-les-moi si vous me signalez un problème.

Sur l'ordinateur, les réponses au Pacte forment un vrai tableau, une colonne par mesure.

### Vérifier que le vote anonyme peut marcher sur votre téléphone (ajouté le 7 novembre 2026)

Le vote anonyme (« une personne, une voix ») n'est pas encore ouvert, mais son moteur est déjà dans l'application. Le téléphone le vérifie lui-même, au premier lancement de « Mon espace ». C'est le seul moyen de savoir s'il fonctionne sur un vrai téléphone, car l'ordinateur n'a pas d'émulateur Android.

1. Ouvrez **Mon espace** (l'avatar en haut à droite) et faites défiler tout en bas.
2. Sous la version installée, une ligne indique d'abord « Vérification du vote anonyme sur cet appareil… », puis, au bout d'une seconde environ :
   - **« Vote anonyme : vérification réussie sur cet appareil »** : tout va bien ;
   - ou une ligne orange **« vérification échouée (étape …) »** : envoyez-moi une capture de cette ligne avec la version juste au-dessus. Rien n'est envoyé nulle part, et le vote restera simplement fermé sur ce téléphone.

### Tester le comparateur des candidats (ajouté le 7 novembre 2026, au soir)

Tout est inventé et marqué [EXEMPLE] : l'élection, les quatre candidats et leurs positions. Aucun compte n'est nécessaire, et tout marche sans réseau.

1. Ouvrez **Décider**. Sous le Pacte démocratique, la rubrique **Comparer les candidats** affiche « 4 candidats, 3 thèmes ». Touchez la carte.
2. L'encadré du haut rappelle les règles : mêmes thèmes, même ordre, ordre alphabétique, aucune consigne de vote.
3. Sur le téléphone, touchez un thème (Démocratie, Santé, Logement) : les quatre candidats s'affichent dessous, toujours dans le même ordre. Quand un candidat n'a rien publié, la case dit « Pas de position publiée sur ce thème ».
4. Touchez **Lire la source** : l'application demande d'abord si vous voulez quitter PPF. Touchez **Rester ici**.
5. **Sa fiche dans « Voir »** ouvre la fiche du candidat.
6. Sur l'ordinateur, la même page est un tableau : une colonne par candidat, une ligne par thème.

### Tester les rappels d'une élection (ajouté le 9 novembre 2026)

L'élection est réelle : la présidentielle de 2027, dont les dates sont annoncées (premier tour le 18 avril 2027, second tour le 2 mai 2027) mais pas encore fixées par le décret de convocation. Aucun compte n'est nécessaire, et rien ne part sur Internet : les rappels restent sur l'appareil.

1. Ouvrez **Décider**. Dans **Prochaines élections**, touchez la carte « Élection présidentielle de 2027 ».
2. Descendez jusqu'à **Rappels** : rien n'est programmé tant que vous ne le demandez pas. Touchez **Me rappeler ces dates**. Sur le téléphone, Android vous demande d'autoriser les notifications de PPF : touchez **Autoriser** (si vous refusez, les rappels arriveront seulement dans la cloche de l'application).
3. La liste **Prochains rappels** apparaît, avec la date et l'heure de chacun (par exemple « Veille du scrutin, le 17 avril 2027 à 10 h »).
4. Coupez l'interrupteur **Veille du scrutin** : ces rappels disparaissent de la liste. Touchez la croix d'un rappel : il disparaît, et ne reviendra pas.
5. Dans **Mon espace**, notifications : la ligne **Votes et pétitions** est allumée. Éteignez-la puis revenez sur l'élection : un encadré dit que les rappels ne s'afficheront pas, avec un bouton pour la rallumer.
6. Touchez **Ne plus me rappeler cette élection** : tout est effacé, le bouton du début revient.
7. Les vrais rappels arrivent à leur date ; pour en voir un tout de suite, il faudra une élection d'essai plus proche, préparée pour la phase de test.

À l'installation, Android indique que PPF peut « se lancer au démarrage » (permission `RECEIVE_BOOT_COMPLETED`) : elle sert seulement à remettre l'alarme du prochain rappel après un redémarrage du téléphone, ne lit aucune donnée et n'envoie rien.

### Tester les consultations publiques (ajouté le 9 novembre 2026)

Depuis le 9 novembre 2026, la consultation est réelle : le débat public sur le projet de train Réunion Express, ouvert jusqu'au 26 novembre 2026. Comme il concerne La Réunion, il n'apparaît que si une commune de La Réunion est choisie sur l'appareil. Pour l'essayer : dans **Comprendre**, rubrique **Mes élus**, choisissez par exemple Saint-Denis (code postal 97400) ; rien n'est envoyé. Après le 26 novembre, la section disparaît jusqu'à la prochaine consultation. Aucun compte n'est nécessaire. Les étapes ci-dessous décrivent les consultations d'essai des tests automatiques ; sur le téléphone, vous verrez le débat de La Réunion à la place.

1. Ouvrez **Décider** et descendez jusqu'à **Consultations publiques** : la consultation en ligne d'un ministère d'exemple (ouverte, avec le nombre de jours qui restent pour répondre), puis l'enquête publique d'exemple (« À venir », avec sa date d'ouverture).
2. Touchez la consultation en ligne : la page dit qui l'organise, ses dates, ce que prévoit la loi (avec les articles), et que PPF ne recueille aucune réponse.
3. Touchez **Répondre sur le site officiel** : l'application demande d'abord si vous voulez quitter PPF. Touchez **Rester ici** (l'adresse d'exemple ne mène nulle part).
4. Revenez en arrière et ouvrez l'enquête publique. Dans **Rappels**, touchez **Me rappeler cette consultation** : deux rappels apparaissent, à l'ouverture et trois jours avant la clôture, à 10 h. Touchez **Ne plus me rappeler cette consultation** : tout est effacé.

### Tester « Interpeller ses élus » (ajouté le 9 novembre 2026)

Le modèle de courrier est réel et utilisable tel quel : il demande à un député ou à un sénateur comment il votera un texte, à choisir vous-même. Rien n'est envoyé : c'est vous qui envoyez le courrier, si vous le voulez.

1. Ouvrez **Décider** et descendez jusqu'à **Interpeller ses élus**. Touchez le modèle « Avant un vote ».
2. Sans commune choisie, la page vous le dit : le courrier garde « [Nom de l'élu] » et « [Votre commune] ». Pour voir le nom de vos élus s'inscrire, choisissez d'abord votre commune dans **Comprendre**, rubrique **Mes élus**, puis revenez sur le modèle.
3. Modifiez le courrier directement dans la zone de texte, puis touchez **Copier le courrier** : un message confirme la copie. Collez-le dans une note pour vérifier.
4. Les boutons de **Où l'envoyer** ouvrent, après « Vous quittez PPF », la page de contact officielle (des adresses d'exemple pour l'instant).
5. Dans **Vos envois**, touchez **J'ai envoyé ce courrier** : une note apparaît avec la date du jour, gardée sur ce téléphone seulement. Touchez **J'ai reçu une réponse**, puis la croix pour effacer la note.
6. Quittez la page : le texte du courrier n'est gardé nulle part.

### Tester les pétitions locales (ajouté le 9 novembre 2026)

La pétition locale est inventée et marquée [EXEMPLE] ; elle est rattachée au code de commune 69123, et n'apparaît que si cette commune est choisie sur l'appareil.

1. Dans **Comprendre**, rubrique **Mes élus**, choisissez la commune 69123 (code postal 69001). Rien n'est envoyé : le code postal ne quitte pas l'appareil.
2. Ouvrez **Décider** : la section **Pétitions locales** apparaît, avec « 640 signatures sur 1 200 demandées ».
3. Touchez la carte : la page dit à quelle collectivité la demande s'adresse, comment signer (ici sur papier), et ce que dit la loi (un dixième des électeurs d'une commune, une seule demande par trimestre, le conseil décide librement).
4. Sans commune choisie, la section n'apparaît pas.

### Demander un document administratif (ajouté le 9 novembre 2026)

1. Ouvrez **Décider**, section **Demander un document administratif**, puis **Rédiger une demande et suivre ses délais**.
2. Écrivez une administration et un document, tous deux inventés et marqués [EXEMPLE] (par exemple « [EXEMPLE] Mairie de Lyon » et « [EXEMPLE] Délibération du 3 octobre 2026 »), choisissez **Copie papier** : la demande se remplit, avec les articles de loi. Touchez **Copier la demande**.
3. Dans **Mes demandes**, laissez la date du jour ou écrivez une date passée (par exemple 09/10/2026), puis **Noter cette demande** : la demande apparaît avec la date limite de réponse. Avec une date de plus d'un mois, elle est dite refusée et la date limite pour saisir la CADA s'affiche ; **Préparer la saisine de la CADA** montre le texte à copier.
4. **Effacer cette demande** la retire de l'appareil. Rien n'est envoyé à aucun moment.

### « Mon bulletin a été compté » (ajouté le 9 novembre 2026)

Quand une urne est close (par exemple une revendication que l'équipe a retenue), sa page propose **Recompter et vérifier mon bulletin**. Votre appareil télécharge la liste anonyme des bulletins, la même pour tout le monde, refait tout le compte et y cherche votre bulletin. Il vous dit si votre bulletin est compté avec votre choix, sans jamais afficher ce choix ni garder la réponse : elle ne peut servir de preuve à personne, et c'est voulu (personne ne peut vous obliger à prouver votre vote). En cas de problème, une alerte s'affiche.

Pas encore essayable à la main : il faut qu'une urne soit close, ce que fait l'équipe depuis le back-office. Les tests automatiques vérifient la recherche du bulletin et le recomptage.

### Essayer le vote anonyme sur l'ordinateur (ajouté le 7 novembre 2026, au soir)

Le vote marche maintenant de bout en bout, mais seulement avec un serveur. Sur votre ordinateur, un serveur de développement suffit ; il ne contient que des exemples. Il faut deux fenêtres de commande, ouvertes dans le dossier du projet.

1. Dans la première fenêtre, lancez le serveur de développement et laissez-le tourner :

   `gradlew.bat :server:runDev`

   Attendez la ligne « Development server: … http://localhost:8080 ».
2. Dans la seconde fenêtre, lancez l'application de bureau reliée à ce serveur :

   `set PPF_SERVER_URL=http://localhost:8080`

   puis

   `gradlew.bat :desktop:run`
3. Dans l'application, créez un compte dans **Mon espace** (une minute, avec les douze mots), puis ouvrez **Revendications**.
4. Les trois revendications d'exemple « En débat » ou « Très soutenue » ont chacune leur urne. La ligne sous les boutons passe de « s'ouvre dans quelques minutes » à « Votre vote est anonyme » ; comptez deux à quatre minutes après le démarrage du serveur.
5. Touchez **Pour**. Le bouton devient actif, et la ligne dit que votre vote partira tout seul dans quelques minutes, sans votre compte. Ce délai tiré au hasard est voulu : c'est ce qui empêche de relier votre vote à votre compte. Laissez l'application ouverte une dizaine de minutes.
6. La ligne devient « Votre vote est enregistré, anonymement », et les résultats « Citoyens : 1 pour, 0 contre » s'affichent. Les votes des autres arrivent ensuite toutes les trente minutes environ.
7. Vous pouvez changer d'avis (touchez **Contre**) ou **Retirer mon vote** jusqu'à la clôture.
8. Les revendications « Nouvelle », « Retenue » et « Campagne » n'ont pas d'urne : la ligne l'explique.

Pour arrêter, fermez l'application, puis tapez Ctrl+C dans la première fenêtre. Rien n'est gardé : le serveur de développement oublie tout à l'arrêt.

### Tester les partis, le droit de réponse et le signalement d'erreur dans « Voir » (ajouté le 7 novembre 2026)

1. Ouvrez **Voir**, onglet **Le pouvoir** : la rubrique **Partis politiques** arrive avant les responsables publics, les partis par ordre alphabétique. Touchez un parti : toutes les fiches ont les mêmes rubriques dans le même ordre (financement, sièges, aide publique, direction, membres, faits qui visent le parti), même vides.
2. Sur la fiche d'une personne, la puce de son parti ouvre la fiche du parti.
3. En bas de n'importe quel contenu, touchez **Signaler une erreur** : par défaut, l'envoi est anonyme (l'équipe ne pourra pas vous répondre) ; si vous avez un compte, « Avec mon compte » affiche d'abord une mise en garde.
4. Sur la fiche d'une personne ou d'un fait qui la concerne, touchez **Droit de réponse** : le formulaire explique la loi, compte les mots en direct et demande un compte (créé en une minute dans « Mon espace » ; votre texte est gardé pendant ce temps).
5. Sur l'ordinateur, avec le serveur de développement (`gradlew :server:runDev`), ouvrez http://localhost:8080/voir : les mêmes contenus sur le site, lisibles sans JavaScript.

### Tester les parlementaires et leurs votes (ajouté le 7 novembre 2026)

Tout est fictif et marqué [EXEMPLE] : 12 députés, 4 sénateurs, 3 eurodéputés, 6 scrutins.

1. Ouvrez **Voir**, onglet **Le pouvoir**, puis la carte **Tous les parlementaires et leurs votes** : un bandeau « Données fictives [EXEMPLE] » et la mention de la source avec sa date.
2. Cherchez un nom sans accent, filtrez par chambre ou par groupe.
3. Ouvrez un député : sa participation avec sa définition, puis ses votes écrits en toutes lettres (« Pour », « Contre », « Abstention », « Non-votant »), sans couleur de jugement. Un vote corrigé après coup montre la mise au point à côté du vote enregistré.
4. Ouvrez un scrutin : le résultat, le décompte et le tableau par groupe.
5. Ouvrez un sénateur : « Votes du Sénat : bientôt », avec le lien vers sa page officielle (après la mise en garde « Vous quittez PPF »).

### Tester « Cette semaine au Parlement » (ajouté le 7 novembre 2026)

Tout est fictif et marqué [EXEMPLE] : quelques séances de l'Assemblée nationale, du Sénat et du Parlement européen sur les quinze jours qui suivent l'import d'exemple.

1. Ouvrez **En direct** : un bloc « Cette semaine au Parlement » montre les quatre prochains points (heure, assemblée, objet). Touchez **Voir toute la semaine**.
2. La page présente un jour après l'autre (les jours passés sont masqués). Filtrez par assemblée : Assemblée nationale, Sénat, Parlement européen.
3. Chaque point indique l'heure, le type de réunion, l'objet et, quand la source le dit, son état (« Confirmé », « Prévisionnel », « Annulé ») en badge neutre.
4. Touchez un lien officiel (dossier législatif, ordre du jour) : la fenêtre « Vous quittez PPF » s'ouvre d'abord. Essayez aussi « Qui décide de ça ? » (espace Comprendre) et « Interpeller mes élus » (espace Décider).
5. La même page s'ouvre depuis **Voir**, onglet **Le pouvoir**, carte **Tous les parlementaires et leurs votes**, puis, sous la liste, la carte « Cette semaine au Parlement » et son bouton **Voir l'ordre du jour**.

### Tester l'espace « Voir » (ajouté le 6 novembre 2026, tard le soir)

Tout ce que vous verrez est inventé et marqué [EXEMPLE] : personnes, pays, organisations et chiffres. Aucun compte n'est nécessaire, et tout marche sans réseau.

1. Ouvrez le menu (les trois traits en haut à gauche) et touchez **Voir**. Trois onglets apparaissent : **Le mouvement**, **Le pouvoir**, **Libertés**. Faites glisser le doigt de droite à gauche pour passer de l'un à l'autre, comme dans le Fil.
2. Dans « Le mouvement », la première carte « Manifestants lors des journées nationales » montre **trois chiffres côte à côte** (autorités, organisateurs, comptage indépendant) et l'étiquette « Sources divergentes ». Touchez-la.
3. La fiche du chiffre s'ouvre : les trois sources avec leur fiabilité et un lien vers chacune, le graphique d'évolution (une courbe par source ; touchez un point pour voir les valeurs du jour), puis **Voir les données en tableau**. Plus bas : toutes les mesures, la méthode, la correction du 18 octobre, et « Et maintenant ? ».
4. Touchez **Copier avec la source**, puis collez dans une note de votre téléphone : le texte emporte chaque source, sa date et la mention [EXEMPLE].
5. Touchez **Suivre**, puis le **bouton retour** de votre téléphone : vous revenez à l'accueil de « Voir », où « Vos suivis » affiche ce chiffre. Cette liste reste sur votre téléphone.
6. Sur la fiche « Interpellations », touchez « Et maintenant ? » puis **Que faire en garde à vue** : la fiche pratique s'ouvre dans « Comprendre ».
7. Onglet **Le pouvoir** : touchez **Dominique Fictif**. Toutes les fiches ont les mêmes rubriques dans le même ordre (votes, promesses, discours et votes, affaires…), même vides. Dans « Affaires judiciaires », l'encadré de la **présomption d'innocence** ; touchez l'affaire : l'étape de la procédure (« Enquête en cours »), la correction du 5 juin, puis le **droit de réponse de Dominique Fictif** publié sous le contenu, et le bouton « Droit de réponse » qui explique la procédure.
8. Touchez le **projet de loi relatif à l'ordre public** : l'étape (4 sur 7, Sénat), le prochain vote, les deux recours au 49.3, **Qui gagne, qui perd**, les votes des élus et les rencontres avec des représentants d'intérêts.
9. Onglet **Libertés** : le tableau de bord des contre-pouvoirs, l'**observatoire des libertés publiques** (l'arrêté d'Exempleville apparaît ici et dans la chronologie : un même fait, deux endroits), les dossiers, le **journal des corrections**.
10. Touchez **Établi, allégué, fiabilité : ce que veulent dire les étiquettes** : le lexique explique chaque étiquette et chaque étape d'une procédure.
11. En haut, cherchez « temoin » (sans accent) : Sacha Témoin est trouvée.
12. Le bouton retour du téléphone ferme maintenant la page ouverte, puis ramène au Fil, partout dans l'application (avant, il quittait l'application).

Sur l'ordinateur, la fiche d'un chiffre ou d'un texte place la méthode et « Et maintenant ? » à côté du contenu quand la fenêtre est assez large ; la touche Échap fait comme le bouton retour.

### Tester la messagerie (ajouté le 6 novembre 2026 au soir)

### Ce qu'il faut savoir avant de commencer

- Pour s'écrire, deux comptes doivent passer par le même serveur. Le serveur de développement ne fonctionne que sur votre ordinateur, et l'application Android n'est pas encore reliée à un serveur : **un échange entre votre téléphone et votre ordinateur n'est pas encore possible**. Il le sera quand le serveur de préproduction sera en ligne. Sur le téléphone, vous pouvez déjà écrire un message : il reste sur le téléphone, marqué « En attente d'envoi ».
- Pour voir un vrai échange, le plus simple est d'ouvrir **deux fois l'application sur l'ordinateur, avec deux comptes d'essai**, reliées au serveur de développement. Chaque fenêtre garde ses données dans un dossier d'essai séparé : vos vraies données ne sont pas touchées.

#### Conversations privées et flux sur le téléphone

### Sur le téléphone Android (APK)

1. Installez l'APK de cette version comme d'habitude, ouvrez l'application, créez un compte si ce n'est pas fait.
2. Messages : la liste est vide et indique « Pas de serveur pour l'instant ». Vérifiez l'affichage plein écran : « Nouvelle conversation », collez une clé `npub1…` (par exemple celle d'un compte d'essai de l'ordinateur, envoyée par un moyen à vous), « Commencer », écrivez : le message s'affiche avec l'horloge « En attente d'envoi ». La flèche de retour en haut à gauche ramène à la liste.
3. Onglet « Flux » : même essai qu'au point 12 ci-dessus.

#### Salons des collectifs et conservation des messages sur le téléphone

### Sur votre téléphone Android

L'application installée sur le téléphone n'est encore reliée à aucun serveur : elle montre les salons enregistrés sur l'appareil (aucun pour l'instant) et explique que la création demande le serveur. Les essais complets (créer un salon, écrire, voir un doublon refusé) se feront sur l'ordinateur (ci-dessous), puis sur le téléphone dès que la préproduction existera.

1. Installez le nouvel APK comme d'habitude, puis ouvrez l'application.
2. Touchez **Messages** dans la barre du bas, puis l'onglet **Collectifs** (ou glissez le doigt de droite à gauche).
3. Vous voyez : le champ « Rechercher un salon », le bouton bleu « Créer un salon », la ligne « Hors ligne : salons enregistrés sur l'appareil. », le titre « Mes salons » avec « Vous n'avez rejoint aucun salon pour l'instant. », puis « Aucun salon connu pour l'instant… », la carte « Vous ne trouvez pas votre collectif… » et, tout en bas, le rappel avec un cadenas : rejoindre un salon ne publie rien.
4. Touchez le champ de recherche et écrivez « santé ». La ligne « Aucun salon ne correspond à « santé ». » apparaît, et le bouton de la carte devient « Créer le salon « santé » ». Touchez-le.
5. Le formulaire prend tout l'écran. Vérifiez : le nom est déjà rempli avec « santé » ; un encadré jaune dit que l'appareil n'est relié à aucun serveur. Écrivez « Lyon » dans **Territoire** : la ligne « Identifiant du salon : sante-lyon » apparaît sous la description.
6. Faites défiler : la charte (« Autorisé », « Interdit ») et la case « J'ai lu la charte de valeurs… », **décochée**. Touchez-la : elle se coche. Plus bas, si vous n'avez pas de compte, un encadré propose « Créer mon compte » ; si vous en avez un, un encadré jaune « Votre pseudonyme sera relié à ce salon », puis « Annuler » à gauche et « Créer le salon » à droite, grisé faute de serveur.
7. Touchez la croix en haut à droite : vous revenez à la liste.
8. Touchez votre avatar en haut à droite (**Mon espace**). Créez un compte si vous n'en avez pas (comme d'habitude). Faites défiler jusqu'à la carte **« Messages privés : conservation sur le serveur »** : « 3 mois » est choisi, avec la mention « Par défaut, la durée la plus protectrice », et la ligne « Vous n'avez rien choisi : le serveur garde vos enveloppes 3 mois. »
9. Touchez **« 6 mois »** : une fenêtre « Garder vos enveloppes plus longtemps ? » s'ouvre. Touchez **Annuler** : « 3 mois » reste choisi.
10. Touchez **« 1 an »**, puis **« Garder 1 an »** : « 1 an » est choisi et la ligne dit « Réglage enregistré sur cet appareil, en attente d'envoi au serveur. »
11. Touchez **« 3 mois »** : aucune fenêtre (revenir à la durée la plus protectrice ne demande rien).
12. Tournez le téléphone en paysage : les écrans se réorganisent sans rien perdre.

#### Échanger un message entre deux comptes sur l'ordinateur

### Échanger un message entre deux comptes sur l'ordinateur

1. Ouvrez une fenêtre PowerShell dans le dossier `C:\Users\clair\Documents\PPF` (dans l'Explorateur, ouvrez ce dossier, puis Fichier, « Ouvrir Windows PowerShell »).
2. Tapez `.\gradlew.bat :server:runDev` puis Entrée. Attendez la ligne qui se termine par `http://localhost:8080`. Laissez cette fenêtre ouverte : c'est le serveur. Il oublie tout quand on le ferme.
3. Ouvrez une deuxième fenêtre PowerShell dans le même dossier et tapez `.\gradlew.bat :desktop:createDistributable` puis Entrée (une seule fois ; quelques minutes la première fois).
4. Dans cette deuxième fenêtre, lancez le premier compte d'essai en collant ces trois lignes, puis Entrée :
   ```
   $env:APPDATA = "$env:USERPROFILE\ppf-essai-1"
   $env:PPF_SERVER_URL = "http://localhost:8080"
   & ".\desktop\build\compose\binaries\main\app\PolitisationPopulaireFrancaise\PolitisationPopulaireFrancaise.exe"
   ```
5. Ouvrez une troisième fenêtre PowerShell dans le même dossier et lancez le second compte de la même façon, avec `ppf-essai-2` à la place de `ppf-essai-1`.
6. Dans chacune des deux fenêtres de l'application : Mon espace, « Créer mon compte », suivez les étapes (douze mots, que vous pouvez ne pas garder puisque ce sont des comptes d'essai).
7. Dans la première fenêtre : Messages. En bas de la liste, sous « Votre clé publique, à donner aux personnes qui veulent vous écrire », sélectionnez la clé `npub1…` à la souris et copiez-la (Ctrl+C).
8. Dans la seconde fenêtre : Messages, « Nouvelle conversation », cliquez dans le champ « npub1… ou clé hexadécimale », collez (Ctrl+V), puis « Commencer ». La conversation s'ouvre à droite de la liste. Écrivez un message (il doit commencer par [EXEMPLE]) et appuyez sur Entrée.
9. Dans la première fenêtre : au plus tard 20 secondes après (ou tout de suite avec le bouton d'actualisation, la flèche en cercle à côté de « Conversations »), la conversation apparaît avec une pastille rouge « 1 ». Cliquez dessus : le message s'affiche, la pastille disparaît. Répondez : la réponse arrive dans la seconde fenêtre de la même façon.
10. Hors ligne : fermez le serveur (dans sa fenêtre, Ctrl+C). Écrivez un message dans une des fenêtres : il s'affiche avec une petite horloge et la mention « Serveur injoignable ». Relancez le serveur (`.\gradlew.bat :server:runDev`) : à l'actualisation suivante, l'horloge disparaît et le message arrive de l'autre côté.
11. Petit groupe : avec un troisième compte d'essai (`ppf-essai-3`), utilisez « Ajouter » dans « Nouvelle conversation » pour mettre deux clés ; donnez un sujet si vous voulez.
12. Flux : dans le Fil, suivez Politisation Populaire Française avec le bouton « Suivre » d'une publication ; dans Messages, onglet « Flux », le compte apparaît ; cliquez dessus pour lire ses publications comme une conversation à sens unique.
13. Explication : dans une conversation, le bouton « i » en haut à droite ouvre « Comment vos messages sont protégés ».
14. Déconnexion : dans Mon espace, « Se déconnecter de cet appareil » prévient que les conversations seront effacées ; après confirmation, Messages invite de nouveau à créer un compte.
15. À la fin, fermez les fenêtres. Les dossiers `ppf-essai-1`, `ppf-essai-2` (et `ppf-essai-3`) dans votre dossier personnel `C:\Users\clair` ne contiennent que ces comptes d'essai : vous pouvez les supprimer vous-même dans l'Explorateur.

#### Salons des collectifs sur l'ordinateur

Avec le serveur de développement lancé (étape 2 ci-dessus) et un compte d'essai ouvert (étape 4) :

### Sur l'ordinateur, avec le serveur de développement (quand la branche du serveur des salons sera fusionnée)

1. Lancez le serveur de développement : `gradlew :server:runDev`.
2. Lancez l'application de bureau reliée à ce serveur : variable `PPF_SERVER_URL=http://localhost:8080`, puis `gradlew :desktop:run`.
3. Créez un compte dans Mon espace, puis Messages, Collectifs, **Créer un salon** : le formulaire s'affiche à droite de la liste. Nom « [EXEMPLE] Collectif vélo », territoire « Lyon », cochez la charte, touchez **Créer le salon** : le salon s'ouvre à droite, il apparaît dans « Mes salons » avec « Rejoint ».
4. Écrivez un message et touchez l'avion en papier : le message apparaît en bas, à droite, signé « Vous ».
5. Recommencez la création avec « [EXEMPLE] Collectif vélo » et « lyon » : l'encadré **« Ce salon existe déjà »** propose **« Rejoindre ce salon »**. Touchez-le : le salon existant s'ouvre.
6. Arrêtez le serveur, écrivez un message : il porte « En attente d'envoi ». Relancez le serveur et touchez le bouton « Actualiser les messages » (les deux flèches) : la mention disparaît.
7. Dans Mon espace, choisissez « 6 mois » et confirmez : la ligne dit « Réglage enregistré sur le serveur. »

### Tester les parcours « Comprendre en 5 minutes » (ajouté le 6 novembre 2026)

1. Ouvrez le menu (bouton en haut à gauche) et touchez **« Comprendre »**. L'écran commence par **« Comprendre en 5 minutes »** : quatre parcours de démonstration marqués [EXEMPLE] (l'élection présidentielle, comment une loi est votée, la dissolution, le 49.3 et la motion de censure). Chaque carte indique la durée, le nombre d'étapes et de questions, « Produit avec l'aide d'une IA », et « Pas encore commencé ». Les fiches pratiques suivent, sous « Connaître ses droits ».
2. Touchez **« Comment une loi est votée »** : la première page présente le parcours avec son encart « En bref ». Touchez **« Commencer »**.
3. Chaque étape affiche « Étape 1 sur 5 », une barre de progression, un texte court, puis une **question**. Choisissez une réponse et touchez **« Valider ma réponse »** : l'application dit si c'est juste, montre la bonne réponse et l'explication avec l'article de loi. Après une mauvaise réponse, **« Réessayer »** permet de recommencer la question.
4. Passez d'une étape à l'autre avec **« Étape suivante »** et **« Étape précédente »**. Revenez à l'accueil par la flèche en haut à gauche : la carte indique « En cours : étape 2 sur 5 ». Rouvrez le parcours : le bouton propose **« Reprendre à l'étape 2 »**.
5. À la fin, la page **« Parcours terminé »** donne vos bonnes réponses, propose **« Et maintenant ? »** (le bouton « Voir les revendications des citoyens » ouvre le mur des revendications) et liste les sources.
6. Fermez complètement l'application et rouvrez-la : la progression est toujours là. Elle n'est enregistrée que sur votre téléphone, jamais envoyée, et n'est pas liée à votre compte (on peut tout faire sans compte).
7. En bas de l'accueil de « Comprendre », **« Effacer ma progression »** demande confirmation, puis remet tous les parcours à « Pas encore commencé ».
8. Mode avion : tout fonctionne, les parcours sont dans l'application. Sur l'ordinateur, l'accueil reste à gauche et le parcours s'affiche à droite.

Ces quatre parcours sont des brouillons rédigés avec l'aide d'une IA, vérifiés sur Légifrance (version en vigueur au 6 novembre 2026), le Conseil constitutionnel, l'Assemblée nationale et service-public ; ils doivent être relus par l'équipe éditoriale avant toute publication réelle. Pour en modifier un ou en demander un nouveau, dites-le-moi.

### Tester les décryptages, l'histoire des luttes et « Qui décide de ça ? » (ajouté le 7 novembre 2026)

1. Dans **« Comprendre »**, faites défiler l'accueil : après les parcours viennent **« Qui décide de ça ? »**, **« Le vrai du faux »** (les décryptages), **« Connaître ses droits »** (les fiches) puis **« Histoire des luttes »**. Tous les contenus sont des exemples marqués [EXEMPLE].
2. Touchez un décryptage : l'affirmation est citée entre guillemets, avec l'endroit où elle circule, puis le **verdict** écrit en toutes lettres (par exemple « Contredit par les faits »). Le lien **« Comment nous choisissons un verdict »** ouvre la page « Notre méthode » qui définit les cinq verdicts. Plus bas, l'encart **« Objections et réponses »** présente les arguments contraires et y répond avec des sources.
3. Touchez un récit de l'histoire des luttes (par exemple le Front populaire) : sous le titre, la période et le pays ; un encart dit **ce qui était demandé** et les **résultats obtenus** ; une **frise** donne les dates ; les objections et réponses présentent les débats des historiens.
4. Touchez **« Manifester : qui autorise, qui peut interdire ? »** (ou une autre fiche « Qui décide de ça ? ») : la fiche commence par l'encart **« Qui décide ? Les élus concernés »** : chaque niveau (commune, État...) avec ce qu'il décide et les élus concernés. Sous « État », le nom d'une personne d'exemple (« Dominique Fictif ») apparaît, liée par l'équipe. Une phrase indique que les noms de vos élus locaux apparaîtront quand l'annuaire officiel des élus sera intégré.
5. En bas de chaque contenu, **« Et maintenant ? »** propose une action (le bouton ouvre le mur des revendications), puis les sources.
6. Les parcours ont maintenant huit sujets ; certains se terminent aussi par des objections et réponses.

### Tester la vague 3 de « Comprendre » : vos élus, le glossaire, les nouveaux contenus (ajouté le 7 novembre 2026)

Trois nouveautés, que vous avez demandées le 7 novembre : l'annuaire des élus, le glossaire (les mots compliqués expliqués partout) et davantage de contenus. Tout est marqué [EXEMPLE] et rédigé avec l'aide d'une IA, à relire par l'équipe éditoriale.

#### Sur le téléphone (version d'essai `android-preview.apk`, sans serveur)

Installez la dernière version d'essai comme d'habitude (section 2, « Installer ou mettre à jour l'APK »).

**Vos élus (en attendant le serveur)**

1. Ouvrez le menu (les trois traits en haut à gauche) et touchez **Comprendre**. Juste après « Qui décide de ça ? », touchez **Mes élus**.
2. Cette version n'a pas encore de serveur : la page l'explique et propose les **annuaires officiels** (mairies, Assemblée nationale, Sénat, Parlement européen, Répertoire national des élus). Touchez-en un : la fenêtre **« Vous quittez PPF »** s'ouvre d'abord. Touchez **Rester sur PPF** (ou continuez si vous voulez voir le site officiel).
3. Ouvrez une fiche « Qui décide de ça ? » (par exemple « Le logement et les loyers ») : dans l'encart « Qui décide ? Les élus concernés », une phrase dit que les noms de vos élus locaux viendront plus tard, avec un bouton **Annuaires officiels des élus**.

**Le glossaire**

4. Ouvrez un parcours, par exemple **« Le Conseil constitutionnel »**. Dans le texte, certains mots sont **soulignés en pointillés**, suivis d'un petit **« ? »** dans un rond (par exemple « Constitution » ou « QPC »). Seule la première apparition de chaque mot est soulignée, jamais dans les titres, les liens ou les sources.
5. Touchez le mot ou le « ? » : une **infobulle** s'ouvre sous le mot, avec le terme, le badge [EXEMPLE], la définition rapide et « Produit avec l'aide d'une IA ».
6. Touchez **En savoir plus** : la page du mot s'ouvre par-dessus votre lecture (« En bref », l'explication détaillée, « Voir aussi », « Pour aller plus loin », les sources). Touchez un mot de « Voir aussi » : il s'ouvre à son tour ; le bouton retour revient au mot précédent. Fermez avec la croix ou le retour du téléphone : vous retrouvez votre texte là où vous l'aviez laissé.
7. Touchez à côté d'une infobulle ouverte : elle se ferme.
8. Revenez à l'accueil de « Comprendre » : sous les parcours, la rubrique **Glossaire** propose « Ouvrir le glossaire (64 mots) ». Touchez-la : les mots sont rangés par ordre alphabétique sous leur lettre. Dans la recherche, tapez `senat` (sans accent) puis `49` : le Sénat, puis le 49.3, apparaissent.
9. Les mots soulignés apparaissent aussi dans les autres espaces (« Voir », « Agir », revendications, fiches pratiques). Rien de ce que vous consultez n'est enregistré ni envoyé.

**Les nouveaux contenus**

10. **Parcours** (16 en tout) : huit nouveaux, dans cet ordre de priorité : « S'inscrire sur les listes électorales et voter », « Le droit de grève », « Le Conseil constitutionnel », « Qui fait quoi : la séparation des pouvoirs », « L'Union européenne : qui décide quoi ? », « Commune, département, région : qui s'occupe de quoi ? », « Se présenter à une élection », « Les pétitions officielles ». Chacun se lit étape par étape avec une question à la fin de chaque étape.
11. **« Qui décide de ça ? »** (12 en tout) : huit nouvelles fiches : logement et loyers, hôpital et médecins, eau du robinet, salaires et Smic, impôts locaux, retraites, ramassage des déchets, police municipale et nationale.
12. **Fiches pratiques** (« Connaître ses droits », 5 en tout) : « Faire grève : vos droits » et « Voter par procuration ».
13. Mode avion : tout fonctionne (sauf l'ouverture des annuaires officiels, qui sont des sites extérieurs).

#### Sur l'ordinateur, avec le serveur de développement (annuaire des élus fictifs)

Le serveur de développement sert un annuaire **fictif** : les noms sont inventés (« [EXEMPLE] Prénom Nom »), mais les communes, les codes postaux et les circonscriptions sont réels, pour Lyon, Lille, Marseille, Rennes, Paris et quelques communes voisines. Les vrais noms ne seront chargés qu'en production.

1. Ouvrez une fenêtre PowerShell dans le dossier `C:\Users\clair\Documents\PPF` (dans l'Explorateur, ouvrez ce dossier, puis Fichier, « Ouvrir Windows PowerShell »).
2. Tapez `.\gradlew.bat :server:runDev` puis Entrée. Attendez la ligne qui se termine par `http://localhost:8080`. Laissez cette fenêtre ouverte : c'est le serveur. Pour vérifier, ouvrez http://localhost:8080/api/v1/elus dans le navigateur : un texte technique commence par `{"format":1,"example":true`.
3. Ouvrez une deuxième fenêtre PowerShell dans le même dossier et collez ces deux lignes, puis Entrée :
   ```
   $env:PPF_SERVER_URL = "http://localhost:8080"
   .\gradlew.bat :desktop:run
   ```
   L'application s'ouvre au bout de quelques instants (quelques minutes la première fois).
4. Dans l'application : **Comprendre**, puis **Mes élus**, puis **Indiquer ma commune**. Une phrase rappelle que votre commune reste sur votre ordinateur et que le serveur sait seulement qu'un appareil a téléchargé la liste d'un département.
5. Tapez le code postal `69001` (Lyon) et touchez **Continuer**. L'application annonce la **taille** de la liste du Rhône sur le bouton (« Télécharger (… ko) ») : rien n'est téléchargé avant que vous touchiez ce bouton. Touchez-le.
6. S'il y a plusieurs communes pour ce code postal, choisissez la vôtre ; s'il n'y en a qu'une, elle est choisie tout de suite.
7. La page **Mes élus** s'ouvre avec le bandeau **« Données fictives [EXEMPLE] »**, puis vos élus par niveau : commune (le maire, les adjoints dans l'ordre, puis le conseil municipal), intercommunalité, département, région, État (vos députés et sénateurs), Union européenne. Pour Lyon, le niveau « Département » montre l'**assemblée de la Métropole de Lyon**, qui remplace le conseil départemental. En bas : la source, sa licence et ses dates, et les annuaires officiels.
8. Ouvrez une fiche « Qui décide de ça ? » (par exemple « L'école » ou « Le ramassage des déchets ») : sous chaque niveau de l'encart « Qui décide ? Les élus concernés », les noms fictifs de vos élus apparaissent, avec « Voir tous mes élus » et « Changer de commune ».
9. Revenez à « Mes élus », touchez **Changer de commune** et tapez `69400` (Villefranche-sur-Saône) : cette fois, le niveau « Département » montre les conseillers départementaux de son canton et la présidence du conseil départemental du Rhône, et non la Métropole.
10. Touchez **Oublier ma commune** : une confirmation s'ouvre, avec **Garder ma commune** en premier. Confirmez : la commune et les listes téléchargées sont effacées de l'ordinateur, et la page propose de nouveau « Indiquer ma commune ».
11. Hors ligne : choisissez de nouveau une commune, fermez l'application, arrêtez le serveur (Ctrl+C dans sa fenêtre) et relancez l'application (étape 3) : vos élus s'affichent toujours. **Rechercher une mise à jour** dit alors que le serveur ne répond pas et propose les annuaires officiels.
12. À la fin, fermez l'application et arrêtez le serveur.

### Tester les fiches pratiques (ajouté le 5 novembre 2026, encarts le 6)

1. Ouvrez le menu (bouton en haut à gauche) et touchez **« Comprendre »**. Chaque fiche commence par un encart bleu « En bref », puis des encarts verts « Légal » et rouges « Illégal » avec des listes à puces, et des encarts « Attention » (jaune) et « Conseils » (gris). Les titres des fiches sont en bleu de marque, le texte en Atkinson Hyperlegible, 16 points.
2. La rubrique **« Connaître ses droits »** liste trois fiches de démonstration marquées [EXEMPLE] : droits en manifestation, filmer les forces de l'ordre, garde à vue. La ligne d'état indique « Hors ligne : contenus intégrés à l'application ».
3. Touchez une fiche : son texte s'affiche avec les titres, les listes et les sources ; la ligne sous le titre indique le type, la date de mise à jour et l'origine (« Rédigé par un humain »). La flèche en haut à gauche revient à la liste.
4. Coupez le réseau (mode avion) et refaites les étapes 1 à 3 : tout doit fonctionner à l'identique, puisque les fiches sont dans l'application.
5. Sur l'ordinateur, la liste et la fiche s'affichent côte à côte quand la fenêtre est large.

Les textes de ces fiches sont des brouillons écrits par moi pour la démonstration, avec les sources de droit citées en bas de chaque fiche ; ils doivent être relus par un avocat avant toute publication réelle. Pour modifier une fiche ou en ajouter une, dites-le-moi : je modifie les fichiers de `content/fiches`, je les signe et je vous livre une nouvelle version.

### Voir « Comprendre » sur le site, sur votre ordinateur (ajouté le 7 novembre 2026)

Le site est la version de PPF pour les iPhone tant que l'application iOS n'existe pas. Il se lit sans JavaScript (le petit programme qui rend les pages interactives, souvent désactivé par prudence).

1. Lancez le serveur de développement : dans le dossier `C:\Users\clair\Documents\PPF`, faites un clic droit, « Ouvrir dans le terminal », tapez `gradlew :server:runDev` et appuyez sur Entrée. Attendez la ligne qui commence par « Comprendre: » (une à trois minutes).
2. Ouvrez votre navigateur à l'adresse http://localhost:8080/comprendre : les rubriques « Connaître ses droits », « Comprendre en 5 minutes », « Qui décide de ça ? », « Le vrai du faux », « Histoire des luttes », le glossaire et « Notre méthode ».
3. Ouvrez une fiche pratique, par exemple http://localhost:8080/comprendre/fiches/garde-a-vue : encarts en couleurs, puis « Les mots du glossaire de cette page », « Et maintenant ? » et les sources tout en bas. Les mots soulignés en pointillés mènent à leur page du glossaire.
4. Ouvrez un parcours, par exemple http://localhost:8080/comprendre/parcours/le-49-3-et-la-motion-de-censure : toutes les étapes sur une page ; sous chaque question, touchez « Voir la bonne réponse » pour la découvrir avec son explication. Rien n'est enregistré ni envoyé.
5. Essayez aussi http://localhost:8080/comprendre/glossaire (lettres et recherche), une fiche « Qui décide de ça ? » (les annuaires officiels en bas de l'encart) et http://localhost:8080/comprendre/hors-ligne (toutes les fiches sur une page, à enregistrer ou imprimer en PDF depuis le menu du navigateur).
6. Hors ligne : après avoir ouvert http://localhost:8080/comprendre une fois, arrêtez le serveur (Ctrl+C dans sa fenêtre) puis rouvrez une fiche : elle s'affiche toujours. Une page jamais ouverte affiche alors « Lire hors ligne ».
7. Mode sombre : réglez Windows en mode sombre (Paramètres, Personnalisation, Couleurs) ; les pages suivent. Réduisez la fenêtre à la largeur d'un téléphone : tout reste lisible.
8. À la fin, arrêtez le serveur (Ctrl+C).

### Voir « Décider » sur le site, sur votre ordinateur (ajouté le 8 novembre 2026)

1. Lancez le serveur de développement comme ci-dessus (`gradlew :server:runDev`) et attendez la ligne qui commence par « Décider: ».
2. Ouvrez http://localhost:8080/decider : le Pacte démocratique d'exemple, les pétitions officielles ouvertes, puis à part les pétitions closes.
3. Ouvrez une pétition, par exemple http://localhost:8080/decider/petitions/petition-senat-2456 : le bouton « Signer sur le site officiel » mène au site du Sénat (marqué « site extérieur ») ; PPF ne recueille aucune signature. Plus bas : les relevés du compteur officiel, les étapes et les sources.
4. Ouvrez le Pacte : http://localhost:8080/decider/pacte/pacte-election-exemple : les mesures, la méthode (même texte, même jour, même délai pour tous) et le tableau des réponses des candidats.
5. Réduisez la fenêtre à la largeur d'un téléphone et passez Windows en mode sombre : tout reste lisible. Arrêtez le serveur à la fin (Ctrl+C).

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

### Protéger l'ordinateur : chiffrer le disque (ajouté le 8 novembre 2026)

Pourquoi : sur un ordinateur, le code de PPF ferme la porte de l'application, mais ses fichiers restent lisibles par quelqu'un qui emporte l'ordinateur ou son disque. **Chiffrer le disque** (le rendre illisible sans votre session Windows) les protège vraiment ; c'est aussi ce que PPF conseillera aux militants qui utilisent l'application sur ordinateur. Votre ordinateur est sous **Windows 11 Famille** (vu le 8 novembre 2026) : suivez la partie A. La partie B ne concerne que Windows 11 Professionnel. Sources : pages du support de Microsoft en français, consultées le 8 novembre 2026 (« Chiffrement d'appareil dans Windows », « Rechercher votre clé de récupération BitLocker », « Chiffrement de lecteur BitLocker »).

**Savoir quelle édition de Windows vous avez** : clic droit sur le bouton **Démarrer**, **Paramètres**, **Système**, puis tout en bas **Informations système** (Microsoft l'appelle aussi « À propos ») ; l'édition se lit sous **Spécifications Windows** (« Windows 11 Famille » ou « Windows 11 Professionnel »). Raccourci : touches **Windows + R**, tapez `ms-settings:about`, **OK**.

**A. Windows 11 Famille : « Chiffrement de l'appareil »**

1. Ouvrez votre session Windows avec un compte **administrateur** (c'est le cas du compte principal de l'ordinateur).
2. Touches **Windows + R**, tapez `ms-settings:deviceencryption`, puis **OK** : la bonne page des Paramètres s'ouvre directement. (Par les menus : **Démarrer**, **Paramètres**, **Confidentialité et sécurité**, **Chiffrement de l'appareil**.)
3. Si l'interrupteur **Chiffrement de l'appareil** est sur **Désactivé**, mettez-le sur **Activé**. Le chiffrement se fait pendant que vous continuez à utiliser l'ordinateur.
4. Si vous ouvrez Windows avec un **compte Microsoft**, la **clé de récupération** (48 chiffres, à taper si Windows ne reconnaît plus l'ordinateur, par exemple après un changement de matériel) est enregistrée dans ce compte. Pour la noter sur papier : depuis un autre appareil, allez sur `aka.ms/myrecoverykey`, connectez-vous, repérez votre ordinateur et recopiez la clé. Rangez ce papier loin de l'ordinateur. Microsoft ne peut ni retrouver ni refaire une clé perdue.
5. Si vous ouvrez Windows avec un **compte local** (sans adresse Microsoft), le chiffrement ne s'active pas tout seul. Faites quand même les étapes 2 et 3, puis dites-moi ce que Windows affiche à propos de la clé : je vous dirai comment la sauvegarder.

**Si « Chiffrement de l'appareil » n'apparaît pas** : votre ordinateur ne remplit pas une condition. Pour savoir laquelle : **Démarrer**, tapez `Informations système`, clic droit sur le résultat, **Exécuter en tant qu'administrateur**, puis lisez la ligne **Prise en charge du chiffrement automatique de l'appareil** :
- « Conforme aux conditions préalables » : l'option devrait être là ; vérifiez que vous êtes sur un compte administrateur ;
- « Le module de plateforme sécurisée n'est pas utilisable » : la puce de sécurité (TPM) est absente ou désactivée dans les réglages de démarrage (BIOS ou UEFI) ;
- « La liaison PCR7 n'est pas prise en charge » : le démarrage sécurisé est désactivé, ou un appareil est branché au démarrage (station d'accueil, carte graphique externe) ;
- « WinRE n'est pas configuré » : l'environnement de récupération de Windows manque.
Dans ces trois derniers cas, envoyez-moi une capture de cette ligne : changer les réglages de démarrage demande de la prudence, je vous guiderai pas à pas. Passer à Windows 11 Professionnel pour avoir BitLocker est payant : je ne le recommande pas sans en parler d'abord.

**B. Windows 11 Professionnel : BitLocker**

1. Session **administrateur**. **Démarrer**, tapez `BitLocker`, puis choisissez **Gérer BitLocker** (si ce choix n'apparaît pas, votre Windows est l'édition Famille : partie A).
2. Dans la fenêtre **Chiffrement de lecteur BitLocker**, en face du disque `C:`, cliquez sur **Activer BitLocker**.
3. Laissez Windows déverrouiller le disque automatiquement à l'ouverture de session.
4. **Sauvegardez la clé de récupération**, de préférence de deux façons : **Enregistrer dans votre compte Microsoft**, et **Imprimer la clé de récupération** (ou **Enregistrer sur une clé USB**). Ne gardez jamais la clé dans un fichier sur le disque que vous chiffrez, ni la clé USB ou le papier rangés avec l'ordinateur. **Suivant**.
5. Choisissez **Chiffrer le lecteur entier** (recommandé : un disque déjà utilisé garde des traces de fichiers effacés que « l'espace utilisé uniquement » laisserait lisibles).
6. Choisissez **Nouveau mode de chiffrement**, puis **Continuer**, et redémarrez si Windows le demande. Le chiffrement continue en arrière-plan ; l'ordinateur reste utilisable.

Les libellés des écrans de BitLocker viennent d'une page de Microsoft traduite automatiquement : ils peuvent varier un peu à l'écran. En cas de doute, une capture me suffit.

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
- **Clé d'administration du serveur** (7 novembre 2026) : `C:\Users\clair\.ssh\ppf_server_ed25519`. Sa partie publique (`.pub`) est la seule à aller sur le serveur, collée par vous lors de la commande (guide du serveur). L'autre fichier ne quitte jamais votre ordinateur. En cas de vol de l'ordinateur, dites-le-moi : je la retire du serveur.
- **Mot de passe des pages web de la préproduction** : vous le choisissez et le tapez vous-même au premier déploiement, dans l'onglet de terminal que j'ouvre ; il n'est écrit nulle part (le serveur n'en garde qu'une empreinte) : enregistrez-le dans votre gestionnaire de mots de passe.
- Ne m'envoyez jamais un mot de passe ou une clé par la conversation : je n'en ai pas besoin, tout est sur votre ordinateur.

## 6 bis. La préproduction en ligne (ajouté le 7 novembre 2026)

La **préproduction** est la version d'essai de PPF sur Internet, sur le vrai serveur, mais fermée au public : seuls vos appareils, reliés par un tunnel WireGuard, peuvent l'ouvrir, et elle ne contient que des données fictives [EXEMPLE]. C'est là que vous validez chaque nouveauté avant qu'elle parte en production.

- **Adresse** : https://preprod.politisation-populaire-francaise.org (tunnel actif, puis identifiant `equipe` et le mot de passe ci-dessus).
- **Mise en place**, dans l'ordre : `docs/guides/liaison-github.md` (GitHub), `docs/guides/serveur-hostinger-kvm1.md` (achat et configuration du serveur, après votre accord sur le prix), `docs/guides/wireguard-preproduction.md` (vos deux appareils).
- **Mises à jour** : dès qu'un changement est accepté dans la branche principale, GitHub fabrique une nouvelle version du serveur, la signe, et la préproduction l'installe d'elle-même dans les 10 minutes. Si la nouvelle version ne démarre pas correctement, le serveur revient seul à la précédente.
- **L'APK d'essai** se connecte tout seul à la préproduction quand le tunnel est actif sur le téléphone : fil, messages, salons et propositions passent alors par le vrai serveur, et deux téléphones peuvent échanger.
- **La production** (le site public) ne reçoit une version qu'avec votre accord écrit, et seulement une version que vous avez vue tourner en préproduction.

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
- **Préproduction** : la copie d'essai de la plateforme, en ligne mais fermée au public, avec des données fictives.
- **WireGuard** : un logiciel de tunnel chiffré ; il relie un appareil au serveur comme un câble privé.
- **Image** (Docker) : le serveur emballé avec tout ce qu'il faut pour tourner, identique en préproduction et en production.
- **Empreinte** (digest) : le code unique d'une image ; deux images différentes n'ont jamais la même empreinte.
- **DNS** : l'annuaire d'Internet, qui associe un nom (`preprod.politisation-populaire-francaise.org`) à l'adresse d'un serveur.
