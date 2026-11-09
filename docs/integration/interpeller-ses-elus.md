# Interpeller ses élus (DE-09) : conception

- Pôle « Décider », 9 novembre 2026. Cahier fonctionnel 2.1, M13.1 : « Modèles de courrier à personnaliser ; suivi public des réponses reçues ou non, sans données personnelles au-delà du mandat public ; accessible depuis le législomètre et « Qui décide de ça ? ». » Dépendances : annuaire des élus (D-047), réponses signées (DE-23).
- Étape 1 : décisions techniques prises en autonomie (gratuites, aucun tiers). Le suivi public des réponses attend une décision : options A, B et C dans `docs/projet/brainstorming-cahier-fonctionnel.md` (entrée du 9 novembre 2026), recommandation A maintenant, B avec DE-23.

## 1. Principes

- **La personne écrit et envoie elle-même.** PPF prépare un modèle et le remplit sur l'appareil ; il n'envoie rien, ne garde rien, ne sait pas à qui l'on écrit ni ce qu'on écrit. Le courrier part par la page de contact officielle de l'élu, un courriel ou la poste, au choix de la personne.
- **Aucune civilité devinée.** Le nom vient de l'annuaire des élus (D-047) tel quel ; la formule d'appel du modèle reste neutre (« Madame, Monsieur »). On ne déduit jamais le genre d'un prénom.
- **Les mêmes modèles pour tous les élus d'un même mandat**, écrits par l'équipe, relus comme les autres contenus éditoriaux, marqués [EXEMPLE] tant qu'ils sont fictifs. Un modèle demande une position ou une explication ; il n'insulte ni ne menace (charte de contenu, section 9.3 du cahier des charges).
- **Les contacts officiels** (pages de contact des assemblées, de la mairie…) sont écrits et vérifiés par l'équipe dans le modèle (`ppf-contact`), jamais devinés par le code ; ils s'ouvrent après « Vous quittez PPF ».

## 2. Format

Un contenu `modele-courrier` ; détail dans `docs/FORMATS.md` (« Modèle de courrier à ses élus »). Exemple [EXEMPLE] : `content/decider/courriers/courrier-avant-un-vote-exemple.md`.

## 3. Dans l'application (étape 1, faite le 9 novembre 2026)

- **« Interpeller ses élus »** sur l'accueil de « Décider » : les modèles, avec à qui ils s'adressent.
- **Page d'un modèle** : « À qui l'adresser ? » liste les élus concernés de la commune choisie dans « Mes élus » (lus sur l'appareil seulement, sans réseau) ; sans commune, la page le dit et mène à « Mes élus », et le courrier garde « [Nom de l'élu] » et « [Votre commune] » à remplacer. Le courrier rempli (élu, commune, date) se modifie librement dans l'écran, « Copier le courrier » le met dans le presse-papiers, puis « Où l'envoyer » ouvre les contacts officiels du mandat choisi.
- **Rien n'est gardé** : le texte vit dans l'écran ; il est perdu en quittant la page (c'est voulu : aucune trace d'un engagement politique sur l'appareil ni ailleurs). Le presse-papiers du système peut être lu par d'autres applications : la page conseille de n'y mettre que ce qu'on accepte de voir lu par l'équipe de l'élu.

## 4. Étapes suivantes

1. Suivi public des réponses, selon la décision (option B : compteur anonyme déclaratif et réponses signées des élus certifiés, DE-23, après l'avis d'un avocat).
2. Bouton « Interpeller mes élus avant le vote » sur une page de texte du législomètre (« Voir ») et sur « Qui décide de ça ? » (« Comprendre ») : ils ouvriront `DeciderDestination.Letter` (modèle lié au texte par le rôle `texte`). À convenir avec ces deux pôles.
3. Vrais modèles et contacts officiels, vérifiés sur les sites des assemblées et des collectivités.
4. Site sans JavaScript : les modèles en lecture, à copier.
