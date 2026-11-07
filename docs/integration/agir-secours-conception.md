# Conception : alerte aux street medics et coordination des secours (M25 : AG-27 complet, AG-28 à AG-30, AG-41)

Rédigée le 7 novembre 2026 par un agent de la session « Agir », branche `worktree-agent-a6bbc59bcf9fcaf8f` (partie de `feature/agir`). **Conception écrite seulement : aucune ligne de code de cette note n'est écrite.** Elle prépare la deuxième étape de M25 ; la première (bouton AIDE, feuille d'urgence, mode manifestation) est décrite dans `agir-manifestation.md`.

Rien de ce qui suit ne se code avant les réponses de la section 9 (passées par la session principale au chef d'équipe), l'accord des organisations de street medics avec qui le dispositif est construit, et la relecture d'un avocat avant le lancement (règles du module M25 ; points d'attention n° 14, 17, 18 et 24).

## 1. Ce que demande le cahier fonctionnel (rappel)

- **AG-27** : ouvert à tous, avec ou sans compte ; la personne blessée ou un proche envoie sa position aux street medics disponibles et indique la gravité (1 léger, 2 sérieux, 3 urgence vitale, qui propose d'abord le 15 ou le 112) ; elle voit « un medic arrive » et sa distance ; aucune limite d'alertes par téléphone.
- **AG-28** : certification en personne (code QR ou représentant certifié), niveau 1 (PSC1) à 5 (médecin urgentiste ou chirurgien), déclaré et confirmé, **aucun diplôme stocké** ; dépend de la validation en personne (S-88).
- **AG-29** : jusqu'à 5 medics répondent « J'y vais » (réglable selon la gravité), l'alerte passe « prise en charge » ; medic « occupé » puis « disponible » ; les alertes non prises en charge remontent.
- **AG-30** : tableau réservé aux medics et coordinateurs : medics disponibles ou occupés avec leur niveau, alertes par gravité, zones qui concentrent les alertes, zones sans medic.
- **AG-41** : alertes à environ 15 m et 2 min (réglables) regroupées en une, la plus grave ; les autres restent en petits points rouges ; validation (nouvel appel) ou invalidation avec motif ; seul « fausse alerte » compte pour les abus ; un téléphone signalé envoie à la coordination avec la mention « à vérifier » ; identifiant anonyme temporaire effacé après la manifestation.
- **Règles du module** : jamais la position ni les mouvements des forces de l'ordre ; position et gravité **seulement** aux medics certifiés concernés, chiffrées, effacées automatiquement après la clôture (délai à fixer, par exemple 2 heures), sans jamais permettre de reconstituer qui était où ; PPF ne remplace jamais le 15 ou le 112 ; PPF met en relation, elle ne soigne pas.

## 2. Principes retenus pour la conception

1. **Le serveur route, il ne sait pas.** Le serveur relaie des messages chiffrés de bout en bout entre la personne en détresse et les medics ; il ne connaît que la **maille grossière** nécessaire au routage (une case géographique d'environ 5 km, geohash de 5 caractères), jamais la position exacte ni la gravité.
2. **Rien sur disque.** Les alertes, les présences des medics et les compteurs de fausses alertes vivent dans la **mémoire vive** du serveur, avec une durée de vie bornée ; aucune table PostgreSQL, aucun journal applicatif de contenu, aucune adresse IP journalisée (règle du dépôt). Un redémarrage du serveur efface tout, et c'est voulu.
3. **Clés éphémères partout.** Chaque alerte est signée et chiffrée par une **clé créée pour la manifestation** (l'« identifiant anonyme temporaire » d'AG-41), jamais par la clé du compte. Un medic « disponible » se présente par une clé de session tirée de sa clé de compte (voir 3.3), changée à chaque manifestation.
4. **L'intelligence sur les appareils.** Le regroupement à 15 m et 2 min, la distance « un medic arrive, à 150 m », les zones du tableau de coordination se calculent **sur les appareils des medics et des coordinateurs**, qui sont les seuls à voir les positions exactes.
5. **Le 15 et le 112 d'abord**, à chaque niveau : la gravité 3 ouvre l'appel au 15 avant même d'envoyer l'alerte ; l'alerte part en parallèle, jamais à la place.

## 3. Certification des street medics (AG-28, S-88)

### 3.1 Ce qui est certifié

Une **attestation** : « la clé `p` est un street medic de niveau N, certifié par l'organisation O, valable jusqu'au JJ/MM/AAAA ». Pas de nom, pas de diplôme, pas de numéro RPPS, pas de lieu ni de date de la séance (S-88 : « aucun lieu, aucun événement, aucune identité n'est noté »). Le niveau est **déclaré** par le medic puis **confirmé** par le certificateur, qui a vu la preuve sans la garder.

| Niveau | Exemples (à valider avec les organisations) |
|---|---|
| 1 | PSC1, premiers secours citoyens |
| 2 | PSE1, PSE2, secouriste associatif |
| 3 | Infirmier, aide-soignant, ambulancier |
| 4 | Médecin, sage-femme |
| 5 | Médecin urgentiste, anesthésiste, chirurgien |

### 3.2 Qui certifie

Un **représentant certifié** d'une organisation de street medics **vérifiée** de l'annuaire (type `street-medics`, M3). Il est lui-même un validateur au sens de S-88 (séances limitées par un quota, back-office du Directeur technique, D-126). La certification se fait **en personne** : le medic montre le code QR de sa clé ; le représentant scanne (lecture de code QR sans service Google, comme S-88) et signe.

### 3.3 Où vit l'attestation : trois options

| Option | Fonctionnement | Avantages | Inconvénients |
|---|---|---|---|
| A. Attestation publique | Événement signé par le certificateur, publié comme les autres contenus (par exemple type 30078 avec `p` du medic) | Simple ; vérifiable par tous ; révocation par republication | **Révèle qui est street medic** (risque de ciblage, d'intimidation, de recoupement avec les participations) ; contraire au principe 10 |
| B. Registre côté serveur | Le serveur garde la liste des clés certifiées (table chiffrée) | Le serveur peut filtrer seul les medics | Liste nominative de fait sur le serveur ; saisie ou fuite = liste des medics ; une table de plus |
| C. **Attestation gardée par le medic** (recommandée) | L'attestation signée est remise au medic (dans le code QR retour ou par la messagerie chiffrée) et **reste sur son appareil** ; il la présente au serveur seulement quand il se déclare « disponible », dans une session en mémoire vive | Aucune liste sur le serveur ni en public ; le serveur vérifie la signature du certificateur et l'oublie à la fin de la session ; révocation possible | Révocation à diffuser : une **liste de révocation** publique ne contient que des empreintes d'attestations, pas de clés ; un medic qui change d'appareil doit refaire certifier ou restaurer sa sauvegarde chiffrée |

**Recommandation : C.** Durée de validité de l'attestation : un an (à valider avec les organisations). L'attestation porte le niveau ; le serveur ne le garde que pendant la session pour router selon la gravité (AG-29).

### 3.4 Coordinateurs

Même mécanisme, avec un rôle « coordinateur » dans l'attestation, accordé par l'organisation pour une manifestation précise ou durablement. Le coordinateur a une **clé de coordination de la manifestation**, créée pour l'occasion, dont la clé publique est donnée aux medics de la manifestation au moment où ils se déclarent disponibles.

## 4. Transport des alertes

### 4.1 Trois options

| Option | Fonctionnement | Avantages | Inconvénients | Coût |
|---|---|---|---|---|
| A. Serveur en clair | L'alerte (position, gravité) part en HTTPS ; le serveur choisit les medics proches et la leur transmet | Le plus simple | **Le serveur connaît qui était où et blessé comment** ; une saisie ou une fuite reconstitue tout ; contraire à la règle du module | Aucun |
| B. **Relais aveugle par cases** (recommandée) | Le serveur ne voit qu'une case d'environ 5 km et des messages chiffrés pour chaque medic de la case (NIP-44, comme la messagerie) ; position exacte et gravité dans le chiffré | Le serveur ne sait ni la position exacte ni la gravité ; rien sur disque ; réutilise NIP-44, NIP-59 et la connexion temps réel du relais (D-027) | Un message par medic de la case (quelques dizaines au plus) ; le serveur voit le nombre d'alertes par case (accepté : c'est aussi ce qu'affiche la coordination) | Aucun ; mémoire vive modeste |
| C. Pair à pair (Bluetooth) | L'alerte passe de téléphone en téléphone sans serveur | Marche sans réseau saturé | Portée et fiabilité faibles ; c'est AG-39, prévu en phase 4 avec la messagerie hors ligne | Aucun ; long à faire |

**Recommandation : B maintenant, C en complément en phase 4 (AG-39).**

### 4.2 Déroulé avec l'option B

1. **Un medic se déclare disponible** pour une manifestation : son appareil ouvre une connexion temps réel (WebSocket du relais, D-027), présente son attestation (3.3, option C) signée par sa clé de session, et s'abonne à la case de sa position (et aux huit cases voisines, pour les bords). Le serveur garde en mémoire : clé de session, niveau, cases, « disponible » ou « occupé ». Il **ne reçoit pas** la position exacte du medic.
2. **La personne appuie sur AIDE, puis « Alerter les street medics »**, choisit la gravité. Gravité 3 : l'application propose **d'abord** l'appel au 15 ou au 112, puis envoie l'alerte. Sans compte : la clé éphémère de la manifestation suffit.
3. L'appareil demande au serveur la **liste des clés de session des medics disponibles de sa case** (et des voisines). Il chiffre pour chacun un message (NIP-44 v2, enveloppe NIP-59 signée par une clé à usage unique) contenant : position exacte, précision annoncée par le téléphone, gravité, heure, une courte description facultative, la clé éphémère de réponse. Il chiffre le même message pour la **clé de coordination** de la manifestation.
4. Le serveur relaie les enveloppes aux destinataires connectés ; un medic non connecté reçoit un **réveil sans contenu** par ntfy (D-078).
5. Un medic répond « J'y vais » (AG-29) : sa réponse, chiffrée pour la clé éphémère de la personne, contient sa distance calculée sur son appareil (arrondie à 50 m). La personne voit « un medic arrive, à 150 m », jamais la position du medic (décision du 4 novembre). Au cinquième « J'y vais » (réglable par gravité), le serveur, qui voit passer les réponses sans les lire grâce à une étiquette en clair « prise en charge », cesse de relayer l'alerte aux autres et la marque « prise en charge ».
6. **Clôture** par le medic (« situation gérée », « transmis au 15 », « fausse alerte », « rien à signaler ») ou par la personne (« je n'ai plus besoin d'aide »). Le serveur oublie l'alerte immédiatement ; les appareils effacent position et gravité **2 heures après la clôture** au plus tard (délai proposé, question 2), et toute alerte non close **6 heures après son envoi**.

### 4.3 Ce qui empêche de reconstituer qui était où

- Clé éphémère par manifestation pour la personne, clés de session pour les medics : rien ne relie une alerte à un compte ni deux manifestations entre elles.
- Le serveur ne voit que des cases de 5 km et des chiffrés ; ni position exacte, ni gravité, ni adresse IP journalisée ; tout en mémoire vive, effacé à la clôture, et au plus tard à la fin de la manifestation.
- Les appareils des medics effacent les positions après le délai ; le tableau de coordination n'a **aucun historique** enregistré (une capture d'écran reste possible : à dire dans l'engagement signé par les medics).
- Aucune sauvegarde du serveur ne contient ces données (rien sur disque).
- Le constat de nasse (AG-46) et les statistiques de l'observatoire n'utilisent jamais ces alertes ; un éventuel bilan public se limite à des nombres par manifestation, sans lieu ni heure fine (à décider plus tard).

### 4.4 Regroupement à 15 m et 2 min (AG-41)

Fait **sur les appareils des medics et des coordinateurs**, seuls à connaître les positions : deux alertes à moins de 15 m (réglable) et 2 min (réglable) forment un groupe ; la plus grave est l'alerte principale, les autres sont des petits points rouges clignotants. Les réglages sont choisis par la coordination et transmis chiffrés aux medics de la manifestation. Le serveur, qui ne voit pas les positions, ne regroupe rien : il relaie chaque alerte ; ce sont les appareils qui affichent le regroupement et les medics qui valident (« nouvel appel ») ou invalident avec un motif.

### 4.5 Fausses alertes

- Seul le motif **« fausse alerte »** compte (décision du 4 novembre). L'invalidation, envoyée par le medic, porte la clé éphémère de l'alerte ; le serveur tient, **en mémoire vive et pour la seule manifestation**, un compteur par clé éphémère.
- Au-delà d'un seuil (proposé : 3 fausses alertes et plus de la moitié des alertes de la clé ; réglable, gardé dans la configuration privée hors dépôt), les alertes de cette clé partent **à la seule coordination**, avec la mention « à vérifier ». Jamais de refus.
- Limite connue : une personne malveillante peut créer une nouvelle clé éphémère. La protection repose alors sur une **preuve de travail légère** (type ALTCHA, calculée en arrière-plan en moins d'une seconde dès l'ouverture d'AIDE, jamais une question posée à la personne) et sur la limitation de débit par case, qui ne doit jamais bloquer une vraie urgence (seuil large, alerte gardée pour la coordination plutôt que perdue).
- L'IA de modération ne lit jamais les alertes (elles sont chiffrées).

## 5. Tableau de coordination (AG-30)

- Réservé aux coordinateurs (et, en lecture réduite, aux medics de la manifestation).
- Les medics disponibles envoient **leur position exacte chiffrée pour la clé de coordination** (toutes les 30 s quand ils bougent, réglable), jamais au serveur en clair.
- Le tableau affiche, sur la carte de la manifestation (vue carte de l'agenda, D-046, fonds hors ligne) : medics disponibles ou occupés avec leur niveau, alertes par gravité (couleur **et** pictogramme et texte, WCAG), groupes d'alertes, zones qui concentrent les alertes, zones sans medic à moins de N mètres (calcul sur l'appareil).
- **Jamais** de position ni de mouvement des forces de l'ordre, ni de champ pour en saisir (règle du module).
- Rien n'est enregistré ; l'écran s'efface à la fin de la manifestation ou à la déconnexion.

## 6. Ce qu'il faut côté serveur et infrastructure

1. **Ktor** : un module `server/.../secours/` avec une route WebSocket (déjà utilisée par le relais de la messagerie, D-027) pour les medics, les coordinateurs et les personnes en attente de réponse ; une route pour lister les clés de session d'une case ; vérification des attestations (signature du certificateur, liste de révocation) ; **aucune migration Flyway** pour les alertes (mémoire vive). Seule la liste de révocation des attestations, publique et faite d'empreintes, pourrait être un contenu signé ordinaire.
2. **Mémoire** : quelques kilo-octets par medic connecté et par alerte ; même 2 000 medics et 500 alertes simultanées tiennent largement dans le budget du serveur de 4 Go (cahier des charges, section 3.5). Limites fixes en configuration pour éviter qu'un afflux artificiel ne sature la mémoire.
3. **Caddy** : passage des WebSocket (déjà prévu pour le relais) ; aucun journal d'accès contenant l'adresse IP (règle du dépôt, à vérifier dans la configuration du Directeur technique).
4. **ntfy** (D-078) : réveil sans contenu des medics disponibles dont l'application dort ; sur iOS, APNs n'envoie qu'un signal de réveil, jamais de contenu (règle du dépôt).
5. **Back-office** (Directeur technique, D-120 à D-127) : séances de certification des medics sur le modèle des séances des validateurs (D-126) ; révocation d'une attestation ; journal d'audit des certifications et révocations (sans clé du medic en clair, empreinte seulement).
6. **Préproduction** : tests de charge avec des medics et des alertes fictifs [EXEMPLE] ; jamais de position réelle.
7. **Hôtes contactés** : aucun nouveau (le serveur PPF seul, ntfy auto-hébergé).

## 7. Côté application

- Écran « Alerter les street medics » dans la feuille AIDE (aujourd'hui annoncé « bientôt ») : trois gros boutons de gravité, texte et pictogramme ; gravité 3 ouvre d'abord le 15 ; puis « Alerte envoyée à N medics », « Un medic arrive, à 150 m », « Je n'ai plus besoin d'aide ».
- Permission de localisation demandée **au moment de l'alerte seulement**, jamais avant ; refus possible : la personne décrit alors le lieu en mots (champ libre), et l'alerte part quand même.
- Mode medic (compte requis et attestation) : « Je suis disponible pour cette manifestation », « Occupé », liste et carte des alertes, « J'y vais », clôture avec motif.
- Tout le code partagé reste compatible iOS ; la localisation passe par `expect`/`actual`.

## 8. Cadre légal et risques à faire relire

- **Données de santé** : la gravité d'une blessure liée à une position et à une heure est très probablement une donnée concernant la santé (RGPD, article 9), même si elle n'est pas couverte par le secret médical (position de la propriétaire, point d'attention n° 17). Base envisagée : la sauvegarde des intérêts vitaux de la personne (article 9, paragraphe 2, point c) ou son consentement explicite. Une **analyse d'impact (AIPD)** paraît obligatoire avant le lancement. À faire valider par l'avocat.
- **Responsabilité** : PPF met en relation, elle ne soigne pas ; conditions d'utilisation et engagement signé par les medics (point d'attention n° 17).
- **Ce que la loi interdit de montrer** : la position des contrôles et des forces de l'ordre n'est jamais affichée (point d'attention n° 14, article L130-11 du Code de la route pour les applications de navigation, par analogie et par décision de la propriétaire).
- **Saisie d'un téléphone de medic** : il contient les positions des alertes en cours et des 2 dernières heures ; le verrouillage de l'application (S-43) et l'effacement rapide (S-44) doivent être prêts avant cette étape.

## 9. Questions (à transmettre par la session principale au chef d'équipe), avec recommandations

1. **Où vit l'attestation d'un medic** (3.3) ? Recommandation : **C**, gardée par le medic, présentée seulement pendant ses sessions de disponibilité ; liste de révocation publique faite d'empreintes.
2. **Délai d'effacement** des positions et gravités après la clôture sur les appareils ? Recommandation : **2 heures**, et 6 heures au plus pour une alerte jamais close.
3. **Transport** (4.1) ? Recommandation : **B**, relais aveugle par cases d'environ 5 km, messages NIP-44 par medic ; Bluetooth en phase 4.
4. **Taille de la case** : recommandation geohash à 5 caractères (environ 5 km sur 5 km) plus les huit voisines ; plus petite, le serveur en saurait davantage ; plus grande, trop de messages par alerte.
5. **Seuil des fausses alertes** : recommandation 3 fausses alertes et plus de la moitié des alertes d'une clé, dans la configuration privée ; jamais de refus, seulement « à vérifier » vers la coordination (décision du 4 novembre).
6. **Nombre de répondants par gravité** : recommandation 2 pour la gravité 1, 3 pour la gravité 2, 5 pour la gravité 3, réglable par la coordination.
7. **Échelle des niveaux** (tableau 3.1) : à valider avec les organisations de street medics.
8. **Qui peut certifier** : recommandation, uniquement les représentants désignés d'une organisation de type `street-medics` vérifiée, avec un quota par séance (S-88).
9. **Prérequis** : recommandation de livrer S-43 (verrouillage) et S-44 (effacement rapide) avant le mode medic, et de faire relire par l'avocat le dispositif complet (dont l'AIPD) avant toute ouverture publique.

## 10. Ordre de réalisation proposé

1. S-43 et S-44 (sécurité de l'appareil), déjà prévus juste après cette étape.
2. Certification en personne (après les séances des validateurs du back-office, D-126).
3. Relais aveugle et mode medic (AG-27 complet, AG-29), avec le regroupement sur les appareils (AG-41).
4. Tableau de coordination (AG-30), sur la carte de la manifestation en direct (AG-24).
5. Relais Bluetooth (AG-39, phase 4).
