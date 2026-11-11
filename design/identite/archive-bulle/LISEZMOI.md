# Politisation Populaire Française : logo et bannières

Version 1.0 du 6 novembre 2026. Proposition à valider par la propriétaire.

## 1. Le symbole

Une bulle de parole indigo contient trois silhouettes de taille croissante. Un point rouge est posé sur son coin supérieur droit.

- **La bulle** représente la parole citoyenne et le réseau d'échange.
- **Les trois silhouettes** représentent le peuple qui prend la parole et la mobilisation qui grandit. Elles rappellent aussi un graphique : des chiffres vérifiés et sourcés.
- **Le point rouge** représente ce qui se passe en direct. On retrouve le même point dans l'application pour les alertes et les événements en cours.

Le symbole est dessiné uniquement avec les couleurs du projet (indigo #2B3A8F et rouge #C8213A). Il n'utilise ni Marianne, ni drapeau, ni aucun élément de l'identité visuelle de l'État.

Le nom est écrit en Atkinson Hyperlegible Gras. Les lettres ont été converties en formes vectorielles : les fichiers s'affichent correctement même sur un ordinateur où la police n'est pas installée.

## 2. Les versions du logo (dossier `logo/`)

| Fichier | Quand l'utiliser |
|---|---|
| `symbole-*` | Petits espaces : icône, avatar, tampon, pied de page |
| `logo-horizontal-*` | Usage principal : en-tête du site, documents, affiches |
| `logo-ligne-*` | Bandeaux très larges et peu hauts |
| `logo-vertical-*` | Formats carrés ou verticaux : couverture, autocollant, écran d'accueil |

Chaque version existe en cinq couleurs :

| Suffixe | Fond conseillé |
|---|---|
| `-couleur` | Fond blanc ou très clair (version par défaut) |
| `-inverse` | Fond indigo #2B3A8F |
| `-sombre` | Fond sombre #0F1220 (thème sombre de l'application) |
| `-noir` | Impression en une couleur, tampons, fax |
| `-blanc` | Photo ou fond foncé quelconque |

Les fichiers `.svg` sont les originaux : ils s'agrandissent sans perte. Les fichiers `.png` du sous-dossier `png/` ont un fond transparent.

### Règles d'usage

- **Zone de protection :** laisser autour du logo un espace vide au moins égal à la hauteur du point rouge (environ un cinquième de la hauteur de la bulle).
- **Tailles minimales :** symbole à 16 pixels, logo horizontal à 120 pixels de large, logo en ligne à 200 pixels de large.
- **À ne pas faire :** déformer le logo, changer ses couleurs, lui ajouter une ombre ou un contour, le placer sur un fond qui le rend illisible, lui ajouter une Marianne, un drapeau tricolore ou tout autre symbole de l'État.

## 3. Icônes du site web (dossier `web/`)

| Fichier | Taille | Rôle |
|---|---|---|
| `favicon.ico` | 16, 32 et 48 px | Icône d'onglet pour tous les navigateurs |
| `favicon.svg` | vectoriel | Icône d'onglet moderne, qui passe en couleurs claires quand le navigateur est en mode sombre |
| `favicon-16x16.png`, `-32x32`, `-48x48` | | Mêmes icônes en PNG |
| `apple-touch-icon.png` | 180 × 180 | Raccourci sur l'écran d'accueil d'un iPhone ou d'un iPad |
| `icon-192.png`, `icon-512.png` | | Application web installable (PWA) |
| `icon-maskable-192.png`, `-512` | | Version PWA que le téléphone peut découper en rond ou en carré arrondi |
| `og-image-1200x630.png` | 1200 × 630 | Aperçu affiché quand quelqu'un partage un lien du site |
| `site.webmanifest` | | Fiche d'identité de l'application web |
| `head-snippet.html` | | Les lignes à ajouter dans les pages du site (Claude Code s'en charge) |

## 4. Application Android (dossier `android/`)

| Fichier | Rôle |
|---|---|
| `res/mipmap-*/ic_launcher.png` | Icône classique, en 5 densités d'écran (48 à 192 px) |
| `res/mipmap-*/ic_launcher_round.png` | Icône ronde pour les téléphones qui l'utilisent |
| `res/mipmap-*/ic_launcher_foreground.png` | Icône adaptative, couche de premier plan (108 à 432 px) |
| `res/mipmap-*/ic_launcher_monochrome.png` | Icône à thème : Android la colore selon le fond d'écran (Android 13 et plus) |
| `res/mipmap-anydpi-v26/*.xml`, `res/values/ic_launcher_background.xml` | Fichiers qui assemblent l'icône adaptative sur fond indigo |
| `play-store/icon-512.png` | Icône de la fiche Google Play (512 × 512) |
| `play-store/feature-graphic-1024x500.png` | Bannière de la fiche Google Play |
| `splash/splash-icon-clair-1152.png`, `-sombre` | Icône de l'écran de démarrage (Android 12 et plus), thèmes clair et sombre |

L'icône adaptative respecte la zone de sécurité d'Android : sur une grille de 108 unités, le dessin reste dans le cercle central de 66 unités, que le téléphone ne découpe jamais.

## 5. Application iOS (dossier `ios/`)

| Fichier | Rôle |
|---|---|
| `AppIcon-1024.png` | Icône principale, sans transparence (Apple l'exige) |
| `AppIcon-1024-sombre.png` | Variante pour le mode sombre d'iOS, fond transparent |
| `AppIcon-1024-teinte.png` | Variante en niveaux de gris pour les icônes teintées d'iOS |

Apple génère lui-même toutes les autres tailles à partir de ces fichiers 1024 × 1024.

## 6. Réseaux sociaux (dossier `bannieres/`)

| Fichier | Taille | Où |
|---|---|---|
| `avatar-800.png`, `avatar-400.png` | carré | Photo de profil sur tous les réseaux (fond indigo) |
| `avatar-fond-blanc-800.png`, `-400` | carré | Variante sur fond blanc |
| `x-1500x500.png` | 1500 × 500 | Bannière de profil X |
| `mastodon-1500x500.png` | 1500 × 500 | En-tête de profil Mastodon |
| `bluesky-3000x1000.png` | 3000 × 1000 | Bannière Bluesky (format 3:1, haute définition) |
| `linkedin-page-1512x256.png` | 1512 × 256 | Couverture d'une page LinkedIn d'association |
| `linkedin-profil-1584x396.png` | 1584 × 396 | Bannière d'un profil personnel LinkedIn (membres de l'équipe) |
| `youtube-2560x1440.png` | 2560 × 1440 | Bannière de chaîne YouTube |

Sur les bannières de profil, le texte est décalé vers la droite : la photo de profil vient se superposer en bas à gauche. Sur la bannière YouTube, le logo et le slogan restent dans la zone centrale de 1546 × 423 pixels, la seule visible sur tous les écrans.

## 7. Dossier `_sources-svg/`

Les originaux vectoriels de chaque icône et bannière, pour les modifier ou les réexporter sans perte.

## 8. Points à valider

1. **Le symbole lui-même.** Il pourra être remplacé ou complété par la Marianne et le bonnet phrygien de l'artiste bénévole, si vous le souhaitez.
2. **Le nom court sous l'icône du téléphone.** Il est provisoirement fixé à « Politisation » dans `site.webmanifest`, en attendant la décision sur l'usage public du sigle.
3. **Une vérification d'antériorité à l'INPI** avant tout dépôt du logo comme marque, pour s'assurer qu'aucun logo proche n'est déjà protégé.

## 9. Sources des formats

- Android, icônes adaptatives (grille 108 × 108, zone de sécurité 66 × 66, couche monochrome) : developer.android.com/develop/ui/views/launch/icon_design_adaptive
- YouTube (2560 × 1440, zone centrale 1546 × 423) : guides de dimensions 2026, dont dev.to/pixotter et lilachbullock.com
- Bluesky (format 3:1, limite de 1 Mo par image) : opentweet.io/tools/bluesky-banner-size et postiz.com
- LinkedIn (page 1512 × 256, profil 1584 × 396) : opentweet.io/tools/linkedin-image-size et manypixels.co, qui citent l'aide officielle de LinkedIn en octobre 2026
