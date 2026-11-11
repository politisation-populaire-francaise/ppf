# « Fédérer » dans « Agir » (D-195)

Écrite le 11 novembre 2026 par la session « [Agir] MVP 1 ».

## 1. Ce que fait la rubrique

Carte « Fédérer » de l'accueil d'« Agir » (route `AgirRoute.FEDERER`, id `federer`), écran `ui/agir/federer/FedererScreen.kt` :

1. **Mes proches à mobiliser** (AG-63) : ouvre la liste privée ; son retour ramène à « Fédérer ».
2. **Le kit « Convaincre autour de soi »** : la fiche pratique `convaincre-autour-de-soi` de Comprendre, lue en entier dans Agir (`GuideReader`, avec ses sources) ; sans la fiche sur l'appareil, un encadré honnête.
3. **Cartes à montrer ou à partager** : `core/agir/federer/ConvinceKit.kt` découpe la fiche en cartes (arguments numérotés de la section « arguments », bloc `::: objections` lu par `ContentBlocks.parseObjections`, liste de la section « Le monde bouge », en une carte). « Partager » (Android) et « Copier le texte » donnent la carte, la mention « rédigé par l'IA » et l'adresse de la fiche (`/comprendre/fiches/convaincre-autour-de-soi`).
4. **Conseils** : écouter, dire vrai, proposer un petit pas, respecter le désaccord.

La liste des proches affiche aussi `LockReminder` (`ui/agir/security/LockReminder.kt`) tant que PPF n'a pas de code (AG-63, question 3, D-190).

## 2. Vie privée

Rien n'est envoyé ni compté ; aucune donnée nouvelle n'est gardée (rien à ajouter au registre). Les cartes sont tirées d'un contenu public signé ; la liste des proches reste sur l'appareil.

## 3. Dépendances

- Contenu : la fiche `convaincre-autour-de-soi` (Comprendre, branche `claude/comprendre-mvp1-a853cd`, pas encore dans `main` le 11 novembre 2026). Si sa structure change (titre « arguments », bloc `::: objections`, section « Le monde bouge »), adapter `ConvinceKitReader` et son test.
- Plus tard : un lien vers le texte du cap n° 7 `federer-autour-de-soi` (type `CAP` de Comprendre) quand il sera dans `main`.

## 4. Tests

`ConvinceKitReaderTest` (découpage, kit sans ces parties, texte partagé) ; `FedererScreenTest` (sans kit : encadré, aller-retour vers la liste des proches ; avec un kit [EXEMPLE] sur téléphone, tablette sombre et ordinateur : cartes, copie avec origine et adresse, lecture de la fiche) ; `AgirScreenTest` (la carte « Fédérer » est la troisième).
