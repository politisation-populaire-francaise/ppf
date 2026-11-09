# Demander un document administratif (DE-31) : conception

- Pôle « Décider », 9 novembre 2026. Cahier fonctionnel 2.1, DE-31 : « Demande rédigée selon le code des relations entre le public et l'administration (CRPA) et envoyée par le citoyen lui-même ; délais comptés sur l'appareil (silence d'un mois vaut refus, article R311-13 ; saisine de la CADA dans les deux mois, article R343-1), saisine préparée ; réponse publiable au choix du citoyen (M4) ; lien vers Ma Dada (madada.fr) après « Vous quittez PPF ». » Priorité : tôt.
- Décisions techniques prises en autonomie (gratuites, aucun tiers, aucune bibliothèque nouvelle). Aucune donnée ne quitte l'appareil.

## 1. Le droit, vérifié le 9 novembre 2026

Sur Légifrance (articles en vigueur au 9 novembre 2026) et service-public.gouv.fr (fiche F2467, vérifiée le 13 août 2026 par ses auteurs) :

| Règle | Source |
|---|---|
| Les administrations de l'article L300-2 publient en ligne ou communiquent les documents administratifs qu'elles détiennent à qui les demande | [L311-1](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000033218946) |
| L'administration a **un mois** à compter de la réception de la demande | CRPA, [R311-13](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000031370409) |
| Son silence pendant ce mois **vaut refus** | [R*311-12](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000031370407), auquel renvoie R311-13 ; [service-public.gouv.fr, F2467](https://www.service-public.gouv.fr/particuliers/vosdroits/F2467) |
| **Deux mois** pour saisir la CADA, à compter du refus notifié ou de la fin du mois de silence | [R343-1](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000031370507) |
| La saisine de la CADA est **obligatoire** avant un recours au tribunal | [L342-1](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000045213381) |
| La CADA rend son avis dans **un mois** à compter de l'enregistrement de la saisine ; l'administration lui dit, dans le mois qui suit cet avis, la suite qu'elle donne | [R343-3](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000031370511) |
| Sans réponse **deux mois** après l'enregistrement de la saisine par la CADA, l'administration a refusé | [R*343-4](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000031370513), [R343-5](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000031370515) |
| Au choix du demandeur : consultation gratuite sur place, copie aux frais de reproduction au plus, courrier électronique sans frais | [L311-9](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000033218942) ; frais de reproduction et d'envoi annoncés d'avance : [R311-11](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000031370405) |
| Documents réservés à l'intéressé (vie privée, appréciation sur une personne, comportement) ; le reste est communiqué après occultation ou disjonction | [L311-6](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000037269056), [L311-7](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000031367719) |
| Pas besoin de motiver la demande ; demande orale ou écrite (courrier ou courriel), l'écrit gardant une preuve datée | service-public.gouv.fr, F2467 |
| Recours au tribunal administratif dans les deux mois de la décision de refus | service-public.gouv.fr, F2467 (code de justice administrative non relu) |

Ma Dada (madada.fr), projet de l'association OpenKnowledge France, n'est pas une source officielle : sa page d'accueil, lue le 9 novembre 2026, dit qu'elle aide à formuler la demande, qu'elle publie tous les échanges (demande, réponse, documents) et qu'elle n'est pas faite pour les documents personnels.

## 2. Dans l'application

- **Décider**, section « Demander un document administratif », puis la page du même nom :
  - trois réponses (l'administration, le document, la façon de le recevoir) remplissent sur l'appareil une demande modifiable, qui cite les articles ; des blancs (« [Nom de l'administration] », « [Vos nom et prénom] ») restent à compléter ; « Copier la demande » ;
  - la personne l'envoie elle-même (courriel, ou lettre recommandée avec accusé de réception) ; autre possibilité, **Ma Dada** (madada.fr, site associatif), après « Vous quittez PPF » ; l'écran dit que Ma Dada publie tous les échanges et n'est pas faite pour un document personnel ;
  - **« Mes demandes »** : « Noter cette demande » avec le jour de réception par l'administration (aujourd'hui par défaut, jamais un jour à venir) ; pour chaque demande, où elle en est (en attente jusqu'au…, refusée depuis le…, CADA à saisir jusqu'au…, CADA saisie, refus après la CADA, document reçu) et les gestes « Document reçu », « Refus écrit reçu aujourd'hui », « J'ai saisi la CADA aujourd'hui », « Préparer la saisine de la CADA » (texte à copier, lien vers cada.fr après « Vous quittez PPF »), « Effacer cette demande » ;
  - **rappels**, désactivés par défaut, demandés pour une demande à la fois (« Me rappeler les délais ») : le lendemain du mois sans réponse, et sept jours avant le dernier jour pour saisir la CADA, à 10 h, heure de Paris (contrat `docs/integration/rappels.md`, section 4 ter) ; recalculés quand la personne note un refus écrit, et retirés quand elle note le document reçu ou la saisine.
- Dates indicatives, comptées de date à date (le 31 devient le dernier jour d'un mois plus court) ; l'écran le dit.

## 3. Données et protections

- Table `document_request` sur l'appareil (migration locale `17.sqm`, avec les rappels et les notes des courriers) : l'administration, le document, la façon, et quatre jours (réception, document reçu, refus écrit, saisine de la CADA). Le texte de la demande n'est jamais gardé.
- Partie `decider-documents` de l'effacement rapide (S-44). Registre des données : une ligne.
- Une demande peut révéler un engagement (un permis contesté, une enquête) : rien n'est envoyé, ni à PPF ni à personne ; le serveur ignore qu'une demande existe.

## 4. Étapes

1. Fait le 9 novembre 2026 (non compilé) : rédaction, notes et délais, rappels, saisine préparée, Ma Dada ; tests `DocumentRequestTest` (cœur) et `DocumentRequestViewsTest` (interface).
2. Relire le code de justice administrative (délai du recours) ; faire relire les deux modèles de texte.
3. **Publier la réponse** reçue, au choix du citoyen (M4), avec les données des tiers masquées avant publication (L311-6, L311-7) : à concevoir avec la brique de publication et l'outil de masquage ; relecture par l'équipe.
4. Sur le site : une page explicative sans JavaScript, sans formulaire (rien à envoyer).
