# Soutien aux personnes interpellées (AG-57) : conception, avant tout code

- Pôle « Agir », 9 novembre 2026. Fonctionnalité AG-57 du cahier fonctionnel 2.1 (M25, brainstorming du 8 novembre 2026, décision du chef d'équipe par délégation de la propriétaire) : « "Je suis sans nouvelles de…" transmis chiffré à la seule legal team, accueil bénévole à la sortie, agenda sans nom des comparutions de manifestants ».
- **Rien n'est codé.** Cette note pose la conception et les questions ; le code attend la réponse du chef d'équipe et l'avis d'un avocat (la fonctionnalité est déjà dans la liste « à faire relire par un avocat » de la feuille de route, section M25).

## 1. Le cadre juridique (vérifié le 9 novembre 2026)

- **RGPD, article 10** : les données relatives aux condamnations pénales et aux infractions ne peuvent être traitées que sous le contrôle de l'autorité publique, ou si le droit de l'Union ou d'un État membre l'autorise avec des garanties.
- **Loi Informatique et Libertés, article 46** (version en vigueur, [Légifrance, LEGIARTI000037823133](https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000037823133/)) : ces traitements sont réservés (1°) aux juridictions, autorités publiques et organismes gérant un service public, et à certaines personnes morales de droit privé qui collaborent au service public de la justice ; (2°) aux **auxiliaires de justice**, pour les stricts besoins des missions que la loi leur confie ; (3°) aux personnes qui préparent, exercent ou suivent une action en justice comme victime ou mise en cause, pour cette action seulement.
- Une interpellation, une garde à vue ou une comparution sont des données d'infraction au sens de ces textes. **PPF n'entre dans aucune des trois catégories** : la plateforme ne doit donc **jamais** pouvoir lire ces données. Elle peut seulement transporter des enveloppes chiffrées qu'elle ne sait pas ouvrir, comme pour les messages privés (D-027) et les témoignages (D-052).
- Les legal teams militantes ne sont pas toujours composées d'avocats. Savoir si une legal team sans avocat entre dans le 3° (elle agit pour la personne mise en cause) est **la question n° 1 pour l'avocat**.

## 2. Ce que la fonctionnalité ferait

### 2.1 « Je suis sans nouvelles de… »

- Une personne signale qu'un proche ou un camarade a disparu pendant ou après une mobilisation : surnom ou prénom, description utile (vêtements, lieu et heure où on l'a vu pour la dernière fois), moyen de la recontacter (un message privé PPF par défaut ; un numéro seulement si elle le choisit).
- Le signalement est **chiffré sur l'appareil** pour la clé de la legal team choisie (NIP-44 dans une enveloppe NIP-59, clé d'envoi jetable, comme les témoignages) : le serveur ne voit qu'une enveloppe adressée à la clé de la legal team.
- Seules les legal teams **vérifiées** de l'annuaire d'« Agir » (M3, type « legal team », clé `p`) peuvent recevoir ; recommandation : seulement celles qui déclarent un avocat référent (à confirmer par l'avocat, section 1).
- Côté appareil de l'expéditeur : rien n'est gardé après l'envoi, sauf, si la personne le veut, un rappel « Ai-je eu des nouvelles ? » sans le contenu (partie `agir-soutien` de l'effacement rapide, S-44).
- Côté legal team : le signalement s'ouvre dans son client d'équipe (le même que pour les témoignages, à construire avec le Directeur technique), avec effacement automatique proposé (recommandation : 30 jours après la clôture du cas).

### 2.2 Accueil bénévole à la sortie

- Une legal team ou un collectif publie un appel « Accueil à la sortie du commissariat » : un appel ordinaire (M12, AP-01) découpé en créneaux (AP-11, créneaux de bénévolat, à faire), avec le lieu au niveau de la ville ou du commissariat, **jamais le nom des personnes attendues**.
- Rien de nouveau côté données : les inscriptions aux créneaux suivent AP-11 (pseudonymes visibles du seul organisateur, chiffrés, puis effacés).

### 2.3 Agenda sans nom des comparutions

- Une legal team vérifiée publie un événement de type nouveau `comparution` dans l'agenda (NIP-52, kind 31923, `ppf-agenda-type`) : date et heure, tribunal, chambre, nombre de personnes concernées et la mobilisation dont il s'agit, appel à venir soutenir dans le calme ; **aucun nom, aucune initiale, aucun âge, aucune profession**, aucune photo. Le formulaire refuse un texte libre trop long et rappelle la règle ; la relecture avant publication (file `agir.held`) s'applique toujours.
- Le serveur refuse le type `comparution` à tout compte qui n'est pas une legal team vérifiée.
- Les audiences correctionnelles sont publiques (code de procédure pénale, article 400, à faire confirmer par l'avocat), mais publier les noms reviendrait à tenir un fichier d'infractions : jamais.

## 3. Menaces principales

- **Réquisition ou saisie du serveur** : il ne détient que des enveloppes illisibles et la clé publique de la legal team destinataire.
- **Fausse legal team** : seules les organisations vérifiées reçoivent ; la vérification d'une legal team (M3) mérite une attention particulière (contrôle de l'avocat référent).
- **Signalement malveillant** (désigner quelqu'un pour le faire rechercher) : la legal team reste seule juge ; aucune donnée n'est publiée.
- **Téléphone saisi de l'expéditeur** : rien n'est gardé après l'envoi.

## 4. Questions pour le chef d'équipe, avec recommandations

1. **Destinataires** : seulement les legal teams vérifiées qui déclarent un avocat référent ? Recommandation : **oui** en attendant l'avis de l'avocat (section 1), car c'est le cadre le plus sûr au regard de l'article 46.
2. **Effacement côté legal team** : effacement automatique 30 jours après la clôture du cas, réglable plus court ? Recommandation : **oui, 30 jours au plus**.
3. **Agenda des comparutions** : type `comparution` réservé aux legal teams vérifiées, sans aucun nom et relu avant publication ? Recommandation : **oui**.
4. **Ordre** : coder d'abord l'agenda sans nom (2.3, le moins sensible), puis le signalement (2.1) après l'avis de l'avocat et le client d'équipe, et l'accueil à la sortie avec AP-11 ? Recommandation : **oui**.

## 5. Pour l'avocat (à ajouter à la liste de la feuille de route)

- Une legal team sans avocat peut-elle recevoir ces signalements (article 46, 3°) ?
- Durée de conservation côté legal team.
- Publication des audiences sans nom : conditions, et rédaction du rappel du formulaire.
- Mentions à afficher à l'expéditeur (il transmet des données d'un tiers).
