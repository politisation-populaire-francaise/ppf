# Registre des données (RGPD)

Ce registre liste exactement ce qui est stocké, pourquoi, où, combien de temps et qui y accède. Toute nouvelle donnée doit y être inscrite avant d'être codée (CLAUDE.md, section 7). Les opinions politiques sont des données sensibles (RGPD, article 9) : la plateforme ne relie jamais un vote, un soutien ou un engagement à une identité réelle.

Dernière mise à jour : 5 novembre 2026 (phase 0 et début de la phase 1 : compte sur l'appareil).

## 1. Données traitées par la plateforme

Rien n'est encore en ligne : aucun serveur ne tourne et les applications ne font encore aucun appel réseau. Le serveur ne tient aucun compte et n'enregistre aucune adresse IP (ni dans le proxy, ni dans l'application). Voici ce que le serveur stockera dès qu'il sera déployé, tel que le code le prévoit au 5 novembre 2026.

| Donnée | Finalité | Où | Durée | Accès |
|---|---|---|---|---|
| Événements signés publiés : contenu, type, étiquettes, date déclarée par l'auteur, clé publique de l'auteur (pseudonyme), signature | Publier et distribuer les contenus de la plateforme ; prouver leur authenticité | Table `events` de PostgreSQL, sur le serveur | Tant que le contenu est publié ; une version plus récente remplace l'ancienne ; suppression sur demande de l'auteur (type 5) ou décision de modération, à coder | Public (c'est le but d'une publication) |
| Date de réception par le serveur (`received_at`) | Ordonner la synchronisation et détecter les rafales | Même table | Même durée | Équipe technique |

Ce que le serveur ne stocke pas : adresse IP, appareil, langue, heure de connexion, lecture des contenus. La clé publique est un pseudonyme choisi par l'appareil, jamais relié à une identité réelle par la plateforme. Les contenus sensibles (témoignages, signalements) auront leur propre ligne, chiffrée, quand ils seront codés.

## 1 bis. Données sur l'appareil de l'utilisateur (depuis le 5 novembre 2026)

Les applications stockent sur l'appareil le compte et, depuis le 7 novembre 2026, la liste des comptes suivis. Aucune de ces données ne quitte l'appareil comme telle ; le serveur ne les reçoit jamais. Qui l'on suit peut révéler une appartenance politique ou syndicale (donnée sensible, RGPD article 9) : c'est pourquoi cette liste reste sur l'appareil (S-120, AG-22, point d'attention n° 32).

| Donnée | Finalité | Où | Durée | Accès |
|---|---|---|---|---|
| Clé secrète de l'identité (32 octets) | Signer les contenus et prouver que l'on est l'auteur de son compte | Android : stockage privé de l'application, chiffré par une clé AES-256 du Keystore (puce sécurisée quand elle existe), exclu des sauvegardes ; ordinateur : fichier `identity.json` dans le dossier de données privé de l'utilisateur (chiffrement par code à venir, S-43) ; iOS : en mémoire seulement tant que le trousseau n'est pas implémenté | Jusqu'à la déconnexion (effacement) | L'utilisateur, par l'application |
| Phrase de récupération (12 mots) et sa langue | Retrouver son compte sur un autre appareil ; pouvoir la réafficher si elle a été mal notée | Avec la clé secrète, même protection ; absente si le compte a été importé par sa clé « nsec » | Jusqu'à la déconnexion | L'utilisateur, après un avertissement |
| Clé publique (pseudonyme technique) | Identifier l'auteur d'un contenu signé | Dérivée de la clé secrète ; sera jointe à chaque contenu publié, donc visible publiquement avec lui | Tant que des contenus signés existent | Public, pour les contenus publiés |
| Liste des comptes suivis : clé publique (pseudonyme) de chaque compte suivi et date du suivi | Assembler la vue « Suivis » du fil sur l'appareil ; demander au serveur les publications de ces comptes | Table `follow` de la base locale SQLite de l'application (Android : stockage privé ; ordinateur : fichier `ppf.db` du dossier de données privé ; iOS : bac à sable) | Jusqu'à ce que l'utilisateur cesse de suivre le compte, efface ses clés de l'appareil (la liste est alors effacée avec elles) ou désinstalle l'application | L'utilisateur, par l'application. Jamais transmise comme liste : le serveur ne voit que des demandes de publications par lots d'au plus 20 clés en ordre aléatoire, et la clé seule du compte que l'on vient de suivre ; aucune adresse IP journalisée |
| Réactions « J'aime » | Mémoriser ce que l'on a aimé | En mémoire seulement, le temps de la session (prototype) | Jusqu'à la fermeture de l'application | L'utilisateur |

La clé publique est un pseudonyme : elle ne contient aucune information sur la personne, mais elle relie entre eux les contenus d'un même compte. L'écran du compte le dit à l'utilisateur ; il pourra créer plusieurs identités.

## 2. Données tenues par des tiers pour le projet

| Donnée | Finalité | Responsable et lieu | Durée | Accès |
|---|---|---|---|---|
| Identité de la titulaire des noms de domaine (nom, adresse, téléphone, e-mail) | Obligation des registres pour enregistrer un domaine | Hostinger (Lituanie) et registres (.org, .fr, .com, .eu, .net, .app) ; masquée dans l'annuaire public | Tant que les domaines sont détenus ; transfert à l'association à sa création | Hostinger, registres, autorités sur réquisition |
| Facturation Hostinger | Paiement des domaines, puis du serveur | Hostinger | Durée légale de conservation des factures | Hostinger, propriétaire |
| Compte GitHub de la propriétaire et dépôt public | Hébergement du code, intégration continue | GitHub (États-Unis), dépôt public | Tant que le projet existe | Public pour le code ; propriétaire pour le compte |
| Auteur des commits | Traçabilité du code | Dans l'historique Git public : nom « PPF » et adresse « noreply » fournie par GitHub, jamais l'adresse personnelle | Permanent (historique public) | Public |
| Journaux de l'intégration continue | Diagnostic des compilations | GitHub Actions | 90 jours (réglage GitHub) | Public (dépôt public) |

## 3. Données sur l'ordinateur de la propriétaire

| Donnée | Finalité | Où | Durée | Accès |
|---|---|---|---|---|
| Clé de signature de test des APK | Installer les versions de test sur le téléphone, toujours avec la même signature | Hors dépôt, dossier `.ppf` du profil utilisateur | Tant que des versions de test circulent | Propriétaire |
| Clé SSH de signature des commits | Prouver l'origine des commits | Hors dépôt, dossier `.ssh` du profil utilisateur | Révocable à tout moment sur GitHub | Propriétaire |
| Clé de signature de release (future) | Publier les applications | Hors dépôt et hors ligne | Permanent | Propriétaire |

## 4. Préproduction

La préproduction ne contient jamais de données réelles : uniquement des données fictives marquées [EXEMPLE].
