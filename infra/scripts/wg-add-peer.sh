#!/usr/bin/env bash
# Adds one device of the team to the WireGuard tunnel of the preproduction. Installed as
# /usr/local/sbin/ppf-wg-add-peer by bootstrap-server.sh, run as root:
#
#   ppf-wg-add-peer <device-name>        for example: ppf-wg-add-peer proprietaire-telephone
#
# Writes /root/wireguard-peers/<device-name>.conf (profile for the WireGuard app on a computer)
# and <device-name>.png (QR code for the WireGuard app on a phone), and prints the QR code.
# The device's private key exists only in these two files: give them to the device, then delete
# them (shred). A lost device is removed by deleting its [Peer] block from /etc/wireguard/wg0.conf
# and running: wg syncconf wg0 <(wg-quick strip wg0)
set -euo pipefail

readonly CONF=/etc/wireguard/wg0.conf
readonly OUT=/root/wireguard-peers
readonly SUBNET_PREFIX="10.8.0"
readonly PORT=51820

name="${1:-}"
[[ "$name" =~ ^[a-z0-9-]{3,40}$ ]] || { echo "usage: $0 <device-name> (lowercase letters, digits, dashes)" >&2; exit 2; }
[ "$(id -u)" -eq 0 ] || { echo "Run as root." >&2; exit 1; }
[ -f "$CONF" ] || { echo "No $CONF: run bootstrap-server.sh first." >&2; exit 1; }
grep -q "^# device: $name$" "$CONF" && { echo "Device $name already exists." >&2; exit 1; }

# The first free address after the server (10.8.0.1).
address=""
for i in $(seq 2 254); do
    if ! grep -q "AllowedIPs = $SUBNET_PREFIX.$i/32" "$CONF"; then
        address="$SUBNET_PREFIX.$i"
        break
    fi
done
[ -n "$address" ] || { echo "No free address left." >&2; exit 1; }

# The public IPv4 address of the server, as the system routes to the Internet (nothing is sent).
public_ip="$(ip -4 route get 192.0.2.1 | awk '{for (i = 1; i < NF; i++) if ($i == "src") {print $(i + 1); exit}}')"
[ -n "$public_ip" ] || { echo "Cannot find the public IPv4 address." >&2; exit 1; }

umask 077
install -d -m 700 "$OUT"
private_key="$(wg genkey)"
public_key="$(printf '%s' "$private_key" | wg pubkey)"
preshared_key="$(wg genpsk)"

cat >> "$CONF" <<EOF

[Peer]
# device: $name
PublicKey = $public_key
PresharedKey = $preshared_key
AllowedIPs = $address/32
EOF
wg syncconf wg0 <(wg-quick strip wg0)

# The device sends through the tunnel the tunnel network and the server's own public address:
# the proxy answers the preproduction only to connections that come through the tunnel.
cat > "$OUT/$name.conf" <<EOF
[Interface]
# PPF preproduction, device: $name
PrivateKey = $private_key
Address = $address/32

[Peer]
PublicKey = $(cat /etc/wireguard/server.pub)
PresharedKey = $preshared_key
Endpoint = $public_ip:$PORT
AllowedIPs = $SUBNET_PREFIX.0/24, $public_ip/32
PersistentKeepalive = 25
EOF
qrencode -t PNG -o "$OUT/$name.png" < "$OUT/$name.conf"
unset private_key preshared_key

echo "Device $name added with the address $address."
echo "Profile: $OUT/$name.conf   QR code: $OUT/$name.png"
qrencode -t ansiutf8 < "$OUT/$name.conf"
