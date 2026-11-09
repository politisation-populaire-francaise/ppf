# Rappels personnels sur l'appareil (S-98, TE-09) : contrat commun

- Pôle « Décider », 9 novembre 2026 (décision D-118). Premier usage : les rappels des élections (DE-28, `docs/integration/calendrier-electoral.md`, section 3). Écrit pour être repris par les autres pôles : « Me rappeler cet événement » d'« Agir » (S-98), le rappel quotidien facultatif d'un parcours de « Comprendre » (CO-19), l'écran « Mes rappels » de « Communauté » (TE-09).
- Rien de ce qui suit n'a besoin du serveur : un rappel est programmé, gardé et affiché sur l'appareil seulement.

## 1. Principes

- **Désactivé par défaut** (principe 10) : aucun rappel n'existe tant que la personne ne l'a pas demandé, sur l'écran du contenu concerné.
- **Sur l'appareil seulement** : le serveur ne sait jamais qu'un rappel existe, ni pour quelle élection, quel événement ou quelle commune. Un rappel peut révéler un engagement (suivre une élection, aller à un rassemblement) : il est effacé par l'effacement rapide (S-44, partie `core-reminders`).
- **Par le centre de notifications** de « Communauté » (M31) : un rappel arrivé à son heure entre dans le centre comme toute autre nouveauté de l'appareil, avec les mêmes règles (écran verrouillé : « PPF : nouvelle activité » ; titre et détail seulement si la personne a autorisé les détails ; « silence total » respecté).
- **Modifiable et effaçable** : chaque rappel se retire un par un ; un rappel retiré ne revient pas quand l'espace qui l'a programmé le recalcule.

## 2. Le code commun

`shared/core/.../core/reminders/ReminderStore.kt` (tables `reminder` et `reminder_plan`, fichier `Reminders.sq`, migration locale provisoire `16.sqm`) :

| Élément | Rôle |
|---|---|
| `Reminder(id, owner, category, title, detail, target, dueAt, createdAt)` | Un rappel. `id` est stable ; `owner` nomme ce qui l'a programmé, préfixé par le pôle (`decider:election:<id>`, `agir:event:<adresse>`…) ; `category` est une catégorie du centre (`NotificationCategory`, sauf « Mon compte ») ; `title` et `detail` sont écrits par l'espace dans la langue de l'interface au moment où il programme ; `target` est le lien interne ouvert depuis la notification (`ppf:<espace>/...`) ; `dueAt` en secondes depuis 1970 (UTC). |
| `schedule(reminder)` | Programme un rappel simple, qu'aucun recalcul ne gère (un événement, une date choisie). |
| `setPlan(owner, kinds)`, `plan(owner)`, `plans(prefix)`, `dropPlan(owner)` | Pour les rappels qu'un espace recalcule quand son contenu change (une élection dont le décret déplace une date) : les sortes de rappels choisies (leur sens appartient à l'espace) et les rappels retirés un par un. |
| `replaceFuture(owner, planned, now)` | Remplace les rappels à venir de `owner` par ceux qui viennent d'être recalculés : ceux déjà passés restent, ceux retirés ne reviennent pas ; les rappels passés depuis plus de 30 jours sont effacés au passage. |
| `remove(id)` | Retire un rappel ; s'il appartient à un plan, il est noté comme retiré. |
| `upcoming(now)`, `upcomingOf(owner, now)` | Les rappels à venir, le plus proche d'abord (« Mes rappels », TE-09). |
| `nextDueAfter(now)` | Quand réveiller l'appareil pour le prochain rappel. |
| `eraseFromDevice()` (`DeviceErasable`, identifiant `core-reminders`) | Efface tous les rappels et tous les plans, attend la fin, peut être relancé. |
| `ReminderStore.notificationSources(database)` | Les sources à donner au centre (`Notifications(extraSources = ...)`) : une par catégorie ; elles ne lisent que l'appareil. Branchées dans `AppDependencies.withDatabase` et dans `PpfApplication` (Android). |

- L'identifiant d'une notification contient l'heure du rappel : un rappel déplacé à un autre jour est notifié de nouveau à sa nouvelle date, jamais deux fois à la même.
- Un rappel n'est notifié que si sa catégorie est allumée. L'espace qui propose un rappel allume la catégorie au moment où la personne le demande (après la permission du système, comme l'interrupteur de Mon espace : `NotificationsSettingsState.toggleCategory`), le dit avant, et signale sur son écran une catégorie éteinte depuis (« ces rappels ne s'afficheront pas », avec un bouton pour la rallumer).
- Une catégorie allumée après l'heure d'un rappel ne le montre pas en retard (le moteur repart de l'instant où elle est allumée).

## 3. Réveiller l'appareil

- **Android** : `android/.../ReminderAlarms.kt`. Une alarme inexacte du système (`AlarmManager.setAndAllowWhileIdle`, aucune permission d'alarme exacte ; Android peut la retarder de quelques minutes pour la batterie) réveille l'application à l'heure du prochain rappel ; elle lance le tour du centre sur l'appareil seulement (`NotificationEngine.check`, aucune requête au serveur), puis arme le rappel suivant. Elle est armée de nouveau au démarrage du processus, chaque fois qu'on quitte l'écran de l'application (les rappels ont pu changer) et après le redémarrage du téléphone (permission `RECEIVE_BOOT_COMPLETED`, récepteur qui n'accepte que l'action envoyée par le système).
- **Ordinateur** : un rappel s'affiche quand l'application est ouverte (tour du centre chaque minute) ; l'écran le dit.
- **iPhone (plus tard)** : notifications locales programmées du système, sans serveur, derrière la même interface.

## 4. Les rappels des élections (DE-28)

- Proposés sur la page de chaque élection à venir (« Me rappeler ces dates »), jamais ailleurs ; trois sortes, chacune réglable : inscription sur les listes (7 jours avant chaque date limite d'inscription, en mairie et en ligne), procuration (10 jours avant chaque tour), veille du scrutin (la veille de chaque tour, jour de vote de la métropole) ; tous à 10 h, heure de Paris. Les mêmes délais pour tout le monde (`core/decider/ElectionReminders.kt`).
- Catégorie « Votes et pétitions » ; `owner` = `decider:election:<identifiant de l'élection>` ; lien `ppf:decider/election/<identifiant>`, qui ouvre la page de l'élection.
- Recalculés après chaque lecture des contenus de « Décider » (synchronisation) : un décret de convocation qui déplace une date déplace les rappels avec elle. Une élection absente des contenus lus garde ses rappels tels quels.
- Le texte nomme l'élection par le titre de son contenu (un exemple garde sa mention [EXEMPLE]) et la date ; il ne dit jamais rien de la personne.

## 4 bis. Les rappels des consultations publiques (DE-27)

- Proposés sur la page de chaque consultation ouverte ou à venir (« Me rappeler cette consultation ») : à l'ouverture (si elle est à venir) et trois jours avant le dernier jour (pour une consultation d'un jour, le jour même), à 10 h, heure de Paris (`ConsultationReminders` dans `core/decider/PublicConsultation.kt`).
- Catégorie « Votes et pétitions » ; `owner` = `decider:consultation:<slug>` ; lien `ppf:decider/consultation/<slug>`, ouvert par la coquille.
- Recalculés comme ceux des élections : une enquête publique prolongée déplace le rappel de sa clôture.
- L'état commun de l'interface est `ui/decider/DeciderRemindersState.kt` (élections et consultations), créé une fois par la coquille (`rememberDeciderReminders`).

## 4 ter. Les rappels des demandes de documents administratifs (DE-31)

- Proposés pour chaque demande notée dans « Mes demandes » (« Me rappeler les délais »), tant que le document n'est pas reçu et que la CADA n'est pas saisie : le lendemain du mois sans réponse (silence qui vaut refus) et sept jours avant le dernier jour pour saisir la CADA, à 10 h, heure de Paris (`DocumentRequestReminders` dans `core/decider/DocumentRequest.kt`).
- Catégorie « Votes et pétitions » ; `owner` = `decider:document:<identifiant de la demande>` ; lien `ppf:decider/documents`, ouvert par la coquille.
- Calculés depuis les jours notés par la personne, pas depuis les contenus : recalculés quand elle note un refus écrit ; plus rien quand elle note le document reçu ou la saisine ; supprimés avec la demande. Le détail nomme l'administration (texte écrit par la personne) et la date limite ; le centre cache les détails par défaut.

## 5. Pour les autres pôles

- **Communauté (TE-09, « Mes rappels »)** : lister `upcoming()`, retirer par `remove(id)` ; ouvrir `target`. Les liens `ppf:decider/election/<id>` et `ppf:decider/consultation/<slug>` sont déjà ouverts par la coquille (`AdaptiveShell`).
- **Agir (S-98)** : `schedule(Reminder(owner = "agir:…", category = MOBILISATIONS, …))` pour un rappel simple, ou un plan s'il doit suivre le contenu.
- **Comprendre (CO-19)** : un rappel quotidien facultatif peut être un plan recalculé chaque jour (`replaceFuture`).
- Une nouvelle sorte de cible (`ppf:agir/...`) se branche dans `openEntry` de `AdaptiveShell`.
