# Kit contre le harcèlement dans « Agir » (AG-59, D-230)

Commencé le 9 novembre 2026 par un agent de la session « Agir » (branche `worktree-agent-ae692c216c5a4eb5d`, b1dbee0c et 6d1b81e3), interrompu par la pause du 9 novembre, fini, compilé et testé le 11 novembre 2026 par la session « [Agir] MVP 1 ».

## 1. Ce que fait le kit

Pour une personne harcelée en ligne ou dont l'adresse, le visage ou les coordonnées sont publiés pour l'exposer (« divulgation ») :

1. **Garder des preuves sur le téléphone** : adresse de la page, texte recopié, plateforme, date et heure (remplies avec le moment présent), note, capture d'écran nettoyée de ses métadonnées. Chaque preuve est chiffrée dans un coffre au format de la caméra protégée (AG-58), dans son propre dossier exclu des sauvegardes (`ppf-harcelement`) et sous sa propre clé maîtresse de l'Android Keystore ; son empreinte SHA-256 est gardée et affichée.
2. **Préparer un signalement**, rempli à partir des preuves choisies : notification à la plateforme (règlement européen sur les services numériques, article 16), signalement PHAROS, plainte au procureur de la République. Le texte se copie ou se partage par le système ; **PPF n'envoie rien**.
3. **Se faire aider** : numéros et services (dont la plateforme de signalement de la police et de la gendarmerie), guide « Harcèlement en ligne et divulgation » quand il sera dans les contenus, « Prévenir mon cercle » quand les cercles existeront.

Entrées : la feuille AIDE (« Harcèlement ou divulgation en ligne ») et une carte de l'accueil d'« Agir ». Le kit est dessiné par la coquille par-dessus la fenêtre (`HarassmentKitLayer`), la page dessous restant cachée au lecteur d'écran.

## 2. Vie privée et sécurité

- Les preuves ne quittent jamais le téléphone ; l'effacement rapide détruit la clé du coffre puis les fichiers (partie d'effacement `agir-harcelement`). « Effacer mes clés » ne les efface pas : elles ne dépendent pas du compte (même règle que la caméra protégée, D-190).
- Rien n'est gardé sur ordinateur à l'étape 1 (pas de Keystore) : l'écran le dit, les modèles de signalement restent utilisables.
- Les images déchiffrées ne restent qu'en mémoire, effacées quand l'écran se ferme ou quand PPF se verrouille.
- Aucune donnée nouvelle n'est traitée par PPF (rien à ajouter au registre) : tout reste sur l'appareil de la personne.

## 3. Tests

Cœur : `EvidenceLockerTest`, `HarassmentReportsTest`, `EvidenceVaultTest` (29 tests). Interface : `HarassmentKitScreenTest` (une preuve gardée avec son empreinte, reprise dans le modèle PHAROS copié, suppression après confirmation, sur téléphone, tablette sombre et ordinateur ; sans coffre, l'écran le dit et les modèles restent), `DeviceSecurityTest` (partie d'effacement).

## 4. Reste à faire

- Guide `harcelement-en-ligne-et-divulgation` (demandé à la session Comprendre) : sans lui, le lien du kit dit honnêtement que le guide n'est pas encore là.
- Règlement (UE) 2022/2065, article 16 : la structure de la notification (explication étayée, adresse exacte, nom et adresse électronique, déclaration de bonne foi, accusé de réception, décision avec voies de recours) a été recoupée le 11 novembre 2026 sur des commentaires du texte ; le texte officiel n'a pas pu être relu sur EUR-Lex ce jour-là : à relire avant l'ouverture publique.
- Relecture par un avocat des trois modèles et des qualifications citées (code pénal, harcèlement en ligne et divulgation d'informations personnelles), avant l'ouverture publique.
- « Prévenir mon cercle » avec les cercles (M28) ; vidéo des preuves avec celle de la caméra protégée.
