# Types de compte : personne ou collectif (D-133)

Pôle Communauté, version 1.0 du 7 novembre 2026, contrat adopté par le pôle Décider (branche `feature/decider-une-voix`, 27e533c).

Décision de la propriétaire (7 novembre 2026) : « Un compte de type collectif ou association ou bref tout truc qui n'est pas personne physique ne peut pas voter. »

## 1. Déclaration

- Le type d'un compte est écrit dans son **profil signé** (événement de type 0, NIP-01), par l'étiquette `["ppf-account-type", "person"]` ou `["ppf-account-type", "collective"]`.
- **Absente, elle vaut `person`** : tous les comptes existants sont des comptes de personne.
- Une personne a **un seul compte personnel**, le seul qui vote (correction de la propriétaire, 7 novembre 2026, 22 h 50 : « Il n'y a que le compte personnel qui vote ! »). Elle peut gérer en plus des comptes associatifs, syndicaux ou de collectif, créés par un parcours à part de Mon espace (« Créer le compte d'un collectif ») qui publie ce profil dès la création ; le sélecteur de comptes de Mon espace passe de l'un aux autres (après M28). Pas de fonction pour plusieurs pseudonymes personnels.
- **Verrouillage** : une fois qu'un profil `collective` a été reçu pour une clé, le serveur refuse tout profil plus récent de cette clé qui ne porte pas `collective` (`409 account_type_locked`). Un collectif ne peut donc pas redevenir une « personne » pour voter.

## 2. Côté serveur (pôle Communauté)

- Table `account_type(pubkey, type, declared_at)` : clé publique et type seulement, écrite quand un profil `collective` est stocké. Aucune donnée personnelle de plus : le profil lui-même est public.
- `AccountTypes.isCollective(pubkey)` (paquet `server/accounts`) : vrai si la clé a déclaré un profil `collective`.
- Vérification dans l'API des événements avant le stockage d'un profil (verrouillage), enregistrement après.

## 3. Côté Décider

- Point d'appel unique : `PersonAccounts.refusal(pubkey)` (`server/urne/PersonAccounts.kt`), appelé par `UrneService` à l'inscription et à chaque demande de jetons ; réponse `403 not_a_person` pour les clés éditoriales, les organisations vérifiées d'Agir et les comptes déclarés collectifs.
- En attendant `AccountTypes` dans `main`, Décider lit lui-même le dernier profil de type 0 ; il branche ensuite `AccountTypes.isCollective` et retire sa lecture provisoire. Le type commun de l'appareil est `core/identity/AccountType`.

## 4. Limite assumée

Un collectif peut mentir en prenant un compte de personne : ce compte reste alors dans la colonne « non vérifiés ». Seule la validation en personne garantit une personne physique, et un validateur ne valide jamais le compte d'une organisation (`urne-contrat.md`, interdiction n° 9).
