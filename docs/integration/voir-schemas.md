# Schémas des briques de « Voir » (étape 2 bis du circuit éditorial)

Rédigé le 8 novembre 2026 par le pôle « Voir ». Suite de `docs/integration/circuit-editorial.md` (section « État ») et de la décision D-092 : les briques de « Voir » sont décrites par des schémas déclaratifs et passent par le même moteur que « Comprendre », dans `tools/content-signer` aujourd'hui, dans le back-office demain.

## Ce qui est fait

- `shared/core/.../core/voir/VoirSchemas.kt` : un schéma par type de `VoirCatalog.TYPES`, dans cet ordre : `indicateur`, `fait`, `personne`, `texte`, `mouvement`, `dossier`, `reponse`, `parti`. Ils sont ajoutés à `EditorialSchemas.all` (espace `voir`, paquet `voir.json`).
- `tools/content-signer/.../VoirContent.kt` : chaque fichier de `content/voir/**` est vérifié puis construit par le moteur (`engineEvent`, comme pour « Comprendre ») ; c'est toujours le champ `type` qui choisit le schéma, les sous-dossiers ne servent qu'à ranger. `voirEvent`, `checkVoir` et `checkParty` sont supprimés. L'outil refuse encore un `slug` utilisé deux fois dans `content/voir`.
- Le test de non-régression (`NoRegressionTest`) reste vert : les 54 briques de `voir.json` sont identiques à l'octet près (signature mise à part). Aucun paquet n'a été régénéré.
- Ligne vide d'un champ répété : le moteur l'ignore, `voirEvent` en faisait une étiquette vide. Vérifié le 8 novembre 2026 : aucune brique embarquée n'a d'étiquette vide, aucun contenu de `content/voir` n'a de ligne vide ; rien à corriger.
- Tests : `VoirSchemasTest` (code commun de `shared/core`) et quatre cas de « Voir » dans `InvalidContentTest` de l'outil.

## Champs et règles

Les champs, étiquettes et valeurs sont ceux de `docs/FORMATS.md`, section 4 quinquies, et de `VoirTags` ; ils ne changent pas. Communs à plusieurs types : `source` (type, nom, adresse, date facultative), `correction` (date, motif), `next` (« Et maintenant ? »), `archive` (empreinte SHA-256, preuve `opentimestamps` ou `rfc3161`, adresse, date) pour les faits, `axis` pour les indicateurs et les dossiers.

Ce que l'outil vérifie maintenant, en plus de ce que faisaient `checkVoir` et `checkParty` :

- **tout champ est déclaré** : un champ inconnu est refusé au lieu de devenir une étiquette `ppf-` que personne ne lit ;
- **chaque valeur a son type** : listes fermées (types de source, statuts, étapes, rôles…), dates `AAAA-MM-JJ`, adresses `https://`, nombres écrits à la française, empreinte de 64 caractères hexadécimaux, identifiants officiels (`an:…`, `senat:…`, `pe:…` sur une personne ; `an-dossier:…` sur un texte, comme `ParliamentLinks`) ;
- **sources** : au moins une pour un fait, une personne, un texte, un mouvement et un parti ; l'adresse d'une source est obligatoire (celle d'une mesure reste facultative, les signalements n'en ont pas) ;
- **règles croisées** : une affaire a toujours son étape de procédure ; une peine (`sentence`, `sentence-served`) ne s'écrit que pour une affaire ; jamais le nom d'un mandataire financier ; une seule aide publique par année ; identifiants de mesure, types de source retenus, rôles et dates de 49.3 sans doublon ; la part d'une mesure ne dépasse pas sa valeur ; `espace:` nomme un espace de l'application ;
- **liens** : `person` et `leader` vers une personne, `party` vers un parti, `text` vers un texte, `indicator` vers un indicateur, `target` (droit de réponse) vers une brique de « Voir », `fiche:` vers une fiche, `voir:` vers une brique de « Voir » ;
- **lecture par l'application** : la brique doit être lue (comme avant) et **aucune ligne ne doit être ignorée** (mesures, sièges, aides publiques, mandats, sources, corrections, étapes, impacts, revendications, résultats…) : l'outil compare le nombre de lignes écrites et le nombre de valeurs que lit l'application.

## Écarts et points ouverts

- `FieldType.Date(yearOnly)` n'est pas utilisé : l'application lit toutes les dates de « Voir » en `AAAA-MM-JJ` (date de fondation d'un parti, période d'un mouvement comprises).
- Par rapport à l'exemple de la section 1.3 de `circuit-editorial.md` : `website` d'un parti ne se donne qu'une fois (l'application n'en lit qu'un) ; un nombre de sièges est un entier de 0 à 999 ; le résumé est obligatoire sauf pour un droit de réponse ; aucune section « ## Sources » n'est exigée dans le corps (les sources de « Voir » sont des étiquettes).
- Pas encore décrit : le lien d'un fait vers un Pacte de « Décider » (`a` de rôle `pacte` et `ppf-pacte-measure`, section 4 septies de `FORMATS.md`), faute de type cible dans le moteur et de contenu qui l'utilise. À ajouter avec « Décider » quand un fait de suivi existera.
- Les libellés (`voir.field.*`, `voir.help.*`, préfixes des listes) sont des clés de traduction, pas encore écrites dans les textes du back-office.
