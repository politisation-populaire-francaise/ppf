# Note d'intégration : salons des collectifs et conservation des messages privés

Rédigée le 6 novembre 2026 par l'agent chargé des salons (branche `worktree-agent-a10def948163ead75`, partie de `main` à `c5b364f`). Elle reprend et termine le travail laissé inachevé sur la branche `worktree-agent-a8ac42353eeafbd6a` (commit `b1bf1f4`, fusionné ici). Elle s'adresse à la session principale, qui fusionne et met à jour `CHANGELOG.md`, `ROADMAP.md`, `REGISTRE_DONNEES.md`, `GUIDE_PROPRIETAIRE.md`, `THREAT_MODEL.md` et, si besoin, `DECISIONS.md` et `FORMATS.md`. Aucun de ces documents n'a été modifié ici.

Décisions appliquées : D-027 (salons NIP-29 sur notre serveur) et D-028 (création ouverte à tout compte, charte, territoire, refus des doublons ; conservation des messages privés réglable, trois mois au minimum), entrées du 2026-11-06 et du 2026-11-07 du brainstorming du cahier fonctionnel (M14).

## 1. Ce qui est fait

### Cœur (`shared/core`)

- **`core/groups/Group.kt`** : un salon est la description adressable de type 39000 (`d` = identifiant, `name`, `about`, `picture`, `ppf-territory`, `ppf-charter` = `1.0`) ; `Group.from` la lit, `Group.build` la prépare (salon public et ouvert, sans drapeau), `Group.slugify` normalise, `Group.idFor(nom, territoire)` propose l'identifiant d'un nouveau salon en joignant le nom et le territoire (`collectif-sante-lyon`, le territoire n'est pas répété s'il termine déjà le nom), `Group.numbered` donne `-2`, `-3`… quand l'identifiant est pris. `GroupMessage` : message de type 9 avec exactement une étiquette `h`, texte de 4000 caractères au plus.
- **`core/groups/GroupStore.kt`** et **`db/Groups.sq`** : la liste des salons rejoints, **sur l'appareil seulement** (table `group_membership` : identifiant du salon, date). Rejoindre ne publie rien ; le serveur n'a aucune liste de membres.
- **`core/groups/MailboxRetention.kt`** : le réglage de conservation, événement NIP-78 (type 30078, `d` = `ppf:mailbox`, étiquette `retention_days`) : 90, 180 ou 365 jours ; une valeur d'un autre client est bornée à 90..365 et lue comme le choix le plus proche.
- **`core/publication`** : `RemoteEvents.fetchGroups(query, territory, limit)` (`GET /api/v1/groups`) ; `PublicationResult.Rejected` porte `existing` (le salon à rejoindre pour `group_exists`, l'identifiant pris pour `group_taken`) ; nouvelles raisons `GROUP_EXISTS`, `GROUP_TAKEN`, `GROUP_CLOSED`, `GROUP_NOT_FOUND`, `MISSING_TERRITORY`, `MISSING_CHARTER`, `MISSING_NAME`, `DESCRIPTION_TOO_LONG`. `ServerPublicationPort` lit le corps des refus par une fonction testable (`rejection`), garde `existing` seulement si c'est un identifiant de salon valide, lit la liste des salons en tolérant une entrée sans nom (l'identifiant sert alors de nom) et garde `ProposalStatusSource` venu des revendications. `InMemoryPublicationPort` applique les mêmes règles que le serveur pour les tests.
- **Migration : `migrations/5.sqm`, schéma de la version 5 à la version 6** (table `group_membership`). `DatabaseMigrationTest` vérifie la version 6, une base de version 1 qui passe directement en 6, et une base de version 5 (avec une vue du mur des revendications) qui gagne la table et garde ses lignes.

### Interface (`shared/ui`)

- **Vue « Collectifs » des messages** (`ui/messages/CollectivesPage.kt`) : champ de recherche (filtre immédiat sur l'appareil, puis demande au serveur 400 ms après la dernière frappe), bouton « Créer un salon », état de la synchronisation, **suggestions par territoire** (une pastille par territoire des salons connus, le plus fréquent d'abord), **« Mes salons » en premier**, puis « Salons à découvrir » où les salons situés sur le territoire d'un salon rejoint passent devant (« Sur les territoires de vos salons »), puis l'invitation « Vous ne trouvez pas votre collectif… » qui ouvre le formulaire avec le texte cherché, et le rappel que rejoindre ne publie rien. Rien n'est demandé au serveur avant le premier affichage de la vue (économie de données).
- **Un salon** (`ui/groups/GroupScreen.kt`) : en-tête (marque, nom, territoire, badge [EXEMPLE], actualiser, Rejoindre ou Quitter), description repliable, **encadré « Salon public et modéré : tout le monde peut lire ces messages, qui ne sont pas chiffrés de bout en bout, et voir le pseudonyme qui signe chacun d'eux »**, messages du plus ancien au plus récent avec le plus récent en bas (bulles, « Vous » à droite pour les siens, mention « En attente d'envoi »), état du réseau, puis le champ d'écriture. Sans compte : lecture libre et encadré « Créer mon compte ». Avec un compte : « Rejoindre ce salon pour écrire », puis le champ et le bouton Envoyer. Un message écrit sans réseau reste sur l'appareil (`pending`) et part à l'actualisation suivante ; un message refusé (salon fermé ou disparu) n'est pas gardé et son texte revient dans le champ.
- **Création d'un salon** (`ui/groups/GroupCreationForm.kt`) : nom (80 caractères au plus), territoire (80 au plus, commune, département, région ou « France »), description facultative (1000 au plus), identifiant prévu affiché, **salons qui se ressemblent** (recherche avant création ; simple indice, car seul le serveur décide d'un doublon avec sa propre normalisation), charte (autorisé, interdit, case **décochée par défaut**), **avertissement « Votre pseudonyme sera relié à ce salon »** (la création publie la clé de son auteur à côté d'un collectif, ce qui peut révéler un engagement politique, syndical ou associatif, RGPD article 9), puis « Annuler » **avant** « Créer le salon ». Sur `409 group_exists`, encadré « Ce salon existe déjà » avec « Rejoindre ce salon » (le choix sûr, en premier) et « Modifier ma saisie » ; la description du salon existant est demandée au serveur par son identifiant si l'appareil ne la connaît pas. Sans serveur configuré : explication, bouton inactif. Serveur injoignable : message, saisie gardée. La saisie survit à une visite d'un autre espace.
- **Écrans adaptatifs** : sur téléphone et tablette, un salon ouvert ou le formulaire prend tout l'écran des messages, avec une flèche de retour ; sur ordinateur, la liste reste à gauche et le salon ou le formulaire s'affiche à droite (ou à la place de la liste si la page est trop étroite).
- **`ui/groups/GroupsLibrary.kt`** : l'état des salons (liste, recherche, territoire choisi, salons rejoints, salon ouvert, messages, brouillons du formulaire et du message), créé une fois par `AdaptiveShell` pour toute la session.
- **Mon espace** (`ui/account/MailboxCard.kt`, `ui/account/MailboxSetting.kt`) : carte « Messages privés : conservation sur le serveur » sous « Qui voit quoi » : explication de ce que garde le serveur (des enveloppes chiffrées qu'il ne peut pas ouvrir, effacées au bout de la durée choisie comptée depuis leur arrivée ; les appareils gardent leur copie sans limite), trois choix **3 mois (par défaut, le plus protecteur)**, 6 mois, 1 an, état du réglage, et rappel que le réglage est un événement signé lisible par tous qui ne dit rien des conversations. **Choisir une durée plus longue affiche d'abord une mise en garde** avec « Annuler » et « Garder 6 mois » (ou « Garder 1 an ») : le serveur ne lit toujours pas les enveloppes, mais une clé secrète volée un jour permettrait d'en lire davantage ; revenir à 3 mois ne demande rien. Le réglage est signé sur l'appareil, gardé s'il n'y a pas de réseau et envoyé ensuite (au prochain lancement ou au prochain choix) ; un refus du serveur rétablit le réglage précédent.
- **Effacer ses clés** efface aussi la liste des salons rejoints (branchement dans `AdaptiveShell`, à côté des comptes suivis).
- **Textes** : tous dans le nouveau fichier `composeResources/values/strings_salons.xml` (les textes ajoutés par l'agent précédent dans `strings.xml` y ont été déplacés ; `strings.xml` est identique à celui de `main`). Les textes de la charte (`propose_charter_*`) sont réutilisés depuis le formulaire des revendications. Aucun tiret cadratin, vouvoiement.
- **Accessibilité** : titres annoncés comme tels, bouton « Rejoindre » annoncé avec le nom du salon, bulles de message lues d'un seul tenant, case de la charte annoncée comme case à cocher, choix de durée comme boutons radio groupés, cibles de 44 à 48 dp, couleurs et police de la palette commune uniquement (modes clair et sombre vérifiés par captures).
- **Ce que la vue Collectifs n'utilise plus** : `PlannedView` et le texte `messages_encrypted_note`. Ils ne servent plus qu'aux vues Privé et Flux de `MessagesScreen.kt` ; la session principale peut les retirer quand la branche des conversations privées les aura remplacés.

### Fichiers touchés hors de mon domaine

- `shared/ui/.../messages/MessagesScreen.kt` : deux paramètres facultatifs (`groups`, `onNavigate`), le salon ouvert ou le formulaire prend tout l'écran sur téléphone et tablette (cinq lignes), appel de `CollectivesPage`.
- `shared/ui/.../shell/AdaptiveShell.kt` : `rememberGroupsLibrary`, `rememberMailboxSetting`, l'effacement des salons rejoints à la déconnexion, les deux états passés à `MessagesScreen` et `MySpaceScreen`.
- `shared/ui/.../account/MySpaceScreen.kt` : paramètre facultatif `mailbox`, carte insérée avant « Se déconnecter ».
- `shared/ui/src/commonTest/.../messages/MessagesScreenTest.kt` : la vue Collectifs n'affiche plus le texte d'attente (une ligne).
- `shared/core/.../publication/PublicationPort.kt`, `ServerPublicationPort.kt`, `RemoteEvents.kt`, `signing/EventKinds.kt` (types 9, 39000, 30078).

## 2. Tests

- **Cœur** (`gradlew :shared:core:jvmTest`) : `GroupTest` (7 : normalisation, identifiant d'un nouveau salon, aller-retour d'une description, refus, drapeaux, doublons, messages), `MailboxRetentionTest` (2), `InMemoryPublicationPortGroupsTest` (5 : annuaire et recherche, remplacement par une version plus récente, doublon et identifiant pris, descriptions mal formées dont sans nom, messages dans un salon ouvert seulement), `ServerPublicationPortParsingTest` (3 : codes d'erreur dont `description_too_long`, corps du `409 group_exists` et des autres refus, liste des salons avec entrées sans nom), `GroupStoreTest` (2), `DatabaseMigrationTest` (4, dont la migration de la version 5 à la version 6).
- **Interface** (`gradlew :shared:ui:jvmTest`) : `CollectivesScreenTest` (10 tests d'interface, téléphone 360 dp et ordinateur 1000 dp : rejoindre place le salon en tête et ne publie rien ; la recherche filtre et propose de créer le salon manquant ; suggestions par territoire ; lecture sans compte et retour à la liste ; message signé envoyé au salon puis quitter ; message écrit hors ligne en attente puis envoyé ; création sur ordinateur après la charte et l'avertissement ; doublon refusé par le serveur et proposition de rejoindre le salon existant ; salons qui se ressemblent et compte nécessaire ; sans serveur, création expliquée et saisie gardée après une visite du fil), `GroupsLibraryTest` (7 : ordre des suggestions et territoires, identifiant pris sur le serveur numéroté, identifiant connu sur l'appareil sauté, doublon décidé par le serveur, création sans serveur ou sans charte refusée, message refusé par un salon fermé qui rend son texte, limites du brouillon), `MailboxSettingTest` (4 : 3 mois par défaut et mise en garde avant 6 mois ou 1 an, réglage hors ligne envoyé au lancement suivant, refus qui garde le réglage précédent, réglage fait sur un autre appareil et autre compte sur le même appareil). Faux serveur commun : `RoomsServer` (règles du serveur, coupure du réseau, salons absents de l'annuaire complet).
- `GroupsScreenshotTest` : captures de la vue Collectifs, d'un salon, du formulaire, du doublon et du réglage de conservation (téléphone clair et sombre, ordinateur), seulement avec `PPF_SCREENSHOTS_DIR`.
- Tous les tests existants restent verts ; `gradlew build`, `tools/check-no-trackers.sh` et `tools/check-dependencies.sh` passent. Aucune dépendance nouvelle.

## 3. À reporter dans `docs/REGISTRE_DONNEES.md`

### Sur l'appareil (section 1 bis)

| Donnée | Finalité | Où | Durée | Accès |
|---|---|---|---|---|
| Liste des salons rejoints : identifiant de chaque salon et date | Afficher « Mes salons » en premier et proposer les salons des mêmes territoires | Table `group_membership` de la base locale (migration `5.sqm`) | Jusqu'à ce que l'utilisateur quitte le salon, efface ses clés (la liste est effacée avec elles) ou désinstalle l'application | L'utilisateur, par l'application. Jamais transmise : le serveur ne connaît aucun membre |
| Descriptions des salons (type 39000) et messages des salons ouverts (type 9), y compris les messages de l'utilisateur en attente d'envoi | Lecture hors ligne ; envoi différé | Table `event` de la base locale, comme les autres contenus | Comme les autres contenus reçus (pas d'effacement automatique pour l'instant) ; un message refusé par le serveur est effacé | L'utilisateur. Données publiques sur le serveur |
| Réglage de conservation de l'utilisateur (événement 30078 signé) | Afficher et envoyer le réglage | Table `event` | Remplacé à chaque nouveau choix ; il reste après l'effacement des clés (donnée publique, lue seulement pour la clé du compte affiché ; un autre compte part du réglage par défaut) | L'utilisateur |

Le réglage de conservation **n'utilise pas** la table `messaging_state` des conversations privées : `MessageStore.clear()` ne l'efface donc pas. Les brouillons (formulaire de création, message en cours) restent en mémoire et disparaissent à la fermeture de l'application.

Paragraphe à ajouter : les collectifs que l'on rejoint peuvent révéler une appartenance politique ou syndicale (RGPD, article 9) ; c'est pourquoi cette liste reste sur l'appareil, comme celle des comptes suivis.

### Sur le serveur (section 1)

| Donnée | Finalité | Durée | Visibilité |
|---|---|---|---|
| Description d'un salon : identifiant, nom, description, territoire, version de la charte acceptée, **clé publique de la personne qui l'a créé**, date, signature | Annuaire des salons, refus des doublons | Tant que le salon existe (suppression et modération : à définir avec le relais) | Publique |
| Message d'un salon : texte, salon, **clé publique de l'auteur**, date, signature | Lecture du salon par tous | À définir (aucune limite dans cette version) | Publique, non chiffrée |
| Réglage de conservation : clé publique, nombre de jours (90 à 365), date, signature | Durée de conservation des enveloppes de cette boîte | Remplacé à chaque nouveau choix | Publique (lisible par `GET /api/v1/events?kind=30078&author=…`) |

Aucune liste de membres, aucune adresse IP. Créer un salon ou y écrire relie publiquement un pseudonyme à un collectif : le formulaire et l'encadré du salon le disent avant l'action.

## 4. À reporter dans `docs/THREAT_MODEL.md`

> **Salons des collectifs.** Les salons publics ne sont pas chiffrés de bout en bout : leurs messages et la clé de leurs auteurs sont lisibles par tous, et le pseudonyme de la personne qui crée un salon reste attaché à sa description. Un pseudonyme qui écrit dans plusieurs salons révèle ses engagements à qui le lit. L'application le dit avant la création et dans chaque salon, garde sur l'appareil la liste des salons rejoints (aucune liste de membres sur le serveur) et efface cette liste avec les clés. Pour les engagements que l'on ne veut pas montrer : lire sans compte ou sans écrire, ou utiliser un autre compte ; les salons réservés et chiffrés viendront avec MLS.
>
> **Conservation des enveloppes.** Le réglage de conservation est public. Une durée plus longue garde plus longtemps des enveloppes chiffrées : sans confidentialité persistante (NIP-17), une clé volée permettrait d'en lire davantage. L'application l'explique avant d'allonger la durée ; le défaut reste 3 mois.

## 5. Guide de test pour la propriétaire (à reporter dans `docs/GUIDE_PROPRIETAIRE.md`)

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

### Sur l'ordinateur, avec le serveur de développement (quand la branche du serveur des salons sera fusionnée)

1. Lancez le serveur de développement : `gradlew :server:runDev`.
2. Lancez l'application de bureau reliée à ce serveur : variable `PPF_SERVER_URL=http://localhost:8080`, puis `gradlew :desktop:run`.
3. Créez un compte dans Mon espace, puis Messages, Collectifs, **Créer un salon** : le formulaire s'affiche à droite de la liste. Nom « [EXEMPLE] Collectif vélo », territoire « Lyon », cochez la charte, touchez **Créer le salon** : le salon s'ouvre à droite, il apparaît dans « Mes salons » avec « Rejoint ».
4. Écrivez un message et touchez l'avion en papier : le message apparaît en bas, à droite, signé « Vous ».
5. Recommencez la création avec « [EXEMPLE] Collectif vélo » et « lyon » : l'encadré **« Ce salon existe déjà »** propose **« Rejoindre ce salon »**. Touchez-le : le salon existant s'ouvre.
6. Arrêtez le serveur, écrivez un message : il porte « En attente d'envoi ». Relancez le serveur et touchez le bouton « Actualiser les messages » (les deux flèches) : la mention disparaît.
7. Dans Mon espace, choisissez « 6 mois » et confirmez : la ligne dit « Réglage enregistré sur le serveur. »

## 6. Ce qui reste

- Le texte d'avertissement de la déconnexion (`account_sign_out_warning`, dans `strings.xml`) ne mentionne pas encore l'effacement des salons rejoints : non modifié ici pour éviter un conflit avec la branche des conversations privées, à compléter à la fusion (« La liste de vos salons sera aussi effacée. »).
- Pas de mise à jour en direct : les nouveaux messages arrivent à l'ouverture du salon et avec le bouton « Actualiser » (abonnements en temps réel à prévoir avec le relais). Seuls les 200 messages les plus récents d'un salon sont demandés et affichés ; pas encore de chargement des plus anciens.
- Pas encore : rôles d'administrateurs et adhésions NIP-29 (types 9000 à 9022, refusés par le serveur), modification ou fermeture d'un salon par son créateur, image du salon, signalement d'un message ou d'un salon, modération a posteriori, rapprochement des doublons par le sens (IA auto-hébergée), fusion de salons, salons réservés chiffrés (MLS), notifications.
- Pas de bouton « retour » du système Android dans le salon ou le formulaire (la flèche à l'écran fonctionne) : aucun écran de l'application ne le gère encore.
- `docs/FORMATS.md`, section 5 : la réponse `409 group_exists` avec `existing`, les refus `missing_territory`, `missing_charter`, `missing_name`, `description_too_long`, `GET /api/v1/groups?q=&territory=&limit=` et l'événement 30078 sont à documenter (ou déjà documentés par la branche du serveur).
- Décisions prises en autonomie, à consigner si la propriétaire les confirme : mise en garde avant d'allonger la conservation (principe 10 du cahier des charges) ; création d'un salon possible seulement avec le serveur joignable (lui seul peut refuser un doublon) ; identifiant d'un salon formé du nom et du territoire ; salons qui se ressemblent montrés comme simple indice ; liste et salon côte à côte sur ordinateur.

## 7. Conflits attendus à la fusion

- `MessagesScreen.kt` et `AdaptiveShell.kt` : la branche des conversations privées change aussi la signature de `MessagesScreen` (`messaging`, `onNavigate`). Garder les deux : `groups` et l'appel `CollectivesFullScreen` / `CollectivesPage` d'ici, `messaging` de l'autre branche, un seul `onNavigate`.
- `MessagesScreenTest.kt` : une ligne changée ici (la vue Collectifs).
- Numéro de migration : si la branche des conversations privées ajoute elle aussi une migration (par exemple pour `messaging_state`), l'une des deux doit devenir `6.sqm` (schéma 6 à 7) et `DatabaseMigrationTest` passer à la version 7.
- `MySpaceScreen.kt` : un paramètre facultatif et une ligne dans `ReadyCards`.
