# Installer ou mettre à jour la version de test sur un téléphone Android

Ce guide sert à la propriétaire et aux personnes qui testent l'application avec elle. Il est écrit pour les téléphones Samsung (les plus touchés par les blocages), mais les étapes 3 et 4 valent pour tous les téléphones Android. Version du 6 novembre 2026.

## Pourquoi le téléphone affiche des avertissements

L'application de test ne vient pas encore d'un magasin d'applications (Google Play ou Galaxy Store). Deux protections du téléphone réagissent alors, quelle que soit l'application :

- **Google Play Protect** ne connaît pas encore le développeur. Il prévient que les applications de développeurs inconnus « peuvent » être dangereuses. Ce message ne veut pas dire qu'un virus a été trouvé.
- **Le Bloqueur automatique de Samsung** (One UI 6 et plus récent) bloque toute installation qui ne vient pas de Google Play ou du Galaxy Store. Il ne regarde pas le contenu de l'application.

Ces messages ne disparaîtront complètement que lorsque l'application sera distribuée par Google Play (décision en attente de la propriétaire, voir `docs/projet/brainstorming-cahier-des-charges.md`, entrée « Distribution des versions de test sur Android »). En attendant, les étapes ci-dessous permettent d'installer en sécurité.

**Règle de prudence** : n'installez qu'un fichier reçu directement de la propriétaire. Ne suivez jamais ce guide pour un fichier `.apk` trouvé ailleurs.

## 1. Récupérer le fichier

Le fichier s'appelle `android-preview.apk` (les anciennes versions s'appelaient `android-debug.apk`). Copiez-le sur le téléphone, dans le dossier « Téléchargements » : par câble USB, ou en vous l'envoyant par une messagerie que vous contrôlez.

## 2. Samsung : désactiver le Bloqueur automatique, le temps de l'installation

1. Ouvrez **Paramètres**.
2. Touchez **Sécurité et confidentialité**.
3. Touchez **Bloqueur automatique**.
4. Désactivez l'interrupteur, puis confirmez.

Vous le réactiverez à l'étape 5. Si votre téléphone n'a pas ce menu, passez à l'étape 3.

## 3. Ouvrir le fichier et autoriser la source

1. Ouvrez l'application **Mes fichiers**, puis **Téléchargements**, et touchez `android-preview.apk`.
2. La première fois, le téléphone dit « Pour votre sécurité, votre téléphone n'est pas autorisé à installer des applications inconnues provenant de cette source ». Touchez **Paramètres**, activez **Autoriser cette source**, puis revenez en arrière.
3. Touchez **Installer** (ou **Mettre à jour** si l'application est déjà là).

## 4. Répondre à Google Play Protect

Selon le cas, un de ces messages peut apparaître :

- **« Envoyer l'application pour analyse ? »** : touchez **Envoyer**. Google analyse l'application et la reconnaîtra mieux ensuite, pour vous comme pour les autres testeurs. Le fichier envoyé est le même pour tout le monde et ne contient aucune donnée personnelle : seulement le code de l'application et des contenus d'exemple, jamais votre compte ni vos messages.
- **« Application inconnue »** ou **« Play Protect ne reconnaît pas le développeur de cette application »** : touchez **Plus de détails**, puis **Installer quand même**.
- **« Application dangereuse bloquée »** ou un message rouge qui parle de **code malveillant** : **n'installez pas**. Faites une capture d'écran et envoyez-la à la propriétaire : ce cas n'est pas normal et doit être examiné.

## 5. Samsung : réactiver le Bloqueur automatique

Paramètres, Sécurité et confidentialité, Bloqueur automatique : réactivez l'interrupteur. L'application installée continue de fonctionner normalement.

## Mettre à jour plus tard

Refaites les étapes 1 à 5 avec le nouveau fichier. Chaque nouvelle version s'installe par-dessus l'ancienne, sans rien effacer (compte, contenus, réglages restent en place). Pour voir la version installée : Paramètres, Applications, Politisation Populaire Française.

## Si l'installation échoue

- **« Application non installée car le package est en conflit avec un package existant »** : la signature du fichier ne correspond pas à celle de l'application déjà installée. Ne désinstallez rien, prévenez la propriétaire.
- **« Application non installée »** juste après une version plus récente : le fichier est plus ancien que celui déjà installé. Demandez le dernier fichier.
- Autre message : faites une capture d'écran et envoyez-la à la propriétaire.
