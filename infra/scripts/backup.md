# Sauvegardes (plan, à mettre en place au premier déploiement)

- Quoi : la base de données de production (export `pg_dump`), les volumes de données (médias) et les fichiers de configuration (`.env`, secrets, certificats, configuration WireGuard).
- Comment : sauvegarde chiffrée de bout en bout avant de quitter le serveur (outil envisagé : restic, chiffrement par mot de passe gardé hors ligne par la propriétaire), puis envoi vers un stockage objet chez un **autre prestataire européen** que Hostinger (cahier des charges, section 5.3). Prestataire et coût à valider par la propriétaire avant le premier déploiement.
- Quand : chaque nuit ; conservation 7 jours, 4 semaines, 6 mois.
- Restauration : testée au moins une fois par trimestre sur la préproduction, procédure écrite dans `docs/GUIDE_PROPRIETAIRE.md`.
- La préproduction n'est pas sauvegardée : elle ne contient que des données fictives.
