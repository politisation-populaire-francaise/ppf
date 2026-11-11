# Identité graphique de PPF

## Le logo officiel (11 novembre 2026, D-187)

Décision de la propriétaire : « Le logo officiel est désormais celui-ci [...] et ajuste le logo en fonction de l'endroit et le support sur lequel il apparait ». Une Marianne coiffée du bonnet phrygien brandit le drapeau français au milieu d'une foule de citoyens et de musiciens, sur un disque.

| Où | Quoi |
|---|---|
| `logo-officiel/marianne-logo.png` | L'original de la propriétaire (1254 × 1254, fond transparent) |
| `logo-officiel/derives/` | Les déclinaisons produites pour chaque support (web, iOS, icône Play Store) |
| `logo-officiel/generateur/deriver-logo.ps1` | Le script Windows (PowerShell, sans dépendance) qui produit toutes les déclinaisons à partir de l'original |
| `illustrations/` | Les images du tutoriel : `marianne-card.png` (écrans 1 et 2) et `bonnet-phrygien.png` (écran 6), originaux |
| `archive-bulle/` | L'ancien lot « bulle de parole » (version 1.0 du 6 novembre 2026, D-185), gardé en archive, embarqué nulle part |

### Les déclinaisons, par support

- **Android** (`android/src/main/res/mipmap-*/`) : icône adaptative sur fond blanc, le dessin dans le cercle de 66 dp que le téléphone ne découpe jamais ; icône ronde (disque blanc) ; icône classique (carré blanc arrondi) ; icône monochrome des icônes à thème (Android 13 et plus) : les parties colorées du dessin en une seule couleur, ses traits blancs laissés vides. L'icône neutre du mode discret ne change pas.
- **Site** (`web/src/main/resources/static/`) : à 16 et 32 pixels, la Marianne et son drapeau seuls (le dessin entier n'y serait qu'une tache), sur un disque blanc lisible sur un onglet clair ou sombre ; dès 48 pixels, le dessin entier ; icônes de l'application web (disque blanc), icônes « maskable » (dessin dans la zone sûre de 62 %), icône de l'écran d'accueil d'un iPhone, image de partage (logo et nom sur fond blanc).
- **Ordinateur** (`desktop/icons/`) : `ppf.ico` (16, 32, 48 et 192 pixels) pour l'installateur Windows, `ppf.png` pour Linux ; la fenêtre affiche le logo de l'application.
- **Application** (`shared/ui/.../drawable/ppf_logo.png`, 256 pixels) : composant `PpfSymbol`, dans la barre du haut quand le nom entier tient à côté, en tête du menu, dans la barre de l'ordinateur et en bas de Mon espace ; posé sur un disque clair en thème sombre (l'indigo du dessin se perdrait sur une page sombre). Taille minimale : 24 dp.
- **iOS** (`logo-officiel/derives/ios/AppIcon-1024.png`) : fond blanc opaque, pour le jour où l'application iOS sera compilée.

À faire avec la propriétaire : de nouvelles bannières de réseaux sociaux au logo officiel (celles de l'archive portent l'ancien symbole) ; une vérification d'antériorité à l'INPI avant tout dépôt de marque.
