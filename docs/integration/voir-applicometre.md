# Applicomètre (VO-36) : conception, version 1.0

Rédigé le 9 novembre 2026 par le pôle « Voir » (« [Voir] Team Dev 2 »), à la demande du chef d'équipe, d'après le cahier fonctionnel (M20, VO-36 : « Pour chaque loi, les décrets d'application attendus et publiés, les retards et le taux d'application, reliés aux promesses (VO-12) »), le cahier des charges (section 3.8, règles communes des imports et sources de la version 2.2) et la règle transverse n° 9 (traitement identique). Branche `feature/voir-presence`, pas encore compilée.

## 0. La source (vérifiée le 9 novembre 2026)

- **Jeu « Données du baromètre de l'application des lois »** sur data.gouv.fr (https://www.data.gouv.fr/datasets/donnees-du-barometre-de-lapplication-des-lois, identifiant `6825af1382d4b7afed51de59`), publié par **LexImpact**, l'équipe de l'Assemblée nationale (contact `leximpact@assemblee-nationale.fr`), pour le baromètre de l'Assemblée (https://www.assemblee-nationale.fr/dyn/taux-application-lois/). Licence `lov2` de data.gouv.fr : **Licence Ouverte 2.0** (réutilisation libre, mention de la source). Fréquence « quotidienne », dernière mise à jour le 6 novembre 2026. D'après sa description, il réunit les données ouvertes de Légifrance (DILA, les échéanciers de mise en application des lois), celles de l'Assemblée nationale et des calculs de LexImpact.
- **Deux fichiers CSV**, séparés par des points-virgules, sans guillemets, en UTF-8, aux adresses stables du site du baromètre :
  - `https://barometre.assemblee-nationale.fr/barometre_application_lois_assemblee.csv` (« Liste des lois », 1,4 Mo) : 21 colonnes, de « Date de publication de la loi » à « Mots-clés » ; 2 065 lois promulguées depuis juillet 2002 (12e législature), dont 150 de la 17e législature (1 011 mesures à prendre), 140 de la 16e (1 235) et 353 de la 15e (3 333).
  - `https://barometre.assemblee-nationale.fr/barometre_application_mesures_assemblee.csv` (« Liste des mesures », 7,1 Mo) : 17 colonnes, de « Titre loi » à « Délai d'application (en jours) » ; une ligne par mesure de l'échéancier de Légifrance ; « État » vaut « appliqué », « sans objet » ou « en attente d'application » ; « Date prévue » est vide dans les lignes lues, la date annoncée se trouve dans la colonne des observations (« Publication envisagée en mars 2027 »).
  - Les deux fichiers se joignent par l'identifiant Légifrance de la loi (`JORFTEXT…`) ; la liste des lois porte aussi l'identifiant du dossier législatif de l'Assemblée (`DLR5L17N…`, depuis 2012), le même que celui des scrutins et des briques « texte » de « Voir » (`ppf-official-id` = `an-dossier:<uid>`).
- **Non documenté par la source** : la formule du « taux d'application » (les lignes lues montrent « mesures appliquées / mesures à appliquer », et 1 pour une loi sans mesure) et le calcul du délai. L'application affiche donc les deux nombres de la source et une part arrondie à l'unité inférieure, jamais 100 % tant qu'une mesure manque, et rien pour une loi qui n'attend aucune mesure ; le délai d'une mesure prise est présenté comme « calculé par LexImpact ».
- **Non vérifié, donc non affiché** : l'objectif de publication des décrets dans les six mois suivant la loi (circulaire du Premier ministre du 27 décembre 2022, d'après la presse) ; le texte n'a pas pu être lu sur Légifrance. L'application ne parle de « retard » qu'au sens de la date annoncée par le gouvernement lui-même dans l'échéancier.

## 1. Principes

1. **Les chiffres de la source, les mêmes pour chaque loi** : mesures prises sur mesures attendues, mesures en attente, devenues sans objet ; aucune note, aucun classement des lois, des ministères ou des gouvernements ; la présidence, le gouvernement du moment et les mots-clés ne sont pas gardés.
2. **Un retard se dit avec des faits** : le temps écoulé depuis la publication de la loi (en mois entiers) et, quand l'échéancier l'annonce, le mois prévu par le gouvernement, « date passée » une fois ce mois fini. Une formulation vague (« premier semestre ») n'est jamais interprétée : l'observation est montrée telle quelle.
3. **Relié aux promesses** : la fiche d'un texte de « Voir » qui porte son dossier de l'Assemblée montre l'application de sa loi ; une promesse (VO-12) liée à ce texte la montre aussi ; la page d'une loi renvoie vers la fiche du texte dans « Voir » et vers ses scrutins publics présents sur l'appareil.
4. **Le même paquet pour tous** : un paquet par législature, téléchargé sur un geste, taille affichée ; le serveur ne sait pas quelles lois la personne consulte ; la recherche et les filtres restent en mémoire.

## 2. Paquets `application-lois-<législature>`

Dans l'index signé des données ouvertes (même clé d'import, même route `/api/v1/parlement/<nom>`, même contrôle d'empreinte sur l'appareil), source `an-barometre` (« Assemblée nationale (LexImpact), baromètre de l'application des lois, d'après les échéanciers de Légifrance », Licence Ouverte 2.0). Législatures importées : 17, 16 et 15 (`ApplicationFormat.LEGISLATURES`).

```json
{
  "format": 1,
  "example": true,
  "legislature": 17,
  "updatedOn": "2026-11-07",
  "laws": [
    {
      "id": "JORFTEXT000099000001",
      "title": "[EXEMPLE] LOI n° 2026-9001 du 18 août 2026 relative au logement abordable",
      "publishedOn": "2026-08-18",
      "legislature": 17,
      "dossier": "an-dossier:DLR5L17N90001",
      "dossierPath": "exemple_logement_abordable",
      "committee": "[EXEMPLE] Commission des affaires économiques",
      "toApply": 4,
      "applied": 1,
      "measures": [
        { "order": 2, "articles": "Article 3, I", "legalBasis": "…", "subject": "…", "observation": "Publication envisagée en octobre 2026", "state": "en-attente", "plannedMonth": "2026-10" },
        { "order": 1, "articles": "Article 1er", "observation": "[EXEMPLE] Décret n° 2026-9101 du 27/9/2026", "decree": "JORFTEXT000099100001", "state": "applique", "delayDays": 40 }
      ]
    }
  ]
}
```

- `state` : `applique`, `sans-objet`, `en-attente` ; un état inconnu fait refuser le fichier (jamais deviné).
- Drapeaux facultatifs (écrits seulement quand ils sont vrais) : `directApplication`, `ratification`, `enabling` (loi d'habilitation), `ordinanceRatification`, `withoutSchedule` (pas d'échéancier sur Légifrance).
- Lois triées de la plus récente à la plus ancienne, mesures par numéro d'ordre : deux imports identiques donnent les mêmes octets.
- **Garde-fou** : un paquet qui compte plus de 20 % de lois de moins que le précédent est refusé (fichier tronqué) et les paquets précédents restent servis.
- **Source à part** : une erreur de l'Applicomètre (téléchargement, garde-fou, fichier illisible) n'arrête jamais l'import du Parlement ; le journal dit pourquoi (`Importer.apart`, comme pour les parrainages).
- Modèles : `shared/core/.../core/voir/application/` (`LawApplication.kt`, `ApplicationCatalog.kt`, `ApplicationLoader.kt`) ; chargeur commun aux paquets à part (`core/voir/parlement/OpenDataPackageLoader.kt`, repris aussi par les parrainages de VO-37).

## 3. Import

- `tools/open-data-importer/.../Application.kt` : `BarometerCsv` lit chaque ligne à partir de l'identifiant Légifrance (un point-virgule dans un texte ne décale pas les colonnes) ; `ApplicationImport` produit un paquet par législature et la source de l'index.
- Option `--only an-barometre` (seulement par son nom, ni par défaut ni en production tant que l'hôte n'est pas dans la liste des sorties) ; `--from <dossier>` lit `an-barometre/barometre_application_lois_assemblee.csv` et `an-barometre/barometre_application_mesures_assemblee.csv`.
- Fichiers fictifs au format officiel : `src/main/resources/example/an-barometre/lois.csv` et `mesures.csv` (lois de la 14e, de la 16e et de la 17e législature, dont le dossier d'exemple `DLR5L17N90001` des scrutins fictifs, un point-virgule dans un texte, une loi d'application directe).
- **Pas encore de téléchargement officiel** : `OfficialSources` ne lit rien pour cette source tant que l'hôte `barometre.assemblee-nationale.fr` n'est pas inscrit dans `infra/EGRESS.md` et dans `ALLOWED_HOSTS` (cahier des charges, section 3.8 : l'hôte est inscrit avant tout code qui le contacte). Voir la section 5 bis.

## 4. Application

- Page « Applicomètre » du Parlement de « Voir » (`ui/voir/parlement/ApplicationPage.kt`, état `LawApplicationState.kt`) : la méthode, un bouton par législature annoncée (taille affichée), puis les lois de la plus récente à la plus ancienne avec les mêmes chiffres, recherche par mot du titre, filtre par législature et « seulement les lois qui attendent encore une mesure ».
- Page d'une loi : nature (application directe, ratification, habilitation, ratification d'ordonnances, sans échéancier), chiffres, liens vers Légifrance et le dossier de l'Assemblée (avec la fenêtre « Vous quittez l'application »), fiche du texte dans « Voir », scrutins publics du dossier présents sur l'appareil, puis les mesures en attente, prises et devenues sans objet ; chaque mesure se lit comme un bloc au lecteur d'écran.
- Fiche d'un texte de « Voir » et promesse liée : bloc « Application de la loi » (`BillApplication`), seulement pour un texte promulgué ou connu des paquets ; avant téléchargement, il ouvre l'Applicomètre.
- Effacement rapide (S-44) : les paquets partent avec les tables du Parlement ; la recherche et les filtres à l'écran sont vidés.

## 5. Reste

- Compiler à l'entrée de « Voir » dans `main` (règle 5 bis), régénérer les paquets d'exemple embarqués avec `--only an,senat,pe,an-barometre`, et ajouter une brique « texte » [EXEMPLE] promulguée avec `official-id: an-dossier:DLR5L17N90001`, signée avec les autres contenus.
- Site : page de l'Applicomètre (plus tard, avec les autres pages du Parlement).

### 5 bis. Demandé au Directeur technique (infra/)

- Une ligne dans `infra/EGRESS.md` : outil d'import, `barometre.assemblee-nationale.fr`, HTTPS, deux fichiers par jour (8,5 Mo), seulement avec `an-barometre` dans `--only`.
- Puis, dans ma branche avec son accord : l'hôte dans `ALLOWED_HOSTS`, le téléchargement dans `OfficialSources` (deux fichiers, taille plafonnée, sans redirection), et `--only an,senat,cc,an-barometre` dans le minuteur quotidien des deux environnements (sources officielles en préproduction, D-076).

## 6. Tests

- `shared/core` : `LawApplicationTest` (mois annoncé, chiffres arrondis à l'unité inférieure, délais, catalogue, paquet par législature sur un geste).
- Outil d'import : `ApplicationImportTest` (fichiers officiels vers les paquets, point-virgule dans un texte, garde-fou, état inconnu refusé, option `--only`).
- Interface : `ApplicationScreenTest` (rien avant le premier import, un paquet par geste, mêmes chiffres, mois annoncé dépassé ou non, aucun classement ni bouton vers un élu, bloc sur la fiche d'un texte).
