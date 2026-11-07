# Contrat `core/urne` : compter sans jamais relier (pour « Agir » et « Voir »)

- Pôle « Décider », 7 novembre 2026. Exigence R6 de D-110. Conception : `docs/integration/une-personne-une-voix.md` ; formats : `docs/FORMATS.md`, section 4 nonies ; décisions : D-110, D-112, D-113.
- Disponible pour les autres pôles quand la branche de « Décider » sera entrée dans `main` (migration serveur `V7`, migration locale `11.sqm`, numéro provisoire). D'ici là, aucun pôle n'écrit de compteur à lui.

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
- `closesAt` : la fin naturelle (fin de la mobilisation, fin de la période de signalement) ; à la clôture, tout est publié et recomptable.
- `liveResults` : `true` pour un compteur affiché en direct (participation, soutiens), `false` pour une question dont le résultat ne doit pas influencer les votants.
- L'ouverture se fait dans le code serveur du pôle, au moment où l'objet devient comptable (une mobilisation publiée, un appel publié, un fait ouvert aux signalements). Aucune route publique ne permet d'ouvrir une urne.

### 2.2 Côté application
- `UrneSession` (code commun) : `sync(signer)` récupère les jetons, `vote(urne, choix, choix possibles)` enregistre le choix, `sendDue(difficulté)` envoie les bulletins dont le moment est venu, `choiceOf(urne)` dit ce que l'appareil a choisi et si le serveur l'a, `forget()` efface tout (avec les clés). Elle sera fournie par `AppDependencies`, une seule pour toute l'application.
- `VoteControl(choices, state, onChoose)` et `UrneResultsView(journal, labels)` (interface commune, `ui/urne/`) : le composant de vote et l'affichage des résultats, les mêmes partout.
- `UrneRemote.journal(n)` : le journal public d'une urne (comptes et jetons par niveau, bulletins, résultats si l'urne les montre).

## 3. Ce qui est interdit aux pôles

1. Écrire un compteur à soi de participations, de soutiens, de votes ou de signalements (en base, en mémoire ou dans un événement) : tout compte passe par une urne.
2. Envoyer un choix, une participation ou un signalement **avec le compte** (signature, NIP-98, clé publique, identifiant d'appareil), ou les journaliser.
3. Appeler `Voprf`, `UrneTokenStore` ou les routes `/api/v1/urnes/tokens` et `/api/v1/ballots` directement : seulement `UrneSession`.
4. Proposer un « envoi immédiat » : le délai aléatoire avant l'envoi est une protection commune (D-112), sans réglage.
5. Additionner le vote des élus et celui des citoyens, ou montrer un total qui les mêle (D-112).
6. Garder le choix d'un citoyen ailleurs que dans `UrneTokenStore` (pas de copie dans une autre table ni dans l'état d'un écran au-delà de son affichage).
7. Déduire d'un bulletin, d'un jeton ou d'une heure quoi que ce soit sur une personne.

## 4. Participation visible (D-044) : à côté, jamais à la place

La décision de la propriétaire du 7 novembre 2026 (participation publique, élus et organisations affichés, chacun pouvant masquer la sienne tout en comptant) reste un **affichage choisi par la personne** : une réponse signée publiquement (par exemple NIP-52 pour une mobilisation), publiée seulement si la personne le décide. Le **nombre** de participants, lui, vient toujours de l'urne : une personne qui se montre et une personne qui reste discrète comptent chacune une fois, et on ne peut jamais retrouver qui, parmi les bulletins, s'est montré.

## 5. Ce que « Décider » garantit

- Le même code pour tous : les vecteurs de la RFC 9497, l'autotest de l'appareil, le recomptage public (`UrneRecount`).
- Un jeton par compte et par urne ; le niveau du votant figé la semaine de l'ouverture ; bulletins modifiables et retirables jusqu'à la clôture.
- Toute évolution du contrat passe par le chef d'équipe, qui la diffuse.
