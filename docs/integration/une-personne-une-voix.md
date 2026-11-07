# « Une personne, une voix » : conception technique

- Pôle « Décider », 7 novembre 2026. Statut : **approuvé par le chef d'équipe le 7 novembre 2026 (D-110)**, avec les conditions et exigences de la section 15 ; les points qui changent l'expérience des citoyens (section 9) sont présentés à la propriétaire.
- Références : D-044 (méthode approuvée par la propriétaire), D-062 (délibération citoyenne sur le Pacte et les campagnes), brainstorming du cahier des charges (entrée « Méthode technique une personne, une voix »), inventaire S-86 à S-91 et point d'attention n° 10 (barrières combinées, résultats transparents), point d'attention n° 9 (jetons anonymes), cahier des charges 2 (principes 9 et 10), 5.1 et 5.5.
- Usages : votations (M13.6), votes sur les revendications (M11), délibération sur le Pacte et les campagnes (propositions, amendements, vote final), compteurs de participation d'« Agir », signalements chiffrés de « Voir » (VO-03).

## 1. Ce que le système doit garantir

| N° | Exigence | Origine |
|---|---|---|
| E1 | Le serveur ne peut pas relier un vote, un soutien ou un amendement voté à un compte, **même s'il le voulait** : impossibilité cryptographique, pas seulement une règle. | CDC principe 9, inventaire « rendre cette consultation impossible, et pas seulement interdite » |
| E2 | Une personne, une voix par question, au mieux de ce que permet l'absence d'identité, par des barrières combinées. | D-044, point n° 10 |
| E3 | Ni téléphone, ni e-mail, ni pièce d'identité, ni nom ; aucune adresse IP journalisée. | CLAUDE.md 7, CDC 5.1 |
| E4 | Résultats transparents : séparés par niveau de confiance, comptes confirmés en premier ; recomptables par n'importe qui après la clôture. | S-91, D-044 « la transparence passe avant tout » |
| E5 | Changer son vote jusqu'à la clôture. | Contre la pression sur le votant |
| E6 | Fonctionne hors ligne (vote mis en file, envoyé au retour du réseau), sur Android 8, ordinateur et, plus tard, iOS ; aucune bibliothèque réservée à une plateforme dans le code commun. | CLAUDE.md 7 |
| E7 | Tient sur le serveur KVM 1 (1 processeur virtuel, 4 Go), préproduction comprise. | CLAUDE.md 8 |
| E8 | Méthode publique et code ouvert ; seuls les **seuils** anti-fraude restent dans la configuration privée. | CLAUDE.md 11 |

## 2. Modèle de menaces

| Menace | Exemple | Barrières (section) | Risque qui reste |
|---|---|---|---|
| M1 Comptes multiples | Une personne crée vingt comptes pour voter vingt fois | Niveaux de confiance (4), preuve de travail (5), éligibilité figée à l'ouverture (6.4), résultats par niveau (7) | Les comptes « nouveaux » restent faciles à multiplier : ils sont comptés à part, jamais mélangés aux confirmés dans le chiffre mis en avant |
| M2 Robots, fermes de comptes | Script qui crée des milliers de comptes la veille d'un vote | Preuve de travail à l'inscription et à chaque demande, éligibilité figée, détection des vagues (8) | Un attaquant riche en calcul peut payer la preuve de travail : il reste au niveau « nouveau » |
| M3 Achat de voix | « 10 euros si tu votes contre » | Vote modifiable jusqu'à la clôture (6.6), aucun reçu avant la clôture | Après la clôture, la liste publique des bulletins permet à un votant de prouver son vote (choix à faire, section 9, Q4) |
| M4 Pression sur le votant | Un proche ou un militant exige de voir le vote | Vote modifiable en privé plus tard ; aucun historique côté serveur ; historique de l'appareil effaçable | Pression au moment même du vote, sur l'appareil : inévitable pour un vote à distance (la CNIL le reconnaît aussi) |
| M5 Serveur qui tente de relier (équipe infiltrée, piratage, saisie, réquisition) | Marquer un citoyen avec une clé à lui ; comparer les heures ; lire les adresses IP | Jetons aveugles vérifiables (6.1), clés publiques publiées et vérifiées (6.2), jetons obtenus à l'avance pour toutes les questions (6.3), envoi séparé et différé des bulletins (6.5), aucune IP journalisée | Un serveur pris en main **en direct** peut observer le réseau ; parade complète avec Tor et l'adresse .onion (phase 3) |
| M6 Serveur qui fabrique ou supprime des voix | Un administrateur s'émet des jetons | Nombre de jetons délivrés publié par question et par niveau ; recomptage public après la clôture ; graine des clés hors du serveur principal à terme | Un émetteur unique peut toujours tricher sur le nombre de jetons ; la publication rend la triche visible si elle dépasse le nombre de comptes de chaque niveau |
| M7 Rejeu, double usage | Envoyer deux fois le même bulletin | Identifiant de bulletin unique par jeton ; le second remplace le premier | Aucun |
| M8 Vol de l'appareil | Un tiers vote avec le téléphone | Verrouillage de l'appareil ; jetons dans la base locale effacée avec les clés | Comme pour tout le reste du compte |
| M9 Validateur complice | Un bénévole confirme cent faux comptes | Quotas par séance, détection des taux anormaux, lien compte-séance chiffré pour pouvoir révoquer (4.3) | Un complice qui reste sous les quotas passe inaperçu un temps |
| M10 Parrainages en chaîne | Un compte parraine ses propres faux comptes, qui en parrainent d'autres | Seuls les confirmés en personne parrainent, quota mensuel, les parrainés ne parrainent pas (4.2) | Quelques parrainages frauduleux par mois et par tricheur, au niveau « parrainé », compté à part |
| M11 Inondation | Millions de demandes ou de faux bulletins | Preuve de travail sur les bulletins, limitation de débit en mémoire, vérification d'un jeton peu coûteuse | Attaque réseau massive : relève de l'infrastructure (DDoS) |
| M12 Petits groupes | Niveau + territoire + heure = une seule personne | Le bulletin ne porte que le niveau (trois valeurs) ; heure arrondie ; aucune autre donnée ; seuil minimal avant d'afficher un croisement (7) | Une question très locale avec peu de votants : résultats montrés seulement au-delà du seuil |
| M13 Contribution de code malveillante | Affaiblir le tirage aléatoire | Vecteurs de test officiels, relecture ligne à ligne, bibliothèque cryptographique éprouvée | Comme pour tout le dépôt |

## 3. Pistes étudiées et choix proposé

Faits vérifiés le 7 novembre 2026 sur les sources officielles (adresses en fin de document).

| Piste | État | Verdict |
|---|---|---|
| Signature de Schnorr aveugle « naïve » | Cassée par l'attaque ROS (Benhamouda et al., Eurocrypt 2021) : avec environ 256 sessions concurrentes sur secp256k1, l'attaquant obtient plus de signatures que de sessions | **Exclue**, comme demandé. Les variantes sûres (Clause Blind Schnorr, Snowblind) ne sont ni normalisées ni disponibles en Kotlin |
| Signatures RSA aveugles, RFC 9474 (octobre 2023) | RFC ; variantes « Randomized » recommandées ; jetons vérifiables par tous | Possible, mais une clé RSA par question, environ dix fois plus de calcul que la piste retenue et des jetons de 256 octets. **Solution de repli** (voir 10) |
| RSA partiellement aveugle (métadonnée publique) | Brouillon `draft-irtf-cfrg-partially-blind-rsa-02`, expiré le 6 avril 2026, pas de RFC | Écartée : pas de norme stable |
| Privacy Pass, RFC 9576 à 9578 (juin 2024) | RFC ; types 0x0001 (VOPRF P-384) et 0x0002 (RSA aveugle 2048) | On en reprend les règles de confidentialité, pas le protocole HTTP : notre serveur joue tous les rôles (cas « Shared Origin, Attester, Issuer », RFC 9576, 4.1), ce qui impose de séparer l'obtention et l'usage des jetons dans le temps et dans le réseau (6.3, 6.5) |
| ARC (Anonymous Rate-Limited Credentials) | `draft-ietf-privacypass-arc-crypto-01` : **abandonné par le groupe de travail le 22 juillet 2026** (« Dead WG Document ») | Écartée, bien qu'elle aurait évité de délivrer un jeton par question |
| Signatures BBS avec pseudonymes par question | Brouillons actifs (`draft-irtf-cfrg-bbs-signatures-14`, `bbs-per-verifier-linkability-03`), sections de sécurité inachevées ; courbe BLS12-381 avec couplages | Écartée pour l'instant ; **piste d'avenir** si la masse de questions devient trop grande (10.3) |
| **VOPRF, RFC 9497 (décembre 2023), suite ristretto255-SHA512** | RFC ; preuve DLEQ que le serveur a bien utilisé la clé publiée | **Retenue** |

**Choix proposé** : une fonction pseudo-aléatoire oblivieuse vérifiable (VOPRF), mode 0x01 de la RFC 9497, suite ristretto255-SHA512, avec **une clé par question et par niveau de confiance**. Raisons :
- le serveur calcule le jeton sans voir ce qu'il signe (le « mélange » aléatoire du téléphone le rend parfaitement indiscernable) ; il ne peut donc pas reconnaître le jeton quand il revient avec le bulletin : c'est E1 ;
- la preuve DLEQ empêche le serveur d'utiliser une clé secrète différente pour un citoyen ciblé, à condition que la clé publique soit la même pour tous (6.2) ;
- c'est le calcul le plus léger des pistes normalisées (quelques multiplications sur la courbe par jeton) : E7 ;
- une clé par question peut être **publiée après la clôture** sans rien révéler d'autre : n'importe qui peut alors vérifier chaque bulletin et recompter (E4). C'est la raison pour laquelle on n'utilise pas le mode POPRF, dont la clé de chaque question est liée à la clé maîtresse : la publier révélerait tout.
- on n'utilise pas les preuves groupées de la RFC (« no security analysis » pour plusieurs clés, section 7.2.1) : une preuve par jeton, puisque chaque jeton a sa propre clé ;
- chaque clé ne répond qu'une fois par compte : avec Q demandes, la RFC (7.2.3, attaques Diffie-Hellman statiques) prévoit une perte d'environ log2(Q)/2 bits de sécurité, soit une dizaine de bits pour un million de comptes sur environ 128 ; la limitation de débit s'ajoute.

## 4. Niveaux de confiance (les barrières sur les comptes)

Le serveur connaît, pour chaque compte (clé publique Nostr), son niveau et la **semaine** à partir de laquelle il compte : un changement de niveau prend effet le lundi suivant, et le serveur ne garde que cette semaine, jamais le jour (le jour d'une validation en personne pourrait trahir l'événement où elle a eu lieu). Il ne connaît ni nom, ni lieu, ni appareil.

| Niveau | Comment on l'atteint | Ce que le compte peut faire |
|---|---|---|
| N0 « Nouveau compte » | Inscription signée avec une preuve de travail (5) | Voter ; son vote est compté dans la colonne « nouveaux comptes » |
| N1 « Parrainé » | Une invitation d'un compte N2 (4.2), plus une ancienneté minimale | Voter, compté dans la colonne « parrainés » ; ne parraine pas |
| N2 « Confirmé en personne » | Validation par un validateur de PPF lors d'un événement (4.3) | Voter, compté dans la colonne mise en avant ; parrainer quelques comptes par mois |

Les comptes d'organisations, les comptes « élu vérifié » (DE-23) et les clés de l'équipe éditoriale ne reçoivent pas de jetons : une organisation n'est pas une personne. Les membres de l'équipe votent avec leur compte personnel, comme tout le monde.

### 4.1 Rétrogradation
Un compte reconnu frauduleux redescend au niveau N0 pour les questions ouvertes après la décision. Les jetons déjà délivrés ne peuvent pas être retirés un par un (ils sont anonymes) : c'est la contrepartie de E1, compensée par l'éligibilité figée à l'ouverture (6.4) et l'annotation des résultats (8).

### 4.2 Parrainage sans graphe social sur le serveur
La règle « le graphe social reste sur les appareils » interdit au serveur de savoir qui a parrainé qui. Proposition :
- chaque compte N2 reçoit au début du mois un petit nombre d'**invitations** (3 par défaut, seuil privé), sous forme de jetons VOPRF d'une clé « parrainage » du mois, obtenues automatiquement par l'application **avant** tout besoin (séparation dans le temps : sinon l'heure de la demande et celle de l'usage trahiraient le lien) ;
- le parrain montre une invitation en code QR ; le filleul l'envoie avec son compte ; le serveur vérifie le jeton, le marque comme utilisé et fait passer le filleul en N1 ;
- le serveur sait que A a reçu trois invitations et que B en a utilisé une, jamais que A a invité B ;
- un parrainé ne parraine pas : pas de chaîne. Un tricheur N2 ne crée au plus que quelques N1 par mois, comptés à part.
- Limite : si un parrain triche, on ne peut pas retrouver ses filleuls. Option inverse (garder le lien pour révoquer) : section 9, Q2.

### 4.3 Validation en personne (S-88)
- Un **validateur** est un compte de l'équipe ou un bénévole habilité, avec double authentification (socle du back-office M16 du Directeur technique) ; il ouvre une **séance** limitée (40 validations au plus par défaut, seuil privé).
- La personne montre le code QR de son compte (clé publique et défi signé, pour prouver qu'elle tient la clé) ; le validateur le lit ; le serveur fait passer le compte en N2.
- **Aucun lieu, aucun événement, aucune identité** : le serveur garde la semaine d'effet (4) et, pour pouvoir annuler les validations d'un complice, le lien « compte, séance » **chiffré avec la clé publique d'une équipe d'audit** dont la clé secrète reste hors ligne (même principe que les contenus sensibles en attente, CLAUDE.md 7). Un serveur saisi ne révèle donc pas qui a été validé par qui.
- En clair, le serveur ne garde que les compteurs par séance (validateur, semaine, nombre de validations, durée de la séance), pour détecter les taux anormaux (8).
- Limite : une personne qui passe à plusieurs stands avec plusieurs téléphones obtient plusieurs comptes N2. Parades : coût physique, consigne aux validateurs (le compte montré doit être ouvert sur le téléphone présenté), quotas, détection. C'est le risque principal qui reste de M1.

## 5. Preuve de travail

- **Format NIP-13** : étiquette `["nonce", "<nonce>", "<difficulté visée>"]`, difficulté = nombre de bits à zéro en tête de l'identifiant de l'événement ; le serveur refuse un événement sans difficulté annoncée ou en dessous de son seuil.
- **Inscription** (premier événement d'un compte qui demande des jetons) : difficulté élevée, réglée pour quelques secondes sur un téléphone Android 8 d'entrée de gamme.
- **Demande de jetons** : authentification NIP-98 (événement 27235 signé, adresse et méthode exactes, empreinte du corps, horodatage à 60 secondes près) avec une petite preuve de travail ; le serveur garde en mémoire les identifiants déjà vus pendant la fenêtre, car NIP-98 n'empêche pas seul le rejeu.
- **Bulletin anonyme** : petite preuve de travail sur le bulletin, pour rendre coûteuse l'inondation de faux bulletins.
- Sur le site (PWA, plus tard) : ALTCHA (`org.altcha:altcha` 2.1.1, licence MIT, Java 17 ou plus) côté serveur.
- Toutes les difficultés sont des seuils de la configuration privée ; le code n'a que des valeurs de développement.

## 6. Le vote anonyme, étape par étape

### 6.1 Clés
- Une **graine maîtresse** de 32 octets, secrète, sert à dériver toutes les clés de vote : `DeriveKeyPair(graine, info = "ppf-urne-v1" ‖ identifiant de la question ‖ niveau)` (RFC 9497, 3.2.1). Rien d'autre à stocker.
- La graine n'a **aucun rôle de confidentialité** : la connaître ne permet pas de relier un vote à un compte (l'aveuglement en est indépendant). Elle protège l'**intégrité** (fabriquer des jetons). Elle est rangée dans un **fichier secret Docker lisible seulement par l'utilisateur du serveur**, jamais dans une variable d'environnement ni en base, une graine différente par environnement ; plus tard, elle passera dans le service de signature séparé (R3).
- Clés distinctes de toutes les clés Nostr : aucune clé n'est réutilisée d'un protocole à l'autre.
- **Emplacement** (Directeur technique) : dossier `PPF_VOTE_SEEDS_DIR=/run/ppf/vote_seeds`, monté en lecture seule, un fichier par version de graine nommé par son numéro (`1`, `2`…), 32 octets aléatoires chacun, propriété de l'utilisateur du serveur, droits 0400 ; `bootstrap-server.sh` crée la version 1, `ppf-vote-seed rotate <environnement>` ajoute la suivante, `retire <environnement> <version>` détruit une ancienne version quand ses dernières urnes sont closes (le serveur le signale). Un jeu de graines par environnement.
- **Perte** : les jetons des urnes ouvertes deviendraient invérifiables, et ces votes seraient à rouvrir ; les urnes closes ne perdent rien, leurs clés sont déjà publiées. La graine entre donc dans la sauvegarde chiffrée hors site, avec la base ; une copie hors ligne gardée par la propriétaire est recommandée.
- **Renouvellement** : chaque urne indique la **version de graine** qui a servi à ses clés. Une nouvelle graine ne sert qu'aux urnes ouvertes ensuite ; l'ancienne reste lisible jusqu'à la clôture de ses dernières urnes et la publication de leurs clés, puis elle est détruite. Les jetons déjà délivrés restent donc valables. Renouvellement une fois par an, et tout de suite en cas de doute. En cas de fuite, les urnes ouvertes avec la graine compromise sont annotées publiquement ; elles sont rouvertes avec de nouvelles clés si le journal montre plus de jetons que de comptes.

### 6.2 L'urne, publiée et signée
Chaque question votable a une **urne** : un événement signé par la clé de la plateforme, publié par le port de publication comme les autres contenus. Elle contient : l'objet voté (revendication, mesure, amendement, votation, mobilisation, indicateur), le type de bulletin, les choix possibles, l'ouverture et la clôture, et **une clé publique par niveau**. L'application refuse tout jeton dont la preuve ne correspond pas à la clé publique de l'urne signée. Toutes les applications voient la même urne (même identifiant de contenu) ; les miroirs Nostr de la phase 3 permettront de le vérifier depuis l'extérieur. Le nombre de clés actives est public et borné (trois par question).

### 6.3 Obtenir ses jetons : tout pour tous, à l'avance
- À chaque synchronisation, l'application demande automatiquement **un jeton pour chaque urne ouverte qu'elle n'a pas encore**, qu'on ait l'intention de voter ou non. Le serveur ne peut donc rien déduire des demandes : tout le monde demande tout.
- Pour chaque urne, l'application tire 32 octets au hasard (`y`, l'identifiant secret du futur bulletin), les « aveugle » et envoie le résultat. Le serveur, authentifié par NIP-98, applique la clé de l'urne pour le **niveau du compte**, joint la preuve, et note seulement « ce compte a reçu les jetons jusqu'à l'urne numéro n » (un curseur par compte, aucune liste par question).
- L'application vérifie la preuve, retire l'aveuglement et garde `(urne, niveau, y, jeton)` dans sa base locale.
- Elle vérifie aussi que le niveau utilisé est bien le sien (affiché dans « Mon espace ») : un niveau inattendu est refusé et signalé.
- Le serveur ne délivre jamais deux jetons d'une même urne à un compte. Pour qu'une réponse perdue en route ne coûte pas les jetons, il garde 24 heures la dernière réponse de chaque compte et la renvoie si la même demande revient.
- Réinstallation : les jetons non utilisés sont perdus, sauf s'ils sont dans la sauvegarde chiffrée facultative ; on ne peut pas en redemander pour les urnes déjà servies (ce serait un second vote).
- Mode économie de données : la demande attend le premier besoin (ouverture d'un écran de vote), mais porte toujours sur toutes les urnes ouvertes.

### 6.4 Éligibilité figée à l'ouverture
Le niveau d'un jeton est celui que le compte avait **la semaine de l'ouverture de l'urne** ; un compte créé après l'ouverture vote au niveau N0. Une vague de faux comptes la veille d'un vote ne peut donc toucher que la colonne « nouveaux comptes ». Conséquence voulue : une montée de niveau ne vaut que pour les urnes ouvertes ensuite.

### 6.5 Voter : un envoi séparé, sans compte
- Le bulletin contient : urne, niveau, `y`, jeton, choix, preuve de travail. **Ni signature du compte, ni clé, ni en-tête d'authentification.**
- Il part par un **client HTTP distinct** (sans cookie, sans réutilisation de connexion, sans en-tête d'authentification, avec un agent utilisateur générique, R4), après un **délai aléatoire** (par défaut entre 0 et 10 minutes, réglage le plus protecteur ; le citoyen peut choisir l'envoi immédiat après une mise en garde, principe 10). Le vote est enregistré tout de suite sur l'appareil ; hors ligne, il part au retour du réseau.
- Le serveur vérifie le jeton avec la clé de l'urne, vérifie la preuve de travail, et range le bulletin sous `y`. Il garde l'heure **arrondie à l'heure**, jamais l'adresse IP.
- Plus tard : passage par Tor et l'adresse .onion (phase 3) ; éventuellement un relais HTTP oblivieux (RFC 9458) tenu par un tiers de confiance.

### 6.6 Changer son vote, le retirer
Un nouveau bulletin avec le même `y` et le même jeton **remplace** le précédent jusqu'à la clôture ; un bulletin « retiré » annule le vote. Seul l'appareil qui connaît `y` peut le faire. Le compteur public de remplacements par urne fait partie du journal (8).

### 6.7 Clôture et recomptage par tous
À la clôture, le serveur publie un événement signé de résultat : totaux par niveau et par choix, nombre de jetons délivrés par niveau, **la clé secrète de l'urne pour chaque niveau**, et la liste anonyme des bulletins (`y`, jeton, choix, niveau, heure arrondie), **triée par `y`** pour ne rien dire de l'ordre d'arrivée (R1). N'importe qui peut alors vérifier que chaque clé correspond à la clé publique annoncée, recalculer chaque jeton, vérifier qu'aucun `y` n'est en double, recompter, et vérifier que les bulletins ne dépassent pas les jetons délivrés. Publier la clé d'une urne close ne permet ni de créer des jetons utiles (l'urne est close) ni de relier un bulletin (l'aveuglement ne dépend pas de la clé), ni de retrouver la graine (une clé dérivée ne permet pas de remonter à la graine, ni donc aux clés des autres urnes). Choix à confirmer : section 9, Q4.

Les questions sans fin naturelle (une revendication reste ouverte au vote des mois) ne publient que leurs totaux tant qu'elles sont ouvertes ; la liste vérifiable vient quand l'équipe les arrête (passage au Programme du peuple, fusion, clôture).

## 7. Les résultats

- Une colonne par niveau, toujours dans le même ordre : **confirmés en personne** (mis en avant, S-91), parrainés, nouveaux comptes, puis tous les comptes (chaque compte n'a qu'un jeton par question, donc la somme ne compte personne deux fois).
- À côté de chaque colonne : le nombre de jetons délivrés (la participation) et un rappel de ce que vaut chaque niveau.
- Les mots avant les couleurs (WCAG) ; même présentation pour toutes les questions.
- Aucun croisement plus fin (territoire, tranche d'âge, bord politique) sans un **seuil minimal de bulletins par case** (20 par défaut, seuil privé). Le baromètre politique de DE-21 (déclaratif, PR-01 à PR-07) reste À valider : s'il vient, la réponse est saisie dans le bulletin lui-même, jamais rattachée au compte.

## 8. Détection des manipulations et journal public

Le serveur ne peut rien relier : la détection porte sur des **compteurs agrégés**, publiés **en continu** dans un journal de l'urne, pas seulement à la clôture (R2) : nombre de comptes par niveau et nombre de jetons délivrés par urne et par niveau, pour qu'un bourrage (plus de jetons que de comptes) se voie tout de suite ; puis :
- jetons délivrés et bulletins reçus par urne, niveau et heure ; remplacements ;
- inscriptions par jour, montées en N1 et N2 par jour, validations par séance ;
- alertes à l'équipe au-delà des seuils privés (vague d'inscriptions, séance trop rapide, pic de bulletins sur une urne) ;
- réponses possibles : suspendre les montées de niveau, retarder l'ouverture d'une urne, **annoter publiquement** un résultat (« vague anormale de nouveaux comptes le … »), rétrograder les comptes d'une séance révoquée (4.3). Jamais d'effacement silencieux de bulletins.

## 9. Questions pour la propriétaire (ce qui change l'expérience)

Chaque question a ma recommandation en premier.

1. **Q1 Les trois niveaux** (« nouveau », « parrainé », « confirmé en personne ») et le principe « tout le monde vote, les confirmés sont mis en avant, chaque niveau est montré à part » ; les comptes d'organisations et d'élus ne votent pas (une organisation n'est pas une personne ; leurs membres votent avec leur compte personnel). Recommandation : oui.
2. **Q2 Parrainage anonyme** (PPF ne sait jamais qui a invité qui ; trois invitations par mois pour un confirmé ; un parrainé ne parraine pas) plutôt qu'un parrainage enregistré qui permettrait d'annuler les filleuls d'un tricheur. Recommandation : anonyme, conforme au graphe social gardé sur les appareils.
3. **Q3 Validation en personne** par l'équipe et des bénévoles habilités, sans lieu ni identité ; le lien « compte, séance » chiffré pour une équipe d'audit, utilisé seulement pour annuler les validations d'un complice. Recommandation : oui.
4. **Q4 Publier la liste anonyme des bulletins après la clôture** (chacun peut recompter et vérifier que son bulletin est compté ; mais un votant peut alors prouver son vote à un acheteur) plutôt que les seuls totaux. Recommandation : publier, au nom de la transparence et contre les accusations de chiffres truqués ; les votations n'ont pas de valeur légale.
5. **Q5 Vote modifiable jusqu'à la clôture.** Recommandation : oui.
6. **Q6 Niveau figé à l'ouverture de chaque vote** (un nouveau venu vote tout de suite, au niveau « nouveau »). Recommandation : oui.
7. **Q7 Envoi différé du bulletin** de quelques minutes par défaut, envoi immédiat possible après une mise en garde. Recommandation : oui.

## 10. Bibliothèques et code à écrire

Vérifié le 7 novembre 2026 sur Maven Central, les dépôts et les binaires publiés. **Aucune bibliothèque Java ou Kotlin maintenue n'implémente la RFC 9497 ni la RFC 9474** : le protocole est à écrire et à tester par nous ; seule l'arithmétique de la courbe vient d'une bibliothèque.

### 10.1 Proposition

| Brique | Choix | État vérifié |
|---|---|---|
| Groupe ristretto255 (points, scalaires, multiplication) | `com.ionspin.kotlin:multiplatform-crypto-libsodium-bindings` 0.9.5 (Apache-2.0), qui appelle libsodium (ISC) | Version du 22 novembre 2025 ; ristretto255 complet depuis 0.9.3 (`crypto_core_ristretto255_from_hash`, addition, soustraction, validation, scalaires, `crypto_scalarmult_ristretto255` et sa version de base) ; Android (minSdk 26), JVM, iOS publiés |
| SHA-512 | `fr.acinq.bitcoin.crypto.Digest.sha512()` de bitcoin-kmp, **déjà dans le projet** | Public dans 0.32.0 |
| Protocole RFC 9497 (`expand_message_xmd`, `HashToGroup`, `HashToScalar`, preuve DLEQ, `DeriveKeyPair`, aveuglement, finalisation) | **Écrit par nous**, en Kotlin commun pur, au-dessus d'une petite interface `Ristretto255` | Environ 400 lignes ; testé avec **tous les vecteurs de l'annexe A de la RFC 9497** (suite ristretto255-SHA512, modes OPRF et VOPRF) et des tests négatifs |
| Preuve de travail NIP-13, authentification NIP-98 | Écrites par nous, au-dessus de la signature déjà présente dans `shared/core` | Formats lus dans les NIP le 7 novembre 2026 |

Raisons et précautions :
- **Interface entre le protocole et la bibliothèque** : le code du protocole ne voit qu'une interface `Ristretto255` (décoder et valider un point, point depuis 64 octets, addition, soustraction, multiplication, scalaires). Si la bibliothèque pose problème sur une plateforme, on change son fournisseur sans toucher au protocole, comme le demande CLAUDE.md pour iOS.
- **Risques de la bibliothèque, à vérifier dès la première étape** :
  - son README la dit « expérimentale » : on n'utilise que les fonctions de bas niveau, qui transmettent directement à libsodium, et les vecteurs de la RFC vérifient le résultat de bout en bout ;
  - elle embarque libsodium 1.0.19 (ordinateur, iOS) et 1.0.20 (Android), alors que la dernière version est 1.0.22 (9 avril 2026) : les fonctions ristretto255 existent depuis longtemps, mais il faudra surveiller les correctifs de libsodium ;
  - sur JVM et Android, elle passe par JNA (licence double LGPL-2.1 ou Apache-2.0, utilisée sous Apache-2.0) et `com.goterl:resource-loader` (MIT) : deux dépendances de plus ;
  - elle doit être initialisée (`LibsodiumInitializer.initialize()`, fonction suspendue) avant le premier usage ;
  - le ticket ouvert n° 56 signale un plantage de l'application Windows empaquetée (`FileSystemNotFoundException: Provider "jar"`) : à tester sur l'exécutable de bureau (`createDistributable`), probablement réglé en ajoutant le module `jdk.zipfs`, non vérifié ;
  - la bibliothèque iOS a été compilée avec Kotlin/Native 1.9.23 et dépend d'une bibliothèque d'interopérabilité C, dont la compatibilité avec Kotlin 2.4.21 n'est pas garantie : non vérifiable tant qu'iOS n'est pas compilé ; repli prévu, une interopérabilité directe avec la libsodium officielle derrière la même interface ;
  - taille de l'APK : bibliothèques natives de libsodium et de JNA pour quatre architectures, à mesurer ; `tools/check-apk.sh` devra les attendre.
- **Licences** : Apache-2.0, ISC et MIT sont compatibles avec l'AGPL-3.0 du projet.
- **Serveur** : la même bibliothèque sur la JVM ; la graine des clés lue dans la configuration privée (plus tard le service de signature séparé).

### 10.2 Solution de repli : signatures RSA aveugles (RFC 9474)
Si libsodium se révèle inutilisable :
- côté application, en Kotlin commun pur : grands entiers `com.ionspin.kotlin:bignum` 0.3.10 (Apache-2.0, juillet 2024 ; utiliser `ModularBigInteger.pow(BigInteger)`, car la puissance par un entier calcule tout avant de réduire ; ni Montgomery ni temps constant, acceptable puisque l'application ne manipule aucune clé secrète RSA), SHA-384 par `org.kotlincrypto.hash:sha2` 0.8.0 (Apache-2.0 ; bitcoin-kmp n'a pas de SHA-384) ;
- côté serveur : opération RSA privée de la JCA, avec vérification de la signature avant de la rendre (RFC 9474, 4.3) ; BouncyCastle n'a pas d'API RFC 9474 toute faite ;
- variante RSABSSA-SHA384-PSS-Randomized, clés de 2048 bits ou plus ; vecteurs de test de la RFC.
- Inconvénients : une clé RSA par question et par niveau à générer et garder, environ dix fois plus de calcul par jeton, jetons de 256 octets. Réservé aux votations et aux délibérations (peu de questions) ; les votes des revendications attendraient une meilleure solution.

### 10.3 Volume : le point à surveiller
« Tout pour tous » (6.3) veut dire autant de jetons par compte que d'urnes ouvertes. Ordres de grandeur **à mesurer à la première étape** (estimations, non vérifiées) : environ six multiplications sur la courbe par jeton côté application (quelques millisecondes sur un vieux téléphone), trois côté serveur, environ 130 octets échangés par jeton. Pour 5 000 revendications ouvertes : une première synchronisation de quelques secondes en tâche de fond et environ 650 Ko ; ensuite seulement les nouvelles urnes. Côté serveur, 10 000 comptes fois 5 000 urnes représentent quelques heures de calcul étalées dans le temps ; une file d'attente lisse les pics d'inscriptions.
Si la mesure dépasse ce que tient le KVM 1 :
- n'ouvrir au vote que les revendications « en débat » (et non tout le mur) ;
- délivrer les jetons en plusieurs fois, toujours pour toutes les urnes ;
- plus tard, des signatures BBS avec un pseudonyme par question (un seul titre par compte, aucun jeton par question), quand les brouillons de l'IRTF seront terminés.

### 10.4 Pour les étapes suivantes
- Site installable (iPhone, plus tard) : ristretto255 existe dans libsodium.js ; le vote demandera du JavaScript, le site reste lisible sans.
- ALTCHA côté serveur : `org.altcha:altcha` 2.1.1 (MIT, Java 17 ou plus).
- Lecture et création de codes QR (parrainage, validation) : bibliothèques à vérifier à l'étape 5 (jamais ML Kit, qui est un service Google).
- Archivage horodaté (tâche suivante du pôle) : `java-opentimestamps` n'est plus maintenu (version 1.20 de janvier 2021, LGPL-3.0, dépend de bitcoinj 0.14.7) ; à comparer : un petit client OpenTimestamps écrit par nous, ou l'horodatage RFC 3161 de BouncyCastle (`bcpkix-jdk18on` 1.86, JVM seulement, suffisant pour le serveur).

## 11. Délibération citoyenne sur le Pacte et les campagnes (D-062)

La propriétaire a décidé que la liste des mesures du Pacte et les modalités des campagnes « seront populaires » : la liste de PPF n'est qu'une proposition de départ. Le même mécanisme de jetons sert :
- **Une délibération** a un objet (par exemple « les mesures du Pacte pour la présidentielle de 2027 », « les modalités des campagnes »), des phases datées et la proposition de départ de PPF, affichée avec la mention « proposition de départ, pas encore votée ».
- **Propositions et amendements** : publiés sous le pseudonyme de leur auteur (comme les revendications, avec la même relecture avant publication et les mêmes vérifications automatiques) ; chaque proposition et chaque amendement a son **urne** (pour, contre, abstention), donc ses jetons.
- **Vote final** : une seule urne dont le bulletin porte tout le choix (par exemple une mention par mesure en jugement majoritaire, ou l'approbation de cinq à dix mesures). La méthode de vote finale est elle-même une modalité que les citoyens pourront changer ; la proposition de départ sera faite avec le module.
- Le même module servira au Programme du peuple (DE-03), aux cahiers de doléances (DE-02) et aux votations (M13.6, une seule question).
- Calendrier : pour une diffusion du Pacte au début de 2027, la délibération sur les mesures doit s'ouvrir au plus tard en janvier 2027.

## 12. Branchements par pôle

| Usage | Objet de l'urne | Pôle |
|---|---|---|
| Votations (M13.6) | La question | Décider |
| Revendications (M11) | Chaque revendication publiée (pour, contre) | Décider |
| Délibération (Pacte, campagnes) | Propositions, amendements, vote final | Décider |
| Participation (« J'y participe », soutiens d'un appel) | La mobilisation ou l'appel ; le nombre public de participants vient des bulletins ; afficher son nom reste un choix à part (D-044) | Agir |
| Signalements chiffrés (VO-03) | Le couple indicateur et événement ; le bulletin porte la valeur signalée | Voir |

Chaque pôle n'appelle qu'une interface commune (`core/urne`, à créer) et un composant d'interface commun ; aucune cryptographie dans les pôles.

## 13. Ce qui est stocké (pour le registre RGPD)

- **Serveur, par compte** : clé publique, niveau, semaine d'effet de chaque niveau atteint et semaine d'inscription, invitations restantes du mois, curseur des jetons délivrés (il révèle la dernière synchronisation, rien de plus), dernière réponse de délivrance gardée 24 heures. Lien « compte, séance de validation » chiffré pour l'équipe d'audit.
- **Serveur, par urne** : nombre de jetons délivrés par niveau ; bulletins (`y`, jeton, choix, niveau, heure arrondie), sans compte ni IP ; invitations utilisées (empreinte). Les clés ne sont pas stockées (dérivées de la graine).
- **Appareil** : jetons non utilisés et identifiants `y` de ses bulletins, ses votes (pour « Mes votes et soutiens », TE-06), effacés avec les clés.

## 14. Étapes de réalisation proposées

1. `core/urne` : VOPRF ristretto255-SHA512, `DeriveKeyPair`, preuve DLEQ, avec tous les vecteurs de test de la RFC 9497 (annexe A) et des tests négatifs (preuve fausse, élément neutre, mauvaise clé) ; formats de l'urne, du jeton et du bulletin dans `docs/FORMATS.md`.
2. Serveur : niveaux, inscription avec preuve de travail, délivrance des jetons (NIP-98), réception des bulletins, remplacement, clôture et publication, journal ; migration Flyway (numéro à demander au chef d'équipe).
3. Application : base locale des jetons (migration locale, numéro à demander), synchronisation, file d'envoi différé, composant de vote, résultats par niveau, niveau dans « Mon espace ».
4. Branchement des revendications (M11), puis délibération (11) ; contrat pour « Agir » et « Voir ».
5. Parrainage par code QR, puis validation en personne (avec le back-office du Directeur technique).
6. Plus tard : client du site installable (iPhone), Tor.

## 15. Décision du chef d'équipe (D-110) : conditions et exigences

- **T1 à T5 approuvés** : mécanisme de la section 3 ; bibliothèque de 10.1 ; migrations réservées Flyway `V7` et `13.sqm` ; validation en personne après le back-office du Directeur technique (Flyway `V8` à lui) ; volume mesuré à l'étape 1.
- **Conditions de l'étape 1, avant toute suite** (sinon repli RSA aveugle de 10.2) :
  - (a) tous les vecteurs de l'annexe A (ristretto255-SHA512, OPRF et VOPRF) et les tests négatifs passent sur la JVM ;
  - (b) **autotest au premier usage** : l'application rejoue quelques vecteurs sur l'appareil et refuse de voter, avec un message clair, s'ils échouent ; c'est ce qui prouvera que libsodium marche sur un vrai téléphone ;
  - (c) l'exécutable de bureau empaqueté démarre et passe l'autotest (ticket n° 56) ;
  - (d) hausse de taille de l'APK mesurée, `tools/check-apk.sh` mis à jour ;
  - (e) iOS : la dépendance seulement là où elle compile ; sinon l'interface reste commune et l'implémentation iOS est à écrire plus tard (feuille de route).
- **R1 aucune corrélation par la base** : bulletins indexés par `y`, aucun identifiant séquentiel, aucune heure plus fine que l'heure arrondie ; liste publiée triée par `y` ; délivrance des jetons datée au jour au plus ; journalisation des requêtes SQL et des corps de requête désactivée sur ces routes.
- **R2 journal public en continu** (section 8).
- **R3 graine maîtresse** en fichier secret Docker (6.1), décrite au Directeur technique et dans le modèle de menaces.
- **R4 client des bulletins distinct** (6.5).
- **R5 relecture indépendante** de `core/urne` avant la fusion dans `main` : un agent séparé ne reçoit que le code et la RFC 9497, pas ces conclusions ; ses remarques sont traitées avant la fusion.
- **R6 contrat `core/urne`** pour « Agir » et « Voir » (interface, composant de vote, ce qui leur est interdit), envoyé au chef d'équipe pour diffusion.

## Sources

- RFC 9474, RSA Blind Signatures : https://www.rfc-editor.org/rfc/rfc9474.html
- RFC 9497, OPRF, VOPRF, POPRF : https://www.rfc-editor.org/rfc/rfc9497.html
- RFC 9576, 9577, 9578, Privacy Pass : https://www.rfc-editor.org/rfc/rfc9576.html, https://www.rfc-editor.org/rfc/rfc9577.html, https://www.rfc-editor.org/rfc/rfc9578.html
- RSA partiellement aveugle : https://datatracker.ietf.org/doc/draft-irtf-cfrg-partially-blind-rsa/
- ARC, historique et abandon : https://datatracker.ietf.org/doc/draft-ietf-privacypass-arc-crypto/history/
- BBS : https://datatracker.ietf.org/doc/draft-irtf-cfrg-bbs-signatures/ et https://datatracker.ietf.org/doc/draft-irtf-cfrg-bbs-per-verifier-linkability/
- Attaque ROS : https://eprint.iacr.org/2020/945
- NIP-13 et NIP-98 : https://github.com/nostr-protocol/nips/blob/master/13.md, https://github.com/nostr-protocol/nips/blob/master/98.md
- ALTCHA : https://github.com/altcha-org/altcha-lib-java
- CNIL, délibération n° 2019-053 sur le vote par Internet : https://www.legifrance.gouv.fr/affichTexte.do?cidTexte=JORFTEXT000038661239
- Liaisons libsodium d'ionspin : https://github.com/ionspin/kotlin-multiplatform-libsodium (README, CHANGELOG, ticket n° 56) et https://repo1.maven.org/maven2/com/ionspin/kotlin/multiplatform-crypto-libsodium-bindings/maven-metadata.xml
- libsodium, ristretto255 et versions : https://doc.libsodium.org/advanced/point-arithmetic/ristretto et https://github.com/jedisct1/libsodium/releases
- bignum d'ionspin : https://github.com/ionspin/kotlin-multiplatform-bignum
- KotlinCrypto (SHA-2) : https://github.com/KotlinCrypto/hash
- bitcoin-kmp 0.32.0 (sources) : https://repo1.maven.org/maven2/fr/acinq/bitcoin/bitcoin-kmp/0.32.0/
- BouncyCastle : https://github.com/bcgit/bc-java (RSABlindingEngine, paquet tsp)
- Implémentations de référence, pour comparer : https://github.com/cloudflare/circl (oprf, blindsign/blindrsa), https://github.com/meta/voprf, https://github.com/apple/swift-crypto (CryptoExtras)
- java-opentimestamps : https://github.com/opentimestamps/java-opentimestamps
