# Brainstorming : cahier des charges

Brouillon de travail pour `cahier-des-charges.md`. On y note les idées nouvelles, les questions et les remises en question. Rien de ce qui figure ici n'est validé tant que l'entrée n'a pas le statut « Intégré ».

Statuts possibles : Idée, Question, À étudier, À valider, Intégré le [date], Rejeté le [date].

---

### [2026-11-04] Nom de domaine
- Statut : Intégré le 2026-11-05 (cahier des charges 1.3)
- Concerne : cahier-des-charges.md, sections 1.2 et 10.2 (phase 0)
- Description : l'ancien domaine lié à « Marianne Libre » est abandonné. Il faut choisir et acheter un nouveau nom de domaine.
- Prochaine étape : l'IA propose 3 noms disponibles (par exemple autour de « politisation-populaire »), avec leurs avantages, puis un guide d'achat pas à pas.
- Points de vigilance pour l'achat : bureau d'enregistrement européen, distinct de l'hébergeur (une suspension de compte ne doit pas faire perdre à la fois le domaine et les serveurs) ; double authentification sur le compte ; verrouillage contre les transferts ; DNSSEC ; renouvellement automatique activé pour ne jamais perdre le domaine ; données personnelles du titulaire masquées dans l'annuaire public quand l'extension le permet. Tant que l'association n'est pas déclarée, les domaines sont pris au nom de la propriétaire, puis transférés à l'association.
- Décision (2026-11-05) : domaine principal **politisation-populaire-francaise.org**, complété par les extensions .fr, .com, .eu, .net et .app pour protéger le nom ; la propriétaire se charge du dépôt auprès de l'INPI. Le bureau d'enregistrement est proposé dans l'entrée « Hébergement de production et de préproduction ». La propriétaire écarte les sociétés françaises (2026-11-05) : Hostinger est envisagé pour les domaines comme pour les serveurs.
- Complément de décision (2026-11-05) : les six noms de domaine sont achetés chez Hostinger, comme les serveurs (prestataire unique). Le point de vigilance « bureau d'enregistrement distinct de l'hébergeur » est donc abandonné ; le risque d'un compte unique est compensé par la double authentification forte, le verrouillage des domaines, le renouvellement automatique et la répartition du nom sur plusieurs extensions. Les domaines sont achetés dès la phase 0, avant le serveur.

### [2026-11-04] Licence du code
- Statut : À étudier
- Concerne : cahier-des-charges.md, section 8
- Description : l'AGPL-3.0 est envisagée pour empêcher une récupération fermée du code. À vérifier : compatibilité avec la distribution sur l'App Store et avec les licences de bitchat (MIT pour Android, domaine public pour iOS).
- Vérifications du 2026-11-05 : les licences de bitchat (MIT, domaine public) sont compatibles avec l'AGPL-3.0 à condition de conserver leurs mentions. Le conflit connu entre les conditions de l'App Store et les licences GPL se règle par une permission supplémentaire accordée par le titulaire des droits (article 7 de la GPL version 3), comme le fait Signal ; pour la conserver, chaque contributeur accepte que sa contribution porte la même permission.
- Options présentées le 2026-11-05 : A. AGPL-3.0-or-later avec permission App Store et clause contributeur (recommandée) ; B. EUPL-1.2 (texte officiel en français, droit européen, protection réseau plus faible) ; C. MPL-2.0 (souple, pas de protection contre l'exploitation d'une version modifiée en service en ligne).
- Décision (2026-11-05) : option A, AGPL-3.0-or-later, avec les permissions supplémentaires pour les magasins d'applications (`LICENSE-ADDITIONAL-PERMISSIONS.md`) et l'accord des contributeurs (`CONTRIBUTING.md`). Relecture par un avocat prévue avant le lancement public. À intégrer dans le cahier des charges, section 8.

### [2026-11-04] Dépôt privé au départ, public au lancement ?
- Statut : À valider
- Concerne : cahier-des-charges.md, section 8
- Recommandation : dépôt privé pendant la phase 0 et le début de la phase 1, rendu public au lancement de la première version, après un audit de sécurité (aucun secret, aucun réglage anti-fraude dans le dépôt).
- Constat du 2026-11-05 : avec le plan gratuit de GitHub, la protection de la branche principale n'existe pas sur un dépôt privé, et les minutes d'intégration continue y sont limitées (2 000 par mois, les machines Windows comptant double). Options présentées : A. public dès le premier commit (protection de branche gratuite, intégration continue illimitée y compris sur macOS, miroir Codeberg possible, détection des secrets avant chaque commit, audit avant le lancement du service) ; B. privé avec GitHub Pro (environ 4 dollars par mois) ; C. privé avec le plan gratuit, sans protection de branche.
- Décision (2026-11-05) : option A, dépôt public dès le premier commit. Garde-fous : détection des secrets avant chaque commit et en intégration continue, aucune configuration sensible dans le dépôt, audit de sécurité avant le lancement public du service. À intégrer dans le cahier des charges, section 8.

### [2026-11-04] Phase 1 trop chargée face à l'urgence ?
- Statut : Question
- Concerne : cahier-des-charges.md, section 10.2
- Description : la phase 1 contient beaucoup de modules alors que le mouvement est en cours et que le Pacte démocratique doit être diffusé au début de l'année 2027.
- Options :
  - A. Garder la phase 1 telle quelle.
  - B. Créer une « phase 1a » minimale (site web lisible, fiches pratiques hors ligne, carte et agenda, mur des revendications avec vote), puis une « phase 1b » avec le reste.
- Recommandation : B, pour avoir quelque chose d'utile en ligne en quelques semaines.
- Mise à jour du 2026-11-05 : cette entrée sera remplacée par le découpage complet en MVP, proposé après la réécriture du cahier fonctionnel en version 2.0.
- Décision :

### [2026-11-04] Méthode technique « une personne, une voix »
- Statut : À étudier
- Concerne : cahier-des-charges.md, sections 2 et 5.1 ; cahier-fonctionnel.md, M13.6
- Description : les votations citoyennes exigent qu'une personne ne vote qu'une fois, sans collecter d'identité (principe de vie privée). Options techniques à comparer : votes physiques dans des stands avec contrôle sur place ; vote en ligne avec limitation par appareil et preuve de travail, aux garanties plus faibles et clairement signalées ; jetons de vote anonymes distribués lors d'événements physiques ; système mixte.
- Décision :

### [2026-11-04] Modération et assistant IA : hébergement du modèle
- Statut : À valider
- Concerne : cahier-des-charges.md, sections 4 et 5.6 ; inventaire du cahier fonctionnel, points d'attention n° 33 et n° 34 (S-137, CO-20)
- Description : choisir où tourne le modèle d'IA qui sert à la modération, à l'assistant citoyen, aux synthèses et aux rapports. Le fonctionnement de la modération est décidé (cahier des charges 1.2, section 5.6) ; l'hébergement reste à choisir.
- Précision du 2026-11-05 : « auto-hébergé » ne veut jamais dire un serveur à installer chez soi. La propriétaire écarte toute solution qui l'obligerait à acheter ou installer du matériel.
- Options :
  - A. Serveur loué avec carte graphique chez un hébergeur européen, avec un modèle ouvert installé par PPF : aucune donnée ne sort de l'infrastructure de PPF ; coût fixe élevé (environ 575 € HT par mois pour la plus petite instance de ce type chez Scaleway, si elle tourne en permanence).
  - B. Service d'IA européen facturé à l'usage, sans conservation des requêtes (par exemple Scaleway Generative APIs, à comparer avec OVHcloud AI Endpoints) : coût faible au démarrage ; les textes sont confiés à un sous-traitant sous contrat ; le serveur de PPF sert d'intermédiaire et ne transmet jamais le compte, le pseudonyme ni l'adresse IP ; passage à l'option A possible plus tard sans modifier l'application.
  - C. Petit modèle sur le téléphone : totalement privé, mais limité aux téléphones récents ; en complément plus tard.
- Recommandation : B pour démarrer, A quand le volume ou les dons le justifient, C en complément. Pour l'assistant, l'option B dépasse ce que prévoit aujourd'hui la section 5.6 (API externe limitée aux contenus déjà publics) : si elle est retenue, la section 5.6 sera modifiée en conséquence. Détail et sources : point d'attention n° 34.
- Mise à jour du 2026-11-05 : l'assistant citoyen est mis de côté (il pourra revenir plus tard sous forme de module autonome) et le budget de lancement est limité (entrée « Budget »). L'IA ne sert donc au départ qu'à la modération des contenus publics. L'option B s'applique alors dans le cadre déjà prévu par la section 5.6 (API externe limitée aux contenus déjà publics), pour un coût faible au démarrage ; l'option A, trop chère (environ 575 € HT par mois), est écartée pour le lancement.
- Décision :

### [2026-11-05] Toutes les plateformes dès le départ
- Statut : Intégré le 2026-11-05 (cahier des charges 1.3)
- Concerne : cahier-des-charges.md, sections 1.2, 4, 6, 10.2 et 12
- Description : la propriétaire rappelle que l'application doit fonctionner sur Android, iPhone, ordinateur et tablette, avec un affichage adapté à chaque écran, et être très sécurisée. Aujourd'hui, le planning prévoit le site web et Android en phase 1, l'application de bureau en phase 2 et l'application iOS en phase 3.
- Ce qui est vérifié : avec Compose Multiplatform, le même code d'interface sert à Android, iOS et l'ordinateur ; la version iOS est déclarée stable depuis la version 1.8.0 (mai 2025). La version web de Compose Multiplatform est encore en bêta : elle ne convient pas au site, qui doit de toute façon être lisible sans JavaScript.
- Options :
  - A. Garder le planning actuel : iOS en phase 3, ordinateur en phase 2.
  - B. Une seule base de code dès la première ligne : toutes les plateformes compilées et testées automatiquement dès la phase 0 ; écrans adaptatifs (téléphone, tablette, ordinateur) dès le premier écran ; publication d'abord sur le site et en APK Android, puis l'application iOS en test (TestFlight) puis sur l'App Store dès que le compte Apple est prêt, et l'application de bureau dans les releases du dépôt.
- Contraintes de l'option B :
  - compte Apple Developer : 99 € par an dans l'Union européenne ; Apple peut en dispenser les organisations à but non lucratif qui ne distribuent que des applications gratuites (à vérifier pour PPF ; une vente de crédits liée à l'application pourrait faire perdre cette dispense) ;
  - la compilation iOS demande des machines macOS en intégration continue (coût à chiffrer) ;
  - la propriétaire n'a pas d'iPhone : tests sur simulateur en intégration continue, puis par des bénévoles équipés via TestFlight ;
  - l'examen par Apple d'une application politique peut retarder ou bloquer la publication : le site installable (PWA) reste la solution de secours sur iPhone.
- Recommandation : B. Le coût supplémentaire au départ est faible, puisque le code est commun, et l'on évite de découvrir tard des problèmes propres à l'iPhone.
- Décision (2026-11-05) : option B, un code compatible avec tous les supports dès le départ. Apple est mis de côté pour l'instant : pas de compte Apple Developer, pas de publication sur l'App Store ni de compilation iOS dans un premier temps ; ces coûts sont prévus pour plus tard, dans le budget de mise en place. Le code reste écrit pour pouvoir être compilé sur iPhone le moment venu.

### [2026-11-05] Prestataire de paiement : éligibilité de HelloAsso
- Statut : À étudier
- Concerne : cahier-des-charges.md, sections 9 et 12 ; inventaire du cahier fonctionnel, points d'attention n° 4 (dons), n° 16 (adhésions) et n° 34 (recharge de crédits) ; brainstorming-vision.md (statut de PPF)
- Description : la propriétaire envisage HelloAsso pour vendre des codes de recharge de crédits. Or les conditions d'utilisation de HelloAsso excluent expressément toute association ayant pour objet la création et l'animation d'un mouvement ou d'un parti politique, ou la collecte de fonds pour un tel mouvement ou parti. Selon la rédaction des statuts de PPF, HelloAsso pourrait refuser ou fermer le compte, ce qui toucherait aussi les dons et les adhésions. Par ailleurs, HelloAsso demande au payeur ses nom, prénom, adresse postale et adresse e-mail. Les appels à dons des organisations vérifiées qui renvoient vers leur propre page HelloAsso (AP-08) ne sont pas concernés : chaque organisation répond de sa propre éligibilité.
- Options :
  - A. Rédiger les statuts de PPF de façon conforme à sa nature (éducation populaire, information civique, défense des libertés), sans objet de création ou d'animation d'un mouvement politique, puis demander une confirmation écrite à HelloAsso avant de s'y engager.
  - B. Choisir un autre prestataire de paiement sans traceur, à comparer.
  - C. Au démarrage, seulement le virement et les espèces (stands, assemblées générales), contre reçu.
- Recommandation : A, avec un juriste, et C en solution de secours ; ne rien construire qui dépende de HelloAsso avant sa confirmation. Dépend de la décision sur le statut de PPF.
- Source (vérifiée le 5 novembre 2026) : conditions générales d'utilisation de HelloAsso pour les associations, définition de l'« Organisme » et données demandées au payeur : https://info.helloasso.com/cgu-associations
- Position de la propriétaire (2026-11-05) : PPF est une plateforme d'information ; que des utilisateurs puissent, grâce à elle, créer des mouvements ou des partis, dans le respect du pluralisme, ne fait pas de PPF un mouvement politique. Si HelloAsso n'est pas possible, on s'en passera, en sécurisant au maximum le tunnel d'achat et la gestion des données, éventuellement avec un autre prestataire.
- Analyse : la clause de HelloAsso vise l'objet de l'association elle-même (créer ou animer un mouvement ou un parti, ou collecter des fonds pour lui), et non ce que font ses utilisateurs. Une plateforme qui héberge des collectifs de tous bords, sans en animer aucun et sans collecter d'argent pour un parti ou un candidat (ce qu'exclut déjà AP-08), a donc de bonnes chances d'être éligible, à condition que les statuts le disent clairement. Le risque tient à l'interprétation : le caractère militant de PPF, les campagnes qu'elle porte et une éventuelle évolution vers un parti (brainstorming-vision.md) pourraient être lus autrement. Seule HelloAsso peut trancher : lui demander une confirmation écrite, statuts en main, avant de s'y engager. L'assistant étant mis de côté, la question ne concerne plus, dans l'immédiat, que les dons et les adhésions : elle ne bloque pas le développement.
- Décision :

### [2026-11-05] Hébergement de production et de préproduction
- Statut : Intégré le 2026-11-05 (cahier des charges 1.3)
- Concerne : cahier-des-charges.md, sections 3.1, 5.3, 10.2 (phase 0) et 12 ; entrées « Nom de domaine » et « Budget »
- Description : la propriétaire demande ce qu'il faut acheter, et chez qui, pour héberger le site, l'application et une préproduction obligatoire, aussi sécurisée que la production, avec un budget de lancement de 300 € tout compris. Le cahier des charges prévoit aujourd'hui un VPS Hostinger (sections 3.1 et 12).
- Rien n'est à acheter pour commencer à écrire le code : il vit dans un dépôt de code gratuit (GitHub, avec un miroir sur Codeberg, section 8). Les serveurs ne servent qu'au moment de mettre en ligne le premier squelette : on les loue à ce moment-là, pour ne pas payer des serveurs inutilisés. Seuls les noms de domaine sont à prendre tout de suite, pour les protéger.
- Architecture proposée, la même quelle que soit l'option :
  - **deux serveurs séparés**, l'un pour la production, l'autre pour la préproduction : une faille dans la préproduction ne doit jamais donner accès à la production ;
  - la préproduction **n'est pas accessible au public** : on n'y entre que par un tunnel chiffré (VPN WireGuard) installé sur les appareils de l'équipe, en plus d'un mot de passe ;
  - elle ne contient **jamais de données réelles** : uniquement des données fictives, marquées [EXEMPLE] ;
  - même durcissement que la production : pare-feu, connexion par clé uniquement, mises à jour de sécurité automatiques, double authentification sur le compte de l'hébergeur ; clés et secrets distincts de ceux de la production ;
  - circuit de mise en ligne : code, tests automatiques, déploiement en préproduction, validation par la propriétaire, puis production ;
  - sauvegardes chiffrées de la production, stockées chez un autre prestataire (section 5.3) ;
  - noms de domaine chez un bureau d'enregistrement distinct de l'hébergeur.
- Options pour les serveurs (prix relevés le 5 novembre 2026 dans des comparatifs récents, à confirmer au moment de commander) :
  - A. **OVHcloud** (société française), deux VPS-1 : 2 vCores, 4 Go de mémoire, 40 Go de disque NVMe, trafic illimité, sauvegarde quotidienne incluse ; environ 3,81 € HT (4,57 € TTC) par mois et par serveur au prix d'entrée, jusqu'à environ 7,79 € par mois sans engagement. Avantages : société française, trafic illimité, facturation mensuelle sans grosse avance. Inconvénients : réputation mitigée du support ; deux hausses de prix en 2026.
  - B. **Hostinger** (société lituanienne, choix actuel du cahier des charges), deux KVM 1 : 1 vCPU, 4 Go, 50 Go ; 5,49 € par mois, mais seulement en payant 24 mois d'avance (environ 132 € par serveur, soit environ 264 € pour deux), puis 11,99 € par mois au renouvellement. Inconvénients : l'avance consomme presque tout le budget ; le prix double au renouvellement.
  - C. **Hetzner** (société allemande), deux CX23 : 2 vCPU, 4 Go, 40 Go ; 5,49 € HT par mois plus 0,50 € pour l'adresse IPv4 ; mais cette gamme est actuellement indisponible à la commande.
- Bureau d'enregistrement proposé pour les domaines : un bureau français distinct de l'hébergeur, par exemple BookMyName (groupe Iliad), qui affiche le .fr à 5,98 € HT puis 6,59 € HT au renouvellement, le .com à 12,34 € HT et le .eu à 5,98 € HT ; les tarifs du .org, du .net et du .app, ainsi que la double authentification et DNSSEC, sont à vérifier avant la commande.
- Estimation de la première année avec l'option A (TTC, estimations à confirmer) : six noms de domaine, environ 90 € ; deux serveurs avec un engagement de 12 mois, environ 110 € ; sauvegardes chiffrées hors site, environ 30 à 60 € ; modération par IA, quelques euros au démarrage (premier million de tokens offert par Scaleway Generative APIs) ; clé de sécurité physique de la propriétaire, quelques dizaines d'euros, nécessaire avant l'ouverture au public. Total : environ 260 à 330 €. Restent hors de ce budget et à prévoir plus tard : compte Apple Developer, audit de sécurité externe avant le lancement public, avocat, expert-comptable.
- Recommandation : option A, avec des domaines chez un bureau d'enregistrement distinct. C'est la seule qui tienne dans le budget sans grosse avance, chez une société européenne, avec un trafic illimité. Elle modifie le cahier des charges (VPS Hostinger aux sections 3.1 et 12) : à intégrer dans sa prochaine version si elle est validée.
- Sources (consultées le 5 novembre 2026) : comparatif des VPS 2026 (OVHcloud VPS-1, Hostinger KVM 1 et renouvellement) : https://hackceleration.com/fr/labs/meilleur/serveur-vps ; hausse des prix d'OVHcloud en 2026 : https://digitiz.fr/hausse-prix-vps-2026/ ; prix et disponibilité de Hetzner Cloud en 2026 : https://www.bitdoze.com/hetzner-cloud-cost-optimized-plans ; bureaux d'enregistrement français et leurs tarifs : https://hackceleration.com/fr/labs/francais/nom-de-domaine ; tarifs des domaines chez BookMyName (.eu, .fr, .com) : https://hackceleration.com/labs/european/domain-registrar
- **Position de la propriétaire (2026-11-05)** : pas de société française, ni pour l'hébergement ni pour les noms de domaine, par crainte d'une censure plus facile ; OVHcloud écarté ; o2switch possible mais Hostinger préféré ; idéalement un seul prestataire pour tout.
- Analyse de cette position :
  - **La nationalité de l'hébergeur protège peu** : depuis le 18 août 2026, le règlement européen sur les preuves électroniques (2023/1543) permet à une autorité judiciaire française d'ordonner directement à un fournisseur établi dans un autre État membre de produire ou de conserver des données, où qu'elles soient stockées. Un hébergeur lituanien ralentit à peine une démarche légale française. La vraie protection contre une coupure est l'architecture prévue par le cahier des charges : plusieurs noms de domaine, miroirs statiques, Nostr, adresse .onion, application installable hors des magasins (sections 3.3 et 5).
  - **Pour les domaines, c'est le registre qui compte plus que le bureau d'enregistrement** : le .fr dépend toujours de l'AFNIC (France), le .eu d'EURid (Belgique), le .org d'un registre américain, quel que soit le vendeur. Répartir le nom sur plusieurs extensions, comme décidé, est déjà la bonne protection.
  - **o2switch** est une société française (contraire au critère de la propriétaire) et son offre principale est un hébergement mutualisé, que le cahier des charges exclut (section 3.1) : écarté.
  - **Hostinger** (société lituanienne) convient techniquement : VPS avec virtualisation KVM, ressources dédiées à chaque serveur, accès complet ; centres de données en Europe en France, en Allemagne, en Lituanie, aux Pays-Bas et au Royaume-Uni, choisis à la commande. Pour suivre la position de la propriétaire, choisir un centre hors de France (par exemple Lituanie, Allemagne ou Pays-Bas, au sein de l'Union européenne pour le RGPD).
  - **Deux serveurs séparés chez le même prestataire** : c'est possible et suffisant pour l'isolement. Deux VPS sont deux machines virtuelles distinctes, chacune avec son système, son pare-feu et ses clés ; une faille dans l'une ne donne pas accès à l'autre. Ce qui reste commun, c'est le compte Hostinger : s'il est piraté ou suspendu, les deux serveurs sont touchés. Protections : double authentification forte sur le compte (clé de sécurité physique si Hostinger l'accepte, à vérifier) ; sauvegardes chiffrées de la production stockées ailleurs, seule exception au prestataire unique, pour pouvoir tout redéployer ailleurs en quelques heures (sections 3.3 et 5.3).
  - **Domaines chez Hostinger aussi** : possible, avec le même risque concentré sur un seul compte ; il est atténué par les protections ci-dessus et par la répartition sur plusieurs extensions. Prix des six extensions chez Hostinger, double authentification, verrouillage et DNSSEC à vérifier avant la commande.
  - **Budget** : chez Hostinger, le prix bas (KVM 1 à 5,49 € par mois) suppose de payer 24 mois d'avance, soit environ 132 € par serveur, puis 11,99 € par mois au renouvellement. Pour tenir le budget, achat en deux temps : pendant le développement, seule la préproduction est nécessaire (rien n'est encore ouvert au public) ; le serveur de production est pris au moment du lancement public. Estimation : domaines (environ 90 à 110 € pour les six, ou environ 30 € pour le .org et le .fr seuls au départ) + préproduction (environ 132 € pour deux ans) au démarrage ; production (environ 132 € pour deux ans) au lancement ; sauvegardes hors site, quelques euros par mois.
- **Recommandation révisée** : option B, Hostinger, pour les deux serveurs (centre de données dans l'Union européenne, hors de France) et, si la propriétaire le souhaite, pour les noms de domaine ; sauvegardes chiffrées chez un second prestataire européen ; achat en deux temps pour respecter le budget. Le cahier des charges, qui prévoit déjà Hostinger, n'a pas à changer sur ce point ; il faudra seulement y ajouter la préproduction et les sauvegardes hors site.
- Sources (consultées le 5 novembre 2026) : règlement (UE) 2023/1543, applicable à partir du 18 août 2026 : https://oeil.europarl.europa.eu/oeil/fr/document-summary?id=1754183 ; centres de données de Hostinger : https://www.hostinger.com/ma-fr/la-technologie ; virtualisation KVM et ressources dédiées des VPS Hostinger : https://www.hostinger.com/fr/tutoriels/?p=49591 ; tarifs VPS Hostinger (plan de 24 mois payé d'avance) : https://www.hostinger.com/be-fr/tarifs/vps-hosting
- **Questions de la propriétaire (2026-11-05)** : un centre de données au Royaume-Uni éviterait-il l'ingérence française ? Quel effet sur les performances ? La séparation entre production et préproduction peut-elle attendre, à cause du coût de l'engagement de 24 mois ?
- Réponses proposées :
  - **Royaume-Uni** : n'apporte pas de protection supplémentaire. Une injonction européenne vise le fournisseur, ici Hostinger, société établie en Lituanie, et s'applique quelle que soit la localisation des données (règlement 2023/1543). Le Royaume-Uni étant hors de l'Union européenne, il ajoute en revanche une deuxième législation et un transfert de données hors de l'Union, que le RGPD encadre. Recommandation : centre de données dans l'Union européenne, hors de France (Lituanie, Allemagne ou Pays-Bas).
  - **Performances** : l'éloignement géographique coûte quelques centièmes de seconde par échange, imperceptibles pour l'utilisateur. Avec beaucoup d'utilisateurs, ce qui compte est la puissance du serveur, qui peut être augmentée depuis le panneau de Hostinger, et la conception de l'application (contenus enregistrés sur l'appareil, mise en cache), prévue dès le départ.
  - **Séparation reportée** : oui. Pendant le développement, rien n'est ouvert au public : un seul serveur suffit, utilisé comme préproduction. La question ne se pose qu'au lancement public. À ce moment-là :
    - A. Deuxième serveur pour la production (recommandé) : isolement complet ; une version en test qui plante ou contient une faille ne peut ni couper ni exposer la production, qui contiendra des données sensibles.
    - B. Production et préproduction sur le même serveur, chacune dans son propre compartiment (conteneurs séparés, bases de données, réseaux, utilisateurs et secrets distincts, ressources limitées pour la préproduction, accès à la préproduction par tunnel chiffré uniquement, jamais de données réelles en préproduction) : moins cher, mais isolement plus faible, car les deux partagent le même système et la même machine. Acceptable comme solution temporaire si le budget l'impose, avec passage à A dès que les dons le permettent.
  - Recommandation : un seul serveur pendant le développement ; décision entre A et B au moment du lancement public, selon le budget disponible.
- Source complémentaire (consultée le 5 novembre 2026) : possibilité d'augmenter la puissance d'un VPS Hostinger depuis le panneau d'administration : https://www.vpsbenchmarks.com/compare/features/hostinger_vs_my_host
- **Question de la propriétaire (2026-11-05)** : si le droit européen permet à la France la même ingérence, pourquoi prendre un serveur hors de France ? Cela complique-t-il quand même la tâche ?
- Réponse : oui, un peu, et cela ne coûte rien de plus.
  - Ce qui ne change pas : une demande de données (règlement 2023/1543 sur les preuves électroniques) ou une injonction de retrait d'un contenu illicite (article 9 du règlement sur les services numériques, DSA) peut viser un hébergeur établi dans un autre État membre. Et un blocage du site par les fournisseurs d'accès à Internet français reste possible quel que soit le lieu d'hébergement.
  - Ce qui change : une saisie physique du serveur ou une perquisition dans le centre de données exige la coopération des autorités du pays concerné (décision d'enquête européenne, directive 2014/41/UE), validée par un juge de ce pays ; pour certaines demandes de contenus, l'État où se trouve le prestataire est notifié et peut opposer des motifs de refus, dont la liberté de la presse et la liberté d'expression ; l'hébergeur répond selon son propre droit et ses propres juristes, et non comme une entreprise française soumise directement aux réquisitions des enquêteurs français. Chaque étape demande plus de formalités et de temps, et laisse une trace écrite.
  - Conclusion : la localisation hors de France est un frein utile mais limité ; la protection réelle reste l'architecture (plusieurs domaines, miroirs, Nostr, adresse .onion, application installable hors des magasins) et le fait de ne détenir presque aucune donnée sur les utilisateurs. Le centre de données dans l'Union européenne, hors de France, est conservé.
  - Sources : règlement (UE) 2022/2065 (DSA), article 9 : https://eur-lex.europa.eu/eli/reg/2022/2065/oj ; directive 2014/41/UE (décision d'enquête européenne) : https://eur-lex.europa.eu/eli/dir/2014/41/oj ; règlement (UE) 2023/1543 : voir ci-dessus.
- Décision (2026-11-05) :
  - **Hostinger pour tout** : serveurs et noms de domaine, prestataire unique ; centre de données dans l'Union européenne, hors de France (Lituanie, Allemagne ou Pays-Bas) ; double authentification forte sur le compte.
  - **Un seul serveur au départ (option B de la séparation)**, un VPS KVM 1 pris au moment du premier déploiement : pendant le développement, il ne sert que de préproduction ; au lancement public, il accueille la production et la préproduction, chacune dans son compartiment (conteneurs, bases de données, réseaux, utilisateurs et secrets distincts ; ressources limitées pour la préproduction ; accès à la préproduction par tunnel chiffré WireGuard et mot de passe ; jamais de données réelles en préproduction).
  - **Deuxième serveur pour la production** dès que le budget le permet.
  - **Sauvegardes chiffrées** stockées chez un autre prestataire européen, seule exception au prestataire unique.
  - Circuit de mise en ligne : tests automatiques, préproduction, validation par la propriétaire, production.

### [2026-11-04] Framework du site web
- Statut : À étudier
- Concerne : cahier-des-charges.md, section 4
- Description : choisir le framework de rendu côté serveur (lisible sans JavaScript, PWA, hors ligne), en tenant compte du partage de code avec Kotlin.
- Options présentées le 2026-11-05 : A. Ktor, déjà retenu pour le serveur, avec des gabarits HTML écrits en Kotlin (kotlinx.html) : un seul langage, modèles partagés avec les applications, pages lisibles sans JavaScript, PWA, export statique possible pour les miroirs de la phase 3, très peu de dépendances (recommandée) ; B. Astro : excellent pour un site de contenu, mais un second langage et un second serveur, et des centaines de dépendances JavaScript ; C. générateur de site statique plus l'API Ktor : rapide et résistant, mais l'interactivité exige du JavaScript ou un second circuit.
- Décision (2026-11-05) : option A, Ktor avec kotlinx.html (module `web`). À intégrer dans le cahier des charges, section 4.

### [2026-11-04] Réutilisation des données de la HATVP
- Statut : À étudier
- Concerne : cahier-des-charges.md, section 9.2 ; cahier-fonctionnel.md, M13.2
- Description : vérifier la licence et les conditions de réutilisation des déclarations d'intérêts et du répertoire des représentants d'intérêts, et confirmer l'interdiction de publier les déclarations de patrimoine des parlementaires.
- Décision :

### [2026-11-04] Agrément « Jeunesse et éducation populaire »
- Statut : Idée
- Concerne : cahier-des-charges.md, section 9
- Description : une fois l'association déclarée, l'agrément pourrait renforcer sa légitimité et ouvrir certains financements. Conditions à vérifier (ancienneté, fonctionnement démocratique, pluralisme).
- Décision :

### [2026-11-04] Budget
- Statut : Intégré le 2026-11-05 (cahier des charges 1.3)
- Concerne : cahier-des-charges.md, section 12
- Description : estimer les coûts récurrents (VPS, nom de domaine, sauvegardes hors site, éventuelle instance PeerTube, runners macOS pour iOS, comptes développeur Google Play et Apple) et les sources de financement possibles de l'association.
- Contrainte fixée par la propriétaire (2026-11-05) : 300 € tout compris au lancement, 500 € au grand maximum, à éviter.
- Mise à jour du 2026-11-05 : coûts identifiés à ajouter à l'estimation : compte Apple Developer (99 € par an dans l'Union européenne, dispense possible pour une organisation à but non lucratif) ; modèle d'IA (environ 575 € HT par mois pour un serveur loué avec carte graphique, ou un coût à l'usage avec un service européen) ; audit de sécurité externe avant chaque lancement public ; avis d'un expert-comptable et d'un avocat.
- Décision (2026-11-05) : les 300 € couvrent la mise en route (noms de domaine, serveur KVM 1 payé 24 mois d'avance, sauvegardes hors site, modération par IA au démarrage). La suite du budget dépendra des adhérents et des dons. Sont reportés, faute de budget : compte Apple Developer et compilation iOS, deuxième serveur pour la production, serveur d'IA dédié, audit de sécurité externe, avocat, expert-comptable. Aucune dépense n'est engagée sans l'accord de la propriétaire.

### [2026-11-04] Conséquences du retrait de la notion de non-partisanerie
- Statut : À valider
- Concerne : cahier-des-charges.md, section 2 (principe 8) et 9.2 ; cahier-fonctionnel.md, section 3 (règle « Traitement identique ») et M13.5, M13.7 ; instructions du projet, section 5 (garde-fou « Militante mais jamais partisane »)
- Description : la vision 3.0 ne dit plus que PPF est non partisan. Plusieurs passages des autres documents reposent encore sur cette notion :
  - cahier des charges, principe 8 « Non partisane » ;
  - cahier des charges, 9.2 : les outils qui concernent des candidats les traitent tous de façon identique ;
  - cahier fonctionnel : règle transverse « Traitement identique », Pacte démocratique envoyé à tous les candidats, comparateur « strictement identique » ;
  - instructions : garde-fou interdisant à l'IA de produire un contenu qui soutient un candidat ou un parti.
- Ce qui ne change pas, quelle que soit la décision : tant que PPF est une association, la loi lui interdit de financer une campagne ou de fournir à un candidat des avantages, même en nature (site, fichiers, communication). Cette limite reste dans le cahier des charges (9.2).
- Options :
  - A. Supprimer le principe général « Non partisane » et le garde-fou des instructions, mais garder le traitement identique à l'intérieur de trois outils précis (Pacte démocratique, comparateur, baromètre des élus) : leur crédibilité vient justement de ce qu'ils appliquent les mêmes règles à tous.
  - B. Supprimer toute notion de traitement identique, y compris dans ces outils.
- Recommandation : A. Un Pacte ou un comparateur jugé partial perd sa force de pression et devient facile à contester.
- Décision :

### [2026-11-04] Navigation et UX : quatre espaces, un accueil, « Mon territoire »
- Statut : À valider
- Concerne : cahier-des-charges.md, section 7 (design et UX) ; dépend de l'entrée « Restructurer les piliers » de brainstorming-vision.md
- Proposition :
  - **Barre de navigation à cinq entrées** (le maximum lisible sur mobile), identique sur le site et l'application : **Maintenant** (accueil), **Voir**, **Comprendre**, **Agir**, **Décider**.
  - **Maintenant** : ce qu'il faut savoir en moins de 30 secondes : chiffres clés, prochaines mobilisations, fil d'actualité, alertes importantes (vote imminent, campagne en cours).
  - **« Mon territoire »** : vue personnelle accessible depuis chaque espace, après saisie d'un code postal stocké uniquement sur l'appareil : mes élus, les mobilisations près de chez moi, les revendications et le cahier de doléances de mon territoire, les groupes locaux.
  - **Passerelles « Et maintenant ? »** : chaque contenu propose l'étape suivante dans un autre espace (par exemple : un vote contraire aux promesses, dans « Voir », mène à « Interpeller cet élu » et à la campagne liée, dans « Décider »).
  - **Recherche unique** sur l'ensemble des contenus.
  - **Rubriques hors navigation** : à propos et transparence de PPF, charte, sécurité et vie privée, compte.
- Étape suivante si validé : maquettes de la navigation et de l'accueil, à présenter à la propriétaire.
- Mise à jour du 2026-11-05 : la propriétaire a décidé le 2026-11-04 d'une autre navigation (« En direct », « Mon espace », barre du haut fixe, barre latérale en accordéon, bouton « Carte » ; voir S-05 et le point d'attention n° 22 de l'inventaire du cahier fonctionnel). Cette entrée sera réécrite lors du découpage en MVP, en ajoutant l'adaptation aux tablettes.
- **Décision de la propriétaire (2026-11-06)** : barre du bas fixe sur téléphone (Fil, En direct, Carte, Mon espace), écran par défaut = fil social ; piliers dans le menu en accordéon. Détail dans brainstorming-cahier-fonctionnel.md, entrées du 2026-11-06. La section 7 du cahier des charges reste valable ; le « chemin des piliers » devient le menu, le fil la porte d'entrée.

### [2026-11-06] Direction de design
- Statut : Intégré le 2026-11-06 (décision de la propriétaire ; précise la section 7 du cahier des charges)
- Concerne : cahier-des-charges.md, section 7
- **Décision de la propriétaire (2026-11-06)** : design moderne, épuré, intuitif ; d'inspiration nationale sans jamais pouvoir être confondu avec un site gouvernemental ; moins austère, « ludique mais mature », pour les jeunes adultes et les adultes ; encarts bien mis en avant pour les informations importantes ; contenu ultra lisible et agréable à lire, surtout les fiches pratiques, avec un encart « En bref » qui résume, un encart vert « Légal » et un encart rouge « Illégal » à listes à puces. Maillage interne (guides et contenus connexes) plus tard.
- Première mise en œuvre (6 novembre 2026, matin) : palette bleu nuit et corail, polices Inter et Outfit ; remplacée le jour même.
- **Décision de la propriétaire (6 novembre 2026, soir)** : l'identité visuelle à respecter est celle de ses maquettes « interface communautaire » (fil téléphone, profil et confidentialité, page d'un collectif, messagerie chiffrée, fil ordinateur, en clair et en sombre) : bleu de marque #2B3A8F et accent carmin #C8213A sur des surfaces blanc et gris-bleu très clair, police unique Atkinson Hyperlegible, icônes au trait, arrondis de 6, 10 et 16 dp, cartes bordées à ombre légère, badges [EXEMPLE], encarts teintés (marque, succès, accent, avertissement). Consignée en D-025 et appliquée à l'application et au site.
- Décision : Intégré le 2026-11-06 (D-025).

### [2026-11-06] Protocole de la messagerie chiffrée (Internet)
- Statut : Intégré le 2026-11-07 (décision de la propriétaire : option A ; D-027)
- Concerne : cahier-des-charges.md, sections 3.4 (messagerie hors ligne bitchat) et 5 (sécurité) ; cahier fonctionnel M14 ; point d'attention n° 26
- Description : la propriétaire a confirmé la messagerie (privé, groupes, collectifs, flux). Le cahier des charges ne fixe que le mode hors ligne (bitchat : Bluetooth, chiffrement Noise, Nostr en relais). Il faut choisir le protocole des conversations par Internet, compatible avec nos identités (clés secp256k1 Nostr) et avec le serveur unique du départ. Décision de sécurité structurante, donc présentée avant tout code.
  - **Option A : messages privés Nostr (NIP-17) et groupes Nostr (NIP-29)**. NIP-17 : chaque message est chiffré pour le destinataire (NIP-44) puis enveloppé deux fois (« gift wrap ») pour cacher l'expéditeur, le destinataire et l'heure ; les petits groupes se font en envoyant le message à chaque membre. NIP-29 : salons gérés par le relais, adaptés aux collectifs publics et réservés, avec rôles d'administrateurs. Avantages : mêmes clés que nos comptes, aucun serveur supplémentaire (notre serveur devient relais), interopérable avec les applications Nostr, bien documenté. Inconvénients : pas de confidentialité persistante (une clé volée déchiffre les anciens messages), groupes privés limités à quelques dizaines de membres, pas de vérification de l'appareil distant en standard. Coût : nul.
  - **Option B : MLS (Messaging Layer Security, RFC 9420) sur Nostr, protocole « Marmot »**. Chiffrement de groupe moderne avec confidentialité persistante et récupération après compromission, transporté par des relais Nostr. Avantages : le niveau de sécurité de Signal pour les groupes, grands groupes possibles, même identité que nos comptes. Inconvénients : jeune (2025), bibliothèques en Rust à relier à Kotlin Multiplatform, peu d'applications, audit indépendant encore partiel. Coût : nul en licence, mais plusieurs semaines d'intégration et de tests.
  - **Option C : protocole Signal (libsignal) ou Matrix avec son propre serveur**. Avantages : protocoles éprouvés et audités. Inconvénients : une seconde identité (numéros ou comptes Matrix) à côté des clés Nostr, un serveur de plus à faire tenir dans 4 Go de mémoire (Matrix Synapse est lourd), compatibilité avec bitchat perdue. Coût : nul en licence, mais serveur et maintenance.
  - Recommandation : **A maintenant, B ensuite**. Commencer par NIP-17 pour les conversations privées et de petit groupe et NIP-29 pour les salons de collectifs, en gardant une couche de messagerie abstraite (comme le port de publication) pour remplacer le chiffrement par MLS quand Marmot sera mûr. Dans tous les cas : clés sur l'appareil, aucune liste de conversations sur le serveur, vérification des contacts par QR code (S-128), signalement par le destinataire (S-127), accusés de lecture désactivés par défaut (S-125).
- Décision : **Option A retenue par la propriétaire le 7 novembre 2026** (D-027). À reporter dans le cahier des charges, section 3.4, lors de sa prochaine version.

### [2026-11-06] Maquettes de l'interface communautaire : écarts de navigation à trancher
- Statut : À valider
- Concerne : cahier-des-charges.md, section 7 ; cahier-fonctionnel.md, S-05
- Description : les maquettes de la propriétaire diffèrent sur deux points de sa décision du matin sur la navigation.
  1. **Barre du bas ou boutons flottants.** Les maquettes n'ont pas de barre du bas : la carte est un bouton rond flottant « Carte » (accent) en bas à droite, « En direct » un bandeau en haut du fil, et les sections sont dans le menu. L'application garde pour l'instant la barre du bas (Fil, En direct, Carte) demandée le matin, avec le bandeau « EN DIRECT » en plus. Option A : garder la barre du bas (plus visible, un geste pour chaque entrée quotidienne). Option B : suivre les maquettes (boutons flottants Carte et AIDE, menu pour le reste). Recommandation : A, car la carte et le direct sont les deux usages du quotidien en mobilisation et une barre fixe se repère sans apprentissage ; le bouton AIDE peut s'ajouter en flottant au-dessus de la barre.
  2. **« Mon espace »** : sur les maquettes, c'est l'avatar en haut à droite ; appliqué tel quel (retiré de la barre du bas).
- Décision :

### [2026-11-04] Briques de contenu communes
- Statut : À valider
- Concerne : cahier-des-charges.md, section 3 (architecture) ; cahier-fonctionnel.md
- Description : pour qu'un même contenu puisse apparaître à plusieurs endroits sans être recopié (ce qui supprime le problème des fonctionnalités « transverses »), tous les contenus reposent sur un petit nombre de briques communes, chacune signée, sourcée et versionnée :
  - **Fait sourcé** : énoncé, date, lieu, sources, statut (établi, allégué, en cours d'enquête), personnes et organisations liées ;
  - **Personne publique** : mandats, fonctions passées, déclarations d'intérêts publiques ;
  - **Organisation** : association, syndicat, parti, entreprise, média, institution ;
  - **Événement** : mobilisation, décision, audience, élection ;
  - **Indicateur et mesure** : valeur, méthode, source, fiabilité ;
  - **Texte et vote** : loi, amendement, scrutin, position de chaque élu ;
  - **Document archivé** : copie, empreinte, horodatage ;
  - **Revendication**, **Campagne**, **Engagement** (Pacte démocratique) ;
  - **Contenu éditorial** : article, fiche pratique, dossier, décryptage.
- Exemple : une fausse information diffusée par un élu est un seul « fait sourcé » ; il apparaît à la fois dans la fiche de l'élu (Voir), dans le décryptage (Comprendre) et, si besoin, dans le fil d'actualité (Maintenant).
- Décision :

### [2026-11-05] Identités, phrase de récupération et signature des contenus : choix techniques
- Statut : À valider (mise en œuvre commencée le 2026-11-05 sous l'autorisation générale donnée par la propriétaire ; réversible)
- Concerne : cahier-des-charges.md, sections 3.2 (fondations de la phase 1) et 4 (technologies)
- Description : le cahier des charges fixe le principe (clés secp256k1 créées sur l'appareil, signatures Schnorr BIP-340 compatibles Nostr, phrase secrète) mais pas les bibliothèques ni le format exact de la phrase. Trois choix ont été faits, détaillés dans `docs/DECISIONS.md` (D-016 à D-018) :
  - **Bibliothèques** : `secp256k1-kmp` et `bitcoin-kmp` d'ACINQ (enveloppe de la libsecp256k1 de Bitcoin Core, auditée ; Apache 2.0 ; mises à jour en août et septembre 2026). Options écartées : écrire nous-mêmes BIP-39 et BIP-32 (cryptographie maison), une bibliothèque Nostr trop récente, la cryptographie des plateformes (Schnorr absent partout).
  - **Phrase de récupération** : 12 mots de la liste française officielle BIP-39 par défaut, liste anglaise acceptée à la saisie, dérivation NIP-06 (compte 0), clé « nsec » exportable et importable. À savoir : la communauté Nostr marque NIP-06 « non recommandé » et préfère échanger la clé « nsec » directement ; la phrase reste indispensable pour un public non technique, et la clé « nsec » assure la compatibilité.
  - **Format des contenus signés** : événement Nostr NIP-01 ; JSON de l'événement = format d'export ouvert (`docs/FORMATS.md`) ; identifiants stables `type:clé:identifiant` pour les contenus modifiables ; port de publication unique.
- Questions à la propriétaire :
  - Confirme-t-elle la phrase en **français** par défaut ? Les autres applications Nostr ne comprendront que la clé « nsec », pas la phrase (c'est déjà le cas pour une phrase anglaise, NIP-06 étant peu répandu).
  - Confirme-t-elle **12 mots** (et non 24) ? Douze mots donnent 128 bits d'aléa, le niveau standard des portefeuilles grand public.
- Prochaine étape : stockage sécurisé de la clé sur l'appareil (Android Keystore, trousseau iOS, fichier protégé sur ordinateur), puis écran « Mon espace » de création et de restauration du compte.
- Décision (2026-11-05) : options appliquées par l'IA sous autorisation générale ; en attente de confirmation de la propriétaire.
