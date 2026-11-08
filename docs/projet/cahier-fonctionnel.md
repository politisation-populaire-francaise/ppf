# Politisation Populaire Française : cahier fonctionnel

- Version : 2.2
- Date : 8 novembre 2026
- Statut : définitif. Toute évolution passe d'abord par `brainstorming-cahier-fonctionnel.md`.
- Documents liés : `vision.md` (pourquoi, version 4.3), `cahier-des-charges.md` (exigences, architecture, cadre légal, planning, version 2.3), `docs/DECISIONS.md` (décisions numérotées D-xxx), `docs/ROADMAP.md` (avancement).
- Origine de la version 2.0 : mandat de la propriétaire du 7 novembre 2026 (« ajouter autant de taches dans le cahier fonctionnel et cahier des charges que nécessaire à l'accomplissement de la vision du projet »), qui intègre l'inventaire des fonctionnalités validé le 4 novembre 2026 et les décisions prises jusqu'au 7 novembre 2026.
- Origine de la version 2.1 : mandat de la propriétaire du 7 novembre 2026, 23 h (« brainstormer sur les fonctionnalités indispensables, innovantes et pertinentes », puis « Pour les decisions a prendre, je suis tes recommandations ») ; les fonctionnalités retenues lors du brainstorming de la nuit du 7 au 8 novembre 2026 sont ajoutées par le chef d'équipe, par délégation de la propriétaire (entrée « [2026-11-08] Brainstorming de la nuit » de `brainstorming-cahier-fonctionnel.md`).

Ce document décrit **ce que fait la plateforme** : qui l'utilise, comment, et le détail de chaque espace et de chaque module. Les exigences de sécurité, de vie privée, d'accessibilité et le cadre légal du cahier des charges s'appliquent à toutes les fonctionnalités décrites ici. En cas de contradiction, la vision l'emporte sur le cahier des charges, qui l'emporte sur ce cahier.

## 0. Comment lire ce document

- **Plan** : les rôles (1), les parcours clés (2), les règles transverses (3), un chapitre par espace (4 En direct, 5 Voir, 6 Comprendre, 7 Agir, 8 Décider, 9 Mon espace, 10 Fil social, 11 Messagerie), les modules transverses (12), puis les annexes : correspondance des modules (A), dépendances communes (B), décisions en attente (C), identifiants retirés et pistes écartées (D).
- **Modules** : M1 à M18 gardent leur numéro, auquel renvoient le code et les documents ; les modules nouveaux portent les numéros M19 à M35. La table de l'annexe A donne l'ancien et le nouvel emplacement de chacun.
- **Fonctionnalités** : chacune garde l'identifiant stable de l'inventaire du 4 novembre 2026 : S (socle commun), MA (En direct), VO (Voir), CO (Comprendre), AG (Agir), AP (appels), DE (Décider), PR (questionnaire citoyen), TE (Mon espace), PP (À propos de PPF), EQ (outils de l'équipe). Les fonctionnalités nouvelles de la version 2.0 continuent la numérotation (S-140, VO-30, CO-21, AG-54, DE-25, TE-08 et suivants), celles de la version 2.1 la prolongent (S-145, VO-34, CO-23, AG-55, AP-11, DE-29, TE-10, PP-09 et suivants). Un identifiant retiré n'est jamais réutilisé (annexe D).
- **Priorité** (indicative, arrêtée lors du découpage en MVP, c'est-à-dire en versions successives utilisables) : « Tôt » : dans les premières versions, parce que c'est simple et utile tout de suite ou nécessaire avant la présidentielle d'avril 2027 ; « Plus tard » : lourd, ou dépendant d'autre chose ; « Phase n » : phase fixée par le cahier des charges (section 10.2). Quand ni le cahier des charges ni l'inventaire ne fixent la priorité, elle est proposée selon ces mêmes critères.
- **Dépendances et état** : ce dont la fonctionnalité dépend (méthode « une personne, une voix », avis d'un avocat, back-office de l'équipe, référentiel des territoires, hébergement du modèle d'IA, prestataire de paiement, notifications) ; « fait (D-xxx) » ou « première version faite (D-xxx) » quand la feuille de route le dit ; « en cours (D-xxx) » quand le travail a commencé ; « à faire » sinon.
- **Mentions** : « décision en attente : titre » renvoie à une entrée encore ouverte d'un brainstorming, et rien de ce qui en dépend n'est codé avant la décision ; « (ajout du chef d'équipe, 7 novembre 2026) » marque ce qui a été ajouté au titre du mandat de la propriétaire ; « (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire) » marque ce qui a été ajouté ou modifié en version 2.1 ; « mis de côté » signifie qu'on ne code rien pour l'instant.
- **Exemples** : tout contenu d'exemple est fictif et marqué [EXEMPLE].

### Mots techniques employés

| Mot | Ce qu'il veut dire |
|---|---|
| Compte, paire de clés | Un compte est une paire de clés créée sur l'appareil : la clé publique sert d'identifiant, la clé secrète signe ; aucun nom, téléphone ni e-mail n'est demandé. |
| Phrase de récupération | Douze mots à noter qui permettent de retrouver son compte sur un autre appareil ; personne d'autre ne peut le faire à votre place. |
| Contenu signé | Chaque contenu porte la signature de la clé de son auteur, ce qui permet à chacun de vérifier qui l'a publié et qu'il n'a pas été modifié. |
| Nostr, NIP | Nostr est un protocole ouvert de publication où chaque contenu est un « événement » signé ; les NIP sont ses spécifications numérotées (NIP-01 : format des événements ; NIP-17 : messages privés ; NIP-23 : articles ; NIP-29 : salons ; NIP-52 : événements de calendrier ; NIP-13 : preuve de travail ; NIP-78 : réglages ; NIP-98 : authentification). |
| Identifiant stable | Adresse d'un contenu qui ne change pas quand le contenu est mis à jour ; on peut la citer et la partager durablement. |
| Empreinte (SHA-256) | Petit code calculé à partir d'un fichier ou d'un texte : le moindre changement donne une autre empreinte. |
| Horodatage (OpenTimestamps, RFC 3161) | Preuve qu'une empreinte existait à une date donnée, délivrée par des services indépendants : on peut prouver plus tard qu'un document n'a pas été modifié depuis. |
| Jeton anonyme | Sorte de ticket de vote que le serveur signe sans voir ce qu'il signe ; quand le ticket revient avec le bulletin, le serveur ne peut pas savoir à qui il l'avait donné (méthode normalisée VOPRF, RFC 9497). |
| Preuve de travail | Petit calcul que l'appareil fait avant d'envoyer certaines demandes ; il ne gêne pas une personne, mais rend coûteux l'envoi de milliers de demandes par un robot. |
| Hors ligne d'abord | L'application garde une copie de ce qui est utile et fonctionne sans réseau ; ce qu'on envoie part au retour de la connexion. |
| PWA | Site web installable sur l'écran d'accueil comme une application ; c'est la solution sur iPhone tant que l'application iOS n'est pas publiée. |
| UnifiedPush, ntfy | Moyens d'envoyer des notifications sans passer par Google, avec un serveur que PPF héberge elle-même. |
| Tor, adresse .onion | Réseau qui cache l'adresse de l'internaute et permet d'atteindre un site même quand son adresse normale est bloquée. |
| bitchat | Messagerie qui fonctionne sans Internet, de téléphone en téléphone par Bluetooth. |
| MLS | Méthode de chiffrement normalisée pour les grands groupes de discussion, prévue plus tard. |
| Métadonnées | Informations cachées dans une photo ou une vidéo (lieu GPS, date, modèle d'appareil), supprimées sur l'appareil avant tout envoi. |
| C2PA | Norme qui inscrit dans un fichier son origine et ses modifications, pour prouver qu'une image n'a pas été truquée. |
| Back-office | Espace réservé à l'équipe de PPF (modération, administration, publication), sur le site web. |
| CMS | Outil de rédaction, de relecture et de publication des contenus de l'équipe. |
| iCal, RSS | Formats ouverts pour ajouter des événements à son agenda (iCal) ou suivre des publications sans application (RSS). |
| RGPD, DSA, LCEN | Règlement européen sur les données personnelles ; règlement européen sur les services numériques (signalement, recours, transparence) ; loi française sur l'économie numérique (mentions légales, droit de réponse). |
| ITT | Incapacité totale de travail fixée par un médecin ; elle sert en droit pénal à mesurer la gravité d'une blessure. |
| Référentiel des territoires | Liste commune des communes, départements et régions, avec leur population et leurs électeurs inscrits, utilisée par tous les espaces. |
| Clé de secours | Seconde clé dérivée de la phrase de récupération, jamais gardée sur le téléphone, qui permet depuis un autre appareil de déclarer son compte compromis et de passer à une nouvelle clé (S-146). |
| Empreinte visuelle | Code calculé à partir de l'aspect d'une image : deux copies de la même image, même redimensionnées ou recompressées, ont des empreintes proches (CO-29). |
| ActivityPub, fédivers | ActivityPub est le protocole ouvert de Mastodon et de Mobilizon ; le fédivers est l'ensemble des sites reliés par ce protocole. |
| LoRa | Radio longue portée et peu gourmande, qui transmet de courts messages sur plusieurs kilomètres sans réseau téléphonique (type Meshtastic). |
| OONI | Observatoire ouvert des blocages d'Internet, qui publie ses mesures en données ouvertes. |

## 1. Rôles

| Rôle | Comment on l'obtient | Peut faire |
|---|---|---|
| Lecteur anonyme | Sans compte | Tout consulter (fil, carte, fiches, recherche), utiliser le bouton AIDE (AG-27), répondre à une mobilisation ou à un appel (gardé sur l'appareil), signaler une erreur anonymement (VO-22), écrire à l'équipe avec un numéro de dossier (S-105). |
| Compte non vérifié | Paire de clés créée sur l'appareil, preuve de travail à l'inscription | Publier dans le fil, commenter les débats, proposer des revendications et des événements, lancer des appels, créer un salon, témoigner, signaler, voter (son vote est compté dans la colonne « comptes non vérifiés »). |
| Compte vérifié | Validation en personne par un validateur (S-88) | En plus : vote compté dans la colonne mise en avant « comptes vérifiés » ; créer un collectif (M24) ; se porter volontaire pour un tirage au sort (DE-15, S-140). |
| Élu certifié | Certification en personne par l'équipe et mandat contrôlé dans l'annuaire officiel des élus (D-047) | Répondre publiquement aux interpellations et au Pacte (DE-23), voter dans la colonne des élus, jamais additionnée à celle des citoyens (D-112). |
| Organisation vérifiée | Demande de vérification (S-81) examinée par l'équipe (EQ-07) | Page, publications et calendrier publiés sans relecture dès la vérification (D-039), participation publique confirmée (D-045), appels à dons (AP-08), signalements de confiance (S-69), désigner des validateurs. Une organisation ne vote pas (D-112). |
| Membre d'un collectif | Adhésion au collectif (S-132) | Selon son rôle dans le collectif : fondateur, administrateur, modérateur, membre, ou statut personnalisé (S-131). |
| Bénévoles certifiés en manifestation | Certification en personne ou par une organisation vérifiée | Street medic (AG-28), avocat bénévole (AG-32), équipe citoyenne de sécurité (AG-45), observateur ou legal team (AG-40). |
| Validateur | Bénévole désigné par une organisation vérifiée et agréé par PPF | Ouvrir des séances de validation en personne, avec un quota (S-88). |
| Modérateur | Nommé par un administrateur (S-57) | Traiter la file de modération, décider, motiver, notifier (M15). |
| Éditeur, directeur de la publication | Nommé par un administrateur | Rédiger, relire, valider et publier les contenus éditoriaux ; le directeur de la publication traite les droits de réponse (VO-21, EQ-04). |
| Trésorier | Désigné par l'association | Saisir les mouvements d'argent de la trésorerie publique, validés à deux (PP-06). |
| Administrateur | Nommé par un super-administrateur | Gérer l'équipe, les groupes, les permissions et la configuration (M15). |
| Super-administrateur | La propriétaire et une seconde personne | Nommer et retirer les administrateurs ; deux au minimum, chacun avec une clé de sécurité physique (S-56). |

- Tous les comptes de l'équipe ont la double authentification obligatoire et des permissions minimales ; toutes les actions de modération et d'administration sont journalisées (S-63).
- Aucun rôle, pas même le super-administrateur, ne peut voir les votes, les soutiens ou les abonnements d'un utilisateur : l'architecture rend cette consultation impossible, et pas seulement interdite (M16).
- Les partis et les candidats n'ont pas d'espace d'organisation (point d'attention n° 7, option A) ; ils sont présents dans « Voir » et dans les outils électoraux, tous de la même façon, et peuvent exercer leur droit de réponse (VO-21).
- La plateforme est ouverte à tous, sans limite d'âge (point d'attention n° 13).

## 2. Parcours clés

Le chemin du citoyen va de « Voir » à « Décider » : voir ce qui se passe, comprendre comment cela fonctionne, agir ensemble, et décider à nouveau. C'est ce que le collectif appelle la politisation populaire (vision, section 6). Chaque contenu se termine par « Et maintenant ? », qui propose l'étape suivante.

**Entrer**
- **Découvrir en 30 secondes** : l'application s'ouvre sur le fil (M18) ; un geste sur « En direct » (M19) montre les chiffres clés, les prochaines mobilisations, les alertes, la revendication et la campagne à la une. Tout se lit sans compte.
- **Premier lancement** : trois écrans au plus, qu'on peut passer : ce qu'est PPF, « tout est lisible sans compte », ce que l'application collecte ou non (S-01).

**Voir**
- Un lecteur voit un chiffre de la mobilisation avec toutes ses sources côte à côte (M2), ouvre la fiche d'un responsable public (votes, promesses, affaires avec le statut exact de la procédure, M17) ou « Cette semaine au Parlement » (VO-31), et suit ce qui l'intéresse. « Et maintenant ? » l'emmène vers « Qui décide de ça ? » ou « Interpeller mes élus ».

**Comprendre**
- Il choisit un sujet de sa vie quotidienne et sa commune : il découvre la chaîne de décision, ses élus nommés, leurs votes et comment les contacter (CO-01, TE-01) ; il suit un parcours de cinq minutes avec un quiz (CO-02) ; un mot difficile s'explique d'un geste (CO-14) ; il vérifie une rumeur (CO-04, CO-18).

**Agir**
- **Se mobiliser** : il trouve une manifestation près de chez lui (M1) ; il prépare sa sortie avec le mode manifestation, ses droits hors ligne (AG-13) ; sur place, il a la carte en direct et le bouton AIDE (M25) ; après, il témoigne (M5) et trouve du soutien s'il a été blessé (AG-14).
- **S'organiser** : il répond à un appel ou en lance un (M12), rejoint ou crée un collectif (M24), suit une organisation (M3).

**Décider**
- **Revendiquer** : il propose une revendication, vote pour celles des autres, débat (M11).
- **Peser** : il signe une pétition officielle sur le site officiel (M10), soutient une campagne (M13.5), interpelle ses élus (DE-09), répond à une consultation publique (DE-27).
- **Exiger des engagements** : il suit le Pacte démocratique et délibère sur ses mesures (DE-08, DE-25).
- **Décider ensemble** : il vote dans une votation ou une délibération, une personne, une voix, sans que personne puisse savoir ce qu'il a voté (M13.6, M16).
- **S'engager** : il crée une association ou une liste et se présente avec le kit du candidat citoyen (M13.4).

**Parcours qui traversent les espaces**
- **Se faire vérifier** : il montre le code QR de son compte à un validateur lors d'un stand ou d'une assemblée ; son vote compte désormais dans la colonne des comptes vérifiés (S-88).
- **Retrouver ce qu'on suit** : dans Mon espace, ses élus, ses votes, ses suivis et ses rappels, tous gardés sur son appareil (M27).
- **Vérifier un contenu reçu** : il colle un lien ou ouvre une capture partagée et sait si elle vient bien de PPF et n'a pas été modifiée (S-141, ajout du chef d'équipe, 7 novembre 2026).
- **En classe, sans compte** : un enseignant projette un parcours adapté et utilise la fiche pédagogique qui l'accompagne (CO-13, ajout du chef d'équipe, 7 novembre 2026).
- **Citer un chiffre** : une journaliste copie un chiffre avec sa source et une référence stable (S-144, ajout du chef d'équipe, 7 novembre 2026).
- **Écrire à l'équipe ou réclamer** : tout se passe dans l'application, avec un numéro de dossier et des délais affichés (M30).

## 3. Règles transverses

Elles s'appliquent à tous les espaces et à tous les modules.

1. **Sources** : chaque donnée affichée comporte sa source, sa date et son niveau de fiabilité. Quand les sources divergent, elles sont affichées côte à côte, jamais fusionnées en un chiffre unique sans explication. Un chiffre copié emporte toujours ses sources (« Copier avec la source », D-034).
2. **Établi, allégué, en cours d'enquête** : la distinction est toujours visible ; le vocabulaire est normé (« déclaration contredite par les faits » plutôt que « mensonge », sauf décision de justice) ; une personne n'est présentée comme l'auteur d'une infraction que si le fait est établi (M17).
3. **Pluralisme** : les contenus de « Comprendre » présentent les objections et y répondent de façon sourcée (bloc « Objections et réponses »), obligatoire dans les décryptages et l'histoire des luttes, recommandé ailleurs (décision de la propriétaire du 7 novembre 2026, D-038).
4. **« Et maintenant ? »** : chaque contenu, dans tous les espaces, se termine par une action concrète proposée, souvent dans un autre espace.
5. **Origine de chaque contenu** : chaque contenu indique s'il a été rédigé par un humain ou produit avec l'aide d'une IA et, le cas échéant, sa relecture par un membre de l'équipe (décision de la propriétaire ; règlement européen sur l'IA, article 50).
6. **Protéger par défaut, laisser le choix** (vision 4.2 ; cahier des charges, principe 10) :
   - chaque réglage de confidentialité et d'anonymat est livré à son niveau le plus protecteur : profil réduit au pseudonyme et à l'avatar, comptes et organisations suivis privés, statuts des relations privés, appartenance à un collectif visible des seuls administrateurs, messages privés et demandes limités, bord politique non affiché, aucun partage automatique, sauvegarde chiffrée désactivée ;
   - chacun peut assouplir ces réglages pour ses propres données, jusqu'à les rendre publiques, cercle par cercle ou pour tous ;
   - chaque assouplissement affiche d'abord une mise en garde simple (ce qui deviendra visible, par qui, les risques de recoupement, de capture d'écran, de saisie ou de piratage), avec « Je comprends, je confirme » et « Annuler » ; on peut revenir en arrière à tout moment, en sachant qu'une information déjà vue ou copiée ne peut pas être effacée partout ;
   - cette liberté ne s'étend jamais aux données des autres, ne lève aucune obligation légale et ne modifie pas les protections communes : le serveur ne relie jamais un vote à un compte, même si l'utilisateur publie lui-même ses votes, et l'envoi différé des bulletins s'applique à tous (D-112).
7. **Opinions politiques, données sensibles** (RGPD, article 9) : les votes, soutiens, participations, suivis, vues personnelles et le territoire de chacun restent sur son appareil (avec une sauvegarde chiffrée facultative, S-112) ; la plateforme ne constitue jamais de profil politique individuel ; seuls des résultats agrégés sont publiés.
8. **Une personne, une voix** : tout vote, soutien ou compteur de participation passe par des jetons anonymes ; les résultats sont publiés par niveau de votant, les comptes vérifiés en premier, le vote des élus à part (M16).
9. **Traitement identique** : les outils qui concernent des candidats (Pacte démocratique, comparateur, baromètre des élus) les présentent tous de façon strictement identique, sans appel à voter ; c'est une règle de méthode et une limite légale (une personne morale ne finance aucune campagne et n'accorde aucun avantage à un candidat, même en nature ; cahier des charges, 9.2). Les fiches de « Voir » présentent les mêmes rubriques dans le même ordre pour tous, et les mêmes critères d'entrée s'appliquent à tous les partis (M17).
10. **Charte de contenu et légalité** : la charte (cahier des charges, 9.3) est acceptée par chaque contributeur et sert de référence à la modération. Aucun appel ni aucune incitation à la violence : c'est une obligation légale, pas un choix éditorial. Les contenus pédocriminels et terroristes sont signalés aux autorités (PHAROS).
11. **Transparence d'abord, curseur au maximum** (doctrine de la propriétaire, D-044) : « la transparence passe avant tout le reste » ; on pousse au maximum ce qu'un citoyen peut faire, même quand cela pourrait attirer des problèmes, et tout ce qui est poussé ainsi est inscrit dans la liste « à faire relire par un avocat » de la feuille de route, avant l'ouverture publique. Les obligations légales restent non négociables.
12. **Publication immédiate, relecture ciblée** (point d'attention n° 33) : seuls les témoignages, les appels à témoins et les revendications sont relus avant publication ; tout le reste se publie immédiatement, est analysé par l'IA juste après et peut être signalé par tous. Tant que la modération par IA n'est pas en service, les contenus des comptes qui ne sont ni l'équipe ni une organisation vérifiée passent par une relecture humaine (D-039).
13. **Hors ligne d'abord** : les fiches pratiques, la carte, l'agenda, les contenus consultés et les données essentielles restent disponibles sans réseau ; ce qui est fait hors ligne part au retour de la connexion (S-26, S-27).
14. **« Vous quittez PPF »** : aucun site extérieur ne s'ouvre sans avertissement, avec « Rester sur PPF » proposé en premier, parce que le site visité voit l'adresse de l'internaute (D-022, D-034).
15. **Contenus signés, identifiants stables, export ouvert** : chaque contenu publié est signé au format d'événement Nostr, a un identifiant stable et peut être exporté en JSON documenté (D-018, S-46, S-48).
16. **Facile à lire** : version simplifiée des contenus essentiels (S-18).
17. **Accessible et adaptatif** : WCAG 2.2 niveau AA, les mots avant les couleurs (une couleur n'est jamais le seul signe), écrans adaptés au téléphone, à la tablette et à l'ordinateur en portrait et en paysage, modes clair et sombre, textes de l'interface prêts pour la traduction, français par défaut (M32).
18. **Mineurs** : aucune restriction de communication selon l'âge ; seuls le questionnaire citoyen et l'affichage du bord politique sont réservés aux personnes qui déclarent 15 ans ou plus ; des réglages protecteurs par défaut pour tous, puisque l'âge n'est pas connu (point d'attention n° 13).
19. **Territoire** : le code postal ou la commune de chacun est gardé sur son appareil, jamais envoyé (S-20, D-047). Un référentiel commun des territoires, confié au pôle « Comprendre » (D-062), sert à « Qui décide de ça ? », aux salons, aux objectifs de signatures, aux cahiers de doléances et à l'argent public local.
20. **Aucun traceur, aucun tiers** : l'application ne contacte aucun service tiers ; les statistiques sont uniquement agrégées et anonymes : on compte ce qui se passe, on ne suit jamais le parcours d'une personne (point d'attention n° 23).
21. **Ni classement ni badge de militants** : écartés volontairement, parce qu'ils poussent à la course à l'activité, favorisent la manipulation et révèlent l'engagement de chacun.
22. **Données réelles** : aucune donnée personnelle réelle dans les tests, les jeux de données, la préproduction ou les captures ; tout exemple est marqué [EXEMPLE].

## 4. En direct

L'espace « En direct » est l'accueil de l'information du moment, à un geste depuis la barre du bas. L'écran par défaut de l'application reste le fil (M18). « En direct » assemble sur l'appareil des contenus qui vivent dans les autres espaces et ouvre chacun là où il vit (D-037).

### M19. En direct

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| MA-01 | Chiffres clés | Les trois à cinq indicateurs du moment, repris de « Voir », avec leurs sources. | Tôt | Première version faite (D-036, D-037). |
| MA-02 | Prochaines mobilisations | Les trois prochaines, hors annulées, et près de chez soi si le territoire est renseigné ; elles s'ouvrent dans « Carte ». | Tôt | Première version faite (D-039). |
| MA-03 | Fil d'actualité | Articles, vidéos, podcasts et formats courts de PPF et des organisations vérifiées, l'auteur toujours indiqué, avec boutons de partage (ancien fil de M6). | Tôt | Dernières publications de PPF affichées (D-037) ; reste le fil complet, la vidéo et l'audio (S-32, S-33). |
| MA-04 | Alertes | Vote proche au Parlement, interdiction de manifester, échéance d'une pétition, appel urgent. | Tôt | Votes et interdictions faits (D-037) ; reste pétitions et appels ; notifications (M31). |
| MA-05 | À la une | La revendication et la campagne prioritaires du moment. | Tôt | Fait (D-037). |
| MA-06 | Journée de mobilisation en direct | Pendant une journée de mobilisation, fil en direct alimenté par l'équipe et les organisations vérifiées : rassemblements, changements de parcours, alertes, chiffres ; lien vers la carte de la manifestation (AG-24). | Tôt | Bandeau « EN DIRECT » et publications de l'équipe faits (D-037) ; reste la publication par les organisations. |

« En direct » reprend aussi le bloc « Cette semaine au Parlement » (VO-31, décrit en M20 ; ajout du chef d'équipe, 7 novembre 2026).

## 5. Voir

« Voir » rend visible, de façon chiffrée et sourcée, ce qui se passe et ce que fait le pouvoir, en France et, pour nos représentants, au niveau européen (vision, pilier 1). Il a trois axes, le mouvement, le pouvoir, les libertés et les contre-pouvoirs, complétés par l'état du pays. Seule l'équipe éditoriale publie dans « Voir » : l'application n'affiche que des contenus signés par une clé de confiance (D-034). Chaque contenu a aussi sa page sur le site, lisible sans JavaScript, à une adresse stable (en cours, D-055).

### 5.1 Le mouvement

#### M2. Observatoire : les chiffres

- **Indicateurs configurables par l'équipe sans code** : création, définition, unité, méthode de calcul, sources acceptées (EQ-01).
- **Indicateurs de départ** : nombre de manifestants par manifestation, passée ou à venir ; nombre de personnes participant au mouvement en France ; syndicats et associations membres du mouvement ; lycées et universités bloqués, actualisés en temps réel ; blessés légers, blessés graves, décès (avec la mention « dont mineurs ») ; vitrines et commerces dégradés ou pillés ; victimes de violences policières avec leur indice de gravité ; interpellations, gardes à vue, nasses (nombre et durée moyenne) ; amendes forfaitaires (MICA) reçues par manifestation ; comptes de réseaux sociaux déclarant un « shadowban », c'est-à-dire une visibilité réduite (indicateur déclaratif, signalé comme tel).
- **Chaque donnée** comporte : valeur, date, lieu, type de source (préfecture ou ministère, syndicats ou organisateurs, comptage indépendant, médias, signalements d'utilisateurs), lien vers la source, méthode, niveau de fiabilité.
- **Indice de gravité des violences policières** (décision de la propriétaire du 7 novembre 2026 : « Oui on inscrit l'ordre de gravité dans le cahier fonctionnel » ; D-057) :
  1. sans incapacité (pas d'ITT) ;
  2. blessure légère (ITT de 8 jours ou moins) ;
  3. blessure grave (ITT de plus de 8 jours) ;
  4. mutilation ou infirmité permanente ;
  5. décès.
  Pour chaque cas : mineur ou non, arme ou moyen en cause, niveau de preuve (certificat médical, vidéo, décision de justice, déclaration seule). Les cas déclaratifs sont comptés à part, jamais mélangés aux cas vérifiés. L'indice d'une journée est la répartition des cas entre les cinq niveaux, jamais une note unique, qui cacherait les cas graves.

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| VO-01 | Observatoire des chiffres | Indicateurs ci-dessus, créés sans code ; les nasses documentées en direct (AG-46) l'alimentent. | Phase 1 | Première version faite, contenus [EXEMPLE] (D-034) ; reste l'éditeur sans code (M15). |
| VO-02 | Sources côte à côte | Toutes les sources d'un chiffre affichées ensemble, jamais fusionnées sans explication. | Phase 1 | Fait (D-034). |
| VO-03 | Signalements chiffrés des citoyens | Un citoyen signale un blocage, une nasse, une blessure ; une personne, un signalement, grâce aux jetons anonymes ; détection des comportements anormaux ; validation avant intégration. | Tôt | Dépend de « une personne, une voix » (en cours, D-110, D-112). |
| VO-04 | Indice de gravité des violences policières | Grille en cinq niveaux ci-dessus, avec mineur, moyen en cause et niveau de preuve. | Tôt | Grille décidée (D-057) ; à coder. |
| VO-05 | Visualisations et méthodologie | Compteurs, courbes, cartes ; chaque graphique a sa version en tableau ; méthode publique pour chaque indicateur. | Phase 1 | Graphiques et tableaux faits (D-034) ; cartes avec la vue carte (D-046). |
| VO-40 | Comptage de foule participatif | Estimation de la foule d'une manifestation par la méthode de Jacobs (surface multipliée par densité), un bulletin par compte, vérifié ou non, grâce à l'urne anonyme ; résultat affiché à côté des chiffres de la préfecture et des organisateurs, méthode publique. | Tôt (après la carte de la manifestation, M25) | Outil sur la carte : pôle Agir (AG-24) ; observatoire : pôle Voir ; urne : pôle Décider (« une personne, une voix ») ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |

#### M4. Chronologie et archives

Les fiches des responsables publics ont rejoint M17, les votes des parlementaires M20, et la fiche « Accès aux archives publiques » M6 (CO-10).

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| VO-06 | Chronologie du mouvement | Manifestations, déclarations officielles, décisions publiées (Journal officiel, décrets, arrêtés préfectoraux, circulaires), réponses du gouvernement. | Phase 2 | Première version faite (D-034). |
| VO-07 | Archives des documents officiels | Pour chaque élément : copie archivée, empreinte horodatée (OpenTimestamps, avec RFC 3161 en complément), lien vers Internet Archive, historique des versions. | Phase 2 | Horodatage décidé (D-111), à coder. |
| VO-08 | Témoignages publiés | Galerie et carte des témoignages et vidéos vérifiés et anonymisés (M5), dans « Le mouvement ». | Phase 2 | Dépend du canal sécurisé (AG-07) et du back-office de modération (M15). |
| VO-38 | Que sont devenues nos paroles ? | Suivi mesure par mesure des propositions citoyennes passées (Convention citoyenne pour le climat, grand débat de 2019 et cahiers de doléances), avec une méthode publique. | Tôt | Travail éditorial du pôle Voir ; comptages divergents montrés côte à côte (VO-02) ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| VO-42 | Journal des textes officiels modifiés | Chaque modification d'un texte officiel (Légifrance ou données LEGI) relevée et datée, avec l'empreinte de chaque version. | Plus tard | Accès gratuit à l'API de Légifrance (PISTE) à créer par la propriétaire, guide à prévoir ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |

#### M9. International

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| VO-09 | Mouvements dans le monde | Carte et chronologie des mouvements sociaux liés à la vie chère et à la démocratie : revendications, déroulé, résultats, y compris les changements de gouvernement obtenus. | Phase 5 (version complète) | Première version faite (D-034). |

### 5.2 Le pouvoir, les libertés et les contre-pouvoirs

#### M17. Mettre en lumière

Validé par la propriétaire le 7 novembre 2026 (D-036). Au niveau national et européen (représentants français au Parlement européen et au Conseil). L'ancien étage 2 de M13, « Voir pour qui la loi est faite » (M13.2), en fait partie : il montre les faits, sans affirmer de conclusion (VO-12, VO-13, VO-17, VO-18, VO-19).

- **Méthode obligatoire** :
  - uniquement des faits établis par des sources primaires (décisions de justice, Journal officiel, comptes rendus parlementaires, rapports officiels) ou par des enquêtes de presse identifiées ; distinction toujours visible entre ce qui est établi, allégué et en cours d'enquête ;
  - présomption d'innocence : statut de la procédure affiché sur chaque fiche (enquête, mise en examen, condamnation en première instance, appel, condamnation définitive, relaxe) ;
  - vocabulaire normé : « déclaration contredite par les faits » plutôt que « mensonge », sauf décision de justice ;
  - contradictoire : la personne concernée est invitée à réagir avant publication, et sa réaction est publiée ;
  - droit de réponse (LCEN, article 1-1, III) publié sous le contenu dans les trois jours ; journal public des corrections ; double validation éditoriale avant publication (EQ-03) ;
  - points juridiques à faire valider par un avocat avant la publication de personnes réelles : diffamation, amnistie et réhabilitation, réutilisation des décisions de justice en données ouvertes, droit à l'oubli.
- **Fiches identiques pour tous** : chaque fiche présente les mêmes rubriques dans le même ordre, quelle que soit la personne ; les couleurs décrivent un fait (promesse tenue en vert, contredite en rouge ; « y gagne » en vert, « y perd » en rouge), toujours accompagnées du mot ; les votes restent neutres.
- **Ordre d'entrée des responsables publics** (décision de la propriétaire du 7 novembre 2026) : d'abord les personnes citées dans les contenus publiés, puis les grands partis, puis les personnes les plus médiatisées de ces partis, puis les personnes condamnées en justice, celles qui ont une affaire en cours, celles dont une affaire a été classée sans suite ou s'est terminée par une relaxe. Les mêmes critères s'appliquent à tous les partis. Un parti a sa propre fiche avant les personnes de ce parti.
- Aucune personne réelle n'est publiée avant la relecture juridique et la double validation éditoriale (D-036).

**Le pouvoir**

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| VO-10 | Fiche de responsable public | Mandats, fonctions passées, lien vers ses déclarations d'intérêts publiées ; point d'entrée vers toutes les rubriques ci-dessous. | Phase 2 | Première version faite (D-034) ; parlementaires importés des données ouvertes (en cours, D-064). |
| VO-30 | Fiche de parti | Structure de financement obligatoire, sièges par source, aide publique par année, groupe européen, direction ; partis rangés par nom, jamais par taille ; seuls les faits qui visent le parti lui-même. | Tôt | Décision de la propriétaire (D-036) ; en cours (D-054). |
| VO-12 | Promesses | Engagements électoraux tenus, en cours, abandonnés ou contraires, avec la source de la promesse et celle de l'acte (M13.2). | Phase 2 | Première version faite (D-034). |
| VO-13 | Discours et votes | Votes contraires aux déclarations publiques et aux programmes, y compris ceux des eurodéputés français (M13.2). | Phase 2 | Première version faite (D-034) ; votes réels avec M20. |
| VO-14 | Affaires et peines | Statut exact de chaque procédure, peine prononcée et peine réellement exécutée (aménagements, réductions, prescriptions, relaxes), comparaison avec le traitement habituel de la même infraction pour un citoyen ordinaire. | Phase 2 | Première version faite (D-034) ; comparaison à faire ; avocat. |
| VO-15 | Déclarations contredites par les faits | Affirmation, date, contexte, faits établis, sources ; lien avec les décryptages (CO-04). | Phase 2 | Première version faite (D-034). |
| VO-16 | Argent public | Détournements et condamnations pour atteinte à la probité, rapports de la Cour des comptes et des chambres régionales des comptes, suites données à leurs recommandations. | Phase 2 | Première version faite (D-034). |
| VO-17 | Conflits d'intérêts et pantouflage | Allers-retours entre fonctions publiques et secteur privé, avis de la HATVP, déclarations d'intérêts des élus, liens documentés entre décisions publiques et intérêts privés (M13.2). | Phase 2 | Première version faite (D-034) ; décision en attente : Réutilisation des données de la HATVP (brainstorming du cahier des charges). |
| VO-18 | Influence des lobbies | Rencontres entre représentants d'intérêts et décideurs sur chaque loi importante, d'après le répertoire de la HATVP, saisies et relues à la main (M13.2). | Phase 2 | Première version faite (D-034). |
| VO-19 | Qui gagne, qui perd | Pour chaque loi importante, effets chiffrés et sourcés sur les différentes catégories de la population (M13.2). | Phase 2 | Première version faite (D-034). |
| VO-39 | Écart entre vote et parole | Rapprochement automatique, côte à côte, des scrutins et des promesses ou déclarations saisies sur le même texte, sans aucun verdict. | Tôt (après l'alerte VO-34) | Méthode M17 ; votes (M20) ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| VO-21 | Droit de réponse | Bouton sous tout contenu qui nomme ou désigne une personne ; un compte vérifié sert de voie rapide, sinon un formulaire avec justificatif d'identité ; longueur : celle du contenu en cause, avec un minimum de 1 000 mots ; contrôle par le directeur de la publication, publication sous trois jours sous le contenu (« Droit de réponse de [nom] ») et mention en haut du contenu ; ouvert aux partis et aux candidats ; alerte de niveau 5 dans l'équipe (S-115). | Tôt (obligation légale) | Affichage fait (D-034) ; formulaire chiffré vers l'équipe en cours (D-056) ; avis d'avocat sur les motifs de refus. |
| VO-22 | Journal des corrections et « Signaler une erreur » | Liste publique de toutes les corrections, avec leur date et leur motif ; sous chaque contenu, signaler une erreur, anonymement par défaut. | Tôt | Journal fait (D-034) ; signalement en cours (D-056). |

**Libertés et contre-pouvoirs**

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| VO-23 | Indépendance de la justice | Modes de nomination, liens hiérarchiques avec l'exécutif, interférences établies, décisions et rapports qui en traitent. | Phase 2 | Première version faite (D-034). |
| VO-24 | À qui appartiennent les médias | Carte des propriétaires, de leurs autres activités économiques et de leurs liens avec le pouvoir ; analyse des lignes éditoriales selon une méthode publique et chiffrée, jamais par simple appréciation. | Phase 2 | Première version faite (D-034). |
| VO-25 | Tableau de bord des contre-pouvoirs | Recours au 49.3, suites données aux rapports d'autorités indépendantes, classements de liberté de la presse, interdictions de manifester. | Phase 2 | Première version faite (D-034). |
| VO-26 | Observatoire des libertés publiques | Arrêtés d'interdiction de manifester, dissolutions d'associations, lois et décrets restrictifs, dispositifs de surveillance (drones, vidéosurveillance algorithmique), recours engagés. | Tôt | Première version faite (D-034). |
| VO-45 | Observatoire des blocages | Prolonge VO-26 : carte des blocages de PPF et d'autres sites, avec les données ouvertes d'OONI. | Plus tard | Sonde de blocage du Directeur technique (cahier des charges, 3.3) ; OONI à inscrire dans la liste des sorties ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| VO-33 | Actes et discours de haine | Documentation sourcée des violences et des discours racistes, antisémites, fascistes ou néonazis et des groupes qui les portent, avec la méthode de M17 ; liens vers le signalement (S-38), le soutien aux victimes (AG-14) et « Comprendre le fascisme » (CO-22). | Plus tard | Avocat (diffamation) ; (ajout du chef d'équipe, 7 novembre 2026). |

#### M20. Le Parlement : votes et textes en cours

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| VO-11 | Votes des parlementaires | Scrutins publics et votes nominatifs des députés et des eurodéputés élus en France, importés automatiquement des données ouvertes officielles (avancé de la phase 5) ; qui a voté quoi ; historique jusqu'à 50 ans (numérisation du Journal officiel) en phase 5. | Tôt (import), Phase 5 (historique) | En cours (D-064, D-065) ; votes nominatifs du Sénat plus tard, faute de données ouvertes exploitables. |
| VO-20 | Législomètre | Textes en discussion, étape, calendrier des votes, amendements importants, compteur des 49.3, bouton « Interpeller mes élus avant le vote » (DE-09). | Tôt | Première version faite (D-034). |
| VO-31 | Cette semaine au Parlement | Ordre du jour, textes en discussion et scrutins à venir de l'Assemblée nationale et du Sénat, tirés de leurs données ouvertes, avec des liens vers « Qui décide de ça ? » (CO-01) et « Interpeller mes élus » (DE-09) ; repris dans « En direct ». | Tôt | Données ouvertes (D-064) ; (ajout du chef d'équipe, 7 novembre 2026). |
| VO-34 | Alerte « mes élus ont voté » | L'appareil, qui connaît mes élus (TE-01), signale chaque nouveau scrutin où ils ont voté, avec leur position et leur mise au point éventuelle ; alertes aussi sur un mot suivi (amendement, scrutin, question), triées sur l'appareil : le serveur ne reçoit jamais les mots suivis. | Tôt | Données du Parlement (D-064) ; notifications (M31), avec le pôle Communauté ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| VO-35 | Présence et délégations | Sur les grands scrutins, présence et délégations de vote par groupe et par chambre, avec la définition affichée ; aucun classement d'élus. | Tôt | Données déjà importées (D-064) ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| VO-36 | Applicomètre | Pour chaque loi, les décrets d'application attendus et publiés, les retards et le taux d'application, reliés aux promesses (VO-12). | Tôt | Données de l'Assemblée et baromètre de l'application des lois (data.gouv.fr), licence à vérifier ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| VO-37 | Parrainages de la présidentielle 2027 | Listes publiées par le Conseil constitutionnel, par candidat et par département ; « mon maire, mon député a parrainé » dans Mes élus (TE-01) ; affichage factuel identique pour tous, sans bouton pour interpeller les maires ; repris dans le comparateur (DE-16). | Tôt (février et mars 2027) | Données rendues publiques par la loi ; traitement identique (règle transverse n° 9) ; affiché aussi par le pôle Décider ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| VO-41 | Délai de réponse aux questions écrites | Pour chaque ministère, délai de réponse aux questions écrites des parlementaires (règlement de l'Assemblée nationale, article 135), sans classement des députés ; plus tard, questions proposées par des citoyens. | Tôt ; Plus tard pour les questions citoyennes | Données du Parlement (D-064) ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| VO-43 | Ce que le Parlement a changé dans le texte | Versions d'un texte comparées article par article, avec l'amendement et l'auteur de chaque changement. | Plus tard | Code de La Fabrique de la loi : licence à étudier ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| VO-44 | Contributions extérieures au Conseil constitutionnel | Les contributions extérieures (« portes étroites ») adressées au Conseil constitutionnel sur les lois qui lui sont soumises. | Plus tard | Vérifier ce qui est publié ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |

« Ce que cette loi change pour moi » (CO-15) et « Suis une loi qui me concerne » (CO-28), décrits dans « Comprendre », se rattachent aussi à ce module (brainstorming du 8 novembre 2026).

### 5.3 L'état du pays

#### M21. État du pays et argent public local

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| VO-29 | État du pays | Tableau de bord des chiffres du constat de la vision (pauvreté, fortunes, lits d'hôpital, effectifs par classe, etc.), mis à jour avec leurs sources. | Tôt | Choix des chiffres : décision en attente : Nouvelle version du constat (section 3) (brainstorming de la vision). |
| VO-27 | Carte des fermetures de services publics | Maternités, urgences, bureaux de poste, classes, gares fermés ou menacés, d'après les données ouvertes et des signalements citoyens vérifiés (VO-03). | Plus tard | Vue carte (D-046) ; « une personne, une voix » pour les signalements. |
| VO-28 | Observatoire de la vie chère | Évolution des prix des carburants, de l'énergie, des loyers et d'un panier alimentaire, d'après des données ouvertes. | Plus tard | Sources officielles à vérifier. |
| VO-32 | Argent public local | Budgets et comptes de chaque collectivité (données ouvertes de la DGFiP), marchés publics (données essentielles de la commande publique), subventions publiées, présentés par commune grâce au référentiel des territoires ; lien avec « Où vont nos impôts » (CO-16) ; « Budget de ma commune » : présentation pédagogique par le pôle Comprendre et comparaison avec des communes de même taille. | Plus tard | Référentiel des territoires ; sources officielles à vérifier ; (ajout du chef d'équipe, 7 novembre 2026) ; budget de ma commune avec le pôle Comprendre (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |

## 6. Comprendre

« Comprendre » explique qui décide de quoi et comment fonctionnent les institutions, les droits de chacun, le vrai et le faux, l'histoire des mouvements sociaux et des révolutions (vision, pilier 2). Public visé en priorité : les adultes, qui pour la plupart n'ont jamais reçu d'éducation civique concrète. Les contenus sont signés, intégrés à l'application pour être lus hors ligne et relus par l'équipe éditoriale avant toute publication réelle (D-022, D-049).

### M13.1. Étage 1 : comprendre qui décide

Les autres parties de l'ancien M13.1 vivent ailleurs : « Mes élus » dans Mon espace (TE-01), « Interpeller ses élus » et « Participer à la démocratie existante » dans Décider (M13.1, suite).

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| CO-01 | Qui décide de ça ? | On choisit un sujet du quotidien et sa commune ; un encart « Qui décide ? Les élus concernés » ouvre le contenu avec les noms des élus, puis la chaîne de décision (commune, intercommunalité, département ou collectivité à statut particulier, région, État, Union européenne), leurs votes récents sur le sujet, comment les contacter, l'explication et « Et maintenant ? ». Sujets de départ (décision du 7 novembre 2026) : prix des carburants, logement et loyers, école, hôpital et santé, transports, impôts locaux, eau et énergie, salaires et retraites, services publics de proximité, environnement local. | Tôt | Fait, 12 sujets [EXEMPLE] (D-038, D-047, D-049) ; noms réels des élus avec le serveur de production. |
| CO-02 | Comprendre en 5 minutes | Parcours courts avec quiz : comment une loi est votée, le budget de l'État, le rôle de chaque élu, le 49.3, la motion de censure, la dissolution, le référendum, la révision de la Constitution, les élections ; progression gardée sur l'appareil seulement et effaçable. | Tôt | Fait, 16 parcours [EXEMPLE] (D-033, D-049). |
| CO-09 | Comprendre les élections | Fonctionnement des scrutins et rôle de chaque élu (ancien M13.7). | Tôt | Parcours sur la présidentielle, l'inscription et le vote, se présenter, faits (D-033, D-049). |
| CO-28 | Suis une loi qui me concerne | On choisit un sujet de sa vie ; à chaque étape d'un texte en cours, une explication et le moment pour agir (pétition, interpellation), avec une notification facultative. | Tôt (après l'alerte de Voir, VO-34) | Pôle Comprendre avec Voir ; législomètre (VO-20, M20) ; notifications (M31) ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |

### M6. Informer : fiches pratiques, décryptages, dossiers et glossaire

Le fil d'actualité de l'ancien M6 est dans « En direct » (MA-03), l'outil de publication de l'équipe dans M15 (S-70).

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| CO-03 | Fiches pratiques | Droits en manifestation, interpellation, garde à vue, nasse, filmer les forces de l'ordre (légal en France), contacts des legal teams, premiers secours, grève, procuration ; encarts « En bref », « Légal » (vert) et « Illégal » (rouge), toujours avec le mot ; disponibles hors ligne dès l'installation. | Phase 1 | Cinq fiches [EXEMPLE] faites (D-022, D-049) ; relecture d'un avocat. |
| CO-04 | Décryptage des fausses informations | Affirmation, verdict sur l'échelle « Exact », « Plutôt exact », « Trompeur », « Contredit par les faits », « Invérifiable », définie dans la page « Notre méthode », explication, objections et réponses, sources ; le verdict porte sur une affirmation, jamais sur une personne. | Phase 2 | Fait (D-038). |
| CO-05 | Dossiers thématiques | Séparation des pouvoirs, contrôle de la police et rôle de l'IGPN, libertés publiques, corruption, etc. ; un dossier qui porte un axe de « Voir » reste dans « Voir ». | Tôt | En partie fait (D-034, D-038). |
| CO-10 | Accès aux archives publiques | Fiche pratique : délais légaux de communication et demande de consultation anticipée (ancien M4). | Tôt | À faire. |
| CO-12 | Sécurité numérique des militants | Téléphone en manifestation, chiffrement, réseaux sociaux, protection de ses données (voir aussi M33). | Tôt | À faire. |
| CO-14 | Glossaire | Les mots difficiles de chaque contenu sont soulignés ; un geste ouvre une définition courte et « En savoir plus » ; glossaire complet avec recherche. | Tôt | Fait, 64 mots [EXEMPLE] (D-048, D-049). |
| CO-18 | Demander une vérification | Soumettre une rumeur ou une information douteuse à l'équipe de décryptage (CO-04). | Tôt | À faire, avec le back-office (M15). |
| CO-23 | Vacciner contre l'intox | Mini-parcours de pré-démystification sur les techniques de manipulation (fausse urgence, faux expert, chiffre sorti de son contexte, image recadrée, image fabriquée), avec des exercices « Repère le procédé ». | Tôt | Études sur l'inoculation à citer ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| CO-25 | Fiches en cartes verticales | Chaque fiche existe aussi en 5 à 8 cartes à faire glisser, avec les sources et « Et maintenant ? » à la fin, utilisables aussi dans « En direct » (M19) ; export en carrousel d'images portant le code de vérification (S-141), sans métadonnées. | Tôt | S-141 ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| CO-26 | « Cette fiche vous a aidé ? » | Trois réponses, « Clair », « Pas clair », « Un mot m'a bloqué », comptées ensemble, sans compte et sans texte libre, envoyées par lots et en différé. | Tôt | Aucun outil d'analyse ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| CO-29 | Vérifier une image qui circule | L'empreinte visuelle de l'image est calculée sur l'appareil et comparée à une liste signée des images déjà vérifiées par PPF ; une image inconnue renvoie vers « Demander une vérification » (CO-18) ; le verdict reste toujours humain. | Tôt (deuxième vague) | Licence de l'algorithme d'empreinte (PDQ) à vérifier ; CO-18 ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |

### M7. Histoire des luttes et valeurs (ancien « Éduquer »)

- L'histoire de toutes les luttes est racontée comme de l'histoire, y compris les épisodes violents, de façon factuelle et sourcée, avec l'analyse de ce qui a fonctionné ou échoué et de ce que ces luttes ont produit (décision de la propriétaire du 7 novembre 2026 : « C'est de l'histoire donc on le fait », D-038).
- Aucun appel ni aucune incitation à la violence : c'est une obligation légale, inchangée.
- Les guides de résistance civile pour aujourd'hui restent soumis à la vérification juridique de la limite de l'appel à la désobéissance civile.

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| CO-06 | Histoire des luttes et des révolutions | En France et dans le monde, avec frise, revendications obtenues, analyse de ce qui a fonctionné ou échoué, objections et réponses. | Tôt | Fait, trois récits [EXEMPLE] (D-038). |
| CO-07 | Résistance civile stratégique | Grève, grève générale, boycott, non-coopération, blocage économique, désobéissance civile, pétitions, mobilisation électorale, avec les facteurs de réussite documentés par la recherche. | Tôt | Avocat (limite de l'appel à la désobéissance civile). |
| CO-08 | Le droit de résistance à l'oppression en débat | Article 2 de la Déclaration de 1789, article 35 de celle de 1793, contradiction avec le droit pénal, débats historiques et philosophiques (volet historique). | Plus tard | Décidé (D-038). |
| CO-21 | Laïcité | Parcours et fiches : liberté de conscience, neutralité de l'État envers toutes les croyances, ce que la laïcité permet et ce qu'elle interdit, objections et réponses. | Plus tard | (ajout du chef d'équipe, 7 novembre 2026). |
| CO-22 | Comprendre le fascisme et la mouvance néonazie | Histoire, mécanismes, codes et symboles, décryptages de discours, avec la même méthode que les autres contenus ; liens vers la documentation des actes de haine (VO-33) et le soutien aux victimes (AG-14). | Plus tard | (ajout du chef d'équipe, 7 novembre 2026). |

### M8. Bibliothèque numérique

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| CO-11 | Bibliothèque numérique | Œuvres du domaine public et sous licence libre hébergées directement ; liens vers les éditeurs et les bibliothèques publiques pour les autres ; aucun contenu piraté. | Phase 5 (version complète) | À faire. |

### M22. Simulateurs et visualisations

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| CO-15 | Ce que cette loi change pour moi | Pour une loi, on choisit sa situation sur l'appareil (par exemple retraite, chômage, impôts) et l'on voit ses effets chiffrés, tirés de l'étude d'impact officielle ; calcul uniquement sur l'appareil, sans rien envoyer ; accessible aussi depuis le Parlement (M20) et « Qui décide de ça ? » (M13.1). | Tôt (promu de Plus tard) | Pôle Voir avec Comprendre ; étude d'impact officielle du texte ; aucun envoi de la situation ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| CO-16 | Où vont nos impôts | Budget de l'État et de sa commune en visualisation simple et interactive ; lien avec l'argent public local (VO-32). | Plus tard | Référentiel des territoires. |
| CO-17 | Et si on votait autrement ? | Résultats d'élections réelles recalculés avec le vote blanc reconnu, la proportionnelle ou le jugement majoritaire. | Plus tard, mais avant les élections | À faire. |

### M23. Apprendre pas à pas et en classe

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| CO-19 | Parcours « Je découvre » | Une micro-leçon par jour pendant sept jours pour les nouveaux venus, avec des notifications facultatives. | Plus tard (les leçons sont à écrire) | Notifications (M31). |
| CO-13 | Ressources pour les enseignants et leurs élèves | Contenus réutilisables en classe, strictement pluralistes ; précisé par le mandat : parcours adaptés aux élèves, fiches pédagogiques pour l'enseignant, usage en classe sans compte et sans aucune donnée d'élève. Format : une fiche pédagogique rattachée à chaque parcours (objectifs, séance de 55 minutes, prérequis, déroulé, quiz à main levée, débat guidé avec les objections des deux côtés, version imprimable sur le site sans JavaScript), le même format que le kit d'atelier (CO-24) ; lycée d'abord (enseignement moral et civique, de la seconde à la terminale), puis cycle 4, chaque fiche citant le programme officiel qu'elle sert ; neutralité de l'enseignement public rappelée (des faits et des débats, jamais une consigne de vote) ; licence commune des contenus de PPF (CC BY 4.0). | Tôt | Décidé le 8 novembre 2026 : format et niveaux par le chef d'équipe (délégation de la propriétaire), licence par la propriétaire ; en cours (M23, pôle Comprendre). |
| CO-24 | Kit d'atelier d'éducation populaire | Pour chaque parcours, une fiche d'animation imprimable et un mode animation hors ligne sans compte (projection, quiz à main levée, cartes à imprimer, version sans écran, déroulé de 45 minutes à 2 heures), pour les associations, syndicats, centres sociaux et médiathèques. | Tôt | Argument pour l'agrément « Jeunesse et éducation populaire » ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| CO-27 | Carnet de route privé | Ce que j'ai appris, l'étape suivante et « Reprendre », gardés sur l'appareil ; ni série, ni points, ni classement. | Tôt | Pôle Comprendre, avec Communauté pour Mon espace (M27) ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| CO-20 | Assistant citoyen IA | Mis de côté (décision de la propriétaire du 5 novembre 2026) : rien n'est codé ; les analyses restent au point d'attention n° 34 de l'inventaire. | Mis de côté | Hébergement de l'IA, paiement, avis d'un expert-comptable. |

## 7. Agir

« Agir » sert à se mobiliser, s'organiser, s'entraider, témoigner et communiquer, même sans Internet (vision, pilier 3). L'accueil de l'espace présente les rubriques prêtes, puis celles en préparation (D-042).

### M1. Carte et agenda des mobilisations

- Manifestations, blocages, rassemblements, grèves, assemblées générales : passés et à venir ; filtres par date, lieu, type, organisation et mots, calculés sur l'appareil ; export iCal ; consultable hors ligne ; accessible par l'entrée « Carte » de la barre du bas.
- Publication directe par l'équipe et les organisations vérifiées ; proposition par les autres comptes (règle transverse n° 12). L'auteur annule son événement sans nouvelle relecture ; un événement encore en attente dont la date est passée est refusé automatiquement, avec un motif fixe (D-039).
- Une manifestation non déclarée en préfecture est affichée comme telle ; une phrase prévient l'auteur que la mention est publique, sans l'empêcher d'envoyer (D-044, D-045).
- **Participation** (D-044) : le nombre de participants est public (comptage anonyme, M16) ; les organisations participantes sont affichées quand elles l'ont confirmé de leur propre clé (D-045) ; chacun peut participer sans s'afficher et compter quand même. Visibilité par défaut de la participation d'un citoyen auprès de son cercle et de celle d'un élu : décision en attente : Agir : questions ouvertes après la première version (contradiction avec le principe 10). En attendant, « J'y serai » et « Ça m'intéresse » restent sur l'appareil et s'effacent un jour après l'événement (D-040).

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| AG-01 | Carte et agenda des mobilisations | Liste par jour, détail avec « Annulé » et « Reporté », vue carte, agenda de chaque organisation, page `/agenda` du site et flux iCal. | Phase 1 | Agenda fait (D-040, D-043) ; vue carte en cours (D-046, D-080). |
| AG-02 | Proposer un événement | Formulaire guidé, événements proches signalés, publication directe ou relecture selon le compte, annulation par l'auteur. | Phase 1 | Fait (D-039, D-040, D-043) ; modifier un événement proposé, à faire. |
| AG-20 | Import des agendas existants | Une organisation relie une fois pour toutes son agenda en ligne (flux iCal ou RSS) : aucune double saisie. | Tôt | À faire. |
| AG-21 | Événements co-organisés | Un événement porté par plusieurs organisations n'apparaît qu'une fois, avec tous ses organisateurs ; une organisation n'est montrée comme organisatrice que si elle l'a confirmé de sa clé, sinon « annoncée, non confirmée » ; doublons détectés. | Tôt | Doublons signalés (D-040) ; confirmation en cours (D-044, D-045). |
| AG-62 | « Et après ? » | Les suites sourcées d'une mobilisation passée (réponses des élus, annonces, reculs, textes déposés), reliées aux revendications, aux appels et à la chronologie (VO-06, M4). | Tôt | Surtout éditorial ; pôle Voir pour la chronologie ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| AG-64 | Agenda fédéré et diffusion vers le fédivers | Agenda fédéré par ActivityPub, compatible avec Mobilizon, et diffusion à sens unique des publications éditoriales vers le fédivers (M34). | Plus tard (phase 3) | Directeur technique et pôle Agir ; sorties à inscrire dans la liste des sorties (EGRESS) ; modération des événements reçus ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |

### M3. Annuaire et espace des organisations

Associations, syndicats, collectifs et legal teams publient directement sur PPF, qui devient le point de rendez-vous unique au lieu d'une information éparpillée sur des dizaines de sites. Le fonctionnement technique des comptes d'organisation (S-78 à S-81) est décrit ici. Les partis, les candidats et leurs comités de soutien ou associations de financement déclarés sont exclus de l'annuaire ; les cas limites sont listés pour l'avocat (D-044).

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| AG-03 | Annuaire des organisations | Associations, avocats bénévoles, legal teams, street medics, collectifs, syndicats : fiche, carte, contacts, badge « vérifiée », « près de chez moi » calculé sur l'appareil. | Phase 1 | Première version faite (D-042) ; carte de l'annuaire et logos à faire. |
| AG-17 | Page de l'organisation | Présentation, contacts (propres à l'organisation, jamais ceux d'un bénévole sans son accord écrit), publications, calendrier, campagnes en cours. | Tôt | Fait (D-042, D-044). |
| AG-18 | Publications des organisations | Articles, communiqués, appels à mobilisation, fiches pratiques, signés de la clé de l'organisation, repris dans le fil, la recherche et Mon espace avec « publié par [organisation] » ; publication directe dès la vérification, sans période probatoire ; modération après publication et retrait possible à tout moment. | Tôt | À faire ; PPF hébergeur de ces contenus (D-044), relecture d'un avocat. |
| AG-19 | Calendrier de l'organisation | Chaque organisation gère son calendrier, qui alimente la carte et l'agenda communs. | Tôt | Fait (D-042). |
| AG-22 | Suivre une organisation | Abonnement à ses publications et à son calendrier ; liste gardée sur l'appareil, parce que suivre un syndicat peut révéler une appartenance syndicale ; publique au choix, après une mise en garde. | Tôt | Fait sur l'appareil (D-042) ; affichage public avec les profils (M28). |
| S-78 | Compte d'organisation | Compte partagé par plusieurs membres, chacun gardant son compte personnel ; l'organisation a sa propre clé de signature. | Tôt | Aujourd'hui, une organisation vérifiée est une clé nommée dans une fiche signée par l'équipe (D-039). |
| S-79 | Équipe de l'organisation | Rôles de responsable, éditeur et contributeur gérés par l'organisation ; invitation et retrait ; double authentification obligatoire. | Plus tard | À faire. |
| S-80 | Circuit de publication interne | Brouillon, relecture et validation par un responsable de l'organisation avant publication (même outil que S-70). | Plus tard | À faire. |
| S-81 | Demande de vérification | Nom, forme juridique, numéro au répertoire des associations ou SIRET, statuts, contact, acceptation de la charte ; suivi de la demande ; procédure publiée : pièces officielles, échange avec une personne responsable, charte signée, revérification chaque année. | Tôt | Procédure décidée (D-044) ; examen par l'équipe (EQ-07, back-office). |

### M12. S'organiser : guides, appels et matériel

N'importe quel compte peut lancer un appel, c'est-à-dire tout ce qu'un militant peut vouloir proposer aux autres. Les appels remplacent et élargissent l'ancienne fonctionnalité « Proposer une action ». Les guides d'organisation politique sont dans M13.4 ; les groupes locaux sont devenus des collectifs (M24).

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| AG-06 | Guides pour s'organiser | Créer une association loi 1901 ou un collectif, déclarer une manifestation en préfecture, organiser une AG, une grève, une campagne d'information, structurer un mouvement. | Tôt | À faire ; relecture d'un avocat. |
| AP-01 | Lancer un appel | Treize types (tableau ci-dessous), puis titre, texte, objectif chiffré facultatif, échéance, lieu facultatif, lien et champs du type ; modèle guidé par type. | Tôt | Fait (D-041). |
| AP-02 | Publication et modération | Publication immédiate sous le contrôle de l'IA, sauf les appels à témoins, toujours relus (règle transverse n° 12) ; appels en double signalés, comme pour les revendications. | Tôt | Fait (D-039, D-041, D-043). |
| AP-03 | Répondre à un appel | Bouton adapté au type (« J'y serai », « Je soutiens », « J'ai signé », « J'aide », « Je témoigne ») ; réponse gardée sur l'appareil ; nombre public de réponses par comptage anonyme ; la liste des personnes n'est jamais publique. | Tôt | Réponses sur l'appareil faites (D-041) ; compteur avec « une personne, une voix ». |
| AP-04 | Objectif et progression | Barre de progression et compte à rebours ; les participants sont prévenus quand l'objectif est atteint ou que l'appel change, d'abord par leur appareil lui-même, puis par notification (D-044). | Tôt | Compte à rebours fait (D-041) ; barre avec le comptage anonyme ; notifications (M31). |
| AP-05 | Mur des appels | Cards filtrables par type, territoire, échéance et popularité ; « près de chez moi » calculé sur l'appareil ; vue sur la carte pour les appels qui ont un lieu. | Tôt | Fait sans le tri par popularité (D-041). |
| AP-06 | Discussion de l'appel | L'auteur ouvre un salon ou relie un salon existant (la règle contre les doublons de salons s'applique) ; il en est l'administrateur. | Tôt | Décidé (D-044) ; à faire. |
| AP-07 | Faire grandir un appel | Le transformer en événement (AG-01), en revendication (DE-18) ou le proposer comme campagne (DE-04) ; kit de partage. | Tôt | Kit fait (D-041) ; transformations à brancher. |
| AP-08 | Appels à collecter des fonds | Réservés aux organisations vérifiées et à PPF elle-même ; renvoi vers leur propre page de collecte ; PPF ne détient ni ne fait transiter l'argent ; jamais pour un candidat ou un parti ; « Vous quittez PPF » avec une mise en garde contre les fausses cagnottes. | Tôt | Fait (D-039, D-041). |
| AP-09 | Clôture et bilan | L'auteur clôt son appel et publie un bilan sans relecture, sauf le bilan d'un appel à témoins, relu ; les appels clos restent consultables ; les réponses s'effacent de l'appareil 7 jours après la clôture ou l'échéance. | Tôt | Fait (D-041, D-043, D-045). |
| AP-10 | Champs obligatoires selon le type | L'appel ne peut pas être publié tant que les informations nécessaires manquent (tableau ci-dessous). | Tôt | Fait (D-041). |
| AP-11 | Créneaux de bénévolat | Un appel découpé en créneaux à places limitées (piquet, permanence, accueil), avec inscription et rappel sur l'appareil et un compteur par créneau ; les pseudonymes inscrits ne sont visibles que de l'organisateur, chiffrés, puis effacés. | Tôt (deuxième vague) | Appels (AP-01) ; collectifs (M24) ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| AG-61 | Affiche et tract à code QR | Un événement ou n'importe quel contenu devient une affiche A4 ou A3 générée sur l'appareil ; le code QR contient l'événement signé lui-même (ajout à l'agenda sans Internet, signature vérifiée) ou l'adresse stable sans suivi ; guide de l'affichage libre. | Tôt | Code de l'environnement, article L581-13, à vérifier ; prolonge le matériel militant (AG-15) et le partage (S-37, M29) ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| AG-63 | Mes proches à mobiliser | Liste privée, sur l'appareil, de quelques proches (surnom, sans import du carnet d'adresses), avec un contenu adapté, un rappel et une étape suivante. | Tôt | Opinions supposées d'autrui (RGPD, article 9) : jamais envoyées, mise en garde ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| AG-11 | Boycotts et alternatives | Fiches sourcées : motifs, faits établis, alternatives. | Plus tard | L'appel au boycott reste (D-044) ; avocat (dénigrement d'une entreprise). |
| AG-15 | Matériel militant | Tracts, affiches et visuels à télécharger et imprimer, personnalisables avec son territoire et la date d'une mobilisation. | Plus tard | À faire. |
| AG-25 | Trouver une salle | Lieux disponibles pour se réunir (bourses du travail, locaux associatifs, salles municipales), proposés par les organisations. | Plus tard | À faire. |

Champs obligatoires par type d'appel (AP-10) :

| Type d'appel | Champs obligatoires |
|---|---|
| Se réunir, débattre, s'organiser | Objet, date et heure, lieu ou lien de réunion en ligne, organisateur, accessibilité du lieu |
| Manifester | Date et heure, lieu de départ, parcours s'il est connu, organisateurs, déclaration en préfecture (faite, en cours ou non faite) |
| Soutenir une cause | Cause, action concrète demandée, échéance |
| Signer une pétition | Lien de la pétition, plateforme (officielle ou non), objectif de signatures, échéance |
| Interpeller des élus | Élus visés, demande précise, échéance (par exemple la date d'un vote) |
| Boycotter | Entreprise ou produit, motif appuyé sur des faits sourcés, alternatives, durée |
| Collecter des fonds | Bénéficiaire, objectif chiffré, usage prévu, lien de la page de collecte, échéance, engagement « ni candidat ni parti » |
| Chercher des bénévoles | Mission, compétences, nombre de personnes, lieu ou à distance, période |
| Faire circuler une information | Source (lien ou document) |
| Appel à témoins ou à témoignages | Date et plage horaire, lieu précis, description factuelle sans accuser nommément personne, éléments recherchés, destinataire des témoignages ; les témoignages arrivent par le canal sécurisé (AG-07) |
| Autre | Les champs communs |

### M24. Collectifs

Un collectif est un groupe ou un club créé par des citoyens, sans forme juridique obligatoire, de la portée nationale à celle d'un quartier ou d'un établissement ; il remplace les anciens groupes locaux. Il se distingue d'une organisation vérifiée (association ou syndicat déclaré, M3).

**Salon ou collectif** : un **salon** de discussion (M14) peut être créé par tout compte, avec la charte acceptée, un territoire et le refus des doublons (D-028) : c'est un espace de conversation. Un **collectif** est un objet distinct, avec une page, des rôles, des membres, un calendrier et des appels ; sa création est réservée aux comptes vérifiés (point d'attention n° 28). Depuis les trois niveaux de votants décidés le 7 novembre 2026 (D-112), « compte vérifié » désigne le compte validé en personne ; l'ancienne lecture « confirmé par l'ancienneté ou de confiance » n'a plus d'objet, ce qui rend la création plus stricte qu'en novembre (point à confirmer, annexe C). Un salon de collectif existant pourra être rattaché au collectif qui le reprend.

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-129 | Créer un collectif | Réservé aux comptes vérifiés ; assistant de création en quelques étapes ; aucune limite au nombre de collectifs par personne (décision de la propriétaire), la détection des manipulations (S-90) repérant les créations en série ; charte acceptée. | Tôt | Validation en personne (S-88) ; sens de « vérifié » à confirmer (annexe C). |
| S-130 | Paramètres et identité | Nom, description, image, portée, territoire, thèmes, type, langue, charte interne, nombre maximal de membres et affichage ou non de leur nombre ; visibilité publique, privée ou secrète ; adhésion ouverte, sur demande ou sur invitation. | Tôt | À faire. |
| S-131 | Rôles et statuts des membres | Fondateur, administrateur, modérateur, membre ; statuts personnalisés (référent, trésorier, porte-parole) ; permissions par rôle ; deux administrateurs au minimum recommandés. | Tôt | Rôles des salons (NIP-29) à faire. |
| S-132 | Rejoindre et quitter | Demande d'adhésion avec message facultatif, file des demandes, invitations par lien ou code QR, quitter à tout moment, exclusion motivée. | Tôt | À faire. |
| S-133 | Membres | PPF ne publie jamais la liste des membres ; elle est chiffrée sur le serveur, qui ne peut pas la lire (point d'attention n° 26) ; par défaut, l'appartenance n'est visible que des administrateurs ; chaque membre choisit ensuite de se montrer aux autres membres, puis sur son profil, après une mise en garde. | Tôt | À faire. |
| S-134 | Espaces de discussion du collectif | Salon général, canal d'annonces, commissions avec leurs propres salons ; chiffrés de bout en bout pour les collectifs privés et secrets. | Tôt | Salons faits (D-028, D-032). |
| S-135 | Modération interne | Les administrateurs modèrent selon la charte du collectif ; les contenus publics passent par la modération assistée par IA ; tout membre peut signaler à PPF ; PPF peut suspendre ou fermer un collectif qui enfreint la charte ou la loi ; pas de relecture préalable des nouveaux collectifs. | Tôt | Modération par IA (S-137). |
| S-136 | Transmission, archivage et dissolution | Transfert du rôle de fondateur, export des contenus, dissolution avec effacement des données ; si tous les administrateurs sont partis, les membres en désignent de nouveaux par un vote. | Plus tard | « Une personne, une voix » pour le vote. |
| AG-47 | Trouver et rejoindre un collectif | Recherche par nom, thème, type, portée et territoire ; « près de chez moi » calculé sur l'appareil ; filtres par mode d'adhésion ; les collectifs secrets n'apparaissent jamais. | Tôt | Recherche des salons faite (D-032). |
| AG-48 | Page du collectif | Présentation, portée, publications, calendrier, appels et tout type de contenu ; publique ou réservée aux membres selon la visibilité. | Tôt | Présentation détaillée : décision en attente : Maquettes de l'interface communautaire. |
| AG-49 | Calendrier du collectif | Événements internes ou publics ; les publics alimentent la carte et l'agenda communs ; « J'y serai » ; rappels. | Tôt | Notifications (M31). |
| AG-50 | Appels du collectif | Le collectif lance des appels en son nom, publics ou réservés aux membres. | Tôt | M12. |
| AG-51 | Décider ensemble | Votes, sondages et assemblées générales internes avec les outils de démocratie interne (DE-13) : vote anonyme, tirage au sort, mandats courts et révocables. | Plus tard | « Une personne, une voix » (D-110). |
| AG-52 | Alliances entre collectifs | Salon commun chiffré, événements et appels co-organisés, page de coordination (par exemple une coordination départementale). | Plus tard | À faire. |
| AG-53 | Du collectif à l'association | Accompagnement pour devenir une association déclarée (AG-06), puis demande de vérification (S-81). | Plus tard | À faire. |
| S-150 | Réunion de collectif clé en main | Dans un salon (M14) : ordre du jour, notes partagées, décisions votées, compte rendu signé et archivé. | Tôt (deuxième vague) | Pôle Communauté avec Agir (AG-51) ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |

### M5. Témoigner et conserver les preuves

- Photo, vidéo, audio, texte ; date et lieu déclarés ; métadonnées supprimées sur l'appareil avant l'envoi ; floutage des visages proposé ; empreinte SHA-256 de l'original, horodatée.
- Envoi par un canal sécurisé : en attente de publication, le témoignage est chiffré avec la clé publique de l'équipe de modération.
- Circuit : vérification (date, lieu, recoupement, authenticité), anonymisation (visages des passants et des victimes floutés sauf accord), publication dans la galerie et sur la carte (VO-08), avec un lien depuis l'appel à témoins s'il y en a un (point d'attention n° 19).
- Filmer et diffuser des images de forces de l'ordre en intervention est légal ; seule est interdite la révélation de l'identité des membres de certaines unités protégées (loi du 29 juillet 1881, article 39 sexies).

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| AG-07 | Témoigner | Envoi d'un témoignage par le canal sécurisé, relu avant publication ; réponse aux appels à témoins (AP-10). | Phase 2 | À faire ; « Je témoigne » désactivé en attendant (D-041). |
| AG-58 | Caméra protégée | Photos et vidéos prises dans PPF chiffrées hors de la galerie du téléphone, jamais copiées vers un service de sauvegarde ; plus tard, envoi continu par morceaux chiffrés vers les avocats partenaires. | Tôt (galerie chiffrée), Plus tard (envoi continu) | Stockage des vidéos sur le serveur : coût à présenter à la propriétaire avant l'envoi continu ; avocats partenaires (AG-08) ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| AG-54 | Provenance des témoignages | Signature de l'appareil et horodatage dès la capture, pour prouver qu'une image n'a pas été modifiée ; original chiffré pour les avocats partenaires ; compatibilité avec la norme C2PA à étudier. | Plus tard | Horodatage (D-111) ; (ajout du chef d'équipe, 7 novembre 2026). |
| AG-08 | Conservation des preuves | Original chiffré, accessible aux seuls avocats partenaires, pour des procédures. | Plus tard | Avocat. |
| AG-09 | Canal pour lanceurs d'alerte | Transmission anonyme de documents par un outil auto-hébergé accessible par Tor ; guide de la protection des lanceurs d'alerte (loi du 21 mars 2022). | Plus tard | Avocat ; décision en attente : Canal sécurisé pour les lanceurs d'alerte. |

### M25. Secours, sécurité et défense en manifestation

Retenu par la propriétaire le 4 novembre 2026 : protéger les manifestants par une coordination en temps réel des secours, de la sécurité et de la défense juridique. Règles du module :
- jamais de position ni de mouvement des unités des forces de l'ordre ; la carte montre les dangers pour les personnes et les passages pour se mettre à l'abri, avec l'heure du signalement ;
- la position d'un blessé et la gravité de sa blessure ne vont qu'aux street medics certifiés concernés, chiffrées, et sont effacées automatiquement après la clôture de l'alerte (délai à fixer, par exemple deux heures) ; elles ne permettent jamais de reconstituer qui était où ;
- PPF ne remplace jamais les secours publics : en cas d'urgence vitale, l'application propose d'abord d'appeler le 15 ou le 112 ; PPF met en relation, elle ne soigne pas ;
- les bénévoles informent, soignent et guident ; ils n'ont aucun pouvoir de police et ne s'interposent jamais physiquement ;
- tout le module est relu par un avocat avant le lancement et construit avec les organisations existantes (points d'attention n° 17, 18 et 24).

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| AG-13 | Mode manifestation | L'essentiel en un geste et hors ligne : droits, numéro de la legal team, premiers secours, que faire en cas d'interpellation ; une rubrique d'« Agir » pour se préparer et le bouton AIDE pour l'urgence (D-044). | Tôt | Rubrique annoncée dans l'accueil d'Agir (D-042). |
| AG-27 | Bouton « AIDE » | Ouvert à tous, avec ou sans compte : la personne blessée ou un proche envoie sa position aux street medics disponibles et indique la gravité (1 léger, 2 sérieux, 3 urgence vitale, qui propose d'abord le 15 ou le 112), puis voit « un medic arrive » et sa distance ; le même bouton propose « J'ai besoin d'aide pour sortir » (AG-44) et « Besoin d'un avocat » (AG-31) ; aucune limite d'alertes par téléphone ; il flotte au-dessus de la barre du bas (D-044). | Tôt | Quand et où il apparaît : décision en attente : Maquettes de l'interface communautaire (bouton AIDE) ; avocat. |
| AG-28 | Street medics certifiés | Certification en personne par code QR ou par un représentant certifié ; niveau de compétence de 1 (PSC1) à 5 (médecin urgentiste ou chirurgien), déclaré et confirmé, aucun diplôme stocké. | Tôt | Validation en personne (S-88). |
| AG-29 | Prise en charge coordonnée | Jusqu'à 5 medics répondent « J'y vais » (nombre réglable selon la gravité), puis l'alerte passe « prise en charge » ; medic « occupé » puis « disponible » ; les alertes non prises en charge remontent. | Tôt | AG-27. |
| AG-30 | Tableau de coordination des secours | Réservé aux medics et coordinateurs : medics disponibles ou occupés avec leur niveau, alertes par gravité, zones qui concentrent les alertes et zones sans medic. | Tôt | Vue carte (D-046). |
| AG-41 | Regroupement des alertes simultanées | Plusieurs alertes à environ 15 mètres et 2 minutes (réglables) n'en font qu'une, la plus grave ; les autres restent visibles en petits points rouges ; les medics sur place valident (nouvel appel) ou invalident avec un motif ; seul le motif « fausse alerte » compte pour repérer les abus, et les alertes d'un téléphone signalé partent vers la coordination avec la mention « à vérifier ». | Tôt | AG-27 ; identifiant anonyme temporaire, effacé après la manifestation. |
| AG-31 | Alerte « Besoin d'un avocat » | La personne interpellée ou un proche demande un avocat bénévole en indiquant la ville et si possible le lieu de garde à vue ; seule la ville est visible avant acceptation ; échanges chiffrés, effacés après la prise en charge ; la personne gardée à vue confirme son choix (Code de procédure pénale, article 63-3-1). | Tôt | Organisations d'avocats partenaires ; avocat. |
| AG-32 | Avocats bénévoles certifiés | Certifiés par une organisation vérifiée partenaire après vérification de l'inscription au barreau ; ville, disponibilité, domaines. | Tôt | M3. |
| AG-24 | Carte de la manifestation en direct | Parcours, horaires, points de rendez-vous et de dispersion, postes de secours fixes, zones dangereuses et sorties ; zone téléchargeable à l'avance ; ouverte par « Carte » quand une manifestation est en cours. | Tôt | Vue carte en cours (D-046, D-080). |
| AG-42 | Signaler une zone dangereuse | Gaz, charge, tirs de grenades ou de LBD, mouvement de foule, nasse en formation ; heure et nombre de confirmations ; disparaît s'il n'est pas confirmé (par exemple après 15 minutes) ; on signale le danger, jamais les unités. | Tôt | AG-24. |
| AG-43 | Sorties de la manifestation | État de chaque sortie (ouverte, fermée, contrôlée, inconnue), heure et auteur de la dernière confirmation ; « M'y guider » vers la sortie ouverte confirmée la plus proche. | Tôt | AG-24. |
| AG-44 | Aide pour sortir | Une personne vulnérable, ou quelqu'un qui la voit, demande de l'aide ; les bénévoles proches répondent « J'y vais » et la guident vers une sortie ouverte. | Tôt | AG-43, AG-45. |
| AG-45 | Équipe citoyenne de sécurité | Bénévoles certifiés et formés : veilleur de sortie, guide, coordinateur ; positions visibles de la seule coordination ; travail en binôme. | Plus tard | Formation avec les organisations expérimentées ; avocat. |
| AG-46 | Constat en direct aux sorties | Photo ou vidéo horodatée de la sortie, envoyée à la coordination et aux legal teams, conservée comme preuve ; l'historique des sorties produit un constat de nasse (début, fin, durée) qui alimente l'observatoire (VO-01). | Plus tard | AG-45, AG-39. |
| AG-33 | Points utiles autour de la manifestation | Points d'eau, toilettes, pharmacies, urgences, lieux refuges. | Tôt (avec la carte) | AG-24. |
| AG-34 | Accessibilité du parcours | Portions accessibles en fauteuil, cortège adapté, zones calmes. | Tôt (avec la carte) | AG-24. |
| AG-36 | Point de ralliement | Lieu convenu à l'avance pour retrouver ses proches ou un enfant perdu. | Tôt | AG-24. |
| AG-37 | Périmètres d'interdiction officiels | Zones interdites par un arrêté préfectoral publié, avec le lien vers l'arrêté. | Tôt | Arrêtés suivis par l'observatoire (VO-26). |
| AG-23 | Alerte à mes proches | En un geste, un message préparé (et sa position si on le veut) aux cercles choisis, par la messagerie chiffrée ou par SMS ; message « je suis bien rentré » à la fin. | Tôt | Cercles (M28), messagerie (M14). |
| AG-55 | Minuteur « sans nouvelles de moi » | À une heure fixée, sans « je vais bien » confirmé par un code, l'alerte préparée part vers les cercles choisis et, si on le veut, la legal team ; déposée chiffrée à l'avance sur leurs appareils, elle s'affiche d'elle-même, même si mon téléphone est saisi ; un code de détresse simule l'annulation. | Tôt | Le serveur n'apprend rien ; cercles (M28) avec le pôle Communauté ; messagerie (M14) ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| AG-56 | Préparer mon téléphone avant la manifestation | Bilan actif de PPF (verrouillage, phrase de récupération notée, notifications discrètes, carte téléchargée), guide des réglages du téléphone (biométrie désactivée, sauvegardes automatiques) et passage de PPF au code seul en un geste (M33). | Tôt | Exactitude juridique (refus de remettre son code : Cour de cassation, 7 novembre 2022, n° 21-83.146) ; avocat ; S-43, S-25 ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| AG-57 | Soutien aux personnes interpellées | « Je suis sans nouvelles de… » transmis chiffré à la seule legal team, accueil bénévole à la sortie, agenda sans nom des comparutions de manifestants. | Tôt | Données d'infractions (RGPD, article 10) : chiffrement de bout en bout, effacement rapide ; avocat ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| AG-59 | Kit contre le harcèlement et la divulgation d'informations personnelles | Preuves horodatées (D-111), modèles de signalement (DSA, article 16 ; PHAROS ; procureur), contacts d'associations et soutien du cercle. | Tôt (deuxième vague) | Preuves chiffrées sur l'appareil ; cercles (M28) ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| AG-35 | Retrouver son cortège | Position des cortèges annoncée par leurs organisateurs. | Plus tard | AG-24. |
| AG-38 | Transports et retour | Stations fermées, lignes perturbées, covoiturages de retour (AG-10). | Plus tard | Sources à vérifier. |
| AG-39 | Relais des alertes sans réseau | Bouton AIDE, sorties et messages de coordination relayés par Bluetooth quand le réseau est saturé. | Phase 4 (avec la messagerie hors ligne) | AG-16. |
| AG-65 | Relais radio longue portée | Alertes AIDE et coordination relayées par radio longue portée (LoRa, type Meshtastic) quand le réseau est coupé. | Plus tard (phase 4) | Achat de radios : accord de la propriétaire ; position diffusée par défaut à neutraliser ; AG-39 ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| AG-40 | Observateurs et legal teams | Leur position, visible de la seule coordination. | Plus tard | AG-45. |
| AG-26 | Permanences juridiques | Rendez-vous avec les avocats et juristes bénévoles des organisations partenaires, hors urgence. | Plus tard | Organisations partenaires. |
| AG-14 | Soutien aux victimes | Orientation vers les associations d'aide aux victimes, l'accompagnement juridique et le soutien psychologique ; fiche « après une blessure en manifestation » (certificat médical, plainte, saisine de l'IGPN ou du Défenseur des droits) ; aussi pour les victimes d'actes de haine (VO-33). | Tôt | À faire ; relecture d'un avocat. |

## 8. Décider

« Décider » est l'aboutissement du chemin et le cœur de la mission : transformer ses difficultés en revendications collectives, faire campagne pour de nouveaux droits démocratiques, obliger les élus et les candidats à s'engager, s'organiser politiquement et se présenter, décider ensemble dès maintenant (vision, pilier 4). Ce chapitre regroupe l'ancien module **M13 « Se politiser : reprendre le pouvoir »** et ses étages, M10 et M11. Tout vote passe par la méthode « une personne, une voix » (M16). L'accueil de l'espace présente le Pacte en cours, les pétitions ouvertes et les rubriques à venir (D-061).

### M11. Mur des revendications (étage 3 : revendiquer)

- Les citoyens proposent ce qui doit changer, votent pour ou contre, débattent ; les revendications prioritaires alimentent les cahiers de doléances, le Programme du peuple, le choix des campagnes et les propositions de mesures du Pacte.
- **Vues et filtres** (décision de la propriétaire du 6 novembre 2026, D-029) : une vue est un regard enregistré sur le mur (un thème, les revendications des comptes suivis, les siennes, les nationales) ; chacun compose ses vues, les nomme, les ordonne, les retire, et elles restent sur l'appareil. Un filtre affine la vue en cours (thème, territoire, statut, auteurs, mots-clés), un tri l'ordonne (nouveauté, popularité, mises à jour récentes) ; un ensemble de filtres peut devenir une vue.
- **Vérifications automatiques** (demande de la propriétaire du 6 novembre 2026, D-030) : pendant la saisie puis après l'envoi, l'outil repère doublons et revendications proches, loi déjà en vigueur ou en discussion, revendication contraire, plusieurs idées dans une seule, données personnelles et ce qui enfreint la charte. Il conseille l'auteur et prépare la relecture ; il ne publie ni ne refuse jamais rien : la relecture humaine décide.

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| DE-01 | Mur des revendications | Cards lisibles d'un coup d'œil : la revendication, son thème et son territoire, les votes pour et contre par niveau, le baromètre (PR-07), le nombre de participants au débat, le statut (nouvelle, en débat, très soutenue, retenue dans le Programme du peuple, devenue campagne), le badge de consensus ; vote sur la card ; tri, filtres, vues ; regroupement par thème assisté par IA et validé par l'équipe ; synthèse publique. | Phase 1 | Mur, vues et filtres faits (D-029) ; vote avec « une personne, une voix » (en cours, D-110, D-112), éventuellement limité aux revendications « en débat » si le serveur ne tient pas la charge (D-110) ; liste des thèmes : décision en attente : Revendications : vues, filtres et vérifications automatiques. |
| DE-18 | Proposer une revendication | Formulaire guidé (une revendication, une idée), revendications semblables proposées pendant la saisie pour soutenir l'existante, vérifications automatiques, relecture avant publication. | Phase 1 | Fait (D-030) ; analyse par IA et recherche dans les textes de loi : décisions en attente : Modération et assistant IA : hébergement du modèle, et Vérification « loi déjà en vigueur » : source des textes (brainstorming du cahier des charges). |
| DE-19 | Fusion des doublons | Détection des revendications proches par le sens ; fusion proposée à l'équipe et validée par un humain ; les votes sont réunis sans compter deux fois la même personne. | Tôt | Hébergement de l'IA ; « une personne, une voix ». |
| DE-20 | Espace de débat | Ouvert pour chaque revendication publiée, dans un ordre fixe : la card, puis la synthèse des arguments assistée par IA, validée et marquée comme telle, puis les commentaires ; chaque commentaire porte l'étiquette Pour, Contre ou Mitigé ; compteurs et filtres par étiquette ; règles du débat affichées. | Tôt | Hébergement de l'IA pour la synthèse. |
| DE-21 | Consensus au-delà des clivages | Répartition des votes selon le baromètre politique, la tranche d'âge, le sexe et la région ; badge « soutenue par tous les bords » quand une majorité de votes pour est atteinte à gauche, au centre et à droite, chacun avec assez de votes ; seuls les votes des comptes vérifiés comptent. | Plus tard | Questionnaire (M26), « une personne, une voix ». |

### M26. Questionnaire citoyen anonyme et baromètre politique

But : montrer qu'une revendication est partagée par des citoyens de tous les âges, de tous les territoires et de tous les bords. Aucun profil n'est stocké ; voter ne demande de remplir aucun champ (points d'attention n° 9 et n° 13). Une analyse d'impact sur la protection des données est faite avant le lancement.

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| PR-01 | Questionnaire au moment du vote | Après un vote, un court formulaire facultatif : tranche d'âge, sexe (femme, homme, non binaire, « Je préfère ne pas répondre »), département, position sur le baromètre ; la réponse « moins de 15 ans » clôt le questionnaire ; aucune question sur la religion, l'orientation sexuelle ou l'identité de genre. | Plus tard | « Une personne, une voix » ; analyse d'impact. |
| PR-02 | Consentement explicite | Écran d'explication la première fois : à quoi servent les réponses, comment elles sont protégées, rappel que voter ne les exige pas ; accord par un geste clair. | Plus tard | PR-01. |
| PR-03 | Réponses dans le bulletin anonyme | Les réponses partent dans le bulletin anonyme lui-même, jamais rattachées au compte ; la seule information sur l'auteur est le niveau du bulletin (D-112). | Plus tard | Jetons anonymes (D-110). |
| PR-04 | Statistiques protégées | Répartitions par grandes catégories, affichées seulement si chaque case compte assez de personnes (seuil privé), sinon « pas assez de votes » ; « non renseigné » pour les réponses vides ; mention « données déclaratives » toujours visible. | Plus tard | PR-01. |
| PR-05 | Afficher son bord politique | Désactivé par défaut, réservé aux personnes qui déclarent 15 ans ou plus : sa position sur le baromètre affichée sur son profil, retirable, jamais reliée à ses votes, après une mise en garde. | Plus tard | Profil social (S-123). |
| PR-06 | Mémorisation pendant la session | Les dernières réponses restent dans la mémoire de l'appareil pendant la session, jamais enregistrées durablement ni envoyées, effacées à la fermeture ou après une période d'inactivité (durée à fixer, par exemple 30 minutes). | Plus tard | PR-01. |
| PR-07 | Baromètre politique | Pour se situer : trois colonnes, gauche, centre, droite ; à gauche et à droite trois hauteurs (proche de l'extrême, « classique », proche du centre) ; le centre en une seule case ; « Je ne me reconnais pas dans ce clivage » et « Je préfère ne pas répondre ». Pour lire une card : dans chaque case, le pourcentage de votes pour et le nombre de votants ; jamais de moyenne. PPF n'écrit jamais « extrême gauche » ou « extrême droite » à propos d'une personne. | Plus tard | Libellés neutres des hauteurs à arrêter (point d'attention n° 9). |

### M13.3. Revendiquer ensemble : cahiers de doléances, Programme du peuple, loi citoyenne

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| DE-02 | Cahiers de doléances | Par territoire : regroupement des difficultés et des revendications locales, synthèse validée par l'équipe et publiée. | Phase 2 | Référentiel des territoires ; délibération citoyenne (DE-25). |
| DE-03 | Programme du peuple | Synthèse nationale des revendications prioritaires, votée par les citoyens, versionnée et mise à jour régulièrement. | Phase 2 | Décision en attente : Déroulé du Programme du peuple ; « une personne, une voix ». |
| DE-22 | Écrire la loi ensemble | Rédaction collaborative d'une proposition de loi citoyenne à partir d'une revendication très soutenue : texte, amendements citoyens, vote final, remise aux parlementaires. | Plus tard | DE-25. |

### M10. Pétitions et mobilisation massive

- Relais des pétitions officielles : Assemblée nationale, Sénat, Conseil économique, social et environnemental (CESE, gardé par la propriétaire, D-062), initiative citoyenne européenne, soutiens aux référendums d'initiative partagée quand une procédure est ouverte. PPF ne recueille jamais de signature : on signe toujours sur le site officiel, après « Vous quittez PPF », et PPF ne sait jamais qui a signé. La liste des soutiens d'un référendum d'initiative partagée est publique par la loi : c'est dit avant de soutenir.
- Chaque seuil est expliqué avec ce qu'il obtient et ce qu'il ne garantit pas. Droit vérifié le 7 novembre 2026 sur les sources officielles (D-059), à revérifier avant chaque mise à jour :
  - Assemblée nationale : à 100 000 signatures, la pétition est mise en ligne sur le site de l'Assemblée, sans examen obligatoire ; à 500 000 signataires domiciliés dans au moins 30 départements ou collectivités d'outre-mer, la Conférence des présidents peut inscrire un débat, jamais automatique et toujours sans vote ;
  - Sénat : 100 000 signatures en six mois obligent la Conférence des présidents à l'examiner, la suite restant libre ;
  - CESE : 150 000 signataires de 16 ans et plus en un an ; avis rendu sous six mois, sans obligation pour le Gouvernement ni le Parlement ;
  - référendum d'initiative partagée : initiative d'un cinquième des parlementaires (185), soutenue par un dixième des électeurs inscrits en neuf mois ; pas de référendum si chaque assemblée a examiné le texte dans les six mois ;
  - initiative citoyenne européenne : un million de signatures dans au moins sept États (seuil français : 58 320), sans obligation de légiférer.
- Objectif : rendre possibles des mobilisations de plusieurs millions de signatures.

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| DE-07 | Pétitions officielles | Pétitions ouvertes et closes, compteur et prochain seuil, kit de partage, suivi gardé sur l'appareil ; relevé automatique des compteurs par le serveur à partir des données ouvertes (l'application ne contacte jamais ces sites) ; objectifs de signatures par territoire. | Phase 2 | Fait (D-059) ; relevé automatique et objectifs par territoire décidés (D-062), à faire avec le référentiel des territoires ; alertes d'échéance (MA-04). |
| DE-26 | Pétitions locales | Demander à une collectivité d'organiser une consultation : un dixième des électeurs d'une commune, un vingtième dans les autres collectivités (Constitution, article 72-1 ; CGCT, article L1112-16), l'assemblée décidant librement ; guide et suivi. | Plus tard | Décision de la propriétaire (D-062) ; référentiel des territoires. |

### M13.5. Changer les règles : campagnes, Pacte démocratique et délibération citoyenne

- **Les citoyens fixent les priorités** (décision de la propriétaire du 7 novembre 2026, D-062) : la liste des mesures du Pacte et les modalités des campagnes de PPF (fréquence des votes, nombre de campagnes actives, seuil de participation, rôle de l'équipe) ne sont qu'une proposition de départ : « c'est le peuple qui doit la modifier et la valider via notre appli ». Elles sont affichées avec la mention « proposition de départ, pas encore votée », puis délibérées (DE-25).
- **Proposition de départ des mesures du Pacte** [EXEMPLE], soumise à la délibération : référendum d'initiative citoyenne (législatif, abrogatoire, constituant) ; reconnaissance du vote blanc comme suffrage exprimé, avec un nouveau scrutin tant qu'il est majoritaire ; débat suivi d'un vote obligatoire pour toute pétition dépassant 500 000 signatures ; référendum obligatoire pour toute révision de la Constitution ; conventions citoyennes tirées au sort dont les propositions sont soumises au Parlement ou au référendum ; transparence des archives des responsables publics. Chaque mesure doit être précise et vérifiable (texte à modifier, délai).
- **Proposition de départ des modalités des campagnes**, soumise elle aussi à la délibération : l'équipe vérifie seulement la faisabilité juridique et la conformité à la charte, sans filtrer selon ses préférences ; trois campagnes actives au maximum ; un vote de priorisation tous les six mois.

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| DE-25 | Délibération citoyenne | Proposer une mesure ou une modalité, l'amender, voter : une urne par proposition et par amendement, puis un vote final ; propositions publiées sous pseudonyme, relues comme les revendications ; le même module sert au Programme du peuple, aux cahiers de doléances et aux votations. | Tôt (ouverte au plus tard en janvier 2027 pour le Pacte) | Décision de la propriétaire (D-062) ; « une personne, une voix » (en cours, D-110). |
| DE-04 | Campagnes pour de nouveaux droits | Choisies par les citoyens ; exemples [EXEMPLE] tant que le vote n'a pas eu lieu (référendum d'initiative citoyenne, vote blanc reconnu, débat et vote obligatoires pour les grandes pétitions, conventions citoyennes dont les décisions s'imposent, transparence des archives présidentielles) ; fiche : droit actuel et changement demandé, texte exact à modifier, propositions de loi déjà déposées et leur sort, réponses sourcées aux objections, chemin pour obtenir la réforme. | Phase 2 | DE-25 ; fiche du vote blanc : décision en attente : Vote blanc : questions à trancher pour une proposition solide. |
| DE-05 | Soutenir une campagne | Compteur de soutiens par territoire et lien avec les pétitions officielles (M10). | Phase 2 | « Une personne, une voix ». |
| DE-06 | Baromètre des élus | Par campagne : qui s'est engagé, qui a voté pour, contre, s'est abstenu ou n'a pas répondu ; même présentation pour tous. | Phase 2 | Votes (M20). |
| DE-08 | Pacte démocratique | 5 à 10 mesures précises et vérifiables, choisies par les citoyens ; même texte (avec son empreinte), même jour et même délai pour tous les candidats d'une élection, sans exception ; envoi par l'équipe pour l'instant (D-062) ; tableau public par candidat et par mesure (signé, refusé, en attente, sans réponse), en ordre alphabétique ; une réponse reçue après la date limite n'est pas comptée mais reste visible sous le tableau avec sa date (D-062) ; réponses archivées et horodatées ; suivi de ce que chaque élu fait ensuite ; première échéance : présidentielle d'avril 2027 (diffusion au début de 2027), puis les législatives. | Phase 1 (priorité du calendrier) | Première version faite (D-060, D-062) ; horodatage (D-111) ; mesures à délibérer (DE-25). |
| DE-23 | Réponses des élus | Un élu certifié répond publiquement aux interpellations et au Pacte et signe sa réponse ; tous les élus ont exactement les mêmes droits et le même affichage. | Plus tard | Certification des élus (M16) ; avis juridique. |
| DE-34 | Carte des opinions | Prolonge la délibération (DE-25), sur le modèle de Pol.is : affirmations courtes, « D'accord », « Pas d'accord », « Je passe », groupes d'opinion et mise en avant de ce qui rassemble. | Plus tard, conception à étudier | Incompatible en l'état avec l'urne anonyme, qui ne relie pas les votes d'une même personne dans une conversation : à étudier avec une analyse d'impact (annexe C) ; (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| DE-35 | Jury citoyen du suivi du Pacte | Un jury tiré au sort suit les engagements du Pacte (tenu, en cours, rompu), par un vote anonyme aux motifs publics. | Plus tard (après l'élection) | Avocat (méthode M17, droit de réponse) ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| DE-37 | Collectivités partenaires | Une collectivité signe une charte et répond chaque trimestre, de façon motivée, aux revendications locales les plus soutenues. | Plus tard | Avocat (communication en période électorale) ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |

### M13.1 (suite). Interpeller ses élus et participer à la démocratie existante

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| DE-09 | Interpeller ses élus | Modèles de courrier à personnaliser ; suivi public des réponses reçues ou non, sans données personnelles au-delà du mandat public ; accessible depuis le législomètre et « Qui décide de ça ? ». | Phase 2 | Annuaire des élus (D-047) ; réponses signées (DE-23). |
| DE-10 | Participer à la démocratie existante | S'inscrire sur les listes électorales, voter par procuration, devenir assesseur, budgets participatifs, consultations et enquêtes publiques, conseils de quartier, pétitions (M10). | Tôt | Parcours « S'inscrire et voter » et fiche « Voter par procuration » faits (D-049). |
| DE-27 | Consultations publiques officielles | Consultations en cours des ministères, de la Commission nationale du débat public, enquêtes publiques locales et consultations des collectivités : liste, dates limites, guide pour y répondre, rappel gardé sur l'appareil. | Tôt | Sources officielles à vérifier ; référentiel des territoires ; (ajout du chef d'équipe, 7 novembre 2026). |
| DE-31 | Demander un document administratif | Demande rédigée selon le code des relations entre le public et l'administration (CRPA) et envoyée par le citoyen lui-même ; délais comptés sur l'appareil (silence d'un mois vaut refus, article R311-13 ; saisine de la CADA dans les deux mois, article R343-1), saisine préparée ; réponse publiable au choix du citoyen (M4) ; lien vers Ma Dada (madada.fr) après « Vous quittez PPF ». | Tôt | Données de tiers masquées avant publication ; articles à vérifier sur Légifrance ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| DE-32 | Observer son conseil municipal | Dates des séances, guide pour y assister et filmer (CGCT, article L2121-18), délibérations mises en ligne (article L2121-25), comptes rendus citoyens relus (M21). | Tôt (guide), Plus tard (relevé des délibérations) | Pôle Décider avec Comprendre ; droit à l'image du public ; méthode M17 pour les comptes rendus ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |

### M13.4. S'organiser politiquement

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| DE-11 | S'engager en politique | Guides pas à pas juridiquement vérifiés : créer un collectif, une association politique, une liste citoyenne aux élections locales, un parti (statuts, déclaration, mandataire financier ou association de financement agréée par la CNCCFP, plafonds de dons, obligations comptables). | Phase 2 | Avocat ; montants à revérifier avant tout guide. |
| DE-12 | Kit du candidat citoyen | Législatives en priorité : éligibilité, choix du remplaçant, déclaration en préfecture (formulaire officiel, pièces, dépôt en personne), mandataire et compte de campagne, plafond de dépenses, seuil de remboursement (5 % des suffrages exprimés), seuil d'accès au second tour (12,5 % des inscrits), budget type, calendrier, mode « dissolution » (scrutin 20 à 40 jours après la dissolution), carte des circonscriptions avec les résultats passés ; identique pour tous. | Phase 2 (prêt avant une éventuelle dissolution) | Parcours « Se présenter à une élection » fait (D-049). |
| DE-13 | Démocratie interne des groupes | Votes, AG en ligne, tirage au sort, mandats courts et révocables, rotation des responsabilités (utilisés par les collectifs, AG-51). | Phase 5 (outils avancés) | « Une personne, une voix ». |

### M13.6. Décider nous-mêmes, dès maintenant

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| DE-14 | Votations citoyennes | Grandes questions soumises au vote, sans valeur légale, pour devenir un fait politique par leur ampleur ; résultats par niveau de votant ; liste anonyme des bulletins publiée à la clôture pour un recomptage par tous. | Phase 3 | « Une personne, une voix » (conception approuvée, D-110, D-112). |
| DE-15 | Assemblées citoyennes tirées au sort | Tirage au sort parmi les comptes vérifiés volontaires, avec un tirage vérifiable par tous ; information contradictoire ; rapport public. | Phase 3 | Tirage vérifiable parmi les comptes vérifiés (ajout du chef d'équipe, 7 novembre 2026). |
| DE-24 | Budget participatif de PPF | Les comptes vérifiés votent l'usage d'une partie des dons reçus par l'association. | Plus tard (il faut d'abord des dons) | Trésorerie (PP-06). |
| DE-36 | Budget du peuple | Répartir 100 € entre les grandes missions de l'État par tranches fermées ; répartition médiane publiée et portée aux commissions des finances ; aussi à l'échelle d'une commune. | Plus tard (automne 2027) | À faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |

### M13.7. Élections

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| DE-28 | Calendrier électoral personnel | Dates des scrutins, date limite d'inscription sur les listes électorales, procuration, lien vers la vérification officielle de son inscription, rappels gardés sur l'appareil (TE-09). | Tôt (présidentielle d'avril 2027) | Dates prises au Journal officiel (décret de convocation) ; (ajout du chef d'équipe, 7 novembre 2026). |
| DE-16 | Comparateur présidentielle 2027 | Programmes et votes passés des candidats et des partis, présentés de façon strictement identique pour tous, sans appel à voter. | Phase 2 (avant avril 2027) | Votes (M20). |
| DE-30 | Questions citoyennes aux candidats 2027 | Questions proposées et votées (une urne par question) ; les dix plus soutenues sont envoyées le même jour, avec le même délai, à tous les candidats ; réponses côte à côte dans le comparateur (DE-16) ; absence de réponse affichée. | Tôt (ouverture en janvier 2027) | Traitement identique (cahier des charges, 9.2) ; « une personne, une voix » ; avocat ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| DE-33 | Boussole 2027 | Sur le modèle du Wahl-O-Mat : thèses choisies par délibération (DE-25), réponses des candidats eux-mêmes avec leur justification, proximité calculée sur l'appareil, profil jamais envoyé ; même calcul avec les votes de ses députés. | Plus tard (après le comparateur et les questions citoyennes, si le calendrier le permet) | Neutralité des thèses : avocat ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| DE-17 | Désignation citoyenne d'un candidat | Module isolé ; une personne, une voix, ce qui impose une vérification d'identité limitée à ce module, avec des données supprimées après le vote ; cadre légal (droit électoral, financement des campagnes) validé par un avocat avant tout développement. | Phase 6 | Avocat ; décision en attente : Désignation citoyenne d'un candidat (phase 6) et neutralité. |

« Comprendre les élections » est dans « Comprendre » (CO-09) et « Ma circonscription » dans Mon espace (TE-04). Le comparateur (DE-16) affiche aussi les parrainages de la présidentielle (VO-37, décrits dans « Voir ») et les réponses aux questions citoyennes (DE-30) (brainstorming du 8 novembre 2026).

## 9. Mon espace

### M27. Mon espace

Tableau de bord personnel, ouvert par l'avatar de la barre du haut et composé par l'utilisateur (ajout du chef d'équipe, 7 novembre 2026 : Mon espace devient un module à part entière). Tout ce qui est personnel (territoire, élus, suivis, votes, rappels) est calculé et gardé sur l'appareil ; la sauvegarde entre appareils est facultative et chiffrée avec la clé de l'utilisateur. On y trouve aussi son compte et sa phrase de récupération (M16), la conservation de ses messages (M14), ses échanges avec l'équipe (S-105) et la version installée de l'application (D-061).

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-20 | Mon territoire | Code postal ou commune gardés sur l'appareil seulement, désactivé par défaut, commun à l'annuaire, à l'agenda, aux appels et aux revendications (D-044) ; modifiable et effaçable (« Oublier ma commune »). | Tôt | Commune de « Mes élus » faite (D-047). |
| TE-01 | Mes élus | Tous ses élus, de la commune au Parlement européen, d'après l'annuaire officiel des élus, avec leurs votes et leurs engagements ; sans serveur, liens vers les annuaires officiels. | Tôt | Fait (D-047) ; votes avec M20 ; parrainages de ses élus (VO-37) et alerte « mes élus ont voté » (VO-34) à ajouter (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| TE-02 | Près de chez moi | Mobilisations, collectifs, organisations proches, calculés sur l'appareil. | Tôt | En partie (D-040, D-042). |
| TE-03 | Ce que veut mon territoire | Revendications et cahier de doléances locaux. | Plus tard | DE-02. |
| TE-04 | Ma circonscription | Résultats des élections passées, députés successifs, candidats déclarés. | Tôt (avant une dissolution) | Circonscriptions de l'annuaire (D-047). |
| TE-05 | Tableau de bord personnalisable | Blocs ajoutés, déplacés ou retirés : mon territoire, pétitions et chiffres suivis, contenus enregistrés, campagnes, organisations suivies, appels auxquels j'ai répondu, prochaines mobilisations, mes échanges avec l'équipe ; disposition par défaut au premier usage. | Tôt | À faire. |
| TE-06 | Mes votes et mes suivis | Tous ses votes et soutiens, tenus par l'appareil au moment du vote et jamais par le serveur, avec l'évolution des résultats et des débats ; ses suivis de chiffres, fiches, textes et pétitions ; notifications facultatives ; verrouillage proposé à l'activation. | Tôt | Suivis faits (D-034, D-059) ; votes avec « une personne, une voix ». |
| TE-07 | Partager avec mes cercles | Partager un vote, un appel, une revendication ou un contenu avec des cercles choisis, toujours volontairement et au cas par cas ; rendre publics ses votes et suivis après une mise en garde. | Plus tard | Cercles (M28), messagerie (M14). |
| TE-08 | Mon niveau de votant | Son niveau (non vérifié, vérifié, élu), la semaine à partir de laquelle il compte, comment se faire vérifier, et le résultat de l'autotest du vote anonyme sur l'appareil. | Tôt | D-110, D-112 ; (ajout du chef d'équipe, 7 novembre 2026). |
| TE-09 | Mes rappels | Tous les rappels programmés sur l'appareil au même endroit (événements, échéances de pétitions et de consultations, scrutins), modifiables et effaçables. | Tôt | S-98 ; (ajout du chef d'équipe, 7 novembre 2026). |
| TE-10 | Ma semaine | Résumé hebdomadaire calculé sur l'appareil (suivis, votes de mes élus, mobilisations proches, échéances, salons), en une seule notification à l'heure choisie, terminé par « C'est tout pour cette semaine » ; résumé quotidien en option ; désactivé par défaut et proposé une fois. | Tôt | Notifications (M31) ; alerte VO-34 ; pôle Communauté ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| S-112 | Sauvegarde et synchronisation chiffrées | Facultatif : Mon espace (tableau de bord, suivis, votes, jetons non utilisés, marque-pages, réglages) chiffré de bout en bout avec la clé de l'utilisateur et retrouvé sur ses appareils ; le serveur ne garde qu'un bloc illisible ; désactivé par défaut. | Plus tard | Point d'attention n° 25. |

## 10. Fil social

### M18. Fil social

- **L'écran par défaut de l'application est un fil**, sur le modèle d'un réseau social : le premier réseau social de la vie politique (décision de la propriétaire du 6 novembre 2026).
- Contenu du fil : publications des comptes, organisations et collectifs que l'on suit (S-120, AG-22) ; contenus suggérés ; publications éditoriales de PPF (MA-03) ; mobilisations, revendications et campagnes du moment.
- Actions : publier (compte requis : texte, puis image sans métadonnées, lien), partager (S-37), aimer, commenter (commentaire simple ; l'étiquette Pour, Contre ou Mitigé est réservée aux débats, DE-20), signaler (S-38), suivre (S-120).
- Navigation : barre du bas fixe sur téléphone à cinq entrées (Fil, En direct, Carte, Messages, Revendications) ; « Mon espace » par l'avatar de la barre du haut ; les piliers Voir, Comprendre, Agir et Décider dans le menu (S-05).
- Vues du fil : des onglets sous la barre du haut (Tout, Comptes PPF, Revendications, Mes publications, puis Mon cercle, Collectifs, Suivis, Près de moi avec les relations et les collectifs) ; on passe de l'une à l'autre en glissant le doigt de droite à gauche. La vue « Revendications » ne montre que les cards du mur des revendications (M11).
- Règles : la liste des comptes suivis est privée par défaut et reste sur l'appareil, qui assemble lui-même le fil ; les réactions ne constituent jamais par défaut une liste publique « qui a aimé quoi » ; les suggestions se calculent sans profil individuel côté serveur, à partir des choix explicites de l'utilisateur et de tendances agrégées, et un fil chronologique sans profilage reste toujours disponible (DSA, article 27) ; toute publication est soumise à la charte, à la modération assistée par IA après publication et au signalement par tous.
- État : première version faite (D-023, D-026) : fil chronologique, publication de notes courtes, vues au doigt, suivre des comptes avec une liste gardée sur l'appareil. Comptage anonyme des réactions et calcul des suggestions : décision en attente : Fil social : l'application comme premier réseau social de la vie politique (modalités). Visibilité d'une publication limitée à un cercle : décision en attente : Maquettes de l'interface communautaire.

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-145 | Fil fini et explicable | « Vous êtes à jour » au lieu d'un défilement infini, « Pourquoi je vois ça ? » sous chaque contenu suggéré, accès direct aux réglages du fil ; par défaut, heures de silence la nuit et aucune pastille rouge de compteur ; bilan du temps passé, calculé sur l'appareil. | Tôt | DSA, articles 25 et 27 ; notifications (M31) ; pôle Communauté ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| S-152 | Notes de contexte communautaires | Notes de contexte ajoutées par la communauté sous un contenu, affichées quand elles sont jugées utiles au-delà des clivages, sur le modèle des Community Notes (M15). | Plus tard, à étudier | Analyse d'impact ; jetons anonymes (M16) ; pôle Communauté ; (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |

### M28. Relations et cercles

Le cercle d'un utilisateur regroupe ses amis et amis proches, sa famille et sa famille proche, et les organisations et collectifs dont il fait partie ; les personnes qu'il suit forment un lien à sens unique (définition de la propriétaire, 5 novembre 2026). Un lien entre deux personnes n'existe que si l'autre l'accepte. Les relations, statuts et cercles sont stockés sur les appareils et dans la sauvegarde chiffrée facultative, jamais en clair sur le serveur : PPF ne détient pas la carte du mouvement (point d'attention n° 26). Contrepartie assumée : pas de suggestions « personnes que vous pourriez connaître ».

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-116 | Inviter quelqu'un | Lien ou code QR d'invitation sans traceur, à usage unique ou limité, avec une durée de validité ; mise en relation après acceptation. | Tôt | Graphe social minimisé (point d'attention n° 26). |
| S-117 | Demandes de mise en relation | Envoyer une demande par pseudonyme, code QR ou depuis un collectif commun ; accepter, refuser, ignorer, bloquer ; par défaut, demandes reçues seulement des membres de ses collectifs et par code QR ou lien ; nombre limité pour les comptes nouveaux. | Tôt | S-116. |
| S-118 | Cercles et statuts | Ami, ami proche, famille, famille proche et cercles personnalisés ; les organisations et collectifs forment des cercles ; statuts privés par défaut, chacun pouvant montrer à une personne le statut qu'il lui donne. | Tôt | S-117. |
| S-119 | Permissions par cercle | Pour chaque cercle : voir son profil complet, son bord politique, ses partages, sa participation (« J'y serai »), lui écrire, l'inviter dans un collectif, recevoir ses alertes en manifestation ; réglages les plus protecteurs par défaut. | Tôt | S-118 ; participation : annexe C. |
| S-120 | Suivre | Lien à sens unique vers un compte, une organisation ou un collectif ; suivi libre ou sur acceptation, au choix de chacun ; liste privée par défaut, publique au choix. | Tôt | Liste gardée sur l'appareil faite (D-028, D-042). |
| S-121 | Mes relations | Liste de ses relations, cercles, comptes suivis et abonnés ; recherche ; modifier un statut ; retirer une relation sans notification. | Tôt | S-118. |
| S-122 | Bloquer et masquer | Bloquer un compte (plus de messages, de demandes ni de profil complet) ; masquer ses publications sans le bloquer ; signaler. | Tôt | À faire. |
| S-123 | Profil social | Ce que les autres voient selon leur cercle ; par défaut, rien au-delà du pseudonyme et de l'avatar ; aperçu « voir mon profil comme » un inconnu, un ami, la famille ; la ligne « Mes votes et soutiens : jamais visibles ». | Tôt | Présentation : décision en attente : Maquettes de l'interface communautaire. |

## 11. Messagerie

### M14. Messagerie chiffrée

- La messagerie fait partie des ambitions de la phase 1 (décision de la propriétaire du 6 novembre 2026) : accès rapide dans la barre du bas, même logique qu'une messagerie de réseau social ou que WhatsApp : conversations privées, de groupe, de collectif, de syndicat ou d'association.
- Trois vues : **Privé** (conversations entre deux personnes et petits groupes, chiffrées de bout en bout), **Collectifs** (salons publics ou réservés des associations, syndicats et collectifs), **Flux** (publications des médias et des élus suivis, présentées comme des conversations à sens unique, sans réponse privée).
- Protocole (décision de la propriétaire du 7 novembre 2026, D-027) : messages privés NIP-17, qui cachent l'expéditeur, le destinataire et l'heure ; salons NIP-29 ; notre serveur sert de relais ; chiffrement MLS pour les grands groupes plus tard, derrière une interface qui permet de changer de protocole sans toucher aux écrans. Aucune liste de conversations ni de contacts sur le serveur ; les salons publics sont lisibles par tous et modérés, les salons réservés sont chiffrés. L'IA ne lit jamais les échanges chiffrés.
- Conservation (D-028) : trois mois au minimum sur le serveur, durée réglable par chacun pour sa boîte (3, 6 ou 12 mois), avec une mise en garde avant d'allonger ; plafond d'un an ; l'appareil garde sa copie.
- Mode hors ligne : sans Internet, par Bluetooth de proche en proche, compatible avec les applications bitchat existantes, sans lien avec le serveur central (cahier des charges, 3.4).

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-82 | Salons de discussion | Un salon par groupe, appel, campagne ou organisation ; public ou privé ; création ouverte à tout compte avec la charte et un territoire ; doublons refusés (même nom et même territoire), le salon existant étant proposé ; mise en garde : le pseudonyme du créateur est relié publiquement au salon. | Tôt | Fait (D-028, D-032) ; rapprochement par le sens : décision en attente : Salons de collectifs : création ouverte, charte, doublons ; squat d'un nom de salon : décision en attente : Messagerie terminée : questions ouvertes. |
| S-83 | Messages privés | Chiffrés de bout en bout ; par défaut, personne ne peut écrire à quelqu'un qui ne l'a pas accepté : le premier message d'un inconnu arrive comme une demande, sans images ni liens affichés ; chacun peut ouvrir ses messages à tous ou les désactiver. | Tôt | Conversations faites (D-027, D-032) ; demandes à faire ; taille des groupes : décision en attente : Messagerie terminée : questions ouvertes. |
| S-125 | Fonctions de messagerie | Texte, messages vocaux, photos et vidéos sans métadonnées, fichiers, réponses, réactions, mentions, messages épinglés, sondages ; accusés de lecture et « en train d'écrire » désactivés par défaut ; plusieurs appareils. | Tôt | Texte fait (D-032) ; le reste à faire. |
| S-127 | Signaler un message chiffré | Un participant transmet lui-même à la modération une copie du message et de son contexte immédiat ; les conditions d'utilisation le prévoient. | Tôt | Back-office (M15). |
| S-128 | Vérification des contacts | Code QR scanné en face à face ou code de sécurité comparé ; alerte si les clés d'un contact changent. | Tôt | À faire. |
| S-147 | Salon d'accueil des nouveaux | Salon tenu par des hôtes bénévoles (AG-12), proposé une fois après l'inscription ; kit d'accueil par collectif (bienvenue, règles épinglées, référent, M24). | Tôt | Salons (S-82) ; pôle Communauté ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| S-149 | Messages relayés avec leur provenance | Au transfert, l'événement signé d'origine est gardé : « original vérifié, publié par X le … » ou « modifié depuis l'original » (M33). | Tôt (deuxième vague) | Prolonge S-141 ; pôle Communauté ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| S-124 | Discussions de cercle | Chaque cercle peut avoir sa discussion de groupe chiffrée, créée en un geste. | Plus tard | Cercles (M28). |
| S-84 | Messages éphémères | Effacement automatique après un délai choisi par le salon ou la conversation. | Plus tard | À faire. |
| S-85 | Réunions en visioconférence | Pour les AG en ligne et les réunions de groupe, avec un outil auto-hébergé. | Plus tard (à étudier) | Coût d'hébergement à présenter. |
| S-126 | Appels audio et vidéo chiffrés | Entre deux personnes et en petit groupe. | Plus tard (à étudier) | À faire. |
| AG-16 | Messagerie hors ligne | Bluetooth de proche en proche, chiffrée, compatible bitchat, sans lien avec le serveur ; relais des alertes de manifestation (AG-39). | Phase 4 | Au moins deux téléphones Android pour les essais. |

## 12. Modules transverses

### M29. Socle de l'application

Ce que toute application sérieuse doit savoir faire, quelle que soit sa mission, construit une fois et servant à tous les espaces.

**Installer, démarrer, se mettre à jour**

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-01 | Premier lancement | Trois écrans au plus, qu'on peut passer : ce qu'est PPF, « tout est lisible sans compte », une explication simple de la sécurité (ce qui est collecté ou non, y compris sur les votes ; Tor et VPN), proposition de renseigner son territoire. | Tôt | À faire. |
| S-02 | Toutes les plateformes | Android, ordinateur (Windows, macOS, Linux), iPhone, et site installable (PWA), qui sert de solution sur iPhone en attendant. | Tôt | Android et ordinateur en cours ; iOS compatible mais non compilé. |
| S-03 | Mises à jour | Par les magasins et, sur Android, par un fichier d'installation téléchargeable directement dont la signature est vérifiée (F-Droid à étudier) ; notes de version lisibles ; mise à jour obligatoire en cas de faille ; numéro de version croissant (D-031). | Tôt | Distribution hors magasins décidée (point d'attention n° 5). |
| S-04 | État du service | Message en cas de panne, de maintenance ou d'attaque, avec les adresses de secours. | Tôt | À faire. |

**Naviguer et lire**

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-05 | Navigation principale | Sur téléphone, barre du bas à cinq entrées (Fil par défaut, En direct, Carte, Messages, Revendications) ; barre du haut avec menu, marque, recherche, notifications et avatar de Mon espace ; menu en accordéon avec Voir, Comprendre, Agir, Décider et leurs accès rapides ; sur tablette, une colonne d'icônes ; sur ordinateur, une barre latérale permanente avec la mention « Plateforme citoyenne indépendante, sans lien avec l'État ni le gouvernement » ; pas de bouton flottant « Carte », bouton AIDE au-dessus de la barre quand il le faut (D-044) ; même organisation sur le site ; utilisable au clavier et au lecteur d'écran ; le retour du système ferme d'abord ce qui est ouvert (D-035). | Tôt | Première version faite (D-023, D-025, D-026, D-035, D-044) ; détails : décision en attente : Navigation et UX (brainstorming du cahier des charges). |
| S-06 | Liens directs | Un lien partagé ouvre le bon contenu dans l'application si elle est installée, sinon sur le site. | Tôt | Adresses stables du site (D-055). |
| S-07 | Enregistrer pour plus tard | Marque-pages gardés sur l'appareil (et dans la sauvegarde chiffrée si elle est activée). | Tôt | À faire. |
| S-08 | Historique | Historique de lecture et de recherche sur l'appareil seulement, effaçable en un geste. | Tôt | À faire. |
| S-31 | Lecteur d'articles | Mise en page lisible, sources cliquables, version imprimable. | Tôt | Fait (D-022). |
| S-32 | Lecteur vidéo | Vidéos hébergées sur PeerTube, avec sous-titres. | Plus tard | Serveur PeerTube. |
| S-33 | Lecteur audio | Podcasts et radio, lecture en arrière-plan. | Plus tard | À faire. |
| S-34 | Visionneuse | Images avec zoom, documents PDF. | Tôt | À faire. |
| S-35 | Carte | MapLibre et OpenStreetMap ; fond intégré à l'application pour le hors ligne, fond détaillé de la France entière à la même précision servi par notre serveur, aucune requête à un tiers ; couches activables ; position facultative, utilisée seulement sur l'appareil et jamais envoyée, sauf pour une alerte qu'on lance soi-même ; zones téléchargeables ; mention « © contributeurs OpenStreetMap ». | Tôt | En cours (D-046, D-080). |

**Se régler**

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-16 | Apparence | Thème clair, sombre ou automatique ; taille du texte ; réduction des animations ; identité visuelle des maquettes de la propriétaire (D-025). | Tôt | Thèmes faits (D-025). |
| S-17 | Langue | Français par défaut ; autres langues au fil des traductions. | Tôt | Textes externalisés. |
| S-19 | Économie de données | Images et vidéos chargées à la demande, téléchargements réservés au wifi, confirmation avant un gros téléchargement. | Tôt | En partie (D-047). |
| S-21 | Confidentialité | Écran qui montre ce qui est stocké sur l'appareil et ce qui est envoyé au serveur, avec un bouton « Tout effacer ». | Tôt | À faire. |
| S-151 | Mode appareil partagé | Pour un appareil commun (médiathèque, local syndical) : sans compte ni historique, tout est effacé à la fin de l'usage (M33). | Plus tard | Pôle Communauté ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |

**Hors ligne, recherche, interactions communes**

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-26 | Contenus hors ligne | Fiches pratiques, carte, agenda, contenus consultés ; choix des rubriques à télécharger ; espace occupé affiché et bouton pour le libérer. | Phase 1 | Fiches, parcours et glossaire intégrés (D-022, D-033, D-048) ; choix des rubriques à faire. |
| S-27 | Actions en attente | Ce qui est fait sans réseau (publication, vote, témoignage) part au retour de la connexion, avec l'indicateur « en attente d'envoi ». | Tôt | File de publication faite ; bulletins avec envoi différé (M16). |
| S-28 | Indicateur de fraîcheur | On voit toujours si l'on est connecté et de quand datent les données. | Tôt | À faire. |
| S-29 | Recherche unique | Dans la barre du haut, sur tous les contenus, avec des filtres par espace, type, date et territoire. | Tôt | Recherche de « Voir » faite (D-034) ; recherche unique à faire. |
| S-30 | Recherche hors ligne | Dans les contenus téléchargés. | Tôt | À faire. |
| S-36 | Envoi de fichiers | Photo, vidéo, audio : métadonnées supprimées sur l'appareil, floutage des visages proposé, empreinte horodatée. | Tôt | À faire. |
| S-37 | Partager | Lien, code QR, image de partage générée (chiffre ou citation avec sa source et le nom de PPF), « Copier avec la source », kits de partage. | Tôt | En partie (D-034, D-041, D-059). |
| S-38 | Signaler | Tout utilisateur signale en un geste un contenu, un commentaire ou un autre utilisateur, même quand l'IA n'a rien relevé ; le signalement rejoint la file de modération. | Phase 1 | Back-office (M15). |
| S-39 | Commenter | Débats structurés sur les revendications, les appels et les campagnes, avec l'étiquette Pour, Contre ou Mitigé ; commentaire simple dans le fil (point d'attention n° 1). | Tôt | DE-20, M18. |
| S-40 | Ajouter à son agenda | Export iCal de tout événement. | Tôt | Fait pour l'agenda (D-040). |

**Site web, aide et confort**

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-49 | Site lisible sans JavaScript | Pages construites sur le serveur (D-007), lisibles sur tout navigateur, même ancien. | Tôt | Pages `/agenda` et « Voir » (D-043, en cours D-055). |
| S-50 | Aperçus de partage | Titre, résumé et image affichés quand un lien est partagé, sans script tiers. | Tôt | À faire. |
| S-51 | Flux RSS | Par espace et par thème, pour suivre PPF sans application. | Tôt | Flux iCal de l'agenda fait (D-043). |
| S-52 | Référencement | Plan du site et informations structurées pour les moteurs de recherche. | Tôt | À faire. |
| S-94 | Aide et questions fréquentes | Page d'aide depuis la barre du haut, réponses courantes, petite visite guidée des espaces. | Tôt | À faire. |
| S-98 | Rappels personnels | « Me rappeler cet événement » ou « cette échéance », programmés uniquement sur l'appareil ; regroupés dans Mon espace (TE-09). | Tôt | À faire. |
| S-101 | Rapports de plantage volontaires | Après un plantage, proposer d'envoyer un rapport technique anonyme, seulement avec l'accord de l'utilisateur. | Tôt | À faire. |
| S-96 | Widget d'écran d'accueil | Petit encart sur l'écran du téléphone : prochaine mobilisation, chiffre clé, alerte. | Plus tard | À faire. |

### M16. Comptes, rôles et intégrité

- Compte créé sur l'appareil (paire de clés compatible Nostr), sans numéro de téléphone ni e-mail, récupérable par phrase secrète ; lecture sans compte (D-016 à D-018, décisions techniques prises sous l'autorisation générale de la propriétaire, à confirmer par elle).
- Sans pièce d'identité ni téléphone, aucune méthode ne garantit seule qu'un compte est une personne réelle : la protection repose sur des barrières combinées et sur la transparence des résultats (point d'attention n° 10 ; méthode approuvée par la propriétaire, D-044).

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-09 | Utilisation sans compte | Lecture, carte, fiches pratiques, recherche et bouton AIDE sans compte. | Phase 1 | Fait. |
| S-10 | Créer un compte | Paire de clés créée sur l'appareil et pseudonyme ; preuve de travail à l'inscription. | Phase 1 | Fait (D-016, D-017) ; preuve de travail avant l'ouverture publique. |
| S-11 | Phrase de récupération | Douze mots, en français par défaut, avec une vérification qu'ils ont été notés ; restauration sur un nouvel appareil ; clé exportable vers toute application Nostr ; se déconnecter efface les clés de l'appareil après un rappel de la phrase, et le verrouillage est proposé à la place pour l'usage quotidien. | Phase 1 | Fait (D-017). |
| S-12 | Plusieurs appareils | Transfert du compte d'un appareil à l'autre par code QR. | Tôt | À faire. |
| S-13 | Profil public minimal | Par défaut, pseudonyme et avatar généré ; photo, présentation et appartenances ajoutées au choix, après une mise en garde. | Tôt | À faire. |
| S-14 | Exporter et supprimer ses données | Export de toutes ses données dans un fichier lisible ; suppression du compte : les votes et soutiens restent comptés anonymement, les contenus publiés affichent « compte supprimé », et chacun peut en demander la suppression un par un (point d'attention n° 3). | Tôt | À faire. |
| S-15 | Comptes de l'équipe | Double authentification obligatoire (application dédiée ou clé de sécurité), sessions visibles et révocables. | Phase 1 | Back-office en cours (Directeur technique). |
| S-86 | Niveaux des votants | Trois niveaux (décision de la propriétaire du 7 novembre 2026, D-112) : compte non vérifié (inscription avec preuve de travail), compte vérifié (validation en personne), élu (certification en personne par l'équipe et mandat contrôlé dans l'annuaire officiel des élus) ; ils remplacent les niveaux « nouveau, confirmé, de confiance » de l'inventaire ; le niveau prend effet la semaine suivante, et le serveur ne garde que la semaine. | Tôt | Décidé (D-112) ; en cours (D-110). |
| S-88 | Validation en personne | Un validateur, bénévole désigné par une organisation vérifiée et agréé par PPF, ouvre une séance limitée (quota) ; la personne montre le code QR de son compte ; aucun lieu, aucun événement, aucune identité n'est noté ; le lien entre compte et séance est chiffré pour l'audit, afin d'annuler les validations d'un complice. | Tôt | Back-office (D-110) ; lecture de codes QR sans service Google. |
| S-89 | Barrières contre les robots | Preuve de travail auto-hébergée (dans l'application ; ALTCHA sur le site) et limites de débit ; jamais de reCAPTCHA. | Phase 1 | Limites de débit faites (D-039) ; preuve de travail avant l'ouverture publique. |
| S-90 | Détection des manipulations | Rafales de votes, comptes créés en série, comportements coordonnés, taux anormaux de validation, repérés sur des compteurs agrégés, sans adresse IP ni identité ; réponses : suspendre les montées de niveau, retarder une urne, annoter publiquement un résultat ; jamais d'effacement silencieux de bulletins. | Tôt | D-110. |
| S-91 | Résultats transparents | Le vote des citoyens : comptes vérifiés en premier, puis non vérifiés, puis le total des citoyens ; le vote des élus à part, jamais additionné ; à côté de chaque colonne, le nombre de jetons délivrés. | Tôt | D-112. |
| S-146 | Déclarer mon compte compromis | Une clé de secours, dérivée de la phrase de récupération, est créée avec le compte et absente du téléphone ; depuis un autre appareil, elle publie un avis signé « compte compromis depuis… », prévient contacts et salons (M14), retire l'ancienne clé des salons et passe à une nouvelle clé. | Tôt (conception immédiate : la clé de secours doit exister dès la création des comptes) | Pôle Communauté avec Décider (M16) ; proposition Nostr NIP-41 à étudier ; S-10, S-11 ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| DE-29 | « Mon bulletin a été compté » | Après la clôture, l'appareil vérifie seul que son bulletin figure dans la liste publiée avec le bon choix et rejoue le recomptage (M13.6) ; le reçu n'est jamais montrable à un tiers. | Tôt | « Une personne, une voix » (D-110, D-112) ; faible coût ; pôle Décider ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |

**« Une personne, une voix » : ce que garantit le vote anonyme** (méthode approuvée par la propriétaire, D-044 ; choix de la propriétaire du 7 novembre 2026, D-112 ; conception technique approuvée, D-110, détaillée dans `docs/integration/une-personne-une-voix.md`)

- **Impossible à relier** : le serveur ne peut pas relier un vote, un soutien ou un amendement voté à un compte, même s'il le voulait ; c'est une impossibilité mathématique, pas seulement une règle. Chaque question votable a une urne signée par la plateforme ; l'application obtient pour chaque urne un jeton anonyme, que le serveur signe sans le voir.
- **Tout pour tous, à l'avance** : l'application demande automatiquement un jeton pour chaque urne ouverte, qu'on veuille voter ou non ; le serveur ne peut rien déduire des demandes.
- **Envoi séparé et différé, pour tous** : le bulletin part sans compte, par une connexion distincte, après un délai aléatoire de quelques minutes ; c'est une protection commune du système de vote, appliquée automatiquement à tous et sans réglage, à la demande de la propriétaire (« systematiquement de maniere automatique sans laisser le choix a l'utilisateur pour ce cas precis »), comme le fait que le serveur ne relie jamais un vote à un compte. Hors ligne, le vote part au retour du réseau.
- **Changer d'avis** : on peut modifier ou retirer son vote jusqu'à la clôture.
- **Niveau figé** : le niveau qui compte pour une urne est celui de la semaine de son ouverture ; une vague de faux comptes la veille d'un vote ne touche que la colonne des comptes non vérifiés.
- **Recomptage par tous** : à la clôture, la liste anonyme des bulletins et les clés de l'urne sont publiées, pour que n'importe qui puisse recompter ; un journal public montre en continu le nombre de comptes par niveau et de jetons délivrés par urne.
- **Qui vote** : chaque personne, avec son compte ; un élu peut avoir aussi un compte de citoyen sans être compté deux fois dans un même total ; les organisations et les clés de l'équipe éditoriale ne votent pas.
- **Petits groupes** : aucun croisement (territoire, âge, bord politique) n'est affiché sous un seuil minimal de bulletins par case.
- **Usages** : votes des revendications (M11), délibération sur le Pacte et les campagnes (DE-25), votations (DE-14), compteurs de participation d'« Agir » (M1, AP-03), signalements chiffrés de « Voir » (VO-03), décisions internes des collectifs (AG-51).
- **Limite connue** : une personne qui passe à plusieurs stands avec plusieurs téléphones peut obtenir plusieurs comptes vérifiés ; parades : consigne aux validateurs, quotas par séance, détection des taux anormaux.

### M15. Modération et administration

L'administration est un espace réservé à l'équipe, sur le site web, depuis un ordinateur ou un téléphone, avec double authentification et journal d'audit. Aucun rôle ne peut voir les votes, les soutiens ou les abonnements d'un utilisateur. Le back-office est en cours (Directeur technique) ; les files de relecture existent déjà sur le serveur (D-030, D-043).

**Modération**

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-72 | Modération | File de modération ; trois sources de signalement : IA, communauté, organisations partenaires ; décision motivée et notifiée, recours ; écran de relecture des files existantes (revendications, mobilisations, appels) ; rapport de transparence publié par choix (DSA, article 15) ; signalement à PHAROS. | Phase 1 | Files du serveur faites (D-030, D-043) ; back-office en cours. |
| S-137 | Modération assistée par IA | Analyse de chaque contenu public juste après sa mise en ligne, sans retarder la publication : **alerte rouge** (contenu grave et manifestement illicite : appel à la violence, menaces, contenu pédocriminel ou terroriste, divulgation d'informations personnelles) masqué en attendant la décision humaine ; **alerte orange** (contenu limite) publié, équipe prévenue ; tout le reste publié sans notification. L'équipe confirme ou annule chaque masquage ; l'auteur est prévenu du motif et du recours à un outil automatisé et peut contester. | Phase 2 | Décision en attente : Modération et assistant IA : hébergement du modèle (brainstorming du cahier des charges). |
| S-66 | Sanctions graduées | Avertissement, restriction (lecture seule ou publications validées), suspension temporaire, bannissement ; motif obligatoire, notification. | Phase 1 | Back-office. |
| S-67 | Recours et levée des sanctions | Recours traité par une autre personne que celle qui a sanctionné ; levée d'une sanction avec motif. | Phase 1 | Back-office. |
| S-69 | Signaleurs de confiance | Organisations ou personnes dont les signalements sont traités en priorité. | Tôt | Back-office. |
| S-140 | Jurys citoyens de recours | Des contestations de modération examinées par un jury tiré au sort parmi les comptes vérifiés volontaires ; rôle exact du jury (avis ou décision) et cas concernés à étudier. | Plus tard (à étudier) | Avis d'avocat ; validation en personne (S-88) ; (ajout du chef d'équipe, 7 novembre 2026). |

**Équipe, groupes et permissions**

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-56 | Super-administrateur | Deux au minimum, chacun avec une clé de sécurité physique, et une procédure de secours écrite gardée hors ligne. | Phase 1 | Achat des clés : guide à prévoir (coût à présenter). |
| S-57 | Nommer les membres de l'équipe | Invitation par lien à usage unique et à durée limitée, double authentification à l'acceptation, retrait immédiat. | Phase 1 | Back-office. |
| S-58 | Groupes | Créer, renommer, supprimer des groupes (modération nationale ou régionale, rédaction, équipe juridique, vérification des organisations) et gérer leurs membres. | Phase 1 | Back-office. |
| S-59 | Permissions par groupe | Actions autorisées et périmètre (espace, type de contenu, thème, territoire), configurables sans code ; groupes de départ : modération, édition, administration. | Phase 1 | Back-office. |
| S-60 | Accès temporaires | Permission limitée dans le temps (renfort pendant une grande mobilisation), retirée automatiquement. | Tôt | Back-office. |
| S-61 | Validation à deux | Accord d'une deuxième personne pour les actions sensibles : nommer un administrateur, modifier des permissions, supprimer définitivement, bannir, notification nationale, mouvement de trésorerie. | Phase 1 | Back-office. |
| S-62 | Coupure d'urgence | Toutes les sessions et permissions d'un membre compromis coupées en un clic. | Phase 1 | Back-office. |
| S-63 | Journal d'audit | Trace de toutes les actions de l'équipe, consultable et filtrable, impossible à modifier ou à effacer. | Phase 1 | Back-office. |
| S-64 | Tableau de bord de l'équipe | Ce qui attend chaque groupe : contenus à relire, signalements, demandes de vérification, droits de réponse, recours, messages, réclamations, alertes. | Phase 1 | Back-office. |
| S-115 | Niveaux d'importance des notifications de l'équipe | De 1 à 5, qui fixent l'ordre, la couleur et le mode d'alerte ; niveau 5 (alerte immédiate et rappel tant qu'elle n'est pas prise en charge) : droit de réponse et alerte rouge de l'IA ; niveau 4 : alerte orange (décisions de la propriétaire) ; proposition pour le reste, réglable sans code : 5 pour une faille ou un compte de l'équipe compromis, 4 pour une réclamation sur les données ou un contenu manifestement illicite, 3 pour une contestation ou une demande de vérification, 2 pour un message, 1 pour une suggestion. | Phase 1 | Notifications (M31). |

**Utilisateurs, contenus et plateforme**

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-65 | Rechercher un utilisateur | Par pseudonyme ou identifiant public : contributions publiques, signalements le concernant, sanctions passées ; aucune identité réelle à consulter. | Phase 1 | Back-office. |
| S-68 | Supprimer un compte | À la demande de l'utilisateur ou après un bannissement, selon les règles de S-14. | Tôt | Back-office. |
| S-70 | Outil de publication (CMS) | Brouillon, relecture, validation, publication programmée, bibliothèque de médias ; aujourd'hui, fichiers Markdown signés par l'outil de signature (D-022). | Phase 1 | À faire. |
| S-71 | Gérer les contenus | Ajouter, modifier, dépublier, supprimer, restaurer depuis une corbeille, mettre en avant ; historique de toutes les versions. | Phase 1 | À faire. |
| S-73 | Pages et menus | Pages fixes (à propos, aide, charte), ordre des rubriques et accès rapides du menu. | Tôt | À faire. |
| S-74 | Envoi de notifications | Par l'équipe, ciblé par thème ou territoire, validation à deux pour les envois nationaux. | Tôt | Notifications (M31). |
| S-75 | Traductions | Gestion des textes de l'interface et des contenus traduits. | Plus tard | À faire. |
| S-76 | Mesure de fréquentation | Compteurs agrégés calculés sur le serveur (lectures par contenu et par jour, parcours d'ensemble entre les espaces, recherches sans résultat), sans cookie, sans adresse IP, sans identifiant. | Tôt | À faire. |
| S-77 | Réglages sans code | Contenus mis en avant, textes d'accueil et seuils modifiables par l'équipe. | Tôt | À faire. |
| S-113 | Tableau de bord statistique de l'équipe | Toutes les statistiques au même endroit (fréquentation, votes, appels, revendications, questionnaire, alertes, contact, trésorerie), avec filtres, croisements, courbes, cartes, export et alertes sur les évolutions inhabituelles ; uniquement agrégées et anonymes, avec des seuils minimaux d'affichage. | Plus tard | Point d'attention n° 23. |
| S-114 | Rapports d'analyse assistés par IA | Rapports périodiques rédigés par une IA à partir des seules statistiques agrégées, origine affichée, relus avant toute publication. | Plus tard | Hébergement de l'IA. |

**Outils de l'équipe propres à PPF**

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| EQ-01 | Gestion des indicateurs | Création d'indicateurs sans code ; validation des signalements chiffrés des citoyens. | Phase 1 | À faire. |
| EQ-02 | Import des données publiques | Assemblée nationale, Sénat, Parlement européen, HATVP, Journal officiel, résultats électoraux, Répertoire national des élus. | Tôt | Annuaire des élus fait (D-047) ; données du Parlement en cours (D-064). |
| EQ-03 | Double validation et contradictoire | Pour tout contenu de « Voir » qui met en cause une personne : deux validations et demande de réaction à la personne concernée avant publication. | Phase 2 | À faire. |
| EQ-04 | Suivi des droits de réponse | Délai de trois jours affiché, alerte avant échéance. | Tôt | À faire. |
| EQ-05 | Synthèse des revendications | Regroupement assisté par IA, validation, rédaction des synthèses et des cahiers de doléances. | Tôt | Hébergement de l'IA. |
| EQ-06 | Envoi du Pacte et suivi des réponses | Même texte et même délai pour tous les candidats, envoi par l'équipe, archivage horodaté des réponses. | Phase 1 | D-060, D-062, D-111. |
| EQ-07 | Vérification des organisations | Examen des demandes, contrôle de l'existence légale, attribution, suspension et retrait du badge, chaque décision inscrite au journal d'audit. | Tôt | Procédure décidée (D-044). |
| EQ-08 | Votes de priorisation | Organisation des votes sur les campagnes, archivage des résultats, selon les modalités choisies par les citoyens (DE-25). | Phase 2 | DE-25. |

**Intelligence artificielle** : au lancement, l'IA ne sert qu'à aider l'équipe et les auteurs : modération (S-137), vérifications des revendications (DE-18), doublons (DE-19, et rapprochement des salons, à étudier), synthèses (DE-20, EQ-05) et rapports (S-114). Elle conseille et alerte ; elle ne publie, ne refuse et ne sanctionne jamais seule ; elle ne lit jamais les échanges chiffrés ; son origine est toujours affichée ; ses critères reprennent la charte publique et son taux d'erreur est suivi pour repérer les biais. Hébergement du modèle : décision en attente : Modération et assistant IA : hébergement du modèle (brainstorming du cahier des charges). L'assistant citoyen (CO-20) et ses crédits mensuels et recharges (S-138, S-139) sont mis de côté : rien n'est codé à leur sujet.

### M30. Contact, réclamations et obligations légales

Comme dans les espaces sécurisés des banques ou des administrations, tous les échanges avec l'équipe ont lieu dans l'application ou sur le site, jamais par e-mail ; l'e-mail, facultatif, sert seulement à prévenir qu'un message attend, sans en reproduire le contenu. Vers l'extérieur, les réponses sont signées « l'équipe PPF », pour protéger les bénévoles du harcèlement (point d'attention n° 20).

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-102 | Ouvrir un ticket | Type obligatoire : bug, suggestion ou signalement ; informations techniques jointes avec l'accord de l'utilisateur ; un signalement de contenu ou de compte rejoint la modération, une faille la procédure dédiée (S-92). | Tôt | Back-office. |
| S-103 | Formulaire de contact | Catégorie d'expéditeur (citoyen, association ou collectif, syndicat, journaliste, élu, institution, entreprise, autre), déclarative sauf pour les comptes vérifiés ; objet et message. | Tôt | Back-office. |
| S-104 | Formulaire de réclamation | Distinct du contact ; motifs : contester une décision de modération, exercer ses droits sur ses données, demander un droit de réponse (VO-21), usage des fonds, comportement d'un membre de l'équipe, autre ; délai applicable affiché à l'envoi. | Tôt | Back-office. |
| S-105 | Mes échanges | Tous ses tickets, messages et réclamations, avec les réponses : dans Mon espace avec un compte ; sans compte, avec un numéro de dossier et un code secret, ou un lien unique. | Tôt | Back-office. |
| S-106 | Accusé de réception et suivi | Numéro de dossier immédiat ; statut reçu, en cours, réponse envoyée, en attente de votre réponse, clos ; notification ou e-mail sans contenu. | Tôt | Notifications (M31). |
| S-107 | Boîte de réception de l'équipe | Tous les échanges triés par type et par catégorie, filtrables, assignés à un groupe ou une personne, avec une priorité. | Tôt | Back-office. |
| S-108 | Répondre et tracer | Chaque réponse est rattachée au membre de l'équipe qui l'a écrite, datée et non modifiable ; notes internes ; réponses types. | Tôt | Back-office. |
| S-109 | Archives | Dossiers clos consultables par les seuls membres autorisés, chaque consultation inscrite au journal ; messages anodins supprimés 3 ans après la clôture ; réclamations gardées 5 ans puis anonymisées, jamais supprimées ; archive juridique motivée pour les dossiers sensibles, réexaminée chaque année. | Tôt | Point d'attention n° 20 (décidé). |
| S-110 | Traitement des réclamations | File séparée, délais affichés et alertes avant échéance (un mois pour les droits sur les données, trois jours pour un droit de réponse) ; contestation examinée par une autre personne que l'auteur de la décision ; bilan annuel publié (PP-01). | Tôt | Back-office. |
| S-111 | Statistiques du service | Nombre d'échanges, délais moyens de réponse et de résolution, par type et par catégorie (repris dans S-113). | Plus tard | S-113. |
| S-53 | Pages légales | Mentions légales, conditions d'utilisation, politique de confidentialité, charte de contenu, procédure de signalement et de recours ; modèles marqués « À FAIRE RELIRE PAR UN AVOCAT ». | Phase 0 | À faire. |

Obligations légales que ce module et M15 servent : exercice des droits sur les données (RGPD, réponse sous un mois) ; droit de réponse (insertion sous trois jours) ; exposé des motifs d'une décision de modération, avec la mention d'un outil automatisé le cas échéant, et traitement interne des réclamations par une autre personne (DSA, articles 17 et 20) ; rapport de transparence, publié par choix (DSA, article 15) ; signalement des contenus pédocriminels et terroristes (PHAROS) ; déclaration d'accessibilité (S-42) ; analyse d'impact avant le questionnaire citoyen (M26).

### M31. Notifications

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-22 | Notifications sans Google | Par UnifiedPush et un serveur ntfy auto-hébergé ; sur iPhone, simple signal de réveil, sans aucun contenu. | Tôt | À faire ; en attendant, les messages arrivent quand l'écran est ouvert. |
| S-23 | Abonnements | Choix par thème, par campagne, par territoire et par type (mobilisations, alertes, votes au Parlement, consultations). | Tôt | S-22. |
| S-24 | Centre de notifications | Liste des notifications reçues, depuis la barre du haut. | Tôt | S-22. |
| S-25 | Notifications discrètes | Aucun contenu sensible sur l'écran verrouillé ; option « silence total ». | Tôt | S-22. |

Règle (D-044) : pour ce qu'on suit sans que le serveur le sache (appels auxquels on a répondu, événements, contenus suivis), l'appareil vérifie lui-même à chaque synchronisation et affiche une notification locale ; les notifications poussées par sujet anonyme viennent ensuite, pour le temps réel. Les notifications de l'équipe ont leurs niveaux d'importance (S-115).

### M32. Accessibilité et inclusion

La plateforme est utilisable par tous, y compris avec un vieux téléphone (Android 8 et plus), une connexion faible ou un handicap (vision, valeur « Accessibilité »).

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-41 | Accessibilité WCAG 2.2 AA | Lecteurs d'écran, contrastes, navigation au clavier (y compris dans le menu en accordéon), textes alternatifs, transcriptions des vidéos et des podcasts, police très lisible (Atkinson Hyperlegible, D-025), mots avant les couleurs. | Phase 1 | Appliqué à chaque écran ; tests automatiques quand c'est possible. |
| S-42 | Déclaration d'accessibilité | Page publique du niveau atteint, avec un moyen de signaler un problème d'accessibilité. | Tôt | À faire. |
| S-18 | Mode « Facile à lire » | Bascule générale vers les versions simplifiées des contenus essentiels, quand elles existent. | Tôt | Versions simplifiées à écrire. |
| S-95 | Lecture à voix haute | Écouter n'importe quel article ou fiche avec la synthèse vocale du téléphone. | Tôt | À faire. |
| S-143 | Vidéos en langue des signes française | Pour les fiches essentielles (droits en manifestation, garde à vue, vote). | Plus tard | Lecteur vidéo (S-32) ; (ajout du chef d'équipe, 7 novembre 2026). |
| S-148 | Mode simple et accompagnant | Quatre gros boutons (Qui décide ?, Mes élus, Près de chez moi, Aide), sans balayage, « Écouter » partout, réglages de lecture (espacement, contraste, interligne) ; guide pour le proche aidant, sans jamais noter la phrase de récupération à la place de la personne. | Tôt | Réglages gardés sur l'appareil ; pôle Communauté, avec Comprendre pour l'audio (S-95) ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| CO-30 | Fiches essentielles dans les langues de France | Traduction bénévole, relue deux fois, des fiches essentielles en arabe maghrébin, berbère, créoles, anglais, portugais et turc, puis en langues régionales. | Plus tard | Après la relecture des versions françaises ; traductions (S-75) ; pôle Comprendre ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |

Autres mesures d'inclusion décrites ailleurs : économie de données (S-19), langue (S-17), accessibilité des lieux dans les appels (AP-10) et du parcours d'une manifestation (AG-34), aide pour sortir d'une manifestation (AG-44), réglages protecteurs par défaut pour les mineurs (règle transverse n° 18).

### M33. Résistance à la censure et sécurité numérique

L'information doit survivre à la coupure d'un serveur, d'un nom de domaine ou au retrait d'une boutique d'applications (vision, valeur « Résistance à la censure »). Les guides pour les militants sont dans « Comprendre » (CO-12).

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| S-43 | Verrouillage de l'application | Code ou empreinte digitale, facultatif. | Tôt | À faire. |
| S-44 | Effacement rapide | Un geste efface toutes les données de l'appareil, après un rappel de la phrase de récupération ; effacer ses clés efface aussi conversations, réponses et suivis (D-032, D-039) ; plus tard, un code PIN de contrainte ouvre un compte vide ou efface discrètement (prolonge aussi S-45). | Tôt ; Plus tard pour le code de contrainte | En partie (D-032, D-039) ; code de contrainte à étudier, avis d'un avocat indispensable (Code pénal, articles 434-4 et 434-15-2) (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| S-45 | Application discrète | Option : nom et icône neutres, contenu masqué dans la liste des applications ouvertes. | Plus tard (à étudier) | À faire. |
| S-46 | Authenticité des contenus | Chaque contenu est signé ; l'application vérifie la signature et l'indique (« contenu authentique PPF »). | Phase 1 | Signature faite (D-018) ; service de signature séparé, rotation et révocation à faire. |
| S-141 | Vérifier un contenu PPF | À partir d'un lien, d'un fichier ou du code imprimé sur une image de partage, vérifier qu'un contenu, une capture ou un lien vient bien de PPF et n'a pas été modifié (signature, identifiant stable, empreinte), dans l'application et sur le site, même hors ligne. | Tôt | S-46 ; (ajout du chef d'équipe, 7 novembre 2026). |
| S-97 | Partager l'application sans Internet | Transmettre l'application et les fiches essentielles d'un téléphone à l'autre, par Bluetooth ou code QR. | Tôt | À faire. |
| S-142 | Partage hors ligne de contenus signés | Transmettre un contenu signé par code QR ou fichier, vérifié à la réception (prolonge S-97). | Plus tard | S-141 ; (ajout du chef d'équipe, 7 novembre 2026). |
| S-47 | Adresses de secours | Si l'adresse principale est bloquée, bascule vers des miroirs, puis vers Tor (adresse .onion) et Nostr ; publication sur Nostr par le port de publication unique ; avant cela, liste d'adresses de secours signée et tenue à jour dans l'application, résolution DNS chiffrée et sonde de blocage désactivée par défaut (cahier des charges, 3.3 et 5.2). | Phase 3 ; Tôt pour la liste signée et la sonde | À faire ; liste signée et sonde avancées (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| S-92 | Signaler une faille de sécurité | Page publique et procédure de divulgation responsable : engagement de réponse, correction, remerciements publics. | Tôt | À faire. |
| S-93 | Transparence sur la sécurité | Publication des audits de sécurité et des incidents, avec ce qui a été corrigé. | Tôt (incidents), Plus tard (audit externe, coût reporté) | À faire. |

### M34. Transparence de PPF et ouverture des données

Une plateforme qui exige des comptes du pouvoir doit être exemplaire. Le code est ouvert (licence AGPL-3.0-or-later, D-006) et le dépôt public dès le premier commit (D-005). Les journalistes, chercheurs et observateurs trouvent ici ce dont ils ont besoin (ajout du chef d'équipe, 7 novembre 2026). Les textes et les images publiés par PPF sont sous licence CC BY 4.0, les jeux de données sous Licence Ouverte 2.0 (décision de la propriétaire du 8 novembre 2026, cahier des charges, section 3.8).

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| PP-01 | Transparence de PPF | Comptes annuels, décisions importantes, composition et fonctionnement des instances, rapport de modération, bilan annuel des réclamations. | Tôt | À faire. |
| PP-02 | Notre méthode | Comment PPF choisit ses sources, vérifie, distingue l'établi de l'allégué et corrige ses erreurs. | Tôt | Page « Notre méthode » des décryptages faite (D-038). |
| PP-03 | Nos victoires | Ce qui a été obtenu : engagements tenus, revendications satisfaites, lois modifiées, avec les sources. | Tôt | À faire. |
| PP-04 | Espace presse | Communiqués, kit média, chiffres réutilisables, contact presse (par M30). | Tôt | À faire. |
| PP-06 | Trésorerie publique | Chaque mouvement d'argent (entrées, sorties, redistributions) saisi par le trésorier, validé à deux et publié sous 48 heures avec sa date, son montant, sa catégorie et son justificatif ; solde, graphiques, export ; les particuliers, donateurs comme bénéficiaires, ne sont jamais nommés. | Tôt (dès le premier mouvement d'argent) | Point d'attention n° 15 (décidé). |
| PP-07 | Tableau de bord fédérateur | Chiffres de la force du mouvement (adhérents, comptes actifs, revendications, votes, appels, organisations, collectifs, signatures, engagements de candidats, argent collecté et redistribué), sur toute période. | Tôt | Statistiques agrégées (S-76). |
| S-48 | Données ouvertes | Export JSON documenté de tous les contenus publiés, avec un schéma versionné ; précisé par le mandat : API publique en lecture documentée et jeux de données téléchargeables (JSON et tableur), chaque donnée avec sa source. | Tôt | Formats et API faits (D-018, D-021) ; jeux de données à faire. |
| S-144 | Citer ce contenu | Référence prête à copier pour un article ou une étude : titre, date, version, adresse stable et empreinte. | Tôt | (ajout du chef d'équipe, 7 novembre 2026). |
| PP-09 | Engagements de conception loyale | Page publique des motifs trompeurs que PPF s'interdit, grille de relecture de chaque écran et bouton « Signaler un motif trompeur ». | Tôt | DSA, article 25 ; pôle Communauté ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |

### M35. Solidarité, soutien et bénévolat

PPF ne détient ni ne fait jamais transiter l'argent des collectes lancées par d'autres (point d'attention n° 12).

| ID | Fonctionnalité | Ce qu'elle fait | Priorité | Dépendances et état |
|---|---|---|---|---|
| AG-10 | Solidarité | Annuaire des caisses de grève et des caisses de solidarité juridique, avec des liens vers leurs propres pages de dons ; entraide (covoiturage vers les manifestations, hébergement). | Tôt | Appels à dons (AP-08). |
| AG-12 | Je veux aider | Bénévolat par compétences (juristes, traducteurs, développeurs, graphistes, vérificateurs, modérateurs) ; missions publiées par l'équipe ; seulement le pseudonyme et les compétences. | Tôt | À faire. |
| AG-60 | Simulateur du gréviste | Retenue de salaire selon le statut (privé au prorata ; fonction publique d'État, trentième indivisible ; territoriale et hospitalière au prorata), préavis et déclaration selon le secteur, caisses de grève proches (AG-10). | Tôt | Règles vérifiées sur Légifrance et relues ; à faire (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire). |
| S-55 | Faire un don à l'association | Don à PPF par un prestataire de paiement sans traceur ; reçu fiscal selon les règles applicables. | Plus tard | Décision en attente : Prestataire de paiement : éligibilité de HelloAsso (brainstorming du cahier des charges). |
| PP-08 | Adhérer à PPF | Adhésion et cotisation, avec un registre des adhérents tenu à part, chiffré, sans lien avec les comptes ni les votes ; paiement en espèces possible sur les stands ; la plateforme n'affiche que le nombre d'adhérents. | Plus tard | Statuts de l'association (juriste) ; prestataire de paiement (point d'attention n° 16). |

Voir aussi les appels à collecter des fonds (AP-08), le budget participatif de PPF (DE-24) et la trésorerie publique (PP-06).

## Annexe A. Correspondance des modules

| Module | Ancien emplacement (version 1.4) | Nouvel emplacement (version 2.0) |
|---|---|---|
| M1 Carte et agenda des mobilisations | Section 4 | 7 Agir |
| M2 Observatoire : les chiffres | Section 4 | 5.1 Voir, le mouvement |
| M3 Annuaire des organisations | Section 4 | 7 Agir, avec les comptes d'organisation (S-78 à S-81) |
| M4 Documentation et archives | Section 4 | 5.1 Voir (chronologie, archives, témoignages publiés) ; fiches des responsables dans M17 ; votes dans M20 ; accès aux archives publiques dans M6 (CO-10) |
| M5 Témoignages | Section 4 | 7 Agir (témoigner, preuves, lanceurs d'alerte) ; témoignages publiés dans M4 (VO-08) |
| M6 Informer | Section 4 | 6 Comprendre (fiches, décryptages, dossiers, glossaire) ; fil d'actualité dans M19 (MA-03) ; outil de publication dans M15 (S-70) |
| M7 Éduquer | Section 4 | 6 Comprendre, sous le nom « Histoire des luttes et valeurs » |
| M8 Bibliothèque numérique | Section 4 | 6 Comprendre |
| M9 International | Section 4 | 5.1 Voir, le mouvement |
| M10 Pétitions et mobilisation massive | Section 4 | 8 Décider |
| M11 Mur des revendications | Section 4 | 8 Décider |
| M12 S'organiser (mobilisation) | Section 4 | 7 Agir (guides, appels, matériel) ; groupes locaux devenus collectifs (M24) |
| M13 Se politiser : reprendre le pouvoir | Section 4 | 8 Décider (introduction du chapitre) |
| M13.1 Étage 1 : Comprendre | Section 4, M13 | 6 Comprendre (CO-01, CO-02, CO-09) ; « Mes élus » dans M27 (TE-01) ; « Interpeller » et « Participer » dans 8 Décider (M13.1, suite) |
| M13.2 Étage 2 : Voir pour qui la loi est faite | Section 4, M13 | 5.2 Voir, dans M17 |
| M13.3 Étage 3 : Revendiquer | Section 4, M13 | 8 Décider |
| M13.4 Étage 4 : S'organiser politiquement | Section 4, M13 | 8 Décider ; démocratie interne aussi dans M24 (AG-51) |
| M13.5 Étage 5 : Changer les règles | Section 4, M13 | 8 Décider, avec la délibération citoyenne (DE-25) |
| M13.6 Étage 6 : Décider nous-mêmes | Section 4, M13 | 8 Décider ; méthode du vote dans M16 |
| M13.7 Élections | Section 4, M13 | 8 Décider ; « Comprendre les élections » dans M13.1 (CO-09) |
| M14 Messagerie chiffrée | Section 4 | 11 Messagerie |
| M15 Modération | Section 4 | 12 Modules transverses, étendu à l'administration et à l'IA |
| M16 Comptes et rôles | Section 4 | 12 Modules transverses, étendu à l'intégrité et à « une personne, une voix » |
| M17 Mettre en lumière | Section 4 | 5.2 Voir, le pouvoir, les libertés et les contre-pouvoirs |
| M18 Fil social | Section 4 | 10 Fil social |
| M19 En direct | Nouveau (parcours « Découvrir », inventaire B1) | 4 En direct |
| M20 Le Parlement : votes et textes en cours | Nouveau (M4, législomètre, ajout du chef d'équipe) | 5.2 Voir |
| M21 État du pays et argent public local | Nouveau (pistes retenues, ajout du chef d'équipe) | 5.3 Voir |
| M22 Simulateurs et visualisations | Nouveau (pistes retenues) | 6 Comprendre |
| M23 Apprendre pas à pas et en classe | Nouveau (pistes retenues, brainstorming) | 6 Comprendre |
| M24 Collectifs | Nouveau (inventaire A22 et AG-47 à AG-53) | 7 Agir |
| M25 Secours, sécurité et défense en manifestation | Nouveau (inventaire B6) | 7 Agir |
| M26 Questionnaire citoyen anonyme et baromètre politique | Nouveau (inventaire B11) | 8 Décider |
| M27 Mon espace | Nouveau (inventaire B8, ajout du chef d'équipe) | 9 Mon espace |
| M28 Relations et cercles | Nouveau (inventaire A21) | 10 Fil social |
| M29 Socle de l'application | Nouveau (inventaire A1, A2, A4, A6 à A10, A12) | 12 Modules transverses |
| M30 Contact, réclamations et obligations légales | Nouveau (inventaire A13, A20) | 12 Modules transverses |
| M31 Notifications | Nouveau (inventaire A5) | 12 Modules transverses |
| M32 Accessibilité et inclusion | Nouveau (inventaire A10) | 12 Modules transverses |
| M33 Résistance à la censure et sécurité numérique | Nouveau (inventaire A11, A19) | 12 Modules transverses |
| M34 Transparence de PPF et ouverture des données | Nouveau (inventaire B9) | 12 Modules transverses |
| M35 Solidarité, soutien et bénévolat | Nouveau (inventaire B6, B9) | 12 Modules transverses |

## Annexe B. Dépendances communes

| Dépendance | Fonctionnalités concernées | État |
|---|---|---|
| Méthode « une personne, une voix » | DE-01, DE-05, DE-14, DE-19, DE-21, DE-25, DE-03, AG-51, AP-03, AP-04, VO-03, VO-27, M26, S-136, TE-06, VO-40, DE-29, DE-30 | Conception approuvée (D-110, D-112) ; en cours. |
| Validation en personne et certification des élus | S-86, S-88, S-129, AG-28, AG-32, AG-45, DE-15, DE-23, S-140 | Après le socle du back-office (D-110). |
| Back-office de l'équipe | M15, M30, S-38, S-81, S-127, CO-18, VO-08, AG-07 | En cours (Directeur technique). |
| Avis d'un avocat (avant l'ouverture publique) | M17 avant toute personne réelle, VO-14, VO-21, VO-33, CO-03, CO-07, AG-06, AG-08, AG-09, AG-11, AG-14, AG-18, M25, DE-11, DE-17, DE-23, S-140, S-53, statut d'hébergeur (D-044), mention « non déclarée » (D-045), AG-56, AG-57, DE-30, DE-33, DE-35, DE-37, code de contrainte (S-44) | Coût reporté (cahier des charges, section 12) ; liste tenue dans la feuille de route. |
| Hébergement du modèle d'IA | S-137, DE-18 (analyse), DE-19, DE-20, EQ-05, S-114 | Décision en attente : Modération et assistant IA : hébergement du modèle. |
| Référentiel des territoires | DE-02, DE-07 (objectifs), DE-26, DE-27, VO-32, CO-16, salons | Confié au pôle « Comprendre » (D-062). |
| Données ouvertes du Parlement | VO-10, VO-11, VO-13, VO-31, DE-06, DE-16, TE-01 (votes), VO-34, VO-35, VO-36, VO-39, VO-41, VO-43 | En cours (D-064, D-065). |
| Vue carte et tuiles | AG-01, AG-24, AG-30, AG-33 à AG-44, VO-05, VO-27, AP-05, VO-40 | En cours (D-046, D-080). |
| Notifications | M31, MA-04, AP-04, AG-49, CO-19, S-106, VO-34, CO-28, TE-10, S-145 | À faire. |
| Archivage horodaté | VO-07, DE-08, EQ-06, AG-54, AG-59 | Décidé (D-111), à coder. |
| Cercles (M28) | AG-23, AG-55, AG-59, TE-07, S-124 | À faire (point d'attention n° 26). |
| Prestataire de paiement | S-55, PP-08 | Décision en attente : Prestataire de paiement : éligibilité de HelloAsso. |
| Service de signature séparé de la clé de la plateforme | S-46, S-141, urnes du vote | À faire (cahier des charges, 3.2). |
| Phase 3 (Nostr, miroirs, Tor) | S-47, DE-14 (passage par Tor), AG-64 (ActivityPub) | Phase 3. |
| Phase 4 (bitchat) | AG-16, AG-39, AG-65 (radios LoRa) | Phase 4. |

## Annexe C. Décisions en attente

Rien de ce qui dépend de ces points n'est codé avant la décision de la propriétaire.

**Brainstorming du cahier fonctionnel**
- « Agir : questions ouvertes après la première version » : contradiction avec le principe 10 sur la visibilité par défaut de la participation d'un citoyen (cercle) et d'un élu (public) (M1, S-119).
- « Maquettes de l'interface communautaire : cercles, visibilité, collectifs, messagerie, bouton AIDE » : spécification du bouton AIDE, visibilité d'une publication par cercle, pages de collectif, « voir mon profil comme » (AG-27, AG-48, M18, S-123).
- « Fil social : l'application comme premier réseau social de la vie politique » : modalités des réactions et des suggestions (M18).
- « Messagerie terminée : questions ouvertes » : squat d'un nom de salon, clé distincte pour créer un salon, taille des conversations de groupe (S-82, S-83).
- « Salons de collectifs : création ouverte, charte, doublons » : rapprochement des salons par le sens (S-82).
- « Revendications : vues, filtres et vérifications automatiques » : liste des thèmes, durée maximale d'une proposition non traitée, mémoire des refus (DE-01, DE-18).
- « Vote blanc : questions à trancher pour une proposition solide » (DE-04).
- « Déroulé du Programme du peuple » (DE-03).
- « Canal sécurisé pour les lanceurs d'alerte » (AG-09).
- « Désignation citoyenne d'un candidat (phase 6) et neutralité » (DE-17).
- Pistes de campagnes à l'état d'idée, à proposer au vote des citoyens : « Campagne sur la transparence des archives », « Campagne : inscrire la Déclaration de 1793 dans la Constitution ».
- « [2026-11-08] Brainstorming de la nuit : fonctionnalités indispensables, innovantes et pertinentes », points restés à étudier : carte des opinions compatible avec l'urne anonyme, avec une analyse d'impact (DE-34) ; notes de contexte communautaires, avec une analyse d'impact et des jetons anonymes (S-152) ; code PIN de contrainte, seulement après l'avis d'un avocat (S-44). À présenter à la propriétaire le moment venu : coût du stockage des vidéos avant l'envoi continu (AG-58) ; achat des radios LoRa (AG-65) ; création gratuite d'un accès à l'API de Légifrance (PISTE), avec un guide (VO-42).

**Inventaire, points d'attention**
- N° 34 : hébergement du modèle d'IA et prestataire de paiement (assistant mis de côté).
- N° 28 relu avec D-112 : « compte vérifié » pour créer un collectif désigne désormais la validation en personne, ce qui rend la création plus stricte qu'en novembre ; à confirmer par la propriétaire (S-129).
- Délais et libellés à fixer : effacement des données d'une alerte de secours (par exemple deux heures, n° 17), durée d'inactivité du questionnaire (par exemple 30 minutes, PR-06), libellés neutres des hauteurs du baromètre (n° 9).

**Autres brainstormings qui touchent ce cahier**
- Cahier des charges : « Modération et assistant IA : hébergement du modèle » ; « Prestataire de paiement : éligibilité de HelloAsso » ; « Navigation et UX : quatre espaces, un accueil, « Mon territoire » » ; « Briques de contenu communes » ; « Vérification « loi déjà en vigueur » : source des textes » ; « Identités, phrase de récupération et signature des contenus : choix techniques » (confirmation de D-016 à D-018) ; « Réutilisation des données de la HATVP » ; « Licence des résultats des départementales de 2021 ».
- Vision : « Nouvelle version du constat (section 3) », dont dépend le choix des chiffres de VO-29.

## Annexe D. Identifiants retirés ou fusionnés, pistes écartées

Un identifiant retiré n'est jamais réutilisé.

| Identifiant | Ancienne fonctionnalité | Devenue |
|---|---|---|
| S-54 | Contact et signaler un bug | Remplacée par le contact et les réclamations (S-102 à S-111, M30). |
| S-87 | Parrainage | Retirée : « pas de parrainage » (décision de la propriétaire du 7 novembre 2026, D-112). |
| S-99 | Mon espace d'engagement | Fusionnée dans TE-06. |
| S-100 | Inviter un proche | Fusionnée dans S-116. |
| AG-04 | Groupes locaux | Remplacée par les collectifs (M24). |
| AG-05 | Proposer une action | Remplacée par les appels (AP-01 à AP-10, M12). |
| PP-05 | Piste fusionnée | Fusionnée dans PP-07. |

CO-20, S-138 et S-139 (assistant citoyen et crédits) ne sont pas retirés : ils sont mis de côté.

Pistes écartées, sans identifiant (brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire) :

| Piste | Raison |
|---|---|
| Délégation de vote par thème (démocratie liquide) | Contraire à l'urne anonyme (D-110), qui ne relie jamais un vote à un compte. |
| Audition publique au second seuil (proposition d'un agent de recherche) | La propriétaire a décidé le 7 novembre 2026 que l'équipe dépose une pétition officielle quand une revendication prend de l'ampleur. |

## Journal des versions

| Version | Date | Modifications |
|---|---|---|
| 1.0 | 4 novembre 2026 | Création à partir des instructions initiales du projet et des échanges sur le bloc « Se politiser ». |
| 1.1 | 6 novembre 2026 | Ajout du module M18 « Fil social » (écran par défaut en fil de réseau social, barre du bas fixe) et de la direction de design des fiches pratiques (encarts « En bref », « Légal », « Illégal »), décisions de la propriétaire du 6 novembre 2026 consignées dans les brainstormings. Copie locale modifiée par l'IA sur demande de la propriétaire ; à reporter dans le projet claude.ai. |
| 1.2 | 6 novembre 2026 | Barre du bas à cinq entrées (Fil, En direct, Carte, Messages, Revendications), vues du fil parcourues au doigt dont une vue « Revendications » (M18) ; messagerie confirmée dans les ambitions fonctionnelles avec ses trois vues (M14). Décisions de la propriétaire du 6 novembre 2026 (D-026). Copie locale modifiée par l'IA ; à reporter dans le projet claude.ai. |
| 1.3 | 6 novembre 2026 | M11 : vues et filtres du mur des revendications, vérifications automatiques des revendications proposées (doublons, loi déjà en vigueur, autres problèmes), demandes de la propriétaire du 6 novembre 2026 (D-029, D-030). Copie locale modifiée par l'IA ; à reporter dans le projet claude.ai. |
| 1.4 | 7 novembre 2026 | Ajout du module M17 « Mettre en lumière » (espace « Voir » : le pouvoir, libertés et contre-pouvoirs) avec sa méthode, les fiches identiques pour tous et l'ordre d'entrée des responsables publics, validés par la propriétaire le 7 novembre 2026 (D-036). Copie locale modifiée par l'IA ; à reporter dans le projet claude.ai. |
| 1.5 | 7 novembre 2026 | M2 : grille de l'indice de gravité des violences policières en cinq niveaux, validée par la propriétaire le 7 novembre 2026 (D-057). Copie locale modifiée par l'IA ; à reporter dans le projet claude.ai. |
| 2.0 | 7 novembre 2026 | Réécriture complète au titre du mandat de la propriétaire du 7 novembre 2026 (ajouter tout ce qui est nécessaire à l'accomplissement de la vision) : organisation selon la vision 4.x (rôles, parcours de « Voir » à « Décider », règles transverses, un chapitre par espace, modules transverses) ; intégration de l'inventaire des fonctionnalités validé le 4 novembre 2026, avec identifiants, priorités, dépendances et état ; M1 à M18 conservés et replacés, modules M19 à M35 ajoutés, table de correspondance ; décisions de la propriétaire du 7 novembre 2026 : grille de gravité (D-057), traitement identique des candidats gardé comme règle de méthode et de droit (D-062), trois niveaux de votants sans parrainage, validation en personne, liste anonyme des bulletins, vote modifiable, niveau figé et envoi différé pour tous (D-112), Pacte et campagnes délibérés par les citoyens, CESE, réponses tardives non comptées, relevé automatique, objectifs par territoire, pétitions locales, envoi manuel du Pacte (D-062), doctrine de transparence (D-044), histoire de toutes les luttes sans aucun appel à la violence (D-038) ; décisions déjà prises intégrées (D-005 à D-007, D-016 à D-018, D-022 à D-049, D-054 à D-057, D-059 à D-062, D-064, D-080, D-110 à D-112) ; ajouts du chef d'équipe marqués comme tels. Copie locale modifiée par l'IA ; à reporter dans le projet claude.ai. |
| 2.1 | 8 novembre 2026 | Intégration du brainstorming de la nuit du 7 au 8 novembre 2026 (30 idées des pôles, 41 idées de trois agents de recherche), sur mandat de la propriétaire du 7 novembre 2026 (« Pour les decisions a prendre, je suis tes recommandations ») : décisions du chef d'équipe par délégation de la propriétaire. Fonctionnalités ajoutées : VO-34 à VO-45, CO-23 à CO-30, AG-55 à AG-65, AP-11, DE-29 à DE-37, S-145 à S-152, TE-10, PP-09 ; tableau ajouté à M18 ; lignes modifiées : CO-15 (promue de Plus tard à Tôt), VO-32 (budget de ma commune), S-44 (code de contrainte à étudier), S-47 (liste d'adresses de secours signée et sonde avancées), TE-01 (parrainages et alerte de vote) ; deux pistes écartées (annexe D) ; annexes B et C, section 0 et mots techniques mis à jour. Copie locale modifiée par l'IA (chef d'équipe) ; à reporter dans le projet claude.ai. |
| 2.2 | 8 novembre 2026 | CO-13 « Ressources pour les enseignants » : format, niveaux et neutralité décidés par le chef d'équipe (délégation de la propriétaire), licence commune ; priorité Tôt (M23) ; la décision sort de l'annexe C. M34 : licence des contenus (CC BY 4.0) et des données (Licence Ouverte 2.0), décision de la propriétaire du 8 novembre 2026. Copie locale modifiée par l'IA (chef d'équipe) ; à reporter dans le projet claude.ai. |
