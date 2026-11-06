# Application iOS

L'application iPhone et iPad n'est pas compilée pour l'instant : il n'y a pas de compte Apple Developer, et la compilation demande un Mac (cahier des charges, section 1.2).

Ce que le dépôt garantit dès maintenant :

- les modules `shared/core` et `shared/ui` déclarent les cibles `iosArm64` et `iosSimulatorArm64` ; le code commun n'utilise aucune bibliothèque réservée à Android ou à la JVM ;
- `shared/ui` expose un framework `PpfUi` avec la fonction `MainViewController()`, point d'entrée de la future application SwiftUI ;
- les versions choisies (Kotlin, Compose Multiplatform) sont celles dont la prise en charge d'iOS est stable.

Quand le compte Apple existera, ce dossier recevra le projet Xcode (`iosApp`), et l'intégration continue ajoutera une machine macOS pour compiler et tester sur simulateur.
