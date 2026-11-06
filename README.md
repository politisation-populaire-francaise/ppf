# Politisation Populaire Française

Plateforme numérique du collectif citoyen Politisation Populaire Française : site web, applications Android, iOS et ordinateur, et serveur. Mission : redonner l'espace politique aux citoyens, en quatre piliers qui sont aussi les quatre espaces de la plateforme : **Voir, Comprendre, Agir, Décider**.

État : **phase 0, fondations**, et premières fondations de la phase 1 (identités par paires de clés, signature des contenus, écran du compte). Rien n'est encore en ligne. Les autres écrans sont des emplacements réservés marqués [EXEMPLE].

## Organisation du dépôt

| Dossier | Contenu |
|---|---|
| `shared/core` | Modèles et logique métier partagés (Kotlin Multiplatform, sans interface) |
| `shared/ui` | Interface Compose Multiplatform commune à Android, ordinateur et iOS |
| `android` | Application Android (hôte de l'interface partagée) |
| `desktop` | Application Windows, macOS et Linux (hôte de l'interface partagée) |
| `ios` | Application iPhone : code compatible, compilée plus tard |
| `server` | Serveur Ktor (point de contrôle de santé, site web) |
| `web` | Site web rendu côté serveur, lisible sans JavaScript |
| `infra` | Infrastructure décrite en code : production et préproduction séparées, Caddy, WireGuard |
| `docs` | Documents techniques ; `docs/projet` contient les documents de référence (vision, cahiers) |
| `tools` | Scripts de vérification (traceurs, dépendances), outil de signature des contenus (`tools/content-signer`), icônes Lucide et leur convertisseur (`tools/icons`) |
| `content` | Contenus éditoriaux intégrés à l'application (fiches pratiques et publications [EXEMPLE] en Markdown), signés avant intégration |
| `licenses` | Licence de la police embarquée Atkinson Hyperlegible (SIL Open Font License) ; la licence des icônes Lucide (ISC) est dans `tools/icons/lucide` |

## Compiler et tester

Prérequis : JDK 21 et, pour Android, le SDK Android (plateforme 36). Tout passe par le wrapper Gradle.

```bash
./gradlew build                      # compile tout et lance tous les tests
./gradlew :android:assembleDebug     # APK de test : android/build/outputs/apk/debug/
./gradlew :desktop:run               # lance l'application de bureau
./gradlew :desktop:createDistributable   # application de bureau empaquetée, sans installateur
./gradlew :server:run                # lance le serveur sur http://localhost:8080
bash tools/check-no-trackers.sh      # vérifie l'absence de traceurs et de tiers
```

Sous Windows, remplacez `./gradlew` par `.\gradlew.bat`.

## Règles non négociables

Aucun traceur, aucun service tiers, aucune ressource externe ; données réduites au strict nécessaire ; opinions jamais reliées à une identité ; accessibilité WCAG 2.2 AA ; écrans adaptatifs ; une seule base de code pour toutes les plateformes. Détail dans `CLAUDE.md` et `docs/projet/cahier-des-charges.md`.

## Licence

GNU AGPL version 3 ou ultérieure (`LICENSE`), avec des permissions supplémentaires pour la distribution par les magasins d'applications (`LICENSE-ADDITIONAL-PERMISSIONS.md`). Contributions : voir `CONTRIBUTING.md`. Failles de sécurité : voir `SECURITY.md`.
