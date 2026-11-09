# Note d'intégration : minuteur « Sans nouvelles de moi » (AG-55), étape 1

Rédigée le 9 novembre 2026 par un agent de la session « Agir » (branche `worktree-agent-a3d74e88d479cf770`, partie de `feature/agir` 75d234e). Décision : **D-088** (`docs/DECISIONS.md`). Format : `docs/FORMATS.md`, section 4 quattuordecies.

> **Rien n'est compilé ni testé.** Les compilations de branches étaient interdites ce jour-là (machine fragile, autre équipe en cours de compilation). Le code et les tests ont été écrits en lisant les sources réelles de chaque API appelée ; ils doivent être compilés et lancés (`gradle-safe.ps1`, section 9) avant toute entrée dans main.

## 1. Ce qui est fait

Cahier fonctionnel, AG-55 : « À une heure fixée, sans "je vais bien" confirmé par un code, l'alerte préparée part vers les cercles choisis et, si on le veut, la legal team ; déposée chiffrée à l'avance sur leurs appareils, elle s'affiche d'elle-même, même si mon téléphone est saisi ; un code de détresse simule l'annulation. »

1. **Préparer** (mode manifestation, rubrique « Sans nouvelles de moi », après le point de ralliement) : choisir les destinataires parmi les contacts des conversations privées et, facultativement, une legal team vérifiée de l'annuaire qui a un compte ; l'heure (dans 2 h, 4 h, 6 h, « Ce soir, 23:59 », ou une heure précise, aujourd'hui ou demain) ; le message de l'alerte (modèle modifiable) ; le lieu prévu, facultatif, **écrit à la main** (jamais une position lue sur l'appareil) ; le code « je vais bien » (4 à 8 chiffres, tapé deux fois, codes trop simples refusés) et le code de détresse (facultatif, conseillé, différent).
2. **Principe 10** : rien n'est armé par défaut. « Continuer » ouvre d'abord une explication : combien de personnes recevront l'alerte, déposée dès maintenant ; l'heure ; qu'elle partira même si le téléphone est éteint, perdu ou saisi ; qu'un oubli alerte les proches. « Annuler » avant « Armer le minuteur ».
3. **Armer** : un dépôt scellé par destinataire part par la messagerie privée ; l'écran dit combien sont partis et, s'il en reste, qu'ils partiront au retour du réseau (et que l'alerte ne peut pas s'afficher chez ces personnes tant qu'ils ne sont pas partis).
4. **Minuteur en cours** : l'heure, le temps qui reste, les personnes prévenues, la mise en garde ; « Je vais bien » (le code) et « Repousser » (nouvelle heure : dans 1 h, 2 h, 4 h après l'heure prévue, ce soir, ou une heure précise ; puis le code). Après l'heure, l'écran le dit, et « Je vais bien » envoie quand même l'annulation.
5. **Code de détresse** : tapé dans « Je vais bien », il affiche exactement le même écran « Minuteur annulé » que le vrai code, n'envoie rien, et l'alerte part à l'heure prévue. Ensuite, l'appareil ne montre plus aucun minuteur. Tapé dans « Repousser », il affiche « Minuteur repoussé » et ne déplace que l'heure affichée (l'alerte part à l'heure que connaissent les destinataires).
6. **Chez les destinataires** : avant l'heure, une ligne discrète « [pseudonyme] vous a désigné(e) pour un minuteur jusqu'à HH:MM » dans le bloc « Minuteurs de vos proches », en tête de l'accueil d'« Agir » ; rien du contenu de l'alerte. À l'heure, sans annulation valide, l'alerte s'affiche d'elle-même en tête de l'accueil d'« Agir » et dans la feuille AIDE (juste après le 15 et le 112) : le message, le lieu prévu, l'heure de la dernière synchronisation des messages, la phrase honnête « Si votre téléphone était hors ligne, une annulation a pu ne pas encore arriver », et « En cas de danger immédiat, appelez le 112 ». « Masquer cette alerte » demande d'abord confirmation (seulement si l'on a eu des nouvelles par un autre moyen). Une confirmation reçue s'affiche en ligne discrète pendant 12 heures.
7. **Relevés et notification** : tant qu'un minuteur d'un proche court et que l'application est ouverte, les messages privés sont relevés toutes les 2 minutes (pour qu'une annulation arrive avant l'heure plutôt qu'après). Quand une alerte devient due pendant que l'application est ouverte, les messages sont relevés aussitôt (une annulation peut être en route), puis, si l'alerte est toujours due au battement suivant (15 secondes), une entrée part dans le centre de notifications (catégorie « Mobilisations », seulement si la personne l'a activée), titre neutre sans nom ni texte : « Un proche n'a pas donné de nouvelles ».
8. **Sans compte, sans serveur, sur iOS** : l'écran explique et ne propose rien. **Compte déclaré compromis** (S-146) : « Continuer » est grisé et `PublishBlockedNotice` dit pourquoi ; « Je vais bien » reste possible (sinon l'alerte partirait à tort).
9. **Effacement rapide** (S-44) : partie **`agir-minuteur`**, inscrite dans `AdaptiveShell` juste après `agir-rally-point` : le minuteur, ses envois en attente et les minuteurs reçus, effacés puis vérifiés ; l'effacement attend la fin. **Il n'annule jamais un dépôt déjà sur l'appareil d'un destinataire** : l'alerte part quand même, c'est le but si le téléphone est saisi ou effacé. « Effacer mes clés » efface aussi le minuteur, comme les conversations (sans les clés, plus d'annulation possible). La table de la section 2 bis de `agir-securite-appareil.md` ne liste que les parties des autres pôles : cette partie d'« Agir » est donc décrite ici.

## 2. Le protocole

```
Armer (appareil de la personne A)
  s = 32 octets aléatoires ; h = SHA-256(s) ; id = 32 octets aléatoires
  clé = poivre( PBKDF2-HMAC-SHA-256(code « je vais bien », sel, 600 000) )   (poivre : clé HMAC du Keystore Android)
  boîte = s XOR HKDF-SHA-256(clé, nonce, "ppf-minuteur-secret-v1")          (le secret n'est jamais gardé en clair)
  pour chaque destinataire D : rumeur 8593 { p=D, ppf-action=depot, ppf-timer=id, ppf-deadline, ppf-hash=h, location?, texte }
                              → sceau (signé par A, chiffré pour D) gardé sur l'appareil → enveloppe → serveur
« Je vais bien » (vrai code)
  clé → ouvre la boîte → s ; vérifie SHA-256(s) = h
  pour chaque D : rumeur { ppf-action=annulation, ppf-timer=id, ppf-secret=s } → file d'envoi (après ce qui attend encore)
« Repousser » (vrai code)
  s, nouveau secret s' ; h' = SHA-256(s') ; la boîte cache désormais s'
  pour chaque D : rumeur { ppf-action=report, ppf-secret=s, ppf-deadline=nouvelle, ppf-hash=h' }
Code de détresse : rien n'est préparé ni envoyé ; « Je vais bien » efface le minuteur de l'écran, « Repousser » ne déplace que l'heure affichée.
Chez D : garde dépôt et révélations (dans n'importe quel ordre), suit la chaîne h → h' → … ; annulation valide → confirmé ; sinon, à l'échéance, alerte.
```

- **Vérifier un code** = ouvrir la boîte et comparer l'empreinte : le code « je vais bien » n'est même pas gardé en empreinte. Le code de détresse est une empreinte lente (`PinRecord`). Les deux dérivations tournent à chaque essai, quel que soit le code tapé : le temps ne trahit pas lequel. Attente croissante après 5 codes faux (`UnlockDelays`, comme l'écran de verrouillage), gardée dans le minuteur.
- **Envois en attente** : comme les messages privés, ce sont des sceaux déjà chiffrés, **réemballés avec des dates fraîches** à chaque essai (sinon la fenêtre de relevé des destinataires pourrait les manquer) ; envoyés dans l'ordre (un dépôt ou un report avant l'annulation qui suit) ; un envoi en cours prend aussi ce qui arrive pendant qu'il tourne ; réessayés toutes les minutes tant que l'application est ouverte et à chaque action.
- **Heures** : écrites HH:MM, heure de Paris, comme l'agenda (la personne et ses proches lisent la même heure).

## 3. Ce que voit le serveur

- Rien de nouveau : des **enveloppes NIP-59** (type 1059, déjà relayées pour les messages privés), signées par une clé jetable, avec la seule étiquette `p` du destinataire et une date aléatoire jusqu'à deux jours dans le passé. Il ne voit **jamais** le type 8593, ni l'auteur, ni l'échéance, ni le texte, ni le lieu ; il ne sait pas qu'un minuteur existe.
- **Métadonnées** : le serveur voit qu'une enveloppe est déposée pour telle clé, au moment où l'appareil l'envoie (armement, « Je vais bien », « Repousser », retour du réseau). Un observateur du serveur pourrait remarquer qu'un groupe de clés reçoit des enveloppes au même moment, sans savoir de qui ni pourquoi, comme pour un message de groupe (limite connue de D-027). Avec le code de détresse, aucune enveloppe ne part : rien à observer.
- Aucun code serveur, aucune migration (ni SQLDelight, ni Flyway), aucune dépendance nouvelle, aucun hôte tiers.

## 4. Fichiers

Nouveaux (pôle Agir) :
- `shared/core/.../core/agir/timer/TimerFormats.kt` (type, étiquettes, construction et lecture des rumeurs), `TimerCodes.kt` (règles des codes, dérivation, boîte du secret), `TimerData.kt` (document JSON de l'appareil, `TimerStorage`, `TimerStore`), `TimerService.kt` (armer, « Je vais bien », « Repousser », envois, `TimerTransport`, `MessagingTimerTransport`), `TimerInbox.kt` (réception, chaîne des empreintes, phases, oubli), `TimerRecipients.kt` (`TimerCandidate`, interface `TimerRecipientSource`) ;
- `shared/ui/.../ui/agir/timer/TimerState.kt` (état de la coquille, `rememberTimerState`, `LocalTimerStorage`, `expect rememberPlatformTimerStorage`), `TimerScreen.kt` (préparation, explication, minuteur en cours, dialogue du code, écran après une action), `TimerBlocks.kt` (entrée du mode manifestation, alertes, « Minuteurs de vos proches », bloc de l'accueil), `TimerParts.kt` (balises de test, choix de l'heure, formats des heures) ;
- `actual` : `androidMain/.../ui/agir/timer/TimerStorage.android.kt` (préférence privée `ppf-minuteur`), `jvmMain/.../TimerStorage.jvm.kt` (fichier `minuteur.json` du dossier privé), `iosMain/.../TimerStorage.ios.kt` (rien pour l'instant) ;
- `shared/ui/src/commonMain/composeResources/values/strings_minuteur.xml` ;
- tests : `shared/core/src/commonTest/.../agir/timer/TimerServiceTest.kt`, `TimerInboxTest.kt`, `shared/core/src/jvmTest/.../agir/timer/TimerMessagingTest.kt`, `shared/ui/src/jvmTest/.../agir/timer/TimerScreenTest.kt`.

Fichiers d'Agir modifiés : `ui/agir/manifestation/HelpState.kt` (propriété `timer`, `legalTeamCandidates()`), `ManifestationScreen.kt` (rubrique et écran du minuteur), `HelpSheet.kt` (alertes après le 15 et le 112), `ui/agir/AgirHome.kt` (bloc en tête de l'accueil, une ligne), test `ui/agir/security/DeviceSecurityTest.kt` (`agir-minuteur` dans la liste des parties attendues).

**Fichiers d'autres pôles touchés** (crochets minimaux, section 5) : `core/messaging/MessagingService.kt` et le nouveau `core/messaging/IncomingRumors.kt` (Communauté) ; `core/signing/EventKinds.kt` (socle) ; `ui/shell/AdaptiveShell.kt` (coquille). **Aucun fichier de M24 « Collectifs »** (`core/agir/collectives`, `ui/agir/collectives`, `server/agir/collectives`, `strings_collectifs.xml`, rubrique « Collectifs » d'`AgirScreen.kt`) n'est touché ; `AgirScreen.kt` n'est pas modifié du tout.

## 5. Crochets dans les fichiers des autres pôles (lignes exactes)

- **Communauté, `shared/core/.../core/messaging/IncomingRumors.kt`** (nouveau) : registre `IncomingRumors(expected)` ; `register(kind, handler)` rend une fonction de retrait ; `takes(kind)` ; `dispatch(rumors, recipient)` rend le nombre de rumeurs « retenues » ; `IncomingRumors.Process` = le registre du processus, qui attend le type 8593.
- **Communauté, `shared/core/.../core/messaging/MessagingService.kt`** :
  - lignes 25 à 32 : `ReceiveReport` gagne `others: List<Rumor> = emptyList()` (valeur par défaut : les comparaisons existantes ne changent pas), additionné par `plus` ;
  - lignes 60 et 61 : paramètre `others: IncomingRumors = IncomingRumors.Process` du constructeur (les appels existants, `MessagingSession` et `Notifications.onWake`, ne changent pas) ;
  - lignes 145 à 147 (commentaire) et 153 à 170 (`receive`) : une rumeur authentique d'un autre type que 14, que `others.takes(kind)` accepte, est mise de côté dans `others` au lieu d'être comptée `ignored` ; rien d'autre ne change (les types inconnus restent ignorés) ;
  - lignes 194 et 202 à 208 (`pull`) : après chaque page, `others.dispatch(...)` ; si une rumeur est retenue, `setLastPullAt` n'est pas appelé : le relevé suivant la relit (sans cela, un réveil Android avant l'ouverture d'un écran perdrait un dépôt, et l'alerte ne partirait jamais).
- **Socle, `shared/core/.../core/signing/EventKinds.kt`**, lignes 25 à 27 : `const val TIMER: Int = 8593`.
- **Coquille, `shared/ui/.../ui/shell/AdaptiveShell.kt`**, ligne 103 (import) et lignes 240 à 242, juste après `RegisterErasable(dependencies, "agir-rally-point") { help.clearRallyPoint() }` :
  ```kotlin
  val timer = rememberTimerState(dependencies, help, accountSession, messaging)
  RegisterErasable(dependencies, "agir-minuteur") { timer.eraseFromDevice() }
  ```
  `rememberTimerState` inscrit le gestionnaire du type 8593 dans `IncomingRumors.Process` pour la durée de la coquille, suit le compte (`AccountSession`), relève les messages quand une alerte devient due (`MessagingSession.refresh`), lit les contacts (`MessagingSession.conversations`, sans les comptes déclarés compromis), l'heure du dernier relevé (`MessageStore.lastPullAt`) et le poivre du verrouillage (`AppDependencies.securityStorage`), et dépose les entrées du centre par `Notifications.engine.deliver` (API existante, inchangée).

## 6. Limites (dites honnêtement)

- **Destinataire hors ligne à l'échéance** : son appareil ne sait pas qu'une annulation est partie ; il peut afficher une alerte annulée. L'alerte le dit, avec l'heure de la dernière synchronisation ; l'application relève les messages dès qu'une alerte devient due, et une annulation arrivée ensuite la transforme en confirmation.
- **Téléphone du destinataire éteint à l'échéance** : l'alerte s'affiche à la prochaine ouverture de PPF (le dépôt est déjà sur l'appareil, aucun réseau n'est nécessaire).
- **Pas de réveil précis à l'échéance** tant que les rappels de l'appareil de « Décider » ne sont pas dans main : l'alerte s'affiche quand l'application est ouverte (battement toutes les 15 secondes), ou au prochain tour du centre. Voir section 7.
- **Dépôt pas encore parti** (armé hors ligne) : tant qu'il n'a pas quitté le téléphone, l'alerte ne peut pas s'afficher chez ce destinataire ; si le téléphone est saisi avant, elle ne partira jamais. L'écran le dit en clair (« Pas encore déposé chez … »).
- **Code court** : 4 à 8 chiffres. Sur Android, le poivre du Keystore empêche d'essayer tous les codes sur un ordinateur à partir d'une copie des fichiers ; **sur ordinateur, sans poivre**, un code de 4 chiffres se casse vite à partir d'une copie : le chiffrement du disque reste la vraie protection (guide de la propriétaire).
- **Code de détresse et contrainte prolongée** : si la personne est forcée à taper son code alors qu'elle n'a pas choisi de code de détresse, l'annulation part. Le code de détresse est fortement conseillé à la préparation.
- **Après un code de détresse**, l'appareil ne permet plus d'annuler pour de vrai : pour arrêter une fausse alerte, la personne prévient ses proches par un autre moyen (ils peuvent masquer l'alerte). Voir la question 5.
- **Un contact compromis** (S-146) : la personne qui détient sa clé pourrait envoyer de fausses alertes ou annulations ; les contacts déclarés compromis ne sont pas proposés comme destinataires, mais une alerte reçue d'un compte déclaré compromis n'est pas encore signalée comme telle.
- **Plusieurs appareils de la personne** : le minuteur vit sur l'appareil qui l'a armé (aucune copie vers ses autres appareils) ; l'annuler demande cet appareil.

## 7. Ce qui reste

- **Cercles (M28, Communauté)** : remplacer la source des contacts par une source « cercles » (`TimerRecipientSource`, une seule fonction), sans toucher au reste ; les cercles restent sur l'appareil, rien ne change pour le serveur.
- **Réveil précis** : quand `ReminderStore` et `ReminderAlarms` (contrat `docs/integration/rappels.md`, branche `feature/decider-une-voix`) seront dans main : à la réception d'un dépôt (`TimerInbox.accept`), programmer `ReminderStore.schedule(Reminder(id = "agir-minuteur:<auteur>:<id>", owner = "agir:minuteur:<id>", category = MOBILISATIONS, title = "Un proche n'a pas donné de nouvelles", detail = …, target = "ppf:agir/minuteur", dueAt = échéance))`, le déplacer à chaque report valide (`replaceFuture`), le retirer à l'annulation valide (`remove`) ; la partie `core-reminders` de l'effacement rapide les emporte avec elle. Le texte du rappel ne doit jamais contenir le nom ni le message (écran verrouillé).
- **Source de notification en arrière-plan** : une `NotificationSource` des alertes dues, donnée à `Notifications(extraSources = …)` dans `PpfApplication` (Android) et `AppDependencies.withDatabase`, demanderait que le stockage du minuteur soit construit par l'hôte (il l'est aujourd'hui par l'interface) : à faire avec le Directeur technique, en même temps que le réveil précis.
- **Lien `ppf:agir/minuteur`** : à ouvrir dans `openEntry` d'`AdaptiveShell` (aujourd'hui l'entrée du centre est seulement marquée lue).
- **Client des legal teams** : une vue dédiée pour une legal team qui reçoit beaucoup d'alertes (tri, suivi, qui s'en occupe), et le choix de la legal team par territoire ; aujourd'hui une legal team qui a un compte PPF reçoit l'alerte comme un contact.
- **Notifications poussées** : le réveil du destinataire par ntfy n'apporte rien de plus qu'un message privé ; il marche déjà si la personne a activé les réveils de la messagerie (le relevé en arrière-plan retient le dépôt jusqu'à l'ouverture d'un écran, section 5).
- **iOS** : fichier du conteneur privé exclu des sauvegardes iCloud, avec la compilation iOS.
- **Compilation et essais** (section 9), puis essai sur deux téléphones Android en phase de test.

## 8. Questions pour le chef d'équipe (avec recommandations)

1. **Le code de détresse doit-il aussi avancer l'alerte** (l'envoyer tout de suite plutôt qu'à l'heure prévue) ? Avantage : les proches sont prévenus plus tôt d'une contrainte. Inconvénients : il faudrait envoyer quelque chose au moment même où la personne est surveillée (l'enveloppe peut se voir dans le trafic réseau), et une fausse manipulation alerterait aussitôt. **Recommandation : non pour l'instant** ; l'alerte part à l'heure prévue, sans aucun envoi au moment du code. À reconsidérer avec les retours des organisations.
2. **L'alerte doit-elle inclure par défaut le dernier lieu prévu** (par exemple le point de ralliement déjà gardé sur l'appareil) ? **Recommandation : non** ; le lieu reste facultatif et tapé à la main pour chaque minuteur (principe 10 : le plus protecteur par défaut ; un lieu ancien peut tromper les proches). On peut proposer un bouton « Reprendre mon point de ralliement » qui remplit le champ, sans rien cocher d'office.
3. **Code de détresse et avocat** : contrairement au code de contrainte de S-44 (non codé, D-082), celui-ci n'efface rien et ne cache aucune donnée : il simule une annulation auprès des proches. **Recommandation** : l'ajouter quand même à la liste « à faire relire par un avocat » (feuille de route), avec le texte de l'écran, pour vérifier qu'il ne peut pas être reproché à la personne (Code pénal, articles 434-4 et 434-15-2, qui visent l'effacement de preuves et le refus de remettre une convention de déchiffrement, pas un message à des proches).
4. **Legal teams** : faut-il proposer seulement les legal teams du territoire de la personne, et leur demander leur accord avant qu'elles puissent être choisies ? **Recommandation** : oui pour l'accord (une étiquette de l'annuaire « accepte les alertes "Sans nouvelles de moi" », posée par l'équipe éditoriale à la demande de la legal team), le territoire en premier comme pour « Besoin d'un avocat » ; en attendant, toute legal team vérifiée qui a un compte peut être choisie.
5. **Après un code de détresse, garder une annulation réelle possible ?** Par exemple, retaper plus tard le vrai code dans « Sans nouvelles de moi » annulerait pour de vrai. Avantage : moins de fausses alertes une fois la personne en sécurité. Inconvénient : il faudrait garder sur l'appareil, invisible mais lisible par une analyse, de quoi annuler, et une personne qui fouille le téléphone pourrait la forcer à le refaire. **Recommandation : non** ; la personne prévient ses proches par un autre moyen, et ils masquent l'alerte.
6. **Durées** : heure au plus tôt dans 15 minutes, au plus tard dans 48 heures ; minuteurs reçus oubliés 7 jours après leur échéance. **Recommandation** : garder ces valeurs pour l'étape 1, à revoir avec les organisations (une journée d'action, un déplacement de deux jours).
7. **Destinataires sans conversation** : faut-il pouvoir choisir quelqu'un sans conversation privée existante (par sa clé ou un code QR) ? **Recommandation** : pas avant les cercles (M28) : une conversation existante garantit que la personne sait déjà écrire chiffré à ce contact et qu'il utilise PPF.

## 9. Comment tester

Tests écrits, **non lancés** (à lancer par `gradle-safe.ps1`, dès que les compilations sont de nouveau permises) :
- `:shared:core:jvmTest --tests "*TimerServiceTest*" --tests "*TimerInboxTest*" --tests "*TimerMessagingTest*"` : dépôts scellés pour chaque destinataire (seul `p`, rien de lisible sur le serveur, ni code ni secret en clair sur l'appareil) ; annulation par le vrai code acceptée ; annulation avec un autre secret, pour un autre minuteur ou d'un autre auteur refusée ; report avec une nouvelle empreinte, puis chaîne qui annule ; code de détresse : même réponse, rien d'envoyé, aucune trace, alerte à l'heure ; report de détresse qui ne déplace que l'écran ; attentes croissantes ; hors ligne : dépôts puis annulations en file, dans l'ordre, reçus dans le désordre ; refus (destinataires, heure, texte, codes, minuteur déjà armé) ; ligne puis alerte, annulation tardive, alerte masquée qui ne revient pas ; lecture stricte des rumeurs ; oubli après 7 jours ; autre compte ; aller-retour JSON ; règles des codes ; crochet de la messagerie (rumeur remise au gestionnaire, jamais dans les conversations, curseur gardé tant que personne ne la prend ou si le gestionnaire échoue) ;
- `:shared:ui:jvmTest --tests "*TimerScreenTest*" --tests "*DeviceSecurityTest*"` : à 360, 700 et 1000 dp (sombre à 700) : explication avant l'armement, « Annuler » qui n'arme rien, armement, codes jamais gardés ; code faux puis vrai code qui envoie les annulations ; code de détresse : mêmes textes à l'écran que le vrai code, aucun envoi, plus de minuteur affiché ; « Repousser » qui envoie un report ; destinataire : ligne discrète avant l'heure (sans le message), alerte après, avec le lieu et la phrase sur le hors-ligne, masquage confirmé ; sans serveur ou sans compte, explication ; compte compromis, armement impossible ; choix de l'heure ; partie `agir-minuteur` inscrite dans l'effacement rapide.

Pour la propriétaire : **il faut deux comptes, et idéalement deux téléphones Android** (ou un téléphone et l'application de bureau, reliés à la préproduction). Voir `docs/GUIDE_PROPRIETAIRE.md`, section « Tester le minuteur "Sans nouvelles de moi" ».
