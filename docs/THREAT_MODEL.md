# Modèle de menaces, première version

Version du 5 novembre 2026 (phase 0, complétée le soir même par les fondations de la phase 1 : identités et signature). Ce document est tenu à jour à chaque phase. Il part des menaces minimales listées par le cahier des charges (section 5.5) et indique, pour chacune, ce qui est déjà en place et ce qui est prévu.

## 1. Ce que l'on protège

1. **Les personnes** : utilisateurs, contributeurs, membres de l'équipe, organisations partenaires. En particulier : leurs opinions politiques, leurs relations, leur localisation, leur identité.
2. **L'intégrité de l'information** : chiffres, faits sourcés, votes, engagements des candidats ; ils doivent être exacts et impossibles à falsifier discrètement.
3. **La disponibilité** : l'information doit survivre à la coupure du serveur, du nom de domaine ou au retrait des magasins d'applications.
4. **Le projet lui-même** : le code, les comptes (GitHub, Hostinger), les clés de signature, la réputation et la légalité de l'association.

## 2. Qui peut attaquer

- Des **acteurs étatiques ou judiciaires** : réquisitions, saisie, blocage, suspension du domaine.
- Des **groupes politiques hostiles** : harcèlement, désinformation, manipulation des votes, tentative de faire passer la plateforme pour partisane.
- Des **criminels et des spammeurs** : piratage, rançon, faux comptes, déni de service.
- Des **personnes de l'intérieur** : membre de l'équipe malveillant ou compte compromis, contributeur de code malveillant.
- La **chaîne d'approvisionnement** : dépendances logicielles compromises, actions d'intégration continue détournées.

## 3. Menaces, protections en place et protections prévues

| Menace | En place (phase 0) | Prévu |
|---|---|---|
| Réquisition judiciaire des données | Aucune donnée d'utilisateur n'existe ; aucune adresse IP journalisée (proxy et serveur) ; registre des données | Minimisation stricte, comptes par paires de clés, graphe social sur les appareils, votes avec jetons anonymes, chiffrement des contenus sensibles |
| Saisie du serveur | Infrastructure décrite en code, redéployable ailleurs | Sauvegardes chiffrées chez un autre prestataire, restauration testée, centre de données hors de France |
| Suspension du nom de domaine | Six extensions achetées, verrou de transfert, renouvellement automatique | Miroirs statiques, Nostr, adresse .onion, application installable hors des magasins |
| Retrait des magasins d'applications | APK installable directement et dans les releases | F-Droid, partage de l'APK entre téléphones |
| Déni de service (DDoS) | Proxy Caddy, limites de ressources des conteneurs | Limitation de débit, preuve de travail auto-hébergée, miroirs statiques |
| Pollution de l'API des événements (spam, contenus énormes) | Signature obligatoire et vérifiée, 64 Ko au plus par événement, types éphémères refusés, une seule version par contenu remplaçable ; API non déployée | Avant toute mise en ligne : limitation de débit, preuve de travail (ALTCHA), règles de publication par rôle, file de modération |
| Piratage du serveur | Conteneurs sans privilège et en lecture seule, secrets hors dépôt, en-têtes de sécurité, HTTPS et HSTS | Durcissement du serveur (pare-feu, SSH par clé, mises à jour automatiques), OWASP ASVS, audit externe avant le lancement |
| Piratage des comptes du projet (GitHub, Hostinger) | Double authentification sur Hostinger ; commits signés ; détection des secrets | Double authentification obligatoire sur GitHub (à activer), clé de sécurité physique pour les super-administrateurs |
| Faux comptes et manipulation des chiffres | Sans objet en phase 0 | Niveaux de confiance, parrainage, validation en personne, détection des rafales, résultats transparents (S-86 à S-91) |
| Reconstitution de la liste des comptes suivis par le serveur | L'appareil ne transmet jamais la liste : il demande les publications par lots d'au plus 20 clés en ordre aléatoire, et la clé seule d'un compte au moment où il est suivi ; les lots ne partent jamais dans la même passe qu'une publication signée de l'utilisateur ; aucune adresse IP journalisée. Limite acceptée : une passe de lots part sur la même connexion dans la même seconde, un serveur malveillant peut donc corréler les lots d'un appareil anonyme sans pouvoir les relier à une personne ni à un compte ; la corrélation temporelle entre une publication et une demande de clé isolée faite peu après reste possible | Circuits distincts par lot (Tor, phase 3), relais Nostr |
| Inondation de la boîte d'enveloppes d'un destinataire (messagerie) | Au plus 1000 enveloppes par destinataire et 30 jours de conservation ; plafond global de publications par minute ; quota par clé après vérification de signature. Limite acceptée : un attaquant peut pousser les vrais messages d'une personne hors de sa boîte en lui envoyant 1000 enveloppes, et quiconque peut compter les enveloppes adressées à une clé | Preuve de travail auto-hébergée avant toute publication, authentification des lecteurs (NIP-42) |
| Inondation du serveur par des clés à usage unique | Plafond global de 600 publications par minute (dégradation de service assumée pendant une inondation), 64 Ko et 1000 étiquettes au plus par événement | Preuve de travail, niveaux de confiance |
| Manipulation des votes | Sans objet en phase 0 | Méthode « une personne, une voix » à valider avant le mur des revendications |
| Désinformation injectée | Format de contenu signé (événement NIP-01, Schnorr BIP-340) et vérification écrits et testés sur les vecteurs officiels | Sources obligatoires, double validation, modération |
| Contenu éditorial publié par un auteur non autorisé | L'application n'affiche que les contenus d'une liste d'auteurs de confiance ; la clé des contenus d'exemple est gardée hors dépôt | Liste d'auteurs signée par la clé de la plateforme et distribuée comme un événement ; retrait de la clé d'exemple avant le lancement ; côté serveur, règles de publication par rôle |
| Faux contenu attribué à PPF ou à un auteur | Toute mise en œuvre du port de publication refuse un événement dont l'identifiant ou la signature ne correspond pas | Vérification systématique à la réception et mention « contenu authentique PPF » dans l'application ; clé de la plateforme dans un service de signature séparé, jamais en clair sur le serveur principal ; rotation et révocation |
| Vol ou perte de l'appareil d'un utilisateur (clés) | Android : clé chiffrée par une clé AES-256 du Keystore (matériel quand il existe), fichier exclu des sauvegardes ; ordinateur : fichier dans le dossier privé de l'utilisateur, protégé par la session seulement ; les clés ne sont jamais envoyées | Chiffrement par code sur ordinateur et verrouillage de l'application (S-43) avant le lancement public ; trousseau iOS ; effacement rapide (S-44) |
| Perte de la phrase de récupération | Trois mots demandés à la création pour vérifier la copie ; phrase réaffichable après avertissement ; avertissement explicite avant la déconnexion | Message clair dans les conditions d'utilisation : aucun serveur ne peut rendre un compte perdu |
| Lecture de l'écran par un tiers (phrase, clé secrète) | Secrets masqués par défaut, affichés seulement après un avertissement et masquables en un geste | Masquage automatique après un délai ; capture d'écran bloquée sur Android pour ces écrans |
| Phrase ou clé devinée | Aléa pris uniquement au générateur sécurisé du système ; 128 bits pour la phrase, 256 bits pour la clé | Aucun mot de passe faible n'entre jamais dans la dérivation |
| Infiltration de l'équipe | Une seule personne (la propriétaire) | Permissions minimales, validation à deux, journal d'audit, coupure d'urgence (S-56 à S-63) |
| Contribution de code malveillante | Dépôt public avec demandes de fusion obligatoires, tests, relecture, vérification des tiers et des secrets | Relecture ligne par ligne des contributions externes, signature obligatoire des commits, verrouillage des dépendances |
| Dépendance compromise | Versions verrouillées (fichiers de verrouillage), catalogue de versions, Dependabot, vérification des groupes interdits | Vérification des empreintes des dépendances, surveillance des avis de sécurité |
| Plateforme présentée comme partisane | Charte de contenu, règles des outils électoraux (cahier des charges, section 9.2) | Traitement identique dans les outils électoraux (décision en attente), relecture juridique |
| Fuite de données personnelles de la propriétaire | Auteur des commits sous pseudonyme avec adresse « noreply » ; coordonnées masquées dans l'annuaire des domaines | Association titulaire des domaines et des comptes |

## 4. Hypothèses

- La propriétaire est la seule personne ayant accès aux comptes et aux clés en phase 0 ; chaque accès est protégé par un mot de passe unique et, dès que possible, par une double authentification.
- Le code est public : la sécurité ne repose jamais sur le secret du code, uniquement sur les clés, les secrets de configuration et l'architecture.
- GitHub et Hostinger sont des tiers qui peuvent couper le service ; l'architecture de résilience (phase 3) et les sauvegardes hors site (premier déploiement) répondent à ce risque.
