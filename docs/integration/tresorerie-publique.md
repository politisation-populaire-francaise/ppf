# Trésorerie publique (PP-06) : proposition de conception, version 0.1, à concevoir avec le Directeur technique

Rédigé le 9 novembre 2026 par le pôle « Voir » (« [Voir] Team Dev 2 »), à la demande du chef d'équipe : « PP-06, conception d'abord avec le Directeur technique (saisie validée à deux dans le back-office) ». **Rien n'est codé.** Ce document sert de base à la discussion avec le Directeur technique.

Sources :
- cahier fonctionnel, M34, PP-06 : chaque mouvement d'argent saisi par le trésorier, validé à deux et publié sous 48 heures avec sa date, son montant, sa catégorie et son justificatif ; solde, graphiques, export ; les particuliers, donateurs comme bénéficiaires, ne sont jamais nommés ;
- point d'attention n° 15 : décision de la propriétaire du 4 novembre 2026, et obligations légales vérifiées le 4 novembre 2026 sur Service-public.fr (fiche F34245) ;
- cahier fonctionnel, rôle « Trésorier ».

Tout reste [EXEMPLE] tant que l'association n'existe pas.

## 1. Ce qui est déjà décidé

- Le trésorier saisit chaque mouvement dès qu'il a lieu. Une deuxième personne le valide (ni l'auteur, ni une personne qui a modifié la saisie, ni un compte de la même clé, comme pour la relecture éditoriale). La publication suit sous 48 heures, avec le justificatif.
- Les particuliers ne sont jamais nommés :
  - un don d'un particulier apparaît comme « Don d'un particulier », avec son montant et sa date ;
  - une aide à une personne apparaît comme « Aide aux frais d'avocat, Lyon, 450 € ».
  Seuls sont nommés les organisations (caisses de grève, associations, prestataires) et les financeurs publics.
- Le compte d'emploi annuel des ressources devient obligatoire au-delà de 153 000 € de dons (Service-public.fr, F34245). La trésorerie publique va au-delà de cette obligation. Les comptes annuels approuvés sont publiés dans « Transparence de PPF » (PP-01).

## 2. Proposition

1. **Saisie dans le back-office** : une section `tresorerie`, branchée sur le point d'extension `BackOfficeSection` du socle, avec deux permissions :
   - `treasury.write` (rôle « trésorier ») ;
   - `treasury.approve` (une deuxième personne).

   Chaque action est inscrite au journal d'audit.
2. **Un mouvement** contient :
   - un numéro d'ordre et une date ;
   - un montant en centimes ;
   - un sens : entrée, sortie ou redistribution ;
   - une catégorie, prise dans une liste fixe :
     - entrées : dons, cotisations, subventions ;
     - sorties : fonctionnement, hébergement, frais juridiques ;
     - redistributions : caisses de grève, frais d'avocat, aide aux victimes ;
     - autre ;
   - la contrepartie : une organisation ou un financeur public nommé, ou une formule fixe pour un particulier ;
   - un lieu facultatif, à l'échelle de la ville ;
   - le justificatif : la version publiée, expurgée de toute donnée personnelle, avec son empreinte SHA-256 et son horodatage OpenTimestamps (D-111) ;
   - l'empreinte du mouvement précédent.

   Un mouvement publié n'est jamais effacé. Une erreur se corrige par un mouvement de correction, qui renvoie au numéro corrigé. Le chaînage par empreintes rend visible toute écriture retirée ou modifiée après coup.
3. **Publication** : chaque mouvement validé devient un contenu signé de l'espace « Voir », par le service de signature, comme les autres contenus. Il exige toujours deux approbations (règle de `DoubleValidation`, à étendre par le Directeur technique pour ce type).
4. **Ce que l'on voit** :
   - une page de l'application et une page du site `/tresorerie`, avec le solde (calculé, jamais saisi), les mouvements les plus récents en tête, des filtres par sens et par catégorie, et des graphiques mois par mois (rendus côté serveur pour le site, sans JavaScript) ;
   - un jeu de données de l'export ouvert (D-101), sous Licence Ouverte 2.0 ;
   - les chiffres « argent collecté » et « argent redistribué » du tableau de bord (PP-07, par une `DashboardProvider`).

## 3. Questions

**Pour le Directeur technique :**
1. Où garder les justificatifs d'origine ? Les originaux (relevés, factures) contiennent des données personnelles. Ma proposition : chiffrés au repos, avec un accès tracé pour le trésorier et le bureau seulement ; seule la version expurgée est publiée.
2. Faut-il un nouvel usage dans le service de signature (`TREASURY`), avec deux approbations toujours exigées, ou le même usage que les contenus de « Voir » ?
3. Le chaînage des mouvements par empreinte, et l'horodatage, sont-ils faits par le serveur (D-111) ou par le service de signature ?

**Pour la propriétaire (par le chef d'équipe) :**
4. Un donateur particulier peut-il choisir d'apparaître sous son nom ? Ma recommandation : oui, s'il le demande lui-même, après une mise en garde simple, et de façon réversible (principe 10 : protéger par défaut, laisser le choix). Par défaut, il n'est jamais nommé.

**Pour l'avocat :**
5. Un montant et une date suffisent-ils parfois à reconnaître un donateur, par exemple un don unique et important ? Faudrait-il alors regrouper les petits dons des particuliers par jour ?
