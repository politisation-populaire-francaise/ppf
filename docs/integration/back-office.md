# Contrat du back-office (socle M15 et M16), version 1.1

Rédigé le 7 novembre 2026 (version 1.1 le même soir : lignes des files confirmées par les pôles) par la session « Directeur Technique PPF » (branche `feature/backoffice`), à la demande du chef d'équipe « Team lead [Voir] ». Il fixe ce que tous les pôles partagent pour brancher leurs files de relecture et de décision sur un seul espace de l'équipe, sans conflit. Le code du socle suit ce contrat ; un pôle qui a besoin d'autre chose l'écrit au Directeur technique avant de coder.

- Numéros : décisions D-120 à D-129 ; migration serveur `V8__back_office.sql`.
- Sources : cahier fonctionnel, section 1 (rôles) et modules M15, M16 ; cahier des charges, section 5 (« double authentification obligatoire (TOTP ou clé de sécurité) pour tous les comptes de l'équipe », « rôles et permissions minimales », « journal d'audit de toutes les actions de modération et d'administration »), section 9.4 (DSA, PHAROS) ; inventaire du socle, bloc A14 (S-56 à S-64, S-115), liste validée le 4 novembre 2026 ; point d'attention n° 8 (deux super-administrateurs avec clé physique, décidé le 4 novembre 2026) ; décisions de la propriétaire transmises le 7 novembre 2026 (votants à trois niveaux, validation en personne par des bénévoles agréés par PPF via leur association).

## 1. Périmètre

| Étape | Contenu | Qui |
|---|---|---|
| 1. Socle | Accès réservé, comptes de l'équipe par invitation, connexion par clé d'accès (passkey) ou clé de sécurité, sessions, rôles et permissions, accès temporaires, validation à deux, coupure d'urgence, journal d'audit inaltérable, tableau de bord, point de branchement des files, première file branchée (relecture d'« Agir ») | Directeur technique |
| 2. Signalements et DSA | Réception des signalements (tout utilisateur, organisations signaleuses de confiance, alertes de l'IA), file de modération, décision motivée, recours examiné par une autre personne, transmission à PHAROS tracée, rapport de transparence public | Directeur technique |
| 3. Files des pôles | Chaque pôle branche ses files (section 7) | Pôles |
| 4. Validateurs et certificateurs | Rôles, séances et clés de séance (section 8) ; la logique des urnes reste à « Décider » | Socle : Directeur technique ; urnes : Décider |

Hors de ce contrat : groupes et permissions configurables sans code (S-58, S-59 complets : le socle livre des rôles fixes et un champ de périmètre réservé), notifications de l'équipe sur téléphone (S-115 : viendront avec ntfy), éditeur de contenus sans code (CMS), statistiques.

## 2. Principes

1. **Personne n'entre sans y être invité**, et chaque compte a sa double authentification dès sa création.
2. **Aucun mot de passe** : la connexion se fait par une **clé d'accès** (passkey : Windows Hello, téléphone, gestionnaire de mots de passe) ou une **clé de sécurité physique** (FIDO2), avec vérification de la personne (code ou empreinte) exigée. Une clé d'accès vaut à elle seule deux facteurs (l'appareil et le code) et ne peut pas être hameçonnée. C'est plus fort que le « TOTP » cité par le cahier des charges, qui reste écarté (D-120).
3. **Permissions minimales** : chaque action exige une permission précise ; un rôle n'en donne que ce qu'il faut.
4. **Tout est tracé** : chaque connexion, chaque décision, chaque changement de rôle entre dans un journal que personne ne peut modifier ni effacer, pas même un administrateur.
5. **Vie privée de l'équipe et des citoyens** : aucune adresse IP, aucun navigateur, aucune adresse e-mail obligatoire ; un membre de l'équipe est un pseudonyme ; le back-office ne montre d'un citoyen que ce que la décision demande (le contenu en cause, sa clé publique pseudonyme).
6. **Décision humaine toujours** (DSA, cahier des charges 5.6) : l'IA pré-signale, l'équipe décide ; chaque décision est motivée et peut faire l'objet d'un recours examiné par une autre personne.
7. **Un seul chemin** : les pôles ne créent pas leurs propres pages d'administration ; ils fournissent une file au socle (section 7). Les routes `/dev/...` du serveur de développement restent pour le travail local seulement.

## 3. Accès

- Adresses : `https://equipe.politisation-populaire-francaise.org` (production) et `https://equipe.preprod.politisation-populaire-francaise.org` (préproduction), certificats Let's Encrypt comme la préproduction (D-071).
- **Version 1 : uniquement à travers le tunnel WireGuard** de l'équipe, en production comme en préproduction (le proxy ferme toute autre connexion, comme pour la préproduction). Chaque membre reçoit un profil WireGuard avec son invitation. Question à la propriétaire, section 12.
- Le serveur ouvre les routes du back-office sur **un second port interne** (8081), jamais sur le port public (8080) : une erreur de routage du site public ne peut pas exposer le back-office. Caddy ne transmet au port 8081 que l'adresse `equipe.…`.
- Pages rendues par le serveur (kotlinx.html), lisibles au clavier et au lecteur d'écran (RGAA), sur ordinateur et téléphone, modes clair et sombre. Un seul script, auto-hébergé, pour la cérémonie de la clé d'accès (l'API WebAuthn du navigateur n'existe qu'en JavaScript) ; tout le reste fonctionne sans JavaScript. En-têtes : ceux du site, plus `Cache-Control: no-store`.

## 4. Comptes de l'équipe

- **Création par invitation seulement** (S-57) : un administrateur crée une invitation (pseudonyme, rôles proposés) ; le serveur donne un lien à usage unique, valable 48 heures (jeton aléatoire de 256 bits, seule son empreinte est gardée). La personne ouvre le lien à travers le tunnel, enregistre au moins une clé d'accès ; une **deuxième clé** (de secours) est demandée avant toute permission sensible.
- **Premier compte** : créé sur le serveur par la commande `ppf-backoffice bootstrap` (lien d'invitation affiché une seule fois, rôle super-administrateur), puisqu'il n'existe encore personne pour inviter.
- **Super-administrateurs** (S-56, point d'attention n° 8) : deux au minimum, chacun avec une **clé de sécurité physique** ; tant qu'elles ne sont pas achetées, une clé d'accès de l'ordinateur ou du téléphone de la propriétaire.
- Données d'un compte : identifiant interne, pseudonyme, clé publique Nostr facultative (pour relier le compte à l'identité qui signe les contenus éditoriaux), date de création, compte qui a invité, état (actif, suspendu, retiré). Pour chaque clé d'accès : identifiant et clé publique de l'authentificateur, compteur de signatures, nom donné par la personne, dates de création et de dernier usage.
- **Sessions** : cookie `__Host-ppf-equipe` (sécurisé, inaccessible au JavaScript, `SameSite=Strict`) ; seule l'empreinte du jeton est gardée ; 30 minutes sans activité ou 8 heures au plus ; jeton anti-falsification dans chaque formulaire ; **nouvelle vérification de la clé d'accès** pour toute action sensible si la dernière date de plus de 5 minutes.
- **Coupure d'urgence** (S-62) : un administrateur (ou la personne elle-même) coupe en un geste toutes les sessions et toutes les permissions d'un compte ; ses clés d'accès sont révoquées. Tracé dans le journal.
- **Retrait d'un membre** : immédiat, sans validation à deux (on doit pouvoir retirer vite) ; sa trace dans le journal reste.

## 5. Rôles, permissions, accès temporaires, validation à deux

Rôles fixes de la version 1 (la colonne « Permissions » liste les identifiants stables, en anglais, utilisés dans le code) :

| Rôle | Pour qui | Permissions |
|---|---|---|
| `super_admin` | La propriétaire et une deuxième personne de confiance | toutes, dont `team.manage_admins` |
| `admin` | Gestion de l'équipe | `team.invite`, `team.revoke`, `team.grant`, `audit.read`, `settings.manage` |
| `moderator` | Modération | `reports.decide`, `appeals.decide` (jamais sur sa propre décision), `queue.<file>.decide` pour les files de modération, `pharos.record` |
| `editor` | Rédaction (Comprendre, Voir) | `queue.editorial.<espace>.review`, `queue.editorial.<espace>.publish`, `rightofreply.decide` |
| `organisation_verifier` | Vérification des organisations (Agir) | `queue.agir.organisations.decide` |
| `validator` | Bénévole désigné par une organisation vérifiée de l'annuaire d'« Agir », agréé par PPF (décision du 7 novembre 2026) | `validation.session.open` (sa propre séance), rien d'autre dans le back-office |
| `elected_certifier` | Contrôle du mandat des comptes d'élus dans l'annuaire des élus de « Comprendre » | `elected.certify`, `elected.revoke` |

- Une permission peut porter un **périmètre** (espace, territoire) : le champ existe dès la version 1, il est toujours « tout » pour l'instant.
- **Accès temporaires** (S-60) : chaque attribution de rôle peut avoir une date de fin ; le serveur la retire seul à l'échéance (tracé).
- **Validation à deux** (S-61) : les actions suivantes restent en attente jusqu'à l'accord d'une deuxième personne qui a la même permission : nommer ou retirer un `admin` ou un `super_admin`, attribuer un rôle, supprimer définitivement, bannir un compte de citoyen, envoyer une notification nationale, publier un contenu éditorial qui demande la double validation. Une demande en attente expire après 7 jours. La personne qui demande ne peut pas valider sa propre demande.

## 6. Journal d'audit (S-63)

- Une ligne par action : date, compte de l'équipe, action (identifiant stable), objet visé (type et identifiant : événement, file, compte), motif, détails nécessaires (jamais le texte complet d'un contenu de citoyen, jamais une adresse IP), empreinte de la ligne précédente et empreinte de la ligne (chaîne SHA-256).
- **Inaltérable** : la base refuse toute modification et tout effacement de la table (déclencheur PostgreSQL) ; la chaîne d'empreintes révèle une ligne retirée ou changée par un accès direct à la base ; l'empreinte du jour est horodatée avec l'archivage de « Décider » (D-111) dès qu'il existe.
- Entrent dans le journal : connexions et échecs de connexion (sans adresse), invitations, attributions et retraits de rôles, coupures d'urgence, chaque décision sur une file, chaque validation à deux, chaque réglage modifié, chaque consultation d'une donnée sensible (par exemple un témoignage chiffré).
- Consultation par `audit.read`, avec filtres (période, compte, action, objet) ; export JSON.
- Durée de conservation : sans limite par défaut ; à faire relire par un avocat (RGPD, minimisation).

## 7. Brancher une file : ce que fournit chaque pôle

Le socle définit une interface Kotlin (paquet `org.politisationpopulairefrancaise.server.backoffice`) ; un pôle l'implémente et l'enregistre au démarrage de son espace, dans `Application.module`.

```kotlin
interface BackOfficeQueue {
    val id: String                 // stable, for example "agir.held" or "revendications.proposals"
    val title: String              // French label shown to the team
    val viewPermission: String     // for example "queue.agir.held.decide"
    fun pendingCount(): Int
    fun page(cursor: String?, limit: Int): QueuePage           // items, newest first or by deadline
    fun render(itemId: String, html: FlowContent)              // kotlinx.html detail of one item
    fun actions(itemId: String): List<QueueAction>             // what the team may do on it
    fun perform(itemId: String, action: String, reason: String?, actor: TeamActor): ActionOutcome
}
```

- `QueueAction` dit si un motif est obligatoire (refus : toujours), si l'action est sensible (nouvelle vérification de la clé d'accès), si elle demande une validation à deux, et quelle permission elle exige.
- **Le socle se charge de tout le reste** : connexion, permissions, jeton anti-falsification, nouvelle vérification, validation à deux, **entrée du journal d'audit** (le pôle n'écrit jamais le journal lui-même), comptes pour le tableau de bord (S-64) et le rapport de transparence, motivation au format DSA.
- `perform` reçoit un `TeamActor` (identifiant du compte, pseudonyme, permissions) et ne doit rien faire d'autre que l'action demandée ; il renvoie un résultat (`Done`, `NotFound`, `AlreadyDecided`, `Refused(raison)`).
- Une file ne montre pas plus de données personnelles que la décision ne le demande.

Files attendues (confirmées par les pôles le 7 novembre 2026) :

| File | Pôle | Contenu et actions |
|---|---|---|
| `agir.held` : mobilisations et appels en relecture | Agir ; branchée par le socle en exemple, étape 1 | Sur `HeldQueue` : approuver (publie l'événement tel qu'il a été signé), refuser avec motif obligatoire (montré à l'auteur) ; une mobilisation dont la date est passée est refusée automatiquement et ne peut plus être approuvée ; un appel à témoins est signalé comme toujours relu ; pas de modification (l'auteur republie) |
| `agir.organisations` : vérification des organisations | Agir | Une décision publie, remplace ou retire la fiche d'organisation signée par une clé éditoriale : attend le **service de signature de la plateforme** (fondation à concevoir, la clé ne doit jamais être en clair sur le serveur principal) |
| `revendications.proposals` : revendications proposées | Décider | Sur `ProposalQueue` : approuver, refuser avec motif |
| `decider.validation_sessions` : séances des validateurs | Décider | Section 8 |
| `elus.certifications` : comptes d'élus à certifier | Décider (demandes, clé du compte, statut certifié) ; Comprendre fournit `ElusLookup` (mandat en cours par nom, type de mandat et territoire, avec date de début, fonction et date de mise à jour de la source) et un fragment kotlinx.html des fiches correspondantes | Certifier, refuser avec motif, retirer |
| `editorial.comprendre` et `editorial.voir` : circuit éditorial | Comprendre construit une file éditoriale commune, paramétrée par l'espace ; Voir la reprend | Brouillon ou bloc corrigé signé par un éditeur, retenu jusqu'à l'accord d'un **deuxième éditeur différent** (double validation, M17) ; approuver (validation à deux), demander des modifications avec motif, refuser avec motif ; détail : le contenu rendu comme sur le site, la différence avec la version publiée et les sources ; journal des corrections |
| `voir.right_of_reply` : droits de réponse | Voir, avec le futur client de l'équipe | Les demandes arrivent chiffrées de bout en bout (NIP-17) vers la clé de la boîte de l'équipe (D-056) : le serveur ne peut pas les lire. File de **suivi** seulement : le client de l'équipe, qui déchiffre, enregistre l'adresse de la demande, sa date de réception et l'échéance légale (insertion sous trois jours, niveau d'importance 5), jamais le texte ; actions : insérée (avec l'adresse de la réponse publiée), refusée avec motif juridique, en attente de vérification d'identité. Après les étapes 1 et 2, avec le client de l'équipe |
| `voir.error_reports` : signalements d'erreur | Voir, même mécanisme | Corrigé (avec l'entrée du journal des corrections) ou rejeté avec motif ; sans échéance légale |
| `reports` et `appeals` : signalements et recours | Socle, étape 2 | Section 9 |

Chaque élément d'une file peut porter une **échéance** et un **niveau d'importance** de 1 à 5 (S-115) : le tableau de bord les trie par échéance puis par niveau, et signale en tête ce qui dépasse ou approche son échéance. Le tableau de bord montre aussi des **alertes** sans file (par exemple « import des données ouvertes refusé », Voir, D-064) que les pôles déposent par une fonction du socle.

## 8. Validateurs et certificateurs (pour « Décider »)

Le socle fournit, en version 1 :
- les rôles `validator` et `elected_certifier`, avec double authentification comme tout compte de l'équipe ;
- une **clé de séance** : un validateur ouvre une séance dans le back-office (semaine, nombre maximal de validations, 40 par défaut, durée maximale) et y enregistre la clé publique Nostr générée par son application pour cette séance ; pendant la séance, l'application s'authentifie auprès du serveur par une requête signée **NIP-98** avec cette clé ; une séance révoquée ou échue est refusée aussitôt ;
- des compteurs par séance (jamais le lieu ni l'événement) ;
- une **clé « audit »** : sa clé publique est dans la configuration du serveur (`PPF_AUDIT_PUBLIC_KEY`), sa clé secrète reste hors ligne chez la propriétaire ; le socle offre une fonction qui chiffre en **NIP-44** vers cette clé (par exemple le lien entre un compte de citoyen et la séance qui l'a validé).

« Décider » garde la logique des urnes, des jetons et de la validation elle-même (sa migration V7), et implémente la file `decider.validation_sessions`.

## 9. Signalements et DSA (étape 2, socle)

- Tout utilisateur signale un contenu, un commentaire ou un compte (S-38) par un événement **NIP-56** (type 1984) envoyé à l'API habituelle ; le serveur ne le publie jamais, il le range dans la file `reports`. Les organisations vérifiées sont signaleuses de confiance (leurs signalements sont marqués et passent en tête).
- Les alertes de l'IA (rouge : contenu masqué en attendant la décision humaine ; orange : signalé sans masquage) entrent dans la même file, quand le modèle sera hébergé.
- Décision : maintenir, masquer, retirer, avec un motif choisi dans une liste (charte de contenu) et un texte libre ; l'auteur est informé du motif, du recours possible et, le cas échéant, de l'usage d'un outil automatisé.
- Recours : examiné par une autre personne que celle qui a décidé.
- PHAROS : le signalement se fait sur le site officiel par une personne de l'équipe ; le back-office enregistre la date et le numéro de dossier (`pharos.record`).
- Rapport de transparence public : nombres agrégés par mois (signalements reçus, décisions, délais, recours, transmissions), jamais un contenu ni un compte.

## 10. Données (à reporter dans `docs/REGISTRE_DONNEES.md`)

| Donnée | Finalité | Durée |
|---|---|---|
| Compte de l'équipe (pseudonyme, clé Nostr facultative, rôles, dates) | Accès au back-office | Jusqu'au retrait, puis seulement dans le journal |
| Clés publiques des authentificateurs (WebAuthn) | Connexion | Jusqu'à leur révocation |
| Sessions (empreinte du jeton, dates) | Rester connecté | 8 heures au plus, effacées ensuite |
| Invitations (empreinte du jeton, pseudonyme, rôles proposés) | Créer un compte | 48 heures, effacées ensuite |
| Journal d'audit | Traçabilité des actions de l'équipe (cahier des charges 5) | Sans limite (à faire relire) |
| Séances des validateurs (compte, semaine, plafond, compteur, clé de séance) | Validation en personne | Fixée par « Décider » |

Ni adresse IP, ni navigateur, ni adresse e-mail obligatoire.

## 11. Migration `V8__back_office.sql` (prévue)

Tables `team_account`, `team_credential`, `team_invitation`, `team_session`, `team_role_grant` (rôle, périmètre, date de fin, accordé par, validé par), `team_pending_approval`, `team_audit_log` (déclencheur qui refuse modification et effacement), `team_validation_session`. Les files des pôles gardent leurs propres tables.

Bibliothèque envisagée pour vérifier les clés d'accès côté serveur : Yubico `java-webauthn-server` (version 2.10.1, octobre 2026, licence BSD-2-Clause) ou `webauthn4j` (0.32.0, Apache-2.0) ; choix, dépendances et licence vérifiés au moment du code et justifiés dans la décision.

## 12. Questions pour la propriétaire

1. **Accès au back-office en production** : A. seulement à travers le tunnel WireGuard, chaque membre recevant un profil avec son invitation (recommandée pour la version 1 : la surface d'attaque est minimale) ; B. depuis n'importe où, protégé par les seules clés d'accès (plus simple pour des bénévoles nombreux, à envisager quand l'équipe grandira).
2. **Clés de sécurité physiques des deux super-administrateurs** (décidées le 4 novembre 2026) : un achat de quelques dizaines d'euros par clé ; prix et modèle à relever avant de vous le soumettre.
3. **Deuxième super-administrateur** : qui ? Sans cette personne, la perte de vos clés bloquerait l'administration (procédure de secours écrite et gardée hors ligne en attendant).
4. **Durée de conservation du journal d'audit** : sans limite proposée, à faire relire par un avocat.

## 13. Calendrier

- Étape 1 (socle) : codée dès la diffusion de ce contrat, dans `.claude/worktrees/backoffice`, testée (tests unitaires, d'intégration sur PostgreSQL réel, pages au clavier), puis fusionnée dans `main` selon la file du chef d'équipe.
- Étape 2 (signalements, DSA) : à la suite.
- Chaque pôle branche ses files après la fusion de l'étape 1.
