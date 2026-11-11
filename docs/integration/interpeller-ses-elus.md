# Interpeller ses élus (DE-09) : conception

- Pôle « Décider », 9 novembre 2026. Cahier fonctionnel 2.1, M13.1 : « Modèles de courrier à personnaliser ; suivi public des réponses reçues ou non, sans données personnelles au-delà du mandat public ; accessible depuis le législomètre et « Qui décide de ça ? ». » Dépendances : annuaire des élus (D-047), réponses signées (DE-23).
- Étape 1 : décisions techniques prises en autonomie (gratuites, aucun tiers). Suivi des réponses : **décision D-119** du chef d'équipe, par délégation de la propriétaire (9 novembre 2026) : B comme cible, A tout de suite, C écartée (brainstorming du cahier fonctionnel, entrée du 9 novembre 2026).

## 1. Principes

- **La personne écrit et envoie elle-même.** PPF prépare un modèle et le remplit sur l'appareil ; il n’envoie rien, ne garde pas le texte, ne sait pas à qui l’on écrit ni ce qu’on écrit. Le courrier part par la page de contact officielle de l'élu, un courriel ou la poste, au choix de la personne.
- **Aucune civilité devinée.** Le nom vient de l'annuaire des élus (D-047) tel quel ; la formule d'appel du modèle reste neutre (« Madame, Monsieur »). On ne déduit jamais le genre d'un prénom.
- **Les mêmes modèles pour tous les élus d'un même mandat**, écrits par l'équipe, relus comme les autres contenus éditoriaux, marqués [EXEMPLE] tant qu'ils sont fictifs. Un modèle demande une position ou une explication ; il n'insulte ni ne menace (charte de contenu, section 9.3 du cahier des charges).
- **Les contacts officiels** (pages de contact des assemblées, de la mairie…) sont écrits et vérifiés par l'équipe dans le modèle (`ppf-contact`), jamais devinés par le code ; ils s'ouvrent après « Vous quittez PPF ».

## 2. Format

Un contenu `modele-courrier` ; détail dans `docs/FORMATS.md` (« Modèle de courrier à ses élus »). Exemple [EXEMPLE] : `content/decider/courriers/courrier-avant-un-vote-exemple.md`.

## 3. Dans l'application (étape 1, faite le 9 novembre 2026)

- **« Interpeller ses élus »** sur l'accueil de « Décider » : les modèles, avec à qui ils s'adressent.
- **Page d'un modèle** : « À qui l'adresser ? » liste les élus concernés de la commune choisie dans « Mes élus » (lus sur l'appareil seulement, sans réseau) ; sans commune, la page le dit et mène à « Mes élus », et le courrier garde « [Nom de l'élu] » et « [Votre commune] » à remplacer. Le courrier rempli (élu, commune, date) se modifie librement dans l'écran, « Copier le courrier » le met dans le presse-papiers, puis « Où l'envoyer » ouvre les contacts officiels du mandat choisi.
- **Le texte n'est gardé nulle part** : il vit dans l'écran et se perd en quittant la page.
- **« Vos envois » (D-119, option A)** : « J'ai envoyé ce courrier » note sur l'appareil seulement le modèle, l'élu choisi (tel que l'annuaire le nomme ; rien quand la personne a écrit le nom elle-même) et le jour ; « J'ai reçu une réponse » ajoute le jour de la réponse. Une note par modèle et par élu ; chaque note s'efface d'un geste ; partie `decider-courriers` de l'effacement rapide. Rien n'est envoyé. Le presse-papiers du système peut être lu par d'autres applications : la page conseille de n'y mettre que ce qu'on accepte de voir lu par l'équipe de l'élu.

## 4. Étapes suivantes

1. Suivi public des réponses, avec DE-23 (D-119, option B) : deux compteurs publics par élu, « N citoyens disent lui avoir écrit » et « N disent avoir reçu une réponse », chaque déclaration par un jeton anonyme des urnes (D-110), jamais reliée à un compte ; mention « déclaré par des citoyens, non vérifié » ; même règle pour tous, aucun classement ; affichage à partir de 5 déclarations ; réponses publiées par l'élu certifié lui-même, avec son droit de réponse ; relecture par un avocat avant l'ouverture publique. La note privée de l'appareil (option A) pourra proposer, au choix de la personne, de faire sa déclaration anonyme.
2. Bouton « Interpeller mes élus avant le vote » sur une page de texte du législomètre (« Voir ») et sur « Qui décide de ça ? » (« Comprendre ») : ils ouvriront `DeciderDestination.Letter` (modèle lié au texte par le rôle `texte`). À convenir avec ces deux pôles.
3. Vrais modèles et contacts officiels, vérifiés sur les sites des assemblées et des collectivités.
4. Site sans JavaScript : les modèles en lecture, à copier.
