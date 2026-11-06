# WireGuard : le tunnel vers la préproduction

La préproduction n'est jamais exposée sur Internet. Le proxy ne l'écoute que sur l'adresse WireGuard du serveur (`10.8.0.1:8443`). Pour la consulter, chaque appareil de l'équipe (l'ordinateur et le téléphone de la propriétaire pour commencer) installe l'application WireGuard et un profil généré lors du premier déploiement.

- `wg0.conf.example` : modèle de configuration du serveur, sans aucune clé.
- Les clés sont générées sur le serveur au premier déploiement (`wg genkey`) et ne sont jamais versionnées.
- Chaque appareil a sa propre paire de clés ; un appareil perdu est retiré en supprimant son bloc `[Peer]`.
- En plus du tunnel, la préproduction demande un mot de passe (configuré dans le proxy).

Le guide clic par clic pour installer WireGuard sur Windows et Android sera écrit au moment du premier déploiement.
