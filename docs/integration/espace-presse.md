# Espace presse (PP-04) : conception, version 0.1

Rédigé le 9 novembre 2026 par le pôle « Voir » (« [Voir] Team Dev 2 »), à la demande du chef d'équipe, d'après le cahier fonctionnel (M34, PP-04 : « Communiqués, kit média, chiffres réutilisables, contact presse (par M30) »). Décision D-106. Branche `feature/voir-presence`, pas encore compilée.

## 1. Les quatre parties

1. **Communiqués** : un type de contenu signé `communique` de l'espace « Voir ». Il passe par le même moteur éditorial et la même relecture à deux que les autres contenus, et il a son journal public des corrections.
   - Lu par `core/voir/PressReleases.kt` : la date est obligatoire, et les archives, les sources et les corrections sont facultatives.
   - Affichage du plus récent au plus ancien.
   - PPF rédige et valide ses communiqués. L'IA n'en produit que des brouillons [EXEMPLE].
2. **Kit média** : les faits, sans texte éditorial, réunis dans `MediaKit` :
   - le nom et le sigle ;
   - le site ;
   - les couleurs de l'identité (D-025 : bleu `#2B3A8F`, carmin `#C8213A`, texte `#14182B`) ;
   - la police Atkinson Hyperlegible (SIL Open Font License 1.1) et les icônes Lucide (ISC) ;
   - l'icône de l'application (`/icon.svg`) ;
   - les licences (D-103 : CC BY 4.0 pour les textes et les images, Licence Ouverte 2.0 pour les données) ;
   - le rappel que PPF n'utilise jamais l'identité visuelle de l'État.

   Une présentation courte de PPF sera écrite par l'équipe, en contenu signé, quand l'association existera.
3. **Chiffres réutilisables** : des liens vers les données ouvertes (`/donnees`, D-101, chaque jeu avec sa source et sa licence), la transparence de PPF (PP-01), les victoires (PP-03) et, après PP-07, le tableau de bord. Chaque contenu se cite avec « Citer ce contenu » (S-144).
4. **Contact presse** : par le contact de PPF (M30, D-140), catégorie « Journaliste » (`ContactCategory.JOURNALIST`), réponse visée sous 15 jours (`CaseDeadlines`). Il n'y a pas de canal à part et aucun e-mail n'est demandé : le journaliste suit l'échange avec son numéro et son code.

## 2. Pages

- **Application** : « Espace presse », en bas de l'axe « Le pouvoir » de « Voir », sous « Transparence de PPF ».
- **Site** : `/presse`, lisible sans JavaScript, avec un lien dans le pied de page. L'aperçu du back-office montre un communiqué comme sur le site.

## 3. Demandé au Directeur technique (M30)

Le formulaire de contact du site (étape 4 du contrat M30) et celui de l'application devraient accepter une catégorie choisie d'avance, par exemple `?categorie=journaliste`. L'espace presse y renverra alors directement. En attendant, la page explique comment choisir « Journaliste ».
