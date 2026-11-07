# Contrat `core/urne` : compter sans jamais relier (pour « Agir » et « Voir »)

- Pôle « Décider », 7 novembre 2026. Exigence R6 de D-110. Conception : `docs/integration/une-personne-une-voix.md` ; formats : `docs/FORMATS.md`, section 4 decies ; décisions : D-110, D-112, D-113.
- Disponible pour les autres pôles quand la branche de « Décider » sera entrée dans `main` (migration serveur `V9`, migration locale `13.sqm`). D'ici là, aucun pôle n'écrit de compteur à lui.

## 1. À quoi il sert

Tout ce qui compte des personnes, une par compte, sans qu'on puisse savoir qui :
- **« Agir »** : le nombre de participants à une mobilisation (« J'y participe »), le nombre de soutiens d'un appel ;
- **« Voir »** : les signalements chiffrés des citoyens (VO-03), un par compte et par événement ;
- **« Décider »** : votations, votes des revendications, délibération sur le Pacte et les campagnes.

Chaque objet compté a son **urne**. Un bulletin ne porte que l'urne, le niveau du votant et son choix ; jamais le compte.

## 2. Ce que chaque pôle utilise

### 2.1 Côté serveur : ouvrir une urne
```kotlin
// server/urne/UrneService.kt
fun open(id: String, subject: String, choices: List<String>, opensAt: Instant, closesAt: Instant?, liveResults: Boolean): Urne
```
- `id` : stable et unique, de la forme `<pôle>:<usage>:<adresse de l'objet>`, par exemple `agir:participation:31923:<clé>:<d>` ou `voir:signalement:<adresse du fait>`. Ouvrir deux fois le même `id` rend la même urne.
- `subject` : l'adresse de l'objet compté (`kind:clé:d`).
- `choices` : la liste fermée des choix, sans `retire` (réservé au retrait). Pour une participation : `["participe"]`. Pour un signalement chiffré : des tranches convenues avec « Décider » (un bulletin ne porte jamais un nombre libre ni un texte).
- `opensAt` : maintenant (ou un moment passé), **jamais un moment futur** : une urne reçoit son numéro d'ordre à l'ouverture et la délivrance des jetons suit cet ordre, si bien qu'une urne numérotée maintenant et ouverte plus tard serait sautée par les comptes servis entre-temps (relecture R5). Ouvrir l'urne au moment où l'objet devient comptable ; `open` refuse un `opensAt` futur.
- `closesAt` : la fin naturelle (fin de la mobilisation, fin de la période de signalement) ; à la clôture, tout est publié et recomptable.
- **Clôture anticipée** : `UrneService.closeEarly(id, at)` avance la fin à `at` (ou à maintenant si `at` est passé), ne la recule jamais, et ferme tout de suite une urne dont la fin est atteinte ; rend l'urne telle qu'elle est, ou `null` si l'identifiant est inconnu. Disponible depuis le 7 novembre 2026.
- `liveResults` : `true` pour un compteur affiché en direct (participation, soutiens), `false` pour une question dont le résultat ne doit pas influencer les votants.
- L'ouverture se fait dans le code serveur du pôle, au moment où l'objet devient comptable (une mobilisation publiée, un appel publié, un fait ouvert aux signalements). Aucune route publique ne permet d'ouvrir une urne.

### 2.2 Côté application
- `UrneSession` (code commun) : `sync(signer)` récupère les jetons, `vote(urne, choix, choix possibles)` enregistre le choix, `sendDue(difficulté)` envoie les bulletins dont le moment est venu, `choiceOf(urne)` dit ce que l'appareil a choisi et si le serveur l'a, `forget()` efface tout (avec les clés). Elle sera fournie par `AppDependencies`, une seule pour toute l'application.
- `VoteControl(choices, state, onChoose)` et `UrneResultsView(journal, labels)` (interface commune, `ui/urne/`) : le composant de vote et l'affichage des résultats, les mêmes partout.
- `UrneRemote.journal(n)` : le journal public d'une urne (comptes et jetons par niveau, bulletins, résultats si l'urne les montre). Pour afficher des compteurs, lire plutôt `UrneInfo.results` dans la liste publique (`UrneRemote.list`), qui porte les résultats de toutes les urnes qui les montrent : demander le journal d'une seule urne dit au serveur ce qu'on regarde.

## 2 ter. Participation et soutiens d'« Agir », convenus avec « Agir » le 7 novembre 2026

- **Mobilisation** : une seule urne `agir:participation:31923:<clé>:<d>`, choix `["participe", "interesse"]` (deux nombres affichés) ; passer de l'un à l'autre remplace le bulletin, `retire` le retire.
- **Appel** : `agir:soutien:30023:<clé>:<d>`, un seul choix `soutien` ; le libellé dépend du type de l'appel (« Je soutiens », « J'ai signé », « J'aide », « J'y serai »), la valeur reste fermée.
- **Ouverture** quand le contenu devient public (stocké directement, ou approuvé dans la file de relecture) ; `closesAt` = fin de la mobilisation (ou début plus deux heures sans fin), échéance ou clôture de l'appel (`null` sans échéance) ; **clôture anticipée** par `UrneService.closeEarly(id, at)` pour une mobilisation annulée ou un appel clos par son auteur.
- **Organisations** : elles ne votent jamais ; leur participation publique (réponse NIP-52 de type 31925, D-045) est affichée à part (« Organisations participantes : N »), la personne derrière le compte votant avec son compte personnel. Élus : un nombre séparé, jamais additionné.
- **Sans compte** : un pense-bête sur l'appareil (« Ajouté à mon agenda »), sans le mot « participe », n'est ni un vote ni un compteur ; compter demande un compte.
- **Hors ligne** : le vote attend dans `UrneSession` et part au retour du réseau ; une seule tâche de l'application appelle `sync` et `sendDue` (branchée par « Décider »).

## 2 bis. Signalements chiffrés de « Voir » (VO-03), convenus avec « Voir » le 7 novembre 2026

- **Une urne par couple (indicateur, occasion)** : `id` = `voir:signalement:<adresse de l'indicateur>:<occasion>`, l'occasion étant l'identifiant stable de la journée ou de l'événement (une brique de « Voir » ou une mobilisation de l'agenda, type 31923). Une même journée a donc une urne par indicateur.
- **Tranches fermées, déclarées par l'indicateur** (une étiquette de l'indicateur, lue à l'ouverture de l'urne) : par exemple `0`, `1-2`, `3-5`, `6-10`, `11-20`, `21-50`, `51+` pour des blessés vus ; `0`, `1-9`, `10-99`, `100-999`, `1000+` pour des interpellations vues. Jamais un nombre libre, jamais un texte.
- **Résultat** : la répartition par tranche, telle quelle ; jamais une moyenne ni un total calculé à partir des tranches. Une vérification par l'équipe décide si la répartition entre dans l'observatoire, toujours à côté des autres sources, jamais fusionnée avec elles (méthode de M2).
- **Fenêtre** : ouverture le jour de l'événement, clôture 7 jours après (souvenirs frais, pas de gonflement tardif) ; résultats cachés jusqu'à la clôture (`liveResults = false`) pour ne pas influencer les signalements ; le niveau du votant est figé à l'ouverture, comme partout.
- **Rien de géolocalisé dans le bulletin** (ni lieu précis, ni heure fine) : si un territoire compte, c'est une urne par territoire, jamais une information donnée par le votant.
- Longueur : `id` et `subject` acceptent jusqu'à 400 caractères (deux adresses de contenu y tiennent).

## 3. Ce qui est interdit aux pôles

1. Écrire un compteur à soi de participations, de soutiens, de votes ou de signalements (en base, en mémoire ou dans un événement) : tout compte passe par une urne.
2. Envoyer un choix, une participation ou un signalement **avec le compte** (signature, NIP-98, clé publique, identifiant d'appareil), ou les journaliser.
3. Appeler `Voprf`, `UrneTokenStore` ou les routes `/api/v1/urnes/tokens` et `/api/v1/ballots` directement : seulement `UrneSession`.
4. Proposer un « envoi immédiat » : le délai aléatoire avant l'envoi est une protection commune (D-112), sans réglage.
5. Additionner le vote des élus et celui des citoyens, ou montrer un total qui les mêle (D-112).
6. Garder le choix d'un citoyen ailleurs que dans `UrneTokenStore` (pas de copie dans une autre table ni dans l'état d'un écran au-delà de son affichage).
7. Déduire d'un bulletin, d'un jeton ou d'une heure quoi que ce soit sur une personne.
8. Envoyer au serveur, ou y garder, une donnée d'identité civile (nom, e-mail, téléphone, pièce ou photo d'identité), même facultative ou temporaire : elle devrait ensuite être conservée cinq ans (décret n° 2021-1362, article 2 ; note `docs/integration/lcen-conservation-identification.md`). La validation en personne reste hors du serveur ; il ne reçoit que le résultat (compteurs de séance, lien scellé pour la clé « audit »), et pour un élu la référence publique de son mandat dans le répertoire officiel.

## 4. Participation visible (D-044) : à côté, jamais à la place

La décision de la propriétaire du 7 novembre 2026 (participation publique, élus et organisations affichés, chacun pouvant masquer la sienne tout en comptant) reste un **affichage choisi par la personne** : une réponse signée publiquement (par exemple NIP-52 pour une mobilisation), publiée seulement si la personne le décide. Le **nombre** de participants, lui, vient toujours de l'urne : une personne qui se montre et une personne qui reste discrète comptent chacune une fois, et on ne peut jamais retrouver qui, parmi les bulletins, s'est montré.

## 5. Ce que « Décider » garantit

- Le même code pour tous : les vecteurs de la RFC 9497, l'autotest de l'appareil, le recomptage public (`UrneRecount`).
- Un jeton par compte et par urne ; le niveau du votant figé la semaine de l'ouverture ; bulletins modifiables et retirables jusqu'à la clôture.
- Une relecture indépendante (R5, 7 novembre 2026) : constats et corrections dans `docs/THREAT_MODEL.md`.
- Toute évolution du contrat passe par le chef d'équipe, qui la diffuse.
