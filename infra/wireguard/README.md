# WireGuard : le tunnel vers la préproduction

La préproduction (`https://preprod.politisation-populaire-francaise.org`) a un nom public et un vrai certificat, mais le proxy ferme sans répondre toute connexion qui ne vient pas du tunnel (`10.8.0.0/24`, décision D-071). Chaque appareil de l'équipe (l'ordinateur et le téléphone de la propriétaire pour commencer) installe l'application WireGuard et un profil qui lui est propre.

- `wg0.conf.example` : forme de la configuration du serveur, sans aucune clé. La vraie (`/etc/wireguard/wg0.conf`) est écrite par `infra/scripts/bootstrap-server.sh` ; les clés y sont générées et ne quittent jamais le serveur.
- Un appareil s'ajoute par `ppf-wg-add-peer <nom>` (script `infra/scripts/wg-add-peer.sh`) : une paire de clés et une clé partagée par appareil, un profil `.conf` pour un ordinateur et un QR code pour un téléphone. Le profil fait passer par le tunnel le réseau du tunnel et l'adresse publique du serveur, rien d'autre : le reste de la navigation de l'appareil n'est pas touché.
- Un appareil perdu est retiré en supprimant son bloc `[Peer]` de `/etc/wireguard/wg0.conf`, puis `wg syncconf wg0 <(wg-quick strip wg0)`.
- En plus du tunnel, les pages web demandent un mot de passe (D-072).

Guide clic par clic pour Windows et Android : `docs/guides/wireguard-preproduction.md`.
