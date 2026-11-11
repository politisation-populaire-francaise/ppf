# Identité graphique : où sont les fichiers dans le projet

Le lot livré par la propriétaire le 11 novembre 2026 (« ppf-logo-bannieres », version 1.0 du 6 novembre 2026) est rangé ici, tel quel, avec son `LISEZMOI.md` (règles d'usage, tailles minimales, zone de protection, points à valider). Décision D-185.

## Dans ce dossier (`design/identite/`)

| Dossier | Contenu |
|---|---|
| `logo/` | Symbole, logo horizontal, en ligne et vertical, en cinq couleurs (SVG, et PNG dans `logo/png/`) |
| `bannieres/` | Avatars et bannières des réseaux sociaux |
| `magasins/play-store/` | Icône 512 et bannière de la fiche Google Play |
| `ios/` | Icônes 1024 de l'application iOS (claire, sombre, teintée), pour le jour où elle sera compilée |
| `sources-svg/` | Originaux vectoriels de chaque icône et bannière |
| `generateur/` | Scripts Python qui ont produit le lot (polices, `rsvg-convert`, ImageMagick). Lus le 11 novembre 2026 : aucun accès au réseau ; ils écrivent dans des dossiers d'un autre ordinateur (`/home/claude/...`) et ne sont pas exécutés dans ce projet |
| `planche-apercu.png` | Planche d'aperçu de tout le lot |

## Les fichiers d'usage, à leur place

| Où | Quoi |
|---|---|
| `android/src/main/res/mipmap-*/` | Icône de lancement classique, ronde, couche de premier plan et couche monochrome, en cinq densités |
| `android/src/main/res/mipmap-anydpi-v26/` | `ic_launcher.xml` et `ic_launcher_round.xml` : icône adaptative sur fond indigo (`ic_launcher_background`) ; l'icône neutre du mode discret (`ic_launcher_neutral`) ne change pas |
| `web/src/main/resources/static/` | `favicon.ico`, `favicon.svg`, favicons PNG, `apple-touch-icon.png`, icônes PWA (192, 512, maskables), `og-image-1200x630.png`, `icon.svg` (le symbole), `manifest.webmanifest` fusionné |
| `desktop/icons/` | `ppf.ico` (installateur Windows, assemblé à partir des PNG du lot par `tools/icons/make_ico.py`) et `ppf.png` (Linux) |
| `shared/ui/.../components/PpfSymbol.kt` | Le symbole dessiné dans l'application (barre du haut, menu, tutoriel, Mon espace, fenêtre de l'ordinateur), aux couleurs du thème |

Non utilisés pour l'instant : les icônes d'écran de démarrage du lot (`splash-icon-*`, gardées dans `sources-svg/`), parce qu'une icône de démarrage fixée dans le thème s'afficherait aussi en mode discret (voir D-185) ; les icônes macOS (aucun fichier `.icns` dans le lot).
