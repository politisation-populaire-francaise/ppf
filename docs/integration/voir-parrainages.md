# Parrainages de la présidentielle 2027 (VO-37) : conception, version 1.0

Rédigé le 8 novembre 2026 par le pôle « Voir » (« [Voir] Team Dev 2 »), à la demande du chef d'équipe, d'après le cahier fonctionnel (M20, VO-37 : « Listes publiées par le Conseil constitutionnel, par candidat et par département ; « mon maire, mon député a parrainé » dans Mes élus (TE-01) ; affichage factuel identique pour tous, sans bouton pour interpeller les maires ; repris dans le comparateur (DE-16) »), la règle transverse n° 9 (traitement identique) et le cahier des charges (section 9.2, outils électoraux). Branche `feature/voir-presence`, pas encore compilée.

## 0. Ce que dit le droit (vérifié le 8 novembre 2026)

- **Loi n° 62-1292 du 6 novembre 1962, article 3**, version en vigueur depuis le 1er janvier 2026 (Légifrance, https://www.legifrance.gouv.fr/loda/id/JORFTEXT000000684037) : les présentations émanent d'« au moins cinq cents » élus habilités (membres du Parlement, des conseils régionaux, de l'Assemblée de Corse, des conseils départementaux, du Conseil de Paris, maires, maires délégués, maires d'arrondissement, conseillers à l'Assemblée des Français de l'étranger, présidents d'intercommunalités, membres français du Parlement européen élus en France, etc.), élus d'au moins trente départements ou collectivités d'outre-mer, sans que plus d'un dixième d'entre eux soient élus d'un même département ; elles parviennent au Conseil constitutionnel au plus tard le sixième vendredi précédant le premier tour à dix-huit heures. Le Conseil constitutionnel rend publics, « au moins deux fois par semaine », le nom et la qualité des élus qui ont valablement présenté des candidats, puis la liste complète huit jours au moins avant le premier tour.
- **Calendrier 2027** (à confirmer par le décret de convocation, publié au Journal officiel) : d'après la presse et les sites spécialisés, premier tour le 18 avril 2027 et second tour le 2 mai 2027 (choix du Conseil des ministres du 1er juillet 2026), décret au plus tard dix semaines avant le premier tour, recueil jusqu'au vendredi 12 mars 2027 à 18 heures. Le site officiel info.gouv.fr n'a pas pu être lu (refus d'accès automatique) : **ces dates ne sont pas encore vérifiées sur une source officielle** ; l'application n'en affiche aucune tant que le décret n'est pas paru.
- **Données de 2022** : jeu « Parrainages des candidats à l'élection présidentielle française de 2022 » publié par le Conseil constitutionnel sur data.gouv.fr (identifiant `61f109d1fdf76c4be0604d0c`, fréquence « deux fois par semaine », licence « Other (Public Domain) », identifiant `other-pd` de data.gouv.fr), en CSV, JSON et XLSX, un fichier complet à chaque publication (`parrainagestotal.json`, 3,5 Mo le 7 mars 2022). Champs : `Civilite`, `Nom`, `Prenom`, `Mandat`, `Circonscription`, `Departement`, `Candidat`, `DatePublication` (JSON ; le CSV est séparé par des points-virgules, dates JJ/MM/AAAA). Exemples de contenus : maire → commune dans `Circonscription` ; député → « 5ème circonscription » ; sénateur, conseiller départemental, conseiller de Paris → `Circonscription` vide ; conseiller régional → la région ; eurodéputé → `Departement` « Parlement européen ».
- **Hypothèse pour 2027** : le Conseil constitutionnel publie de la même façon sur data.gouv.fr. Si la forme change, seul l'outil d'import change ; le paquet de PPF reste le même.

## 1. Principes

1. **Les listes officielles, rien d'autre** : seuls les parrainages publiés par le Conseil constitutionnel, avec la date de leur publication ; aucune estimation, aucune liste de « promesses ».
2. **Identique pour tous** (règle n° 9) : candidats dans l'ordre alphabétique, les mêmes chiffres pour chacun (nombre de parrainages publiés, nombre de départements ou collectivités cités, plus grand nombre dans un même département), la règle de la loi rappelée à côté, et la phrase « Seul le Conseil constitutionnel arrête la liste des candidats ». Pas de badge « qualifié », pas de couleur par candidat, pas de classement.
3. **Pas d'interpellation** : aucun bouton pour écrire, appeler ou « relancer » un élu ; aucune liste « n'a pas encore parrainé ».
4. **Minimisation** : la civilité n'est jamais gardée ; le mandat est réduit à un code neutre (« Maire », « Membre de l'Assemblée nationale »…), sans le genre du libellé officiel ; aucun autre champ.
5. **L'appareil fait le tri** : le paquet est le même pour tout le monde ; « mon maire, mon député a parrainé » est calculé sur l'appareil à partir de « Mes élus » ; le serveur ne sait jamais quelle commune ni quel candidat intéresse la personne.

## 2. Paquet `parrainages-2027`

Dans l'index signé des données ouvertes (même clé d'import, même route `/api/v1/parlement/<nom>`, même contrôle d'empreinte sur l'appareil), source `cc` (« Conseil constitutionnel, parrainages de l'élection présidentielle », licence « domaine public », adresse du jeu sur data.gouv.fr).

```json
{
  "format": 1,
  "example": true,
  "election": "presidentielle-2027",
  "publishedOn": "2027-03-02",
  "sponsorships": [
    { "lastName": "[EXEMPLE] DURAND", "firstName": "Camille", "mandate": "maire", "territory": "Commune d'exemple", "department": "Rhône", "departmentCode": "69", "candidate": "[EXEMPLE] CANDIDATE Alice", "publishedOn": "2027-02-16" }
  ]
}
```

- `mandate` : `maire`, `maire-delegue`, `maire-arrondissement`, `depute`, `senateur`, `depute-europeen`, `conseiller-departemental`, `conseiller-regional`, `conseiller-paris`, `conseiller-metropolitain-lyon`, `conseiller-corse`, `conseiller-afe`, `president-intercommunalite`, `autre` (avec `otherMandate`, libellé officiel, pour un cas imprévu).
- `departmentCode` : code du département ou de la collectivité quand le nom est reconnu (table fixe de l'outil d'import), absent sinon (« Parlement européen », « Français établis hors de France »).
- Trié par candidat, département, nom, prénom : deux imports identiques donnent les mêmes octets.
- **Garde-fou** : un import qui fait baisser le nombre de parrainages de plus de 20 % est refusé (fichier tronqué), comme pour le Parlement.

## 3. Import (`tools/open-data-importer`)

- Option `--sponsorships` et variable `PPF_SPONSORSHIPS_DATASET` (identifiant du jeu de 2027 sur data.gouv.fr, à renseigner dès sa création) ; sans elle, rien n'est demandé et le paquet précédent reste servi.
- Hôtes contactés en mode `official` seulement : `www.data.gouv.fr` (l'API qui donne la dernière ressource JSON du jeu) et `static.data.gouv.fr` (le fichier) ; HTTPS, sans redirection, taille plafonnée. **À inscrire dans la liste des sorties avec le Directeur technique.**
- Fréquence : chaque jour pendant la période de recueil (minuteur), deux publications par semaine attendues.
- Mode `example` : jeu fictif `example/cc/parrainages.json` au format officiel, tout marqué [EXEMPLE].

## 4. Sur l'appareil

- Cœur : `core/voir/parrainages/` : `SponsorshipsPackage` (format), `SponsorshipsCatalog` (par candidat, par département), `MySponsors` (rapprochement avec « Mes élus »), `SponsorshipsLoader` (comme le paquet `semaine`).
- **Rapprochement** (jamais sur le nom seul) : maire → même département, même commune (nom replié) et même nom ; député → même département, même numéro de circonscription et même nom ; sénateur, conseiller départemental, conseiller régional → même département et même nom ; eurodéputé → « Parlement européen » et même nom. Un élu sans correspondance n'est pas montré comme « n'a pas parrainé » : la page dit seulement ce qui est publié.
- Écran : page « Parrainages 2027 » dans le Parlement de « Voir » (candidats par ordre alphabétique avec leurs chiffres et la règle de la loi ; liste d'un candidat par département ; filtre par département ; « Vos élus ont parrainé » quand une commune est choisie). « Mes élus » (TE-01, Comprendre) et le comparateur (DE-16, Décider) lisent le même `SponsorshipsCatalog`.

## 5. Données personnelles

Noms et mandats d'élus, rendus publics par la loi (article 3), repris tels quels sans la civilité. Sur le serveur : fichier du paquet seulement (pas de base). Sur l'appareil : le paquet, effacé par l'effacement rapide (S-44, partie « voir-data », table des paquets du Parlement). Après l'élection, le paquet reste comme archive publique de l'élection.

## 5 bis. État au 8 novembre 2026 (branche `feature/voir-presence`, pas encore compilée)

- Cœur : `core/voir/parrainages/Sponsorships.kt` (`SponsorMandate.of`, `Sponsorship`, `SponsorshipsPackage`, `SponsorshipsFormat`, `CandidateSponsors`, `SponsorshipsCatalog`), `MySponsors.kt`, `SponsorshipsLoader.kt` ; tests `SponsorshipsTest`, `SponsorshipsLoaderTest`.
- Import : `tools/open-data-importer/.../Sponsorships.kt` (`CcSponsorships.read`, `SponsorshipsImport`), `OfficialSources.sponsorships()` (API data.gouv.fr puis fichier JSON le plus récent du jeu), option `--only cc` et `PPF_SPONSORSHIPS_DATASET`, `ALLOWED_HOSTS` complété ; exemple `example/cc/parrainages.json` (format officiel, [EXEMPLE], noms rapprochables des élus d'exemple du serveur) ; test `SponsorshipsImportTest`. Par défaut, l'import ne demande les parrainages qu'en production et seulement quand le jeu est renseigné ; en préproduction, ajouter `cc` à `--only`.
- Application : `ParliamentPage.Sponsorships` et `SponsorshipsOf`, `SponsorshipsPage.kt`, `SponsorshipsState.kt`, carte sur la liste du Parlement, `strings_voir_parrainages.xml` ; test `SponsorshipsScreenTest`.
- Pour les autres pôles : « Mes élus » (Comprendre, TE-01) lit `MySponsors.of(officials, catalog)` ; le comparateur (Décider, DE-16) lit `SponsorshipsCatalog.candidates()` (mêmes chiffres pour tous) ; tous deux obtiennent le catalogue par `SponsorshipsLoader.held(snapshot)`.

## 6. Étapes et tests

| Étape | Contenu | Tests |
|---|---|---|
| 1 | format, catalogue, rapprochement (cœur) | ordre alphabétique ; chiffres identiques ; minimisation ; rapprochements et absence de devinette |
| 2 | import (exemple et officiel), garde-fou, hôtes | lecture du JSON officiel de 2022 ; mandats ; codes de département ; tri stable ; baisse de plus de 20 % refusée |
| 3 | écran, « Mes élus », comparateur | aucune action vers les élus ; mêmes chiffres pour chaque candidat ; lecteur d'écran |
